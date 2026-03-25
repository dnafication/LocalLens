package com.locallens.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.media.MediaStoreScanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = false,
    val mediaFiles: List<MediaFile> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * Load all media files directly from MediaStore. Call this once media permissions are granted.
     * The home grid shows ALL photos/videos for browsing; background workers handle face indexing.
     */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = HomeUiState(isLoading = true)
            val mediaFiles = mutableListOf<MediaFile>()
            mediaStoreScanner.scanAll().collect { file ->
                mediaFiles.add(file)
            }
            _uiState.value = HomeUiState(
                isLoading = false,
                mediaFiles = mediaFiles.sortedByDescending { it.dateAdded }
            )
        }
    }
}
