package com.locallens.app.ui.photo_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.db.entities.Person
import com.locallens.app.data.repository.FaceRepository
import com.locallens.app.data.repository.MediaRepository
import com.locallens.app.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PhotoDetailUiState(
    val isLoading: Boolean = true,
    val mediaFile: MediaFile? = null,
    val facePersonPairs: List<Pair<FaceDetection, Person?>> = emptyList()
)

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaRepository: MediaRepository,
    private val faceRepository: FaceRepository,
    private val personRepository: PersonRepository
) : ViewModel() {

    private val mediaFileId: Long = savedStateHandle.get<Long>("mediaFileId") ?: 0L

    private val _uiState = MutableStateFlow(PhotoDetailUiState())
    val uiState: StateFlow<PhotoDetailUiState> = _uiState.asStateFlow()

    init {
        loadPhotoDetail()
    }

    private fun loadPhotoDetail() {
        viewModelScope.launch {
            val mediaFile = mediaRepository.getById(mediaFileId)
            val faces = faceRepository.getByMediaFile(mediaFileId)
            val facePersonPairs = faces.map { face ->
                val personId = face.person.targetId
                val person = if (personId > 0) personRepository.getById(personId) else null
                Pair(face, person)
            }

            _uiState.value = PhotoDetailUiState(
                isLoading = false,
                mediaFile = mediaFile,
                facePersonPairs = facePersonPairs
            )
        }
    }
}
