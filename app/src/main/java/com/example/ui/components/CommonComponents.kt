package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.capture.CaptureErrorType
import com.example.capture.CapturePhase
import com.example.capture.CaptureSessionUiState
import com.example.data.model.AppLanguage
import com.example.data.model.CaptureMode
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

fun formatDurationSeconds(sec: Int): String = "%02d:%02d".format(sec / 60, sec % 60)

private class LocalizedContextWrapper(
    base: Context,
    private val localizedResources: Resources
) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

@Composable
fun LocalizedContextProvider(
    language: AppLanguage,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val localizedTuple = remember(baseContext, language) {
        val locale = Locale.forLanguageTag(language.code)
        Locale.setDefault(locale)
        val config = Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        val configCtx = baseContext.createConfigurationContext(config)
        Triple(
            LocalizedContextWrapper(baseContext, configCtx.resources),
            configCtx.resources.configuration,
            activityResultRegistryOwner
        )
    }
    val (wrappedContext, localizedConfig, registryOwner) = localizedTuple
    if (registryOwner != null) {
        CompositionLocalProvider(
            LocalContext provides wrappedContext,
            LocalConfiguration provides localizedConfig,
            LocalActivityResultRegistryOwner provides registryOwner
        ) {
            content()
        }
    } else {
        CompositionLocalProvider(
            LocalContext provides wrappedContext,
            LocalConfiguration provides localizedConfig
        ) {
            content()
        }
    }
}

@Composable
fun CaptureStatusBanner(
    state: CaptureSessionUiState,
    onCaptureFrame: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onUndoFrame: () -> Unit,
    onFinish: () -> Unit,
    onCancel: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onOpenSeamEditor: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible = state.phase != CapturePhase.IDLE, modifier = modifier) {
        val isError = state.phase == CapturePhase.ERROR
        val isDone = state.phase == CapturePhase.COMPLETED_SINGLE || state.phase == CapturePhase.COMPLETED_LONG
        val containerColor = when {
            isError -> MaterialTheme.colorScheme.errorContainer
            isDone -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = when {
            isError -> MaterialTheme.colorScheme.onErrorContainer
            isDone -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
            border = BorderStroke(1.dp, contentColor.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("capture_status_banner")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (state.phase) {
                    CapturePhase.COUNTDOWN -> {
                        Text(
                            text = stringResource(R.string.capture_status_countdown, state.countdownRemaining),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Open the target app (WhatsApp, Chrome, Telegram, Settings) before capture starts.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = onCancel) {
                            Text(stringResource(R.string.capture_action_cancel))
                        }
                    }

                    CapturePhase.CAPTURING, CapturePhase.PAUSED -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.phase == CapturePhase.PAUSED) "Capture Paused" else stringResource(R.string.capture_status_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${state.framePaths.size} frames • ${formatDurationSeconds(state.elapsedSeconds)} • ${state.mode.name.replace('_', ' ')}",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                            if (state.framePaths.size > 1) {
                                TextButton(onClick = onUndoFrame) {
                                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.capture_action_undo), modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.capture_action_undo))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (state.mode == CaptureMode.MANUAL) {
                                FilledTonalButton(
                                    onClick = onCaptureFrame,
                                    modifier = Modifier.weight(1f).testTag("manual_capture_frame_btn")
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.capture_action_frame))
                                }
                            }
                            if (state.phase == CapturePhase.PAUSED) {
                                FilledTonalButton(onClick = onResume, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.capture_action_resume))
                                }
                            } else {
                                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.capture_action_pause))
                                }
                            }
                            Button(
                                onClick = onFinish,
                                modifier = Modifier.weight(1f).testTag("finish_stitch_btn")
                            ) {
                                Text(stringResource(R.string.capture_action_finish))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onCancel) {
                            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.capture_action_cancel))
                        }
                    }

                    CapturePhase.STITCHING -> {
                        Text(
                            text = state.stitchStatusText.ifBlank { "Stitching long screenshot…" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.stitchProgress.coerceIn(0.05f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    CapturePhase.COMPLETED_SINGLE, CapturePhase.COMPLETED_LONG -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = "Capture saved")
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.phase == CapturePhase.COMPLETED_LONG) "Long Screenshot Ready" else "Screenshot Captured",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                val sub = when {
                                    !state.galleryWarning.isNullOrBlank() -> state.galleryWarning
                                    state.lastStitchPlan?.hasLowConfidenceSeam == true ->
                                        "Low-confidence seam detected. You can refine alignment."
                                    else -> "${state.framePaths.size} frame(s) processed locally."
                                }
                                Text(text = sub, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.lastCapturedScreenshotId?.let { id ->
                                Button(onClick = { onOpenDetail(id) }) {
                                    Text("Preview & Edit")
                                }
                            }
                            if (state.lastStitchPlan != null && state.framePaths.size > 1) {
                                OutlinedButton(onClick = onOpenSeamEditor) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Adjust Seams")
                                }
                            }
                            TextButton(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }
                    }

                    CapturePhase.ERROR -> {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = "Error")
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val errorTitle = when (state.errorType) {
                                    CaptureErrorType.SAVE_FAILED -> "Save Error"
                                    CaptureErrorType.INSUFFICIENT_STORAGE -> "Low Storage"
                                    CaptureErrorType.STORAGE_PERMISSION_DENIED -> "Storage Permission Needed"
                                    else -> "Capture Issue"
                                }
                                Text(
                                    text = errorTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = state.errorMessage ?: "An unexpected capture error occurred.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.lastStitchPlan != null && state.framePaths.size > 1) {
                                Button(onClick = onOpenSeamEditor) {
                                    Text("Manual Align")
                                }
                            }
                            TextButton(onClick = onDismiss) {
                                Text("Dismiss")
                            }
                        }
                    }

                    CapturePhase.IDLE -> Unit
                }
            }
        }
    }
}

@Composable
fun CaptureInstructionsDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How Long Screenshot Works") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InstructionStep(
                    "1. Choose Mode & Start",
                    "Select Auto Detect or Manual, then tap Start Long Screenshot and grant Android screen capture consent."
                )
                InstructionStep(
                    "2. Scroll Your Target App",
                    "Switch to WhatsApp, Chrome, Telegram, or Settings. In Auto Detect, scroll at a steady pace and pause briefly at each section. In Manual Mode, use the persistent notification's Capture Frame button."
                )
                InstructionStep(
                    "3. Finish & Refine",
                    "Tap Finish & Stitch in the notification (or inside ScrollShot). Fixed headers/toolbars are removed automatically, and you can fine-tune any join in the Manual Alignment Editor."
                )
                InstructionStep(
                    "Privacy & Protected Apps",
                    "All processing runs 100% locally on-device. Android blocks screen capture on DRM or FLAG_SECURE screens (banking/incognito)."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Got It") }
        }
    )
}

@Composable
private fun InstructionStep(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
