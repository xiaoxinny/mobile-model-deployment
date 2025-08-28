package dev.xiaoxin.testgrounds.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.xiaoxin.testgrounds.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val confidenceThreshold: Float = 0.25f,
    val iouThreshold: Float = 0.45f,
    val censoredClasses: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.confidenceThreshold,
                settingsRepository.iouThreshold,
                settingsRepository.censoredClasses
            ) { confidence, iou, classes -> Triple(confidence, iou, classes) }
                .collect { (conf, iou, classes) ->
                    _uiState.update { it.copy(confidenceThreshold = conf, iouThreshold = iou, censoredClasses = classes, isLoading = false) }
                }
        }
    }

    fun updateConfidence(value: Float) {
        viewModelScope.launch { settingsRepository.setConfidenceThreshold(value) }
    }
    fun updateIou(value: Float) {
        viewModelScope.launch { settingsRepository.setIouThreshold(value) }
    }

    fun toggleClass(className: String) {
        val current = _uiState.value.censoredClasses
        val updated = if (className in current) current - className else current + className
        viewModelScope.launch { settingsRepository.setCensoredClasses(updated) }
    }
}
