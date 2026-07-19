package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModerationLog
import com.example.model.User
import com.example.ui.ChatViewModel
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Logs, 1 = Banned, 2 = Muted
    
    val currentUser = viewModel.currentUser.collectAsState()
    val logs = viewModel.moderationLogs.collectAsState()
    val bannedList = viewModel.bannedUsersList.collectAsState()
    val mutedList = viewModel.mutedUsersList.collectAsState()

    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    val isAdmin = currentUser.value?.role == "Admin"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moderation & Logs", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tabs
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Log Feed") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Banned (${bannedList.value.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Muted (${mutedList.value.size})") }
                )
            }

            // Tab Content
            when (selectedTab) {
                0 -> { // Logs
                    if (logs.value.isEmpty()) {
                        EmptyStatePanel(
                            icon = Icons.Default.History,
                            text = "No moderation logs recorded yet."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(logs.value, key = { it.id }) { log ->
                                LogItemCard(log = log, sdf = sdf)
                            }
                        }
                    }
                }
                1 -> { // Banned
                    if (bannedList.value.isEmpty()) {
                        EmptyStatePanel(
                            icon = Icons.Default.VerifiedUser,
                            text = "No banned users on this server."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(bannedList.value, key = { it.id }) { user ->
                                UserModActionCard(
                                    user = user,
                                    actionText = "LIFT BAN",
                                    actionIcon = Icons.Default.LockOpen,
                                    actionColor = Color(0xFF00E676),
                                    enabled = isAdmin, // Lift ban is Admin-only
                                    onAction = { viewModel.unbanUser(user) }
                                )
                            }
                        }
                    }
                }
                2 -> { // Muted
                    if (mutedList.value.isEmpty()) {
                        EmptyStatePanel(
                            icon = Icons.Default.VolumeUp,
                            text = "No muted users."
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(mutedList.value, key = { it.id }) { user ->
                                val muteTimeLeft = if (user.mutedUntil > System.currentTimeMillis()) {
                                    val minutes = ((user.mutedUntil - System.currentTimeMillis()) / 60000).toInt()
                                    " ($minutes min left)"
                                } else ""
                                
                                UserModActionCard(
                                    user = user,
                                    actionText = "UNMUTE$muteTimeLeft",
                                    actionIcon = Icons.Default.VolumeUp,
                                    actionColor = MaterialTheme.colorScheme.primary,
                                    enabled = true, // Admin & Mod can unmute
                                    onAction = { viewModel.unmuteUser(user) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LogItemCard(log: ModerationLog, sdf: SimpleDateFormat) {
    val actionColor = when (log.action) {
        "BAN" -> Color(0xFFE53935)
        "UNBAN" -> Color(0xFF43A047)
        "MUTE" -> Color(0xFFFB8C00)
        "UNMUTE" -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = actionColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = log.action,
                        color = actionColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                
                Text(
                    text = sdf.format(Date(log.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Target: ${log.targetUserName}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Responsible: ${log.performedByUserName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )

            if (log.reason.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Reason: ${log.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UserModActionCard(
    user: User,
    actionText: String,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionColor: Color,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user = user, size = 44)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = user.name,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "ID: ${user.id.take(8)}...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Button(
                onClick = onAction,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionColor,
                    contentColor = if (actionColor == Color(0xFF00E676)) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = actionText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = actionText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EmptyStatePanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium
        )
    }
}
