package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FilterAltOff
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlbumEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.DateFilter
import com.example.data.model.GallerySort
import com.example.data.model.ImageFormat
import com.example.data.storage.GalleryStorageAccessMode
import com.example.ui.components.DeleteScreenshotsConfirmDialog
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.GalleryGridCard
import com.example.ui.components.ImageDetailsDialog
import com.example.ui.components.ManageAlbumsDialog
import com.example.ui.components.MoveToAlbumDialog
import com.example.ui.components.RenameScreenshotDialog
import com.example.ui.viewmodel.GalleryFilter
import com.example.ui.viewmodel.GalleryUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    screenshots: List<ScreenshotEntity>,
    allFilteredScreenshots: List<ScreenshotEntity>,
    albums: List<AlbumEntity>,
    uiState: GalleryUiState,
    hasMorePages: Boolean,
    defaultJpegQuality: Int,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSortChange: (GallerySort) -> Unit,
    onFilterChange: (GalleryFilter, Long?) -> Unit,
    onDateFilterChange: (DateFilter, Long?) -> Unit,
    onClearAllFilters: () -> Unit,
    onEnterSelectionMode: (Long) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onToggleFavorite: (ScreenshotEntity) -> Unit,
    onRenameScreenshot: (Long, String) -> Unit,
    onDuplicateScreenshot: (ScreenshotEntity) -> Unit,
    onCopyToClipboard: (ScreenshotEntity) -> Unit,
    onDeleteScreenshot: (ScreenshotEntity) -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: (List<ScreenshotEntity>) -> Unit,
    onMoveScreenshotsToAlbum: (List<Long>, Long?) -> Unit,
    onRemoveFromAlbum: (List<Long>) -> Unit,
    onExportSelectedToPdf: (List<Long>) -> Unit,
    onExportImage: (ScreenshotEntity, ImageFormat, Int, Boolean) -> Unit,
    onExportImageToUri: ((ScreenshotEntity, Uri, ImageFormat, Int) -> Unit)? = null,
    onRequestLegacyGalleryPermission: (() -> Unit)? = null,
    onDismissLegacyStorageBanner: (() -> Unit)? = null,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    onImportPhotoUri: (Uri) -> Unit,
    onOpenDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showManageAlbumsDialog by remember { mutableStateOf(false) }
    var showBulkMoveAlbumDialog by remember { mutableStateOf(false) }
    var showBulkDeleteConfirm by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    var renamingTarget by remember { mutableStateOf<ScreenshotEntity?>(null) }
    var deletingTarget by remember { mutableStateOf<ScreenshotEntity?>(null) }
    var movingTarget by remember { mutableStateOf<ScreenshotEntity?>(null) }
    var detailsTarget by remember { mutableStateOf<ScreenshotEntity?>(null) }
    var exportingTarget by remember { mutableStateOf<ScreenshotEntity?>(null) }

    val albumMap = remember(albums) { albums.associateBy { it.id } }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportPhotoUri(uri)
    }

    if (showManageAlbumsDialog) {
        ManageAlbumsDialog(
            albums = albums,
            onDismiss = { showManageAlbumsDialog = false },
            onCreateAlbum = onCreateAlbum,
            onRenameAlbum = onRenameAlbum,
            onDeleteAlbum = onDeleteAlbum
        )
    }

    if (showBulkMoveAlbumDialog) {
        MoveToAlbumDialog(
            albums = albums,
            currentAlbumId = null,
            onDismiss = { showBulkMoveAlbumDialog = false },
            onSelectAlbum = { albumId ->
                onMoveScreenshotsToAlbum(uiState.selectedIds.toList(), albumId)
            },
            onCreateAlbum = onCreateAlbum
        )
    }

    if (showBulkDeleteConfirm) {
        DeleteScreenshotsConfirmDialog(
            count = uiState.selectedIds.size,
            onDismiss = { showBulkDeleteConfirm = false },
            onConfirm = onDeleteSelected
        )
    }

    renamingTarget?.let { target ->
        RenameScreenshotDialog(
            initialTitle = target.title,
            onDismiss = { renamingTarget = null },
            onConfirm = { newTitle -> onRenameScreenshot(target.id, newTitle) }
        )
    }

    deletingTarget?.let { target ->
        DeleteScreenshotsConfirmDialog(
            count = 1,
            singleTitle = target.title,
            onDismiss = { deletingTarget = null },
            onConfirm = { onDeleteScreenshot(target) }
        )
    }

    movingTarget?.let { target ->
        MoveToAlbumDialog(
            albums = albums,
            currentAlbumId = target.albumId,
            onDismiss = { movingTarget = null },
            onSelectAlbum = { albumId -> onMoveScreenshotsToAlbum(listOf(target.id), albumId) },
            onCreateAlbum = onCreateAlbum
        )
    }

    detailsTarget?.let { target ->
        ImageDetailsDialog(
            screenshot = target,
            albumName = target.albumId?.let { albumMap[it]?.name },
            isCorrupted = target.id in uiState.corruptedIds,
            onDismiss = { detailsTarget = null }
        )
    }

    exportingTarget?.let { target ->
        ExportImageDialog(
            screenshot = target,
            defaultQuality = defaultJpegQuality,
            onDismiss = { exportingTarget = null },
            onExport = { fmt, q, addToGallery ->
                onExportImage(target, fmt, q, addToGallery)
            },
            onExportToUri = onExportImageToUri?.let { handler ->
                { destUri, fmt, q -> handler(target, destUri, fmt, q) }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.customDateMillis ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis
                        if (picked != null) {
                            onDateFilterChange(DateFilter.CUSTOM_DATE, picked)
                        }
                        showDatePicker = false
                    }
                ) { Text("Filter") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top Header or Bulk Selection Bar
        if (uiState.isSelectionMode) {
            val selectedItems = remember(allFilteredScreenshots, uiState.selectedIds) {
                allFilteredScreenshots.filter { it.id in uiState.selectedIds }
            }
            val allSelected = allFilteredScreenshots.isNotEmpty() &&
                uiState.selectedIds.size >= allFilteredScreenshots.size
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("gallery_selection_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onDeselectAll,
                        modifier = Modifier.testTag("selection_close_btn")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Deselect all")
                    }
                    Text(
                        text = "${uiState.selectedIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("selection_count_text")
                    )
                    IconButton(
                        onClick = if (allSelected) onDeselectAll else onSelectAll,
                        modifier = Modifier.testTag("selection_toggle_all_btn")
                    ) {
                        Icon(
                            imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                            contentDescription = if (allSelected) "Deselect all" else "Select all"
                        )
                    }
                    IconButton(
                        onClick = { onShareSelected(selectedItems) },
                        modifier = Modifier.testTag("selection_share_btn")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share selected")
                    }
                    IconButton(
                        onClick = { showBulkMoveAlbumDialog = true },
                        modifier = Modifier.testTag("selection_move_btn")
                    ) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move to album")
                    }
                    IconButton(onClick = { onExportSelectedToPdf(uiState.selectedIds.toList()) }) {
                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = "Export selected to PDF")
                    }
                    IconButton(
                        onClick = { showBulkDeleteConfirm = true },
                        modifier = Modifier.testTag("selection_delete_btn")
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete selected",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.gallery_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.totalCount} screenshots • ${uiState.favoritesCount} favorites",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("gallery_refresh_btn")
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh gallery")
                    }
                    IconButton(
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("gallery_import_btn")
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = stringResource(R.string.gallery_import_photo))
                    }
                    IconButton(
                        onClick = { showManageAlbumsDialog = true },
                        modifier = Modifier.testTag("gallery_manage_albums_btn")
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = stringResource(R.string.gallery_manage_albums))
                    }
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.testTag("gallery_sort_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort screenshots")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            val sortOptions = listOf(
                                GallerySort.NEWEST to stringResource(R.string.sort_newest),
                                GallerySort.OLDEST to stringResource(R.string.sort_oldest),
                                GallerySort.LARGEST to stringResource(R.string.sort_largest),
                                GallerySort.DIMENSIONS to stringResource(R.string.sort_dimensions)
                            )
                            sortOptions.forEach { (sortOption, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = if (uiState.sort == sortOption) {
                                        { Icon(Icons.Rounded.Check, contentDescription = "Selected") }
                                    } else null,
                                    onClick = {
                                        onSortChange(sortOption)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.isRefreshing && screenshots.isNotEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.gallery_search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("gallery_search_input")
        )

        Spacer(Modifier.height(8.dp))

        // Album / Collection Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.filter == GalleryFilter.ALL,
                    onClick = { onFilterChange(GalleryFilter.ALL, null) },
                    label = { Text("${stringResource(R.string.gallery_all_album)} (${uiState.totalCount})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.filter == GalleryFilter.LONG_ONLY,
                    onClick = { onFilterChange(GalleryFilter.LONG_ONLY, null) },
                    label = { Text("${stringResource(R.string.gallery_long_album)} (${uiState.longCount})") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.filter == GalleryFilter.FAVORITES,
                    onClick = { onFilterChange(GalleryFilter.FAVORITES, null) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text("${stringResource(R.string.gallery_favorites_album)} (${uiState.favoritesCount})") },
                    modifier = Modifier.testTag("filter_favorites_chip")
                )
            }
            items(albums, key = { it.id }) { album ->
                FilterChip(
                    selected = uiState.filter == GalleryFilter.CUSTOM_ALBUM && uiState.selectedAlbumId == album.id,
                    onClick = { onFilterChange(GalleryFilter.CUSTOM_ALBUM, album.id) },
                    label = { Text(album.name) },
                    modifier = Modifier.testTag("filter_album_chip_${album.id}")
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Date Filter Chips
        val customDateLabel = remember(uiState.customDateMillis) {
            val ms = uiState.customDateMillis
            if (ms == null) "Pick Date"
            else {
                val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.format(Date(ms))
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.dateFilter == DateFilter.ALL_DATES,
                    onClick = { onDateFilterChange(DateFilter.ALL_DATES, null) },
                    label = { Text("Any Date") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.dateFilter == DateFilter.TODAY,
                    onClick = { onDateFilterChange(DateFilter.TODAY, null) },
                    label = { Text("Today") },
                    modifier = Modifier.testTag("date_filter_today")
                )
            }
            item {
                FilterChip(
                    selected = uiState.dateFilter == DateFilter.LAST_7_DAYS,
                    onClick = { onDateFilterChange(DateFilter.LAST_7_DAYS, null) },
                    label = { Text("Last 7 Days") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.dateFilter == DateFilter.LAST_30_DAYS,
                    onClick = { onDateFilterChange(DateFilter.LAST_30_DAYS, null) },
                    label = { Text("Last 30 Days") }
                )
            }
            item {
                FilterChip(
                    selected = uiState.dateFilter == DateFilter.CUSTOM_DATE,
                    onClick = { showDatePicker = true },
                    leadingIcon = {
                        Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(customDateLabel) },
                    modifier = Modifier.testTag("date_filter_custom")
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (uiState.storageAccessMode == GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED &&
            !uiState.legacyStorageBannerDismissed
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .testTag("legacy_storage_permission_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.storage_legacy_permission_denied_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    if (onRequestLegacyGalleryPermission != null) {
                        TextButton(onClick = onRequestLegacyGalleryPermission) {
                            Text(stringResource(R.string.storage_enable_gallery_sync))
                        }
                    }
                    if (onDismissLegacyStorageBanner != null) {
                        IconButton(
                            onClick = onDismissLegacyStorageBanner,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Dismiss")
                        }
                    }
                }
            }
        }

        when {
            uiState.isLoading && screenshots.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("gallery_loading_indicator")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.gallery_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            screenshots.isEmpty() && uiState.totalCount == 0 -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("gallery_empty_state")
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_empty_gallery),
                            contentDescription = "Empty gallery illustration",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(200.dp)
                                .clip(MaterialTheme.shapes.large)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.gallery_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.gallery_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.gallery_import_photo))
                        }
                    }
                }
            }

            screenshots.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("gallery_filtered_empty_state")
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.FilterAltOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No Matching Screenshots",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Try adjusting your search query, album, or date filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onClearAllFilters,
                            modifier = Modifier.testTag("gallery_clear_filters_btn")
                        ) {
                            Text(stringResource(R.string.gallery_clear_filters))
                        }
                    }
                }
            }

            else -> {
                val gridState = rememberLazyGridState()
                val shouldLoadMore by remember(screenshots.size, hasMorePages) {
                    derivedStateOf {
                        if (!hasMorePages) false
                        else {
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            lastVisible >= (screenshots.size - 6).coerceAtLeast(0)
                        }
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) onLoadMore()
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("gallery_grid")
                ) {
                    items(screenshots, key = { it.id }) { shot ->
                        val selected = shot.id in uiState.selectedIds
                        GalleryGridCard(
                            screenshot = shot,
                            albumName = shot.albumId?.let { albumMap[it]?.name },
                            isSelected = selected,
                            isSelectionMode = uiState.isSelectionMode,
                            isCorrupted = shot.id in uiState.corruptedIds,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    onToggleSelect(shot.id)
                                } else {
                                    onOpenDetail(shot.id)
                                }
                            },
                            onLongClick = { onEnterSelectionMode(shot.id) },
                            onToggleFavorite = { onToggleFavorite(shot) },
                            onRename = { renamingTarget = shot },
                            onShare = { onShareSelected(listOf(shot)) },
                            onDelete = { deletingTarget = shot },
                            onDuplicate = { onDuplicateScreenshot(shot) },
                            onCopyClipboard = { onCopyToClipboard(shot) },
                            onMoveToAlbum = { movingTarget = shot },
                            onRemoveFromAlbum = { onRemoveFromAlbum(listOf(shot.id)) },
                            onViewDetails = { detailsTarget = shot },
                            onExportImage = { exportingTarget = shot }
                        )
                    }

                    if (hasMorePages) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
