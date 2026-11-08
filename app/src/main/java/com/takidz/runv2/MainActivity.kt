package com.takidz.runv2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.takidz.runv2.ui.AppScreen
import com.takidz.runv2.ui.MainTab
import com.takidz.runv2.ui.RunUiState
import com.takidz.runv2.ui.RunViewModel
import com.takidz.runv2.ui.screens.AccountScreen
import com.takidz.runv2.ui.screens.ActiveCallScreen
import com.takidz.runv2.ui.screens.CallsScreen
import com.takidz.runv2.ui.screens.ChatScreen
import com.takidz.runv2.ui.screens.CreateRoomScreen
import com.takidz.runv2.ui.screens.LoginScreen
import com.takidz.runv2.ui.screens.PinRestoreSuccessScreen
import com.takidz.runv2.ui.screens.PinScreen
import com.takidz.runv2.ui.screens.RoomInfoScreen
import com.takidz.runv2.ui.screens.RoomsScreen
import com.takidz.runv2.ui.screens.SearchScreen
import com.takidz.runv2.ui.theme.LocalRunColors
import com.takidz.runv2.ui.theme.RunV2Theme

/**
 * MainActivity for Run v2 (`com.takidz.runv2`).
 * - Handles `run://join/<code>` and `run://friend/<handle>` deep links on launch and `onNewIntent`.
 * - Requests Camera & Microphone permissions when initiating or answering WebRTC calls.
 * - Renders the single-column Design B layout with fixed bottom tabs: Rooms / Calls / Account.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: RunViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.dataString?.let { uriStr ->
            viewModel.handleIncomingDeepLink(uriStr)
        }

        setContent {
            val state by viewModel.uiState.collectAsState()
            RunV2Theme(themeMode = state.themeMode) {
                RunV2RootApp(viewModel = viewModel, state = state)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.dataString?.let { uriStr ->
            viewModel.handleIncomingDeepLink(uriStr)
        }
    }
}

@Composable
fun RunV2RootApp(
    viewModel: RunViewModel,
    state: RunUiState
) {
    val colors = LocalRunColors.current
    val context = LocalContext.current

    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed after permission prompt
    }

    fun ensureCallPermissions(needCamera: Boolean, action: () -> Unit) {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (needCamera) add(Manifest.permission.CAMERA)
        }
        val missing = required.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) {
            callPermissionsLauncher.launch(required.toTypedArray())
        }
        action()
    }

    // Back button handling across screens
    BackHandler(
        enabled = state.currentScreen != AppScreen.LOGIN &&
                state.currentScreen != AppScreen.MAIN_TABS
    ) {
        when (state.currentScreen) {
            AppScreen.ROOM_INFO -> viewModel.navigateToScreen(AppScreen.CHAT)
            AppScreen.CHAT,
            AppScreen.SEARCH,
            AppScreen.CREATE_ROOM,
            AppScreen.PIN_CREATE,
            AppScreen.PIN_RESTORE,
            AppScreen.PIN_RESTORE_SUCCESS -> viewModel.navigateToScreen(AppScreen.MAIN_TABS)
            AppScreen.ACTIVE_CALL -> viewModel.endActiveCall(sendRemoteHangup = true)
            else -> {}
        }
    }

    // Responsive single-column container (adapts cleanly on phones and larger screens)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Scaffold(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            containerColor = colors.appBg,
            bottomBar = {
                if (state.currentScreen == AppScreen.MAIN_TABS) {
                    FixedBottomNavigationBar(
                        activeTab = state.activeMainTab,
                        unreadTotal = state.rooms.sumOf { it.unreadCount },
                        onSelectTab = { viewModel.selectMainTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (state.currentScreen) {
                    AppScreen.LOGIN -> LoginScreen(
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onLogin = { u, p -> viewModel.loginWithUsername(u, p) },
                        onRegister = { u, p, d -> viewModel.registerWithUsername(u, p, d) }
                    )

                    AppScreen.PIN_CREATE -> PinScreen(
                        isRestoreMode = false,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onPinConfirmed = { pin -> viewModel.createAndUploadPinBackup(pin) },
                        onSkip = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                    )

                    AppScreen.PIN_RESTORE -> PinScreen(
                        isRestoreMode = true,
                        isLoading = state.isLoading,
                        errorMessage = state.errorMessage,
                        onPinConfirmed = { pin -> viewModel.restorePinBackup(pin) },
                        onSkip = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                    )

                    AppScreen.PIN_RESTORE_SUCCESS -> PinRestoreSuccessScreen(
                        onOpenRooms = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                    )

                    AppScreen.MAIN_TABS -> {
                        when (state.activeMainTab) {
                            MainTab.ROOMS -> RoomsScreen(
                                rooms = state.rooms,
                                selectedSpaceFilter = state.selectedSpaceFilter,
                                onSelectSpaceFilter = { viewModel.selectSpaceFilter(it) },
                                onOpenSearch = { viewModel.navigateToScreen(AppScreen.SEARCH) },
                                onOpenCreateRoom = { viewModel.navigateToScreen(AppScreen.CREATE_ROOM) },
                                onOpenRoom = { room -> viewModel.openRoomChat(room) }
                            )

                            MainTab.CALLS -> CallsScreen(
                                callLogs = state.callLogs,
                                callQualityMode = state.callQualityMode,
                                turnStatusMessage = state.turnStatusMessage,
                                onCallAgain = { roomId, isVideo ->
                                    val targetRoom = state.rooms.firstOrNull { it.roomId == roomId }
                                    if (targetRoom != null) {
                                        viewModel.openRoomChat(targetRoom)
                                        ensureCallPermissions(needCamera = isVideo) {
                                            viewModel.startCallInActiveRoom(isVideoRequested = isVideo)
                                        }
                                    }
                                }
                            )

                            MainTab.ACCOUNT -> AccountScreen(
                                account = state.account,
                                themeMode = state.themeMode,
                                callQualityMode = state.callQualityMode,
                                onOpenPinCreate = { viewModel.navigateToScreen(AppScreen.PIN_CREATE) },
                                onOpenPinRestore = { viewModel.navigateToScreen(AppScreen.PIN_RESTORE) },
                                onSelectThemeMode = { viewModel.setThemeMode(it) },
                                onSelectCallQualityMode = { viewModel.setCallQualityMode(it) },
                                onLogout = { viewModel.logout() }
                            )
                        }
                    }

                    AppScreen.SEARCH -> SearchScreen(
                        myPublicHandle = state.account?.publicHandle ?: "@user",
                        searchQuery = state.searchQuery,
                        activeTab = state.activeSearchTab,
                        isSearching = state.isSearching,
                        friendResults = state.friendSearchResults,
                        publicRoomResults = state.publicRoomResults,
                        incomingRequests = state.incomingFriendRequests,
                        outgoingRequests = state.outgoingFriendRequests,
                        friends = state.friends,
                        onQueryChange = { viewModel.updateSearchQuery(it) },
                        onSelectTab = { viewModel.selectSearchTab(it) },
                        onAddFriend = { viewModel.sendFriendRequest(it) },
                        onAcceptRequest = { req -> viewModel.joinRoomByCodeOrId(req.roomId, openAfterJoin = true) },
                        onDeclineRequest = { req -> viewModel.declineIncomingInvite(req.roomId) },
                        onOpenFriendChat = { friend ->
                            viewModel.openOrStartDirectChatWithUser(friend.publicHandle, friend.displayName)
                        },
                        onJoinPublicRoom = { pubRoom ->
                            viewModel.joinRoomByCodeOrId(pubRoom.roomId, openAfterJoin = true)
                        },
                        onBack = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                    )

                    AppScreen.CREATE_ROOM -> CreateRoomScreen(
                        isLoading = state.isLoading,
                        knownFriends = state.friends,
                        searchResults = state.friendSearchResults,
                        onSearchMembers = { q -> viewModel.updateSearchQuery(q) },
                        onCreateRoom = { name, topic, handles ->
                            viewModel.createNewGroupRoom(name, topic, handles)
                        },
                        onBack = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                    )

                    AppScreen.CHAT -> {
                        val activeRoom = state.activeRoom
                        if (activeRoom != null) {
                            ChatScreen(
                                room = activeRoom,
                                messages = state.activeRoomMessages,
                                onSendMessage = { text -> viewModel.sendChatMessage(text) },
                                onOpenRoomInfo = { viewModel.openRoomInfo() },
                                onStartVoiceCall = {
                                    ensureCallPermissions(needCamera = false) {
                                        viewModel.startCallInActiveRoom(isVideoRequested = false)
                                    }
                                },
                                onStartVideoCall = {
                                    ensureCallPermissions(needCamera = true) {
                                        viewModel.startCallInActiveRoom(isVideoRequested = true)
                                    }
                                },
                                onBack = { viewModel.navigateToScreen(AppScreen.MAIN_TABS) }
                            )
                        }
                    }

                    AppScreen.ROOM_INFO -> {
                        val activeRoom = state.activeRoom
                        if (activeRoom != null) {
                            RoomInfoScreen(
                                room = activeRoom,
                                members = state.activeRoomMembers,
                                devices = state.activeRoomDevices,
                                onVerifyDevice = { dev -> viewModel.markDeviceVerified(dev) },
                                onBack = { viewModel.navigateToScreen(AppScreen.CHAT) }
                            )
                        }
                    }

                    AppScreen.ACTIVE_CALL -> ActiveCallScreen(
                        callState = state.activeCall,
                        webRtcCallManager = viewModel.webRtcCallManager,
                        onAcceptIncoming = {
                            ensureCallPermissions(needCamera = state.activeCall.isVideo) {
                                viewModel.acceptIncomingCall()
                            }
                        },
                        onToggleMic = { viewModel.toggleCallMic() },
                        onToggleCamera = { viewModel.toggleCallCamera() },
                        onToggleSpeaker = { viewModel.toggleCallSpeaker() },
                        onEndCall = { viewModel.endActiveCall(sendRemoteHangup = true) }
                    )
                }

                // Optional transient status banner at the top
                if (!state.statusBanner.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface2)
                            .clickable { viewModel.clearStatusBanner() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = state.statusBanner,
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fixed Bottom Tabs required by the approved Design B brief:
 * Rooms / Calls / Account
 */
