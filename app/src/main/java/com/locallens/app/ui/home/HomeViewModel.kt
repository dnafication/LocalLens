package com.locallens.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val mediaFiles: List<MediaFile> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadMediaFiles()
    }

    private fun loadMediaFiles() {
        viewModelScope.launch {
            mediaRepository.getAllMediaFiles().collect { files ->
                _uiState.value = HomeUiState(
                    isLoading = false,
                    mediaFiles = files
                )
            }
        }
    }

    fun onSearchClick() {
        // Navigation handled by screen
    }

    fun onSettingsClick() {
        // Navigation handled by screen
    }
}
