package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ScreenshotEntity
import com.example.data.model.PdfPageSize
import com.example.ui.components.formatBytes
import com.example.ui.viewmodel.PdfUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScreen(
    screenshots: List<ScreenshotEntity>,
    initialSelectedIds: List<Long>,
    uiState: PdfUiState,
    defaultQuality: Int,
    onBack: () -> Unit,
    onImportPhoto: (android.net.Uri, (Long) -> Unit) -> Unit,
    onExportPdf: (List<ScreenshotEntity>, PdfPageSize, Int, Boolean, String?) -> Unit,
    onSharePdf: (String) -> Unit
) {
    var selectedIds by remember(initialSelectedIds) {
        mutableStateOf(
            if (initialSelectedIds.isNotEmpty()) initialSelectedIds.toSet()
            else screenshots.firstOrNull()?.let { setOf(it.id) } ?: emptySet()
        )
    }
    var pageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var fitLongSinglePage by remember { mutableStateOf(false) }
    var jpegQuality by remember(defaultQuality) { mutableFloatStateOf(defaultQuality.toFloat()) }
    var fileName by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportPhoto(uri) { newId ->
                selectedIds = selectedIds + newId
            }
        }
    }

    LaunchedEffect(screenshots) {
        if (selectedIds.isEmpty() && screenshots.isNotEmpty()) {
            selectedIds = setOf(screenshots.first().id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pdf_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Import photo")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                Text(
                    text = "Select Screenshots (${selectedIds.size} selected)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                if (screenshots.isEmpty()) {
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Screenshot from Device")
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(screenshots, key = { it.id }) { shot ->
                            val isSelected = shot.id in selectedIds
                            Card(
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                },
                                modifier = Modifier
                                    .width(116.dp)
                                    .clickable {
                                        selectedIds = if (isSelected) selectedIds - shot.id else selectedIds + shot.id
                                    }
                            ) {
                                Column {
                                    AsyncImage(
                                        model = File(shot.filePath),
                                        contentDescription = shot.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedIds = if (checked) selectedIds + shot.id else selectedIds - shot.id
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = shot.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("PDF Layout & Quality", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PdfPageSize.entries.forEach { size ->
                                FilterChip(
                                    selected = pageSize == size,
                                    onClick = { pageSize = size },
                                    label = { Text(size.label) }
                                )
                            }
                        }

                        if (pageSize != PdfPageSize.ORIGINAL_FIT) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Fit long screenshots on single tall page", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "Off = split cleanly across crisp readable A4/Letter pages",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = fitLongSinglePage, onCheckedChange = { fitLongSinglePage = it })
                            }
                        }

                        Text("Image Compression Quality: ${jpegQuality.toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = jpegQuality,
                            onValueChange = { jpegQuality = it },
                            valueRange = 60f..100f
                        )

                        OutlinedTextField(
                            value = fileName,
                            onValueChange = { fileName = it },
                            label = { Text("PDF File Name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val chosen = screenshots.filter { it.id in selectedIds }
                        onExportPdf(chosen, pageSize, jpegQuality.toInt(), fitLongSinglePage, fileName.trim().ifBlank { null })
                    },
                    enabled = selectedIds.isNotEmpty() && !uiState.isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("export_pdf_btn")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Generate PDF")
                }
            }

            if (uiState.isExporting) {
                item {
                    LinearProgressIndicator(
                        progress = { uiState.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            uiState.error?.let { err ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            uiState.result?.let { res ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("PDF Saved to Documents/ScrollShot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "${res.file.name} • ${res.pageCount} page(s) • ${formatBytes(res.sizeBytes)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(onClick = { onSharePdf(res.file.absolutePath) }) {
                                Icon(Icons.Rounded.Share, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Share PDF")
                            }
                        }
                    }
                }
            }
        }
    }
}
