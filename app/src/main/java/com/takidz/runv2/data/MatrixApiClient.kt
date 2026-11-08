package com.takidz.runv2.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Real network client communicating with the approved backend (`https://matrix.tchncs.de`).
 *
 * Implements:
 * - Login & Registration (Username + Password only, zero email/phone, never logs passwords)
 * - Long-poll `/sync` for rooms, messages, presence, friend invites, and WebRTC call signaling
 * - Room message sending (both encrypted `m.room.encrypted` and standard `m.room.message`)
 * - Room history pagination (`/messages`)
 * - Group room creation with permanent room code alias (`#<code>:tchncs.de`)
 * - Room joining by roomId, alias, or `run://join/<code>`
 * - Friend search by `@username` and display name (`/user_directory/search` + profile lookup)
 * - Public room search by name and room code (`/publicRooms` + alias resolution)
 * - Presence updates and queries (`/presence/{userId}/status`)
 * - PIN-encrypted key backup upload & restore via Account Data (`m.runv2.key_backup.v2`)
 * - TURN server credentials (`/voip/turnServer`) and WebRTC signaling (`m.call.*`)
 */
class MatrixApiClient(
    private val sessionStore: SecureSessionStore,
    private val pinBackupManager: PinKeyBackupManager
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    // In-memory cache of room metadata across incremental /sync batches
    private val roomNameCache = mutableMapOf<String, String>()
    private val roomTopicCache = mutableMapOf<String, String>()
    private val roomAliasCache = mutableMapOf<String, String>()
    private val roomEncryptedCache = mutableMapOf<String, Boolean>()
    private val roomMemberCountCache = mutableMapOf<String, Int>()
    private val directPeerCache = mutableMapOf<String, String>()
    private val userDisplayNameCache = mutableMapOf<String, String>()
    private val userPresenceCache = mutableMapOf<String, PresenceState>()

    // =========================================================================
    // 1. AUTHENTICATION (Login & Registration with @username + password only)
    // =========================================================================

    suspend fun login(handleOrUsername: String, passwordPlain: String): Result<RunAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val localpart = RunConfig.toLocalpart(handleOrUsername)
                require(localpart.isNotBlank()) { "يرجى إدخال اسم المستخدم" }
                require(passwordPlain.isNotBlank()) { "يرجى إدخال كلمة المرور" }

                val payload = JSONObject().apply {
                    put("type", "m.login.password")
                    put("identifier", JSONObject().apply {
                        put("type", "m.id.user")
                        put("user", localpart)
                    })
                    put("password", passwordPlain)
                    put("initial_device_display_name", "Run v2 Android")
                }

                val response = executePost("/_matrix/client/v3/login", payload, authenticated = false)
                val accessToken = response.getString("access_token")
                val userId = response.getString("user_id")
                val deviceId = response.optString("device_id", "RUNV2_${UUID.randomUUID()}")

                sessionStore.saveSession(
                    accessToken = accessToken,
                    internalUserId = userId,
                    deviceId = deviceId,
                    displayName = localpart
                )

                // Fetch profile display name if set
                val profileName = fetchDisplayNameInternal(userId) ?: localpart
                sessionStore.updateDisplayName(profileName)
                setPresenceOnlineInternal(userId)

                RunAccount(
                    internalUserId = userId,
                    publicHandle = RunConfig.toPublicHandle(userId),
                    displayName = profileName,
                    deviceId = deviceId,
                    isPinConfigured = sessionStore.isPinConfigured()
                )
            }
        }

    suspend fun register(handleOrUsername: String, passwordPlain: String, displayName: String? = null): Result<RunAccount> =
        withContext(Dispatchers.IO) {
            runCatching {
                val localpart = RunConfig.toLocalpart(handleOrUsername)
                require(localpart.length >= 3) { "اسم المستخدم يجب أن يتكون من 3 أحرف أو أرقام على الأقل" }
                require(passwordPlain.length >= 6) { "كلمة المرور يجب أن تتكون من 6 أحرف على الأقل" }

                val firstBody = JSONObject().apply {
                    put("username", localpart)
                    put("password", passwordPlain)
                    put("initial_device_display_name", "Run v2 Android")
                }

                val url = "${RunConfig.HOMESERVER_BASE_URL}/_matrix/client/v3/register"
                val req1 = Request.Builder()
                    .url(url)
                    .post(firstBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val finalJson = httpClient.newCall(req1).execute().use { resp1 ->
                    val raw1 = resp1.body?.string().orEmpty()
                    if (resp1.isSuccessful) {
                        JSONObject(raw1)
                    } else if (resp1.code == 401) {
                        // Complete UIAA m.login.dummy stage if supported by homeserver
                        val uiaa = JSONObject(raw1)
                        val session = uiaa.optString("session")
                        val secondBody = JSONObject().apply {
                            put("username", localpart)
                            put("password", passwordPlain)
                            put("initial_device_display_name", "Run v2 Android")
                            put("auth", JSONObject().apply {
                                put("type", "m.login.dummy")
                                if (session.isNotBlank()) put("session", session)
                            })
                        }
                        val req2 = Request.Builder()
                            .url(url)
                            .post(secondBody.toString().toRequestBody(jsonMediaType))
                            .build()
                        httpClient.newCall(req2).execute().use { resp2 ->
                            val raw2 = resp2.body?.string().orEmpty()
                            if (!resp2.isSuccessful) {
                                throw IllegalStateException(mapErrorToArabic(raw2, resp2.code))
                            }
                            JSONObject(raw2)
                        }
                    } else {
                        throw IllegalStateException(mapErrorToArabic(raw1, resp1.code))
                    }
                }

                val accessToken = finalJson.getString("access_token")
                val userId = finalJson.getString("user_id")
                val deviceId = finalJson.optString("device_id", "RUNV2_${UUID.randomUUID()}")
                val chosenDisplay = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: localpart

                sessionStore.saveSession(
                    accessToken = accessToken,
                    internalUserId = userId,
                    deviceId = deviceId,
                    displayName = chosenDisplay
                )
                runCatching { updateDisplayName(chosenDisplay) }
                setPresenceOnlineInternal(userId)

                RunAccount(
                    internalUserId = userId,
                    publicHandle = RunConfig.toPublicHandle(userId),
                    displayName = chosenDisplay,
                    deviceId = deviceId,
                    isPinConfigured = false
                )
            }
        }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { executePost("/_matrix/client/v3/logout", JSONObject(), authenticated = true) }
            sessionStore.clearSession()
            roomNameCache.clear()
            roomTopicCache.clear()
            roomAliasCache.clear()
            roomEncryptedCache.clear()
            roomMemberCountCache.clear()
            directPeerCache.clear()
        }
    }

    suspend fun updateDisplayName(newDisplayName: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
            val encodedUser = encodePathSegment(userId)
            val body = JSONObject().put("displayname", newDisplayName.trim())
            executePut("/_matrix/client/v3/profile/$encodedUser/displayname", body)
            sessionStore.updateDisplayName(newDisplayName.trim())
        }
    }

    // =========================================================================
    // 2. PIN KEY BACKUP (Upload & Restore via Account Data)
    // =========================================================================

    suspend fun uploadPinKeyBackup(pin: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
            val payload = pinBackupManager.createEncryptedBackupPayload(pin, userId)
            val encodedUser = encodePathSegment(userId)
            val encodedType = encodePathSegment(RunConfig.ACCOUNT_DATA_KEY_BACKUP_EVENT)
            executePut("/_matrix/client/v3/user/$encodedUser/account_data/$encodedType", payload)
            val digest = pinBackupManager.computePinDigestHex(pin, userId)
            sessionStore.setPinConfigured(true, digest)
        }
    }

    suspend fun restorePinKeyBackup(pin: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
            val encodedUser = encodePathSegment(userId)
            val encodedType = encodePathSegment(RunConfig.ACCOUNT_DATA_KEY_BACKUP_EVENT)
            val payload = executeGet("/_matrix/client/v3/user/$encodedUser/account_data/$encodedType")
            pinBackupManager.restoreFromEncryptedBackupPayload(pin, userId, payload)
        }
    }

    suspend fun checkRemotePinBackupExists(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val userId = sessionStore.getInternalUserId() ?: return@withContext false
            val encodedUser = encodePathSegment(userId)
            val encodedType = encodePathSegment(RunConfig.ACCOUNT_DATA_KEY_BACKUP_EVENT)
            val payload = executeGet("/_matrix/client/v3/user/$encodedUser/account_data/$encodedType")
            payload.has("ciphertext")
        }.getOrDefault(false)
    }

    // =========================================================================
    // 3. REAL-TIME SYNC (Rooms, Messages, Presence, Invites, Call Signaling)
    // =========================================================================

    suspend fun sync(sinceToken: String?, timeoutMs: Int = 15000): Result<SyncResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myUserId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
                val query = buildString {
                    append("/_matrix/client/v3/sync?timeout=")
                    append(if (sinceToken == null) 0 else timeoutMs)
                    if (!sinceToken.isNullOrBlank()) {
                        append("&since=").append(URLEncoder.encode(sinceToken, "UTF-8"))
                    }
                }

                val root = executeGet(query)
                val nextBatch = root.optString("next_batch", sinceToken.orEmpty())
                if (nextBatch.isNotBlank()) {
                    sessionStore.saveSyncBatchToken(nextBatch)
                }

                // 1. Parse Account Data (m.direct map + remote key backup)
                val accountDataEvents = root.optJSONObject("account_data")?.optJSONArray("events")
                if (accountDataEvents != null) {
                    for (i in 0 until accountDataEvents.length()) {
                        val ev = accountDataEvents.getJSONObject(i)
                        if (ev.optString("type") == "m.direct") {
                            val content = ev.optJSONObject("content") ?: continue
                            val peerIter = content.keys()
                            while (peerIter.hasNext()) {
                                val peerUserId = peerIter.next()
                                val roomArr = content.optJSONArray(peerUserId) ?: continue
                                for (rIdx in 0 until roomArr.length()) {
                                    val rId = roomArr.optString(rIdx)
                                    if (rId.isNotBlank()) {
                                        directPeerCache[rId] = peerUserId
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Parse Presence events
                val presenceEvents = root.optJSONObject("presence")?.optJSONArray("events")
                if (presenceEvents != null) {
                    for (i in 0 until presenceEvents.length()) {
                        val ev = presenceEvents.getJSONObject(i)
                        val sender = ev.optString("sender")
                        val pStr = ev.optJSONObject("content")?.optString("presence", "offline")
                        val state = when (pStr) {
                            "online" -> PresenceState.ONLINE
                            "unavailable" -> PresenceState.UNAVAILABLE
                            else -> PresenceState.OFFLINE
                        }
                        if (sender.isNotBlank()) {
                            userPresenceCache[sender] = state
                        }
                    }
                }

                val roomsObj = root.optJSONObject("rooms")
                val joinObj = roomsObj?.optJSONObject("join")
                val inviteObj = roomsObj?.optJSONObject("invite")

                val roomSummaries = mutableListOf<RoomSummary>()
                val newMessagesByRoom = mutableMapOf<String, List<ChatMessage>>()
                val signalingEvents = mutableListOf<SignalingEvent>()
                val outgoingFriendInvites = mutableListOf<FriendRequestItem>()
                val friendsMap = mutableMapOf<String, UserSearchResult>()

                // 3. Parse Joined Rooms
                if (joinObj != null) {
                    val roomKeys = joinObj.keys()
                    while (roomKeys.hasNext()) {
                        val roomId = roomKeys.next()
                        val roomData = joinObj.getJSONObject(roomId)

                        val stateEvents = roomData.optJSONObject("state")?.optJSONArray("events")
                        val timelineEvents = roomData.optJSONObject("timeline")?.optJSONArray("events")
                        val summaryObj = roomData.optJSONObject("summary")
                        val unreadObj = roomData.optJSONObject("unread_notifications")

                        val joinedCount = summaryObj?.optInt("m.joined_member_count", 0) ?: 0
                        val invitedCount = summaryObj?.optInt("m.invited_member_count", 0) ?: 0
                        if (joinedCount + invitedCount > 0) {
                            roomMemberCountCache[roomId] = joinedCount + invitedCount
                        }

                        var invitedOtherUser: String? = null
                        var joinedOtherUser: String? = null

                        fun processStateEvent(ev: JSONObject) {
                            val type = ev.optString("type")
                            val content = ev.optJSONObject("content") ?: return
                            when (type) {
                                "m.room.name" -> {
                                    val n = content.optString("name")
                                    if (n.isNotBlank()) roomNameCache[roomId] = n
                                }
                                "m.room.topic" -> {
                                    val t = content.optString("topic")
                                    if (t.isNotBlank()) roomTopicCache[roomId] = t
                                }
                                "m.room.canonical_alias" -> {
                                    val alias = content.optString("alias")
                                    if (alias.isNotBlank()) roomAliasCache[roomId] = alias
                                }
                                "m.room.encryption" -> {
                                    roomEncryptedCache[roomId] = true
                                    val sharedKey = content.optString("run_shared_key_b64")
                                    if (sharedKey.isNotBlank()) {
                                        pinBackupManager.storeRoomSecretKeyBase64(roomId, sharedKey)
                                    }
                                }
                                "m.runv2.room_key" -> {
                                    val sharedKey = content.optString("key_b64")
                                    if (sharedKey.isNotBlank()) {
                                        pinBackupManager.storeRoomSecretKeyBase64(roomId, sharedKey)
                                    }
                                }
                                "m.room.member" -> {
                                    val stateKey = ev.optString("state_key")
                                    val membership = content.optString("membership")
                                    val displayName = content.optString("displayname")
                                    if (stateKey.isNotBlank() && displayName.isNotBlank()) {
                                        userDisplayNameCache[stateKey] = displayName
                                    }
                                    if (stateKey.isNotBlank() && stateKey != myUserId) {
                                        if (membership == "join") {
                                            joinedOtherUser = stateKey
                                        } else if (membership == "invite") {
                                            invitedOtherUser = stateKey
                                        }
                                        if (content.optBoolean("is_direct", false)) {
                                            directPeerCache[roomId] = stateKey
                                        }
                                    }
                                }
                            }
                        }

                        if (stateEvents != null) {
                            for (i in 0 until stateEvents.length()) {
                                processStateEvent(stateEvents.getJSONObject(i))
                            }
                        }

                        val roomMessages = mutableListOf<ChatMessage>()
                        var lastSenderName: String? = null
                        var lastMessageText = ""
                        var lastMessageTs = 0L

                        if (timelineEvents != null) {
                            for (i in 0 until timelineEvents.length()) {
                                val ev = timelineEvents.getJSONObject(i)
                                if (ev.has("state_key")) {
                                    processStateEvent(ev)
                                }
                                val type = ev.optString("type")
                                val sender = ev.optString("sender")
                                val eventId = ev.optString("event_id", UUID.randomUUID().toString())
                                val ts = ev.optLong("origin_server_ts", System.currentTimeMillis())
                                val content = ev.optJSONObject("content") ?: JSONObject()

                                when (type) {
                                    "m.room.message" -> {
                                        val body = content.optString("body")
                                        if (body.isNotBlank()) {
                                            val senderDisplay = userDisplayNameCache[sender]
                                                ?: RunConfig.toPublicHandle(sender).removePrefix("@")
                                            val msg = ChatMessage(
                                                eventId = eventId,
                                                roomId = roomId,
                                                senderUserId = sender,
                                                senderHandle = RunConfig.toPublicHandle(sender),
                                                senderDisplayName = senderDisplay,
                                                body = body,
                                                timestamp = ts,
                                                isOutgoing = sender == myUserId,
                                                isEncrypted = false
                                            )
                                            roomMessages.add(msg)
                                            lastSenderName = if (sender == myUserId) "أنت" else senderDisplay
                                            lastMessageText = body
                                            lastMessageTs = ts
                                        }
                                    }
                                    RunConfig.ROOM_EVENT_ENCRYPTED -> {
                                        roomEncryptedCache[roomId] = true
                                        val (decryptedBody, failed) = pinBackupManager.decryptRoomMessage(roomId, content)
                                        val senderDisplay = userDisplayNameCache[sender]
                                            ?: RunConfig.toPublicHandle(sender).removePrefix("@")
                                        val msg = ChatMessage(
                                            eventId = eventId,
                                            roomId = roomId,
                                            senderUserId = sender,
                                            senderHandle = RunConfig.toPublicHandle(sender),
                                            senderDisplayName = senderDisplay,
                                            body = decryptedBody,
                                            timestamp = ts,
                                            isOutgoing = sender == myUserId,
                                            isEncrypted = true,
                                            decryptionFailed = failed
                                        )
                                        roomMessages.add(msg)
                                        lastSenderName = if (sender == myUserId) "أنت" else senderDisplay
                                        lastMessageText = decryptedBody
                                        lastMessageTs = ts
                                    }
                                    "m.call.invite", "m.call.answer", "m.call.candidates", "m.call.hangup" -> {
                                        // Only process recent call signaling events (< 60s old) from remote peer
                                        val ageMs = System.currentTimeMillis() - ts
                                        if (sender != myUserId && ageMs < 60_000L) {
                                            val callId = content.optString("call_id")
                                            val sdpObj = content.optJSONObject("offer")
                                                ?: content.optJSONObject("answer")
                                            val sdp = sdpObj?.optString("sdp")
                                            val isVideo = sdp?.contains("m=video") ?: true
                                            val candList = mutableListOf<IceCandidatePayload>()
                                            val candArr = content.optJSONArray("candidates")
                                            if (candArr != null) {
                                                for (cIdx in 0 until candArr.length()) {
                                                    val cObj = candArr.getJSONObject(cIdx)
                                                    candList.add(
                                                        IceCandidatePayload(
                                                            sdpMid = cObj.optString("sdpMid", "0"),
                                                            sdpMLineIndex = cObj.optInt("sdpMLineIndex", 0),
                                                            candidate = cObj.optString("candidate")
                                                        )
                                                    )
                                                }
                                            }
                                            signalingEvents.add(
                                                SignalingEvent(
                                                    type = type,
                                                    roomId = roomId,
                                                    senderUserId = sender,
                                                    callId = callId,
                                                    sdp = sdp,
                                                    candidates = candList,
                                                    isVideo = isVideo
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (roomMessages.isNotEmpty()) {
                            newMessagesByRoom[roomId] = roomMessages
                        }

                        val heroes = summaryObj?.optJSONArray("m.heroes")
                        val heroUserId = if (heroes != null && heroes.length() > 0) heroes.optString(0) else null
                        val totalMembers = (roomMemberCountCache[roomId] ?: 1).coerceAtLeast(1)

                        val directPeer = directPeerCache[roomId]
                            ?: heroUserId?.takeIf { totalMembers <= 2 && roomNameCache[roomId].isNullOrBlank() }
                            ?: joinedOtherUser?.takeIf { totalMembers <= 2 && roomNameCache[roomId].isNullOrBlank() }
                            ?: invitedOtherUser?.takeIf { totalMembers <= 2 && roomNameCache[roomId].isNullOrBlank() }

                        val isDirectRoom = directPeer != null
                        val resolvedName = roomNameCache[roomId]
                            ?: directPeer?.let { userDisplayNameCache[it] ?: RunConfig.toPublicHandle(it).removePrefix("@") }
                            ?: RunConfig.toPublicRoomCode(roomAliasCache[roomId]).ifBlank { "غرفة Run" }

                        val roomCode = if (isDirectRoom && directPeer != null) {
                            RunConfig.toPublicHandle(directPeer)
                        } else {
                            RunConfig.toPublicRoomCode(roomAliasCache[roomId] ?: roomId)
                        }

                        val peerPresence = directPeer?.let { userPresenceCache[it] ?: PresenceState.OFFLINE }
                            ?: PresenceState.OFFLINE

                        // Track outgoing pending friend invites vs active friends
                        if (isDirectRoom && directPeer != null) {
                            val peerDisplay = userDisplayNameCache[directPeer]
                                ?: RunConfig.toPublicHandle(directPeer).removePrefix("@")
                            if (invitedOtherUser == directPeer && joinedOtherUser == null) {
                                outgoingFriendInvites.add(
                                    FriendRequestItem(
                                        roomId = roomId,
                                        internalUserId = directPeer,
                                        publicHandle = RunConfig.toPublicHandle(directPeer),
                                        displayName = peerDisplay,
                                        isIncoming = false,
                                        presence = peerPresence,
                                        statusText = "بانتظار القبول",
                                        avatarSeedColor = AvatarPalette.colorFor(directPeer)
                                    )
                                )
                            } else {
                                friendsMap[directPeer] = UserSearchResult(
                                    internalUserId = directPeer,
                                    publicHandle = RunConfig.toPublicHandle(directPeer),
                                    displayName = peerDisplay,
                                    presence = peerPresence,
                                    statusText = if (peerPresence == PresenceState.ONLINE) "متصل الآن" else "حساب Run",
                                    avatarSeedColor = AvatarPalette.colorFor(directPeer),
                                    relationState = FriendRelationState.FRIEND
                                )
                            }
                        }

                        val unreadCount = unreadObj?.optInt("notification_count", 0) ?: 0

                        roomSummaries.add(
                            RoomSummary(
                                roomId = roomId,
                                name = resolvedName,
                                topic = roomTopicCache[roomId].orEmpty(),
                                roomCode = roomCode,
                                isDirect = isDirectRoom,
                                directPeerUserId = directPeer,
                                memberCount = totalMembers,
                                onlineCount = if (peerPresence == PresenceState.ONLINE) 2 else 1,
                                lastMessageSender = lastSenderName,
                                lastMessageBody = lastMessageText.ifBlank {
                                    if (isDirectRoom) "محادثة خاصة" else "$totalMembers عضوًا"
                                },
                                lastMessageTimestamp = lastMessageTs,
                                unreadCount = unreadCount,
                                isEncrypted = roomEncryptedCache[roomId] ?: false,
                                avatarSeedColor = AvatarPalette.colorFor(directPeer ?: roomId),
                                peerPresence = peerPresence
                            )
                        )
                    }
                }

                // 4. Parse Incoming Invites (Friend requests & Room invitations)
                val incomingInvites = mutableListOf<FriendRequestItem>()
                if (inviteObj != null) {
                    val inviteKeys = inviteObj.keys()
                    while (inviteKeys.hasNext()) {
                        val roomId = inviteKeys.next()
                        val inviteRoom = inviteObj.getJSONObject(roomId)
                        val events = inviteRoom.optJSONObject("invite_state")?.optJSONArray("events")
                        var inviterUserId = ""
                        var inviterDisplay = ""
                        var roomName = ""
                        if (events != null) {
                            for (i in 0 until events.length()) {
                                val ev = events.getJSONObject(i)
                                val type = ev.optString("type")
                                val content = ev.optJSONObject("content") ?: continue
                                if (type == "m.room.name") {
                                    roomName = content.optString("name")
                                } else if (type == "m.room.member") {
                                    val sender = ev.optString("sender")
                                    if (sender.isNotBlank() && sender != myUserId) {
                                        inviterUserId = sender
                                        inviterDisplay = content.optString("displayname")
                                    }
                                }
                            }
                        }
                        if (inviterUserId.isNotBlank()) {
                            val handle = RunConfig.toPublicHandle(inviterUserId)
                            val dName = roomName.ifBlank {
                                inviterDisplay.ifBlank { handle.removePrefix("@") }
                            }
                            incomingInvites.add(
                                FriendRequestItem(
                                    roomId = roomId,
                                    internalUserId = inviterUserId,
                                    publicHandle = handle,
                                    displayName = dName,
                                    isIncoming = true,
                                    presence = userPresenceCache[inviterUserId] ?: PresenceState.OFFLINE,
                                    statusText = "دعوة واردة",
                                    avatarSeedColor = AvatarPalette.colorFor(inviterUserId)
                                )
                            )
                        }
                    }
                }

                SyncResult(
                    nextBatch = nextBatch,
                    rooms = roomSummaries.sortedByDescending { it.lastMessageTimestamp },
                    incomingFriendInvites = incomingInvites,
                    outgoingFriendInvites = outgoingFriendInvites,
                    friends = friendsMap.values.toList(),
                    newMessagesByRoom = newMessagesByRoom,
                    signalingEvents = signalingEvents
                )
            }
        }

    // =========================================================================
    // 4. ROOMS, MESSAGES, CREATION, MEMBERS & INVITE DEEP LINKS
    // =========================================================================

    suspend fun fetchRoomMessages(roomId: String, limit: Int = 60): Result<List<ChatMessage>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myUserId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
                val encodedRoom = encodePathSegment(roomId)
                val resp = executeGet("/_matrix/client/v3/rooms/$encodedRoom/messages?dir=b&limit=$limit")
                val chunk = resp.optJSONArray("chunk") ?: JSONArray()
                val result = mutableListOf<ChatMessage>()

                for (i in chunk.length() - 1 downTo 0) {
                    val ev = chunk.getJSONObject(i)
                    val type = ev.optString("type")
                    val sender = ev.optString("sender")
                    val eventId = ev.optString("event_id", UUID.randomUUID().toString())
                    val ts = ev.optLong("origin_server_ts", System.currentTimeMillis())
                    val content = ev.optJSONObject("content") ?: JSONObject()
                    val senderDisplay = userDisplayNameCache[sender]
                        ?: RunConfig.toPublicHandle(sender).removePrefix("@")

                    when (type) {
                        "m.room.message" -> {
                            val body = content.optString("body")
                            if (body.isNotBlank()) {
                                result.add(
                                    ChatMessage(
                                        eventId = eventId,
                                        roomId = roomId,
                                        senderUserId = sender,
                                        senderHandle = RunConfig.toPublicHandle(sender),
                                        senderDisplayName = senderDisplay,
                                        body = body,
                                        timestamp = ts,
                                        isOutgoing = sender == myUserId,
                                        isEncrypted = false
                                    )
                                )
                            }
                        }
                        RunConfig.ROOM_EVENT_ENCRYPTED -> {
                            val (decrypted, failed) = pinBackupManager.decryptRoomMessage(roomId, content)
                            result.add(
                                ChatMessage(
                                    eventId = eventId,
                                    roomId = roomId,
                                    senderUserId = sender,
                                    senderHandle = RunConfig.toPublicHandle(sender),
                                    senderDisplayName = senderDisplay,
                                    body = decrypted,
                                    timestamp = ts,
                                    isOutgoing = sender == myUserId,
                                    isEncrypted = true,
                                    decryptionFailed = failed
                                )
                            )
                        }
                    }
                }
                result
            }
        }

    suspend fun sendRoomMessage(roomId: String, text: String, encryptWithRoomKey: Boolean = false): Result<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myUserId = sessionStore.getInternalUserId() ?: error("غير مسجل الدخول")
                val cleanText = text.trim()
                require(cleanText.isNotEmpty()) { "الرسالة فارغة" }

                val encodedRoom = encodePathSegment(roomId)
                val txnId = "runv2_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"

                val eventId = if (encryptWithRoomKey) {
                    val encPayload = pinBackupManager.encryptRoomMessage(roomId, cleanText)
                    val resp = executePut(
                        "/_matrix/client/v3/rooms/$encodedRoom/send/${RunConfig.ROOM_EVENT_ENCRYPTED}/$txnId",
                        encPayload
                    )
                    resp.optString("event_id", txnId)
                } else {
                    val msgPayload = JSONObject().apply {
                        put("msgtype", "m.text")
                        put("body", cleanText)
                    }
                    val resp = executePut(
                        "/_matrix/client/v3/rooms/$encodedRoom/send/m.room.message/$txnId",
                        msgPayload
                    )
                    resp.optString("event_id", txnId)
                }

                ChatMessage(
                    eventId = eventId,
                    roomId = roomId,
                    senderUserId = myUserId,
                    senderHandle = RunConfig.toPublicHandle(myUserId),
                    senderDisplayName = sessionStore.getDisplayName(),
                    body = cleanText,
                    timestamp = System.currentTimeMillis(),
                    isOutgoing = true,
                    isEncrypted = encryptWithRoomKey,
                    isDelivered = true
                )
            }
        }

    suspend fun createGroupRoom(
        name: String,
        topic: String,
        invitedHandlesOrIds: List<String>
    ): Result<RoomSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty()) { "يرجى إدخال اسم الغرفة" }

            val codeNum = (1000..9999).random()
            val aliasPrefix = cleanName
                .filter { it.isLetterOrDigit() && it.code < 128 }
                .uppercase()
                .take(4)
                .ifEmpty { "RUN" }
            val permanentCode = "$aliasPrefix-$codeNum"
            val aliasLocalpart = permanentCode.lowercase()

            val inviteUserIds = invitedHandlesOrIds.map { RunConfig.toInternalUserId(it) }.distinct()
            val tempRoomKeyB64 = android.util.Base64.encodeToString(
                ByteArray(32).also { java.security.SecureRandom().nextBytes(it) },
                android.util.Base64.NO_WRAP
            )

            val initialState = JSONArray()

            val payload = JSONObject().apply {
                put("name", cleanName)
                if (topic.isNotBlank()) put("topic", topic.trim())
                put("room_alias_name", aliasLocalpart)
                put("visibility", "public")
                put("preset", "public_chat")
                if (inviteUserIds.isNotEmpty()) {
                    put("invite", JSONArray(inviteUserIds))
                }
                put("initial_state", initialState)
            }

            val resp = executePost("/_matrix/client/v3/createRoom", payload)
            val roomId = resp.getString("room_id")
            pinBackupManager.storeRoomSecretKeyBase64(roomId, tempRoomKeyB64)

            roomNameCache[roomId] = cleanName
            roomTopicCache[roomId] = topic.trim()
            roomAliasCache[roomId] = "#$aliasLocalpart:${RunConfig.SERVER_DOMAIN}"
            roomEncryptedCache[roomId] = false

            RoomSummary(
                roomId = roomId,
                name = cleanName,
                topic = topic.trim(),
                roomCode = permanentCode,
                isDirect = false,
                directPeerUserId = null,
                memberCount = inviteUserIds.size + 1,
                onlineCount = 1,
                lastMessageSender = "أنت",
                lastMessageBody = "تم إنشاء الغرفة · الكود $permanentCode",
                lastMessageTimestamp = System.currentTimeMillis(),
                unreadCount = 0,
                isEncrypted = false,
                avatarSeedColor = AvatarPalette.colorFor(roomId)
            )
        }
    }

    suspend fun createOrOpenDirectChat(targetHandleOrUserId: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val targetUserId = RunConfig.toInternalUserId(targetHandleOrUserId)
                // Check if we already have a direct room cached
                val existingRoom = directPeerCache.entries.firstOrNull { it.value == targetUserId }?.key
                if (existingRoom != null) {
                    return@runCatching existingRoom
                }

                val tempRoomKeyB64 = android.util.Base64.encodeToString(
                    ByteArray(32).also { java.security.SecureRandom().nextBytes(it) },
                    android.util.Base64.NO_WRAP
                )

                val payload = JSONObject().apply {
                    put("is_direct", true)
                    put("preset", "trusted_private_chat")
                    put("invite", JSONArray().put(targetUserId))
                }

                val resp = executePost("/_matrix/client/v3/createRoom", payload)
                val roomId = resp.getString("room_id")
                directPeerCache[roomId] = targetUserId
                pinBackupManager.storeRoomSecretKeyBase64(roomId, tempRoomKeyB64)
                roomId
            }
        }

    suspend fun joinRoomByCodeOrId(codeOrRoomIdOrLink: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = RunConfig.toInternalRoomAliasOrId(codeOrRoomIdOrLink)
                val encoded = encodePathSegment(target)
                val resp = executePost("/_matrix/client/v3/join/$encoded", JSONObject())
                resp.optString("room_id", target)
            }
        }

    suspend fun leaveOrDeclineRoom(roomId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encoded = encodePathSegment(roomId)
                executePost("/_matrix/client/v3/rooms/$encoded/leave", JSONObject())
                Unit
            }
        }

    suspend fun fetchRoomMembers(roomId: String): Result<List<RoomMember>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val myUserId = sessionStore.getInternalUserId().orEmpty()
                val encoded = encodePathSegment(roomId)
                val resp = executeGet("/_matrix/client/v3/rooms/$encoded/members")
                val chunk = resp.optJSONArray("chunk") ?: JSONArray()
                val members = mutableListOf<RoomMember>()

                for (i in 0 until chunk.length()) {
                    val ev = chunk.getJSONObject(i)
                    val userId = ev.optString("state_key")
                    val content = ev.optJSONObject("content") ?: continue
                    val membership = content.optString("membership")
                    if (membership != "join" && membership != "invite") continue

                    val handle = RunConfig.toPublicHandle(userId)
                    val displayName = content.optString("displayname")
                        .ifBlank { handle.removePrefix("@") }
                    val role = when {
                        userId == myUserId -> "أنت · عضو"
                        membership == "invite" -> "مدعو"
                        else -> "عضو"
                    }
                    members.add(
                        RoomMember(
                            userId = userId,
                            publicHandle = handle,
                            displayName = displayName,
                            roleLabel = role,
                            membership = membership,
                            presence = userPresenceCache[userId] ?: PresenceState.OFFLINE,
                            avatarSeedColor = AvatarPalette.colorFor(userId)
                        )
                    )
                }
                members
            }
        }

    // =========================================================================
    // 5. SEARCH (Friends by @username & Display Name, Public Rooms by Name & Code)
    // =========================================================================

    suspend fun searchUsers(
        query: String,
        knownFriends: Set<String>,
        outgoingPending: Set<String>
    ): Result<List<UserSearchResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val myUserId = sessionStore.getInternalUserId().orEmpty()
            val cleanQuery = query.trim().removePrefix("run://friend/")
            if (cleanQuery.isBlank()) return@runCatching emptyList()

            val resultsMap = linkedMapOf<String, UserSearchResult>()

            // 1. User directory search (matches both display name and @username)
            val dirBody = JSONObject().apply {
                put("search_term", cleanQuery.removePrefix("@"))
                put("limit", 25)
            }
            runCatching {
                val dirResp = executePost("/_matrix/client/v3/user_directory/search", dirBody)
                val arr = dirResp.optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val u = arr.getJSONObject(i)
                    val userId = u.optString("user_id")
                    if (userId.isBlank() || userId == myUserId) continue
                    val handle = RunConfig.toPublicHandle(userId)
                    val dName = u.optString("display_name").ifBlank { handle.removePrefix("@") }
                    val presence = fetchUserPresenceInternal(userId)
                    val relation = when {
                        knownFriends.contains(userId) -> FriendRelationState.FRIEND
                        outgoingPending.contains(userId) -> FriendRelationState.OUTGOING_PENDING
                        else -> FriendRelationState.NONE
                    }
                    resultsMap[userId] = UserSearchResult(
                        internalUserId = userId,
                        publicHandle = handle,
                        displayName = dName,
                        presence = presence,
                        statusText = if (presence == PresenceState.ONLINE) "متصل الآن" else "حساب Run",
                        avatarSeedColor = AvatarPalette.colorFor(userId),
                        relationState = relation
                    )
                }
            }

            // 2. Direct @username profile lookup on the server if user typed a specific handle
            val candidateUserId = RunConfig.toInternalUserId(cleanQuery)
            if (candidateUserId != myUserId && !resultsMap.containsKey(candidateUserId)) {
                val profileName = fetchDisplayNameInternal(candidateUserId)
                if (profileName != null) {
                    val handle = RunConfig.toPublicHandle(candidateUserId)
                    val presence = fetchUserPresenceInternal(candidateUserId)
                    val relation = when {
                        knownFriends.contains(candidateUserId) -> FriendRelationState.FRIEND
                        outgoingPending.contains(candidateUserId) -> FriendRelationState.OUTGOING_PENDING
                        else -> FriendRelationState.NONE
                    }
                    resultsMap[candidateUserId] = UserSearchResult(
                        internalUserId = candidateUserId,
                        publicHandle = handle,
                        displayName = profileName.ifBlank { handle.removePrefix("@") },
                        presence = presence,
                        statusText = if (presence == PresenceState.ONLINE) "متصل الآن" else "حساب Run",
                        avatarSeedColor = AvatarPalette.colorFor(candidateUserId),
                        relationState = relation
                    )
                }
            }

            resultsMap.values.toList()
        }
    }

    suspend fun searchPublicRooms(
        query: String,
        joinedRoomIds: Set<String>
    ): Result<List<PublicRoomResult>> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanQuery = query.trim().removePrefix("run://join/")
            val results = mutableListOf<PublicRoomResult>()
            val seenIds = mutableSetOf<String>()

            // 1. If query looks like a room code (e.g. PHY-D5), resolve alias directly as well
            if (cleanQuery.isNotBlank() && !cleanQuery.contains(" ")) {
                val alias = RunConfig.toInternalRoomAliasOrId(cleanQuery)
                if (alias.startsWith("#")) {
                    runCatching {
                        val encodedAlias = encodePathSegment(alias)
                        val aliasResp = executeGet("/_matrix/client/v3/directory/room/$encodedAlias")
                        val resolvedRoomId = aliasResp.optString("room_id")
                        if (resolvedRoomId.isNotBlank()) {
                            seenIds.add(resolvedRoomId)
                            results.add(
                                PublicRoomResult(
                                    roomId = resolvedRoomId,
                                    name = roomNameCache[resolvedRoomId] ?: cleanQuery.uppercase(),
                                    topic = roomTopicCache[resolvedRoomId].orEmpty(),
                                    roomCode = RunConfig.toPublicRoomCode(alias),
                                    memberCount = roomMemberCountCache[resolvedRoomId] ?: 1,
                                    isJoined = joinedRoomIds.contains(resolvedRoomId),
                                    avatarSeedColor = AvatarPalette.colorFor(resolvedRoomId)
                                )
                            )
                        }
                    }
                }
            }

            // 2. Query /publicRooms directory with generic search filter
            val body = JSONObject().apply {
                put("limit", 30)
                if (cleanQuery.isNotBlank()) {
                    put("filter", JSONObject().put("generic_search_term", cleanQuery))
                }
            }
            val resp = executePost("/_matrix/client/v3/publicRooms", body)
            val chunk = resp.optJSONArray("chunk") ?: JSONArray()
            for (i in 0 until chunk.length()) {
                val obj = chunk.getJSONObject(i)
                val rId = obj.optString("room_id")
                if (rId.isBlank() || seenIds.contains(rId)) continue
                val name = obj.optString("name")
                val canonicalAlias = obj.optString("canonical_alias")
                val code = RunConfig.toPublicRoomCode(canonicalAlias.ifBlank { rId })
                val topic = obj.optString("topic")
                val members = obj.optInt("num_joined_members", 1)

                results.add(
                    PublicRoomResult(
                        roomId = rId,
                        name = name.ifBlank { code },
                        topic = topic,
                        roomCode = code,
                        memberCount = members,
                        isJoined = joinedRoomIds.contains(rId),
                        avatarSeedColor = AvatarPalette.colorFor(rId)
                    )
                )
            }

            results
        }
    }

    // =========================================================================
    // 6. WEBRTC CALL SIGNALING & TURN CREDENTIALS
    // =========================================================================

    suspend fun fetchTurnCredentials(): Result<TurnCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val resp = executeGet("/_matrix/client/v3/voip/turnServer")
            val urisArr = resp.optJSONArray("uris") ?: JSONArray()
            val uris = buildList {
                for (i in 0 until urisArr.length()) {
                    val u = urisArr.optString(i)
                    if (u.isNotBlank()) add(u)
                }
            }
            TurnCredentials(
                uris = uris,
                username = resp.optString("username"),
                password = resp.optString("password"),
                ttlSeconds = resp.optInt("ttl", 86400)
            )
        }
    }

    suspend fun sendCallInvite(roomId: String, callId: String, offerSdp: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedRoom = encodePathSegment(roomId)
                val txnId = "call_inv_${System.currentTimeMillis()}"
                val payload = JSONObject().apply {
                    put("call_id", callId)
                    put("version", 0)
                    put("lifetime", 60000)
                    put("offer", JSONObject().apply {
                        put("type", "offer")
                        put("sdp", offerSdp)
                    })
                }
                executePut("/_matrix/client/v3/rooms/$encodedRoom/send/m.call.invite/$txnId", payload)
                Unit
            }
        }

    suspend fun sendCallAnswer(roomId: String, callId: String, answerSdp: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedRoom = encodePathSegment(roomId)
                val txnId = "call_ans_${System.currentTimeMillis()}"
                val payload = JSONObject().apply {
                    put("call_id", callId)
                    put("version", 0)
                    put("answer", JSONObject().apply {
                        put("type", "answer")
                        put("sdp", answerSdp)
                    })
                }
                executePut("/_matrix/client/v3/rooms/$encodedRoom/send/m.call.answer/$txnId", payload)
                Unit
            }
        }

    suspend fun sendCallCandidates(
        roomId: String,
        callId: String,
        candidates: List<IceCandidatePayload>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (candidates.isEmpty()) return@runCatching
            val encodedRoom = encodePathSegment(roomId)
            val txnId = "call_cand_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(4)}"
            val arr = JSONArray()
            candidates.forEach { c ->
                arr.put(
                    JSONObject().apply {
                        put("sdpMid", c.sdpMid)
                        put("sdpMLineIndex", c.sdpMLineIndex)
                        put("candidate", c.candidate)
                    }
                )
            }
            val payload = JSONObject().apply {
                put("call_id", callId)
                put("version", 0)
                put("candidates", arr)
            }
            executePut("/_matrix/client/v3/rooms/$encodedRoom/send/m.call.candidates/$txnId", payload)
            Unit
        }
    }

    suspend fun sendCallHangup(roomId: String, callId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val encodedRoom = encodePathSegment(roomId)
                val txnId = "call_hup_${System.currentTimeMillis()}"
                val payload = JSONObject().apply {
                    put("call_id", callId)
                    put("version", 0)
                    put("reason", "user_hangup")
                }
                executePut("/_matrix/client/v3/rooms/$encodedRoom/send/m.call.hangup/$txnId", payload)
                Unit
            }
        }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private fun fetchDisplayNameInternal(userId: String): String? {
        return runCatching {
            val encoded = encodePathSegment(userId)
            val obj = executeGet("/_matrix/client/v3/profile/$encoded/displayname")
            obj.optString("displayname").ifBlank { RunConfig.toPublicHandle(userId).removePrefix("@") }
        }.getOrNull()
    }

    private fun fetchUserPresenceInternal(userId: String): PresenceState {
        return runCatching {
            val encoded = encodePathSegment(userId)
            val obj = executeGet("/_matrix/client/v3/presence/$encoded/status")
            val p = obj.optString("presence", "offline")
            val state = when (p) {
                "online" -> PresenceState.ONLINE
                "unavailable" -> PresenceState.UNAVAILABLE
                else -> PresenceState.OFFLINE
            }
            userPresenceCache[userId] = state
            state
        }.getOrElse { userPresenceCache[userId] ?: PresenceState.OFFLINE }
    }

    private fun setPresenceOnlineInternal(userId: String) {
        runCatching {
            val encoded = encodePathSegment(userId)
            val body = JSONObject().apply {
                put("presence", "online")
                put("status_msg", "متصل")
            }
            executePut("/_matrix/client/v3/presence/$encoded/status", body)
        }
    }

    private fun executeGet(pathAndQuery: String, authenticated: Boolean = true): JSONObject {
        val builder = Request.Builder()
            .url("${RunConfig.HOMESERVER_BASE_URL}$pathAndQuery")
            .get()
        if (authenticated) {
            val token = sessionStore.getAccessToken() ?: error("الجلسة غير متوفرة")
            builder.header("Authorization", "Bearer $token")
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(mapErrorToArabic(raw, resp.code))
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun executePost(path: String, body: JSONObject, authenticated: Boolean = true): JSONObject {
        val builder = Request.Builder()
            .url("${RunConfig.HOMESERVER_BASE_URL}$path")
            .post(body.toString().toRequestBody(jsonMediaType))
        if (authenticated) {
            val token = sessionStore.getAccessToken() ?: error("الجلسة غير متوفرة")
            builder.header("Authorization", "Bearer $token")
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(mapErrorToArabic(raw, resp.code))
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun executePut(path: String, body: JSONObject, authenticated: Boolean = true): JSONObject {
        val builder = Request.Builder()
            .url("${RunConfig.HOMESERVER_BASE_URL}$path")
            .put(body.toString().toRequestBody(jsonMediaType))
        if (authenticated) {
            val token = sessionStore.getAccessToken() ?: error("الجلسة غير متوفرة")
            builder.header("Authorization", "Bearer $token")
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw IllegalStateException(mapErrorToArabic(raw, resp.code))
            }
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        }
    }

    private fun encodePathSegment(segment: String): String {
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

    /**
     * Maps backend error responses to clean Arabic messages without exposing
     * "Matrix", domain names, or protocol terms.
     */
    private fun mapErrorToArabic(rawResponse: String, httpCode: Int): String {
        val errCode = runCatching { JSONObject(rawResponse).optString("errcode") }.getOrDefault("")
        return when {
            errCode == "M_FORBIDDEN" || httpCode == 403 -> "اسم المستخدم أو كلمة المرور غير صحيحة"
            errCode == "M_USER_IN_USE" -> "اسم المستخدم هذا محجوز بالفعل، اختر اسمًا آخر"
            errCode == "M_INVALID_USERNAME" -> "اسم المستخدم يحتوي على رموز غير مسموحة (استخدم حروفًا إنجليزية صغيرة وأرقامًا)"
            errCode == "M_ROOM_IN_USE" -> "كود الغرفة مستخدم بالفعل"
            errCode == "M_NOT_FOUND" || httpCode == 404 -> "لم يتم العثور على الحساب أو الغرفة المطلوبة"
            errCode == "M_LIMIT_EXCEEDED" || httpCode == 429 -> "محاولات كثيرة متتالية، يرجى الانتظار قليلاً ثم المحاولة"
            httpCode in 500..599 -> "الخدمة مشغولة حاليًا، يرجى المحاولة بعد لحظات"
            else -> "تعذر إتمام العملية ($httpCode)"
        }
    }
}
