package com.example.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioManager
import android.media.Image
import android.media.ImageReader
import android.media.ToneGenerator
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.ScrollShotApp
import com.example.accessibility.ScrollAccessibilityService
import com.example.data.local.CaptureSessionEntity
import com.example.data.model.CaptureMode
import com.example.data.model.StitchDirection
import com.example.data.model.UserPreferences
import com.example.data.storage.SaveFailureReason
import com.example.data.storage.StorageSaveException
import com.example.stitching.ImageStitcher
import com.example.stitching.StitchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var captureJob: Job? = null
    private var timerJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var bgHandler: Handler? = null

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 1920
    private var screenDpi: Int = 320

    private var currentSessionDir: File? = null
    private var currentDbSessionId: Long? = null
    private var userRequestedFinish = false
    private var userCancelled = false

    private val stitcher = ImageStitcher()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initDisplayMetrics()
        handlerThread = HandlerThread("ScrollShotImageReader").apply { start() }
        bgHandler = Handler(handlerThread!!.looper)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val isSingle = intent.getBooleanExtra(EXTRA_IS_SINGLE, false)
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: CaptureMode.AUTO_DETECT.name
                val dirName = intent.getStringExtra(EXTRA_DIRECTION) ?: StitchDirection.VERTICAL.name
                val mode = runCatching { CaptureMode.valueOf(modeName) }.getOrDefault(CaptureMode.AUTO_DETECT)
                val direction = runCatching { StitchDirection.valueOf(dirName) }.getOrDefault(StitchDirection.VERTICAL)

                startForegroundWithType(buildNotification(getString(R.string.capture_status_title), false, mode, 0, 0))
                beginSession(isSingle, mode, direction)
            }
            ACTION_CAPTURE_FRAME -> captureManualFrameNow()
            ACTION_PAUSE -> togglePause(true)
            ACTION_RESUME -> togglePause(false)
            ACTION_UNDO_FRAME -> undoLastFrame()
            ACTION_FINISH -> finishAndStitch()
            ACTION_CANCEL -> cancelSession()
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initDisplayMetrics() {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            screenWidth = bounds.width().coerceAtLeast(360)
            screenHeight = bounds.height().coerceAtLeast(640)
            screenDpi = resources.configuration.densityDpi
        } else {
            val dm = resources.displayMetrics
            screenWidth = dm.widthPixels.coerceAtLeast(360)
            screenHeight = dm.heightPixels.coerceAtLeast(640)
            screenDpi = dm.densityDpi
        }
    }

    private fun beginSession(isSingle: Boolean, mode: CaptureMode, direction: StitchDirection) {
        val resultCode = CaptureSessionCoordinator.pendingProjectionResultCode
        val data = CaptureSessionCoordinator.pendingProjectionData
        CaptureSessionCoordinator.pendingProjectionResultCode = null
        CaptureSessionCoordinator.pendingProjectionData = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            publishError(CaptureErrorType.PERMISSION_DENIED, getString(R.string.error_permission_denied))
            stopSelf()
            return
        }

        val app = application as ScrollShotApp
        val availableBytes = app.repository.storage.getAvailableStorageBytes()
        if (availableBytes in 1 until 25L * 1024L * 1024L) {
            publishError(CaptureErrorType.INSUFFICIENT_STORAGE, getString(R.string.error_insufficient_storage))
            stopSelf()
            return
        }

        userRequestedFinish = false
        userCancelled = false

        val sessionDir = File(cacheDir, "sessions/session_${System.currentTimeMillis()}").apply { mkdirs() }
        currentSessionDir = sessionDir

        serviceScope.launch {
            val prefs = app.settingsDataStore.preferencesFlow.first()
            val dbId = if (!isSingle) {
                app.repository.upsertActiveSession(
                    CaptureSessionEntity(
                        mode = mode.name,
                        direction = direction.name,
                        sessionDirPath = sessionDir.absolutePath,
                        framePathsSerialized = "",
                        status = "ACTIVE"
                    )
                )
            } else null
            currentDbSessionId = dbId

            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            val projection = runCatching {
                projectionManager.getMediaProjection(resultCode, data)
            }.getOrNull()

            if (projection == null) {
                publishError(CaptureErrorType.PERMISSION_DENIED, getString(R.string.error_permission_denied))
                stopSelf()
                return@launch
            }

            mediaProjection = projection
            projection.registerCallback(projectionCallback, bgHandler)
            setupVirtualDisplay(screenWidth, screenHeight)

            _sessionState.value = CaptureSessionUiState(
                phase = if (prefs.countdownSeconds > 0) CapturePhase.COUNTDOWN else CapturePhase.CAPTURING,
                isSingleScreenshot = isSingle,
                mode = mode,
                direction = direction,
                countdownRemaining = prefs.countdownSeconds,
                sessionDirPath = sessionDir.absolutePath,
                sessionDbId = dbId
            )

            // Countdown so user can navigate to target app
            if (prefs.countdownSeconds > 0) {
                for (sec in prefs.countdownSeconds downTo 1) {
                    if (userCancelled || userRequestedFinish) return@launch
                    _sessionState.update { it.copy(phase = CapturePhase.COUNTDOWN, countdownRemaining = sec) }
                    updateNotification(
                        getString(R.string.capture_status_countdown, sec),
                        isPaused = false,
                        mode = mode,
                        frameCount = 0,
                        elapsedSeconds = 0
                    )
                    delay(1000L)
                }
            }

            if (userCancelled) return@launch

            _sessionState.update { it.copy(phase = CapturePhase.CAPTURING, countdownRemaining = 0) }
            startElapsedTimer()

            if (isSingle) {
                runSingleCapture(prefs)
            } else {
                when (mode) {
                    CaptureMode.AUTO_DETECT -> runAutoDetectLoop(prefs, mode)
                    CaptureMode.MANUAL -> runManualCaptureStart(prefs, mode)
                    CaptureMode.AUTO_SCROLL -> runAutoScrollLoop(prefs, mode, direction)
                }
            }
        }
    }

    private fun setupVirtualDisplay(width: Int, height: Int) {
        virtualDisplay?.release()
        imageReader?.close()

        val w = width.coerceAtLeast(160)
        val h = height.coerceAtLeast(160)
        screenWidth = w
        screenHeight = h

        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScrollShotVirtualDisplay",
            w,
            h,
            screenDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            bgHandler
        )
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            serviceScope.launch {
                if (!userRequestedFinish && !userCancelled) {
                    val frames = _sessionState.value.framePaths
                    if (frames.isNotEmpty() && !_sessionState.value.isSingleScreenshot) {
                        currentDbSessionId?.let { id ->
                            (application as ScrollShotApp).repository.updateSessionProgress(id, "INTERRUPTED", frames)
                        }
                    }
                    publishError(CaptureErrorType.PERMISSION_REVOKED, getString(R.string.error_permission_revoked))
                    cleanupProjection()
                    stopSelf()
                }
            }
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0 && (width != screenWidth || height != screenHeight)) {
                bgHandler?.post {
                    setupVirtualDisplay(width, height)
                }
            }
        }
    }

    private fun startElapsedTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (true) {
                delay(1000L)
                val st = _sessionState.value
                if (st.phase == CapturePhase.CAPTURING) {
                    val nextSec = st.elapsedSeconds + 1
                    _sessionState.update { it.copy(elapsedSeconds = nextSec) }
                    updateNotification(
                        getString(
                            R.string.capture_status_active,
                            modeLabel(st.mode),
                            st.framePaths.size,
                            formatTimer(nextSec)
                        ),
                        isPaused = false,
                        mode = st.mode,
                        frameCount = st.framePaths.size,
                        elapsedSeconds = nextSec
                    )
                }
            }
        }
    }

    private fun runSingleCapture(prefs: UserPreferences) {
        captureJob = serviceScope.launch {
            delay(250L)
            val bmp = acquireStableBitmap(maxAttempts = 6)
            if (bmp == null) {
                publishError(CaptureErrorType.SERVICE_INTERRUPTED, getString(R.string.error_permission_revoked))
                cleanupProjection()
                stopSelf()
                return@launch
            }
            if (isProtectedBlackFrame(bmp)) {
                bmp.recycle()
                publishError(CaptureErrorType.SECURE_CONTENT, getString(R.string.error_secure_content))
                cleanupProjection()
                stopSelf()
                return@launch
            }

            playFeedback(prefs)
            val app = application as ScrollShotApp
            val title = app.repository.storage.buildTitle(prefs.namingTemplate, isLong = false)
            val saveResult = app.repository.saveNewScreenshotResult(
                bitmap = bmp,
                title = title,
                format = prefs.defaultFormat,
                jpegQuality = prefs.jpegQuality,
                isLongScreenshot = false,
                frameCount = 1,
                albumId = prefs.defaultAlbumId
            )
            bmp.recycle()

            userRequestedFinish = true
            cleanupProjection()
            saveResult.onSuccess { outcome ->
                _sessionState.update {
                    it.copy(
                        phase = CapturePhase.COMPLETED_SINGLE,
                        lastCapturedScreenshotId = outcome.entity.id,
                        galleryWarning = outcome.galleryWarning
                    )
                }
            }.onFailure { err ->
                val errorType = when ((err as? StorageSaveException)?.reason) {
                    SaveFailureReason.INSUFFICIENT_STORAGE -> CaptureErrorType.INSUFFICIENT_STORAGE
                    SaveFailureReason.PERMISSION_DENIED -> CaptureErrorType.STORAGE_PERMISSION_DENIED
                    else -> CaptureErrorType.SAVE_FAILED
                }
                publishError(errorType, err.message ?: getString(R.string.error_save_failed))
            }
            stopSelf()
        }
    }

    private fun runManualCaptureStart(prefs: UserPreferences, mode: CaptureMode) {
        captureJob = serviceScope.launch {
            delay(200L)
            // Capture initial anchor frame automatically
            captureAndSaveOneFrame(prefs, allowDuplicate = false)
            updateNotification(
                getString(R.string.capture_status_active, modeLabel(mode), _sessionState.value.framePaths.size, "00:00"),
                isPaused = false,
                mode = mode,
                frameCount = _sessionState.value.framePaths.size,
                elapsedSeconds = 0
            )
        }
    }

    private fun captureManualFrameNow() {
        val st = _sessionState.value
        if (st.phase != CapturePhase.CAPTURING && st.phase != CapturePhase.PAUSED) return
        serviceScope.launch {
            val prefs = (application as ScrollShotApp).settingsDataStore.preferencesFlow.first()
            captureAndSaveOneFrame(prefs, allowDuplicate = false)
        }
    }

    private fun runAutoDetectLoop(prefs: UserPreferences, mode: CaptureMode) {
        captureJob = serviceScope.launch {
            delay(200L)
            var lastSavedBmp: Bitmap? = null
            var prevSampleBmp: Bitmap? = null
            var isScrolling = false
            var stableTicks = 0

            // Grab initial frame
            val first = acquireStableBitmap(maxAttempts = 5)
            if (first != null) {
                if (isProtectedBlackFrame(first)) {
                    first.recycle()
                    publishError(CaptureErrorType.SECURE_CONTENT, getString(R.string.error_secure_content))
                    cleanupProjection()
                    stopSelf()
                    return@launch
                }
                saveFrameToSession(first, prefs)
                lastSavedBmp = first
                prevSampleBmp = downscaleSample(first)
            }

            while (!userRequestedFinish && !userCancelled) {
                delay(220L)
                val st = _sessionState.value
                if (st.phase == CapturePhase.PAUSED) continue
                if (st.framePaths.size >= prefs.maxFrames) {
                    finishAndStitch()
                    break
                }

                val current = withContext(Dispatchers.IO) { acquireLatestBitmapOnce() } ?: continue
                if (isProtectedBlackFrame(current)) {
                    current.recycle()
                    continue
                }
                val currentSample = downscaleSample(current)
                val prev = prevSampleBmp
                if (prev != null) {
                    val deltaFromPrev = sampleDiff(prev, currentSample)
                    if (deltaFromPrev > 4.5f) {
                        isScrolling = true
                        stableTicks = 0
                    } else if (isScrolling) {
                        stableTicks++
                        if (stableTicks >= 2) {
                            // Scroll animation finished! Check duplicate against last saved frame
                            val lastBmp = lastSavedBmp
                            val lastSample = lastBmp?.let { downscaleSample(it) }
                            val diffFromSaved = if (lastSample != null) {
                                val d = sampleDiff(lastSample, currentSample)
                                lastSample.recycle()
                                d
                            } else 255f

                            if (diffFromSaved > 5.0f) {
                                saveFrameToSession(current, prefs)
                                lastSavedBmp?.recycle()
                                lastSavedBmp = current
                                isScrolling = false
                                stableTicks = 0
                                prevSampleBmp?.recycle()
                                prevSampleBmp = currentSample
                                continue
                            } else {
                                isScrolling = false
                                stableTicks = 0
                            }
                        }
                    }
                    prev.recycle()
                }
                prevSampleBmp = currentSample
                current.recycle()
            }

            lastSavedBmp?.recycle()
            prevSampleBmp?.recycle()
        }
    }

    private fun runAutoScrollLoop(prefs: UserPreferences, mode: CaptureMode, direction: StitchDirection) {
        captureJob = serviceScope.launch {
            val accessService = ScrollAccessibilityService.current()
            if (accessService == null) {
                publishError(CaptureErrorType.AUTOSCROLL_UNSUPPORTED, getString(R.string.error_autoscroll_unsupported))
                cleanupProjection()
                stopSelf()
                return@launch
            }

            delay(250L)
            var lastSavedBmp: Bitmap = acquireStableBitmap(maxAttempts = 5) ?: run {
                publishError(CaptureErrorType.SERVICE_INTERRUPTED, getString(R.string.error_permission_revoked))
                cleanupProjection()
                stopSelf()
                return@launch
            }
            if (isProtectedBlackFrame(lastSavedBmp)) {
                lastSavedBmp.recycle()
                publishError(CaptureErrorType.SECURE_CONTENT, getString(R.string.error_secure_content))
                cleanupProjection()
                stopSelf()
                return@launch
            }
            saveFrameToSession(lastSavedBmp, prefs)

            var unchangedCount = 0
            while (!userRequestedFinish && !userCancelled) {
                val st = _sessionState.value
                if (st.phase == CapturePhase.PAUSED) {
                    delay(300L)
                    continue
                }
                if (st.framePaths.size >= prefs.maxFrames) {
                    finishAndStitch()
                    break
                }

                val scrolled = accessService.performAutoScroll(direction)
                if (!scrolled) {
                    if (st.framePaths.size <= 1) {
                        lastSavedBmp.recycle()
                        publishError(CaptureErrorType.AUTOSCROLL_UNSUPPORTED, getString(R.string.error_autoscroll_unsupported))
                        cleanupProjection()
                        stopSelf()
                        return@launch
                    } else {
                        finishAndStitch()
                        break
                    }
                }

                delay(650L) // Wait for scroll animation & fling to settle
                val nextBmp = acquireStableBitmap(maxAttempts = 4) ?: continue
                val sA = downscaleSample(lastSavedBmp)
                val sB = downscaleSample(nextBmp)
                val diff = sampleDiff(sA, sB)
                sA.recycle()
                sB.recycle()

                if (diff < 3.2f) {
                    nextBmp.recycle()
                    unchangedCount++
                    if (unchangedCount >= 2) {
                        // Reached end of page!
                        finishAndStitch()
                        break
                    }
                } else {
                    unchangedCount = 0
                    saveFrameToSession(nextBmp, prefs)
                    lastSavedBmp.recycle()
                    lastSavedBmp = nextBmp
                }
            }
            lastSavedBmp.recycle()
        }
    }

    private suspend fun captureAndSaveOneFrame(prefs: UserPreferences, allowDuplicate: Boolean) {
        val st = _sessionState.value
        if (st.framePaths.size >= prefs.maxFrames) {
            finishAndStitch()
            return
        }
        val bmp = acquireStableBitmap(maxAttempts = 5) ?: return
        if (isProtectedBlackFrame(bmp)) {
            bmp.recycle()
            publishError(CaptureErrorType.SECURE_CONTENT, getString(R.string.error_secure_content))
            return
        }
        if (!allowDuplicate && st.framePaths.isNotEmpty()) {
            val prevBmp = stitcher.decodeBitmap(st.framePaths.last())
            if (prevBmp != null) {
                val sA = downscaleSample(prevBmp)
                val sB = downscaleSample(bmp)
                val diff = sampleDiff(sA, sB)
                sA.recycle()
                sB.recycle()
                prevBmp.recycle()
                if (diff < 2.8f) {
                    bmp.recycle()
                    return
                }
            }
        }
        saveFrameToSession(bmp, prefs)
        bmp.recycle()
    }

    private suspend fun saveFrameToSession(bitmap: Bitmap, prefs: UserPreferences) {
        val dir = currentSessionDir ?: return
        val index = _sessionState.value.framePaths.size
        val file = File(dir, "frame_%03d.png".format(index))
        val wrote = runCatching {
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { out ->
                    val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                    ok && file.exists() && file.length() > 0L
                }
            }
        }.getOrDefault(false)
        if (!wrote) {
            runCatching { file.delete() }
            publishError(CaptureErrorType.INSUFFICIENT_STORAGE, getString(R.string.error_insufficient_storage))
            cleanupProjection()
            stopSelf()
            return
        }
        playFeedback(prefs)
        val updatedPaths = _sessionState.value.framePaths + file.absolutePath
        _sessionState.update { it.copy(framePaths = updatedPaths) }
        currentDbSessionId?.let { id ->
            (application as ScrollShotApp).repository.updateSessionProgress(id, "ACTIVE", updatedPaths)
        }
    }

    private fun undoLastFrame() {
        val paths = _sessionState.value.framePaths
        if (paths.size <= 1) return
        val removed = paths.last()
        runCatching { File(removed).delete() }
        val updated = paths.dropLast(1)
        _sessionState.update { it.copy(framePaths = updated) }
        serviceScope.launch {
            currentDbSessionId?.let { id ->
                (application as ScrollShotApp).repository.updateSessionProgress(id, "ACTIVE", updated)
            }
        }
    }

    private fun togglePause(pause: Boolean) {
        val st = _sessionState.value
        val nextPhase = if (pause) CapturePhase.PAUSED else CapturePhase.CAPTURING
        _sessionState.update { it.copy(phase = nextPhase) }
        updateNotification(
            if (pause) getString(R.string.capture_status_paused, st.framePaths.size, formatTimer(st.elapsedSeconds))
            else getString(R.string.capture_status_active, modeLabel(st.mode), st.framePaths.size, formatTimer(st.elapsedSeconds)),
            isPaused = pause,
            mode = st.mode,
            frameCount = st.framePaths.size,
            elapsedSeconds = st.elapsedSeconds
        )
    }

    private fun finishAndStitch() {
        if (userRequestedFinish) return
        userRequestedFinish = true
        captureJob?.cancel()
        timerJob?.cancel()
        cleanupProjection()

        val st = _sessionState.value
        val paths = st.framePaths
        if (paths.isEmpty()) {
            publishError(CaptureErrorType.STITCH_FAILED, getString(R.string.error_stitch_failed))
            stopSelf()
            return
        }

        _sessionState.update {
            it.copy(phase = CapturePhase.STITCHING, stitchProgress = 0.05f, stitchStatusText = "Analyzing frames…")
        }

        serviceScope.launch {
            val app = application as ScrollShotApp
            val prefs = app.settingsDataStore.preferencesFlow.first()
            val plan = stitcher.buildStitchPlan(
                framePaths = paths,
                direction = st.direction,
                overlapRange = prefs.effectiveOverlapRange()
            ) { progress, msg ->
                _sessionState.update { it.copy(stitchProgress = progress * 0.5f, stitchStatusText = msg) }
            }

            when (val result = stitcher.renderStitchPlan(plan, prefs.maxOutputLengthPx) { progress, msg ->
                _sessionState.update { it.copy(stitchProgress = 0.5f + progress * 0.5f, stitchStatusText = msg) }
            }) {
                is StitchResult.Success -> {
                    val title = app.repository.storage.buildTitle(prefs.namingTemplate, isLong = paths.size > 1)
                    val saveResult = app.repository.saveNewScreenshotResult(
                        bitmap = result.bitmap,
                        title = title,
                        format = prefs.defaultFormat,
                        jpegQuality = prefs.jpegQuality,
                        isLongScreenshot = paths.size > 1,
                        frameCount = result.plan.activeFrameCount,
                        albumId = prefs.defaultAlbumId
                    )
                    result.bitmap.recycle()
                    saveResult.onSuccess { outcome ->
                        currentDbSessionId?.let { app.repository.completeSession(it) }
                        _sessionState.update {
                            it.copy(
                                phase = CapturePhase.COMPLETED_LONG,
                                lastCapturedScreenshotId = outcome.entity.id,
                                lastStitchPlan = result.plan,
                                galleryWarning = outcome.galleryWarning
                            )
                        }
                    }.onFailure { err ->
                        val errorType = when ((err as? StorageSaveException)?.reason) {
                            SaveFailureReason.INSUFFICIENT_STORAGE -> CaptureErrorType.INSUFFICIENT_STORAGE
                            SaveFailureReason.PERMISSION_DENIED -> CaptureErrorType.STORAGE_PERMISSION_DENIED
                            else -> CaptureErrorType.SAVE_FAILED
                        }
                        _sessionState.update {
                            it.copy(
                                phase = CapturePhase.ERROR,
                                errorType = errorType,
                                errorMessage = err.message ?: getString(R.string.error_save_failed),
                                lastStitchPlan = result.plan
                            )
                        }
                    }
                }
                is StitchResult.Failure -> {
                    _sessionState.update {
                        it.copy(
                            phase = CapturePhase.ERROR,
                            errorType = CaptureErrorType.STITCH_FAILED,
                            errorMessage = result.message,
                            lastStitchPlan = result.partialPlan
                        )
                    }
                }
            }
            stopSelf()
        }
    }

    private fun cancelSession() {
        userCancelled = true
        captureJob?.cancel()
        timerJob?.cancel()
        cleanupProjection()
        serviceScope.launch {
            currentDbSessionId?.let { id ->
                (application as ScrollShotApp).repository.updateSessionProgress(id, "COMPLETED", emptyList())
                (application as ScrollShotApp).repository.completeSession(id)
            }
            currentSessionDir?.deleteRecursively()
            _sessionState.value = CaptureSessionUiState()
            stopSelf()
        }
    }

    private suspend fun acquireStableBitmap(maxAttempts: Int): Bitmap? = withContext(Dispatchers.IO) {
        for (attempt in 0 until maxAttempts) {
            val bmp = acquireLatestBitmapOnce()
            if (bmp != null) return@withContext bmp
            delay(90L)
        }
        null
    }

    private fun acquireLatestBitmapOnce(): Bitmap? {
        val reader = imageReader ?: return null
        val image: Image = reader.acquireLatestImage() ?: return null
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val rawWidth = image.width + rowPadding / pixelStride
            val fullBmp = Bitmap.createBitmap(rawWidth, image.height, Bitmap.Config.ARGB_8888)
            fullBmp.copyPixelsFromBuffer(buffer)
            if (rawWidth == image.width) {
                fullBmp
            } else {
                val cropped = Bitmap.createBitmap(fullBmp, 0, 0, image.width, image.height)
                fullBmp.recycle()
                cropped
            }
        } catch (_: Throwable) {
            null
        } finally {
            image.close()
        }
    }

    private fun isProtectedBlackFrame(bitmap: Bitmap): Boolean {
        val stepX = max(1, bitmap.width / 16)
        val stepY = max(1, bitmap.height / 24)
        var nonBlack = 0
        for (y in stepY until bitmap.height - stepY step stepY) {
            for (x in stepX until bitmap.width - stepX step stepX) {
                val p = bitmap.getPixel(x, y)
                if (Color.red(p) > 4 || Color.green(p) > 4 || Color.blue(p) > 4) {
                    nonBlack++
                    if (nonBlack >= 4) return false
                }
            }
        }
        return true
    }

    private fun downscaleSample(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, 72, 128, false)
    }

    private fun sampleDiff(a: Bitmap, b: Bitmap): Float {
        val w = minOf(a.width, b.width)
        val h = minOf(a.height, b.height)
        var sum = 0L
        var count = 0
        for (y in 8 until h - 8 step 2) {
            for (x in 4 until w - 4 step 2) {
                val pA = a.getPixel(x, y)
                val pB = b.getPixel(x, y)
                sum += abs(Color.red(pA) - Color.red(pB)) +
                    abs(Color.green(pA) - Color.green(pB)) +
                    abs(Color.blue(pA) - Color.blue(pB))
                count++
            }
        }
        return if (count == 0) 0f else sum.toFloat() / (count * 3f)
    }

    private fun playFeedback(prefs: UserPreferences) {
        if (prefs.vibrationEnabled) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(VibratorManager::class.java)
                    vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val vib = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vib.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib.vibrate(35L)
                    }
                }
            }
        }
        if (prefs.soundEnabled) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
            }
        }
    }

    private fun publishError(type: CaptureErrorType, message: String) {
        _sessionState.update {
            it.copy(phase = CapturePhase.ERROR, errorType = type, errorMessage = message)
        }
    }

    private fun cleanupProjection() {
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { mediaProjection?.unregisterCallback(projectionCallback) }
        runCatching { mediaProjection?.stop() }
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.capture_notification_channel_desc)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun updateNotification(
        statusText: String,
        isPaused: Boolean,
        mode: CaptureMode,
        frameCount: Int,
        elapsedSeconds: Int
    ) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(statusText, isPaused, mode, frameCount, elapsedSeconds))
    }

    private fun buildNotification(
        statusText: String,
        isPaused: Boolean,
        mode: CaptureMode,
        frameCount: Int,
        elapsedSeconds: Int
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.capture_status_title))
            .setContentText(statusText)
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (mode == CaptureMode.MANUAL) {
            builder.addAction(0, getString(R.string.capture_action_frame), servicePendingIntent(ACTION_CAPTURE_FRAME, 1))
        }

        if (isPaused) {
            builder.addAction(0, getString(R.string.capture_action_resume), servicePendingIntent(ACTION_RESUME, 2))
        } else {
            builder.addAction(0, getString(R.string.capture_action_pause), servicePendingIntent(ACTION_PAUSE, 3))
        }

        builder.addAction(0, getString(R.string.capture_action_finish), servicePendingIntent(ACTION_FINISH, 4))

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, ScreenCaptureService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun modeLabel(mode: CaptureMode): String = when (mode) {
        CaptureMode.AUTO_DETECT -> getString(R.string.mode_auto_detect)
        CaptureMode.MANUAL -> getString(R.string.mode_manual)
        CaptureMode.AUTO_SCROLL -> getString(R.string.mode_auto_scroll)
    }

    private fun formatTimer(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

    override fun onDestroy() {
        super.onDestroy()
        cleanupProjection()
        handlerThread?.quitSafely()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "scrollshot_capture_channel"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_START = "com.example.capture.START"
        const val ACTION_CAPTURE_FRAME = "com.example.capture.CAPTURE_FRAME"
        const val ACTION_PAUSE = "com.example.capture.PAUSE"
        const val ACTION_RESUME = "com.example.capture.RESUME"
        const val ACTION_UNDO_FRAME = "com.example.capture.UNDO_FRAME"
        const val ACTION_FINISH = "com.example.capture.FINISH"
        const val ACTION_CANCEL = "com.example.capture.CANCEL"

        const val EXTRA_IS_SINGLE = "extra_is_single"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_DIRECTION = "extra_direction"

        private val _sessionState = MutableStateFlow(CaptureSessionUiState())
        val sessionState: StateFlow<CaptureSessionUiState> = _sessionState.asStateFlow()

        fun resetSessionState() {
            _sessionState.value = CaptureSessionUiState()
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, ScreenCaptureService::class.java).setAction(action)
            context.startService(intent)
        }
    }
}
