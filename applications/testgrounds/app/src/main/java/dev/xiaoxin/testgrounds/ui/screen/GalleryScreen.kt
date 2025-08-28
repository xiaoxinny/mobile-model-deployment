package dev.xiaoxin.testgrounds.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.xiaoxin.testgrounds.data.MediaItem
import dev.xiaoxin.testgrounds.ui.theme.KronaOneFontFamily
import dev.xiaoxin.testgrounds.ui.theme.QuicksandFontFamily
import dev.xiaoxin.testgrounds.viewmodel.GalleryViewModel
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    navController: NavController,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri -> viewModel.importMedia(uri) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Import") },
                text = { Text("Import") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = true,
                    icon = { Icon(Icons.Filled.Image, contentDescription = "") },
                    onClick = { navController.navigate("gallery") },
                    label = { Text("GALLERY", fontFamily = QuicksandFontFamily) }
                )
                NavigationBarItem(
                    selected = false,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "") },
                    onClick = { navController.navigate("settings") },
                    label = { Text("SETTINGS", fontFamily = QuicksandFontFamily) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header row with refresh
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gallery",
                    fontFamily = KronaOneFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                IconButton(onClick = { viewModel.refreshMedia() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }

            // Loading indicator
            if (uiState.isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Processing media... ${(uiState.processingProgress * 100).toInt()}%")
                        LinearProgressIndicator(
                            progress = uiState.processingProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Error message
            uiState.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            // Censored Media Section
            Text(
                "My censored media (${uiState.censoredMedia.size})",
                fontFamily = KronaOneFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            LazyHorizontalGrid(
                rows = GridCells.Fixed(2),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.censoredMedia) { mediaItem ->
                    MediaItemCard(mediaItem)
                }
            }

            // Verified Media Section
            Text(
                "Verified images (${uiState.verifiedMedia.size})",
                fontFamily = KronaOneFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.verifiedMedia) { mediaItem ->
                    MediaItemCard(mediaItem)
                }
            }
        }
    }
}

@Composable
private fun MediaItemCard(mediaItem: MediaItem) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            val context = LocalContext.current
            val request = ImageRequest.Builder(context)
                .data(mediaItem.uri)
                .crossfade(true)
                .build()

            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .graphicsLayer {
                        if (mediaItem.isCensored && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = RenderEffect
                                .createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    },
                contentScale = ContentScale.Crop
            )

            // Censored overlay label
            if (mediaItem.isCensored) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    tonalElevation = 2.dp,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "Censored",
                            tint = MaterialTheme.colorScheme.onError,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "CENSORED",
                            color = MaterialTheme.colorScheme.onError,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Detection count badge
            if (mediaItem.detections.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (mediaItem.isCensored)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "${mediaItem.detections.size}",
                        modifier = Modifier.padding(4.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp
                    )
                }
            }

            // Video indicator
            if (mediaItem.type == dev.xiaoxin.testgrounds.data.MediaType.VIDEO) {
                Icon(
                    Icons.Filled.PlayCircle,
                    contentDescription = "Video",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}