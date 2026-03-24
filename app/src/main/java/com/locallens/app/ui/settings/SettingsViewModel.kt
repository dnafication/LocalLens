package com.locallens.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.locallens.app.work.ClusteringWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _indexScreenshots = MutableStateFlow(false)
    val indexScreenshots: StateFlow<Boolean> = _indexScreenshots.asStateFlow()

    private val _indexVideos = MutableStateFlow(true)
    val indexVideos: StateFlow<Boolean> = _indexVideos.asStateFlow()

    fun setIndexScreenshots(value: Boolean) {
        _indexScreenshots.value = value
    }

    fun setIndexVideos(value: Boolean) {
        _indexVideos.value = value
    }

    fun rerunClustering() {
        viewModelScope.launch {
            val request = OneTimeWorkRequestBuilder<ClusteringWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
