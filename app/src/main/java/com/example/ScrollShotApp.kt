package com.example

import android.app.Application
import com.example.data.local.ScrollShotDatabase
import com.example.data.preferences.SettingsDataStore
import com.example.data.repository.ScreenshotRepository
import com.example.data.storage.MediaStorageManager

class ScrollShotApp : Application() {

    lateinit var database: ScrollShotDatabase
        private set

    lateinit var settingsDataStore: SettingsDataStore
        private set

    lateinit var repository: ScreenshotRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = ScrollShotDatabase.getInstance(this)
        settingsDataStore = SettingsDataStore(this)
        val storage = MediaStorageManager(this)
        repository = ScreenshotRepository(
            screenshotDao = database.screenshotDao(),
            albumDao = database.albumDao(),
            captureSessionDao = database.captureSessionDao(),
            storage = storage
        )
    }
}
