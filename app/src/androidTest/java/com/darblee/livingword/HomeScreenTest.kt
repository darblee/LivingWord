package com.darblee.livingword

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import com.darblee.livingword.ui.theme.SetColorTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.darblee.livingword.ui.theme.ColorThemeOption

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    /**
     * createAndroidComposeRule allows you to launch and test an Activity.
     * It gives you access to the activity context and is the standard way
     * to test a full screen or navigation flows.
     */
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreen_displaysCorrectly() {
        // We set the content within the test rule.
        // Here we are just applying the theme, as the HomeScreen is
        // the default start destination in the NavGraph launched by MainActivity.
/*        composeTestRule.setContent {
            SetColorTheme(ColorThemeOption.System) {
                // The NavHost in MainActivity will display HomeScreen by default.
            }
        }*/

        // Wait for the UI to settle, especially if there are async operations.
        composeTestRule.waitForIdle()

        // 1. Verify that the main screen title is displayed.
        // The title "Prepare your heart" is in the AppScaffold.
        composeTestRule.onNodeWithText("Prepare your heart").assertIsDisplayed()

        // 2. Verify the "Verse of the Day" section title is displayed.
        composeTestRule.onNodeWithText("Verse of the Day").assertIsDisplayed()

        // 3. Verify the "Daily Prayer" section title is displayed.
        composeTestRule.onNodeWithText("Daily Prayer").assertIsDisplayed()

        // 4. Verify the "Add" button is displayed within the VOTD card.
        // This is a more specific check.
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()
    }

    /**
     * This is an instrumented test that makes a real network call to fetch scripture.
     * It requires an active internet connection and a valid API key configured in the app.
     * It tests fetching a single verse.
     */
    @Test
    fun fetchScripture_John3_16_AMP_returnsCorrectVerse() = runBlocking {
        // Arrange
        val verseRef = BibleVerseRef("John", 3, 16, endVerse = 16)
        val translation = "AMP"

        // Act
        // The GeminiAIService is configured in MainActivity's onCreate, which is launched by the rule.
        val result = GeminiAIService.fetchScripture(verseRef, translation)

        // Assert
        assertTrue("API call should be successful, but was: $result", result is AiServiceResult.Success)
        val verses = (result as AiServiceResult.Success).data
        assertEquals("Should return 1 verse", 1, verses.size)
        assertEquals("Verse number should be 16", 16, verses[0].verseNum)
        assertTrue("Verse text should not be empty", verses[0].verseString.isNotEmpty())
        // A more specific check to ensure we got something relevant for John 3:16
        assertTrue("Verse text for John 3:16 should contain 'For God so loved'", verses[0].verseString.contains("For God so loved", ignoreCase = true))
    }

    /**
     * This is an instrumented test that makes a real network call to fetch scripture.
     * It requires an active internet connection and a valid API key configured in the app.
     * It tests fetching a range of verses.
     */
    @Test
    fun fetchScripture_Romans12_12_14_ESV_returnsCorrectVerses() = runBlocking {
        // Arrange
        val verseRef = BibleVerseRef("Romans", 12, 12, 14)
        val translation = "ESV" // This will test the ESV service fallback logic

        // Act
        val result = GeminiAIService.fetchScripture(verseRef, translation)

        // Assert
        assertTrue("API call should be successful, but was: $result", result is AiServiceResult.Success)
        val verses = (result as AiServiceResult.Success).data
        assertEquals("Should return 3 verses", 3, verses.size)
        assertEquals("First verse number should be 12", 12, verses[0].verseNum)
        assertEquals("Second verse number should be 13", 13, verses[1].verseNum)
        assertEquals("Third verse number should be 14", 14, verses[2].verseNum)
        assertTrue("Verse 12 text should not be empty", verses[0].verseString.isNotEmpty())
        assertTrue("Verse 13 text should not be empty", verses[1].verseString.isNotEmpty())
        assertTrue("Verse 14 text should not be empty", verses[2].verseString.isNotEmpty())
    }
}
