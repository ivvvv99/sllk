package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.ChatViewModel
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                val currentUser = viewModel.currentUser.collectAsState()
                val activeCall = viewModel.activeCall.collectAsState()

                // Check standard media permissions launcher
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
                    val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
                    
                    if (!cameraGranted || !audioGranted) {
                        Toast.makeText(
                            this,
                            "Camera & Microphone permissions are required for calling features.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // Auto route based on whether a local profile exists or not
                LaunchedEffect(currentUser.value) {
                    if (currentUser.value != null) {
                        navController.navigate("chat") {
                            popUpTo("profile_setup") { inclusive = true }
                        }
                    } else {
                        navController.navigate("profile_setup") {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                // Check permissions when a call transitions to active
                LaunchedEffect(activeCall.value) {
                    val call = activeCall.value
                    if (call != null && (call.status == "ringing" || call.status == "accepted")) {
                        val hasCam = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasMic = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        
                        if (!hasCam || !hasMic) {
                            permissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.CAMERA,
                                    Manifest.permission.RECORD_AUDIO
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "profile_setup",
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable("profile_setup") {
                                ProfileSetupScreen(
                                    viewModel = viewModel,
                                    onSetupComplete = {
                                        navController.navigate("chat") {
                                            popUpTo("profile_setup") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            
                            composable("chat") {
                                ChatScreen(
                                    viewModel = viewModel,
                                    onNavigateToAdmin = {
                                        navController.navigate("admin_panel")
                                    }
                                )
                            }
                            
                            composable("admin_panel") {
                                AdminPanelScreen(
                                    viewModel = viewModel,
                                    onBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }

                    // --- FULL-SCREEN WEBRTC CALL OVERLAY SCREEN ---
                    // Display call screen when the call is accepted/connecting
                    AnimatedVisibility(
                        visible = activeCall.value != null && activeCall.value?.status == "accepted",
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
                    ) {
                        CallScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
