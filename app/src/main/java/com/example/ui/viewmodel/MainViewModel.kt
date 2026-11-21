package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ScrollShotApp
import com.example.accessibility.ScrollAccessibilityService
import com.example.capture.CaptureSessionUiState
import com.example.capture.ScreenCaptureService
import com.example.data.local.AlbumEntity
import com.example.data.local.CaptureSessionEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.CaptureMode
import com.example.data.model.PdfPageSize
import com.example.data.model.StitchDirection
import com.example.data.model.UserPreferences
import com.example.data.storage.GalleryStorageAccessMode
import com.example.data.storage.StoragePermissionHelper
import com.example.export.PdfExportResult
import com.example.export.PdfExporter
import com.example.ocr.OcrResult
import com.example.ocr.OnDeviceOcrEngine
import com.example.stitching.ImageStitcher
import com.example.stitching.StitchPlan
import com.example.stitching.StitchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfUiState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val result: PdfExportResult? = null,
    val error: String? = null
)

data class OcrUiState(
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val screenshotId: Long? = null,
    val result: OcrResult? = null,
    val searchQuery: String = "",
    val error: String? = null
)

data class SeamEditorUiState(
    val isBusy: Boolean = false,
    val progress: Float = 0f,
    val statusText: String = "",
    val plan: StitchPlan? = null,
    val savedScreenshotId: Long? = null,
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ScrollShotApp
    private val repository = app.repository
    private val settingsStore = app.settingsDataStore
    private val stitcher = ImageStitcher()
    private val pdfExporter = PdfExporter(repository.storage)
    private val ocrEngine = OnDeviceOcrEngine()

    val preferences: StateFlow<UserPreferences> = settingsStore.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())

    val allScreenshots: StateFlow<List<ScreenshotEntity>> = repository.allScreenshots
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAlbums: StateFlow<List<AlbumEntity>> = repository.allAlbums
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recoverableSession: StateFlow<CaptureSessionEntity?> = repository.recoverableSession
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val captureState: StateFlow<CaptureSessionUiState> = ScreenCaptureService.sessionState
    val isAutoScrollEnabled: StateFlow<Boolean> = ScrollAccessibilityService.isEnabled

    private val _availableStorageBytes = MutableStateFlow(repository.storage.getAvailableStorageBytes())
    val availableStorageBytes: StateFlow<Long> = _availableStorageBytes.asStateFlow()

    private val _usedStorageBytes = MutableStateFlow(repository.storage.getUsedAppStorageBytes())
    val usedStorageBytes: StateFlow<Long> = _usedStorageBytes.asStateFlow()

    private val _storageAccessMode = MutableStateFlow(StoragePermissionHelper.getAccessMode(app))
    val storageAccessMode: StateFlow<GalleryStorageAccessMode> = _storageAccessMode.asStateFlow()

    private val _pdfUiState = MutableStateFlow(PdfUiState())
    val pdfUiState: StateFlow<PdfUiState> = _pdfUiState.asStateFlow()

    private val _ocrUiState = MutableStateFlow(OcrUiState())
    val ocrUiState: StateFlow<OcrUiState> = _ocrUiState.asStateFlow()

    private val _seamEditorState = MutableStateFlow(SeamEditorUiState())
    val seamEditorState: StateFlow<SeamEditorUiState> = _seamEditorState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun refreshStorageStats() {
        _availableStorageBytes.value = repository.storage.getAvailableStorageBytes()
        _usedStorageBytes.value = repository.storage.getUsedAppStorageBytes()
        _storageAccessMode.value = StoragePermissionHelper.getAccessMode(app)
    }

    fun updatePreferences(transform: (UserPreferences) -> UserPreferences) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch {
            val freed = repository.storage.clearTemporaryCache()
            refreshStorageStats()
            val mb = freed / (1024f * 1024f)
            _toastMessage.value = "Cleared %.1f MB temporary cache".format(mb)
        }
    }

    // --- Shared Media Actions ---

    fun importPhoto(uri: Uri, onImported: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.importFromUriResult(uri)
            result.onSuccess { outcome ->
                refreshStorageStats()
                outcome.galleryWarning?.let { _toastMessage.value = it }
                onImported(outcome.entity.id)
            }.onFailure { err ->
                _toastMessage.value = err.message ?: "Failed to import selected image."
            }
        }
    }

    fun copyImageToClipboard(filePath: String) {
        runCatching {
            repository.storage.copyImageToClipboard(filePath)
            _toastMessage.value = "Screenshot copied to clipboard"
        }.onFailure {
            _toastMessage.value = "Could not copy image to clipboard"
        }
    }

    fun createShareImagesIntent(paths: List<String>): Intent {
        return repository.storage.createShareImagesIntent(paths)
    }

    // --- Capture Recovery & Manual Seam Alignment ---

    fun recoverAndStitchSession(session: CaptureSessionEntity) {
        val frames = session.framePathsSerialized.lines().filter { it.isNotBlank() && File(it).exists() }
        if (frames.isEmpty()) {
            discardSession(session)
            return
        }
        val direction = runCatching { StitchDirection.valueOf(session.direction) }.getOrDefault(StitchDirection.VERTICAL)
        viewModelScope.launch {
            _seamEditorState.value = SeamEditorUiState(isBusy = true, statusText = "Recovering ${frames.size} frames…")
            val prefs = preferences.value
            val plan = stitcher.buildStitchPlan(frames, direction, prefs.effectiveOverlapRange()) { p, msg ->
                _seamEditorState.update { it.copy(progress = p * 0.5f, statusText = msg) }
            }
            when (val res = stitcher.renderStitchPlan(plan, prefs.maxOutputLengthPx) { p, msg ->
                _seamEditorState.update { it.copy(progress = 0.5f + p * 0.5f, statusText = msg) }
            }) {
                is StitchResult.Success -> {
                    val title = repository.storage.buildTitle(prefs.namingTemplate, isLong = frames.size > 1)
                    val saveResult = repository.saveNewScreenshotResult(
                        bitmap = res.bitmap,
                        title = title,
                        format = prefs.defaultFormat,
                        jpegQuality = prefs.jpegQuality,
                        isLongScreenshot = frames.size > 1,
                        frameCount = res.plan.activeFrameCount,
                        albumId = prefs.defaultAlbumId
                    )
                    res.bitmap.recycle()
                    saveResult.onSuccess { outcome ->
                        repository.completeSession(session.id)
                        refreshStorageStats()
                        outcome.galleryWarning?.let { _toastMessage.value = it }
                        _seamEditorState.value = SeamEditorUiState(
                            isBusy = false,
                            plan = res.plan,
                            savedScreenshotId = outcome.entity.id
                        )
                    }.onFailure { err ->
                        _seamEditorState.value = SeamEditorUiState(
                            isBusy = false,
                            plan = res.plan,
                            error = err.message ?: "Failed to save recovered screenshot."
                        )
                    }
                }
                is StitchResult.Failure -> {
                    _seamEditorState.value = SeamEditorUiState(
                        isBusy = false,
                        plan = res.partialPlan,
                        error = res.message
                    )
                }
            }
        }
    }

    fun discardSession(session: CaptureSessionEntity) {
        viewModelScope.launch {
            repository.discardSession(session)
        }
    }

    fun loadStitchPlanForAlignment(plan: StitchPlan) {
        _seamEditorState.value = SeamEditorUiState(plan = plan)
    }

    fun updateSeamJoin(index: Int, overlapPx: Int, headerCropPx: Int, footerCropPx: Int, isDuplicate: Boolean) {
        val current = _seamEditorState.value.plan ?: return
        if (index !in current.joins.indices) return
        val updatedJoins = current.joins.toMutableList()
        val old = updatedJoins[index]
        updatedJoins[index] = old.copy(
            overlapPx = overlapPx.coerceAtLeast(0),
            headerCropPx = headerCropPx.coerceAtLeast(0),
            footerCropPx = footerCropPx.coerceAtLeast(0),
            isDuplicate = isDuplicate,
            confidence = 1f
        )
        _seamEditorState.update { it.copy(plan = current.copy(joins = updatedJoins), savedScreenshotId = null, error = null) }
    }

    fun reStitchWithCurrentPlan(onComplete: (Long) -> Unit) {
        val plan = _seamEditorState.value.plan ?: return
        viewModelScope.launch {
            _seamEditorState.update { it.copy(isBusy = true, progress = 0.1f, statusText = "Re-stitching seams…", error = null) }
            val prefs = preferences.value
            when (val res = stitcher.renderStitchPlan(plan, prefs.maxOutputLengthPx) { p, msg ->
                _seamEditorState.update { it.copy(progress = p, statusText = msg) }
            }) {
                is StitchResult.Success -> {
                    val title = repository.storage.buildTitle(prefs.namingTemplate, isLong = true) + "_aligned"
                    val saveResult = repository.saveNewScreenshotResult(
                        bitmap = res.bitmap,
                        title = title,
                        format = prefs.defaultFormat,
                        jpegQuality = prefs.jpegQuality,
                        isLongScreenshot = true,
                        frameCount = res.plan.activeFrameCount,
                        albumId = prefs.defaultAlbumId
                    )
                    res.bitmap.recycle()
                    saveResult.onSuccess { outcome ->
                        refreshStorageStats()
                        outcome.galleryWarning?.let { _toastMessage.value = it }
                        _seamEditorState.update { it.copy(isBusy = false, savedScreenshotId = outcome.entity.id) }
                        onComplete(outcome.entity.id)
                    }.onFailure { err ->
                        _seamEditorState.update {
                            it.copy(isBusy = false, error = err.message ?: "Failed to save aligned screenshot.")
                        }
                    }
                }
                is StitchResult.Failure -> {
                    _seamEditorState.update { it.copy(isBusy = false, error = res.message) }
                }
            }
        }
    }

    // --- PDF Export ---

    fun resetPdfState() {
        _pdfUiState.value = PdfUiState()
    }

    fun exportToPdf(
        screenshots: List<ScreenshotEntity>,
        pageSize: PdfPageSize,
        jpegQuality: Int,
        fitLongToSinglePage: Boolean,
        outputTitle: String?
    ) {
        viewModelScope.launch {
            _pdfUiState.value = PdfUiState(isExporting = true, progress = 0.05f)
            val result = pdfExporter.exportScreenshotsToPdf(
                screenshots = screenshots,
                pageSize = pageSize,
                jpegQuality = jpegQuality,
                fitLongToSinglePage = fitLongToSinglePage,
                outputTitle = outputTitle
            ) { p ->
                _pdfUiState.update { it.copy(progress = p) }
            }
            result.onSuccess { res ->
                refreshStorageStats()
                _pdfUiState.value = PdfUiState(isExporting = false, progress = 1f, result = res)
            }.onFailure { err ->
                _pdfUiState.value = PdfUiState(isExporting = false, error = err.message ?: "PDF export failed")
            }
        }
    }

    fun shareFileIntent(filePath: String, mimeType: String): Intent {
        return repository.storage.createShareFileIntent(filePath, mimeType)
    }

    // --- On-Device OCR ---

    fun extractText(screenshot: ScreenshotEntity) {
        viewModelScope.launch {
            _ocrUiState.value = OcrUiState(isExtracting = true, progress = 0.1f, screenshotId = screenshot.id)
            val result = ocrEngine.extractTextFromSource(
                bitmapProvider = {
                    repository.storage.decodeBitmapSafely(screenshot.filePath, screenshot.mediaStoreUri)
                }
            ) { p ->
                _ocrUiState.update { it.copy(progress = p) }
            }
            result.onSuccess { ocr ->
                repository.updateExtractedText(screenshot.id, ocr.fullText)
                _ocrUiState.update { it.copy(isExtracting = false, progress = 1f, result = ocr) }
            }.onFailure { err ->
                _ocrUiState.update { it.copy(isExtracting = false, error = err.message ?: "OCR failed") }
            }
        }
    }

    fun setOcrSearchQuery(q: String) {
        _ocrUiState.update { it.copy(searchQuery = q) }
    }

    fun copyExtractedText(text: String) {
        repository.storage.copyTextToClipboard("ScrollShot OCR", text)
        _toastMessage.value = "Extracted text copied to clipboard"
    }

    fun exportExtractedTextToTxt(text: String, baseTitle: String): Intent? {
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "${baseTitle}_ocr_$stamp.txt"
            val file = repository.storage.createExportFile(fileName)
            FileOutputStream(file).use { it.write(text.toByteArray()) }
            repository.storage.publishDocumentToMediaStore(file, fileName, "text/plain")
            repository.storage.createShareFileIntent(file.absolutePath, "text/plain")
        }.getOrNull()
    }
}
