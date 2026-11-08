package com.takidz.runv2.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.takidz.runv2.data.ActiveCallState
import com.takidz.runv2.data.AvatarPalette
import com.takidz.runv2.data.CallDirection
import com.takidz.runv2.data.CallLogEntry
import com.takidz.runv2.data.CallPhase
import com.takidz.runv2.data.CallQualityMode
import com.takidz.runv2.data.ChatMessage
import com.takidz.runv2.data.DeviceIdentity
import com.takidz.runv2.data.FriendRelationState
import com.takidz.runv2.data.FriendRequestItem
import com.takidz.runv2.data.MatrixApiClient
import com.takidz.runv2.data.OlmCryptoManager
import com.takidz.runv2.data.PinKeyBackupManager
import com.takidz.runv2.data.PublicRoomResult
import com.takidz.runv2.data.RoomMember
import com.takidz.runv2.data.RoomSummary
import com.takidz.runv2.data.RunAccount
import com.takidz.runv2.data.RunConfig
import com.takidz.runv2.data.SecureSessionStore
import com.takidz.runv2.data.SignalingEvent
import com.takidz.runv2.data.ThemeMode
import com.takidz.runv2.data.UserSearchResult
import com.takidz.runv2.webrtc.WebRtcCallManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

enum class AppScreen {
    LOGIN,
    PIN_CREATE,
    PIN_RESTORE,
    PIN_RESTORE_SUCCESS,
    MAIN_TABS,
    SEARCH,
    CREATE_ROOM,
    CHAT,
    ROOM_INFO,
    ACTIVE_CALL
}

enum class MainTab {
    ROOMS,
    CALLS,
    ACCOUNT
}

enum class SearchTab {
    FRIENDS,
    ROOMS
}

data class RunUiState(
    val currentScreen: AppScreen = AppScreen.LOGIN,
    val activeMainTab: MainTab = MainTab.ROOMS,
    val activeSearchTab: SearchTab = SearchTab.FRIENDS,
    val themeMode: ThemeMode = ThemeMode.WARM_NIGHT,
    val callQualityMode: CallQualityMode = CallQualityMode.DEFAULT_480P,
    val isLoading: Boolean = false,
    val statusBanner: String? = null,
    val errorMessage: String? = null,
    val account: RunAccount? = null,
    // Strictly empty by default: zero fake or placeholder rooms
    val rooms: List<RoomSummary> = emptyList(),
    val selectedSpaceFilter: String = "ALL", // ALL, GROUPS, DIRECT
    val activeRoom: RoomSummary? = null,
    val activeRoomMessages: List<ChatMessage> = emptyList(),
    val activeRoomMembers: List<RoomMember> = emptyList(),
    val activeRoomDevices: List<DeviceIdentity> = emptyList(),
    // Search & Friends (strictly real server results)
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val friendSearchResults: List<UserSearchResult> = emptyList(),
    val publicRoomResults: List<PublicRoomResult> = emptyList(),
    val incomingFriendRequests: List<FriendRequestItem> = emptyList(),
    val outgoingFriendRequests: List<FriendRequestItem> = emptyList(),
    val friends: List<UserSearchResult> = emptyList(),
    // Call logs, TURN server availability status & active WebRTC state
    val turnStatusMessage: String? = null,
    val callLogs: List<CallLogEntry> = emptyList(),
    val activeCall: ActiveCallState = ActiveCallState()
)

class RunViewModel(application: Application) : AndroidViewModel(application) {

    val sessionStore = SecureSessionStore(application)
    val olmCryptoManager = OlmCryptoManager(sessionStore)
    val pinBackupManager = PinKeyBackupManager(sessionStore, olmCryptoManager)
    val apiClient = MatrixApiClient(sessionStore, olmCryptoManager, pinBackupManager)

