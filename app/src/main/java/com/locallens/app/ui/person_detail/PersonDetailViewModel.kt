package com.locallens.app.ui.person_detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.db.entities.Person
import com.locallens.app.data.repository.FaceRepository
import com.locallens.app.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PersonDetailUiState(
    val isLoading: Boolean = true,
    val person: Person? = null,
    val mediaFiles: List<MediaFile> = emptyList()
)

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personRepository: PersonRepository,
    private val faceRepository: FaceRepository
) : ViewModel() {

    private val personId: Long = savedStateHandle.get<Long>("personId") ?: 0L

    private val _uiState = MutableStateFlow(PersonDetailUiState())
    val uiState: StateFlow<PersonDetailUiState> = _uiState.asStateFlow()

    init {
        loadPersonDetail()
    }

    private fun loadPersonDetail() {
        viewModelScope.launch {
            val person = personRepository.getById(personId)
            faceRepository.getMediaFilesForPerson(personId).collect { mediaFiles ->
                _uiState.value = PersonDetailUiState(
                    isLoading = false,
                    person = person,
                    mediaFiles = mediaFiles
                )
            }
        }
    }

    fun renamePerson(newName: String) {
        viewModelScope.launch {
            personRepository.rename(personId, newName)
            val updated = personRepository.getById(personId)
            _uiState.value = _uiState.value.copy(person = updated)
        }
    }
}
