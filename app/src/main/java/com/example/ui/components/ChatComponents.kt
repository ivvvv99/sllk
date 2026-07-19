package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.Message
import com.example.model.User
import java.text.SimpleDateFormat
import java.util.*

// Dynamic Role Styling Tokens
object RoleTheme {
    fun getRoleColor(role: String): Color {
        return when (role) {
            "Admin" -> Color(0xFFE91E63) // Strong Vivid Pink
            "Mod" -> Color(0xFF00E676) // Electric Green
            else -> Color(0xFF29B6F6) // Ocean Blue
        }
    }
}

// Generates avatar URL based on seed name
fun getAvatarUrl(seed: String): String {
    return "https://api.dicebear.com/7.x/bottts/svg?seed=${seed.trim().replace(" ", "_")}"
}

@Composable
fun UserAvatar(
    user: User,
    size: Int = 40,
    modifier: Modifier = Modifier
) {
    val avatarSource = user.avatarUrl.ifEmpty { getAvatarUrl(user.name) }
    
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarSource)
                .crossfade(true)
                .build(),
            contentDescription = "User profile picture",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun UserRoleBadge(role: String) {
    val color = RoleTheme.getRoleColor(role)
    Surface(
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            text = role.uppercase(),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    onLongClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = sdf.format(Date(message.timestamp))
    
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isCurrentUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val textColor = if (isCurrentUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (isCurrentUser) {
        RoundedCornerShape(16.dp, 16.dp, 2.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 2.dp)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = alignment
    ) {
        // Sender info (for other users)
        if (!isCurrentUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            ) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                UserRoleBadge(role = message.senderRole)
            }
        }

        // Message Body Card
        Card(
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clickable { onLongClick() }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.isDeleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = "Deleted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "This message was deleted by ${message.deletedBy}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.error,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }
                } else {
                    // Image attachment
                    if (message.mediaUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.mediaUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Attached media",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .padding(bottom = 6.dp),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Text message
                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            color = textColor,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 20.sp
                        )
                    }

                    // Detect and show Link Preview
                    val url = remember(message.text) { extractFirstUrl(message.text) }
                    if (url != null) {
                        LinkPreviewCard(url = url, isLight = isCurrentUser)
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                
                // Timestamp
                Text(
                    text = timeString,
                    color = textColor.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun LinkPreviewCard(url: String, isLight: Boolean) {
    val cleanUrl = url.removePrefix("http://").removePrefix("https://")
    val domain = cleanUrl.substringBefore("/")
    
    val containerColor = if (isLight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
    }
    
    val borderStrokeColor = if (isLight) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
    }

    Surface(
        color = containerColor,
        border = BorderStroke(1.dp, borderStrokeColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = "Web Link",
                tint = if (isLight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = domain.replaceFirstChar { it.uppercase() },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = url,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

fun extractFirstUrl(text: String): String? {
    val urlRegex = "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}(/[a-zA-Z0-9_.-]*)*)".toRegex()
    val match = urlRegex.find(text)
    return match?.value
}

@Composable
fun IncomingCallNotification(
    callerName: String,
    callerAvatar: String,
    callType: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = callerAvatar.ifEmpty { getAvatarUrl(callerName) },
                contentDescription = "Caller profile",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = callerName,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Incoming ${callType.uppercase()} call...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
            
            IconButton(
                onClick = onDecline,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = "Decline Call",
                    tint = MaterialTheme.colorScheme.onError
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            IconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xFF00E676)
                )
            ) {
                Icon(
                    imageVector = if (callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                    contentDescription = "Accept Call",
                    tint = Color.Black
                )
            }
        }
    }
}
