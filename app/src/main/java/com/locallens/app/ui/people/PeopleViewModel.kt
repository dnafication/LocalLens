package com.locallens.app.ui.people

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
class PeopleViewModel @Inject constructor(
    private val personRepo: PersonRepository
) : ViewModel() {

    private val _persons = MutableStateFlow<List<Person>>(emptyList())
    val persons: StateFlow<List<Person>> = _persons.asStateFlow()

    init {
        loadPersons()
    }

    private fun loadPersons() {
        viewModelScope.launch {
            personRepo.getConfirmedPersons().collect { people ->
                _persons.value = people.sortedWith(
                    compareByDescending<Person> { it.displayName.isNotEmpty() }
                        .thenBy { it.displayName.ifEmpty { "Unknown" } }
                )
            }
        }
    }

    fun createPerson(name: String) {
        viewModelScope.launch {
            personRepo.createPerson(name)
            loadPersons()
        }
    }
}
