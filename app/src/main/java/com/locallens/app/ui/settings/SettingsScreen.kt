package com.locallens.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ListItem(
                headlineContent = { Text("Index screenshots") },
                supportingContent = { Text("Include screenshots in face scanning") },
                trailingContent = {
                    Switch(
                        checked = uiState.indexScreenshots,
                        onCheckedChange = viewModel::onToggleScreenshots
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Index videos") },
                supportingContent = { Text("Extract faces from video frames") },
                trailingContent = {
                    Switch(
                        checked = uiState.indexVideos,
                        onCheckedChange = viewModel::onToggleVideos
                    )
                }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Re-run clustering") },
                supportingContent = { Text("Regroup all detected faces into people") },
                modifier = Modifier
            )

            HorizontalDivider()

            ListItem(
                headlineContent = { Text("Re-index all media") },
                supportingContent = { Text("Scan and process all photos and videos again") }
            )

            HorizontalDivider()

            ListItem(
                headlineContent = {
                    Text("Delete all face data", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text("Remove all detected faces and people. Media files are not affected.") }
            )
        }
    }
}
