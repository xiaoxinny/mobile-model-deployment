package dev.xiaoxin.testgrounds.repository

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import dev.xiaoxin.testgrounds.data.MediaItem
import dev.xiaoxin.testgrounds.data.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val ROOT_DIR_NAME = "testgrounds_media"
        private const val IMAGES_SUBDIR = "images"
        private const val VIDEOS_SUBDIR = "videos"
        val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp")
        val VIDEO_EXTENSIONS = setOf(".mp4", ".mkv", ".webm", ".mov")
    }

    private val rootDir: File by lazy {
        File(context.getExternalFilesDir(null), ROOT_DIR_NAME).apply { mkdirs() }
    }
    private val imagesDir: File by lazy { File(rootDir, IMAGES_SUBDIR).apply { mkdirs() } }
    private val videosDir: File by lazy { File(rootDir, VIDEOS_SUBDIR).apply { mkdirs() } }

    // Public path info (can be shown to user)
    fun getImagesDirectoryPath(): String = imagesDir.absolutePath
    fun getVideosDirectoryPath(): String = videosDir.absolutePath

    fun getAllImages(): Flow<List<MediaItem>> = flow {
        emit(scanDirectoryForMedia(imagesDir, MediaType.IMAGE))
    }.flowOn(Dispatchers.IO)

    fun getAllVideos(): Flow<List<MediaItem>> = flow {
        emit(scanDirectoryForMedia(videosDir, MediaType.VIDEO))
    }.flowOn(Dispatchers.IO)

    private fun scanDirectoryForMedia(dir: File, type: MediaType): List<MediaItem> {
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val list = dir.listFiles()?.toList().orEmpty()
        val filtered = list.filter { file ->
            val nameLower = file.name.lowercase()
            when (type) {
                MediaType.IMAGE -> IMAGE_EXTENSIONS.any { nameLower.endsWith(it) }
                MediaType.VIDEO -> VIDEO_EXTENSIONS.any { nameLower.endsWith(it) }
            }
        }
        return filtered.sortedByDescending { it.lastModified() }
            .map { file ->
                MediaItem(
                    id = file.absolutePath, // unique identifier
                    uri = file.toUri(),
                    type = type,
                    isCensored = false,
                    detections = emptyList(),
                    dateAdded = Date(file.lastModified())
                )
            }
    }

    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val cr = context.contentResolver
            val mime = cr.getType(uri) ?: "application/octet-stream"
            val isImage = mime.startsWith("image/")
            val isVideo = mime.startsWith("video/")
            if (!isImage && !isVideo) return@withContext false

            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.let { "." + it } ?: run {
                if (isImage) ".jpg" else if (isVideo) ".mp4" else ""
            }
            val targetDir = if (isImage) imagesDir else videosDir
            targetDir.mkdirs()
            val fileName = "import_${System.currentTimeMillis()}${ext}"
            val targetFile = File(targetDir, fileName)
            cr.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }
}