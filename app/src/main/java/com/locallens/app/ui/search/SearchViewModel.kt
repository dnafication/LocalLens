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

data class SearchUiState(
    val query: String = "",
    val results: List<Person> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val personRepository: PersonRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var allPersons: List<Person> = emptyList()

    init {
        loadPersons()
    }

    private fun loadPersons() {
        viewModelScope.launch {
            personRepository.getAllPersons().collect { persons ->
                allPersons = persons
                filterResults()
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        filterResults()
    }

    private fun filterResults() {
        val query = _uiState.value.query.trim()
        val filtered = if (query.isEmpty()) {
            allPersons
        } else {
            allPersons.filter {
                it.displayName.contains(query, ignoreCase = true)
            }
        }
        _uiState.value = _uiState.value.copy(results = filtered)
    }
}
