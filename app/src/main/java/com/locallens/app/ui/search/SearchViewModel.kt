package com.locallens.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locallens.app.data.db.entities.Person
import com.locallens.app.data.repository.PersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val personRepo: PersonRepository
) : ViewModel() {

    private val _results = MutableStateFlow<List<Person>>(emptyList())
    val results: StateFlow<List<Person>> = _results.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    fun search(query: String) {
        _query.value = query
        viewModelScope.launch {
            if (query.isBlank()) {
                _results.value = emptyList()
                return@launch
            }
            val all = personRepo.getAll()
            _results.value = all.filter { person ->
                person.displayName.contains(query, ignoreCase = true)
            }
        }
    }
}
