package com.locallens.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.locallens.app.data.db.entities.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onPersonClick: (Long) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { viewModel.search(it) },
                        placeholder = { Text("Search people...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(results, key = { it.id }) { person ->
                PersonSearchResult(
                    person = person,
                    onClick = { onPersonClick(person.id) }
                )
            }
        }
    }
}

@Composable
private fun PersonSearchResult(
    person: Person,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(person.displayName.ifEmpty { "Unknown #${person.id}" }) },
        supportingContent = { Text("${person.faceCount} photos") },
        leadingContent = {
            Icon(Icons.Default.Person, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
    HorizontalDivider()
}
