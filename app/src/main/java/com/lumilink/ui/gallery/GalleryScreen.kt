package com.lumilink.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.lumilink.model.CameraPhoto
import com.lumilink.ui.appContainer

/**
 * Gallery screen — the camera's photos in a selectable grid with a download action bar.
 *
 * @param onBack called when the user taps the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    // Show download results / failures as a transient snackbar, then clear them.
    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photos on camera")
                        if (state.photos.isNotEmpty()) {
                            Text(
                                "${state.photos.size} items",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::load, enabled = !state.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
        bottomBar = {
            if (state.selectedIds.isNotEmpty() || state.downloading) {
                DownloadBar(
                    selectedCount = state.selectedIds.size,
                    selectedBytes = state.selectedBytes,
                    downloading = state.downloading,
                    progress = state.downloadProgress,
                    onDownload = viewModel::downloadSelected,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> LoadError(state.error!!, onRetry = viewModel::load)
                state.photos.isEmpty() -> Text("No photos found on the camera.")
                else -> PhotoGrid(
                    photos = state.photos,
                    selectedIds = state.selectedIds,
                    onToggle = viewModel::toggleSelection,
                )
            }
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<CameraPhoto>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoCell(
                photo = photo,
                selected = photo.id in selectedIds,
                onClick = { onToggle(photo.id) },
            )
        }
    }
}

@Composable
private fun PhotoCell(photo: CameraPhoto, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.thumbnailUrl,
            contentDescription = photo.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Type badge (JPG / RAW) — technical label, top-left.
        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp),
        ) {
            Text(
                text = if (photo.isRaw) "RAW" else "JPG",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
            )
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(2.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadBar(
    selectedCount: Int,
    selectedBytes: Long,
    downloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column {
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$selectedCount selected · ${selectedBytes / 1_000_000} MB",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onDownload, enabled = !downloading) {
                    Text(if (downloading) "Downloading…" else "Download")
                }
            }
        }
    }
}

@Composable
private fun LoadError(message: String, onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}
