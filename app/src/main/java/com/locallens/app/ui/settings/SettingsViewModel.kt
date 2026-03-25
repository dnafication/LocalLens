package com.locallens.app.ui.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val indexScreenshots: Boolean = false,
    val indexVideos: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onToggleScreenshots(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(indexScreenshots = enabled)
    }

    fun onToggleVideos(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(indexVideos = enabled)
    }
}
