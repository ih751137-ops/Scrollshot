package com.example.data.storage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SaveFailureReason {
    INSUFFICIENT_STORAGE,
    PERMISSION_DENIED,
    ENCODE_FAILED,
    IO_ERROR,
    SOURCE_UNREADABLE
}

class StorageSaveException(
    message: String,
    val reason: SaveFailureReason,
    cause: Throwable? = null
) : IOException(message, cause)

data class MediaStorePublishOutcome(
    val uri: Uri?,
    val published: Boolean,
    val warning: String? = null
)

data class SavedImageResult(
    val title: String,
    val filePath: String,
    val mediaStoreUri: String?,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val galleryPublished: Boolean = mediaStoreUri != null,
    val galleryWarning: String? = null
)

data class ImageInspectionResult(
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: ImageFormat
)

data class RenamedFileResult(
    val title: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val mediaStoreUri: String? = null
)

data class ExportImageResult(
    val filePath: String,
    val mediaStoreUri: String?,
    val displayName: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: ImageFormat,
    val galleryPublished: Boolean = mediaStoreUri != null,
    val galleryWarning: String? = null
)

data class ScannedImageFile(
    val title: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: ImageFormat,
    val lastModified: Long
)

data class ScannedMediaStoreImage(
    val mediaStoreUri: String,
    val displayName: String,
    val title: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: ImageFormat,
    val dateTakenMillis: Long
)

class MediaStorageManager(private val context: Context) {

    private val capturesDir: File
        get() = File(context.filesDir, "captures").apply { if (!exists()) mkdirs() }

