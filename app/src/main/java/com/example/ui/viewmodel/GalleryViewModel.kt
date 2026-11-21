package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollShotApp
import com.example.capture.CapturePhase
import com.example.capture.ScreenCaptureService
import com.example.data.local.AlbumEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.DateFilter
import com.example.data.model.GallerySort
import com.example.data.model.ImageFormat
import com.example.data.storage.ExportImageResult
import com.example.data.storage.GalleryStorageAccessMode
import com.example.data.storage.StoragePermissionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class GalleryFilter {
    ALL,
    LONG_ONLY,
    FAVORITES,
    CUSTOM_ALBUM
}

data class GalleryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val searchQuery: String = "",
    val sort: GallerySort = GallerySort.NEWEST,
    val filter: GalleryFilter = GalleryFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ALL_DATES,
    val customDateMillis: Long? = null,
    val selectedAlbumId: Long? = null,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val corruptedIds: Set<Long> = emptySet(),
    val storageDirectoryPath: String = "",
    val storageAccessMode: GalleryStorageAccessMode = GalleryStorageAccessMode.SCOPED_MEDIA_STORE,
    val legacyStorageBannerDismissed: Boolean = false,
    val totalCount: Int = 0,
    val favoritesCount: Int = 0,
    val longCount: Int = 0,
    val visiblePageSize: Int = PAGE_SIZE,
    val statusMessage: String? = null,
    val lastExportedFile: ExportImageResult? = null
) {
    companion object {
        const val PAGE_SIZE = 30
    }
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScrollShotApp
    private val repository = app.repository

    private val _uiState = MutableStateFlow(
        GalleryUiState(
            storageDirectoryPath = repository.storage.capturesDirectory.absolutePath,
            storageAccessMode = StoragePermissionHelper.getAccessMode(app)
        )
    )
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    val allScreenshots: StateFlow<List<ScreenshotEntity>> = repository.allScreenshots
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAlbums: StateFlow<List<AlbumEntity>> = repository.allAlbums
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredScreenshots: StateFlow<List<ScreenshotEntity>> = combine(
        allScreenshots,
        _uiState
    ) { items, ui ->
        filterAndSortScreenshots(items, ui)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pagedScreenshots: StateFlow<List<ScreenshotEntity>> = combine(
        filteredScreenshots,
        _uiState.map { it.visiblePageSize }.distinctUntilChanged()
    ) { filtered, pageSize ->
        filtered.take(pageSize)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasMorePages: StateFlow<Boolean> = combine(
        filteredScreenshots,
        _uiState.map { it.visiblePageSize }.distinctUntilChanged()
    ) { filtered, pageSize ->
        filtered.size > pageSize
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        refreshGallery(silent = false)

        // Update counts automatically whenever Room screenshots change
        viewModelScope.launch {
            allScreenshots.collect { list ->
                val validIds = list.map { it.id }.toSet()
                _uiState.update { st ->
                    val prunedSelected = st.selectedIds.intersect(validIds)
                    st.copy(
                        totalCount = list.size,
                        favoritesCount = list.count { it.isFavorite },
                        longCount = list.count { it.isLongScreenshot },
                        selectedIds = prunedSelected,
                        isSelectionMode = prunedSelected.isNotEmpty()
                    )
                }
            }
        }

        // Automatically refresh gallery after a new capture completes
        viewModelScope.launch {
            ScreenCaptureService.sessionState
                .map { it.phase to it.lastCapturedScreenshotId }
                .distinctUntilChanged()
                .collect { (phase, _) ->
                    if (phase == CapturePhase.COMPLETED_SINGLE || phase == CapturePhase.COMPLETED_LONG) {
                        refreshGallery(silent = true)
                    }
                }
        }
    }

    fun refreshStorageAccessMode() {
        _uiState.update {
            it.copy(storageAccessMode = StoragePermissionHelper.getAccessMode(app))
        }
    }

    fun dismissLegacyStorageBanner() {
        _uiState.update { it.copy(legacyStorageBannerDismissed = true) }
    }

    fun refreshGallery(silent: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                if (silent) it.copy(isRefreshing = true)
                else it.copy(isLoading = it.totalCount == 0, isRefreshing = true)
            }
            val report = runCatching { repository.syncStorageWithDatabase() }.getOrNull()
            _uiState.update { st ->
                st.copy(
                    isLoading = false,
                    isRefreshing = false,
                    corruptedIds = report?.corruptedIds ?: st.corruptedIds,
                    storageDirectoryPath = report?.storageDirectoryPath?.ifBlank { st.storageDirectoryPath }
                        ?: st.storageDirectoryPath,
                    storageAccessMode = StoragePermissionHelper.getAccessMode(app)
                )
            }
        }
    }

    fun loadNextPage() {
        val total = filteredScreenshots.value.size
        val current = _uiState.value.visiblePageSize
        if (current < total) {
            _uiState.update { it.copy(visiblePageSize = it.visiblePageSize + GalleryUiState.PAGE_SIZE) }
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.update { it.copy(searchQuery = q, visiblePageSize = GalleryUiState.PAGE_SIZE) }
    }

    fun setGallerySort(sort: GallerySort) {
        _uiState.update { it.copy(sort = sort, visiblePageSize = GalleryUiState.PAGE_SIZE) }
    }

    fun setGalleryFilter(filter: GalleryFilter, albumId: Long? = null) {
        _uiState.update {
            it.copy(
                filter = filter,
                selectedAlbumId = if (filter == GalleryFilter.CUSTOM_ALBUM) albumId else null,
                visiblePageSize = GalleryUiState.PAGE_SIZE
            )
        }
    }

    fun setDateFilter(dateFilter: DateFilter, customDateMillis: Long? = null) {
        _uiState.update {
            it.copy(
                dateFilter = dateFilter,
                customDateMillis = if (dateFilter == DateFilter.CUSTOM_DATE) customDateMillis else null,
                visiblePageSize = GalleryUiState.PAGE_SIZE
            )
        }
    }

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                filter = GalleryFilter.ALL,
                selectedAlbumId = null,
                dateFilter = DateFilter.ALL_DATES,
                customDateMillis = null,
                visiblePageSize = GalleryUiState.PAGE_SIZE
            )
        }
    }

    fun enterSelectionMode(initialId: Long) {
        _uiState.update { st ->
            val next = st.selectedIds + initialId
            st.copy(selectedIds = next, isSelectionMode = true)
        }
    }

    fun toggleSelectScreenshot(id: Long) {
        _uiState.update { st ->
            val next = if (id in st.selectedIds) st.selectedIds - id else st.selectedIds + id
            st.copy(selectedIds = next, isSelectionMode = next.isNotEmpty())
        }
    }

    fun selectAll() {
        val visibleIds = filteredScreenshots.value.map { it.id }.toSet()
        _uiState.update { it.copy(selectedIds = visibleIds, isSelectionMode = visibleIds.isNotEmpty()) }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun toggleFavorite(shot: ScreenshotEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(shot)
            _uiState.update {
                it.copy(
                    statusMessage = if (!shot.isFavorite) "Added to Favorites" else "Removed from Favorites"
                )
            }
        }
    }

    fun renameScreenshot(id: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Filename cannot be empty") }
            return
        }
        viewModelScope.launch {
            val ok = repository.renameScreenshot(id, trimmed)
            _uiState.update {
                it.copy(statusMessage = if (ok) "Renamed to $trimmed" else "Could not rename screenshot")
            }
        }
    }

    fun duplicateScreenshot(shot: ScreenshotEntity) {
        viewModelScope.launch {
            val copy = repository.duplicateScreenshot(shot)
            _uiState.update {
                it.copy(
                    statusMessage = if (copy != null) "Created copy: ${copy.title}" else "Failed to copy screenshot file"
                )
            }
        }
    }

    fun deleteScreenshot(shot: ScreenshotEntity) {
        viewModelScope.launch {
            repository.deleteScreenshot(shot)
            _uiState.update { it.copy(statusMessage = "Deleted \"${shot.title}\"") }
        }
    }

    fun deleteSelectedScreenshots() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val entities = repository.getScreenshotsByIds(ids)
            repository.deleteScreenshots(entities)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    statusMessage = "Deleted ${entities.size} screenshot(s)"
                )
            }
        }
    }

    fun moveScreenshotsToAlbum(ids: List<Long>, albumId: Long?) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.moveToAlbum(ids, albumId)
            val albumName = allAlbums.value.firstOrNull { it.id == albumId }?.name
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    statusMessage = if (albumName != null) "Moved to \"$albumName\"" else "Removed from album"
                )
            }
        }
    }

    fun moveSelectedToAlbum(albumId: Long?) {
        moveScreenshotsToAlbum(_uiState.value.selectedIds.toList(), albumId)
    }

    fun removeFromAlbum(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.removeFromAlbum(ids)
            _uiState.update {
                it.copy(
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    statusMessage = "Removed from album (images preserved in Gallery)"
                )
            }
        }
    }

    fun createAlbum(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.createAlbum(trimmed)
            _uiState.update { it.copy(statusMessage = "Created album \"$trimmed\"") }
        }
    }

    fun renameAlbum(albumId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            repository.renameAlbum(albumId, trimmed)
            _uiState.update { it.copy(statusMessage = "Renamed album to \"$trimmed\"") }
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch {
            repository.deleteAlbum(albumId)
            _uiState.update { st ->
                val resetFilter = st.filter == GalleryFilter.CUSTOM_ALBUM && st.selectedAlbumId == albumId
                st.copy(
                    filter = if (resetFilter) GalleryFilter.ALL else st.filter,
                    selectedAlbumId = if (resetFilter) null else st.selectedAlbumId,
                    statusMessage = "Album deleted. All screenshots were safely kept."
                )
            }
        }
    }

    fun exportScreenshot(
        shot: ScreenshotEntity,
        format: ImageFormat,
        jpegQuality: Int,
        addToGallery: Boolean
    ) {
        viewModelScope.launch {
            val result = repository.exportScreenshotAsFormat(shot, format, jpegQuality, addToGallery)
            _uiState.update {
                if (result != null) {
                    val msg = if (result.galleryPublished) {
                        "Exported ${result.displayName} (${format.name}) to Pictures/ScrollShot"
                    } else {
                        "Exported ${result.displayName} (${format.name}) to ScrollShot storage"
                    }
                    it.copy(
                        lastExportedFile = result,
                        statusMessage = msg
                    )
                } else {
                    it.copy(statusMessage = "Could not export image (source file missing or unreadable)")
                }
            }
        }
    }

    fun saveScreenshotToExternalUri(
        shot: ScreenshotEntity,
        destinationUri: Uri,
        format: ImageFormat,
        jpegQuality: Int
    ) {
        viewModelScope.launch {
            val res = repository.storage.saveImageToExternalUri(
                sourcePath = shot.filePath,
                sourceMediaStoreUri = shot.mediaStoreUri,
                destinationUri = destinationUri,
                format = format,
                jpegQuality = jpegQuality
            )
            _uiState.update {
                res.fold(
                    onSuccess = { _ -> it.copy(statusMessage = "Saved ${shot.title}.${format.extension} to chosen location") },
                    onFailure = { err -> it.copy(statusMessage = err.message ?: "Failed to save to chosen location") }
                )
            }
        }
    }

    fun copyImageToClipboard(filePath: String, mediaStoreUri: String? = null) {
        if (!repository.storage.isScreenshotAccessible(filePath, mediaStoreUri)) {
            _uiState.update { it.copy(statusMessage = "Image file is no longer accessible") }
            return
        }
        runCatching {
            repository.storage.copyImageToClipboard(filePath, mediaStoreUri)
            _uiState.update { it.copy(statusMessage = "Screenshot copied to clipboard") }
        }.onFailure {
            _uiState.update { it.copy(statusMessage = "Could not copy image to clipboard") }
        }
    }

    fun createShareImagesIntent(paths: List<String>): Intent? {
        return createShareImagesFromSourcesIntent(paths.map { it to null })
    }

    fun createShareImagesIntentForItems(items: List<ScreenshotEntity>): Intent? {
        return createShareImagesFromSourcesIntent(items.map { it.filePath to it.mediaStoreUri })
    }

    private fun createShareImagesFromSourcesIntent(sources: List<Pair<String, String?>>): Intent? {
        val valid = sources.filter { (path, uri) -> repository.storage.isScreenshotAccessible(path, uri) }
        if (valid.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No accessible image files found to share") }
            return null
        }
        return repository.storage.createShareImagesFromSourcesIntent(valid)
    }

    fun createShareFileIntent(filePath: String, mimeType: String): Intent? {
        if (!File(filePath).exists()) {
            _uiState.update { it.copy(statusMessage = "Exported file not found") }
            return null
        }
        return repository.storage.createShareFileIntent(filePath, mimeType)
    }

    fun importPhoto(uri: Uri, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.importFromUriResult(uri)
            result.onSuccess { outcome ->
                _uiState.update {
                    it.copy(
                        statusMessage = outcome.galleryWarning ?: "Imported \"${outcome.entity.title}\""
                    )
                }
                onImported(outcome.entity.id)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusMessage = err.message ?: "Failed to import selected image.")
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun clearLastExportedFile() {
        _uiState.update { it.copy(lastExportedFile = null) }
    }

    companion object {
        internal fun filterAndSortScreenshots(
            items: List<ScreenshotEntity>,
            ui: GalleryUiState,
            nowMillis: Long = System.currentTimeMillis()
        ): List<ScreenshotEntity> {
            val byCollection = when (ui.filter) {
                GalleryFilter.ALL -> items
                GalleryFilter.LONG_ONLY -> items.filter { it.isLongScreenshot }
                GalleryFilter.FAVORITES -> items.filter { it.isFavorite }
                GalleryFilter.CUSTOM_ALBUM -> items.filter { it.albumId == ui.selectedAlbumId }
            }

            val byDate = when (ui.dateFilter) {
                DateFilter.ALL_DATES -> byCollection
                DateFilter.TODAY -> {
                    val start = startOfDayMillis(nowMillis)
                    byCollection.filter { it.createdAt >= start }
                }
                DateFilter.LAST_7_DAYS -> {
                    val start = startOfDayMillis(nowMillis) - 6L * DAY_MILLIS
                    byCollection.filter { it.createdAt >= start }
                }
                DateFilter.LAST_30_DAYS -> {
                    val start = startOfDayMillis(nowMillis) - 29L * DAY_MILLIS
                    byCollection.filter { it.createdAt >= start }
                }
                DateFilter.CUSTOM_DATE -> {
                    val targetUtc = ui.customDateMillis
                    if (targetUtc == null) byCollection
                    else {
                        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            timeInMillis = targetUtc
                        }
                        val targetYear = utcCal.get(Calendar.YEAR)
                        val targetDay = utcCal.get(Calendar.DAY_OF_YEAR)
                        byCollection.filter { shot ->
                            val localCal = Calendar.getInstance().apply { timeInMillis = shot.createdAt }
                            localCal.get(Calendar.YEAR) == targetYear &&
                                localCal.get(Calendar.DAY_OF_YEAR) == targetDay
                        }
                    }
                }
            }

            val query = ui.searchQuery.trim().lowercase(Locale.getDefault())
            val byQuery = if (query.isEmpty()) {
                byDate
            } else {
                byDate.filter { shot ->
                    val fileName = File(shot.filePath).name.lowercase(Locale.getDefault())
                    shot.title.lowercase(Locale.getDefault()).contains(query) ||
                        fileName.contains(query) ||
                        (shot.extractedText?.lowercase(Locale.getDefault())?.contains(query) == true)
                }
            }

            return when (ui.sort) {
                GallerySort.NEWEST -> byQuery.sortedByDescending { it.createdAt }
                GallerySort.OLDEST -> byQuery.sortedBy { it.createdAt }
                GallerySort.LARGEST -> byQuery.sortedByDescending { it.fileSizeBytes }
                GallerySort.DIMENSIONS -> byQuery.sortedByDescending { it.width.toLong() * it.height.toLong() }
            }
        }

        private fun startOfDayMillis(nowMillis: Long): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }

        private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}
