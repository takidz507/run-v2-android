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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * 6-Digit PIN Screen for Key Backup Creation (2 steps: enter + confirm)
 * and Restoration on a new device.
 *
 * Includes the mandatory clear warning that losing the 6-digit PIN permanently
 * forfeits the encrypted backup.
 */
@Composable
fun PinScreen(
    isRestoreMode: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    onPinConfirmed: (String) -> Unit,
    onSkip: () -> Unit
) {
    val colors = LocalRunColors.current
    var firstEntry by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val isConfirmStep = !isRestoreMode && firstEntry.length == 6

    val titleText = when {
        isRestoreMode -> "استعادة النسخة الاحتياطية برمز PIN"
        isConfirmStep -> "تأكيد رمز PIN"
        else -> "أنشئ رمز PIN من 6 أرقام"
    }

    val subtitleText = when {
        isRestoreMode -> "أدخل رمز PIN المكون من 6 أرقام الذي أنشأته سابقًا لفك تشفير نسخة مفاتيحك على هذا الهاتف."
        isConfirmStep -> "أعد إدخال نفس الأرقام الستة لتأكيد رمز PIN وحفظ النسخة المشفرة."
        else -> "هذا الرمز منفصل عن كلمة المرور ويشفر نسخة مفاتيح محادثاتك احتياطيًا لاستعادتها على أي هاتف آخر."
    }

    fun onDigitClick(digit: String) {
        if (isLoading || currentPin.length >= 6) return
        localError = null
        val updated = currentPin + digit
        currentPin = updated

        if (updated.length == 6) {
            if (isRestoreMode) {
                onPinConfirmed(updated)
            } else if (firstEntry.isEmpty()) {
                firstEntry = updated
                currentPin = ""
            } else {
                if (updated == firstEntry) {
                    onPinConfirmed(updated)
                } else {
                    localError = "الرمزان غير متطابقين، يرجى إدخال رمز PIN من جديد"
                    firstEntry = ""
                    currentPin = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = titleText,
            color = colors.ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = subtitleText,
            color = colors.inkSoft,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mandatory explicit permanent-loss warning
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "تنبيه مهم: فقدان رمز PIN يعني فقدان النسخة الاحتياطية لمفاتيح التشفير نهائيًا ولا يمكن استرجاعها.",
                color = colors.accent,
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 6 PIN Dots
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val filled = i < currentPin.length
                    Box(
                        modifier = Modifier
                            .size(15.dp)
                            .clip(CircleShape)
                            .background(if (filled) colors.accent else colors.appBg)
                            .border(
                                width = 2.dp,
                                color = if (filled) colors.accent else colors.inkSoft,
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val displayErr = localError ?: errorMessage
        if (!displayErr.isNullOrBlank()) {
            Text(
                text = displayErr,
                color = colors.danger,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Numeric Keypad
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("", "0", "DEL")
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rows.forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEach { key ->
                            when (key) {
                                "" -> Spacer(modifier = Modifier.size(68.dp))
                                "DEL" -> Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            if (currentPin.isNotEmpty()) {
                                                currentPin = currentPin.dropLast(1)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Backspace,
                                        contentDescription = "حذف رقم",
                                        tint = colors.inkSoft,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                else -> Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(colors.surface)
                                        .clickable { onDigitClick(key) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        color = colors.ink,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "المتابعة إلى الغرف",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            modifier = Modifier
                .clickable { onSkip() }
                .padding(8.dp)
        )
    }
}

@Composable
fun PinRestoreSuccessScreen(
    onOpenRooms: () -> Unit
) {
    val colors = LocalRunColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
            .padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.online.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.online,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "رجعت محادثاتك",
            color = colors.ink,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "رمز PIN فكّ تشفير نسخة المفاتيح الاحتياطية، وتمت استعادة مفاتيح غرفك على هذا الهاتف.",
            color = colors.inkSoft,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onOpenRooms,
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentInk
            ),
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(50.dp)
        ) {
            Text(
                text = "فتح الغرف",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
