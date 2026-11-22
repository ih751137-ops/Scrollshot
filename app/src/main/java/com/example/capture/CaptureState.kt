package com.example.capture

import android.content.Intent
import com.example.data.model.CaptureMode
import com.example.data.model.StitchDirection
import com.example.stitching.StitchPlan

enum class CaptureErrorType {
    NONE,
    PERMISSION_DENIED,
    PERMISSION_REVOKED,
    SECURE_CONTENT,
    INSUFFICIENT_STORAGE,
    LOW_MEMORY,
    STITCH_FAILED,
    SAVE_FAILED,
    STORAGE_PERMISSION_DENIED,
    SERVICE_INTERRUPTED
}

enum class CapturePhase {
    IDLE,
    COUNTDOWN,
    CAPTURING,
    PAUSED,
    STITCHING,
    COMPLETED_SINGLE,
    COMPLETED_LONG,
    ERROR
}

data class CaptureSessionUiState(
    val phase: CapturePhase = CapturePhase.IDLE,
    val isSingleScreenshot: Boolean = false,
    val mode: CaptureMode = CaptureMode.AUTO_DETECT,
    val direction: StitchDirection = StitchDirection.VERTICAL,
    val countdownRemaining: Int = 0,
    val framePaths: List<String> = emptyList(),
    val elapsedSeconds: Int = 0,
    val stitchProgress: Float = 0f,
    val stitchStatusText: String = "",
    val lastCapturedScreenshotId: Long? = null,
    val lastStitchPlan: StitchPlan? = null,
    val errorType: CaptureErrorType = CaptureErrorType.NONE,
    val errorMessage: String? = null,
    val galleryWarning: String? = null,
    val sessionDirPath: String? = null,
    val sessionDbId: Long? = null
)

object CaptureSessionCoordinator {
    var pendingProjectionResultCode: Int? = null
    var pendingProjectionData: Intent? = null
}