@Composable
private fun FixedBottomNavigationBar(
    activeTab: MainTab,
    unreadTotal: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val colors = LocalRunColors.current
    Column(modifier = Modifier.fillMaxWidth().background(colors.appBg)) {
        HorizontalDivider(color = colors.line, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTabItem(
                label = "Rooms",
                icon = Icons.Outlined.ChatBubbleOutline,
                selected = activeTab == MainTab.ROOMS,
                badgeCount = unreadTotal,
                onClick = { onSelectTab(MainTab.ROOMS) }
            )
            BottomTabItem(
                label = "Calls",
                icon = Icons.Outlined.Call,
                selected = activeTab == MainTab.CALLS,
                badgeCount = 0,
                onClick = { onSelectTab(MainTab.CALLS) }
            )
            BottomTabItem(
                label = "Account",
                icon = Icons.Outlined.PersonOutline,
                selected = activeTab == MainTab.ACCOUNT,
                badgeCount = 0,
                onClick = { onSelectTab(MainTab.ACCOUNT) }
            )
        }
    }
}

@Composable
private fun BottomTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeCount: Int,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    val tint = if (selected) colors.accent else colors.inkSoft

    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(23.dp)
                )
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.accent)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = colors.accentInk,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Text(
                text = label,
                color = tint,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}
