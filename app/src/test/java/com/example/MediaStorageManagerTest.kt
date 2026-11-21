package com.example

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ImageFormat
import com.example.data.storage.GalleryStorageAccessMode
import com.example.data.storage.SaveFailureReason
import com.example.data.storage.StoragePermissionHelper
import com.example.data.storage.StorageSaveException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaStorageManagerTest {

    @Before
    fun cleanDatabaseAndStorage() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()
            withContext(Dispatchers.IO) {
                app.database.clearAllTables()
                app.repository.storage.capturesDirectory.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
    }

    private fun warmUpGraphicsAndCreateBitmap(w: Int = 120, h: Int = 240, color: Int = Color.CYAN): Bitmap {
        val warmup = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        Canvas(warmup)
        warmup.recycle()
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
    }

    @Test
    @Config(sdk = [36])
    fun `permission helper requires zero storage permissions on Android 10 through 16`() {
        val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()

        assertFalse(StoragePermissionHelper.requiresLegacyGalleryWritePermission(Build.VERSION_CODES.Q))
        assertFalse(StoragePermissionHelper.requiresLegacyGalleryWritePermission(Build.VERSION_CODES.TIRAMISU))
        assertFalse(StoragePermissionHelper.requiresLegacyGalleryWritePermission(36))

        assertTrue(StoragePermissionHelper.requiredGalleryWritePermissions(Build.VERSION_CODES.Q).isEmpty())
        assertTrue(StoragePermissionHelper.requiredGalleryWritePermissions(36).isEmpty())

        assertTrue(StoragePermissionHelper.hasLegacyGalleryWritePermission(app, Build.VERSION_CODES.Q))
        assertEquals(
            GalleryStorageAccessMode.SCOPED_MEDIA_STORE,
            StoragePermissionHelper.getAccessMode(app, Build.VERSION_CODES.Q)
        )
        assertEquals(
            GalleryStorageAccessMode.SCOPED_MEDIA_STORE,
            StoragePermissionHelper.getAccessMode(app, 36)
        )
    }

    @Test
    @Config(sdk = [28])
    fun `android 8 and 9 falls back gracefully to app storage when write permission denied and publishes when granted`() {
        val bmp = warmUpGraphicsAndCreateBitmap(200, 400, Color.GREEN)
        val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()
        val shadowApp = shadowOf(app)

        // 1. Permission denied on API 28
        shadowApp.denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertTrue(StoragePermissionHelper.requiresLegacyGalleryWritePermission(Build.VERSION_CODES.P))
        assertFalse(StoragePermissionHelper.hasLegacyGalleryWritePermission(app, Build.VERSION_CODES.P))
        assertEquals(
            GalleryStorageAccessMode.APP_STORAGE_ONLY_PERMISSION_DENIED,
            StoragePermissionHelper.getAccessMode(app, Build.VERSION_CODES.P)
        )

        runBlocking {
            val deniedSave = app.repository.saveNewScreenshotResult(
                bitmap = bmp,
                title = "LegacyDeniedShot",
                format = ImageFormat.PNG,
                jpegQuality = 92,
                isLongScreenshot = false,
                frameCount = 1
            )
            assertTrue(deniedSave.isSuccess)
            val outcome = deniedSave.getOrThrow()
            assertTrue(File(outcome.entity.filePath).exists())
            assertFalse(outcome.galleryPublished)
            assertNotNull(outcome.galleryWarning)

            // 2. Grant permission on API 28 -> publishes to public Pictures/ScrollShot too
            shadowApp.grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            assertEquals(
                GalleryStorageAccessMode.LEGACY_PUBLIC_GALLERY_GRANTED,
                StoragePermissionHelper.getAccessMode(app, Build.VERSION_CODES.P)
            )
            val grantedSave = app.repository.saveNewScreenshotResult(
                bitmap = bmp,
                title = "LegacyGrantedShot",
                format = ImageFormat.PNG,
                jpegQuality = 92,
                isLongScreenshot = false,
                frameCount = 1
            )
            assertTrue(grantedSave.isSuccess)
            val grantedOutcome = grantedSave.getOrThrow()
            assertTrue(File(grantedOutcome.entity.filePath).exists())
            assertTrue(grantedOutcome.galleryPublished)
            assertNull(grantedOutcome.galleryWarning)
        }
        bmp.recycle()
    }

    @Test
    @Config(sdk = [36])
    fun `saveBitmapResult publishes to MediaStore on Android 10+ and handles save failures safely`() {
        val bmp = warmUpGraphicsAndCreateBitmap(320, 640, Color.MAGENTA)
        val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()
        val repo = app.repository

        runBlocking {
            val res = repo.saveNewScreenshotResult(
                bitmap = bmp,
                title = "ScopedGalleryShot",
                format = ImageFormat.PNG,
                jpegQuality = 95,
                isLongScreenshot = false,
                frameCount = 1
            )
            assertTrue(res.isSuccess)
            val outcome = res.getOrThrow()
            assertTrue(File(outcome.entity.filePath).exists())
            assertNotNull(outcome.entity.mediaStoreUri)
            assertTrue(outcome.galleryPublished)
            assertTrue(repo.storage.isMediaUriReadable(outcome.entity.mediaStoreUri))

            // Sync must not create duplicates when MediaStore + capturesDir both contain the capture
            val report1 = repo.syncStorageWithDatabase()
            assertEquals(0, report1.addedCount)
            assertEquals(1, repo.getScreenshotsByIds(listOf(outcome.entity.id)).size)

            // Remove only the local app file and verify MediaStore URI fallback keeps it accessible
            File(outcome.entity.filePath).delete()
            assertTrue(repo.storage.isScreenshotAccessible(outcome.entity.filePath, outcome.entity.mediaStoreUri))
            val decoded = repo.storage.decodeBitmapSafely(outcome.entity.filePath, outcome.entity.mediaStoreUri)
            assertNotNull(decoded)
            decoded?.recycle()

            val shareIntent = repo.storage.createShareImagesFromSourcesIntent(
                listOf(outcome.entity.filePath to outcome.entity.mediaStoreUri)
            )
            assertEquals(Intent.ACTION_SEND, shareIntent.action)
            assertEquals(
                Uri.parse(outcome.entity.mediaStoreUri),
                @Suppress("DEPRECATION") shareIntent.getParcelableExtra(Intent.EXTRA_STREAM)
            )

            // Verify recycled bitmap save fails cleanly with StorageSaveException instead of crashing
            val recycled = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            recycled.recycle()
            val failedSave = repo.saveNewScreenshotResult(
                bitmap = recycled,
                title = "BrokenBitmap",
                format = ImageFormat.PNG,
                jpegQuality = 90,
                isLongScreenshot = false,
                frameCount = 1
            )
            assertTrue(failedSave.isFailure)
            val ex = failedSave.exceptionOrNull() as? StorageSaveException
            assertNotNull(ex)
            assertEquals(SaveFailureReason.ENCODE_FAILED, ex!!.reason)
        }

        bmp.recycle()
        shadowOf(Looper.getMainLooper()).idle()
    }
}
