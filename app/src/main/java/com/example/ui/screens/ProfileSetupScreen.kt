package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.ChatViewModel
import com.example.ui.components.getAvatarUrl

@Composable
fun ProfileSetupScreen(
    viewModel: ChatViewModel,
    onSetupComplete: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("User") } // "User", "Mod", "Admin"
    var pinCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Cloudinary Local Settings State
    val cloudNameState = viewModel.cloudinaryCloudName.collectAsState()
    val uploadPresetState = viewModel.cloudinaryUploadPreset.collectAsState()
    
    var cloudName by remember { mutableStateOf(cloudNameState.value) }
    var uploadPreset by remember { mutableStateOf(uploadPresetState.value) }
    var showCloudinaryConfig by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Sync cloud name if changes elsewhere
    LaunchedEffect(cloudNameState.value, uploadPresetState.value) {
        cloudName = cloudNameState.value
        uploadPreset = uploadPresetState.value
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(24.dp)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "OMNICHAT",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Real-time Global Room",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Dynamic Live Avatar Generation
            val avatarSeed = if (name.trim().isEmpty()) "default_bot" else name
            val avatarUrlString = getAvatarUrl(avatarSeed)
            
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrlString)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Dynamic PFP",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            
            Text(
                text = "Auto-generated Avatar",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Name Field
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 15) name = it },
                label = { Text("Display Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "Name")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Role selection header
            Text(
                text = "Select Server Role",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 8.dp)
            )

            // Elegant Custom Role selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("User", "Mod", "Admin").forEach { role ->
                    val isSelected = selectedRole == role
                    val activeColor = when (role) {
                        "Admin" -> Color(0xFFE91E63)
                        "Mod" -> Color(0xFF00E676)
                        else -> MaterialTheme.colorScheme.primary
                    }
                    
                    OutlinedCard(
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) activeColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) activeColor.copy(alpha = 0.08f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedRole = role },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = role,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Pin code input for Admin / Mod
            AnimatedVisibility(
                visible = selectedRole != "User",
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = pinCode,
                        onValueChange = { pinCode = it },
                        label = { Text("Enter Role Verification PIN") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "PIN")
                        },
                        supportingText = {
                            Text(
                                text = if (selectedRole == "Admin") "Default PIN: 1234" else "Default PIN: 5678",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cloudinary Storage Config Collapse Card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCloudinaryConfig = !showCloudinaryConfig },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Cloud",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cloudinary Media Storage",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            imageVector = if (showCloudinaryConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Expand"
                        )
                    }

                    AnimatedVisibility(
                        visible = showCloudinaryConfig,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Enter Cloudinary credentials to support picture uploads in chat & custom avatars.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            
                            OutlinedTextField(
                                value = cloudName,
                                onValueChange = { cloudName = it },
                                label = { Text("Cloud Name") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            
                            OutlinedTextField(
                                value = uploadPreset,
                                onValueChange = { uploadPreset = it },
                                label = { Text("Unsigned Upload Preset") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Error Display
            AnimatedVisibility(visible = errorMessage != null) {
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Register Button
            Button(
                onClick = {
                    if (name.trim().isEmpty()) {
                        errorMessage = "Please enter a valid display name!"
                        return@Button
                    }
                    
                    // Save Cloudinary values to Viewmodel first
                    viewModel.saveCloudinaryConfig(cloudName.trim(), uploadPreset.trim())

                    // Attempt registration
                    val regError = viewModel.registerUserProfile(
                        name = name.trim(),
                        avatarUrl = avatarUrlString,
                        role = selectedRole,
                        pinCode = pinCode.trim()
                    )

                    if (regError != null) {
                        errorMessage = regError
                    } else {
                        onSetupComplete()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "JOIN GLOBAL CHAT",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
            }
        }
    }
}
