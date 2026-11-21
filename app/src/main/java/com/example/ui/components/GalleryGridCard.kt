package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.data.local.ScreenshotEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryGridCard(
    screenshot: ScreenshotEntity,
    albumName: String?,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isCorrupted: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onCopyClipboard: () -> Unit,
    onMoveToAlbum: () -> Unit,
    onRemoveFromAlbum: () -> Unit,
    onViewDetails: () -> Unit,
    onExportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val fileObj = remember(screenshot.filePath) {
        if (screenshot.filePath.isNotBlank()) File(screenshot.filePath) else null
    }
    val hasLocalFile = remember(fileObj) { fileObj != null && fileObj.exists() && fileObj.length() > 0L }
    val imageModel: Any? = remember(screenshot.filePath, screenshot.mediaStoreUri, hasLocalFile) {
        when {
            hasLocalFile -> fileObj
            !screenshot.mediaStoreUri.isNullOrBlank() -> runCatching { Uri.parse(screenshot.mediaStoreUri) }.getOrNull()
            else -> null
        }
    }
    val fileMissingOrCorrupted = isCorrupted || imageModel == null
    val displayFilename = remember(screenshot.title, screenshot.format, screenshot.filePath) {
        val ext = fileObj?.extension?.takeIf { it.isNotBlank() } ?: screenshot.format.lowercase(Locale.US)
        "${screenshot.title}.$ext"
    }

    val thumbnailRequest = remember(screenshot.filePath, screenshot.mediaStoreUri, screenshot.updatedAt) {
        ImageRequest.Builder(context)
            .data(imageModel)
            .size(480, 480)
            .crossfade(true)
            .build()
    }

    Card(
        shape = MaterialTheme.shapes.large,
        border = if (isSelected) {
            BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
        },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gallery_card_${screenshot.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (fileMissingOrCorrupted) {
                    CorruptedThumbnailFallback(modifier = Modifier.fillMaxSize())
                } else {
                    SubcomposeAsyncImage(
                        model = thumbnailRequest,
                        contentDescription = displayFilename,
                        contentScale = ContentScale.Crop,
                        error = {
                            CorruptedThumbnailFallback(modifier = Modifier.fillMaxSize())
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Subtle top scrim for icon legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x88000000), Color.Transparent)
                            )
                        )
                )

                if (screenshot.isLongScreenshot) {
                    Surface(
                        color = Color(0xCC0B132B),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "LONG • ${screenshot.frameCount}f",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF22D3EE),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                if (albumName != null && !isSelectionMode) {
                    Surface(
                        color = Color(0xCC1E293B),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSelected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(20.dp)
                        )
                    }
                } else if (!isSelectionMode) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .testTag("card_favorite_btn_${screenshot.id}")
                    ) {
                        Icon(
                            imageVector = if (screenshot.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (screenshot.isFavorite) Color(0xFFEF4444) else Color.White
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayFilename,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (!isSelectionMode) {
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("card_more_btn_${screenshot.id}")
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "More options for ${screenshot.title}",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                    onClick = { showMenu = false; onRename() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                    onClick = { showMenu = false; onShare() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export PNG / JPEG") },
                                    leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                    onClick = { showMenu = false; onExportImage() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate File") },
                                    leadingIcon = { Icon(Icons.Rounded.FileCopy, contentDescription = null) },
                                    onClick = { showMenu = false; onDuplicate() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Copy to Clipboard") },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                                    onClick = { showMenu = false; onCopyClipboard() }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (screenshot.isFavorite) "Remove Favorite" else "Add to Favorites") },
                                    leadingIcon = {
                                        Icon(
                                            if (screenshot.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = { showMenu = false; onToggleFavorite() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to Album") },
                                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                                    onClick = { showMenu = false; onMoveToAlbum() }
                                )
                                if (screenshot.albumId != null) {
                                    DropdownMenuItem(
                                        text = { Text("Remove from Album") },
                                        leadingIcon = { Icon(Icons.Rounded.FolderOff, contentDescription = null) },
                                        onClick = { showMenu = false; onRemoveFromAlbum() }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("View Image Details") },
                                    leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                                    onClick = { showMenu = false; onViewDetails() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { showMenu = false; onDelete() }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${screenshot.width}×${screenshot.height} • ${formatBytes(screenshot.fileSizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "${dateFmt.format(Date(screenshot.createdAt))} • ${screenshot.format}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun CorruptedThumbnailFallback(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.BrokenImage,
                contentDescription = "Unreadable image",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Unreadable",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
