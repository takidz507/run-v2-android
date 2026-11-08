package com.takidz.runv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.RoomSummary
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Single-Column Rooms Screen.
 * - Strictly displays real rooms synced from the account.
 * - Empty room list on a brand-new account (zero fake/placeholder data).
 * - Top search bar opens SearchScreen (friends, public rooms, room codes).
 * - "+" button opens CreateRoomScreen.
 */
@Composable
fun RoomsScreen(
    rooms: List<RoomSummary>,
    selectedSpaceFilter: String,
    onSelectSpaceFilter: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCreateRoom: () -> Unit,
    onOpenRoom: (RoomSummary) -> Unit
) {
    val colors = LocalRunColors.current
    val groupRooms = rooms.filter { !it.isDirect }
    val directRooms = rooms.filter { it.isDirect }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "الغرف",
                    color = colors.ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "غرف دائمة — التاريخ محفوظ في حسابك، لا يضيع بإغلاق التطبيق",
                    color = colors.inkSoft,
                    fontSize = 12.5.sp
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable { onOpenCreateRoom() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "إنشاء غرفة جماعية جديدة",
                    tint = colors.accentInk,
                    modifier = Modifier.size(21.dp)
                )
            }
        }

        // Top Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.surface2)
                .clickable { onOpenSearch() }
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = colors.inkSoft,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "ابحث عن صديق بالاسم أو @username، أو غرفة بالاسم أو الكود…",
                color = colors.inkSoft,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Spaces / Quick Filters Row (Design B)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpaceFilterOrb(
                label = "الكل",
                shortText = "الكل",
                bgColor = colors.accent,
                selected = selectedSpaceFilter == "ALL",
                onClick = { onSelectSpaceFilter("ALL") }
            )
            SpaceFilterOrb(
                label = "مجموعات",
                shortText = "غرف",
                bgColor = Color(0xFF8A6D3B),
                selected = selectedSpaceFilter == "GROUPS",
                onClick = { onSelectSpaceFilter("GROUPS") }
            )
            SpaceFilterOrb(
                label = "الخاصة",
                shortText = "خاص",
                bgColor = Color(0xFF3F6D8E),
                selected = selectedSpaceFilter == "DIRECT",
                onClick = { onSelectSpaceFilter("DIRECT") }
            )
            SpaceFilterOrb(
                label = "جديدة",
                shortText = "+",
                bgColor = colors.surface2,
                textColor = colors.inkSoft,
                selected = false,
                onClick = { onOpenCreateRoom() }
            )
        }

        if (rooms.isEmpty()) {
            // Strictly empty state on a new account — no fake rooms
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "لا توجد غرف أو محادثات بعد",
                        color = colors.ink,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "استخدم شريط البحث في الأعلى للبحث عن أصدقائك بـ @username أو الانضمام إلى غرفة عامة، أو اضغط زر (+) لإنشاء غرفة جماعية جديدة.",
                        color = colors.inkSoft,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (selectedSpaceFilter != "DIRECT" && groupRooms.isNotEmpty()) {
                    item {
                        SectionLabel("غرف المجموعات")
                    }
                    items(groupRooms, key = { it.roomId }) { room ->
                        RoomRowItem(room = room, onClick = { onOpenRoom(room) })
                    }
                }
                if (selectedSpaceFilter != "GROUPS" && directRooms.isNotEmpty()) {
                    item {
                        SectionLabel("محادثات خاصة")
                    }
                    items(directRooms, key = { it.roomId }) { room ->
                        RoomRowItem(room = room, onClick = { onOpenRoom(room) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceFilterOrb(
    label: String,
    shortText: String,
    bgColor: Color,
    textColor: Color = Color.White,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        val shape = if (selected) CircleShape else RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(shape)
                .background(bgColor)
                .then(
                    if (selected) Modifier.border(2.5.dp, colors.accent, shape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = shortText,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = colors.inkSoft,
            fontSize = 10.5.sp
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    val colors = LocalRunColors.current
    Text(
        text = text,
        color = colors.inkSoft,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RoomRowItem(
    room: RoomSummary,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RunAvatar(
            label = room.name,
            seedColor = room.avatarSeedColor,
            size = 48.dp,
            presence = if (room.isDirect) room.peerPresence else null
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room.name,
                    color = colors.ink,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatShortTime(room.lastMessageTimestamp),
                    color = colors.inkSoft,
                    fontSize = 11.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            val preview = if (!room.lastMessageSender.isNullOrBlank()) {
                "${room.lastMessageSender}: ${room.lastMessageBody}"
            } else {
                room.lastMessageBody
            }
            Text(
                text = preview,
                color = colors.inkSoft,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (room.unreadCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.accent)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = room.unreadCount.toString(),
                    color = colors.accentInk,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
