package com.locallens.app.ui.person_detail

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

@HiltViewModel
class PersonDetailViewModel @Inject constructor(
    private val personRepo: PersonRepository,
    private val faceRepo: FaceRepository
) : ViewModel() {

    private val _person = MutableStateFlow<Person?>(null)
    val person: StateFlow<Person?> = _person.asStateFlow()

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    fun loadPerson(personId: Long) {
        viewModelScope.launch {
            _person.value = personRepo.getById(personId)
            faceRepo.getMediaFilesForPerson(personId).collect { files ->
                _mediaFiles.value = files
            }
        }
    }

    fun renamePerson(personId: Long, newName: String) {
        viewModelScope.launch {
            personRepo.rename(personId, newName)
            _person.value = personRepo.getById(personId)
        }
    }

    fun deletePerson(personId: Long) {
        viewModelScope.launch {
            personRepo.deletePerson(personId)
        }
    }
}
