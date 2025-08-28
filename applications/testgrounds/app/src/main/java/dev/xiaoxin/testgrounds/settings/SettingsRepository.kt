package dev.xiaoxin.testgrounds.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_CONFIDENCE = floatPreferencesKey("confidence_threshold")
        private val KEY_IOU = floatPreferencesKey("iou_threshold")
        private val KEY_CLASSES = stringSetPreferencesKey("censored_classes")
        private const val DEFAULT_CONFIDENCE = 0.25f
        private const val DEFAULT_IOU = 0.45f
        private val DEFAULT_CLASSES = setOf("person", "car", "bicycle", "motorcycle", "bus", "train", "truck")
    }

    val confidenceThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_CONFIDENCE] ?: DEFAULT_CONFIDENCE
    }
    val iouThreshold: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[KEY_IOU] ?: DEFAULT_IOU
    }
    val censoredClasses: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_CLASSES] ?: DEFAULT_CLASSES
    }

    suspend fun setConfidenceThreshold(value: Float) {
        context.dataStore.edit { it[KEY_CONFIDENCE] = value }
    }
    suspend fun setIouThreshold(value: Float) {
        context.dataStore.edit { it[KEY_IOU] = value }
    }
    suspend fun setCensoredClasses(classes: Set<String>) {
        context.dataStore.edit { it[KEY_CLASSES] = classes }
    }
}