    private val _uiState = MutableStateFlow(
        RunUiState(
            currentScreen = if (sessionStore.isLoggedIn()) AppScreen.MAIN_TABS else AppScreen.LOGIN,
            themeMode = sessionStore.getThemeMode(),
            callQualityMode = sessionStore.getCallQualityMode(),
            account = sessionStore.getInternalUserId()?.let { uid ->
                RunAccount(
                    internalUserId = uid,
                    publicHandle = RunConfig.toPublicHandle(uid),
                    displayName = sessionStore.getDisplayName(),
                    deviceId = sessionStore.getDeviceId().orEmpty(),
                    isPinConfigured = sessionStore.isPinConfigured(),
                    deviceEd25519Fingerprint = runCatching {
                        olmCryptoManager.getFormattedDeviceFingerprint()
                    }.getOrDefault(""),
                    deviceCurve25519Key = runCatching {
                        olmCryptoManager.getDeviceCurve25519Key()
                    }.getOrDefault("")
                )
            },
            callLogs = sessionStore.getCallLogs()
        )
    )
    val uiState: StateFlow<RunUiState> = _uiState.asStateFlow()

    private val messagesCache = mutableMapOf<String, MutableList<ChatMessage>>()
    private var syncJob: Job? = null
    private var callTimerJob: Job? = null
    private var pendingDeepLinkUri: String? = null

    val webRtcCallManager = WebRtcCallManager(
        context = application,
        onLocalIceCandidates = { candidates ->
            val call = _uiState.value.activeCall
            if (call.callId.isNotBlank() && call.roomId.isNotBlank()) {
                viewModelScope.launch {
                    apiClient.sendCallCandidates(call.roomId, call.callId, candidates)
                }
            }
        },
        onConnectionConnected = {
            _uiState.update { state ->
                state.copy(
                    activeCall = state.activeCall.copy(phase = CallPhase.CONNECTED)
                )
            }
            startCallTimer()
        },
        onConnectionEnded = {
            endActiveCall(sendRemoteHangup = false)
        }
    )

    init {
        if (sessionStore.isLoggedIn()) {
            startSyncLoop()
            checkTurnServerStatusAfterLogin()
        }
    }

    // =========================================================================
    // 1. AUTHENTICATION & PIN BACKUP FLOWS
    // =========================================================================

