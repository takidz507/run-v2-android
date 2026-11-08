package com.takidz.runv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.FriendRelationState
import com.takidz.runv2.data.FriendRequestItem
import com.takidz.runv2.data.PublicRoomResult
import com.takidz.runv2.data.RunConfig
import com.takidz.runv2.data.UserSearchResult
import com.takidz.runv2.ui.SearchTab
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Search & Friends Screen.
 * - Searches 3 things: Friends (by @username and display name), Public Rooms, and Room Codes.
 * - Friend result shows Display Name large on top, `@username` small below, and `Add` button on the side.
 * - `Add` sends a real friend invite on the server.
 * - Friends list displays real online presence green dot (`PresenceState.ONLINE`); tapping opens a real 1-to-1 chat.
 * - Public rooms search by name and code; joining joins on the server.
 */
@Composable
fun SearchScreen(
    myPublicHandle: String,
    searchQuery: String,
    activeTab: SearchTab,
    isSearching: Boolean,
    friendResults: List<UserSearchResult>,
    publicRoomResults: List<PublicRoomResult>,
    incomingRequests: List<FriendRequestItem>,
    outgoingRequests: List<FriendRequestItem>,
    friends: List<UserSearchResult>,
    onQueryChange: (String) -> Unit,
    onSelectTab: (SearchTab) -> Unit,
    onAddFriend: (UserSearchResult) -> Unit,
    onAcceptRequest: (FriendRequestItem) -> Unit,
    onDeclineRequest: (FriendRequestItem) -> Unit,
    onOpenFriendChat: (UserSearchResult) -> Unit,
    onJoinPublicRoom: (PublicRoomResult) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalRunColors.current
    val clipboard = LocalClipboardManager.current
    var copiedMyLink by remember { mutableStateOf(false) }
    val myFriendLink = RunConfig.buildFriendInviteLink(myPublicHandle)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
    ) {
        // Header with Back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "رجوع",
                    tint = colors.ink,
                    modifier = Modifier.size(19.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "بحث",
                    color = colors.ink,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "أصدقاء وغرف عامة وأكواد الغرف",
                    color = colors.inkSoft,
                    fontSize = 12.sp
                )
            }
        }

        // Search input box
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface2)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.inkSoft,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = if (activeTab == SearchTab.FRIENDS) {
                                "اكتب اسمًا ظاهرًا أو @username…"
                            } else {
                                "اكتب اسم غرفة عامة أو كود الغرفة…"
                            },
                            color = colors.inkSoft,
                            fontSize = 13.sp
                        )
                    }
                    innerTextField()
                }
            )
            if (isSearching) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Segmented toggle: Friends / Rooms
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface2)
                .padding(4.dp)
        ) {
            SegmentedButton(
                title = "أصدقاء",
                selected = activeTab == SearchTab.FRIENDS,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(SearchTab.FRIENDS) }
            )
            SegmentedButton(
                title = "غرف عامة وأكواد",
                selected = activeTab == SearchTab.ROOMS,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(SearchTab.ROOMS) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "البحث بالاسم المعروض أو بـ @username — أو بكود الغرفة مباشرة.",
                color = colors.inkSoft,
                fontSize = 11.5.sp
            )
        }

        if (activeTab == SearchTab.FRIENDS) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionLabel("نتائج البحث") }
                if (friendResults.isEmpty()) {
                    item {
                        Text(
                            text = if (searchQuery.isBlank()) {
                                "اكتب @username أو الاسم المعروض للبحث عن أصدقائك على السيرفر."
                            } else {
                                "لا توجد نتائج مطابقة لـ \"$searchQuery\"."
                            },
                            color = colors.inkSoft,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    items(friendResults, key = { it.internalUserId }) { user ->
                        FriendSearchResultRow(
                            user = user,
                            onAdd = { onAddFriend(user) },
                            onMessage = { onOpenFriendChat(user) }
                        )
                    }
                }

                if (incomingRequests.isNotEmpty()) {
                    item { SectionLabel("طلبات صداقة ودعوات واردة") }
                    items(incomingRequests, key = { it.roomId }) { req ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunAvatar(
                                label = req.displayName,
                                seedColor = req.avatarSeedColor,
                                size = 44.dp,
                                presence = req.presence
                            )
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.displayName,
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = req.publicHandle,
                                        color = colors.inkSoft,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            SmallPillButton(
                                text = "قبول",
                                filled = true,
                                onClick = { onAcceptRequest(req) }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            SmallPillButton(
                                text = "رفض",
                                filled = false,
                                onClick = { onDeclineRequest(req) }
                            )
                        }
                    }
                }

                if (outgoingRequests.isNotEmpty()) {
                    item { SectionLabel("طلبات مُرسلة") }
                    items(outgoingRequests, key = { it.roomId }) { req ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunAvatar(
                                label = req.displayName,
                                seedColor = req.avatarSeedColor,
                                size = 42.dp
                            )
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.displayName,
                                    color = colors.ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = req.publicHandle,
                                        color = colors.inkSoft,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                            RunChip(text = "بانتظار القبول")
                        }
                    }
                }

                item { SectionLabel("أصدقائي") }
                if (friends.isEmpty()) {
                    item {
                        Text(
                            text = "قائمة أصدقائك فارغة حاليًا — ابحث عن صديق واضغط Add لإضافته.",
                            color = colors.inkSoft,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                } else {
                    items(friends, key = { it.internalUserId }) { friend ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenFriendChat(friend) }
                                .padding(horizontal = 20.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunAvatar(
                                label = friend.displayName,
                                seedColor = friend.avatarSeedColor,
                                size = 46.dp,
                                presence = friend.presence
                            )
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = friend.displayName,
                                    color = colors.ink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = friend.publicHandle,
                                        color = colors.inkSoft,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            RunChip(text = "محادثة ←")
                        }
                    }
                }

                // My friend invite link box
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "رابط دعوتي — يفتح ملفي مباشرة داخل تطبيق Run",
                            color = colors.inkSoft,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surface2)
                                        .padding(horizontal = 11.dp, vertical = 9.dp)
                                ) {
                                    Text(
                                        text = myFriendLink,
                                        color = colors.ink,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallPillButton(
                                text = if (copiedMyLink) "تم النسخ ✓" else "نسخ",
                                filled = copiedMyLink,
                                onClick = {
                                    clipboard.setText(AnnotatedString(myFriendLink))
                                    copiedMyLink = true
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // Public Rooms & Room Codes Tab
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionLabel("الغرف العامة — بالاسم أو بالكود") }
                if (publicRoomResults.isEmpty()) {
                    item {
                        Text(
                            text = "لا توجد غرف عامة مطابقة — جرّب كتابة اسم الغرفة أو كودها أو الصق رابط run://join/<code>.",
                            color = colors.inkSoft,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                } else {
                    items(publicRoomResults, key = { it.roomId }) { pubRoom ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RunAvatar(
                                label = pubRoom.name,
                                seedColor = pubRoom.avatarSeedColor,
                                size = 46.dp
                            )
                            Spacer(modifier = Modifier.width(11.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pubRoom.name,
                                    color = colors.ink,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${pubRoom.memberCount} عضوًا · الكود ${pubRoom.roomCode}",
                                    color = colors.inkSoft,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            SmallPillButton(
                                text = if (pubRoom.isJoined) "فتح" else "انضمام",
                                filled = !pubRoom.isJoined,
                                onClick = { onJoinPublicRoom(pubRoom) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.surface else colors.surface2)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (selected) colors.ink else colors.inkSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FriendSearchResultRow(
    user: UserSearchResult,
    onAdd: () -> Unit,
    onMessage: () -> Unit
) {
    val colors = LocalRunColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunAvatar(
            label = user.displayName,
            seedColor = user.avatarSeedColor,
            size = 46.dp,
            presence = user.presence
        )
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Display name large on top
            Text(
                text = user.displayName,
                color = colors.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // @username small underneath
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = user.publicHandle,
                    color = colors.inkSoft,
                    fontSize = 12.sp
                )
            }
        }
        when (user.relationState) {
            FriendRelationState.FRIEND -> SmallPillButton(
                text = "مراسلة",
                filled = false,
                onClick = onMessage
            )
            FriendRelationState.OUTGOING_PENDING -> SmallPillButton(
                text = "تم الإرسال",
                filled = false,
                onClick = {}
            )
            else -> SmallPillButton(
                text = "Add",
                filled = true,
                onClick = onAdd
            )
        }
    }
}

@Composable
fun SmallPillButton(
    text: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (filled) colors.accent else colors.surface2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (filled) colors.accentInk else colors.ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
