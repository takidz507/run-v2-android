package com.takidz.runv2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.PresenceState
import com.takidz.runv2.ui.theme.LocalRunColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RunAvatar(
    label: String,
    seedColor: Long,
    size: Dp = 48.dp,
    presence: PresenceState? = null
) {
    val colors = LocalRunColors.current
    val initial = label.trim().removePrefix("@").firstOrNull()?.uppercaseChar()?.toString() ?: "R"

    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(seedColor)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = (size.value * 0.35f).sp
            )
        }

        if (presence != null && presence != PresenceState.OFFLINE) {
            val dotColor = if (presence == PresenceState.ONLINE) colors.online else colors.away
            Box(
                modifier = Modifier
                    .size(size * 0.27f)
                    .align(Alignment.BottomStart)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(2.dp, colors.appBg, CircleShape)
            )
        }
    }
}

@Composable
fun RunChip(
    text: String,
    icon: ImageVector? = null
) {
    val colors = LocalRunColors.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(colors.accentSoft)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = colors.accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatShortTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    return if (diff < 86_400_000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
    } else {
        SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(timestampMs))
    }
}

fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
