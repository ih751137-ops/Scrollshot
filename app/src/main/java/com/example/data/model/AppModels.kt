package com.example.data.model

enum class CaptureMode {
    AUTO_DETECT,
    MANUAL,
    AUTO_SCROLL
}

enum class StitchDirection {
    VERTICAL,
    HORIZONTAL
}

enum class OverlapPreset(val minPct: Int, val maxPct: Int) {
    LOW(8, 35),
    MEDIUM(18, 55),
    HIGH(32, 75),
    CUSTOM(12, 65)
}

enum class ImageFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg")
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage(val code: String) {
    ENGLISH("en"),
    HINDI("hi")
}

enum class GallerySort {
    NEWEST,
    OLDEST,
    LARGEST,
    DIMENSIONS
}

enum class DateFilter {
    ALL_DATES,
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    CUSTOM_DATE
}

enum class PdfPageSize(val widthPt: Int, val heightPt: Int, val label: String) {
    A4(595, 842, "A4"),
    LETTER(612, 792, "US Letter"),
    ORIGINAL_FIT(0, 0, "Fit Original")
}

data class UserPreferences(
    val defaultFormat: ImageFormat = ImageFormat.PNG,
    val jpegQuality: Int = 92,
    val overlapPreset: OverlapPreset = OverlapPreset.MEDIUM,
    val customOverlapMinPct: Int = 15,
    val customOverlapMaxPct: Int = 65,
    val autoFrameDetection: Boolean = true,
    val defaultCaptureMode: CaptureMode = CaptureMode.AUTO_DETECT,
    val defaultDirection: StitchDirection = StitchDirection.VERTICAL,
    val autoScrollEnabled: Boolean = false,
    val defaultAlbumId: Long? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val countdownSeconds: Int = 3,
    val maxFrames: Int = 25,
    val maxOutputLengthPx: Int = 24000,
    val namingTemplate: String = "ScrollShot_{date}_{time}"
) {
    fun effectiveOverlapRange(): Pair<Float, Float> = when (overlapPreset) {
        OverlapPreset.CUSTOM -> (customOverlapMinPct / 100f) to (customOverlapMaxPct / 100f)
        else -> (overlapPreset.minPct / 100f) to (overlapPreset.maxPct / 100f)
    }
}
