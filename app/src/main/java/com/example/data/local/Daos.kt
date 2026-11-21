package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {
    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ScreenshotEntity>>

    @Query("SELECT * FROM screenshots ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    suspend fun getById(id: Long): ScreenshotEntity?

    @Query("SELECT * FROM screenshots WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<ScreenshotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScreenshotEntity): Long

    @Update
    suspend fun update(entity: ScreenshotEntity)

    @Delete
    suspend fun delete(entity: ScreenshotEntity)

    @Query("DELETE FROM screenshots WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE screenshots SET albumId = :albumId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveToAlbum(ids: List<Long>, albumId: Long?, now: Long = System.currentTimeMillis())

    @Query("UPDATE screenshots SET albumId = NULL, updatedAt = :now WHERE albumId = :albumId")
    suspend fun clearAlbumAssignment(albumId: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE screenshots SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long = System.currentTimeMillis())

    @Query("UPDATE screenshots SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE screenshots SET title = :title, filePath = :filePath, fileSizeBytes = :fileSizeBytes, updatedAt = :now WHERE id = :id")
    suspend fun updateFileRename(
        id: Long,
        title: String,
        filePath: String,
        fileSizeBytes: Long,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE screenshots SET width = :width, height = :height, fileSizeBytes = :fileSizeBytes, updatedAt = :now WHERE id = :id")
    suspend fun updateMetadata(
        id: Long,
        width: Int,
        height: Int,
        fileSizeBytes: Long,
        now: Long = System.currentTimeMillis()
    )

    @Query("UPDATE screenshots SET extractedText = :text, updatedAt = :now WHERE id = :id")
    suspend fun updateExtractedText(id: Long, text: String, now: Long = System.currentTimeMillis())
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: AlbumEntity): Long

    @Query("DELETE FROM albums WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}

@Dao
interface CaptureSessionDao {
    @Query("SELECT * FROM capture_sessions WHERE status IN ('ACTIVE', 'INTERRUPTED') ORDER BY startedAt DESC LIMIT 1")
    fun observeRecoverableSession(): Flow<CaptureSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: CaptureSessionEntity): Long

    @Query("UPDATE capture_sessions SET status = :status, framePathsSerialized = :frames WHERE id = :id")
    suspend fun updateProgress(id: Long, status: String, frames: String)

    @Query("DELETE FROM capture_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM capture_sessions WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
