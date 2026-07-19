package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Message
import com.example.model.User
import com.example.ui.ChatViewModel
import com.example.ui.components.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToAdmin: () -> Unit
) {
    val currentUser = viewModel.currentUser.collectAsState()
    val messages = viewModel.messages.collectAsState()
    val onlineUsers = viewModel.onlineUsers.collectAsState()
    val typingUsers = viewModel.typingUsers.collectAsState()
    val activeCall = viewModel.activeCall.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingMedia by remember { mutableStateOf(false) }

    // Navigation & Modal States
    var showUsersDrawer by remember { mutableStateOf(false) }
    var selectedMessageForMod by remember { mutableStateOf<Message?>(null) }
    var showModDialog by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val me = currentUser.value ?: return

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.value.size) {
        if (messages.value.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.value.size - 1)
        }
    }

    // Dynamic Image Picker
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
        }
    }

    // --- FULL BAN BLOCK SCREEN ---
    if (me.isBanned) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = "Banned",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Access Revoked",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your account has been permanently banned from OmniChat due to policy or moderation violations.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Exit Application", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OmniChat", fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(
                            text = "${onlineUsers.value.size} online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    // Show moderation panel button if Admin/Mod
                    if (me.role == "Admin" || me.role == "Mod") {
                        IconButton(onClick = onNavigateToAdmin) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Mod Panel",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Online users list trigger
                    IconButton(onClick = { showUsersDrawer = true }) {
                        Icon(imageVector = Icons.Default.People, contentDescription = "Active Users")
                    }

                    // Logout Action
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // --- MESSAGE LIST HUB ---
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(messages.value, key = { it.id }) { msg ->
                        val isMe = msg.senderId == me.id
                        MessageBubble(
                            message = msg,
                            isCurrentUser = isMe,
                            onLongClick = {
                                if (me.role == "Admin" || me.role == "Mod") {
                                    selectedMessageForMod = msg
                                    showModDialog = true
                                }
                            }
                        )
                    }
                }

                // --- INLINE TYPING STATUS OVERLAY ---
                AnimatedVisibility(
                    visible = typingUsers.value.isNotEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val typingText = when (typingUsers.value.size) {
                        1 -> "${typingUsers.value[0]} is typing..."
                        2 -> "${typingUsers.value[0]} and ${typingUsers.value[1]} are typing..."
                        else -> "Several users are typing..."
                    }
                    Text(
                        text = typingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // --- PRE-SEND IMAGE PREVIEW HUB ---
                AnimatedVisibility(visible = selectedImageUri != null) {
                    selectedImageUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Selected photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Clear image attachment button
                                IconButton(
                                    onClick = { selectedImageUri = null },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isUploadingMedia) {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- BOTTOM COMPOSABLE INPUT BAR ---
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Media select button
                        IconButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            enabled = !isUploadingMedia
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Add image",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Text input field
                        val isMuted = me.isMuted && (me.mutedUntil == 0L || System.currentTimeMillis() < me.mutedUntil)
                        
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = {
                                textInput = it
                                viewModel.updateTypingState(it.isNotEmpty())
                            },
                            enabled = !isMuted && !isUploadingMedia,
                            placeholder = {
                                Text(
                                    if (isMuted) {
                                        val timeLeft = if (me.mutedUntil > 0L) {
                                            val minutes = ((me.mutedUntil - System.currentTimeMillis()) / 60000).toInt()
                                            " ($minutes min left)"
                                        } else ""
                                        "You are muted$timeLeft"
                                    } else "Type your message..."
                                )
                            },
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Send button
                        IconButton(
                            onClick = {
                                val messageText = textInput.trim()
                                if (messageText.isNotEmpty() || selectedImageUri != null) {
                                    viewModel.sendMessage(
                                        text = messageText,
                                        imageUri = selectedImageUri,
                                        onUploadProgress = { isUploadingMedia = it }
                                    )
                                    textInput = ""
                                    selectedImageUri = null
                                }
                            },
                            enabled = (!isMuted && !isUploadingMedia) && (textInput.trim().isNotEmpty() || selectedImageUri != null)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (isMuted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // --- INCOMING CALL FLOATING OVERLAY ---
            if (activeCall.value != null && activeCall.value?.status == "ringing" && activeCall.value?.receiverId == me.id) {
                val incoming = activeCall.value!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Transparent)
                ) {
                    IncomingCallNotification(
                        callerName = incoming.callerName,
                        callerAvatar = incoming.callerAvatar,
                        callType = incoming.callType,
                        onAccept = { viewModel.acceptIncomingCall() },
                        onDecline = { viewModel.declineIncomingCall() }
                    )
                }
            }
        }
    }

    // --- MODERATION DIALOG MENU (Long-press actions) ---
    if (showModDialog && selectedMessageForMod != null) {
        val targetMessage = selectedMessageForMod!!
        val isTargetAdmin = targetMessage.senderRole == "Admin"
        
        AlertDialog(
            onDismissRequest = {
                showModDialog = false
                selectedMessageForMod = null
            },
            title = { Text("Moderation: ${targetMessage.senderName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Choose an administrative action for this user and message.", fontSize = 14.sp)
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${targetMessage.text}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Delete message button
                    if (!targetMessage.isDeleted) {
                        Button(
                            onClick = {
                                viewModel.deleteMessage(targetMessage)
                                showModDialog = false
                                selectedMessageForMod = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete Inappropriate Message", fontWeight = FontWeight.Bold)
                        }
                    }

                    // 2. Mute User 10 mins (Mod/Admin)
                    if (!isTargetAdmin && targetMessage.senderId != me.id) {
                        Button(
                            onClick = {
                                viewModel.muteUser(
                                    User(id = targetMessage.senderId, name = targetMessage.senderName),
                                    durationMinutes = 10,
                                    reason = "Spamming / Inappropriate content"
                                )
                                showModDialog = false
                                selectedMessageForMod = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFB8C00))
                        ) {
                            Icon(imageVector = Icons.Default.VolumeMute, contentDescription = "Mute")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mute User for 10 Minutes")
                        }

                        // 3. Ban User (Admin Only!)
                        if (me.role == "Admin") {
                            Button(
                                onClick = {
                                    viewModel.banUser(
                                        User(id = targetMessage.senderId, name = targetMessage.senderName),
                                        reason = "Severe server violations"
                                    )
                                    showModDialog = false
                                    selectedMessageForMod = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                            ) {
                                Icon(imageVector = Icons.Default.Gavel, contentDescription = "Ban")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Permanently Ban User", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showModDialog = false
                            selectedMessageForMod = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // --- ACTIVE MEMBERS DRAWER / DIALOG (For calls & user info) ---
    if (showUsersDrawer) {
        AlertDialog(
            onDismissRequest = { showUsersDrawer = false },
            title = { Text("Server Members", fontWeight = FontWeight.Bold) },
            text = {
                Box(modifier = Modifier.heightIn(max = 400.dp)) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(onlineUsers.value, key = { it.id }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    UserAvatar(user = user, size = 36)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = user.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (user.id == me.id) {
                                                Text(
                                                    text = " (You)",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Text(
                                            text = user.role,
                                            fontSize = 11.sp,
                                            color = RoleTheme.getRoleColor(user.role)
                                        )
                                    }
                                }

                                // Calling indicators (Do not call myself)
                                if (user.id != me.id) {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                viewModel.initiateCall(user, callType = "audio")
                                                showUsersDrawer = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "Voice Call",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                viewModel.initiateCall(user, callType = "video")
                                                showUsersDrawer = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Video Call",
                                                tint = Color(0xFF00E676),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showUsersDrawer = false }) {
                    Text("Close")
                }
            }
        )
    }
}
