package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScreenshotEntity::class,
        AlbumEntity::class,
        CaptureSessionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ScrollShotDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao
    abstract fun albumDao(): AlbumDao
    abstract fun captureSessionDao(): CaptureSessionDao

    companion object {
        @Volatile
        private var INSTANCE: ScrollShotDatabase? = null

        fun getInstance(context: Context): ScrollShotDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScrollShotDatabase::class.java,
                    "scrollshot_db"
                ).fallbackToDestructiveMigration(dropAllTables = true).build().also { INSTANCE = it }
            }
        }
    }
}
