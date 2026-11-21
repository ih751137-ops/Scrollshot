package com.example.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.AlbumEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.ImageFormat
import com.example.ui.components.DeleteScreenshotsConfirmDialog
import com.example.ui.components.ExportImageDialog
import com.example.ui.components.ImageDetailsDialog
import com.example.ui.components.MoveToAlbumDialog
import com.example.ui.components.RenameScreenshotDialog
import com.example.ui.components.formatBytes
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenshotDetailScreen(
    screenshots: List<ScreenshotEntity>,
    initialScreenshotId: Long,
    albums: List<AlbumEntity>,
    defaultJpegQuality: Int,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onExtractText: (Long) -> Unit,
    onExportPdf: (Long) -> Unit,
    onExportImage: (ScreenshotEntity, ImageFormat, Int, Boolean) -> Unit,
    onExportImageToUri: ((ScreenshotEntity, Uri, ImageFormat, Int) -> Unit)? = null,
    onDuplicate: (ScreenshotEntity) -> Unit,
    onCopyClipboard: (ScreenshotEntity) -> Unit,
    onShare: (ScreenshotEntity) -> Unit,
    onRename: (Long, String) -> Unit,
    onToggleFavorite: (ScreenshotEntity) -> Unit,
    onMoveToAlbum: (Long, Long?) -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRetake: () -> Unit,
    onDelete: (ScreenshotEntity) -> Unit
) {
    if (screenshots.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Screenshot not found.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
        return
    }

    val startIndex = remember(initialScreenshotId, screenshots.size) {
        screenshots.indexOfFirst { it.id == initialScreenshotId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = startIndex) { screenshots.size }
    val coroutineScope = rememberCoroutineScope()

    val currentShot = screenshots[pagerState.currentPage.coerceIn(0, screenshots.lastIndex)]
    val albumMap = remember(albums) { albums.associateBy { it.id } }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveAlbumDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var isImmersive by remember { mutableStateOf(false) }

    var activeZoomScale by remember { mutableFloatStateOf(1f) }
    var resetZoomTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(pagerState.currentPage) {
        activeZoomScale = 1f
    }

    if (showRenameDialog) {
        RenameScreenshotDialog(
            initialTitle = currentShot.title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle -> onRename(currentShot.id, newTitle) }
        )
    }

    if (showDeleteConfirm) {
        val isLastItem = screenshots.size <= 1
        DeleteScreenshotsConfirmDialog(
            count = 1,
            singleTitle = currentShot.title,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                onDelete(currentShot)
                if (isLastItem) onBack()
            }
        )
    }

    if (showMoveAlbumDialog) {
        MoveToAlbumDialog(
            albums = albums,
            currentAlbumId = currentShot.albumId,
            onDismiss = { showMoveAlbumDialog = false },
            onSelectAlbum = { albumId -> onMoveToAlbum(currentShot.id, albumId) },
            onCreateAlbum = onCreateAlbum
        )
    }

    if (showExportDialog) {
        ExportImageDialog(
            screenshot = currentShot,
            defaultQuality = defaultJpegQuality,
            onDismiss = { showExportDialog = false },
            onExport = { fmt, q, addToGallery ->
                onExportImage(currentShot, fmt, q, addToGallery)
            },
            onExportToUri = onExportImageToUri?.let { handler ->
                { destUri, fmt, q -> handler(currentShot, destUri, fmt, q) }
            }
        )
    }

    if (showDetailsDialog) {
        ImageDetailsDialog(
            screenshot = currentShot,
            albumName = currentShot.albumId?.let { albumMap[it]?.name },
            onDismiss = { showDetailsDialog = false }
        )
    }

    val currentFile = remember(currentShot.filePath) {
        if (currentShot.filePath.isNotBlank()) File(currentShot.filePath) else null
    }
    val displayFilename = remember(currentShot.title, currentShot.format, currentShot.filePath) {
        val ext = currentFile?.extension?.takeIf { it.isNotBlank() } ?: currentShot.format.lowercase(Locale.US)
        "${currentShot.title}.$ext"
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            AnimatedVisibility(visible = !isImmersive, enter = fadeIn(), exit = fadeOut()) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xCC0B132B),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = {
                        Column {
                            Text(
                                text = displayFilename,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("preview_filename_text")
                            )
                            Text(
                                text = "${currentShot.width}×${currentShot.height} • ${formatBytes(currentShot.fileSizeBytes)} • ${currentShot.format}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.testTag("preview_metadata_text")
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("preview_back_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { onToggleFavorite(currentShot) },
                            modifier = Modifier.testTag("preview_favorite_btn")
                        ) {
                            Icon(
                                imageVector = if (currentShot.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentShot.isFavorite) Color(0xFFEF4444) else Color.White
                            )
                        }
                        IconButton(
                            onClick = { showDetailsDialog = true },
                            modifier = Modifier.testTag("preview_details_btn")
                        ) {
                            Icon(Icons.Rounded.Info, contentDescription = "Image details")
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("preview_more_btn")
                            ) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate File") },
                                    leadingIcon = { Icon(Icons.Rounded.FileCopy, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onDuplicate(currentShot) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to Clipboard") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onCopyClipboard(currentShot) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Album") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; showMoveAlbumDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export PNG / JPEG") },
                                    leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; showExportDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export as PDF") },
                                    leadingIcon = { Icon(Icons.Rounded.PictureAsPdf, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onExportPdf(currentShot.id) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Extract Text (OCR)") },
                                    leadingIcon = { Icon(Icons.Rounded.DocumentScanner, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onExtractText(currentShot.id) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Retake Screenshot") },
                                    leadingIcon = { Icon(Icons.Rounded.CameraAlt, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onRetake() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { showOverflowMenu = false; showDeleteConfirm = true }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = !isImmersive, enter = fadeIn(), exit = fadeOut()) {
                NavigationBar(containerColor = Color(0xE60B132B), contentColor = Color.White) {
                    NavigationBarItem(
                        selected = false,
                        onClick = { onEdit(currentShot.id) },
                        icon = { Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color.White) },
                        label = { Text("Edit", color = Color.White) },
                        modifier = Modifier.testTag("preview_edit_btn")
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showExportDialog = true },
                        icon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null, tint = Color.White) },
                        label = { Text("Export", color = Color.White) },
                        modifier = Modifier.testTag("preview_export_btn")
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { onShare(currentShot) },
                        icon = { Icon(Icons.Rounded.Share, contentDescription = null, tint = Color.White) },
                        label = { Text("Share", color = Color.White) },
                        modifier = Modifier.testTag("preview_share_btn")
                    )
                    NavigationBarItem(
                        selected = false,
                        onClick = { showDeleteConfirm = true },
                        icon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFCA5A5)) },
                        label = { Text("Delete", color = Color(0xFFFCA5A5)) },
                        modifier = Modifier.testTag("preview_delete_btn")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = activeZoomScale <= 1.02f,
                key = { idx -> screenshots[idx].id },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("preview_pager")
            ) { page ->
                val shot = screenshots[page]
                ZoomableScreenshotPage(
                    screenshot = shot,
                    resetTrigger = if (page == pagerState.currentPage) resetZoomTrigger else 0,
                    onScaleChanged = { newScale ->
                        if (page == pagerState.currentPage) activeZoomScale = newScale
                    },
                    onSingleTap = { isImmersive = !isImmersive }
                )
            }

            // Zoom Pill Overlay
            if (activeZoomScale > 1.05f) {
                Surface(
                    color = Color(0xCC0F172A),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(activeZoomScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.testTag("preview_zoom_percent")
                        )
                        Spacer(Modifier.width(6.dp))
                        TextButton(onClick = {
                            resetZoomTrigger++
                            activeZoomScale = 1f
                        }) {
                            Text("Reset", color = Color(0xFF22D3EE))
                        }
                    }
                }
            }

            // Adjacent Navigation Controls
            if (!isImmersive && screenshots.size > 1 && activeZoomScale <= 1.02f) {
                if (pagerState.currentPage > 0) {
                    Surface(
                        color = Color(0x990F172A),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            },
                            modifier = Modifier.testTag("preview_prev_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous screenshot",
                                tint = Color.White
                            )
                        }
                    }
                }

                if (pagerState.currentPage < screenshots.lastIndex) {
                    Surface(
                        color = Color(0x990F172A),
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            modifier = Modifier.testTag("preview_next_btn")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next screenshot",
                                tint = Color.White
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xAA0F172A),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${screenshots.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .testTag("preview_page_indicator")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomableScreenshotPage(
    screenshot: ScreenshotEntity,
    resetTrigger: Int,
    onScaleChanged: (Float) -> Unit,
    onSingleTap: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember(screenshot.id) { mutableFloatStateOf(1f) }
    var pan by remember(screenshot.id) { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            scale = 1f
            pan = Offset.Zero
            onScaleChanged(1f)
        }
    }

    fun clampPan(rawPan: Offset, targetScale: Float): Offset {
        if (targetScale <= 1.01f || containerSize == IntSize.Zero) return Offset.Zero
        val maxX = (containerSize.width * (targetScale - 1f)) / 2f
        val maxY = (containerSize.height * (targetScale - 1f)) / 2f
        return Offset(
            x = rawPan.x.coerceIn(-maxX, maxX),
            y = rawPan.y.coerceIn(-maxY, maxY)
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 5f)
        scale = nextScale
        pan = clampPan(pan + panChange, nextScale)
        onScaleChanged(nextScale)
    }

    val file = remember(screenshot.filePath) {
        if (screenshot.filePath.isNotBlank()) File(screenshot.filePath) else null
    }
    val hasLocalFile = remember(file) { file != null && file.exists() && file.length() > 0L }
    val imageModel: Any? = remember(screenshot.filePath, screenshot.mediaStoreUri, hasLocalFile) {
        when {
            hasLocalFile -> file
            !screenshot.mediaStoreUri.isNullOrBlank() -> runCatching { Uri.parse(screenshot.mediaStoreUri) }.getOrNull()
            else -> null
        }
    }
    val request = remember(screenshot.filePath, screenshot.mediaStoreUri, screenshot.updatedAt) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .size(2048, 8192)
            .crossfade(true)
            .build()
    }

    val isTall = screenshot.height > screenshot.width * 2.2f

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .pointerInput(screenshot.id) {
                detectTapGestures(
                    onDoubleTap = { tapOffset ->
                        if (scale > 1.05f) {
                            scale = 1f
                            pan = Offset.Zero
                            onScaleChanged(1f)
                        } else {
                            val targetScale = 2.5f
                            val centerX = containerSize.width / 2f
                            val centerY = containerSize.height / 2f
                            val focusOffset = Offset(
                                x = (centerX - tapOffset.x) * (targetScale - 1f),
                                y = (centerY - tapOffset.y) * (targetScale - 1f)
                            )
                            scale = targetScale
                            pan = clampPan(focusOffset, targetScale)
                            onScaleChanged(targetScale)
                        }
                    },
                    onTap = { onSingleTap() }
                )
            }
            .transformable(
                state = transformState,
                lockRotationOnZoomPan = true,
                canPan = { scale > 1.02f }
            )
            .testTag("preview_zoom_surface_${screenshot.id}")
    ) {
        if (imageModel == null) {
            MissingOrCorruptedPreviewPlaceholder()
        } else {
            val imageModifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                }

            if (isTall) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState(), enabled = scale <= 1.02f)
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = screenshot.title,
                        contentScale = ContentScale.FillWidth,
                        loading = {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(260.dp)) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        },
                        error = { MissingOrCorruptedPreviewPlaceholder() },
                        modifier = imageModifier
                    )
                }
            } else {
                SubcomposeAsyncImage(
                    model = request,
                    contentDescription = screenshot.title,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = { MissingOrCorruptedPreviewPlaceholder() },
                    modifier = imageModifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MissingOrCorruptedPreviewPlaceholder() {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = "Image unavailable",
                tint = Color(0xFFFCA5A5),
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Image file is missing or unreadable",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
