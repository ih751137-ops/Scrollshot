package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlbumEntity
import com.example.data.model.AppLanguage
import com.example.data.model.CaptureMode
import com.example.data.model.ImageFormat
import com.example.data.model.OverlapPreset
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferences
import com.example.data.storage.GalleryStorageAccessMode
import com.example.data.storage.StoragePermissionHelper
import com.example.ui.components.CaptureInstructionsDialog
import com.example.ui.components.formatBytes

@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    albums: List<AlbumEntity>,
    usedStorageBytes: Long,
    availableStorageBytes: Long,
    storageAccessMode: GalleryStorageAccessMode = GalleryStorageAccessMode.SCOPED_MEDIA_STORE,
    onRequestLegacyGalleryPermission: () -> Unit = {},
    onUpdatePreferences: ((UserPreferences) -> UserPreferences) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showInstructions by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showInstructions) {
        CaptureInstructionsDialog(onDismiss = { showInstructions = false })
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Security") },
            text = {
                Text(
                    "• 100% On-Device Processing: Screenshots, image stitching, PDF exports, and OCR text recognition run exclusively on your phone.\n\n" +
                        "• Consent Per Session: Android MediaProjection permission is requested cleanly per capture session.\n\n" +
                        "• Respects Protected Screens: FLAG_SECURE and DRM apps are respected and never bypassed.\n\n" +
                        "• Minimal Permissions: ScrollShot does not request AccessibilityService, broad storage access, or background network permissions."
                )
            },
            confirmButton = { Button(onClick = { showPrivacyDialog = false }) { Text("Close") } }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ScrollShot") },
            text = {
                Text(
                    "ScrollShot – Long Screenshot & Studio\nVersion 1.0\n\n" +
                        "Native Kotlin + Jetpack Compose application built with MediaProjection, ImageReader, on-device stitching, PdfDocument, Room, and ML Kit on-device OCR."
                )
            },
            confirmButton = { Button(onClick = { showAboutDialog = false }) { Text("OK") } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Capture & Stitching Section
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_section_capture)) {
                Text("Default Capture Mode", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.defaultCaptureMode == mode,
                            onClick = { onUpdatePreferences { it.copy(defaultCaptureMode = mode) } },
                            label = {
                                Text(
                                    when (mode) {
                                        CaptureMode.AUTO_DETECT -> stringResource(R.string.mode_auto_detect)
                                        CaptureMode.MANUAL -> stringResource(R.string.mode_manual)
                                    }
                                )
                            }
                        )
                    }
                }

                SettingsSwitchRow(
                    title = "Automatic Frame Detection",
                    subtitle = "Wait for scroll animations to settle before capturing",
                    checked = preferences.autoFrameDetection,
                    onCheckedChange = { checked -> onUpdatePreferences { it.copy(autoFrameDetection = checked) } }
                )

                Text("Stitch Overlap Setting", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OverlapPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = preferences.overlapPreset == preset,
                            onClick = { onUpdatePreferences { it.copy(overlapPreset = preset) } },
                            label = { Text(preset.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                if (preferences.overlapPreset == OverlapPreset.CUSTOM) {
                    Text(
                        "Custom Search Window: ${preferences.customOverlapMinPct}% – ${preferences.customOverlapMaxPct}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = preferences.customOverlapMinPct.toFloat(),
                        onValueChange = { v ->
                            onUpdatePreferences { it.copy(customOverlapMinPct = v.toInt().coerceAtMost(it.customOverlapMaxPct - 10)) }
                        },
                        valueRange = 5f..45f
                    )
                    Slider(
                        value = preferences.customOverlapMaxPct.toFloat(),
                        onValueChange = { v ->
                            onUpdatePreferences { it.copy(customOverlapMaxPct = v.toInt().coerceAtLeast(it.customOverlapMinPct + 10)) }
                        },
                        valueRange = 30f..85f
                    )
                }

                Text("Countdown Before Capture: ${preferences.countdownSeconds}s", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 3, 5).forEach { sec ->
                        FilterChip(
                            selected = preferences.countdownSeconds == sec,
                            onClick = { onUpdatePreferences { it.copy(countdownSeconds = sec) } },
                            label = { Text(if (sec == 0) "Instant" else "${sec}s") }
                        )
                    }
                }

                Text("Max Frames Limit: ${preferences.maxFrames} frames", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = preferences.maxFrames.toFloat(),
                    onValueChange = { v -> onUpdatePreferences { it.copy(maxFrames = v.toInt()) } },
                    valueRange = 5f..60f
                )

                Text("Max Stitched Length: ${preferences.maxOutputLengthPx} px", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = preferences.maxOutputLengthPx.toFloat(),
                    onValueChange = { v -> onUpdatePreferences { it.copy(maxOutputLengthPx = (v.toInt() / 1000) * 1000) } },
                    valueRange = 8000f..36000f
                )
            }
        }

        // Output & Storage Section
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_section_storage)) {
                Text("Default Image Format", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = preferences.defaultFormat == fmt,
                            onClick = { onUpdatePreferences { it.copy(defaultFormat = fmt) } },
                            label = { Text(fmt.name) }
                        )
                    }
                }

                Text("Default JPEG Quality: ${preferences.jpegQuality}%", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = preferences.jpegQuality.toFloat(),
                    onValueChange = { v -> onUpdatePreferences { it.copy(jpegQuality = v.toInt()) } },
                    valueRange = 55f..100f
                )

                OutlinedTextField(
                    value = preferences.namingTemplate,
                    onValueChange = { tpl -> onUpdatePreferences { it.copy(namingTemplate = tpl) } },
                    label = { Text("Naming Template ({date}, {time}, {type})") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (albums.isNotEmpty()) {
                    Text("Default Save Folder / Album", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.defaultAlbumId == null,
                            onClick = { onUpdatePreferences { it.copy(defaultAlbumId = null) } },
                            label = { Text("Default") }
                        )
                        albums.forEach { album ->
                            FilterChip(
                                selected = preferences.defaultAlbumId == album.id,
                                onClick = { onUpdatePreferences { it.copy(defaultAlbumId = album.id) } },
                                label = { Text(album.name) }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Used by ScrollShot: ${formatBytes(usedStorageBytes)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Free device storage: ${formatBytes(availableStorageBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onClearCache) {
                        Icon(Icons.Rounded.CleaningServices, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear Cache")
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = StoragePermissionHelper.accessModeTitle(storageAccessMode),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = StoragePermissionHelper.accessModeDescription(storageAccessMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (storageAccessMode == GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED) {
                        OutlinedButton(onClick = onRequestLegacyGalleryPermission) {
                            Text(stringResource(R.string.storage_enable_gallery_sync))
                        }
                    }
                }
            }
        }

        // Appearance & Language Section
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_section_appearance)) {
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = preferences.themeMode == mode,
                            onClick = { onUpdatePreferences { it.copy(themeMode = mode) } },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Text("Language / भाषा", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = preferences.language == AppLanguage.ENGLISH,
                        onClick = { onUpdatePreferences { it.copy(language = AppLanguage.ENGLISH) } },
                        label = { Text(stringResource(R.string.language_english)) }
                    )
                    FilterChip(
                        selected = preferences.language == AppLanguage.HINDI,
                        onClick = { onUpdatePreferences { it.copy(language = AppLanguage.HINDI) } },
                        label = { Text(stringResource(R.string.language_hindi)) }
                    )
                }

                SettingsSwitchRow(
                    title = "Capture Vibration",
                    subtitle = "Subtle haptic pulse when each frame is captured",
                    checked = preferences.vibrationEnabled,
                    onCheckedChange = { v -> onUpdatePreferences { it.copy(vibrationEnabled = v) } }
                )

                SettingsSwitchRow(
                    title = "Capture Sound",
                    subtitle = "Soft shutter beep on capture",
                    checked = preferences.soundEnabled,
                    onCheckedChange = { v -> onUpdatePreferences { it.copy(soundEnabled = v) } }
                )
            }
        }

        // Privacy, Help & About
        item {
            SettingsSectionCard(title = stringResource(R.string.settings_section_privacy)) {
                TextButton(onClick = { showInstructions = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Capture Instructions", modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { showPrivacyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Privacy Settings & Local Processing", modifier = Modifier.weight(1f))
                }
                TextButton(onClick = { showAboutDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Info, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("About ScrollShot", modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
