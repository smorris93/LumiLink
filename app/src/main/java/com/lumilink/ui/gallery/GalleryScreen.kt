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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.lumilink.model.CameraPhoto
import com.lumilink.ui.appContainer
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Gallery screen — the camera's photos in a lazy, selectable grid with a sort control and a
 * download action bar. Thumbnails prefetch a little ahead of the viewport (once scrolling settles)
 * so they're cached before the user reaches them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.statusMessage) {
        val message = state.statusMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeStatusMessage()
        }
    }

    val gridState = rememberLazyGridState()
    // Jump back to the top when the sort order changes.
    LaunchedEffect(state.sortOrder) { gridState.scrollToItem(0) }

    // Prefetch thumbnails a few rows ahead of the viewport, but only once scrolling settles — the
    // camera's tiny server can't take a flood of requests for rows the user is flinging past.
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val currentPhotos = rememberUpdatedState(state.orderedPhotos)
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .debounce(PREFETCH_DEBOUNCE_MS)
            .collect { lastVisible ->
                val photos = currentPhotos.value
                val end = minOf(lastVisible + PREFETCH_AHEAD, photos.lastIndex)
                for (i in (lastVisible + 1)..end) {
                    val url = photos.getOrNull(i)?.thumbnailUrl ?: continue
                    imageLoader.enqueue(ImageRequest.Builder(context).data(url).build())
                }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photos on camera") },
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.allPhotos.isNotEmpty()) {
                GalleryControls(
                    sortOrder = state.sortOrder,
                    totalCount = state.allPhotos.size,
                    onSort = viewModel::setSortOrder,
                )
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state.loading -> CircularProgressIndicator()
                    state.error != null -> LoadError(state.error!!, onRetry = viewModel::load)
                    state.allPhotos.isEmpty() -> Text("No photos found on the camera.")
                    else -> PhotoGrid(
                        gridState = gridState,
                        photos = state.orderedPhotos,
                        selectedIds = state.selectedIds,
                        onToggle = viewModel::toggleSelection,
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryControls(
    sortOrder: SortOrder,
    totalCount: Int,
    onSort: (SortOrder) -> Unit,
) {
    var sortMenuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TextButton(onClick = { sortMenuOpen = true }) {
                Text(if (sortOrder == SortOrder.NEWEST) "Newest ▾" else "Oldest ▾")
            }
            DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Newest first") },
                    onClick = { onSort(SortOrder.NEWEST); sortMenuOpen = false },
                )
                DropdownMenuItem(
                    text = { Text("Oldest first") },
                    onClick = { onSort(SortOrder.OLDEST); sortMenuOpen = false },
                )
            }
        }

        Text(
            text = "$totalCount photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

@Composable
private fun PhotoGrid(
    gridState: LazyGridState,
    photos: List<CameraPhoto>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
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

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
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
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
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
        Column(modifier = Modifier.navigationBarsPadding()) {
            if (downloading) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

private const val PREFETCH_AHEAD = 6 // thumbnails to warm ahead once scrolling settles
private const val PREFETCH_DEBOUNCE_MS = 200L // wait for scrolling to settle before prefetching
