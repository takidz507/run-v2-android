package com.takidz.runv2.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.DeviceIdentity
import com.takidz.runv2.data.RoomMember
import com.takidz.runv2.data.RoomSummary
import com.takidz.runv2.data.RunConfig
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Room Details, E2EE Device Verification & Invite Screen.
 * - Honest E2EE encryption status card (`Olm/Megolm` vs unencrypted).
 * - Permanent Share Code and `run://join/<code>` app invite link.
 * - 3 working buttons: Copy Link, Copy Code, and Share (via Android Share Sheet).
 * - Real member list and Olm/Ed25519 device verification list.
 */
@Composable
fun RoomInfoScreen(
    room: RoomSummary,
    members: List<RoomMember>,
    devices: List<DeviceIdentity>,
    onVerifyDevice: (DeviceIdentity) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalRunColors.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val inviteLink = if (room.isDirect && room.roomCode.startsWith("@")) {
        RunConfig.buildFriendInviteLink(room.roomCode)
    } else {
        RunConfig.buildRoomInviteLink(room.roomCode)
    }

    var copiedLink by remember { mutableStateOf(false) }
    var copiedCode by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = room.name,
                    color = colors.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (room.isDirect) {
                        "محادثة خاصة · ${room.roomCode}"
                    } else {
                        "${members.size.coerceAtLeast(room.memberCount)} عضوًا · غرفة دائمة"
                    },
                    color = colors.inkSoft,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Encryption Status Box
            item {
                SectionLabel("حالة التشفير")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = if (room.isEncrypted) {
                            "🔒 التشفير من الطرف إلى الطرف مفعّل (Olm / Megolm)"
                        } else {
                            "⚠️ هذه الغرفة غير مشفرة"
                        },
                        color = if (room.isEncrypted) colors.online else colors.danger,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (room.isEncrypted) {
                            "الرسائل تُشفر بمفاتيح Megolm وتُتبادل عبر Olm مع التحقق من تواقيع الأجهزة (متوافق مع Element)."
                        } else {
                            room.encryptionStatusNote
                                ?: "تعذر تفعيل التشفير في هذه الغرفة؛ الرسائل المرسلة فيها غير مشفرة."
                        },
                        color = colors.inkSoft,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            item {
                SectionLabel("دعوة إلى الغرفة")
            }

            // 1. Invite Link Box (`run://join/<code>`) + Copy Link Button
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "رابط الدعوة — يفتح الغرفة مباشرة داخل تطبيق Run",
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
                                    text = inviteLink,
                                    color = colors.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        SmallPillButton(
                            text = if (copiedLink) "تم النسخ ✓" else "نسخ الرابط",
                            filled = copiedLink,
                            onClick = {
                                clipboard.setText(AnnotatedString(inviteLink))
                                copiedLink = true
                            }
                        )
                    }
                }
            }

            // 2. Permanent Room Code Box + Copy Code Button
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        text = "كود الغرفة الدائم",
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
                                    text = room.roomCode,
                                    color = colors.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        SmallPillButton(
                            text = if (copiedCode) "تم النسخ ✓" else "نسخ الكود",
                            filled = copiedCode,
                            onClick = {
                                clipboard.setText(AnnotatedString(room.roomCode))
                                copiedCode = true
                            }
                        )
                    }
                }
            }

            // 3. Share Button (Android Share Intent)
            item {
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "انضم إلى غرفة \"${room.name}\" في تطبيق Run:\nالرابط: $inviteLink\nالكود: ${room.roomCode}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "مشاركة دعوة Run"))
                    },
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentInk
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .height(46.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "مشاركة الدعوة",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                SectionLabel("الأعضاء (${members.size})")
            }

            items(members, key = { it.userId }) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunAvatar(
                        label = member.displayName,
                        seedColor = member.avatarSeedColor,
                        size = 42.dp,
                        presence = member.presence
                    )
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.displayName,
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = member.publicHandle,
                                color = colors.inkSoft,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    RunChip(text = member.roleLabel)
                }
            }

            if (devices.isNotEmpty()) {
                item {
                    SectionLabel("أجهزة التشفير والتحقق (${devices.size})")
                }
                items(devices, key = { "${it.userId}|${it.deviceId}" }) { dev ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${RunConfig.toPublicHandle(dev.userId)} · ${dev.deviceId}",
                                    color = colors.ink,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = "Ed25519: ${dev.formattedFingerprint}",
                                        color = colors.inkSoft,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            SmallPillButton(
                                text = if (dev.manuallyVerified) "موثّق ✓" else "توثيق الجهاز",
                                filled = dev.manuallyVerified,
                                onClick = { onVerifyDevice(dev) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
