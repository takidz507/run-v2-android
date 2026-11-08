package com.takidz.runv2.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.ChatMessage
import com.takidz.runv2.data.PresenceState
import com.takidz.runv2.data.RoomSummary
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Full-Screen Chat Screen.
 * - Real sending and receiving via `/sync` (messages arriving while open appear automatically).
 * - Honest E2EE status: clearly shows whether the room uses Olm/Megolm E2E encryption
 *   or is unencrypted, and never claims an unencrypted room/message is encrypted.
 * - Small, clean message font (13.5sp) as requested in Design B.
 * - Voice call, Video call, and Room Info / Invite buttons in the header.
 */
@Composable
fun ChatScreen(
    room: RoomSummary,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onOpenRoomInfo: () -> Unit,
    onStartVoiceCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalRunColors.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.appBg)
                .padding(horizontal = 14.dp, vertical = 10.dp),
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

            RunAvatar(
                label = room.name,
                seedColor = room.avatarSeedColor,
                size = 40.dp,
                presence = if (room.isDirect) room.peerPresence else null
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenRoomInfo() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        color = colors.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = if (room.isEncrypted) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                        contentDescription = if (room.isEncrypted) "غرفة مشفرة" else "غرفة غير مشفرة",
                        tint = if (room.isEncrypted) colors.online else colors.danger,
                        modifier = Modifier.size(13.dp)
                    )
                }
                val baseStatus = if (room.isDirect) {
                    if (room.peerPresence == PresenceState.ONLINE) "متصل الآن" else room.roomCode
                } else {
                    "${room.memberCount} عضوًا · الكود ${room.roomCode}"
                }
                val encLabel = if (room.isEncrypted) "مشفرة E2E" else "غير مشفرة"
                Text(
                    text = "$baseStatus · $encLabel",
                    color = colors.inkSoft,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Room Info / Invite Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .clickable { onOpenRoomInfo() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = "تفاصيل ودعوة الغرفة",
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Voice Call Button
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .clickable { onStartVoiceCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Call,
                    contentDescription = "مكالمة صوتية",
                    tint = colors.ink,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Video Call Button (Accent)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable { onStartVideoCall() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Videocam,
                    contentDescription = "مكالمة فيديو",
                    tint = colors.accentInk,
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bannerText = if (room.isEncrypted) {
                        "🔒 تشفير من الطرف إلى الطرف مفعّل (Olm/Megolm)"
                    } else {
                        room.encryptionStatusNote
                            ?: "⚠️ هذه الغرفة غير مشفرة — الرسائل فيها تُرسل بدون تشفير طرف-لطرف"
                    }
                    Text(
                        text = bannerText,
                        color = if (room.isEncrypted) colors.inkSoft else colors.danger,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(colors.surface2)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            items(messages, key = { it.eventId }) { msg ->
                MessageBubble(
                    message = msg,
                    showSenderName = !room.isDirect && !msg.isOutgoing
                )
            }
        }

        HorizontalDivider(color = colors.line, thickness = 1.dp)

        // Message Composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.appBg)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.surface2)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (inputText.isEmpty()) {
                            val hint = if (room.isEncrypted) {
                                "اكتب رسالة مشفرة…"
                            } else {
                                "اكتب رسالة (غير مشفرة)…"
                            }
                            Text(hint, color = colors.inkSoft, fontSize = 13.5.sp)
                        }
                        inner()
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent)
                    .clickable {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "إرسال",
                    tint = colors.accentInk,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    showSenderName: Boolean
) {
    val colors = LocalRunColors.current
    val bubbleBg = if (message.isOutgoing) colors.bubbleOut else colors.bubbleIn
    val textColor = if (message.isOutgoing) colors.bubbleOutInk else colors.ink
    val bubbleShape = if (message.isOutgoing) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 5.dp)
    } else {
        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 5.dp, bottomEnd = 16.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 285.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (showSenderName) {
                Text(
                    text = message.senderDisplayName,
                    color = colors.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            // Small compact chat text (13.5sp, normal weight)
            Text(
                text = message.body,
                color = if (message.decryptionFailed) colors.danger else textColor,
                fontSize = 13.5.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isEncrypted && !message.decryptionFailed) {
                    Text(
                        text = "🔒 ",
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 9.5.sp
                    )
                }
                Text(
                    text = formatShortTime(message.timestamp),
                    color = textColor.copy(alpha = 0.65f),
                    fontSize = 10.sp
                )
                if (message.isOutgoing) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (message.isDelivered) "✓✓" else "✓",
                        color = textColor.copy(alpha = 0.75f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}