    fun loginWithUsername(usernameInput: String, passwordPlain: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = apiClient.login(usernameInput, passwordPlain)
            result.onSuccess { acc ->
                val hasRemoteBackup = apiClient.checkRemotePinBackupExists()
                val nextScreen = when {
                    acc.isPinConfigured -> AppScreen.MAIN_TABS
                    hasRemoteBackup -> AppScreen.PIN_RESTORE
                    else -> AppScreen.PIN_CREATE
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        account = acc,
                        currentScreen = nextScreen
                    )
                }
                startSyncLoop()
                checkTurnServerStatusAfterLogin()
                processPendingDeepLinkIfAny()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر تسجيل الدخول")
                }
            }
        }
    }

    fun registerWithUsername(usernameInput: String, passwordPlain: String, displayName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = apiClient.register(usernameInput, passwordPlain, displayName)
            result.onSuccess { acc ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        account = acc,
                        currentScreen = AppScreen.PIN_CREATE
                    )
                }
                startSyncLoop()
                checkTurnServerStatusAfterLogin()
                processPendingDeepLinkIfAny()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر إنشاء الحساب")
                }
            }
        }
    }

    fun createAndUploadPinBackup(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = apiClient.uploadPinKeyBackup(pin)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        account = state.account?.copy(isPinConfigured = true),
                        currentScreen = AppScreen.MAIN_TABS,
                        statusBanner = "تم حفظ نسخة مفاتيح التشفير (Megolm) برمز PIN الخاص بك"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر حفظ رمز PIN")
                }
            }
        }
    }

    fun restorePinBackup(pin: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = apiClient.restorePinKeyBackup(pin)
            result.onSuccess { restoredCount ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        account = state.account?.copy(isPinConfigured = true),
                        currentScreen = AppScreen.PIN_RESTORE_SUCCESS,
                        statusBanner = "تمت استعادة $restoredCount مفتاح جلسة Megolm بنجاح"
                    )
                }
                // Re-fetch active room history if open so newly restored keys decrypt old messages
                _uiState.value.activeRoom?.let { active ->
                    openRoomChat(active)
                }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "رمز PIN غير صحيح أو لا توجد نسخة احتياطية مطابقة"
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            syncJob?.cancel()
            webRtcCallManager.endCall()
            apiClient.logout()
            messagesCache.clear()
            _uiState.value = RunUiState(
                currentScreen = AppScreen.LOGIN,
                themeMode = sessionStore.getThemeMode(),
                callQualityMode = sessionStore.getCallQualityMode()
            )
        }
    }

    // =========================================================================
    // 2. CONTINUOUS MATRIX /SYNC LOOP & TURN CHECK
    // =========================================================================

    private fun checkTurnServerStatusAfterLogin() {
        viewModelScope.launch {
            val turnRes = apiClient.fetchTurnCredentials()
            turnRes.onSuccess { creds ->
                val statusMsg = if (creds.hasValidTurnServers) {
                    "خادم TURN جاهز للمكالمات (${creds.uris.size} عنوان)"
                } else {
                    "تنبيه صادق: السيرفر لا يوفر بيانات TURN عبر /voip/turnServer حالياً (تعتمد المكالمات على STUN المباشر)"
                }
                _uiState.update { it.copy(turnStatusMessage = statusMsg) }
            }.onFailure {
                _uiState.update {
                    it.copy(
                        turnStatusMessage = "تنبيه صادق: السيرفر لم يُرجع بيانات TURN عبر /voip/turnServer (تعتمد المكالمات على STUN المباشر)"
                    )
                }
            }
        }
    }

    private fun startSyncLoop() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            var sinceToken: String? = null
            while (isActive && sessionStore.isLoggedIn()) {
                val syncResult = apiClient.sync(sinceToken, timeoutMs = if (sinceToken == null) 0 else 12000)
                syncResult.onSuccess { data ->
                    sinceToken = data.nextBatch

                    // Merge new timeline messages into room caches
                    data.newMessagesByRoom.forEach { (roomId, msgs) ->
                        val list = messagesCache.getOrPut(roomId) { mutableListOf() }
                        val existingIds = list.map { it.eventId }.toHashSet()
                        msgs.forEach { m ->
                            if (!existingIds.contains(m.eventId)) {
                                list.add(m)
                            }
                        }
                    }

                    // Merge rooms list preserving previously synced rooms
                    _uiState.update { current ->
                        val mergedRoomsMap = current.rooms.associateBy { it.roomId }.toMutableMap()
                        data.rooms.forEach { updatedRoom ->
                            mergedRoomsMap[updatedRoom.roomId] = updatedRoom
                        }
                        val sortedRooms = mergedRoomsMap.values.sortedByDescending { it.lastMessageTimestamp }
                        val updatedActiveRoom = current.activeRoom?.let { active ->
                            mergedRoomsMap[active.roomId] ?: active
                        }
                        val updatedActiveMessages = updatedActiveRoom?.let { active ->
                            messagesCache[active.roomId]?.toList() ?: current.activeRoomMessages
                        } ?: current.activeRoomMessages

                        current.copy(
                            rooms = sortedRooms,
                            activeRoom = updatedActiveRoom,
                            activeRoomMessages = updatedActiveMessages,
                            incomingFriendRequests = data.incomingFriendInvites,
                            outgoingFriendRequests = data.outgoingFriendInvites,
                            friends = data.friends
                        )
                    }

                    // Process incoming WebRTC signaling events
                    data.signalingEvents.forEach { ev ->
                        handleSignalingEvent(ev)
                    }
                }.onFailure {
                    delay(4000)
                }
                delay(800)
            }
        }
    }

    // =========================================================================
    // 3. ROOMS, E2EE MESSAGES, CREATION, INVITES & DEEP LINKS
    // =========================================================================

    fun openRoomChat(room: RoomSummary) {
        _uiState.update {
            it.copy(
                activeRoom = room,
                activeRoomMessages = messagesCache[room.roomId]?.toList().orEmpty(),
                currentScreen = AppScreen.CHAT,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val isEnc = apiClient.checkRoomEncryptionEnabled(room.roomId)
            val encNote = if (isEnc) null else "هذه الغرفة غير مشفرة (رسائل قياسية غير مشفرة)"
            _uiState.update { state ->
                val updatedActive = state.activeRoom?.let {
                    if (it.roomId == room.roomId) {
                        it.copy(isEncrypted = isEnc, encryptionStatusNote = encNote)
                    } else it
                }
                state.copy(
                    activeRoom = updatedActive,
                    rooms = state.rooms.map { r ->
                        if (r.roomId == room.roomId) r.copy(isEncrypted = isEnc, encryptionStatusNote = encNote) else r
                    }
                )
            }

            apiClient.fetchRoomMessages(room.roomId, limit = 60).onSuccess { history ->
                val list = messagesCache.getOrPut(room.roomId) { mutableListOf() }
                val existingIds = list.map { it.eventId }.toHashSet()
                val merged = (history.filter { !existingIds.contains(it.eventId) } + list)
                    .sortedBy { it.timestamp }
                    .toMutableList()
                messagesCache[room.roomId] = merged
                if (_uiState.value.activeRoom?.roomId == room.roomId) {
                    _uiState.update { it.copy(activeRoomMessages = merged.toList()) }
                }
            }
        }
    }

    fun sendChatMessage(text: String) {
        val activeRoom = _uiState.value.activeRoom ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            val result = apiClient.sendRoomMessage(
                roomId = activeRoom.roomId,
                text = trimmed
            )
            result.onSuccess { sentMsg ->
                val list = messagesCache.getOrPut(activeRoom.roomId) { mutableListOf() }
                if (list.none { it.eventId == sentMsg.eventId }) {
                    list.add(sentMsg)
                }
                _uiState.update { state ->
                    state.copy(
                        activeRoomMessages = list.toList(),
                        rooms = state.rooms.map { r ->
                            if (r.roomId == activeRoom.roomId) {
                                r.copy(
                                    lastMessageSender = "أنت",
                                    lastMessageBody = trimmed,
                                    lastMessageTimestamp = sentMsg.timestamp,
                                    isEncrypted = sentMsg.isEncrypted
                                )
                            } else r
                        }.sortedByDescending { it.lastMessageTimestamp }
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(errorMessage = err.message ?: "تعذر إرسال الرسالة") }
            }
        }
    }

    fun openRoomInfo() {
        val room = _uiState.value.activeRoom ?: return
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.ROOM_INFO,
                activeRoomMembers = emptyList(),
                activeRoomDevices = emptyList()
            )
        }
        viewModelScope.launch {
            apiClient.fetchRoomMembers(room.roomId).onSuccess { members ->
                val devices = olmCryptoManager.getKnownDevicesForUsers(members.map { it.userId })
                _uiState.update {
                    it.copy(
                        activeRoomMembers = members,
                        activeRoomDevices = devices
                    )
                }
            }
        }
    }

    fun markDeviceVerified(device: DeviceIdentity) {
        olmCryptoManager.setDeviceManuallyVerified(device.userId, device.deviceId, !device.manuallyVerified)
        val memberIds = _uiState.value.activeRoomMembers.map { it.userId }
        val updatedDevices = olmCryptoManager.getKnownDevicesForUsers(memberIds)
        _uiState.update {
            it.copy(
                activeRoomDevices = updatedDevices,
                statusBanner = "تم تحديث حالة التحقق للجهاز ${device.deviceId}"
            )
        }
    }

    fun createNewGroupRoom(name: String, topic: String, selectedHandles: List<String>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = apiClient.createGroupRoom(name, topic, selectedHandles)
            result.onSuccess { newRoom ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        rooms = listOf(newRoom) + state.rooms,
                        activeRoom = newRoom,
                        currentScreen = AppScreen.ROOM_INFO,
                        statusBanner = if (newRoom.isEncrypted) {
                            "تم إنشاء الغرفة المشفرة (Olm/Megolm) · الكود ${newRoom.roomCode}"
                        } else {
                            "تم إنشاء الغرفة (بدون تشفير) · الكود ${newRoom.roomCode}"
                        }
                    )
                }
                apiClient.fetchRoomMembers(newRoom.roomId).onSuccess { members ->
                    val devices = olmCryptoManager.getKnownDevicesForUsers(members.map { it.userId })
                    _uiState.update {
                        it.copy(
                            activeRoomMembers = members,
                            activeRoomDevices = devices
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر إنشاء الغرفة")
                }
            }
        }
    }

    fun joinRoomByCodeOrId(codeOrRoomId: String, openAfterJoin: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            apiClient.joinRoomByCodeOrId(codeOrRoomId).onSuccess { joinedRoomId ->
                val cleanCode = RunConfig.toPublicRoomCode(codeOrRoomId)
                val isEnc = apiClient.isRoomCurrentlyEncrypted(joinedRoomId)
                val fallbackSummary = _uiState.value.rooms.firstOrNull { it.roomId == joinedRoomId }
                    ?: RoomSummary(
                        roomId = joinedRoomId,
                        name = cleanCode.ifBlank { "غرفة Run" },
                        topic = "",
                        roomCode = cleanCode,
                        isDirect = false,
                        directPeerUserId = null,
                        memberCount = 2,
                        onlineCount = 1,
                        lastMessageSender = null,
                        lastMessageBody = "انضممت إلى الغرفة",
                        lastMessageTimestamp = System.currentTimeMillis(),
                        unreadCount = 0,
                        isEncrypted = isEnc,
                        encryptionStatusNote = if (isEnc) null else "هذه الغرفة غير مشفرة (رسائل قياسية غير مشفرة)",
                        avatarSeedColor = AvatarPalette.colorFor(joinedRoomId)
                    )
                _uiState.update { state ->
                    val updatedRooms = (listOf(fallbackSummary) + state.rooms.filterNot { it.roomId == joinedRoomId })
                    state.copy(
                        isLoading = false,
                        rooms = updatedRooms,
                        publicRoomResults = state.publicRoomResults.map {
                            if (it.roomId == joinedRoomId || it.roomCode.equals(cleanCode, ignoreCase = true)) {
                                it.copy(isJoined = true)
                            } else it
                        },
                        incomingFriendRequests = state.incomingFriendRequests.filterNot { it.roomId == joinedRoomId }
                    )
                }
                if (openAfterJoin) {
                    openRoomChat(fallbackSummary)
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر الانضمام إلى الغرفة")
                }
            }
        }
    }

    fun declineIncomingInvite(roomId: String) {
        viewModelScope.launch {
            apiClient.leaveOrDeclineRoom(roomId).onSuccess {
                _uiState.update { state ->
                    state.copy(
                        incomingFriendRequests = state.incomingFriendRequests.filterNot { it.roomId == roomId }
                    )
                }
            }
        }
    }

    fun handleIncomingDeepLink(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        if (!sessionStore.isLoggedIn()) {
            pendingDeepLinkUri = uriString
            return
        }
        when {
            uriString.startsWith("run://join/", ignoreCase = true) -> {
                val code = uriString.substringAfter("run://join/").trim()
                if (code.isNotEmpty()) {
                    joinRoomByCodeOrId(code, openAfterJoin = true)
                }
            }
            uriString.startsWith("run://friend/", ignoreCase = true) -> {
                val handle = uriString.substringAfter("run://friend/").trim()
                if (handle.isNotEmpty()) {
                    openOrStartDirectChatWithUser(handle, handle.removePrefix("@"))
                }
            }
        }
    }

    private fun processPendingDeepLinkIfAny() {
        val pending = pendingDeepLinkUri ?: return
        pendingDeepLinkUri = null
        handleIncomingDeepLink(pending)
    }

    // =========================================================================
    // 4. SEARCH & FRIENDS (Friends by @username & Display Name, Public Rooms)
    // =========================================================================

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.trim().startsWith("run://join/", ignoreCase = true)) {
            val code = query.trim().removePrefix("run://join/").trim()
            if (code.isNotEmpty()) {
                joinRoomByCodeOrId(code, openAfterJoin = true)
                return
            }
        }
        executeSearch(query)
    }

    fun executeSearch(query: String = _uiState.value.searchQuery) {
        viewModelScope.launch {
            val trimmed = query.trim()
            _uiState.update { it.copy(isSearching = true, errorMessage = null) }

            val knownFriendIds = _uiState.value.friends.map { it.internalUserId }.toSet()
            val outgoingIds = _uiState.value.outgoingFriendRequests.map { it.internalUserId }.toSet()
            val joinedRoomIds = _uiState.value.rooms.map { it.roomId }.toSet()

            if (trimmed.isNotEmpty()) {
                val usersRes = apiClient.searchUsers(trimmed, knownFriendIds, outgoingIds)
                val roomsRes = apiClient.searchPublicRooms(trimmed, joinedRoomIds)
                _uiState.update { state ->
                    state.copy(
                        isSearching = false,
                        friendSearchResults = usersRes.getOrDefault(emptyList()),
                        publicRoomResults = roomsRes.getOrDefault(emptyList())
                    )
                }
            } else {
                val roomsRes = apiClient.searchPublicRooms("", joinedRoomIds)
                _uiState.update { state ->
                    state.copy(
                        isSearching = false,
                        friendSearchResults = emptyList(),
                        publicRoomResults = roomsRes.getOrDefault(emptyList())
                    )
                }
            }
        }
    }

    fun sendFriendRequest(user: UserSearchResult) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            apiClient.createOrOpenDirectChat(user.internalUserId).onSuccess { roomId ->
                val newOutgoing = FriendRequestItem(
                    roomId = roomId,
                    internalUserId = user.internalUserId,
                    publicHandle = user.publicHandle,
                    displayName = user.displayName,
                    isIncoming = false,
                    presence = user.presence,
                    statusText = "بانتظار القبول",
                    avatarSeedColor = user.avatarSeedColor
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        friendSearchResults = state.friendSearchResults.map { item ->
                            if (item.internalUserId == user.internalUserId) {
                                item.copy(relationState = FriendRelationState.OUTGOING_PENDING)
                            } else item
                        },
                        outgoingFriendRequests = (listOf(newOutgoing) + state.outgoingFriendRequests)
                            .distinctBy { it.internalUserId },
                        statusBanner = "تم إرسال طلب الصداقة المشفر إلى ${user.displayName}"
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر إرسال طلب الصداقة")
                }
            }
        }
    }

    fun openOrStartDirectChatWithUser(handleOrUserId: String, displayName: String) {
        val internalId = RunConfig.toInternalUserId(handleOrUserId)
        val existingRoom = _uiState.value.rooms.firstOrNull {
            it.directPeerUserId == internalId || it.roomCode.equals(RunConfig.toPublicHandle(internalId), ignoreCase = true)
        }
        if (existingRoom != null) {
            openRoomChat(existingRoom)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            apiClient.createOrOpenDirectChat(internalId).onSuccess { roomId ->
                val isEnc = apiClient.isRoomCurrentlyEncrypted(roomId)
                val summary = RoomSummary(
                    roomId = roomId,
                    name = displayName.ifBlank { RunConfig.toPublicHandle(internalId).removePrefix("@") },
                    topic = "",
                    roomCode = RunConfig.toPublicHandle(internalId),
                    isDirect = true,
                    directPeerUserId = internalId,
                    memberCount = 2,
                    onlineCount = 1,
                    lastMessageSender = null,
                    lastMessageBody = if (isEnc) "محادثة خاصة مشفرة" else "محادثة خاصة (غير مشفرة)",
                    lastMessageTimestamp = System.currentTimeMillis(),
                    unreadCount = 0,
                    isEncrypted = isEnc,
                    encryptionStatusNote = if (isEnc) null else "تعذر تفعيل التشفير في هذه الغرفة؛ الرسائل فيها غير مشفرة",
                    avatarSeedColor = AvatarPalette.colorFor(internalId)
                )
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        rooms = listOf(summary) + state.rooms.filterNot { it.roomId == roomId }
                    )
                }
                openRoomChat(summary)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "تعذر فتح المحادثة")
                }
            }
        }
    }

    // =========================================================================
    // 5. WEBRTC 1-TO-1 VIDEO & VOICE CALLS (Task 2: Keep WebRTC as-is + TURN check)
    // =========================================================================

    fun startCallInActiveRoom(isVideoRequested: Boolean) {
        val room = _uiState.value.activeRoom ?: return
        val qualityMode = _uiState.value.callQualityMode
        val effectiveVideo = isVideoRequested && qualityMode != CallQualityMode.AUDIO_ONLY
        val callId = "runv2_call_${UUID.randomUUID()}"

        _uiState.update {
            it.copy(
                currentScreen = AppScreen.ACTIVE_CALL,
                activeCall = ActiveCallState(
                    callId = callId,
                    roomId = room.roomId,
                    peerName = room.name,
                    peerHandle = room.roomCode,
                    isVideo = effectiveVideo,
                    phase = CallPhase.OUTGOING_RINGING,
                    durationSeconds = 0,
                    isMicMuted = false,
                    isCameraEnabled = effectiveVideo,
                    isSpeakerOn = effectiveVideo,
                    avatarSeedColor = room.avatarSeedColor
                )
            )
        }

        viewModelScope.launch {
            val turnCreds = apiClient.fetchTurnCredentials().getOrNull()
            val hasTurn = turnCreds?.hasValidTurnServers == true
            _uiState.update { state ->
                state.copy(
                    activeCall = state.activeCall.copy(turnServerAvailable = hasTurn),
                    turnStatusMessage = if (hasTurn) {
                        "خادم TURN متصل للمكالمة"
                    } else {
                        "تنبيه: السيرفر لا يوفر TURN حالياً؛ الاتصال يتم مباشرة عبر STUN/ICE"
                    }
                )
            }
            webRtcCallManager.startOutgoingCall(
                turnCredentials = turnCreds,
                qualityMode = qualityMode,
                requestedVideo = effectiveVideo
            ) { offerSdp ->
                viewModelScope.launch {
                    apiClient.sendCallInvite(room.roomId, callId, offerSdp)
                }
            }
        }
    }

    fun acceptIncomingCall() {
        val call = _uiState.value.activeCall
        val offerSdp = call.remoteOfferSdp ?: return
        val qualityMode = _uiState.value.callQualityMode
        val effectiveVideo = call.isVideo && qualityMode != CallQualityMode.AUDIO_ONLY

        _uiState.update {
            it.copy(
                activeCall = call.copy(
                    phase = CallPhase.CONNECTING,
                    isVideo = effectiveVideo,
                    isCameraEnabled = effectiveVideo
                )
            )
        }

        viewModelScope.launch {
            val turnCreds = apiClient.fetchTurnCredentials().getOrNull()
            val hasTurn = turnCreds?.hasValidTurnServers == true
            _uiState.update { state ->
                state.copy(
                    activeCall = state.activeCall.copy(turnServerAvailable = hasTurn)
                )
            }
            webRtcCallManager.answerIncomingCall(
                offerSdp = offerSdp,
                turnCredentials = turnCreds,
                qualityMode = qualityMode,
                requestedVideo = effectiveVideo
            ) { answerSdp ->
                viewModelScope.launch {
                    apiClient.sendCallAnswer(call.roomId, call.callId, answerSdp)
                }
            }
        }
    }

    fun toggleCallMic() {
        val nextMuted = !_uiState.value.activeCall.isMicMuted
        webRtcCallManager.setMicMuted(nextMuted)
        _uiState.update { it.copy(activeCall = it.activeCall.copy(isMicMuted = nextMuted)) }
    }

    fun toggleCallCamera() {
        val nextEnabled = !_uiState.value.activeCall.isCameraEnabled
        webRtcCallManager.setCameraEnabled(nextEnabled)
        _uiState.update { it.copy(activeCall = it.activeCall.copy(isCameraEnabled = nextEnabled)) }
    }

    fun toggleCallSpeaker() {
        val nextSpeaker = !_uiState.value.activeCall.isSpeakerOn
        webRtcCallManager.setSpeakerphoneEnabled(nextSpeaker)
        _uiState.update { it.copy(activeCall = it.activeCall.copy(isSpeakerOn = nextSpeaker)) }
    }

    fun endActiveCall(sendRemoteHangup: Boolean = true) {
        val call = _uiState.value.activeCall
        if (call.phase == CallPhase.IDLE) return

        callTimerJob?.cancel()
        webRtcCallManager.endCall()

        if (sendRemoteHangup && call.roomId.isNotBlank() && call.callId.isNotBlank()) {
            viewModelScope.launch {
                apiClient.sendCallHangup(call.roomId, call.callId)
            }
        }

        val logEntry = CallLogEntry(
            id = call.callId.ifBlank { UUID.randomUUID().toString() },
            roomId = call.roomId,
            peerName = call.peerName.ifBlank { "مكالمة Run" },
            isVideo = call.isVideo,
            direction = when {
                call.phase == CallPhase.INCOMING_RINGING && !sendRemoteHangup -> CallDirection.MISSED
                call.remoteOfferSdp != null -> CallDirection.INCOMING
                else -> CallDirection.OUTGOING
            },
            durationSeconds = call.durationSeconds,
            timestamp = System.currentTimeMillis(),
            avatarSeedColor = call.avatarSeedColor
        )
        sessionStore.saveCallLogEntry(logEntry)

        _uiState.update { state ->
            state.copy(
                activeCall = ActiveCallState(phase = CallPhase.IDLE),
                callLogs = sessionStore.getCallLogs(),
                currentScreen = if (state.activeRoom != null) AppScreen.CHAT else AppScreen.MAIN_TABS
            )
        }
    }

    private fun handleSignalingEvent(ev: SignalingEvent) {
        val currentCall = _uiState.value.activeCall
        when (ev.type) {
            "m.call.invite" -> {
                if (currentCall.phase != CallPhase.IDLE) return
                val room = _uiState.value.rooms.firstOrNull { it.roomId == ev.roomId }
                val peerHandle = RunConfig.toPublicHandle(ev.senderUserId)
                val peerName = room?.name ?: peerHandle.removePrefix("@")
                _uiState.update {
                    it.copy(
                        currentScreen = AppScreen.ACTIVE_CALL,
                        activeCall = ActiveCallState(
                            callId = ev.callId,
                            roomId = ev.roomId,
                            peerName = peerName,
                            peerHandle = peerHandle,
                            isVideo = ev.isVideo,
                            phase = CallPhase.INCOMING_RINGING,
                            remoteOfferSdp = ev.sdp,
                            isSpeakerOn = ev.isVideo,
                            avatarSeedColor = AvatarPalette.colorFor(ev.senderUserId)
                        )
                    )
                }
            }
            "m.call.answer" -> {
                if (currentCall.callId == ev.callId && !ev.sdp.isNullOrBlank()) {
                    webRtcCallManager.onRemoteAnswerReceived(ev.sdp)
                }
            }
            "m.call.candidates" -> {
                if (currentCall.callId == ev.callId && ev.candidates.isNotEmpty()) {
                    webRtcCallManager.onRemoteIceCandidatesReceived(ev.candidates)
                }
            }
            "m.call.hangup" -> {
                if (currentCall.callId == ev.callId) {
                    endActiveCall(sendRemoteHangup = false)
                }
            }
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive && _uiState.value.activeCall.phase == CallPhase.CONNECTED) {
                delay(1000)
                _uiState.update { state ->
                    state.copy(
                        activeCall = state.activeCall.copy(
                            durationSeconds = state.activeCall.durationSeconds + 1
                        )
                    )
                }
            }
        }
    }

    // =========================================================================
    // 6. NAVIGATION & SETTINGS
    // =========================================================================

    fun navigateToScreen(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen, errorMessage = null) }
        if (screen == AppScreen.SEARCH) {
            executeSearch(_uiState.value.searchQuery)
        }
    }

    fun selectMainTab(tab: MainTab) {
        _uiState.update {
            it.copy(
                currentScreen = AppScreen.MAIN_TABS,
                activeMainTab = tab,
                errorMessage = null
            )
        }
    }

    fun selectSearchTab(tab: SearchTab) {
        _uiState.update { it.copy(activeSearchTab = tab) }
    }

    fun selectSpaceFilter(filter: String) {
        _uiState.update { it.copy(selectedSpaceFilter = filter) }
    }

    fun setThemeMode(mode: ThemeMode) {
        sessionStore.saveThemeMode(mode)
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setCallQualityMode(mode: CallQualityMode) {
        sessionStore.saveCallQualityMode(mode)
        _uiState.update { it.copy(callQualityMode = mode) }
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBanner = null, errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        callTimerJob?.cancel()
        webRtcCallManager.endCall()
    }
}
