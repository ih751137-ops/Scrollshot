package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ScreenshotEntity
import com.example.data.model.ImageFormat
import com.example.data.model.UserPreferences
import com.example.editor.CropAspect
import com.example.editor.EditorColorPalette
import com.example.editor.EditorOperation
import com.example.editor.EditorTool
import com.example.editor.NormRect
import com.example.editor.ShapeKind
import com.example.ui.viewmodel.EditorUiState
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun EditorScreen(
    uiState: EditorUiState,
    preferences: UserPreferences,
    availableScreenshots: List<ScreenshotEntity>,
    onSelectScreenshot: (Long) -> Unit,
    onImportPhoto: (android.net.Uri) -> Unit,
    onSelectTool: (EditorTool) -> Unit,
    onSelectColor: (Int) -> Unit,
    onStrokeWidthChange: (Float) -> Unit,
    onShapeKindChange: (ShapeKind) -> Unit,
    onShapeFilledChange: (Boolean) -> Unit,
    onTextPillChange: (Boolean) -> Unit,
    onApplyOperation: (EditorOperation) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onSave: (Boolean, ImageFormat, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportPhoto(uri)
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showResizeDialog by remember { mutableStateOf(false) }
    var pendingTextPos by remember { mutableStateOf<Offset?>(null) }
    var textDraft by remember { mutableStateOf("") }

    // Live interactive overlay coordinates (normalized 0..1)
    val liveStrokePoints = remember { mutableStateListOf<Offset>() }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var cropRect by remember(uiState.previewBitmap) { mutableStateOf(NormRect(0.08f, 0.08f, 0.92f, 0.92f)) }
    var cropAspect by remember { mutableStateOf(CropAspect.FREE) }

    if (uiState.screenshot == null || uiState.previewBitmap == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Loading image into studio…")
            } else {
                Icon(
                    Icons.Rounded.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.editor_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.editor_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.gallery_import_photo))
                }
                if (availableScreenshots.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "Pick from Recent Screenshots",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(availableScreenshots.take(12), key = { it.id }) { shot ->
                            Card(
                                onClick = { onSelectScreenshot(shot.id) },
                                modifier = Modifier.width(116.dp)
                            ) {
                                Column {
                                    AsyncImage(
                                        model = File(shot.filePath),
                                        contentDescription = shot.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(128.dp)
                                    )
                                    Text(
                                        text = shot.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    val bmp = uiState.previewBitmap

    // Dialogs
    if (pendingTextPos != null) {
        AlertDialog(
            onDismissRequest = { pendingTextPos = null; textDraft = "" },
            title = { Text("Add Text Annotation") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = textDraft,
                        onValueChange = { textDraft = it },
                        label = { Text("Annotation text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.textBackgroundPill,
                            onCheckedChange = onTextPillChange
                        )
                        Text("Contrast background pill")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val pos = pendingTextPos
                    if (pos != null && textDraft.isNotBlank()) {
                        onApplyOperation(
                            EditorOperation.TextLabel(
                                position = pos,
                                text = textDraft.trim(),
                                colorArgb = uiState.selectedColorArgb,
                                sizeNorm = 0.048f,
                                backgroundPill = uiState.textBackgroundPill
                            )
                        )
                    }
                    pendingTextPos = null
                    textDraft = ""
                }) { Text("Place") }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextPos = null; textDraft = "" }) { Text("Cancel") }
            }
        )
    }

    if (showResizeDialog) {
        var widthInput by remember(bmp.width) { mutableStateOf(bmp.width.toString()) }
        var heightInput by remember(bmp.height) { mutableStateOf(bmp.height.toString()) }
        var lockAspect by remember { mutableStateOf(true) }
        val ratio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)

        AlertDialog(
            onDismissRequest = { showResizeDialog = false },
            title = { Text("Resize Image (${bmp.width}×${bmp.height})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.75f to "75%", 0.5f to "50%", 0.25f to "25%").forEach { (scale, label) ->
                            OutlinedButton(onClick = {
                                widthInput = (bmp.width * scale).toInt().coerceAtLeast(32).toString()
                                heightInput = (bmp.height * scale).toInt().coerceAtLeast(32).toString()
                            }) { Text(label) }
                        }
                    }
                    OutlinedTextField(
                        value = widthInput,
                        onValueChange = { v ->
                            widthInput = v.filter { it.isDigit() }
                            if (lockAspect) {
                                widthInput.toIntOrNull()?.let { w ->
                                    heightInput = (w / ratio).toInt().coerceAtLeast(32).toString()
                                }
                            }
                        },
                        label = { Text("Width (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = heightInput,
                        onValueChange = { v ->
                            heightInput = v.filter { it.isDigit() }
                            if (lockAspect) {
                                heightInput.toIntOrNull()?.let { h ->
                                    widthInput = (h * ratio).toInt().coerceAtLeast(32).toString()
                                }
                            }
                        },
                        label = { Text("Height (px)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = lockAspect, onCheckedChange = { lockAspect = it })
                        Text("Lock aspect ratio")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val w = widthInput.toIntOrNull() ?: bmp.width
                    val h = heightInput.toIntOrNull() ?: bmp.height
                    onApplyOperation(EditorOperation.Resize(w, h))
                    showResizeDialog = false
                }) { Text("Apply Resize") }
            },
            dismissButton = {
                TextButton(onClick = { showResizeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSaveDialog) {
        var saveFormat by remember { mutableStateOf(preferences.defaultFormat) }
        var saveQuality by remember { mutableFloatStateOf(preferences.jpegQuality.toFloat()) }
        var overwrite by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save Edited Screenshot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageFormat.entries.forEach { fmt ->
                            FilterChip(
                                selected = saveFormat == fmt,
                                onClick = { saveFormat = fmt },
                                label = { Text(fmt.name) }
                            )
                        }
                    }
                    if (saveFormat == ImageFormat.JPEG) {
                        Text("JPEG Quality: ${saveQuality.toInt()}%")
                        Slider(
                            value = saveQuality,
                            onValueChange = { saveQuality = it },
                            valueRange = 55f..100f
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = overwrite, onCheckedChange = { overwrite = it })
                        Text(
                            text = if (overwrite) "Overwrite original file" else "Save as new copy (preserves original)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSaveDialog = false
                    onSave(overwrite, saveFormat, saveQuality.toInt())
                }) {
                    Text(if (overwrite) stringResource(R.string.editor_overwrite) else stringResource(R.string.editor_save_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Editor Top Action Bar
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUndo, enabled = uiState.undoCount > 0, modifier = Modifier.testTag("editor_undo_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
                    }
                    IconButton(onClick = onRedo, enabled = uiState.redoCount > 0, modifier = Modifier.testTag("editor_redo_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
                    }
                    IconButton(onClick = onReset, enabled = uiState.undoCount > 0, modifier = Modifier.testTag("editor_reset_btn")) {
                        Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.editor_reset))
                    }
                }
                Text(
                    text = "${bmp.width}×${bmp.height}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = { showSaveDialog = true },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.testTag("editor_save_btn")
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save")
                }
            }
        }

        // Scrollable Image Canvas Workspace
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(
                    rememberScrollState(),
                    enabled = uiState.activeTool == EditorTool.VIEW || uiState.activeTool == EditorTool.ROTATE || uiState.activeTool == EditorTool.RESIZE
                )
                .padding(12.dp)
        ) {
            var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
            val imageRatio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageRatio.coerceIn(0.15f, 3f))
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(uiState.activeTool, uiState.selectedColorArgb, uiState.strokeWidthNorm, uiState.nextNumberBadge) {
                        when (uiState.activeTool) {
                            EditorTool.TEXT -> {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0.05f, 0.9f)
                                    val ny = (offset.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0.05f, 0.95f)
                                    pendingTextPos = Offset(nx, ny)
                                }
                            }
                            EditorTool.NUMBER -> {
                                detectTapGestures { offset ->
                                    val nx = (offset.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0.03f, 0.97f)
                                    val ny = (offset.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0.03f, 0.97f)
                                    onApplyOperation(
                                        EditorOperation.NumberBadge(
                                            position = Offset(nx, ny),
                                            number = uiState.nextNumberBadge,
                                            colorArgb = uiState.selectedColorArgb
                                        )
                                    )
                                }
                            }
                            EditorTool.PEN, EditorTool.HIGHLIGHTER -> {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        liveStrokePoints.clear()
                                        liveStrokePoints.add(
                                            Offset(
                                                (start.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                                (start.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        liveStrokePoints.add(
                                            Offset(
                                                (change.position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                                (change.position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                            )
                                        )
                                    },
                                    onDragEnd = {
                                        if (liveStrokePoints.size >= 2) {
                                            val width = if (uiState.activeTool == EditorTool.HIGHLIGHTER) {
                                                uiState.strokeWidthNorm * 2.5f
                                            } else {
                                                uiState.strokeWidthNorm
                                            }
                                            onApplyOperation(
                                                EditorOperation.Stroke(
                                                    points = liveStrokePoints.toList(),
                                                    colorArgb = uiState.selectedColorArgb,
                                                    strokeWidthNorm = width,
                                                    isHighlighter = uiState.activeTool == EditorTool.HIGHLIGHTER
                                                )
                                            )
                                        }
                                        liveStrokePoints.clear()
                                    }
                                )
                            }
                            EditorTool.ARROW, EditorTool.SHAPE, EditorTool.BLUR, EditorTool.PIXELATE, EditorTool.CROP -> {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val norm = Offset(
                                            (start.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (start.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                        dragStart = norm
                                        dragCurrent = norm
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val norm = Offset(
                                            (change.position.x / canvasSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
                                            (change.position.y / canvasSize.height.coerceAtLeast(1)).coerceIn(0f, 1f)
                                        )
                                        dragCurrent = norm
                                        if (uiState.activeTool == EditorTool.CROP && dragStart != null) {
                                            val s = dragStart!!
                                            cropRect = NormRect(
                                                min(s.x, norm.x),
                                                min(s.y, norm.y),
                                                max(s.x, norm.x),
                                                max(s.y, norm.y)
                                            )
                                        }
                                    },
                                    onDragEnd = {
                                        val s = dragStart
                                        val e = dragCurrent
                                        if (s != null && e != null) {
                                            when (uiState.activeTool) {
                                                EditorTool.ARROW -> {
                                                    onApplyOperation(
                                                        EditorOperation.Arrow(s, e, uiState.selectedColorArgb, uiState.strokeWidthNorm)
                                                    )
                                                }
                                                EditorTool.SHAPE -> {
                                                    onApplyOperation(
                                                        EditorOperation.Shape(
                                                            s, e,
                                                            uiState.selectedShapeKind,
                                                            uiState.selectedColorArgb,
                                                            uiState.strokeWidthNorm,
                                                            uiState.shapeFilled
                                                        )
                                                    )
                                                }
                                                EditorTool.BLUR, EditorTool.PIXELATE -> {
                                                    val nr = NormRect(s.x, s.y, e.x, e.y).normalized()
                                                    if (nr.width > 0.02f && nr.height > 0.02f) {
                                                        onApplyOperation(
                                                            EditorOperation.PrivacyMask(
                                                                rect = nr,
                                                                isPixelate = uiState.activeTool == EditorTool.PIXELATE,
                                                                blockSize = 18
                                                            )
                                                        )
                                                    }
                                                }
                                                else -> Unit
                                            }
                                        }
                                        dragStart = null
                                        dragCurrent = null
                                    }
                                )
                            }
                            else -> Unit
                        }
                    }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Editing preview",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize()
                )

                // Live interactive drawing overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (liveStrokePoints.size >= 2) {
                        val path = Path()
                        path.moveTo(liveStrokePoints.first().x * size.width, liveStrokePoints.first().y * size.height)
                        for (i in 1 until liveStrokePoints.size) {
                            path.lineTo(liveStrokePoints[i].x * size.width, liveStrokePoints[i].y * size.height)
                        }
                        val col = Color(uiState.selectedColorArgb).copy(
                            alpha = if (uiState.activeTool == EditorTool.HIGHLIGHTER) 0.45f else 1f
                        )
                        val strokePx = min(size.width, size.height) *
                            if (uiState.activeTool == EditorTool.HIGHLIGHTER) uiState.strokeWidthNorm * 2.5f else uiState.strokeWidthNorm
                        drawPath(path, color = col, style = Stroke(width = strokePx.coerceAtLeast(4f)))
                    }

                    val s = dragStart
                    val e = dragCurrent
                    if (s != null && e != null && uiState.activeTool != EditorTool.CROP) {
                        val sx = s.x * size.width
                        val sy = s.y * size.height
                        val ex = e.x * size.width
                        val ey = e.y * size.height
                        when (uiState.activeTool) {
                            EditorTool.ARROW -> {
                                drawLine(
                                    color = Color(uiState.selectedColorArgb),
                                    start = Offset(sx, sy),
                                    end = Offset(ex, ey),
                                    strokeWidth = 6f
                                )
                            }
                            EditorTool.SHAPE -> {
                                drawRect(
                                    color = Color(uiState.selectedColorArgb),
                                    topLeft = Offset(min(sx, ex), min(sy, ey)),
                                    size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                                    style = Stroke(width = 5f)
                                )
                            }
                            EditorTool.BLUR, EditorTool.PIXELATE -> {
                                drawRect(
                                    color = Color(0x8822D3EE),
                                    topLeft = Offset(min(sx, ex), min(sy, ey)),
                                    size = Size(kotlin.math.abs(ex - sx), kotlin.math.abs(ey - sy)),
                                    style = Stroke(width = 4f)
                                )
                            }
                            else -> Unit
                        }
                    }

                    if (uiState.activeTool == EditorTool.CROP) {
                        val l = cropRect.left * size.width
                        val t = cropRect.top * size.height
                        val r = cropRect.right * size.width
                        val b = cropRect.bottom * size.height
                        drawRect(
                            color = Color(0xFF22D3EE),
                            topLeft = Offset(l, t),
                            size = Size((r - l).coerceAtLeast(10f), (b - t).coerceAtLeast(10f)),
                            style = Stroke(width = 5f)
                        )
                    }
                }
            }
        }

        // Contextual Tool Sub-Controls
        Surface(tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (uiState.activeTool) {
                    EditorTool.CROP -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(CropAspect.entries) { aspect ->
                                    FilterChip(
                                        selected = cropAspect == aspect,
                                        onClick = {
                                            cropAspect = aspect
                                            aspect.ratio?.let { targetRatio ->
                                                val imgRatio = bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                                                val normH = 0.7f
                                                val normW = (normH * targetRatio / imgRatio).coerceIn(0.15f, 0.9f)
                                                val cx = 0.5f
                                                val cy = 0.5f
                                                cropRect = NormRect(
                                                    (cx - normW / 2f).coerceIn(0f, 1f),
                                                    (cy - normH / 2f).coerceIn(0f, 1f),
                                                    (cx + normW / 2f).coerceIn(0f, 1f),
                                                    (cy + normH / 2f).coerceIn(0f, 1f)
                                                )
                                            }
                                        },
                                        label = { Text(aspect.label) }
                                    )
                                }
                            }
                            Button(onClick = {
                                if (cropRect.width > 0.04f && cropRect.height > 0.04f) {
                                    onApplyOperation(EditorOperation.Crop(cropRect))
                                    onSelectTool(EditorTool.VIEW)
                                }
                            }) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Apply")
                            }
                        }
                    }

                    EditorTool.ROTATE -> {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(onClick = { onApplyOperation(EditorOperation.Rotate90(false)) }) {
                                Icon(Icons.AutoMirrored.Rounded.RotateLeft, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("-90°")
                            }
                            OutlinedButton(onClick = { onApplyOperation(EditorOperation.Rotate90(true)) }) {
                                Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("+90°")
                            }
                            OutlinedButton(onClick = { onApplyOperation(EditorOperation.Flip(true)) }) {
                                Icon(Icons.Rounded.Flip, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Flip H")
                            }
                        }
                    }

                    EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ARROW, EditorTool.SHAPE, EditorTool.TEXT, EditorTool.NUMBER -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            EditorColorPalette.forEach { argb ->
                                val selected = uiState.selectedColorArgb == argb
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(argb))
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                            shape = CircleShape
                                        )
                                        .clickable { onSelectColor(argb) }
                                )
                            }
                        }
                        if (uiState.activeTool == EditorTool.SHAPE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.selectedShapeKind == ShapeKind.RECTANGLE,
                                    onClick = { onShapeKindChange(ShapeKind.RECTANGLE) },
                                    label = { Text("Rectangle") }
                                )
                                FilterChip(
                                    selected = uiState.selectedShapeKind == ShapeKind.OVAL,
                                    onClick = { onShapeKindChange(ShapeKind.OVAL) },
                                    label = { Text("Oval") }
                                )
                                FilterChip(
                                    selected = uiState.shapeFilled,
                                    onClick = { onShapeFilledChange(!uiState.shapeFilled) },
                                    label = { Text("Filled") }
                                )
                            }
                        }
                        if (uiState.activeTool in listOf(EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.ARROW, EditorTool.SHAPE)) {
                            Slider(
                                value = uiState.strokeWidthNorm,
                                onValueChange = onStrokeWidthChange,
                                valueRange = 0.005f..0.035f
                            )
                        }
                    }

                    EditorTool.BLUR -> {
                        Text(
                            "Drag over sensitive text or faces on the image to blur them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    EditorTool.PIXELATE -> {
                        Text(
                            "Drag a rectangle over any region to pixelate it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        Text(
                            "Scroll to inspect or choose an editing tool below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Bottom Tool Picker Row
                val tools = listOf(
                    Triple(EditorTool.VIEW, Icons.Rounded.PanTool, "Scroll"),
                    Triple(EditorTool.CROP, Icons.Rounded.Crop, "Crop"),
                    Triple(EditorTool.ROTATE, Icons.AutoMirrored.Rounded.RotateRight, "Rotate"),
                    Triple(EditorTool.RESIZE, Icons.Rounded.PhotoSizeSelectLarge, "Resize"),
                    Triple(EditorTool.PEN, Icons.Rounded.BorderColor, "Pen"),
                    Triple(EditorTool.HIGHLIGHTER, Icons.Rounded.AspectRatio, "Highlight"),
                    Triple(EditorTool.ARROW, Icons.Rounded.NorthEast, "Arrow"),
                    Triple(EditorTool.SHAPE, Icons.Rounded.CropSquare, "Shape"),
                    Triple(EditorTool.TEXT, Icons.Rounded.Title, "Text"),
                    Triple(EditorTool.NUMBER, Icons.Rounded.FormatListNumbered, "Number"),
                    Triple(EditorTool.BLUR, Icons.Rounded.BlurOn, "Blur"),
                    Triple(EditorTool.PIXELATE, Icons.Rounded.GridOn, "Pixelate")
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(tools) { (tool, icon, label) ->
                        val active = uiState.activeTool == tool
                        FilterChip(
                            selected = active,
                            onClick = {
                                if (tool == EditorTool.RESIZE) {
                                    showResizeDialog = true
                                } else {
                                    onSelectTool(tool)
                                }
                            },
                            leadingIcon = { Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp)) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    }
}
