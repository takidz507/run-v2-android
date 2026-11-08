package com.takidz.runv2.ui.screens

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.takidz.runv2.data.UserSearchResult
import com.takidz.runv2.ui.theme.LocalRunColors

/**
 * Design B Create Group Room Screen.
 * - Room Name, optional Description, and member search/selection.
 * - Creates the room on the server and generates a permanent share code & `run://join/<code>` link.
 */
@Composable
fun CreateRoomScreen(
    isLoading: Boolean,
    knownFriends: List<UserSearchResult>,
    searchResults: List<UserSearchResult>,
    onSearchMembers: (String) -> Unit,
    onCreateRoom: (name: String, topic: String, selectedHandles: List<String>) -> Unit,
    onBack: () -> Unit
) {
    val colors = LocalRunColors.current
    var roomName by remember { mutableStateOf("") }
    var roomTopic by remember { mutableStateOf("") }
    var memberQuery by remember { mutableStateOf("") }
    val selectedHandles = remember { mutableStateListOf<String>() }

    val candidateMembers = remember(knownFriends, searchResults, memberQuery) {
        if (memberQuery.isBlank()) knownFriends else (searchResults + knownFriends).distinctBy { it.internalUserId }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.appBg)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = "غرفة جماعية جديدة",
                    color = colors.ink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "دائمة، بتاريخ محفوظ وكود مشاركة خاص",
                    color = colors.inkSoft,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "اسم الغرفة",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = roomName,
                onValueChange = { roomName = it },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 14.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (roomName.isEmpty()) {
                        Text("مثال: مراجعة الرياضيات", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 14.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "وصف الغرفة (اختياري)",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.TopStart
        ) {
            BasicTextField(
                value = roomTopic,
                onValueChange = { roomTopic = it },
                textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (roomTopic.isEmpty()) {
                        Text("اكتب وصفًا مختصرًا للغرفة…", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 13.5.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "إضافة أعضاء — ابحث بالاسم أو @username",
            color = colors.inkSoft,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surface)
                .border(1.5.dp, colors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = memberQuery,
                onValueChange = {
                    memberQuery = it
                    onSearchMembers(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = colors.ink, fontSize = 13.5.sp),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (memberQuery.isEmpty()) {
                        Text("ابحث عن عضو لإضافته…", color = colors.inkSoft.copy(alpha = 0.55f), fontSize = 13.5.sp)
                    }
                    inner()
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(candidateMembers, key = { it.internalUserId }) { person ->
                val isPicked = selectedHandles.contains(person.publicHandle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isPicked) {
                                selectedHandles.remove(person.publicHandle)
                            } else {
                                selectedHandles.add(person.publicHandle)
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RunAvatar(
                        label = person.displayName,
                        seedColor = person.avatarSeedColor,
                        size = 40.dp,
                        presence = person.presence
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = person.displayName,
                            color = colors.ink,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = person.publicHandle,
                                color = colors.inkSoft,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (isPicked) colors.accent else colors.appBg)
                            .border(2.dp, if (isPicked) colors.accent else colors.line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPicked) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.accentInk,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = {
                val extraTypedHandle = memberQuery.trim().takeIf { it.startsWith("@") && it.length > 2 }
                val finalInvites = (selectedHandles.toList() + listOfNotNull(extraTypedHandle)).distinct()
                onCreateRoom(roomName, roomTopic, finalInvites)
            },
            enabled = !isLoading && roomName.isNotBlank(),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentInk
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = colors.accentInk,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = "إنشاء الغرفة (${selectedHandles.size + 1} عضو)",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
