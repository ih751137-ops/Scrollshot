package com.example

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DateFilter
import com.example.data.model.GallerySort
import com.example.data.model.ImageFormat
import com.example.ui.viewmodel.GalleryFilter
import com.example.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryViewModelTest {

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

    private fun createTestBitmap(width: Int, height: Int, color: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
    }

    private suspend fun waitUntil(timeoutMs: Long = 6000L, predicate: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.currentTimeMillis() - start > timeoutMs) break
            delay(30L)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `gallery viewmodel syncs storage, handles corrupted files, searches, sorts, filters, manages albums and file actions`() {
        // Warm up Robolectric Native Graphics on main thread before Dispatchers.IO image calls
        val warmup = createTestBitmap(4, 4, Color.BLACK)
        Canvas(warmup)
        warmup.recycle()

        runBlocking {
            val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()
            val repo = app.repository
            val capturesDir = repo.storage.capturesDirectory

            // 1. Place a valid PNG file directly in capturesDir + a corrupted invalid file
            val diskFile = File(capturesDir, "1700000000000_DiskCapture.png")
            FileOutputStream(diskFile).use { out ->
                val bmp = createTestBitmap(400, 900, Color.CYAN)
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                bmp.recycle()
            }
            val corruptedFile = File(capturesDir, "corrupted_shot.png")
            corruptedFile.writeText("NOT_A_VALID_PNG")

            val vm = GalleryViewModel(app)
            vm.refreshGallery(silent = false)
            waitUntil { vm.allScreenshots.value.any { it.title == "DiskCapture" } && !vm.uiState.value.isLoading }

            assertTrue(vm.allScreenshots.value.any { it.title == "DiskCapture" && it.width == 400 && it.height == 900 })

            // Refreshing again must not duplicate DiskCapture
            vm.refreshGallery(silent = true)
            waitUntil { !vm.uiState.value.isRefreshing }
            assertEquals(1, vm.allScreenshots.value.count { it.title == "DiskCapture" })

            // 2. Save a second long screenshot via repository (simulating capture completion)
            val longBmp = createTestBitmap(600, 1800, Color.MAGENTA)
            val longSaved = repo.saveNewScreenshot(
                bitmap = longBmp,
                title = "ScrollShot_Long_Test",
                format = ImageFormat.PNG,
                jpegQuality = 92,
                isLongScreenshot = true,
                frameCount = 4
            )
            longBmp.recycle()

            waitUntil { vm.allScreenshots.value.any { it.id == longSaved.id } }
            assertEquals(2, vm.allScreenshots.value.size)

            // 3. Search, Sort (DIMENSIONS, OLDEST, NEWEST, LARGEST), Favorites, Date Filter
            vm.setSearchQuery("Long_Test")
            waitUntil { vm.filteredScreenshots.value.size == 1 }
            assertEquals(longSaved.id, vm.filteredScreenshots.value.first().id)

            vm.setSearchQuery("")
            vm.setGallerySort(GallerySort.DIMENSIONS)
            waitUntil { vm.filteredScreenshots.value.firstOrNull()?.id == longSaved.id }
            assertEquals(longSaved.id, vm.filteredScreenshots.value.first().id)

            vm.toggleFavorite(longSaved)
            vm.setGalleryFilter(GalleryFilter.FAVORITES)
            waitUntil { vm.filteredScreenshots.value.size == 1 && vm.filteredScreenshots.value.first().id == longSaved.id }

            vm.setGalleryFilter(GalleryFilter.ALL)
            vm.setDateFilter(DateFilter.TODAY)
            waitUntil { vm.filteredScreenshots.value.size == 2 }
            vm.setDateFilter(DateFilter.ALL_DATES)

            // 4. Albums: Create, Move, Rename Album, Delete Album safely preserving images
            vm.createAlbum("Receipts")
            waitUntil { vm.allAlbums.value.any { it.name == "Receipts" } }
            val albumId = vm.allAlbums.value.first { it.name == "Receipts" }.id

            vm.moveScreenshotsToAlbum(listOf(longSaved.id), albumId)
            vm.setGalleryFilter(GalleryFilter.CUSTOM_ALBUM, albumId)
            waitUntil { vm.filteredScreenshots.value.size == 1 }

            vm.renameAlbum(albumId, "Bills")
            waitUntil { vm.allAlbums.value.any { it.id == albumId && it.name == "Bills" } }

            vm.deleteAlbum(albumId)
            waitUntil { vm.allAlbums.value.none { it.id == albumId } }
            assertEquals(2, vm.allScreenshots.value.size)
            assertTrue(File(longSaved.filePath).exists())

            // 5. Real File Actions: Rename, Duplicate, Export JPEG, Share Intent, Multi-Select & Delete
            vm.renameScreenshot(longSaved.id, "Renamed_Capture")
            waitUntil { vm.allScreenshots.value.any { it.id == longSaved.id && it.title == "Renamed_Capture" } }
            val renamedShot = vm.allScreenshots.value.first { it.id == longSaved.id }
            assertTrue(File(renamedShot.filePath).exists())
            assertTrue(File(renamedShot.filePath).name.contains("Renamed_Capture"))

            vm.duplicateScreenshot(renamedShot)
            waitUntil { vm.allScreenshots.value.size == 3 }
            val copiedShot = vm.allScreenshots.value.first { it.title.contains("_copy") }
            assertTrue(File(copiedShot.filePath).exists())

            vm.exportScreenshot(renamedShot, ImageFormat.JPEG, jpegQuality = 85, addToGallery = false)
            waitUntil { vm.uiState.value.lastExportedFile != null }
            val exported = vm.uiState.value.lastExportedFile!!
            assertEquals(ImageFormat.JPEG, exported.format)
            assertTrue(File(exported.filePath).exists())

            val shareIntent = vm.createShareImagesIntent(listOf(renamedShot.filePath, copiedShot.filePath))
            assertNotNull(shareIntent)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, shareIntent!!.action)

            vm.enterSelectionMode(renamedShot.id)
            assertTrue(vm.uiState.value.isSelectionMode)
            vm.selectAll()
            assertEquals(3, vm.uiState.value.selectedIds.size)
            vm.deselectAll()
            assertFalse(vm.uiState.value.isSelectionMode)

            vm.toggleSelectScreenshot(copiedShot.id)
            vm.deleteSelectedScreenshots()
            waitUntil { vm.allScreenshots.value.none { it.id == copiedShot.id } }
            assertFalse(File(copiedShot.filePath).exists())

            corruptedFile.delete()
        }
    }
}
