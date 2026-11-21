package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.stitching.SeamJoin
import com.example.ui.viewmodel.SeamEditorUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeamAlignmentScreen(
    uiState: SeamEditorUiState,
    onBack: () -> Unit,
    onUpdateJoin: (index: Int, overlapPx: Int, headerCropPx: Int, footerCropPx: Int, isDuplicate: Boolean) -> Unit,
    onReStitch: () -> Unit
) {
    val plan = uiState.plan
    if (plan == null || plan.joins.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Manual Seam Alignment") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(padding)) {
                Text("No multi-frame stitch plan active.")
            }
        }
        return
    }

    var selectedJoinIdx by remember { mutableIntStateOf(0) }
    val join: SeamJoin = plan.joins[selectedJoinIdx.coerceIn(0, plan.joins.lastIndex)]

    // Load cropped bottom strip of left frame and top strip of right frame for onion-skin seam preview
    val seamPreviewPair by produceState<Pair<Bitmap?, Bitmap?>?>(
        initialValue = null,
        plan.framePaths,
        join.leftFrameIndex,
        join.rightFrameIndex,
        join.overlapPx,
        join.headerCropPx,
        join.footerCropPx
    ) {
        value = withContext(Dispatchers.IO) {
            val bmpA = BitmapFactory.decodeFile(plan.framePaths.getOrNull(join.leftFrameIndex))
            val bmpB = BitmapFactory.decodeFile(plan.framePaths.getOrNull(join.rightFrameIndex))
            if (bmpA == null || bmpB == null) {
                bmpA?.recycle()
                bmpB?.recycle()
                return@withContext null
            }
            val stripH = 260.coerceAtMost(bmpA.height / 2).coerceAtMost(bmpB.height / 2)
            val aBottom = (bmpA.height - join.footerCropPx).coerceIn(stripH, bmpA.height)
            val aStrip = Bitmap.createBitmap(bmpA, 0, aBottom - stripH, bmpA.width, stripH)

            val bTop = (join.headerCropPx + (join.overlapPx - stripH).coerceAtLeast(0))
                .coerceIn(0, (bmpB.height - stripH).coerceAtLeast(0))
            val bStrip = Bitmap.createBitmap(bmpB, 0, bTop, bmpB.width, stripH)

            bmpA.recycle()
            bmpB.recycle()
            aStrip to bStrip
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manual Alignment Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = onReStitch, enabled = !uiState.isBusy) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stitch")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (uiState.isBusy) {
                LinearProgressIndicator(progress = { uiState.progress.coerceIn(0.05f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(uiState.statusText, style = MaterialTheme.typography.bodySmall)
            }
            uiState.error?.let {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(it, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(10.dp))
                }
            }

            // Seam selector chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.joins.forEachIndexed { idx, item ->
                    val pct = (item.confidence * 100).toInt()
                    FilterChip(
                        selected = idx == selectedJoinIdx,
                        onClick = { selectedJoinIdx = idx },
                        label = {
                            Text("Join ${idx + 1} (${if (item.isDuplicate) "Dup" else "$pct%"})")
                        }
                    )
                }
            }

            // Onion-Skin Seam Preview Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Onion-Skin Overlap Preview (Frame ${join.leftFrameIndex + 1} ↔ ${join.rightFrameIndex + 1})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Adjust overlap until text and lines align cleanly without ghosting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .background(Color.Black)
                    ) {
                        seamPreviewPair?.first?.let { bmpA ->
                            Image(
                                bitmap = bmpA.asImageBitmap(),
                                contentDescription = "Frame A seam strip",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        seamPreviewPair?.second?.let { bmpB ->
                            Image(
                                bitmap = bmpB.asImageBitmap(),
                                contentDescription = "Frame B seam strip",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(0.5f)
                            )
                        }
                    }
                }
            }

            // Overlap Nudge Controls
            val maxOverlap = (plan.frameHeight * 0.9f).toInt().coerceAtLeast(200)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Overlap Offset: ${join.overlapPx} px", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = join.overlapPx.toFloat(),
                        onValueChange = {
                            onUpdateJoin(selectedJoinIdx, it.toInt(), join.headerCropPx, join.footerCropPx, join.isDuplicate)
                        },
                        valueRange = 0f..maxOverlap.toFloat()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(-20, -4, -1, 1, 4, 20).forEach { delta ->
                            OutlinedButton(
                                onClick = {
                                    onUpdateJoin(
                                        selectedJoinIdx,
                                        (join.overlapPx + delta).coerceIn(0, maxOverlap),
                                        join.headerCropPx,
                                        join.footerCropPx,
                                        join.isDuplicate
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (delta > 0) "+$delta" else "$delta", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text("Fixed Header Crop: ${join.headerCropPx} px", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = join.headerCropPx.toFloat(),
                        onValueChange = {
                            onUpdateJoin(selectedJoinIdx, join.overlapPx, it.toInt(), join.footerCropPx, join.isDuplicate)
                        },
                        valueRange = 0f..(plan.frameHeight * 0.35f)
                    )

                    Text("Fixed Footer Crop: ${join.footerCropPx} px", style = MaterialTheme.typography.titleSmall)
                    Slider(
                        value = join.footerCropPx.toFloat(),
                        onValueChange = {
                            onUpdateJoin(selectedJoinIdx, join.overlapPx, join.headerCropPx, it.toInt(), join.isDuplicate)
                        },
                        valueRange = 0f..(plan.frameHeight * 0.3f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = join.isDuplicate,
                            onCheckedChange = { dup ->
                                onUpdateJoin(selectedJoinIdx, join.overlapPx, join.headerCropPx, join.footerCropPx, dup)
                            }
                        )
                        Text("Skip this frame as duplicate")
                    }
                }
            }
        }
    }
}
