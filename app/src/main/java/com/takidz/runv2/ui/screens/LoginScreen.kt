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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Login & Registration Screen.
 * - Strictly @username and password only (no email, no phone number, no server/protocol mention).
 * - Approved v2 golden-amber 'R' mark on Warm Night charcoal.
 */
@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (username: String, password: String) -> Unit,
    onRegister: (username: String, password: String, displayName: String) -> Unit
) {
    val colors = LocalRunColors.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        // Approved v2 R Logo Mark
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(19.dp))
                .background(colors.surface2)
                .border(1.dp, colors.line, RoundedCornerShape(19.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "R",
                color = colors.accent,
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = if (isRegisterMode) "إنشاء حساب Run جديد" else "مرحبًا بعودتك",
            color = colors.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "اسم المستخدم الذي تختاره هو حسابك نفسه. ادخل به من أي هاتف وستجد غرفك ومحادثاتك كما هي.",
            color = colors.inkSoft,
            fontSize = 13.5.sp,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        // Username field (@username)
        Text(
            text = "اسم المستخدم (Run account)",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@",
                    color = colors.inkSoft,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = username,
                    onValueChange = { username = it.replace(" ", "").removePrefix("@") },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (username.isEmpty()) {
                            Text("username", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        val previewHandle = if (username.isBlank()) "@username" else "@${username.trim()}"
        Text(
            text = "حروف لاتينية صغيرة وأرقام — يصبح حساب Run الخاص بك $previewHandle",
            color = colors.inkSoft,
            fontSize = 11.5.sp
        )

        if (isRegisterMode) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "الاسم الظاهر للناس (اختياري)",
                color = colors.inkSoft,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (displayName.isEmpty()) {
                            Text("مثال: تقي الدين", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Password field
        Text(
            text = "كلمة السر",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(color = colors.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (password.isEmpty()) {
                            Text("••••••••", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 15.sp)
                        }
                        innerTextField()
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = "تكتبها بنفسك داخل التطبيق، ولا تحتاج بريدًا إلكترونيًا ولا رقم هاتف أبدًا.",
            color = colors.inkSoft,
            fontSize = 11.5.sp
        )

        if (!errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.danger.copy(alpha = 0.15f))
                    .padding(12.dp)
            ) {
                Text(
                    text = errorMessage,
                    color = colors.danger,
                    fontSize = 12.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = {
                if (isRegisterMode) {
                    onRegister(username, password, displayName)
                } else {
                    onLogin(username, password)
                }
            },
            enabled = !isLoading && username.isNotBlank() && password.isNotBlank(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentInk
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.accentInk,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = if (isRegisterMode) "إنشاء حساب Run" else "تسجيل الدخول",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading) { isRegisterMode = !isRegisterMode }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isRegisterMode) {
                    "لديك حساب بالفعل؟ تسجيل الدخول"
                } else {
                    "مستخدم جديد؟ أنشئ حساب Run باسم مستخدم وكلمة سر"
                },
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Privacy notes
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerifiedUser,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "خصوصية Run: غرفك ومحادثاتك محفوظة بأمان في Run account الخاص بك وتنتقل معك عند الدخول من أي جهاز.",
                    color = colors.inkSoft,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "بعد الدخول: تنشئ رمز PIN من 6 أرقام لنسخ مفاتيح التشفير احتياطيًا واستعادتها على أي هاتف آخر.",
                    color = colors.inkSoft,
                    fontSize = 12.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}
