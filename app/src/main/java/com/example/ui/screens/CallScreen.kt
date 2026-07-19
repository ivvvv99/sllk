package com.example.ui.screens

import android.view.Gravity
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.CallSession
import com.example.ui.ChatViewModel
import com.example.ui.components.UserAvatar
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun CallScreen(
    viewModel: ChatViewModel
) {
    val activeCall = viewModel.activeCall.collectAsState()
    val callStateText = viewModel.callStateText.collectAsState()
    val localVideoTrack = viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack = viewModel.remoteVideoTrack.collectAsState()
    
    val isMicEnabled = viewModel.isMicrophoneEnabled.collectAsState()
    val isCamEnabled = viewModel.isCameraEnabled.collectAsState()

    val currentUser = viewModel.currentUser.collectAsState()

    val call = activeCall.value ?: return
    val myId = currentUser.value?.id ?: ""

    val isCaller = call.callerId == myId
    val otherPartyName = if (isCaller) call.receiverName else call.callerName
    val otherPartyAvatar = if (isCaller) call.receiverAvatar else call.callerAvatar

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F13)) // Dark slate background
    ) {
        // --- REMOTE VIDEO TRACK (Full Screen Background) ---
        if (call.callType == "video" && remoteVideoTrack.value != null && isCamEnabled.value) {
            WebRtcVideoRenderer(
                videoTrack = remoteVideoTrack.value!!,
                eglContext = viewModel.webRtcManager.eglContext,
                mirror = false,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Audio-only or Camera Disabled: Show Pulsing Avatar & Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                Color(0xFF0F0F13)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    UserAvatar(
                        user = com.example.model.User(name = otherPartyName, avatarUrl = otherPartyAvatar),
                        size = 120,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = otherPartyName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (call.callType == "video") "Camera disabled" else "Voice call active",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // --- LOCAL VIDEO TRACK (Floating PIP Overlay in Top Right) ---
        if (call.callType == "video" && localVideoTrack.value != null && isCamEnabled.value) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .size(width = 110.dp, height = 150.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
            ) {
                WebRtcVideoRenderer(
                    videoTrack = localVideoTrack.value!!,
                    eglContext = viewModel.webRtcManager.eglContext,
                    mirror = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // --- TOP INFO BAR OVERLAY ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = otherPartyName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Surface(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = callStateText.value.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // --- BOTTOM CALL CONTROLS PANEL ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mic Button
                FilledIconButton(
                    onClick = { viewModel.toggleMicrophone() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMicEnabled.value) Color.White.copy(alpha = 0.15f) else Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (isMicEnabled.value) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Toggle Microphone"
                    )
                }

                // Camera toggle (for video calls)
                if (call.callType == "video") {
                    FilledIconButton(
                        onClick = { viewModel.toggleCamera() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isCamEnabled.value) Color.White.copy(alpha = 0.15f) else Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (isCamEnabled.value) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            contentDescription = "Toggle Video"
                        )
                    }

                    // Flip Camera
                    FilledIconButton(
                        onClick = { viewModel.switchCamera() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.15f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Switch Camera"
                        )
                    }
                }

                // End Call Red Button
                FilledIconButton(
                    onClick = { viewModel.hangUpCall() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

/**
 * Android View wrapper to embed WebRTC SurfaceViewRenderer in Jetpack Compose
 */
@Composable
fun WebRtcVideoRenderer(
    videoTrack: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
        }
    }

    // Mirror setting
    LaunchedEffect(mirror) {
        renderer.setMirror(mirror)
    }

    // Connect videoTrack to Sink
    DisposableEffect(videoTrack) {
        videoTrack.addSink(renderer)
        onDispose {
            videoTrack.removeSink(renderer)
        }
    }

    // Clean up EGL renderer on lifecycle disposal
    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    AndroidView(
        factory = { renderer },
        modifier = modifier
    )
}