    private val exportsDir: File
        get() = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }

    val capturesDirectory: File
        get() = capturesDir

    fun getAvailableStorageBytes(): Long {
        return runCatching {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(0L)
    }

    fun getUsedAppStorageBytes(): Long {
        return dirSize(capturesDir) + dirSize(exportsDir) + dirSize(File(context.cacheDir, "sessions"))
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun clearTemporaryCache(): Long {
        var freed = 0L
        val sessionsDir = File(context.cacheDir, "sessions")
        if (sessionsDir.exists()) {
            freed += dirSize(sessionsDir)
            sessionsDir.deleteRecursively()
        }
        return freed
    }

    fun buildTitle(template: String, isLong: Boolean): String {
        val now = Date()
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(now)
        val timeStr = SimpleDateFormat("HHmmss", Locale.US).format(now)
        val kind = if (isLong) "Long" else "Quick"
        return template
            .replace("{date}", dateStr)
            .replace("{time}", timeStr)
            .replace("{type}", kind)
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
    }

    fun sanitizeFileNameTitle(raw: String): String {
        return raw.trim()
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "ScrollShot_${System.currentTimeMillis()}" }
    }

    suspend fun saveBitmapSafely(
        bitmap: Bitmap,
        title: String,
        format: ImageFormat,
        jpegQuality: Int,
        overwritePath: String? = null,
        existingMediaStoreUri: String? = null
    ): Result<SavedImageResult> = runCatching {
        saveBitmap(
            bitmap = bitmap,
            title = title,
            format = format,
            jpegQuality = jpegQuality,
            overwritePath = overwritePath,
            existingMediaStoreUri = existingMediaStoreUri
        )
    }

    suspend fun saveBitmap(
        bitmap: Bitmap,
        title: String,
        format: ImageFormat,
        jpegQuality: Int,
        overwritePath: String? = null,
        existingMediaStoreUri: String? = null
    ): SavedImageResult = withContext(Dispatchers.IO) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            throw StorageSaveException("Invalid or recycled bitmap.", SaveFailureReason.ENCODE_FAILED)
        }

        val availableBytes = getAvailableStorageBytes()
        if (availableBytes in 1L until MIN_REQUIRED_SAVE_BYTES) {
            throw StorageSaveException(
                "Insufficient storage space to save screenshot.",
                SaveFailureReason.INSUFFICIENT_STORAGE
            )
        }

        val safeTitle = sanitizeFileNameTitle(title)
        val fileName = "$safeTitle.${format.extension}"
        val isOverwrite = !overwritePath.isNullOrBlank()
        val targetFile = if (isOverwrite) {
            File(overwritePath!!)
        } else {
            File(capturesDir, "${System.currentTimeMillis()}_$fileName")
        }

        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        val parentDir = targetFile.parentFile ?: capturesDir
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            throw StorageSaveException(
                "Unable to create screenshot directory.",
                SaveFailureReason.IO_ERROR
            )
        }

        val tempFile = File(parentDir, ".tmp_${System.currentTimeMillis()}_${targetFile.name}")
        try {
            FileOutputStream(tempFile).use { out ->
                val encoded = bitmap.compress(compressFormat, jpegQuality.coerceIn(50, 100), out)
                if (!encoded) {
                    throw StorageSaveException(
                        "Failed to compress image as ${format.name}.",
                        SaveFailureReason.ENCODE_FAILED
                    )
                }
                out.flush()
            }

            if (!tempFile.exists() || tempFile.length() <= 0L) {
                throw StorageSaveException(
                    "Written image file was empty.",
                    SaveFailureReason.IO_ERROR
                )
            }

            if (targetFile.exists()) {
                if (!targetFile.delete() && !tempFile.copyTo(targetFile, overwrite = true).exists()) {
                    throw StorageSaveException(
                        "Unable to replace existing screenshot file.",
                        SaveFailureReason.IO_ERROR
                    )
                }
                if (tempFile.exists()) {
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                }
            } else {
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
            }
        } catch (e: StorageSaveException) {
            tempFile.delete()
            if (!isOverwrite) targetFile.delete()
            throw e
        } catch (e: SecurityException) {
            tempFile.delete()
            if (!isOverwrite) targetFile.delete()
            throw StorageSaveException("Storage permission denied while saving.", SaveFailureReason.PERMISSION_DENIED, e)
        } catch (e: OutOfMemoryError) {
            tempFile.delete()
            if (!isOverwrite) targetFile.delete()
            throw StorageSaveException("Out of memory while encoding image.", SaveFailureReason.ENCODE_FAILED, e)
        } catch (e: IOException) {
            tempFile.delete()
            if (!isOverwrite) targetFile.delete()
            val reason = if (e.message?.contains("ENOSPC", ignoreCase = true) == true) {
                SaveFailureReason.INSUFFICIENT_STORAGE
            } else {
                SaveFailureReason.IO_ERROR
            }
            throw StorageSaveException(e.message ?: "I/O error while saving screenshot.", reason, e)
        }

        val publishOutcome = if (isOverwrite && !existingMediaStoreUri.isNullOrBlank()) {
            updateExistingMediaStoreEntry(
                mediaStoreUri = existingMediaStoreUri,
                sourceFile = targetFile,
                displayName = fileName,
                mimeType = format.mimeType,
                width = bitmap.width,
                height = bitmap.height
            )
        } else {
            publishImageToMediaStore(
                file = targetFile,
                displayName = fileName,
                mimeType = format.mimeType,
                width = bitmap.width,
                height = bitmap.height
            )
        }

        SavedImageResult(
            title = title,
            filePath = targetFile.absolutePath,
            mediaStoreUri = publishOutcome.uri?.toString() ?: existingMediaStoreUri,
            width = bitmap.width,
            height = bitmap.height,
            fileSizeBytes = targetFile.length(),
            galleryPublished = publishOutcome.published,
            galleryWarning = publishOutcome.warning
        )
    }

    suspend fun inspectImageFile(filePath: String): ImageInspectionResult? = withContext(Dispatchers.IO) {
        if (filePath.isBlank()) return@withContext null
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() <= 0L) return@withContext null
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return@withContext null
        val format = when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg" -> ImageFormat.JPEG
            else -> ImageFormat.PNG
        }
        ImageInspectionResult(
            width = opts.outWidth,
            height = opts.outHeight,
            fileSizeBytes = file.length(),
            format = format
        )
    }

    suspend fun inspectImageSource(
        filePath: String,
        mediaStoreUri: String?
    ): ImageInspectionResult? = withContext(Dispatchers.IO) {
        inspectImageFile(filePath)?.let { return@withContext it }
        if (mediaStoreUri.isNullOrBlank()) return@withContext null
        val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull() ?: return@withContext null
        runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return@runCatching null
            val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length.takeIf { it > 0L } ?: 0L
            } ?: 0L
            val mime = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.US)
            val format = if (mime.contains("jpeg") || mime.contains("jpg")) ImageFormat.JPEG else ImageFormat.PNG
            ImageInspectionResult(
                width = opts.outWidth,
                height = opts.outHeight,
                fileSizeBytes = size,
                format = format
            )
        }.getOrNull()
    }

    fun isMediaUriReadable(mediaStoreUri: String?): Boolean {
        if (mediaStoreUri.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(mediaStoreUri)
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true }
                ?: context.contentResolver.openInputStream(uri)?.use { true }
                ?: false
        }.getOrDefault(false)
    }

    fun resolveReadableModel(filePath: String, mediaStoreUri: String?): Any? {
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) return file
        }
        if (!mediaStoreUri.isNullOrBlank()) {
            return runCatching { Uri.parse(mediaStoreUri) }.getOrNull()
        }
        return null
    }

    fun isScreenshotAccessible(filePath: String, mediaStoreUri: String?): Boolean {
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) return true
        }
        return isMediaUriReadable(mediaStoreUri)
    }

    private fun openInputStream(filePath: String, mediaStoreUri: String?): InputStream? {
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.isFile && file.length() > 0L) {
                return runCatching { FileInputStream(file) }.getOrNull()
            }
        }
        if (!mediaStoreUri.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull() ?: return null
            return runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
        return null
    }

    suspend fun scanCapturesDirectory(): List<ScannedImageFile> = withContext(Dispatchers.IO) {
        val files = capturesDir.listFiles()?.filter {
            it.isFile &&
                !it.name.startsWith(".tmp_") &&
                it.extension.lowercase(Locale.US) in setOf("png", "jpg", "jpeg", "webp")
        }.orEmpty()

        files.mapNotNull { file ->
            val inspected = inspectImageFile(file.absolutePath) ?: return@mapNotNull null
            val rawBase = file.nameWithoutExtension
            val displayTitle = rawBase.replaceFirst(Regex("^\\d{13}_"), "").ifBlank { rawBase }
            ScannedImageFile(
                title = displayTitle,
                filePath = file.canonicalPath,
                width = inspected.width,
                height = inspected.height,
                fileSizeBytes = inspected.fileSizeBytes,
                format = inspected.format,
                lastModified = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }.sortedByDescending { it.lastModified }
    }

    suspend fun scanAppOwnedMediaStoreImages(): List<ScannedMediaStoreImage> = withContext(Dispatchers.IO) {
        val sdkInt = Build.VERSION.SDK_INT
        if (sdkInt < Build.VERSION_CODES.Q && !StoragePermissionHelper.hasLegacyGalleryWritePermission(context, sdkInt)) {
            return@withContext emptyList()
        }

        val results = mutableListOf<ScannedMediaStoreImage>()
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            if (sdkInt >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.DATE_TAKEN)
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        val selection: String
        val selectionArgs: Array<String>
        if (sdkInt >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            selectionArgs = arrayOf("%${Environment.DIRECTORY_PICTURES}/ScrollShot%")
        } else {
            @Suppress("DEPRECATION")
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/${Environment.DIRECTORY_PICTURES}/ScrollShot/%")
        }

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthCol = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val addedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED)
                val modifiedCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
                val takenCol = if (sdkInt >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                } else -1

                while (cursor.moveToNext()) {
                    if (idCol < 0) continue
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val displayName = if (nameCol >= 0) cursor.getString(nameCol).orEmpty() else "ScrollShot_$id.png"
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty().lowercase(Locale.US) else "image/png"
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateTaken = when {
                        takenCol >= 0 && cursor.getLong(takenCol) > 0L -> cursor.getLong(takenCol)
                        modifiedCol >= 0 && cursor.getLong(modifiedCol) > 0L -> cursor.getLong(modifiedCol) * 1000L
                        addedCol >= 0 && cursor.getLong(addedCol) > 0L -> cursor.getLong(addedCol) * 1000L
                        else -> System.currentTimeMillis()
                    }

                    val inspected = if (width <= 0 || height <= 0 || size <= 0L) {
                        inspectImageSource("", uri.toString())
                    } else null

                    val resolvedWidth = if (width > 0) width else (inspected?.width ?: 0)
                    val resolvedHeight = if (height > 0) height else (inspected?.height ?: 0)
                    val resolvedSize = if (size > 0L) size else (inspected?.fileSizeBytes ?: 0L)
                    if (resolvedWidth <= 0 || resolvedHeight <= 0) continue

                    val format = when {
                        mime.contains("jpeg") || mime.contains("jpg") ||
                            displayName.endsWith(".jpg", true) || displayName.endsWith(".jpeg", true) -> ImageFormat.JPEG
                        else -> ImageFormat.PNG
                    }
                    val title = displayName.substringBeforeLast('.')
                        .replaceFirst(Regex("^\\d{13}_"), "")
                        .ifBlank { "ScrollShot_$id" }

                    results.add(
                        ScannedMediaStoreImage(
                            mediaStoreUri = uri.toString(),
                            displayName = displayName,
                            title = title,
                            width = resolvedWidth,
                            height = resolvedHeight,
                            fileSizeBytes = resolvedSize,
                            format = format,
                            dateTakenMillis = dateTaken
                        )
                    )
                }
            }
        }
        results
    }

    suspend fun renameStoredFile(
        oldFilePath: String,
        newTitle: String,
        format: ImageFormat,
        mediaStoreUri: String?
    ): RenamedFileResult? = withContext(Dispatchers.IO) {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isBlank()) return@withContext null
        val safeBase = sanitizeFileNameTitle(cleanTitle)
        val oldFile = if (oldFilePath.isNotBlank()) File(oldFilePath) else null
        val hasLocalFile = oldFile != null && oldFile.exists()

        var finalPath = oldFilePath
        var finalSize = oldFile?.takeIf { it.exists() }?.length() ?: 0L
        var targetName = "$safeBase.${format.extension}"

        if (hasLocalFile && oldFile != null) {
            val ext = oldFile.extension.ifBlank { format.extension }
            targetName = "$safeBase.$ext"
            val parent = oldFile.parentFile ?: capturesDir
            var targetFile = File(parent, targetName)
            if (targetFile.absolutePath != oldFile.absolutePath && targetFile.exists()) {
                targetFile = File(parent, "${System.currentTimeMillis()}_$safeBase.$ext")
            }

            val moved = if (targetFile.absolutePath == oldFile.absolutePath) {
                true
            } else {
                oldFile.renameTo(targetFile) || runCatching {
                    oldFile.inputStream().use { input ->
                        FileOutputStream(targetFile).use { output -> input.copyTo(output) }
                    }
                    oldFile.delete()
                    true
                }.getOrDefault(false)
            }
            if (!moved || !targetFile.exists()) return@withContext null
            finalPath = targetFile.absolutePath
            finalSize = targetFile.length()
        } else if (mediaStoreUri.isNullOrBlank()) {
            return@withContext null
        }

        if (!mediaStoreUri.isNullOrBlank() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, targetName)
                }
                context.contentResolver.update(Uri.parse(mediaStoreUri), values, null, null)
            }
        }

        RenamedFileResult(
            title = cleanTitle,
            filePath = finalPath,
            fileSizeBytes = finalSize,
            mediaStoreUri = mediaStoreUri
        )
    }

    suspend fun copyStoredFile(
        sourcePath: String,
        newTitle: String,
        format: ImageFormat,
        sourceMediaStoreUri: String? = null
    ): SavedImageResult? = withContext(Dispatchers.IO) {
        val inspected = inspectImageSource(sourcePath, sourceMediaStoreUri) ?: return@withContext null
        val cleanTitle = newTitle.trim().ifBlank { "Copy_${System.currentTimeMillis()}" }
        val safeBase = sanitizeFileNameTitle(cleanTitle)
        val sourceExt = File(sourcePath).extension.takeIf { it.isNotBlank() }
        val ext = sourceExt ?: inspected.format.extension
        val targetFile = File(capturesDir, "${System.currentTimeMillis()}_$safeBase.$ext")

        val copied = runCatching {
            openInputStream(sourcePath, sourceMediaStoreUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
                true
            } ?: false
        }.getOrDefault(false)

        if (!copied || !targetFile.exists() || targetFile.length() <= 0L) {
            targetFile.delete()
            return@withContext null
        }

        val resolvedFormat = when (ext.lowercase(Locale.US)) {
            "jpg", "jpeg" -> ImageFormat.JPEG
            else -> format
        }
        val publishOutcome = publishImageToMediaStore(
            file = targetFile,
            displayName = "$safeBase.$ext",
            mimeType = resolvedFormat.mimeType,
            width = inspected.width,
            height = inspected.height
        )
        SavedImageResult(
            title = cleanTitle,
            filePath = targetFile.absolutePath,
            mediaStoreUri = publishOutcome.uri?.toString(),
            width = inspected.width,
            height = inspected.height,
            fileSizeBytes = targetFile.length(),
            galleryPublished = publishOutcome.published,
            galleryWarning = publishOutcome.warning
        )
    }

    suspend fun exportImageAsFormat(
        sourcePath: String,
        outputTitle: String,
        targetFormat: ImageFormat,
        jpegQuality: Int,
        saveToGalleryDir: Boolean = false,
        sourceMediaStoreUri: String? = null
    ): ExportImageResult? = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmapSafely(sourcePath, sourceMediaStoreUri) ?: return@withContext null
        try {
            val safeBase = sanitizeFileNameTitle(outputTitle)
            val fileName = "${safeBase}_export.${targetFormat.extension}"
            val destDir = if (saveToGalleryDir) capturesDir else exportsDir
            val destFile = File(destDir, "${System.currentTimeMillis()}_$fileName")
            val compressFormat = when (targetFormat) {
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            }
            val encoded = runCatching {
                FileOutputStream(destFile).use { out ->
                    val ok = bitmap.compress(compressFormat, jpegQuality.coerceIn(50, 100), out)
                    out.flush()
                    ok
                }
            }.getOrDefault(false)

            if (!encoded || !destFile.exists() || destFile.length() <= 0L) {
                destFile.delete()
                return@withContext null
            }

            val publishOutcome = publishImageToMediaStore(
                file = destFile,
                displayName = fileName,
                mimeType = targetFormat.mimeType,
                width = bitmap.width,
                height = bitmap.height
            )
            ExportImageResult(
                filePath = destFile.absolutePath,
                mediaStoreUri = publishOutcome.uri?.toString(),
                displayName = fileName,
                width = bitmap.width,
                height = bitmap.height,
                fileSizeBytes = destFile.length(),
                format = targetFormat,
                galleryPublished = publishOutcome.published,
                galleryWarning = publishOutcome.warning
            )
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun saveImageToExternalUri(
        sourcePath: String,
        sourceMediaStoreUri: String?,
        destinationUri: Uri,
        format: ImageFormat,
        jpegQuality: Int
    ): Result<Long> = withContext(Dispatchers.IO) {
        val bitmap = decodeBitmapSafely(sourcePath, sourceMediaStoreUri)
            ?: return@withContext Result.failure(
                StorageSaveException("Unable to read source image.", SaveFailureReason.SOURCE_UNREADABLE)
            )
        try {
            val compressFormat = when (format) {
                ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            }
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                val ok = bitmap.compress(compressFormat, jpegQuality.coerceIn(50, 100), out)
                if (!ok) {
                    return@withContext Result.failure(
                        StorageSaveException("Failed to encode exported image.", SaveFailureReason.ENCODE_FAILED)
                    )
                }
                out.flush()
            } ?: return@withContext Result.failure(
                StorageSaveException("Cannot open selected destination URI.", SaveFailureReason.PERMISSION_DENIED)
            )

            val writtenSize = context.contentResolver.openAssetFileDescriptor(destinationUri, "r")?.use {
                it.length.takeIf { len -> len > 0L } ?: 0L
            } ?: 0L
            Result.success(writtenSize)
        } catch (t: Throwable) {
            Result.failure(
                StorageSaveException(t.message ?: "Failed to export image.", SaveFailureReason.IO_ERROR, t)
            )
        } finally {
            bitmap.recycle()
        }
    }

    fun decodeBitmapSafely(filePath: String, mediaStoreUri: String? = null): Bitmap? {
        if (filePath.isNotBlank()) {
            val file = File(filePath)
            if (file.exists() && file.length() > 0L) {
                decodeFileWithDownsample(file.absolutePath)?.let { return it }
            }
        }
        if (!mediaStoreUri.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull() ?: return null
            return decodeUriBitmapSafely(uri)
        }
        return null
    }

    private fun decodeFileWithDownsample(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(path, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        return try {
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } catch (_: OutOfMemoryError) {
            runCatching {
                BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize * 2 })
            }.getOrNull()
        }
    }

    private fun decodeUriBitmapSafely(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        while (maxDim / sampleSize > MAX_DECODE_DIMENSION) {
            sampleSize *= 2
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
            }
        } catch (_: OutOfMemoryError) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize * 2 })
                }
            }.getOrNull()
        } catch (_: SecurityException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun updateExistingMediaStoreEntry(
        mediaStoreUri: String,
        sourceFile: File,
        displayName: String,
        mimeType: String,
        width: Int,
        height: Int
    ): MediaStorePublishOutcome {
        val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull()
            ?: return publishImageToMediaStore(sourceFile, displayName, mimeType, width, height)

        val updated = runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                sourceFile.inputStream().use { input -> input.copyTo(out) }
                out.flush()
                true
            } ?: false
        }.getOrDefault(false)

        if (updated) {
            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.SIZE, sourceFile.length())
                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000L)
                    if (width > 0) put(MediaStore.Images.Media.WIDTH, width)
                    if (height > 0) put(MediaStore.Images.Media.HEIGHT, height)
                }
                context.contentResolver.update(uri, values, null, null)
            }
            return MediaStorePublishOutcome(uri = uri, published = true, warning = null)
        }

        return publishImageToMediaStore(sourceFile, displayName, mimeType, width, height)
    }

    private fun publishImageToMediaStore(
        file: File,
        displayName: String,
        mimeType: String,
        width: Int = 0,
        height: Int = 0
    ): MediaStorePublishOutcome {
        val resolver = context.contentResolver
        val nowMs = System.currentTimeMillis()
        val nowSec = nowMs / 1000L

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var insertedUri: Uri? = null
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/ScrollShot")
                    put(MediaStore.Images.Media.DATE_ADDED, nowSec)
                    put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
                    put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                    put(MediaStore.Images.Media.SIZE, file.length())
                    if (width > 0) put(MediaStore.Images.Media.WIDTH, width)
                    if (height > 0) put(MediaStore.Images.Media.HEIGHT, height)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return MediaStorePublishOutcome(
                        uri = null,
                        published = false,
                        warning = "Saved inside ScrollShot; MediaStore could not create a public gallery entry."
                    )
                insertedUri = uri

                val wrote = resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                    true
                } ?: false

                if (!wrote) {
                    runCatching { resolver.delete(uri, null, null) }
                    return MediaStorePublishOutcome(
                        uri = null,
                        published = false,
                        warning = "Saved inside ScrollShot; could not write image stream to MediaStore."
                    )
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                MediaStorePublishOutcome(uri = uri, published = true, warning = null)
            } catch (t: Throwable) {
                insertedUri?.let { orphan -> runCatching { resolver.delete(orphan, null, null) } }
                MediaStorePublishOutcome(
                    uri = null,
                    published = false,
                    warning = "Saved inside ScrollShot (${t.localizedMessage ?: "MediaStore error"})."
                )
            }
        }

        // Android 8.0–9 (API 26–28): public Pictures/ScrollShot requires legacy WRITE_EXTERNAL_STORAGE
        if (!StoragePermissionHelper.hasLegacyGalleryWritePermission(context)) {
            return MediaStorePublishOutcome(
                uri = null,
                published = false,
                warning = "Saved inside ScrollShot. Allow storage permission to show captures in your device Gallery."
            )
        }

        return try {
            @Suppress("DEPRECATION")
            val publicPictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val scrollShotPublicDir = File(publicPictures, "ScrollShot").apply { mkdirs() }
            var publicFile = File(scrollShotPublicDir, displayName)
            if (publicFile.exists()) {
                val base = publicFile.nameWithoutExtension
                val ext = publicFile.extension.ifBlank { "png" }
                publicFile = File(scrollShotPublicDir, "${base}_${System.currentTimeMillis()}.$ext")
            }

            file.inputStream().use { input ->
                FileOutputStream(publicFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            val values = ContentValues().apply {
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATA, publicFile.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, publicFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.DATE_ADDED, nowSec)
                put(MediaStore.Images.Media.DATE_MODIFIED, nowSec)
                @Suppress("DEPRECATION")
                put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                put(MediaStore.Images.Media.SIZE, publicFile.length())
                if (width > 0) put(MediaStore.Images.Media.WIDTH, width)
                if (height > 0) put(MediaStore.Images.Media.HEIGHT, height)
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull()

            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(publicFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }

            MediaStorePublishOutcome(uri = uri, published = true, warning = null)
        } catch (t: SecurityException) {
            MediaStorePublishOutcome(
                uri = null,
                published = false,
                warning = "Saved inside ScrollShot (device gallery write permission denied)."
            )
        } catch (t: Throwable) {
            MediaStorePublishOutcome(
                uri = null,
                published = false,
                warning = "Saved inside ScrollShot (${t.localizedMessage ?: "gallery copy failed"})."
            )
        }
    }

    suspend fun importUriToAppStorageResult(
        uri: Uri,
        format: ImageFormat = ImageFormat.PNG
    ): Result<SavedImageResult> = withContext(Dispatchers.IO) {
        val bitmap = decodeUriBitmapSafely(uri)
            ?: return@withContext Result.failure(
                StorageSaveException(
                    "Unable to read selected image from device picker.",
                    SaveFailureReason.SOURCE_UNREADABLE
                )
            )
        try {
            val title = "Imported_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
            saveBitmapSafely(
                bitmap = bitmap,
                title = title,
                format = format,
                jpegQuality = 95
            )
        } finally {
            bitmap.recycle()
        }
    }

    suspend fun importUriToAppStorage(uri: Uri, format: ImageFormat = ImageFormat.PNG): SavedImageResult? {
        return importUriToAppStorageResult(uri, format).getOrNull()
    }

    fun deleteStoredFile(filePath: String, mediaStoreUri: String?) {
        if (filePath.isNotBlank()) {
            runCatching { File(filePath).delete() }
        }
        if (!mediaStoreUri.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(mediaStoreUri) }.getOrNull() ?: return
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val col = MediaStore.Images.Media.DATA
                    context.contentResolver.query(uri, arrayOf(col), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(col)
                            if (idx >= 0) {
                                c.getString(idx)?.let { legacyPath -> File(legacyPath).delete() }
                            }
                        }
                    }
                }
            }
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    fun createExportFile(fileName: String): File {
        return File(exportsDir, fileName)
    }

    fun publishDocumentToMediaStore(file: File, displayName: String, mimeType: String): Uri? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            var insertedUri: Uri? = null
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/ScrollShot")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                insertedUri = uri
                val wrote = resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                    true
                } ?: false
                if (!wrote) {
                    runCatching { resolver.delete(uri, null, null) }
                    return null
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (_: Throwable) {
                insertedUri?.let { runCatching { resolver.delete(it, null, null) } }
                null
            }
        }

        // Android 8-9: write to public Documents/ScrollShot if permission granted, else stays in exportsDir
        if (!StoragePermissionHelper.hasLegacyGalleryWritePermission(context)) {
            return null
        }
        return runCatching {
            @Suppress("DEPRECATION")
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(docsDir, "ScrollShot").apply { mkdirs() }
            val dest = File(targetDir, displayName)
            file.inputStream().use { input ->
                FileOutputStream(dest).use { out ->
                    input.copyTo(out)
                    out.flush()
                }
            }
            MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), arrayOf(mimeType), null)
            Uri.fromFile(dest)
        }.getOrNull()
    }

    fun getShareUri(filePath: String, mediaStoreUri: String? = null): Uri {
        val file = if (filePath.isNotBlank()) File(filePath) else null
        if (file != null && file.exists()) {
            return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        if (!mediaStoreUri.isNullOrBlank()) {
            return Uri.parse(mediaStoreUri)
        }
        throw IllegalStateException("No readable file or MediaStore URI to share.")
    }

    fun copyImageToClipboard(filePath: String, mediaStoreUri: String? = null) {
        val uri = getShareUri(filePath, mediaStoreUri)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newUri(context.contentResolver, "ScrollShot", uri)
        clipboard.setPrimaryClip(clip)
    }

    fun copyTextToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    fun createShareImagesIntent(filePaths: List<String>): Intent {
        return createShareImagesFromSourcesIntent(filePaths.map { it to null })
    }

    fun createShareImagesFromSourcesIntent(sources: List<Pair<String, String?>>): Intent {
        val uris = ArrayList(
            sources.mapNotNull { (path, mediaUri) ->
                if (isScreenshotAccessible(path, mediaUri)) {
                    runCatching { getShareUri(path, mediaUri) }.getOrNull()
                } else null
            }
        )
        return if (uris.size <= 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                uris.firstOrNull()?.let { putExtra(Intent.EXTRA_STREAM, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    fun createShareFileIntent(filePath: String, mimeType: String): Intent {
        val uri = getShareUri(filePath)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        private const val MIN_REQUIRED_SAVE_BYTES = 1L * 1024L * 1024L // 1 MB safety floor
        private const val MAX_DECODE_DIMENSION = 16384
    }
}
