package dev.xiaoxin.testgrounds.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.xiaoxin.testgrounds.ui.theme.KronaOneFontFamily
import dev.xiaoxin.testgrounds.ui.theme.QuicksandFontFamily
import androidx.hilt.navigation.compose.hiltViewModel
import dev.xiaoxin.testgrounds.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val confidenceThreshold = uiState.confidenceThreshold
    val iouThreshold = uiState.iouThreshold
    val selectedClasses = uiState.censoredClasses

    val availableClasses = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
        "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    icon = { Icon(Icons.Filled.Image, contentDescription = "") },
                    onClick = { navController.navigate("gallery") },
                    label = { Text("GALLERY", fontFamily = QuicksandFontFamily) }
                )
                NavigationBarItem(
                    selected = true,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "") },
                    onClick = { navController.navigate("settings") },
                    label = { Text("SETTINGS", fontFamily = QuicksandFontFamily) }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Settings",
                    fontFamily = KronaOneFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Detection Settings",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        // Confidence Threshold
                        Column {
                            Text("Confidence Threshold: ${(confidenceThreshold * 100).toInt()}%")
                            Slider(
                                value = confidenceThreshold,
                                onValueChange = { viewModel.updateConfidence(it) },
                                valueRange = 0.05f..0.9f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        // IoU Threshold
                        Column {
                            Text("IoU Threshold: ${(iouThreshold * 100).toInt()}%")
                            Slider(
                                value = iouThreshold,
                                onValueChange = { viewModel.updateIou(it) },
                                valueRange = 0.1f..0.9f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Classes to Censor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Select which object classes should trigger censoring:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(availableClasses) { className ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    onClick = { viewModel.toggleClass(className) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            className.replaceFirstChar { it.uppercase() },
                            fontSize = 16.sp
                        )
                        if (className in selectedClasses) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "About",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "YOLO Object Detection App\nVersion 1.0\n\nUsing TensorFlow Lite with YOLOv8 model for real-time object detection and media classification.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}