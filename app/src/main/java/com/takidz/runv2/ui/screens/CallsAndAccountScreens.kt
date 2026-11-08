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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.takidz.runv2.data.ActiveCallState
import com.takidz.runv2.data.CallDirection
import com.takidz.runv2.data.CallLogEntry
import com.takidz.runv2.data.CallPhase
import com.takidz.runv2.data.CallQualityMode
import com.takidz.runv2.data.RunAccount
import com.takidz.runv2.data.ThemeMode
import com.takidz.runv2.ui.theme.LocalRunColors
import com.takidz.runv2.webrtc.WebRtcCallManager
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

/**
 * Design B Calls List Screen.
 * - Shows real call history (outgoing, incoming, missed) with durations and timestamps.
 * - Tapping a call entry initiates a real 1-to-1 WebRTC call in that room.
 */
@Composable
fun CallsScreen(
    callLogs: List<CallLogEntry>,
    callQualityMode: CallQualityMode,
    onCallAgain: (roomId: String, isVideo: Boolean) -> Unit
) {
    val colors = LocalRunColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "المكالمات",
                color = colors.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "مكالمات صوت وفيديو 1-إلى-1 حقيقية · الجودة الحالية: ${callQualityMode.labelAr}",
                color = colors.inkSoft,
                fontSize = 12.5.sp
            )
        }

        if (callLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "لا توجد مكالمات مسجلة بعد — افتح أي محادثة واضغط زر المكالمة الصوتية أو الفيديو للاتصال.",
                    color = colors.inkSoft,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item { SectionLabel("الأخيرة") }
                items(callLogs, key = { it.id }) { log ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallAgain(log.roomId, log.isVideo) }
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RunAvatar(
                            label = log.peerName,
                            seedColor = log.avatarSeedColor,
                            size = 46.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.peerName,
                                color = colors.ink,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            val dirLabel = when (log.direction) {
                                CallDirection.OUTGOING -> "صادرة"
                                CallDirection.INCOMING -> "واردة"
                                CallDirection.MISSED -> "فائتة"
                            }
                            val typeLabel = if (log.isVideo) "فيديو" else "صوتية"
                            val durLabel = if (log.durationSeconds > 0) " · ${formatDuration(log.durationSeconds)}" else ""
                            Text(
                                text = "$typeLabel $dirLabel$durLabel",
                                color = if (log.direction == CallDirection.MISSED) colors.danger else colors.inkSoft,
                                fontSize = 12.sp
                            )
                        }
                        Text(
                            text = formatShortTime(log.timestamp),
                            color = colors.inkSoft,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Design B Full-Screen Active WebRTC Call Screen.
 * - Real remote & self-view `SurfaceViewRenderer` when video is enabled.
 * - Real working controls: Call Timer, Mic Mute, Camera On/Off, Speakerphone On/Off,
 *   Accept Incoming Call, and End Call (which sends `m.call.hangup` to remote peer).
 */
@Composable
fun ActiveCallScreen(
    callState: ActiveCallState,
    webRtcCallManager: WebRtcCallManager,
    onAcceptIncoming: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2B1A10), Color(0xFF171310))
    )

    DisposableEffect(Unit) {
        onDispose {
            webRtcCallManager.bindRenderers(null, null)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        // Remote WebRTC Video Renderer (Full-Screen Background when video active)
        if (callState.isVideo && callState.phase == CallPhase.CONNECTED) {
            AndroidView(
                factory = { ctx ->
                    SurfaceViewRenderer(ctx).apply {
                        init(webRtcCallManager.eglBase.eglBaseContext, null)
                        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                        setEnableHardwareScaler(true)
                        webRtcCallManager.bindRenderers(null, this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Self-View PIP Renderer (Top-End corner)
        if (callState.isVideo && callState.isCameraEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 18.dp)
                    .size(width = 86.dp, height = 122.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF3A2618))
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceViewRenderer(ctx).apply {
                            init(webRtcCallManager.eglBase.eglBaseContext, null)
                            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                            setMirror(true)
                            setZOrderMediaOverlay(true)
                            webRtcCallManager.bindRenderers(this, null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Center Avatar & Status Info
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!callState.isVideo || callState.phase != CallPhase.CONNECTED) {
                RunAvatar(
                    label = callState.peerName,
                    seedColor = callState.avatarSeedColor,
                    size = 112.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = callState.peerName,
                color = Color(0xFFFDF3E7),
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            val phaseText = when (callState.phase) {
                CallPhase.OUTGOING_RINGING -> "جاري الاتصال…"
                CallPhase.INCOMING_RINGING -> if (callState.isVideo) "مكالمة فيديو واردة…" else "مكالمة صوتية واردة…"
                CallPhase.CONNECTING -> "جاري ربط الاتصال…"
                CallPhase.CONNECTED -> {
                    val prefix = if (callState.isVideo) "مكالمة فيديو" else "مكالمة صوتية"
                    "$prefix · ${formatDuration(callState.durationSeconds)}"
                }
                else -> ""
            }

            Text(
                text = phaseText,
                color = Color(0xFFFDF3E7).copy(alpha = 0.8f),
                fontSize = 13.5.sp
            )
        }

        // Bottom Call Controls Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (callState.phase == CallPhase.INCOMING_RINGING) {
                // Incoming Call: Accept & Decline
                CallControlItem(
                    label = "قبول",
                    icon = if (callState.isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call,
                    bgColor = Color(0xFF3D9D63),
                    tint = Color.White,
                    onClick = onAcceptIncoming
                )
                CallControlItem(
                    label = "رفض",
                    icon = Icons.Outlined.CallEnd,
                    bgColor = Color(0xFFC93B2E),
                    tint = Color.White,
                    onClick = onEndCall
                )
            } else {
                // Active / Outgoing Call Controls: Mute, Camera, Speaker, End
                CallControlItem(
                    label = if (callState.isMicMuted) "إلغاء الكتم" else "كتم",
                    icon = if (callState.isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    bgColor = if (callState.isMicMuted) Color(0xFFFDF3E7) else Color.White.copy(alpha = 0.16f),
                    tint = if (callState.isMicMuted) Color(0xFF241407) else Color.White,
                    onClick = onToggleMic
                )

                CallControlItem(
                    label = "كاميرا",
                    icon = if (callState.isCameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
                    bgColor = if (!callState.isCameraEnabled) Color(0xFFFDF3E7) else Color.White.copy(alpha = 0.16f),
                    tint = if (!callState.isCameraEnabled) Color(0xFF241407) else Color.White,
                    onClick = onToggleCamera
                )

                CallControlItem(
                    label = "مكبر",
                    icon = if (callState.isSpeakerOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeOff,
                    bgColor = if (callState.isSpeakerOn) Color(0xFFE8A33D) else Color.White.copy(alpha = 0.16f),
                    tint = if (callState.isSpeakerOn) Color(0xFF1F1407) else Color.White,
                    onClick = onToggleSpeaker
                )

                CallControlItem(
                    label = "إنهاء",
                    icon = Icons.Outlined.CallEnd,
                    bgColor = Color(0xFFC93B2E),
                    tint = Color.White,
                    onClick = onEndCall
                )
            }
        }
    }
}

@Composable
private fun CallControlItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFFFDF3E7),
            fontSize = 11.5.sp
        )
    }
}

/**
 * Design B Account & Settings Screen.
 * - Shows user's `@username` and "Run account" (zero server/Matrix mentions).
 * - PIN Creation / Backup & Restore on a new phone.
 * - Call Quality Settings: 480p default, Data saver, Audio-only (respected in WebRTC calls).
 * - Theme switcher: Warm Night (`#171310` + `#E8A33D`), Warm Paper, or System.
 */
@Composable
fun AccountScreen(
    account: RunAccount?,
    themeMode: ThemeMode,
    callQualityMode: CallQualityMode,
    onOpenPinCreate: () -> Unit,
    onOpenPinRestore: () -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectCallQualityMode: (CallQualityMode) -> Unit,
    onLogout: () -> Unit
) {
    val colors = LocalRunColors.current
    val displayName = account?.displayName ?: "مستخدم Run"
    val handle = account?.publicHandle ?: "@user"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text(
                text = "الحساب",
                color = colors.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Profile Card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RunAvatar(
                label = displayName,
                seedColor = 0xFFD95D28L,
                size = 56.dp
            )
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    color = colors.ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = handle,
                        color = colors.inkSoft,
                        fontSize = 13.sp
                    )
                }
            }
            RunChip(text = "Run account")
        }

        SectionLabel("الأمان والنسخة الاحتياطية (PIN)")

        SettingsRowItem(
            icon = Icons.Outlined.Lock,
            title = "رمز PIN لنسخ المفاتيح احتياطيًا (6 أرقام)",
            subtitle = if (account?.isPinConfigured == true) {
                "مفعّل · اضغط لتحديث رمز PIN والنسخة المشفرة"
            } else {
                "غير مفعّل بعد · أنشئ رمز PIN لحفظ نسخة مفاتيحك المشفرة"
            },
            onClick = onOpenPinCreate
        )

        SettingsRowItem(
            icon = Icons.Outlined.PhoneAndroid,
            title = "استعادة المحادثات على هاتف جديد",
            subtitle = "أدخل رمز PIN المكون من 6 أرقام لاسترجاع نسخة مفاتيحك (فقدان الرمز يعني فقدان النسخة نهائيًا)",
            onClick = onOpenPinRestore
        )

        SectionLabel("جودة المكالمات وتوفير البيانات")

        CallQualityMode.entries.forEach { mode ->
            val selected = mode == callQualityMode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectCallQualityMode(mode) }
                    .padding(horizontal = 20.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.accent else colors.appBg)
                        .border(2.dp, if (selected) colors.accent else colors.inkSoft, CircleShape)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mode.labelAr,
                        color = colors.ink,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        SectionLabel("المظهر (Design B)")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallPillButton(
                text = "Warm Night (داكن)",
                filled = themeMode == ThemeMode.WARM_NIGHT,
                onClick = { onSelectThemeMode(ThemeMode.WARM_NIGHT) }
            )
            SmallPillButton(
                text = "Warm Paper (فاتح)",
                filled = themeMode == ThemeMode.WARM_PAPER,
                onClick = { onSelectThemeMode(ThemeMode.WARM_PAPER) }
            )
            SmallPillButton(
                text = "تلقائي",
                filled = themeMode == ThemeMode.SYSTEM,
                onClick = { onSelectThemeMode(ThemeMode.SYSTEM) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        SettingsRowItem(
            icon = Icons.AutoMirrored.Outlined.Logout,
            title = "تسجيل الخروج",
            subtitle = "غرفك وتاريخ محادثاتك محفوظان في Run account الخاص بك عند العودة",
            onClick = onLogout
        )

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun SettingsRowItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalRunColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface2),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.ink,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = colors.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
