package com.locallens.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.locallens.app.data.db.entities.MediaFile
import com.locallens.app.data.db.entities.Person
import com.locallens.app.data.media.MediaStoreScanner
import com.locallens.app.data.repository.MediaRepository
import com.locallens.app.data.repository.PersonRepository
import com.locallens.app.work.MediaIndexWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepo: MediaRepository,
    private val personRepo: PersonRepository,
    private val scanner: MediaStoreScanner
) : ViewModel() {

    private val _mediaFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    val mediaFiles: StateFlow<List<MediaFile>> = _mediaFiles.asStateFlow()

    private val _persons = MutableStateFlow<List<Person>>(emptyList())
    val persons: StateFlow<List<Person>> = _persons.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    init {
        loadMedia()
        loadPersons()
    }

    private fun loadMedia() {
        viewModelScope.launch {
            mediaRepo.getAllMediaFiles().collect { files ->
                _mediaFiles.value = files.sortedByDescending { it.dateAdded }
            }
        }
    }

    private fun loadPersons() {
        viewModelScope.launch {
            personRepo.getConfirmedPersons().collect { people ->
                _persons.value = people
            }
        }
    }

    fun startIndexing() {
        viewModelScope.launch {
            _isIndexing.value = true
            val newFiles = mutableListOf<Long>()
            scanner.scanAll().collect { mediaFile ->
                val existing = mediaRepo.getByMediaStoreId(mediaFile.mediaStoreId)
                if (existing == null) {
                    val id = mediaRepo.upsert(mediaFile)
                    newFiles.add(id)
                }
            }

            if (newFiles.isNotEmpty()) {
                enqueuePendingWork(newFiles)
            }
            _isIndexing.value = false
        }
    }

    private fun enqueuePendingWork(pendingIds: List<Long>) {
        val batches = pendingIds.chunked(MediaIndexWorker.BATCH_SIZE)
        val requests = batches.map { batch ->
            OneTimeWorkRequestBuilder<MediaIndexWorker>()
                .setInputData(workDataOf(MediaIndexWorker.KEY_MEDIA_FILE_IDS to batch.toLongArray()))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
        }
        WorkManager.getInstance(context).enqueue(requests)
    }
}
