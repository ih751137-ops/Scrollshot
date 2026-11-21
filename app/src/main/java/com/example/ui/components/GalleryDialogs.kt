package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.AlbumEntity
import com.example.data.local.ScreenshotEntity
import com.example.data.model.ImageFormat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RenameScreenshotDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialTitle) { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Screenshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Updates both the gallery name and the stored filename.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Filename") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_screenshot_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text.trim())
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("rename_confirm_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteScreenshotsConfirmDialog(
    count: Int,
    singleTitle: String? = null,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val titleText = if (count <= 1) "Delete Screenshot?" else "Delete $count Screenshots?"
    val bodyText = if (count <= 1 && !singleTitle.isNullOrBlank()) {
        "This permanently deletes \"$singleTitle\" from ScrollShot storage."
    } else {
        "These $count selected screenshots will be permanently deleted from ScrollShot storage."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = { Text(bodyText) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_delete_btn")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MoveToAlbumDialog(
    albums: List<AlbumEntity>,
    currentAlbumId: Long? = null,
    onDismiss: () -> Unit,
    onSelectAlbum: (Long?) -> Unit,
    onCreateAlbum: (String) -> Unit
) {
    var newAlbumName by remember { mutableStateOf("") }
    var showInlineCreate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to Album") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedButton(
                    onClick = {
                        onSelectAlbum(null)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.FolderOff, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (currentAlbumId != null) "Remove from Album (Keep in Gallery)" else "No Album (Default)",
                        modifier = Modifier.weight(1f)
                    )
                }

                albums.forEach { album ->
                    val isCurrent = album.id == currentAlbumId
                    OutlinedButton(
                        onClick = {
                            onSelectAlbum(album.id)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isCurrent) "${album.name} (Current)" else album.name,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (showInlineCreate) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = newAlbumName,
                            onValueChange = { newAlbumName = it },
                            label = { Text("New Album") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newAlbumName.isNotBlank()) {
                                    onCreateAlbum(newAlbumName.trim())
                                    newAlbumName = ""
                                    showInlineCreate = false
                                }
                            },
                            enabled = newAlbumName.isNotBlank()
                        ) { Text("Add") }
                    }
                } else {
                    TextButton(
                        onClick = { showInlineCreate = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Create New Album")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ManageAlbumsDialog(
    albums: List<AlbumEntity>,
    onDismiss: () -> Unit,
    onCreateAlbum: (String) -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onDeleteAlbum: (Long) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    var renamingAlbum by remember { mutableStateOf<AlbumEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var deletingAlbum by remember { mutableStateOf<AlbumEntity?>(null) }

    renamingAlbum?.let { album ->
        AlertDialog(
            onDismissRequest = { renamingAlbum = null },
            title = { Text("Rename Album") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRenameAlbum(album.id, renameInput.trim())
                            renamingAlbum = null
                        }
                    },
                    enabled = renameInput.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renamingAlbum = null }) { Text("Cancel") }
            }
        )
    }

    deletingAlbum?.let { album ->
        AlertDialog(
            onDismissRequest = { deletingAlbum = null },
            title = { Text("Delete Album \"${album.name}\"?") },
            text = {
                Text("Only the album folder will be deleted. All screenshots inside it will remain safely in your Gallery.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAlbum(album.id)
                        deletingAlbum = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Delete Album") }
            },
            dismissButton = {
                TextButton(onClick = { deletingAlbum = null }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Albums") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New album name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manage_new_album_input")
                    )
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreateAlbum(newName.trim())
                                newName = ""
                            }
                        },
                        enabled = newName.isNotBlank(),
                        modifier = Modifier.testTag("manage_create_album_btn")
                    ) { Text("Create") }
                }

                if (albums.isEmpty()) {
                    Text(
                        text = "No custom albums yet. Create one above to organize screenshots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    HorizontalDivider()
                    albums.forEach { album ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    renamingAlbum = album
                                    renameInput = album.name
                                }
                            ) {
                                Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Rename ${album.name}")
                            }
                            IconButton(onClick = { deletingAlbum = album }) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete ${album.name}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun ExportImageDialog(
    screenshot: ScreenshotEntity,
    defaultQuality: Int = 92,
    onDismiss: () -> Unit,
    onExport: (format: ImageFormat, quality: Int, addToGallery: Boolean) -> Unit,
    onExportToUri: ((destinationUri: Uri, format: ImageFormat, quality: Int) -> Unit)? = null
) {
    val initialFmt = runCatching { ImageFormat.valueOf(screenshot.format) }.getOrDefault(ImageFormat.PNG)
    var selectedFormat by remember { mutableStateOf(initialFmt) }
    var quality by remember { mutableIntStateOf(defaultQuality.coerceIn(55, 100)) }
    var addToGallery by remember { mutableStateOf(true) }

    val saveAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(selectedFormat.mimeType)
    ) { uri ->
        if (uri != null) {
            onExportToUri?.invoke(uri, selectedFormat, quality)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Export \"${screenshot.title}\" (${screenshot.width}×${screenshot.height}) to Pictures/ScrollShot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Output Format", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.name) }
                        )
                    }
                }
                if (selectedFormat == ImageFormat.JPEG) {
                    Text("JPEG Quality: $quality%", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 55f..100f
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Also add exported copy to Gallery",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Switch(checked = addToGallery, onCheckedChange = { addToGallery = it })
                }
                if (onExportToUri != null) {
                    OutlinedButton(
                        onClick = {
                            val suggested = "${screenshot.title}_export.${selectedFormat.extension}"
                            saveAsLauncher.launch(suggested)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save to Custom Device Folder…")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExport(selectedFormat, quality, addToGallery)
                    onDismiss()
                },
                modifier = Modifier.testTag("export_image_confirm_btn")
            ) {
                Text("Export ${selectedFormat.name}")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImageDetailsDialog(
    screenshot: ScreenshotEntity,
    albumName: String?,
    isCorrupted: Boolean = false,
    onDismiss: () -> Unit
) {
    val file = remember(screenshot.filePath) { File(screenshot.filePath) }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }
    val mp = (screenshot.width.toLong() * screenshot.height.toLong()) / 1_000_000.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Image Details") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                DetailRow("Title", screenshot.title)
                DetailRow("Filename", file.name)
                DetailRow("Dimensions", "${screenshot.width} × ${screenshot.height} px (%.2f MP)".format(Locale.US, mp))
                DetailRow("File Size", "${formatBytes(screenshot.fileSizeBytes)} (${screenshot.fileSizeBytes} B)")
                DetailRow("Format", screenshot.format)
                DetailRow(
                    "Capture Type",
                    if (screenshot.isLongScreenshot) "Long Screenshot (${screenshot.frameCount} frames)" else "Single Screenshot"
                )
                DetailRow("Album", albumName ?: "None")
                DetailRow("Favorite", if (screenshot.isFavorite) "Yes" else "No")
                DetailRow("Created", dateFmt.format(Date(screenshot.createdAt)))
                DetailRow("Modified", dateFmt.format(Date(screenshot.updatedAt)))
                DetailRow(
                    "Device Gallery (MediaStore)",
                    if (!screenshot.mediaStoreUri.isNullOrBlank()) {
                        "Published to Pictures/ScrollShot"
                    } else {
                        "App-private storage only"
                    }
                )
                DetailRow(
                    "Storage Path",
                    screenshot.filePath.ifBlank { screenshot.mediaStoreUri ?: "Unavailable" }
                )
                if (isCorrupted) {
                    DetailRow("File Integrity", "Warning: Image header unreadable or corrupted")
                }
                if (!screenshot.extractedText.isNullOrBlank()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("Extracted Text", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = screenshot.extractedText.take(280),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
