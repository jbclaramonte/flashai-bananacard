package com.mobeezz.bananacard.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mobeezz.bananacard.ui.navigation.AppNavHost
import com.mobeezz.bananacard.ui.placeholder.PLACEHOLDER_TAG
import com.mobeezz.bananacard.ui.theme.BananaCardTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppNavHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun launch() {
        rule.setContent { BananaCardTheme { AppNavHost() } }
    }

    @Test
    fun startsOnLibraryTab() {
        launch()
        rule.onNodeWithTag(PLACEHOLDER_TAG).assertTextEquals("LIBRARY")
    }

    @Test
    fun tappingReviewShowsReviewPlaceholder() {
        launch()
        rule.onNodeWithText("REVIEW").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PLACEHOLDER_TAG).assertTextEquals("REVIEW")
    }

    @Test
    fun tappingProfileShowsProfilePlaceholder() {
        launch()
        rule.onNodeWithText("PROFILE").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(PLACEHOLDER_TAG).assertTextEquals("PROFILE")
    }

    @Test
    fun backFromReviewReturnsToLibrary() {
        launch()
        rule.onNodeWithText("REVIEW").performClick()
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag(PLACEHOLDER_TAG).assertTextEquals("LIBRARY")
    }
}
