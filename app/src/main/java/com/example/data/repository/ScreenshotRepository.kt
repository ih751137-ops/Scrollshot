package com.example.data.repository

import android.graphics.Bitmap
import android.net.Uri
import com.example.data.local.AlbumDao
import com.example.data.local.AlbumEntity
import com.example.data.local.CaptureSessionDao
import com.example.data.local.CaptureSessionEntity
import com.example.data.local.ScreenshotDao
import com.example.data.local.ScreenshotEntity
import com.example.data.model.ImageFormat
import com.example.data.storage.ExportImageResult
import com.example.data.storage.GalleryStorageAccessMode
import com.example.data.storage.MediaStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class StorageSyncReport(
    val addedCount: Int = 0,
    val mediaStoreRecoveredCount: Int = 0,
    val removedMissingCount: Int = 0,
    val corruptedIds: Set<Long> = emptySet(),
    val storageDirectoryPath: String = ""
)

data class SaveScreenshotOutcome(
    val entity: ScreenshotEntity,
    val galleryPublished: Boolean,
    val galleryWarning: String? = null
)

class ScreenshotRepository(
    private val screenshotDao: ScreenshotDao,
    private val albumDao: AlbumDao,
    private val captureSessionDao: CaptureSessionDao,
    val storage: MediaStorageManager
) {
    private val syncMutex = Mutex()

    val allScreenshots: Flow<List<ScreenshotEntity>> = screenshotDao.observeAll()
    val allAlbums: Flow<List<AlbumEntity>> = albumDao.observeAll()
    val recoverableSession: Flow<CaptureSessionEntity?> = captureSessionDao.observeRecoverableSession()

    suspend fun getScreenshotById(id: Long): ScreenshotEntity? = screenshotDao.getById(id)

    suspend fun getScreenshotsByIds(ids: List<Long>): List<ScreenshotEntity> = screenshotDao.getByIds(ids)

    suspend fun syncStorageWithDatabase(): StorageSyncReport = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val existing = screenshotDao.getAllOnce()
            val missingIds = mutableListOf<Long>()
            val corruptedIds = mutableSetOf<Long>()
            val knownCanonicalPaths = mutableSetOf<String>()
            val knownMediaStoreUris = mutableSetOf<String>()
            val knownNameSizeKeys = mutableSetOf<String>()

            existing.forEach { entity ->
                val file = if (entity.filePath.isNotBlank()) File(entity.filePath) else null
                val fileExists = file != null && file.exists() && file.length() > 0L
                val uriReadable = storage.isMediaUriReadable(entity.mediaStoreUri)

                if (!fileExists && !uriReadable) {
                    missingIds.add(entity.id)
                } else {
                    if (file != null && fileExists) {
                        val canonical = runCatching { file.canonicalPath }.getOrDefault(entity.filePath)
                        knownCanonicalPaths.add(canonical)
                    }
                    entity.mediaStoreUri?.takeIf { it.isNotBlank() }?.let { knownMediaStoreUris.add(it) }
                    knownNameSizeKeys.add(buildDedupeKey(entity.title, entity.fileSizeBytes))

                    val inspected = storage.inspectImageSource(entity.filePath, entity.mediaStoreUri)
                    if (inspected == null) {
                        corruptedIds.add(entity.id)
                    } else if (
                        inspected.width != entity.width ||
                        inspected.height != entity.height ||
                        (inspected.fileSizeBytes > 0L && inspected.fileSizeBytes != entity.fileSizeBytes)
                    ) {
                        val resolvedSize = if (inspected.fileSizeBytes > 0L) inspected.fileSizeBytes else entity.fileSizeBytes
                        screenshotDao.updateMetadata(
                            id = entity.id,
                            width = inspected.width,
                            height = inspected.height,
                            fileSizeBytes = resolvedSize
                        )
                        knownNameSizeKeys.add(buildDedupeKey(entity.title, resolvedSize))
                    }
                }
            }

            if (missingIds.isNotEmpty()) {
                screenshotDao.deleteByIds(missingIds)
            }

            var addedCount = 0
            val scanned = storage.scanCapturesDirectory()
            scanned.forEach { diskItem ->
                if (diskItem.filePath !in knownCanonicalPaths) {
                    knownCanonicalPaths.add(diskItem.filePath)
                    knownNameSizeKeys.add(buildDedupeKey(diskItem.title, diskItem.fileSizeBytes))
                    val entity = ScreenshotEntity(
                        title = diskItem.title,
                        filePath = diskItem.filePath,
                        mediaStoreUri = null,
                        width = diskItem.width,
                        height = diskItem.height,
                        fileSizeBytes = diskItem.fileSizeBytes,
                        format = diskItem.format.name,
                        isLongScreenshot = diskItem.height > diskItem.width * 2.4f,
                        frameCount = 1,
                        createdAt = diskItem.lastModified,
                        updatedAt = diskItem.lastModified
                    )
                    screenshotDao.insert(entity)
                    addedCount++
                }
            }

            var mediaStoreRecoveredCount = 0
            val mediaStoreImages = storage.scanAppOwnedMediaStoreImages()
            mediaStoreImages.forEach { msItem ->
                val dedupeKey = buildDedupeKey(msItem.title, msItem.fileSizeBytes)
                if (msItem.mediaStoreUri !in knownMediaStoreUris && dedupeKey !in knownNameSizeKeys) {
                    knownMediaStoreUris.add(msItem.mediaStoreUri)
                    knownNameSizeKeys.add(dedupeKey)
                    val entity = ScreenshotEntity(
                        title = msItem.title,
                        filePath = "",
                        mediaStoreUri = msItem.mediaStoreUri,
                        width = msItem.width,
                        height = msItem.height,
                        fileSizeBytes = msItem.fileSizeBytes,
                        format = msItem.format.name,
                        isLongScreenshot = msItem.height > msItem.width * 2.4f,
                        frameCount = 1,
                        createdAt = msItem.dateTakenMillis,
                        updatedAt = msItem.dateTakenMillis
                    )
                    screenshotDao.insert(entity)
                    mediaStoreRecoveredCount++
                    addedCount++
                }
            }

            StorageSyncReport(
                addedCount = addedCount,
                mediaStoreRecoveredCount = mediaStoreRecoveredCount,
                removedMissingCount = missingIds.size,
                corruptedIds = corruptedIds,
                storageDirectoryPath = storage.capturesDirectory.absolutePath
            )
        }
    }

    private fun buildDedupeKey(title: String, sizeBytes: Long): String {
        val clean = storage.sanitizeFileNameTitle(title).lowercase()
        return "$clean|$sizeBytes"
    }

    suspend fun saveNewScreenshotResult(
        bitmap: Bitmap,
        title: String,
        format: ImageFormat,
        jpegQuality: Int,
        isLongScreenshot: Boolean,
        frameCount: Int,
        albumId: Long? = null
    ): Result<SaveScreenshotOutcome> {
        return storage.saveBitmapSafely(bitmap, title, format, jpegQuality).mapCatching { saved ->
            val entity = ScreenshotEntity(
                title = saved.title,
                filePath = saved.filePath,
                mediaStoreUri = saved.mediaStoreUri,
                albumId = albumId,
                width = saved.width,
                height = saved.height,
                fileSizeBytes = saved.fileSizeBytes,
                format = format.name,
                isLongScreenshot = isLongScreenshot,
                frameCount = frameCount
            )
            val newId = screenshotDao.insert(entity)
            SaveScreenshotOutcome(
                entity = entity.copy(id = newId),
                galleryPublished = saved.galleryPublished,
                galleryWarning = saved.galleryWarning
            )
        }
    }

    suspend fun saveNewScreenshot(
        bitmap: Bitmap,
        title: String,
        format: ImageFormat,
        jpegQuality: Int,
        isLongScreenshot: Boolean,
        frameCount: Int,
        albumId: Long? = null
    ): ScreenshotEntity {
        return saveNewScreenshotResult(
            bitmap = bitmap,
            title = title,
            format = format,
            jpegQuality = jpegQuality,
            isLongScreenshot = isLongScreenshot,
            frameCount = frameCount,
            albumId = albumId
        ).getOrThrow().entity
    }

    suspend fun saveEditedCopyResult(
        bitmap: Bitmap,
        baseTitle: String,
        format: ImageFormat,
        jpegQuality: Int,
        isLongScreenshot: Boolean,
        albumId: Long?
    ): Result<SaveScreenshotOutcome> {
        return saveNewScreenshotResult(
            bitmap = bitmap,
            title = "${baseTitle}_edited",
            format = format,
            jpegQuality = jpegQuality,
            isLongScreenshot = isLongScreenshot,
            frameCount = 1,
            albumId = albumId
        )
    }

    suspend fun saveEditedCopy(
        bitmap: Bitmap,
        baseTitle: String,
        format: ImageFormat,
        jpegQuality: Int,
        isLongScreenshot: Boolean,
        albumId: Long?
    ): ScreenshotEntity {
        return saveEditedCopyResult(
            bitmap = bitmap,
            baseTitle = baseTitle,
            format = format,
            jpegQuality = jpegQuality,
            isLongScreenshot = isLongScreenshot,
            albumId = albumId
        ).getOrThrow().entity
    }

    suspend fun overwriteScreenshotResult(
        existing: ScreenshotEntity,
        bitmap: Bitmap,
        format: ImageFormat,
        jpegQuality: Int
    ): Result<SaveScreenshotOutcome> {
        val targetPath = existing.filePath.takeIf { it.isNotBlank() }
        return storage.saveBitmapSafely(
            bitmap = bitmap,
            title = existing.title,
            format = format,
            jpegQuality = jpegQuality,
            overwritePath = targetPath,
            existingMediaStoreUri = existing.mediaStoreUri
        ).mapCatching { saved ->
            val updated = existing.copy(
                filePath = saved.filePath,
                mediaStoreUri = saved.mediaStoreUri ?: existing.mediaStoreUri,
                width = saved.width,
                height = saved.height,
                fileSizeBytes = saved.fileSizeBytes,
                format = format.name,
                updatedAt = System.currentTimeMillis()
            )
            screenshotDao.update(updated)
            SaveScreenshotOutcome(
                entity = updated,
                galleryPublished = saved.galleryPublished,
                galleryWarning = saved.galleryWarning
            )
        }
    }

    suspend fun overwriteScreenshot(
        existing: ScreenshotEntity,
        bitmap: Bitmap,
        format: ImageFormat,
        jpegQuality: Int
    ): ScreenshotEntity {
        return overwriteScreenshotResult(existing, bitmap, format, jpegQuality).getOrThrow().entity
    }

    suspend fun importFromUriResult(uri: Uri): Result<SaveScreenshotOutcome> {
        return storage.importUriToAppStorageResult(uri).mapCatching { saved ->
            val entity = ScreenshotEntity(
                title = saved.title,
                filePath = saved.filePath,
                mediaStoreUri = saved.mediaStoreUri,
                width = saved.width,
                height = saved.height,
                fileSizeBytes = saved.fileSizeBytes,
                format = ImageFormat.PNG.name,
                isLongScreenshot = saved.height > saved.width * 2.4f,
                frameCount = 1
            )
            val id = screenshotDao.insert(entity)
            SaveScreenshotOutcome(
                entity = entity.copy(id = id),
                galleryPublished = saved.galleryPublished,
                galleryWarning = saved.galleryWarning
            )
        }
    }

    suspend fun importFromUri(uri: Uri): ScreenshotEntity? {
        return importFromUriResult(uri).getOrNull()?.entity
    }

    suspend fun deleteScreenshot(entity: ScreenshotEntity) {
        storage.deleteStoredFile(entity.filePath, entity.mediaStoreUri)
        screenshotDao.delete(entity)
    }

    suspend fun deleteScreenshots(entities: List<ScreenshotEntity>) {
        entities.forEach { storage.deleteStoredFile(it.filePath, it.mediaStoreUri) }
        screenshotDao.deleteByIds(entities.map { it.id })
    }

    suspend fun renameScreenshot(id: Long, newTitle: String): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isBlank()) return false
        val existing = screenshotDao.getById(id) ?: return false
        val format = runCatching { ImageFormat.valueOf(existing.format) }.getOrDefault(ImageFormat.PNG)
        val renamed = storage.renameStoredFile(
            oldFilePath = existing.filePath,
            newTitle = trimmed,
            format = format,
            mediaStoreUri = existing.mediaStoreUri
        )
        if (renamed != null) {
            screenshotDao.updateFileRename(
                id = id,
                title = renamed.title,
                filePath = renamed.filePath,
                fileSizeBytes = if (renamed.fileSizeBytes > 0L) renamed.fileSizeBytes else existing.fileSizeBytes
            )
        } else {
            screenshotDao.rename(id, trimmed)
        }
        return true
    }

    suspend fun duplicateScreenshot(entity: ScreenshotEntity): ScreenshotEntity? {
        val format = runCatching { ImageFormat.valueOf(entity.format) }.getOrDefault(ImageFormat.PNG)
        val copyTitle = "${entity.title}_copy"
        val saved = storage.copyStoredFile(
            sourcePath = entity.filePath,
            newTitle = copyTitle,
            format = format,
            sourceMediaStoreUri = entity.mediaStoreUri
        ) ?: return null
        val copiedEntity = ScreenshotEntity(
            title = saved.title,
            filePath = saved.filePath,
            mediaStoreUri = saved.mediaStoreUri,
            albumId = entity.albumId,
            width = saved.width,
            height = saved.height,
            fileSizeBytes = saved.fileSizeBytes,
            format = format.name,
            isLongScreenshot = entity.isLongScreenshot,
            frameCount = entity.frameCount,
            isFavorite = false,
            extractedText = entity.extractedText
        )
        val id = screenshotDao.insert(copiedEntity)
        return copiedEntity.copy(id = id)
    }

    suspend fun exportScreenshotAsFormat(
        entity: ScreenshotEntity,
        targetFormat: ImageFormat,
        jpegQuality: Int,
        addToGallery: Boolean
    ): ExportImageResult? {
        val exported = storage.exportImageAsFormat(
            sourcePath = entity.filePath,
            outputTitle = entity.title,
            targetFormat = targetFormat,
            jpegQuality = jpegQuality,
            saveToGalleryDir = addToGallery,
            sourceMediaStoreUri = entity.mediaStoreUri
        ) ?: return null

        if (addToGallery) {
            val newEntity = ScreenshotEntity(
                title = "${entity.title}_${targetFormat.name}",
                filePath = exported.filePath,
                mediaStoreUri = exported.mediaStoreUri,
                albumId = entity.albumId,
                width = exported.width,
                height = exported.height,
                fileSizeBytes = exported.fileSizeBytes,
                format = targetFormat.name,
                isLongScreenshot = entity.isLongScreenshot,
                frameCount = entity.frameCount
            )
            screenshotDao.insert(newEntity)
        }
        return exported
    }

    suspend fun toggleFavorite(entity: ScreenshotEntity) {
        screenshotDao.setFavorite(entity.id, !entity.isFavorite)
    }

    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?) {
        screenshotDao.moveToAlbum(ids, albumId)
    }

    suspend fun removeFromAlbum(ids: List<Long>) {
        screenshotDao.moveToAlbum(ids, null)
    }

    suspend fun updateExtractedText(id: Long, text: String) {
        screenshotDao.updateExtractedText(id, text)
    }

    suspend fun createAlbum(name: String): Long {
        return albumDao.insert(AlbumEntity(name = name.trim()))
    }

    suspend fun renameAlbum(albumId: Long, newName: String) {
        if (newName.isNotBlank()) {
            albumDao.rename(albumId, newName.trim())
        }
    }

    suspend fun deleteAlbum(albumId: Long) {
        // Always detach screenshots first so original images remain safely in Gallery
        screenshotDao.clearAlbumAssignment(albumId)
        albumDao.deleteById(albumId)
    }

    suspend fun upsertActiveSession(session: CaptureSessionEntity): Long = captureSessionDao.upsert(session)

    suspend fun updateSessionProgress(id: Long, status: String, framePaths: List<String>) {
        captureSessionDao.updateProgress(id, status, framePaths.joinToString("\n"))
    }

    suspend fun discardSession(session: CaptureSessionEntity) {
        runCatching { File(session.sessionDirPath).deleteRecursively() }
        captureSessionDao.deleteById(session.id)
    }

    suspend fun completeSession(id: Long) {
        captureSessionDao.deleteById(id)
    }
}
