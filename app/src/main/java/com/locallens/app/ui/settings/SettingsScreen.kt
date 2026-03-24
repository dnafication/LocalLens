package com.locallens.app.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val indexScreenshots by viewModel.indexScreenshots.collectAsState()
    val indexVideos by viewModel.indexVideos.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            item {
                ListItem(
                    headlineContent = { Text("Index Screenshots") },
                    supportingContent = { Text("Include screenshots in face detection") },
                    trailingContent = {
                        Switch(
                            checked = indexScreenshots,
                            onCheckedChange = { viewModel.setIndexScreenshots(it) }
                        )
                    }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Index Videos") },
                    supportingContent = { Text("Extract and analyze faces from video frames") },
                    trailingContent = {
                        Switch(
                            checked = indexVideos,
                            onCheckedChange = { viewModel.setIndexVideos(it) }
                        )
                    }
                )
                HorizontalDivider()
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("Re-run Clustering") },
                    supportingContent = { Text("Re-analyze face groups") },
                    trailingContent = {
                        TextButton(onClick = { viewModel.rerunClustering() }) {
                            Text("Run")
                        }
                    }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("About") },
                    supportingContent = { Text("LocalLens v1.0 - On-device face recognition") }
                )
            }
        }
    }
}
