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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ScreenshotEntity
import com.example.ui.viewmodel.OcrUiState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScreen(
    screenshots: List<ScreenshotEntity>,
    initialScreenshotId: Long?,
    uiState: OcrUiState,
    onBack: () -> Unit,
    onImportPhoto: (android.net.Uri, (Long) -> Unit) -> Unit,
    onRunOcr: (ScreenshotEntity) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCopyText: (String) -> Unit,
    onExportTxt: (String, String) -> Unit
) {
    var selectedId by remember(initialScreenshotId) {
        mutableStateOf(initialScreenshotId ?: screenshots.firstOrNull()?.id)
    }

    val selectedShot = remember(screenshots, selectedId) {
        screenshots.firstOrNull { it.id == selectedId }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportPhoto(uri) { newId ->
                selectedId = newId
            }
        }
    }

    LaunchedEffect(selectedId) {
        val target = screenshots.firstOrNull { it.id == selectedId }
        if (target != null && uiState.screenshotId != target.id && !uiState.isExtracting) {
            onRunOcr(target)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ocr_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = "Import photo for OCR")
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
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.ocr_privacy_note),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                if (screenshots.isEmpty()) {
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import Screenshot to Extract Text")
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(screenshots, key = { it.id }) { shot ->
                            val selected = shot.id == selectedId
                            Card(
                                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .width(108.dp)
                                    .clickable {
                                        selectedId = shot.id
                                        onRunOcr(shot)
                                    }
                            ) {
                                Column {
                                    AsyncImage(
                                        model = File(shot.filePath),
                                        contentDescription = shot.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(110.dp)
                                    )
                                    Text(
                                        text = shot.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (selectedShot != null) {
                item {
                    Button(
                        onClick = { onRunOcr(selectedShot) },
                        enabled = !uiState.isExtracting,
                        modifier = Modifier.fillMaxWidth().testTag("run_ocr_btn")
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Extract Text On-Device")
                    }
                }
            }

            if (uiState.isExtracting) {
                item {
                    LinearProgressIndicator(
                        progress = { uiState.progress.coerceIn(0.05f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Recognizing text locally…", style = MaterialTheme.typography.bodySmall)
                }
            }

            uiState.error?.let { err ->
                item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }

            val recognized = uiState.result?.fullText ?: selectedShot?.extractedText
            if (!recognized.isNullOrBlank()) {
                item {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        label = { Text("Search inside recognized text") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { onCopyText(recognized) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Text")
                        }
                        OutlinedButton(
                            onClick = {
                                onExportTxt(recognized, selectedShot?.title ?: "ScrollShot")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Description, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export .TXT")
                        }
                    }
                }

                item {
                    val annotated = remember(recognized, uiState.searchQuery) {
                        val query = uiState.searchQuery.trim()
                        buildAnnotatedString {
                            append(recognized)
                            if (query.isNotEmpty()) {
                                val lowerText = recognized.lowercase()
                                val lowerQ = query.lowercase()
                                var idx = lowerText.indexOf(lowerQ)
                                while (idx >= 0) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = Color(0xFFFDE047),
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        start = idx,
                                        end = idx + query.length
                                    )
                                    idx = lowerText.indexOf(lowerQ, idx + query.length)
                                }
                            }
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            uiState.result?.let { r ->
                                Text(
                                    text = "${r.wordCount} words • ${r.lineCount} lines (Long-press to select text)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            SelectionContainer {
                                Text(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
