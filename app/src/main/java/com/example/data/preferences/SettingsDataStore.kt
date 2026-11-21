package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.CaptureMode
import com.example.data.model.ImageFormat
import com.example.data.model.OverlapPreset
import com.example.data.model.StitchDirection
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scrollshot_prefs")

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val OVERLAP_PRESET = stringPreferencesKey("overlap_preset")
        val CUSTOM_OVERLAP_MIN = intPreferencesKey("custom_overlap_min")
        val CUSTOM_OVERLAP_MAX = intPreferencesKey("custom_overlap_max")
        val AUTO_FRAME_DETECTION = booleanPreferencesKey("auto_frame_detection")
        val DEFAULT_CAPTURE_MODE = stringPreferencesKey("default_capture_mode")
        val DEFAULT_DIRECTION = stringPreferencesKey("default_direction")
        val AUTO_SCROLL_ENABLED = booleanPreferencesKey("auto_scroll_enabled")
        val DEFAULT_ALBUM_ID = longPreferencesKey("default_album_id")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val VIBRATION = booleanPreferencesKey("vibration")
        val SOUND = booleanPreferencesKey("sound")
        val COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        val MAX_FRAMES = intPreferencesKey("max_frames")
        val MAX_OUTPUT_LENGTH = intPreferencesKey("max_output_length")
        val NAMING_TEMPLATE = stringPreferencesKey("naming_template")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            defaultFormat = prefs[Keys.DEFAULT_FORMAT]?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() } ?: ImageFormat.PNG,
            jpegQuality = prefs[Keys.JPEG_QUALITY] ?: 92,
            overlapPreset = prefs[Keys.OVERLAP_PRESET]?.let { runCatching { OverlapPreset.valueOf(it) }.getOrNull() } ?: OverlapPreset.MEDIUM,
            customOverlapMinPct = prefs[Keys.CUSTOM_OVERLAP_MIN] ?: 15,
            customOverlapMaxPct = prefs[Keys.CUSTOM_OVERLAP_MAX] ?: 65,
            autoFrameDetection = prefs[Keys.AUTO_FRAME_DETECTION] ?: true,
            defaultCaptureMode = prefs[Keys.DEFAULT_CAPTURE_MODE]?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() } ?: CaptureMode.AUTO_DETECT,
            defaultDirection = prefs[Keys.DEFAULT_DIRECTION]?.let { runCatching { StitchDirection.valueOf(it) }.getOrNull() } ?: StitchDirection.VERTICAL,
            autoScrollEnabled = prefs[Keys.AUTO_SCROLL_ENABLED] ?: false,
            defaultAlbumId = prefs[Keys.DEFAULT_ALBUM_ID]?.takeIf { it > 0L },
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            language = prefs[Keys.LANGUAGE]?.let { code -> AppLanguage.entries.firstOrNull { it.code == code } } ?: AppLanguage.ENGLISH,
            vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
            soundEnabled = prefs[Keys.SOUND] ?: false,
            countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: 3,
            maxFrames = prefs[Keys.MAX_FRAMES] ?: 25,
            maxOutputLengthPx = prefs[Keys.MAX_OUTPUT_LENGTH] ?: 24000,
            namingTemplate = prefs[Keys.NAMING_TEMPLATE] ?: "ScrollShot_{date}_{time}"
        )
    }

    suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        context.dataStore.edit { prefs ->
            val current = UserPreferences(
                defaultFormat = prefs[Keys.DEFAULT_FORMAT]?.let { runCatching { ImageFormat.valueOf(it) }.getOrNull() } ?: ImageFormat.PNG,
                jpegQuality = prefs[Keys.JPEG_QUALITY] ?: 92,
                overlapPreset = prefs[Keys.OVERLAP_PRESET]?.let { runCatching { OverlapPreset.valueOf(it) }.getOrNull() } ?: OverlapPreset.MEDIUM,
                customOverlapMinPct = prefs[Keys.CUSTOM_OVERLAP_MIN] ?: 15,
                customOverlapMaxPct = prefs[Keys.CUSTOM_OVERLAP_MAX] ?: 65,
                autoFrameDetection = prefs[Keys.AUTO_FRAME_DETECTION] ?: true,
                defaultCaptureMode = prefs[Keys.DEFAULT_CAPTURE_MODE]?.let { runCatching { CaptureMode.valueOf(it) }.getOrNull() } ?: CaptureMode.AUTO_DETECT,
                defaultDirection = prefs[Keys.DEFAULT_DIRECTION]?.let { runCatching { StitchDirection.valueOf(it) }.getOrNull() } ?: StitchDirection.VERTICAL,
                autoScrollEnabled = prefs[Keys.AUTO_SCROLL_ENABLED] ?: false,
                defaultAlbumId = prefs[Keys.DEFAULT_ALBUM_ID]?.takeIf { it > 0L },
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                language = prefs[Keys.LANGUAGE]?.let { code -> AppLanguage.entries.firstOrNull { it.code == code } } ?: AppLanguage.ENGLISH,
                vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
                soundEnabled = prefs[Keys.SOUND] ?: false,
                countdownSeconds = prefs[Keys.COUNTDOWN_SECONDS] ?: 3,
                maxFrames = prefs[Keys.MAX_FRAMES] ?: 25,
                maxOutputLengthPx = prefs[Keys.MAX_OUTPUT_LENGTH] ?: 24000,
                namingTemplate = prefs[Keys.NAMING_TEMPLATE] ?: "ScrollShot_{date}_{time}"
            )
            val updated = transform(current)
            prefs[Keys.DEFAULT_FORMAT] = updated.defaultFormat.name
            prefs[Keys.JPEG_QUALITY] = updated.jpegQuality.coerceIn(50, 100)
            prefs[Keys.OVERLAP_PRESET] = updated.overlapPreset.name
            prefs[Keys.CUSTOM_OVERLAP_MIN] = updated.customOverlapMinPct.coerceIn(5, 50)
            prefs[Keys.CUSTOM_OVERLAP_MAX] = updated.customOverlapMaxPct.coerceIn(30, 85)
            prefs[Keys.AUTO_FRAME_DETECTION] = updated.autoFrameDetection
            prefs[Keys.DEFAULT_CAPTURE_MODE] = updated.defaultCaptureMode.name
            prefs[Keys.DEFAULT_DIRECTION] = updated.defaultDirection.name
            prefs[Keys.AUTO_SCROLL_ENABLED] = updated.autoScrollEnabled
            prefs[Keys.DEFAULT_ALBUM_ID] = updated.defaultAlbumId ?: -1L
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.LANGUAGE] = updated.language.code
            prefs[Keys.VIBRATION] = updated.vibrationEnabled
            prefs[Keys.SOUND] = updated.soundEnabled
            prefs[Keys.COUNTDOWN_SECONDS] = updated.countdownSeconds
            prefs[Keys.MAX_FRAMES] = updated.maxFrames.coerceIn(5, 60)
            prefs[Keys.MAX_OUTPUT_LENGTH] = updated.maxOutputLengthPx.coerceIn(8000, 40000)
            prefs[Keys.NAMING_TEMPLATE] = updated.namingTemplate.ifBlank { "ScrollShot_{date}_{time}" }
        }
    }
}
