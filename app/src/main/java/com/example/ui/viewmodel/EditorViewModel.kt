package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollShotApp
import com.example.data.local.ScreenshotEntity
import com.example.data.model.ImageFormat
import com.example.editor.EditorColorPalette
import com.example.editor.EditorOperation
import com.example.editor.EditorRenderer
import com.example.editor.EditorTool
import com.example.editor.ShapeKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val screenshot: ScreenshotEntity? = null,
    val previewBitmap: Bitmap? = null,
    val activeTool: EditorTool = EditorTool.VIEW,
    val selectedColorArgb: Int = EditorColorPalette.first(),
    val strokeWidthNorm: Float = 0.012f,
    val selectedShapeKind: ShapeKind = ShapeKind.RECTANGLE,
    val shapeFilled: Boolean = false,
    val textBackgroundPill: Boolean = true,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val nextNumberBadge: Int = 1,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val statusMessage: String? = null
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScrollShotApp
    private val repository = app.repository

    private var originalBitmap: Bitmap? = null
    private val undoStack = mutableListOf<EditorOperation>()
    private val redoStack = mutableListOf<EditorOperation>()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun loadScreenshot(screenshotId: Long) {
        viewModelScope.launch {
            val entity = repository.getScreenshotById(screenshotId)
            val current = _uiState.value.screenshot
            if (entity != null && current?.id == screenshotId && current.updatedAt == entity.updatedAt && originalBitmap != null) {
                return@launch
            }
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            if (entity == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Screenshot not found.") }
                return@launch
            }
            val bmp = withContext(Dispatchers.IO) {
                repository.storage.decodeBitmapSafely(entity.filePath, entity.mediaStoreUri)
            }
            if (bmp == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "Unable to decode image.") }
                return@launch
            }
            originalBitmap?.recycle()
            originalBitmap = bmp
            undoStack.clear()
            redoStack.clear()
            _uiState.value = EditorUiState(
                screenshot = entity,
                previewBitmap = bmp.copy(Bitmap.Config.ARGB_8888, false),
                isLoading = false
            )
        }
    }

    fun selectTool(tool: EditorTool) {
        _uiState.update { it.copy(activeTool = tool) }
    }

    fun selectColor(argb: Int) {
        _uiState.update { it.copy(selectedColorArgb = argb) }
    }

    fun setStrokeWidth(norm: Float) {
        _uiState.update { it.copy(strokeWidthNorm = norm.coerceIn(0.004f, 0.045f)) }
    }

    fun setShapeKind(kind: ShapeKind) {
        _uiState.update { it.copy(selectedShapeKind = kind) }
    }

    fun setShapeFilled(filled: Boolean) {
        _uiState.update { it.copy(shapeFilled = filled) }
    }

    fun setTextBackgroundPill(enabled: Boolean) {
        _uiState.update { it.copy(textBackgroundPill = enabled) }
    }

    fun applyOperation(op: EditorOperation) {
        undoStack.add(op)
        redoStack.clear()
        recomputePreview()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val removed = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(removed)
        recomputePreview()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val restored = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(restored)
        recomputePreview()
    }

    fun resetAll() {
        if (undoStack.isEmpty()) return
        undoStack.clear()
        redoStack.clear()
        recomputePreview()
    }

    private fun recomputePreview() {
        val base = originalBitmap ?: return
        viewModelScope.launch {
            val rendered = EditorRenderer.render(base, undoStack.toList())
            val nextBadge = undoStack.count { it is EditorOperation.NumberBadge } + 1
            _uiState.update { old ->
                old.copy(
                    previewBitmap = rendered,
                    undoCount = undoStack.size,
                    redoCount = redoStack.size,
                    nextNumberBadge = nextBadge
                )
            }
        }
    }

    fun saveEdited(overwrite: Boolean, format: ImageFormat, jpegQuality: Int, onSaved: (Long) -> Unit) {
        val base = originalBitmap ?: return
        val entity = _uiState.value.screenshot ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, statusMessage = null) }
            val finalBmp = EditorRenderer.render(base, undoStack.toList())
            val saveResult = if (overwrite) {
                repository.overwriteScreenshotResult(entity, finalBmp, format, jpegQuality)
            } else {
                repository.saveEditedCopyResult(
                    bitmap = finalBmp,
                    baseTitle = entity.title,
                    format = format,
                    jpegQuality = jpegQuality,
                    isLongScreenshot = entity.isLongScreenshot,
                    albumId = entity.albumId
                )
            }
            saveResult.onSuccess { outcome ->
                val savedEntity = outcome.entity
                if (overwrite) {
                    originalBitmap?.recycle()
                    originalBitmap = finalBmp
                    undoStack.clear()
                    redoStack.clear()
                    _uiState.update {
                        it.copy(
                            screenshot = savedEntity,
                            previewBitmap = finalBmp.copy(Bitmap.Config.ARGB_8888, false),
                            undoCount = 0,
                            redoCount = 0,
                            isSaving = false,
                            statusMessage = outcome.galleryWarning ?: "Saved ${savedEntity.title}"
                        )
                    }
                } else {
                    finalBmp.recycle()
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            statusMessage = outcome.galleryWarning ?: "Saved ${savedEntity.title}"
                        )
                    }
                }
                onSaved(savedEntity.id)
            }.onFailure { err ->
                finalBmp.recycle()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        statusMessage = err.message ?: "Failed to save edited screenshot."
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        originalBitmap?.recycle()
        originalBitmap = null
    }
}
