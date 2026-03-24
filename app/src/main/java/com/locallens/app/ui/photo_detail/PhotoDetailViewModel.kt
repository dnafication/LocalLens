package com.locallens.app.ui.photo_detail

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

@HiltViewModel
class PhotoDetailViewModel @Inject constructor(
    private val mediaRepo: MediaRepository,
    private val faceRepo: FaceRepository,
    private val personRepo: PersonRepository
) : ViewModel() {

    private val _mediaFile = MutableStateFlow<MediaFile?>(null)
    val mediaFile: StateFlow<MediaFile?> = _mediaFile.asStateFlow()

    private val _faces = MutableStateFlow<List<FaceDetection>>(emptyList())
    val faces: StateFlow<List<FaceDetection>> = _faces.asStateFlow()

    private val _persons = MutableStateFlow<Map<Long, Person>>(emptyMap())
    val persons: StateFlow<Map<Long, Person>> = _persons.asStateFlow()

    fun loadMedia(mediaFileId: Long) {
        viewModelScope.launch {
            _mediaFile.value = mediaRepo.getById(mediaFileId)
            val detections = faceRepo.getByMediaFile(mediaFileId)
            _faces.value = detections

            val personMap = mutableMapOf<Long, Person>()
            for (face in detections) {
                val personId = face.person.targetId
                if (personId != 0L) {
                    personRepo.getById(personId)?.let { personMap[personId] = it }
                }
            }
            _persons.value = personMap
        }
    }

    fun unassignFace(faceDetectionId: Long) {
        viewModelScope.launch {
            personRepo.splitFace(faceDetectionId)
            _mediaFile.value?.id?.let { loadMedia(it) }
        }
    }
}
