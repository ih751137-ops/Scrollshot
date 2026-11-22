package com.example

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `main activity launches and navigates across all top level screens`() {
        composeRule.waitForIdle()
        composeRule.onNodeWithText("ScrollShot").assertIsDisplayed()
        composeRule.onNodeWithText("Auto Detect").assertIsDisplayed()
        composeRule.onNodeWithText("Manual").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Auto-Scroll").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("start_long_screenshot_btn").performScrollTo().assertIsDisplayed()

        // Navigate to Gallery
        composeRule.onNodeWithText("Gallery").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("gallery_search_input").assertIsDisplayed()

        // Navigate to Editor
        composeRule.onNodeWithText("Editor").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open an Image to Edit").assertIsDisplayed()

        // Navigate to Settings
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Capture & Stitching").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Auto-Scroll Accessibility Service").fetchSemanticsNodes().isEmpty())

        // Navigate back to Home -> PDF
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("card_pdf"))
        composeRule.onNodeWithTag("card_pdf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Convert to PDF").assertIsDisplayed()
    }

    @Test
    fun `manifest excludes accessibility service and broad sensitive permissions`() {
        val activity = composeRule.activity
        val pkgInfo = activity.packageManager.getPackageInfo(
            activity.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val permissions = pkgInfo.requestedPermissions?.toSet().orEmpty()
        assertFalse(permissions.contains(Manifest.permission.READ_EXTERNAL_STORAGE))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(permissions.contains("android.permission.READ_MEDIA_VIDEO"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
        assertFalse(permissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        assertFalse(permissions.contains("android.permission.QUERY_ALL_PACKAGES"))
        assertFalse(permissions.contains(Manifest.permission.BIND_ACCESSIBILITY_SERVICE))

        val serviceNames = pkgInfo.services?.map { it.name }.orEmpty()
        assertTrue(serviceNames.any { it.endsWith("ScreenCaptureService") })
        assertFalse(serviceNames.any { it.contains("Accessibility") })
    }
}
