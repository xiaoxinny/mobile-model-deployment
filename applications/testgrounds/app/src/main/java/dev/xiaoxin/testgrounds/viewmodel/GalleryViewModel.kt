package dev.xiaoxin.testgrounds.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.xiaoxin.testgrounds.data.MediaItem
import dev.xiaoxin.testgrounds.ml.YoloDetector
import dev.xiaoxin.testgrounds.repository.MediaRepository
import dev.xiaoxin.testgrounds.settings.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GalleryUiState(
    val censoredMedia: List<MediaItem> = emptyList(),
    val verifiedMedia: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val processingProgress: Float = 0f
)

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val yoloDetector: YoloDetector,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var rawMediaDetections: MutableMap<String, List<dev.xiaoxin.testgrounds.ml.Detection>> = mutableMapOf()

    init {
        initializeDetector()
        observeSettings()
        refreshMedia()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.confidenceThreshold,
                settingsRepository.iouThreshold,
                settingsRepository.censoredClasses
            ) { conf, iou, classes -> Triple(conf, iou, classes) }
                .collect { (conf, iou, classes) ->
                    yoloDetector.updateConfig(conf, iou, classes)
                    reclassify(conf, classes)
                }
        }
    }

    private fun reclassify(confidence: Float, classes: Set<String>) {
        val state = _uiState.value
        if (state.censoredMedia.isEmpty() && state.verifiedMedia.isEmpty()) return
        val all = (state.censoredMedia + state.verifiedMedia).map { item ->
            val detections = rawMediaDetections[item.id] ?: item.detections
            val isCensored = detections.any { d -> d.confidence >= confidence && classes.contains(d.className.lowercase()) }
            item.copy(isCensored = isCensored, detections = detections)
        }
        _uiState.update { it.copy(
            censoredMedia = all.filter { it.isCensored },
            verifiedMedia = all.filter { !it.isCensored }
        ) }
    }

    fun refreshMedia() {
        loadMedia()
    }

    fun importMedia(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val success = mediaRepository.importFromUri(uri)
            if (!success) {
                _uiState.update { it.copy(error = "Failed to import selected media") }
            }
            refreshMedia()
        }
    }

    private fun initializeDetector() {
        viewModelScope.launch {
            try {
                yoloDetector.initialize()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to initialize detector: ${e.message}") }
            }
        }
    }

    private fun loadMedia() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // Combine images and videos
                combine(
                    mediaRepository.getAllImages(),
                    mediaRepository.getAllVideos()
                ) { images, videos ->
                    images + videos
                }.collect { allMedia ->
                    processMediaItems(allMedia)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to load media: ${e.message}"
                    )
                }
            }
        }
    }

    private suspend fun processMediaItems(mediaItems: List<MediaItem>) {
        val processedItems = mutableListOf<MediaItem>()
        val currentConf = settingsRepository.confidenceThreshold.first()
        val currentClasses = settingsRepository.censoredClasses.first().map { it.lowercase() }.toSet()

        mediaItems.forEachIndexed { index, item ->
            try {
                val bitmap = loadBitmapFromUri(item.uri)
                if (bitmap != null) {
                    val detections = yoloDetector.detectObjects(bitmap)
                    rawMediaDetections[item.id] = detections
                    val isCensored = detections.any { d -> d.confidence >= currentConf && currentClasses.contains(d.className.lowercase()) }

                    val processedItem = item.copy(
                        isCensored = isCensored,
                        detections = detections
                    )
                    processedItems.add(processedItem)
                }

                // Update progress
                val progress = (index + 1).toFloat() / mediaItems.size
                _uiState.update { it.copy(processingProgress = progress) }

            } catch (e: Exception) {
                // Continue with next item if this one fails
                processedItems.add(item)
            }
        }

        // Separate censored and verified media
        val censoredMedia = processedItems.filter { it.isCensored }
        val verifiedMedia = processedItems.filter { !it.isCensored }

        _uiState.update {
            it.copy(
                censoredMedia = censoredMedia,
                verifiedMedia = verifiedMedia,
                isLoading = false,
                processingProgress = 1f
            )
        }
    }

    private fun loadBitmapFromUri(uri: android.net.Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        yoloDetector.release()
    }
}