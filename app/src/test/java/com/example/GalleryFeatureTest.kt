package com.example

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ImageFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryFeatureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

    @Test
    fun `gallery screen and full-screen preview support grid metadata, multi-select, zoom, and adjacent navigation`() {
        // Initialize UI & native graphics on main thread first
        composeRule.waitForIdle()

        val app = ApplicationProvider.getApplicationContext<ScrollShotApp>()
        val repo = app.repository

        val (shot1, shot2) = runBlocking {
            val bmp1 = createTestBitmap(540, 1200, Color.BLUE)
            val s1 = repo.saveNewScreenshot(bmp1, "First_Shot", ImageFormat.PNG, 92, false, 1)
            bmp1.recycle()

            val bmp2 = createTestBitmap(720, 1600, Color.GREEN)
            val s2 = repo.saveNewScreenshot(bmp2, "Second_Shot", ImageFormat.PNG, 92, true, 3)
            bmp2.recycle()
            s1 to s2
        }

        // Navigate to Gallery tab
        composeRule.onNodeWithText("Gallery").performClick()
        composeRule.waitForIdle()

        // Wait until cards appear in LazyVerticalGrid automatically after capture
        composeRule.waitUntil(5000L) {
            runCatching {
                composeRule.onNodeWithTag("gallery_card_${shot2.id}").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // Verify metadata shown on card
        composeRule.onNodeWithText("Second_Shot.png").assertIsDisplayed()
        composeRule.onNodeWithText("720×1600", substring = true).assertIsDisplayed()

        // Long press to enter multi-select mode and verify count + Close
        composeRule.onNodeWithTag("gallery_card_${shot2.id}").performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_selection_bar").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_count_text").assertIsDisplayed()
        composeRule.onNodeWithTag("selection_close_btn").performClick()
        composeRule.waitForIdle()

        // Tap card to open full-screen preview
        composeRule.onNodeWithTag("gallery_card_${shot2.id}").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("preview_filename_text").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_metadata_text").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_page_indicator").assertIsDisplayed()

        // Double-tap to zoom in -> verify zoom percent indicator appears, then double-tap again to reset
        composeRule.onNodeWithTag("preview_zoom_surface_${shot2.id}").performTouchInput {
            doubleClick(Offset(center.x, center.y))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("preview_zoom_percent").assertIsDisplayed()

        composeRule.onNodeWithTag("preview_zoom_surface_${shot2.id}").performTouchInput {
            doubleClick(Offset(center.x, center.y))
        }
        composeRule.waitForIdle()

        // Navigate to adjacent screenshot via Next button
        composeRule.onNodeWithTag("preview_next_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First_Shot.png").assertIsDisplayed()

        // Verify Share, Delete, Edit, and Export options are displayed in Preview
        composeRule.onNodeWithTag("preview_edit_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_export_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_share_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_delete_btn").assertIsDisplayed()

        // Navigate back to previous screenshot
        composeRule.onNodeWithTag("preview_prev_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second_Shot.png").assertIsDisplayed()
    }
}
