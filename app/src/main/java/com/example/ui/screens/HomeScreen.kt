package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Crop
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImage
import com.example.R
import com.example.capture.CaptureSessionUiState
import com.example.data.local.CaptureSessionEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.CaptureMode
import com.example.data.model.StitchDirection
import com.example.data.model.UserPreferences
import com.example.ui.components.CaptureInstructionsDialog
import com.example.ui.components.CaptureStatusBanner
import com.example.ui.components.formatBytes
import java.io.File

@Composable
fun HomeScreen(
    preferences: UserPreferences,
    screenshots: List<ScreenshotEntity>,
    recoverableSession: CaptureSessionEntity?,
    captureState: CaptureSessionUiState,
    availableStorageBytes: Long,
    isAutoScrollServiceEnabled: Boolean,
    onStartLongScreenshot: (CaptureMode, StitchDirection) -> Unit,
    onStartQuickScreenshot: () -> Unit,
    onNavigateGallery: () -> Unit,
    onNavigateEditor: (Long?) -> Unit,
    onNavigatePdf: () -> Unit,
    onNavigateOcr: () -> Unit,
    onOpenScreenshotDetail: (Long) -> Unit,
    onRecoverSession: (CaptureSessionEntity) -> Unit,
    onDiscardSession: (CaptureSessionEntity) -> Unit,
    onCaptureFrameManual: () -> Unit,
    onPauseCapture: () -> Unit,
    onResumeCapture: () -> Unit,
    onUndoCaptureFrame: () -> Unit,
    onFinishCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onOpenSeamEditor: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onSwitchToManualMode: () -> Unit,
    onDismissCaptureStatus: () -> Unit,
    onUpdateMode: (CaptureMode) -> Unit,
    onUpdateDirection: (StitchDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInstructions by remember { mutableStateOf(false) }

    if (showInstructions) {
        CaptureInstructionsDialog(onDismiss = { showInstructions = false })
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("home_lazy_column"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.clickable { showInstructions = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = "Capture guide",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Guide", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ) {
                        IconButton(
                            onClick = onStartQuickScreenshot,
                            modifier = Modifier.testTag("home_quick_capture_icon_btn")
                        ) {
                            Icon(
                                Icons.Rounded.Screenshot,
                                contentDescription = stringResource(R.string.action_quick_screenshot),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Active Capture / Error Banner
        item {
            CaptureStatusBanner(
                state = captureState,
                onCaptureFrame = onCaptureFrameManual,
                onPause = onPauseCapture,
                onResume = onResumeCapture,
                onUndoFrame = onUndoCaptureFrame,
                onFinish = onFinishCapture,
                onCancel = onCancelCapture,
                onOpenDetail = onOpenScreenshotDetail,
                onOpenSeamEditor = onOpenSeamEditor,
                onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                onSwitchToManualMode = onSwitchToManualMode,
                onDismiss = onDismissCaptureStatus
            )
        }

        // Interrupted Session Recovery Card
        if (recoverableSession != null) {
            val frameCount = recoverableSession.framePathsSerialized.lines().count { it.isNotBlank() && File(it).exists() }
            if (frameCount > 0) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().testTag("recover_session_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Restore, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = stringResource(R.string.home_recover_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.home_recover_subtitle, frameCount),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onRecoverSession(recoverableSession) }) {
                                    Text(stringResource(R.string.action_recover_stitch))
                                }
                                TextButton(onClick = { onDiscardSession(recoverableSession) }) {
                                    Text(stringResource(R.string.action_discard))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hero Long Screenshot Launch Card
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_scrollshot),
                        contentDescription = "ScrollShot studio banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xCC070B14),
                                        Color(0xEE0B132B)
                                    )
                                )
                            )
                    )
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.home_hero_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.home_hero_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.home_capture_mode_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CaptureMode.entries.forEach { mode ->
                                val selected = preferences.defaultCaptureMode == mode
                                val label = when (mode) {
                                    CaptureMode.AUTO_DETECT -> stringResource(R.string.mode_auto_detect)
                                    CaptureMode.MANUAL -> stringResource(R.string.mode_manual)
                                    CaptureMode.AUTO_SCROLL -> stringResource(R.string.mode_auto_scroll)
                                }
                                FilterChip(
                                    selected = selected,
                                    onClick = { onUpdateMode(mode) },
                                    label = { Text(label, style = MaterialTheme.typography.labelLarge) }
                                )
                            }
                        }

                        if (preferences.defaultCaptureMode == CaptureMode.AUTO_SCROLL && !isAutoScrollServiceEnabled) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                color = Color(0x33F59E0B),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAccessibilitySettings() }
                            ) {
                                Text(
                                    text = "Enable ScrollShot Auto-Scroll in Android Accessibility Settings (or switch to Manual).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFFDE68A),
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = preferences.defaultDirection == StitchDirection.VERTICAL,
                                onClick = { onUpdateDirection(StitchDirection.VERTICAL) },
                                leadingIcon = { Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.direction_vertical)) }
                            )
                            FilterChip(
                                selected = preferences.defaultDirection == StitchDirection.HORIZONTAL,
                                onClick = { onUpdateDirection(StitchDirection.HORIZONTAL) },
                                leadingIcon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text(stringResource(R.string.direction_horizontal)) }
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onStartLongScreenshot(preferences.defaultCaptureMode, preferences.defaultDirection)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF22D3EE),
                                contentColor = Color(0xFF070B14)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("start_long_screenshot_btn")
                        ) {
                            Icon(Icons.Rounded.AutoAwesomeMotion, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.action_start_long_screenshot),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Storage Indicator
        item {
            val lowStorage = availableStorageBytes in 1 until 250L * 1024L * 1024L
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (lowStorage) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = if (lowStorage) Icons.Rounded.WarningAmber else Icons.Rounded.SdStorage,
                        contentDescription = null,
                        tint = if (lowStorage) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${stringResource(R.string.home_storage_available)}: ${formatBytes(availableStorageBytes)}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (lowStorage) {
                            Text(
                                text = stringResource(R.string.home_low_storage_warning),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Text(
                                text = "${screenshots.size} saved screenshots • 100% on-device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 6 Main Action Cards
        item {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val columns = if (maxWidth >= 560.dp) 3 else 2
                val cards = listOf(
                    ActionCardSpec(
                        title = stringResource(R.string.action_start_long_screenshot),
                        subtitle = "Auto, Manual, Scroll",
                        icon = Icons.Rounded.AutoAwesomeMotion,
                        accent = MaterialTheme.colorScheme.primary,
                        tag = "card_long_screenshot",
                        onClick = { onStartLongScreenshot(preferences.defaultCaptureMode, preferences.defaultDirection) }
                    ),
                    ActionCardSpec(
                        title = stringResource(R.string.action_quick_screenshot),
                        subtitle = "Single crisp frame",
                        icon = Icons.Rounded.Screenshot,
                        accent = MaterialTheme.colorScheme.secondary,
                        tag = "card_quick_screenshot",
                        onClick = onStartQuickScreenshot
                    ),
                    ActionCardSpec(
                        title = stringResource(R.string.action_screenshot_gallery),
                        subtitle = "${screenshots.size} captures",
                        icon = Icons.Rounded.Collections,
                        accent = MaterialTheme.colorScheme.tertiary,
                        tag = "card_gallery",
                        onClick = onNavigateGallery
                    ),
                    ActionCardSpec(
                        title = stringResource(R.string.action_image_editor),
                        subtitle = "Crop, Annotate, Blur",
                        icon = Icons.Rounded.Edit,
                        accent = Color(0xFFF59E0B),
                        tag = "card_editor",
                        onClick = { onNavigateEditor(screenshots.firstOrNull()?.id) }
                    ),
                    ActionCardSpec(
                        title = stringResource(R.string.action_convert_pdf),
                        subtitle = "A4, Letter, Multi-page",
                        icon = Icons.Rounded.PictureAsPdf,
                        accent = Color(0xFFEF4444),
                        tag = "card_pdf",
                        onClick = onNavigatePdf
                    ),
                    ActionCardSpec(
                        title = stringResource(R.string.action_text_extractor),
                        subtitle = "On-device OCR",
                        icon = Icons.Rounded.DocumentScanner,
                        accent = Color(0xFF8B5CF6),
                        tag = "card_ocr",
                        onClick = onNavigateOcr
                    )
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    cards.chunked(columns).forEach { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowItems.forEach { spec ->
                                HomeActionCard(spec = spec, modifier = Modifier.weight(1f))
                            }
                            repeat(columns - rowItems.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Last Capture Preview + Recent Screenshots
        if (screenshots.isNotEmpty()) {
            val latest = screenshots.first()
            item {
                Text(
                    text = stringResource(R.string.home_last_capture),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    onClick = { onOpenScreenshotDetail(latest.id) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().testTag("last_capture_card")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        val latestModel: Any? = remember(latest.filePath, latest.mediaStoreUri) {
                            File(latest.filePath).takeIf { latest.filePath.isNotBlank() && it.exists() }
                                ?: latest.mediaStoreUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        }
                        AsyncImage(
                            model = latestModel,
                            contentDescription = latest.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latest.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${latest.width}×${latest.height} • ${formatBytes(latest.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (latest.isLongScreenshot) {
                                Text(
                                    text = "Long Screenshot (${latest.frameCount} frames)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { onNavigateEditor(latest.id) }) {
                            Icon(Icons.Rounded.Crop, contentDescription = "Edit last capture")
                        }
                    }
                }
            }

            if (screenshots.size > 1) {
                item {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.home_recent_captures),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateGallery) {
                            Text("View All")
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(screenshots.take(10), key = { it.id }) { shot ->
                            Card(
                                onClick = { onOpenScreenshotDetail(shot.id) },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.width(124.dp)
                            ) {
                                Column {
                                    val shotModel: Any? = remember(shot.filePath, shot.mediaStoreUri) {
                                        File(shot.filePath).takeIf { shot.filePath.isNotBlank() && it.exists() }
                                            ?: shot.mediaStoreUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                                    }
                                    AsyncImage(
                                        model = shotModel,
                                        contentDescription = shot.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(136.dp)
                                    )
                                    Text(
                                        text = shot.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class ActionCardSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun HomeActionCard(spec: ActionCardSpec, modifier: Modifier = Modifier) {
    Card(
        onClick = spec.onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
        modifier = modifier.testTag(spec.tag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Surface(
                color = spec.accent.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = spec.title,
                        tint = spec.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = spec.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
