package dev.xiaoxin.testgrounds.data

import android.net.Uri
import java.util.Date

data class MediaItem(
    val id: String,
    val uri: Uri,
    val type: MediaType,
    val isCensored: Boolean,
    val detections: List<dev.xiaoxin.testgrounds.ml.Detection>,
    val dateAdded: Date,
    val thumbnailUri: Uri? = null
)

enum class MediaType {
    IMAGE, VIDEO
}