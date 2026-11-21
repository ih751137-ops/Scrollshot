package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

        // Navigate back to Home -> PDF
        composeRule.onNodeWithText("Home").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_lazy_column").performScrollToNode(hasTestTag("card_pdf"))
        composeRule.onNodeWithTag("card_pdf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Convert to PDF").assertIsDisplayed()
    }
}
