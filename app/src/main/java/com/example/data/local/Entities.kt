package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "screenshots")
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val mediaStoreUri: String? = null,
    val albumId: Long? = null,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val format: String = "PNG",
    val isLongScreenshot: Boolean = false,
    val frameCount: Int = 1,
    val isFavorite: Boolean = false,
    val extractedText: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "capture_sessions")
data class CaptureSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val direction: String,
    val sessionDirPath: String,
    val framePathsSerialized: String,
    val status: String, // ACTIVE, INTERRUPTED, COMPLETED
    val startedAt: Long = System.currentTimeMillis()
)
