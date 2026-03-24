package com.locallens.app.ui.photo_detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.locallens.app.data.db.entities.FaceDetection
import com.locallens.app.data.db.entities.Person

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    mediaFileId: Long,
    onNavigateBack: () -> Unit,
    onPersonClick: (Long) -> Unit,
    viewModel: PhotoDetailViewModel = hiltViewModel()
) {
    val mediaFile by viewModel.mediaFile.collectAsState()
    val faces by viewModel.faces.collectAsState()
    val persons by viewModel.persons.collectAsState()
    var selectedFace by remember { mutableStateOf<FaceDetection?>(null) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(mediaFileId) {
        viewModel.loadMedia(mediaFileId)
    }

    if (selectedFace != null) {
        val face = selectedFace!!
        val personId = face.person.targetId
        val person = persons[personId]
        ModalBottomSheet(onDismissRequest = { selectedFace = null }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = person?.displayName?.ifEmpty { "Unknown" } ?: "Unknown",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    if (person != null) {
                        TextButton(onClick = {
                            onPersonClick(person.id)
                            selectedFace = null
                        }) {
                            Text("View ${person.displayName}")
                        }
                    }
                    TextButton(onClick = {
                        viewModel.unassignFace(face.id)
                        selectedFace = null
                    }) {
                        Text("This isn't ${person?.displayName ?: "them"}")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = mediaFile?.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            imageSize = coordinates.size
                        }
                )

                if (faces.isNotEmpty() && imageSize != IntSize.Zero) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (face in faces) {
                            val left = face.boxLeft * size.width
                            val top = face.boxTop * size.height
                            val right = face.boxRight * size.width
                            val bottom = face.boxBottom * size.height
                            drawRect(
                                color = Color.Yellow,
                                topLeft = Offset(left, top),
                                size = Size(right - left, bottom - top),
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                }
            }

            // Person chips
            if (persons.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(persons.values.toList()) { person ->
                        AssistChip(
                            onClick = { onPersonClick(person.id) },
                            label = { Text(person.displayName.ifEmpty { "Unknown" }) }
                        )
                    }
                }
            }
        }
    }
}
