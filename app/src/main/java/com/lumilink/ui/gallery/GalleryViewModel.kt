package com.lumilink.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumilink.data.PhotoDownloader
import com.lumilink.data.PhotoRepository
import com.lumilink.di.AppContainer
import com.lumilink.model.CameraPhoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the gallery: loads the camera's photos, tracks which are selected, and downloads them.
 * The whole screen renders from a single immutable [UiState] snapshot.
 */
class GalleryViewModel(
    private val photoRepository: PhotoRepository,
    private val photoDownloader: PhotoDownloader,
) : ViewModel() {

    /** Everything the gallery screen needs to render, in one immutable object. */
    data class UiState(
        val loading: Boolean = false,
        val photos: List<CameraPhoto> = emptyList(),
        val selectedIds: Set<String> = emptySet(),
        val error: String? = null,
        val downloading: Boolean = false,
        val downloadProgress: Float = 0f,
        val statusMessage: String? = null,
    ) {
        val selectedPhotos: List<CameraPhoto> get() = photos.filter { it.id in selectedIds }
        val selectedBytes: Long get() = selectedPhotos.sumOf { it.sizeBytes ?: 0L }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)load the photo list from the camera. */
    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val photos = photoRepository.listPhotos()
                _uiState.update { it.copy(loading = false, photos = photos) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Couldn't load photos")
                }
            }
        }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            if (!updated.add(id)) updated.remove(id) // add returns false if already present
            state.copy(selectedIds = updated)
        }
    }

    /** Download all selected photos, one after another, updating overall progress. */
    fun downloadSelected() {
        val selected = _uiState.value.selectedPhotos
        if (selected.isEmpty() || _uiState.value.downloading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(downloading = true, downloadProgress = 0f, error = null, statusMessage = null)
            }
            var completed = 0
            try {
                for (photo in selected) {
                    photoDownloader.download(photo) { fileProgress ->
                        // Overall progress = finished files + current file's fraction, averaged.
                        val overall = (completed + fileProgress) / selected.size
                        _uiState.update { it.copy(downloadProgress = overall) }
                    }
                    completed++
                }
                _uiState.update {
                    it.copy(
                        downloading = false,
                        downloadProgress = 1f,
                        selectedIds = emptySet(),
                        statusMessage = "Saved $completed photo(s) to your device",
                    )
                }
            } catch (e: Exception) {
                // Surface download problems as a transient message; keep the grid visible.
                _uiState.update {
                    it.copy(
                        downloading = false,
                        statusMessage = "Download failed after $completed of ${selected.size}: ${e.message}",
                    )
                }
            }
        }
    }

    fun consumeStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                GalleryViewModel(container.photoRepository, container.photoDownloader)
            }
        }
    }
}
