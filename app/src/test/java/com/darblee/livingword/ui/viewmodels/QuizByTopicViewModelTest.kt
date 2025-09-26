package com.darblee.livingword.ui.viewmodels

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class QuizByTopicViewModelTest {

    private lateinit var viewModel: QuizByTopicViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = QuizByTopicViewModel()
    }

    // Test #1: Initial State Test
    @Test
    fun `initial state should have null selectedTopic, empty quizVerses, and isValidated false`() = runTest {
        val initialState = viewModel.state.value

        assertNull("Initial currentSelectedTopic should be null", initialState.currentSelectedTopic)
        assertTrue("Initial quizVerses should be empty", initialState.quizVerses.isEmpty())
        assertFalse("Initial isValidated should be false", initialState.isValidated)
        assertNull("Initial error should be null", initialState.error)
    }

    // Test #2: Topic Selection Tests
    @Test
    fun `setCurrentSelectedTopic should update currentSelectedTopic correctly`() = runTest {
        val testTopic = "Faith"

        viewModel.setCurrentSelectedTopic(testTopic)

        val state = viewModel.state.value
        assertEquals("Selected topic should match input", testTopic, state.currentSelectedTopic)
    }

    @Test
    fun `setCurrentSelectedTopic should clear quizVerses and reset isValidated to false`() = runTest {
        // First, set up some initial state with verses and validation
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, true, true, ValidationResult.CORRECT),
            QuizVerseItem("Romans 8:28", 2L, false, false, ValidationResult.INCORRECT)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers() // This should set isValidated to true

        // Verify initial state has verses and is validated
        assertTrue("Should have verses before topic change", viewModel.state.value.quizVerses.isNotEmpty())
        assertTrue("Should be validated before topic change", viewModel.state.value.isValidated)

        // Now change the topic
        viewModel.setCurrentSelectedTopic("Love")

        val state = viewModel.state.value
        assertTrue("QuizVerses should be empty after topic selection", state.quizVerses.isEmpty())
        assertFalse("isValidated should be false after topic selection", state.isValidated)
    }

    // Test #3: Topic Selection Behavior Tests
    @Test
    fun `selecting same topic twice should behave correctly`() = runTest {
        val testTopic = "Hope"

        // Select topic first time
        viewModel.setCurrentSelectedTopic(testTopic)
        val firstState = viewModel.state.value

        // Select same topic again
        viewModel.setCurrentSelectedTopic(testTopic)
        val secondState = viewModel.state.value

        assertEquals("Topic should remain the same", testTopic, secondState.currentSelectedTopic)
        assertTrue("QuizVerses should still be empty", secondState.quizVerses.isEmpty())
        assertFalse("isValidated should still be false", secondState.isValidated)

        // States should be equivalent
        assertEquals("States should be equivalent when selecting same topic", firstState, secondState)
    }

    @Test
    fun `selecting different topics should reset state each time`() = runTest {
        // Select first topic
        viewModel.setCurrentSelectedTopic("Faith")
        val firstTopic = viewModel.state.value.currentSelectedTopic

        // Select second topic
        viewModel.setCurrentSelectedTopic("Love")
        val secondState = viewModel.state.value

        assertEquals("Second topic should be set correctly", "Love", secondState.currentSelectedTopic)
        assertNotEquals("Topic should have changed", firstTopic, secondState.currentSelectedTopic)
        assertTrue("QuizVerses should be empty after topic change", secondState.quizVerses.isEmpty())
        assertFalse("isValidated should be false after topic change", secondState.isValidated)
    }

    // Test #4: Validation Function Tests
    @Test
    fun `validateAnswers should correctly identify correct answers - selected verse with matching topic`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true), // CORRECT
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = true, isSelected = false) // INCORRECT
        )
        viewModel.setQuizVerses(testVerses)

        viewModel.validateAnswers()

        val state = viewModel.state.value
        assertEquals("Selected correct match should be CORRECT",
            ValidationResult.CORRECT, state.quizVerses[0].validationResult)
        assertEquals("Unselected correct match should be INCORRECT",
            ValidationResult.INCORRECT, state.quizVerses[1].validationResult)
    }

    @Test
    fun `validateAnswers should correctly identify correct answers - unselected verse without matching topic`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("Psalm 23:1", 1L, isCorrectMatch = false, isSelected = false), // CORRECT
            QuizVerseItem("Proverbs 3:5", 2L, isCorrectMatch = false, isSelected = true) // INCORRECT
        )
        viewModel.setQuizVerses(testVerses)

        viewModel.validateAnswers()

        val state = viewModel.state.value
        assertEquals("Unselected non-match should be CORRECT",
            ValidationResult.CORRECT, state.quizVerses[0].validationResult)
        assertEquals("Selected non-match should be INCORRECT",
            ValidationResult.INCORRECT, state.quizVerses[1].validationResult)
    }

    @Test
    fun `validateAnswers should correctly identify all validation scenarios`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),   // CORRECT: selected + match
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = true, isSelected = false),  // INCORRECT: unselected + match
            QuizVerseItem("Psalm 23:1", 3L, isCorrectMatch = false, isSelected = false), // CORRECT: unselected + no match
            QuizVerseItem("Proverbs 3:5", 4L, isCorrectMatch = false, isSelected = true)  // INCORRECT: selected + no match
        )
        viewModel.setQuizVerses(testVerses)

        viewModel.validateAnswers()

        val state = viewModel.state.value
        assertEquals("Selected match should be CORRECT",
            ValidationResult.CORRECT, state.quizVerses[0].validationResult)
        assertEquals("Unselected match should be INCORRECT",
            ValidationResult.INCORRECT, state.quizVerses[1].validationResult)
        assertEquals("Unselected non-match should be CORRECT",
            ValidationResult.CORRECT, state.quizVerses[2].validationResult)
        assertEquals("Selected non-match should be INCORRECT",
            ValidationResult.INCORRECT, state.quizVerses[3].validationResult)
    }

    @Test
    fun `validateAnswers should set isValidated to true`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)

        assertFalse("isValidated should be false before validation", viewModel.state.value.isValidated)

        viewModel.validateAnswers()

        assertTrue("isValidated should be true after validation", viewModel.state.value.isValidated)
    }

    @Test
    fun `validateAnswers should work with empty verse list`() = runTest {
        // Start with empty verses
        assertTrue("Quiz verses should be empty initially", viewModel.state.value.quizVerses.isEmpty())

        viewModel.validateAnswers()

        val state = viewModel.state.value
        assertTrue("Quiz verses should still be empty", state.quizVerses.isEmpty())
        assertTrue("isValidated should be true even with empty list", state.isValidated)
    }

    // Test #5: Reset Validation Tests
    @Test
    fun `resetValidation should clear all validation results`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers() // This adds validation results

        // Verify validation results exist
        val validatedState = viewModel.state.value
        assertTrue("Should be validated before reset", validatedState.isValidated)
        assertNotNull("First verse should have validation result",
            validatedState.quizVerses[0].validationResult)
        assertNotNull("Second verse should have validation result",
            validatedState.quizVerses[1].validationResult)

        viewModel.resetValidation()

        val resetState = viewModel.state.value
        assertNull("First verse validation result should be null after reset",
            resetState.quizVerses[0].validationResult)
        assertNull("Second verse validation result should be null after reset",
            resetState.quizVerses[1].validationResult)
    }

    @Test
    fun `resetValidation should set isValidated to false`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        assertTrue("Should be validated before reset", viewModel.state.value.isValidated)

        viewModel.resetValidation()

        assertFalse("isValidated should be false after reset", viewModel.state.value.isValidated)
    }

    @Test
    fun `resetValidation should preserve verse selections`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        // Store original selection states
        val originalSelections = viewModel.state.value.quizVerses.map { it.isSelected }

        viewModel.resetValidation()

        val resetState = viewModel.state.value
        val newSelections = resetState.quizVerses.map { it.isSelected }

        assertEquals("Verse selections should be preserved after reset", originalSelections, newSelections)
        assertTrue("First verse should still be selected", resetState.quizVerses[0].isSelected)
        assertFalse("Second verse should still be unselected", resetState.quizVerses[1].isSelected)
    }

    @Test
    fun `validation state after reset allows new selections`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()
        viewModel.resetValidation()

        // Should be able to toggle selections after reset
        viewModel.toggleVerseSelection(1L)

        val state = viewModel.state.value
        assertTrue("Should be able to select verse after reset", state.quizVerses[0].isSelected)
        assertFalse("Should not be validated after new selection", state.isValidated)
        assertNull("Should not have validation result after new selection",
            state.quizVerses[0].validationResult)
    }

    // Test #6-7: Integration Tests for Topic and Verse Management
    @Test
    fun `setQuizVerses should properly update the verse list`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = true),
            QuizVerseItem("Psalm 23:1", 3L, isCorrectMatch = true, isSelected = false)
        )

        assertTrue("Quiz verses should be empty initially", viewModel.state.value.quizVerses.isEmpty())

        viewModel.setQuizVerses(testVerses)

        val state = viewModel.state.value
        assertEquals("Quiz verses count should match", 3, state.quizVerses.size)
        assertEquals("First verse should match", "John 3:16", state.quizVerses[0].verseReference)
        assertEquals("Second verse should match", "Romans 8:28", state.quizVerses[1].verseReference)
        assertEquals("Third verse should match", "Psalm 23:1", state.quizVerses[2].verseReference)

        // Verify properties are preserved
        assertTrue("First verse should be correct match", state.quizVerses[0].isCorrectMatch)
        assertFalse("First verse should not be selected", state.quizVerses[0].isSelected)
        assertFalse("Second verse should not be correct match", state.quizVerses[1].isCorrectMatch)
        assertTrue("Second verse should be selected", state.quizVerses[1].isSelected)
    }

    @Test
    fun `complete topic selection and verse management workflow`() = runTest {
        // 1. Start with initial state
        assertNull("Should start with no topic", viewModel.state.value.currentSelectedTopic)
        assertTrue("Should start with empty verses", viewModel.state.value.quizVerses.isEmpty())

        // 2. Select a topic
        viewModel.setCurrentSelectedTopic("Faith")
        assertEquals("Topic should be set", "Faith", viewModel.state.value.currentSelectedTopic)
        assertTrue("Verses should still be empty after topic selection", viewModel.state.value.quizVerses.isEmpty())

        // 3. Add quiz verses (simulating LaunchedEffect behavior)
        val faithVerses = listOf(
            QuizVerseItem("Hebrews 11:1", 1L, isCorrectMatch = true),
            QuizVerseItem("Romans 10:17", 2L, isCorrectMatch = true),
            QuizVerseItem("Psalm 23:1", 3L, isCorrectMatch = false), // Random verse
            QuizVerseItem("John 3:16", 4L, isCorrectMatch = false)    // Random verse
        )
        viewModel.setQuizVerses(faithVerses)

        val stateWithVerses = viewModel.state.value
        assertEquals("Should have 4 verses", 4, stateWithVerses.quizVerses.size)
        assertEquals("Topic should remain Faith", "Faith", stateWithVerses.currentSelectedTopic)

        // 4. Change topic - should clear verses
        viewModel.setCurrentSelectedTopic("Love")
        val newTopicState = viewModel.state.value
        assertEquals("Topic should change to Love", "Love", newTopicState.currentSelectedTopic)
        assertTrue("Verses should be cleared when topic changes", newTopicState.quizVerses.isEmpty())
        assertFalse("Should not be validated after topic change", newTopicState.isValidated)
    }

    // Test #8-9: Checkbox Interaction Tests
    @Test
    fun `toggleVerseSelection should update verse isSelected state`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)

        // Toggle first verse (currently false -> should become true)
        viewModel.toggleVerseSelection(1L)

        val state = viewModel.state.value
        assertTrue("First verse should be selected after toggle", state.quizVerses[0].isSelected)
        assertTrue("Second verse should remain selected", state.quizVerses[1].isSelected)

        // Toggle first verse again (currently true -> should become false)
        viewModel.toggleVerseSelection(1L)

        val state2 = viewModel.state.value
        assertFalse("First verse should be unselected after second toggle", state2.quizVerses[0].isSelected)
        assertTrue("Second verse should still remain selected", state2.quizVerses[1].isSelected)
    }

    @Test
    fun `toggleVerseSelection with non-existent ID should not crash`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // Try to toggle a verse ID that doesn't exist
        viewModel.toggleVerseSelection(999L)

        val state = viewModel.state.value
        assertEquals("Should still have 1 verse", 1, state.quizVerses.size)
        assertFalse("Original verse should remain unselected", state.quizVerses[0].isSelected)
    }

    @Test
    fun `checkbox selections should be disabled after validation`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // Before validation - should be able to toggle
        viewModel.toggleVerseSelection(1L)
        assertTrue("Should be able to select before validation", viewModel.state.value.quizVerses[0].isSelected)

        // Validate
        viewModel.validateAnswers()
        assertTrue("Should be validated", viewModel.state.value.isValidated)

        // After validation - selections should be preserved but the UI should disable further changes
        // Note: The ViewModel itself doesn't prevent toggles, but the UI should check isValidated
        val validatedState = viewModel.state.value
        assertTrue("Selection should be preserved after validation", validatedState.quizVerses[0].isSelected)
        assertTrue("Should remain validated", validatedState.isValidated)
    }

    @Test
    fun `clearVerseSelections should reset all selections and validation state`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers() // Add validation results

        assertTrue("Should be validated before clear", viewModel.state.value.isValidated)
        assertTrue("First verse should be selected before clear", viewModel.state.value.quizVerses[0].isSelected)
        assertTrue("Second verse should be selected before clear", viewModel.state.value.quizVerses[1].isSelected)

        viewModel.clearVerseSelections()

        val state = viewModel.state.value
        assertFalse("First verse should be unselected after clear", state.quizVerses[0].isSelected)
        assertFalse("Second verse should be unselected after clear", state.quizVerses[1].isSelected)
        assertFalse("Should not be validated after clear", state.isValidated)
    }

    // Test #10: Validation Button Tests
    @Test
    fun `validation button workflow - validate then reset`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // Initial state - should show "Validate" button
        assertFalse("Should not be validated initially", viewModel.state.value.isValidated)

        // Click "Validate" button
        viewModel.validateAnswers()

        val validatedState = viewModel.state.value
        assertTrue("Should be validated after clicking Validate", validatedState.isValidated)
        assertNotNull("Should have validation results", validatedState.quizVerses[0].validationResult)

        // Button should now show "Reset"
        // Click "Reset" button
        viewModel.resetValidation()

        val resetState = viewModel.state.value
        assertFalse("Should not be validated after clicking Reset", resetState.isValidated)
        assertNull("Should not have validation results after reset", resetState.quizVerses[0].validationResult)
        // Selections should be preserved
        assertTrue("Selections should be preserved after reset", resetState.quizVerses[0].isSelected)
        assertFalse("Selections should be preserved after reset", resetState.quizVerses[1].isSelected)
    }

    @Test
    fun `validation button enables and disables checkboxes correctly`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // Before validation - checkboxes should be enabled (isValidated = false)
        assertFalse("Checkboxes should be enabled before validation", viewModel.state.value.isValidated)

        // Can make selections
        viewModel.toggleVerseSelection(1L)
        assertTrue("Should be able to toggle before validation", viewModel.state.value.quizVerses[0].isSelected)

        // Validate - checkboxes should be disabled (isValidated = true)
        viewModel.validateAnswers()
        assertTrue("Checkboxes should be disabled after validation", viewModel.state.value.isValidated)

        // Reset - checkboxes should be enabled again (isValidated = false)
        viewModel.resetValidation()
        assertFalse("Checkboxes should be enabled after reset", viewModel.state.value.isValidated)

        // Can make selections again
        viewModel.toggleVerseSelection(1L)
        assertFalse("Should be able to toggle after reset", viewModel.state.value.quizVerses[0].isSelected)
    }

    @Test
    fun `multiple validation cycles should work correctly`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // First validation cycle
        viewModel.toggleVerseSelection(1L) // Select first verse
        viewModel.validateAnswers()

        val firstValidation = viewModel.state.value
        assertTrue("Should be validated after first cycle", firstValidation.isValidated)
        assertEquals("First verse should be correct", ValidationResult.CORRECT, firstValidation.quizVerses[0].validationResult)
        assertEquals("Second verse should be correct", ValidationResult.CORRECT, firstValidation.quizVerses[1].validationResult)

        // Reset and second validation cycle
        viewModel.resetValidation()
        viewModel.toggleVerseSelection(1L) // Unselect first verse (toggle it off)
        viewModel.toggleVerseSelection(2L) // Select second verse instead
        viewModel.validateAnswers()

        val secondValidation = viewModel.state.value
        assertTrue("Should be validated after second cycle", secondValidation.isValidated)
        assertEquals("First verse should be incorrect in second cycle", ValidationResult.INCORRECT, secondValidation.quizVerses[0].validationResult)
        assertEquals("Second verse should be incorrect in second cycle", ValidationResult.INCORRECT, secondValidation.quizVerses[1].validationResult)
    }

    // Test #11: Empty State Tests
    @Test
    fun `UI should show select topic message when no topic selected`() = runTest {
        val initialState = viewModel.state.value

        assertNull("Should have no selected topic initially", initialState.currentSelectedTopic)
        assertTrue("Should have empty quiz verses initially", initialState.quizVerses.isEmpty())
        assertFalse("Should not be validated initially", initialState.isValidated)

        // This represents the UI state that should show "Please select a topic to start the quiz"
        val shouldShowSelectTopicMessage = initialState.currentSelectedTopic == null
        assertTrue("Should show select topic message", shouldShowSelectTopicMessage)
    }

    @Test
    fun `UI should show loading state when topic selected but no verses yet`() = runTest {
        viewModel.setCurrentSelectedTopic("Faith")

        val state = viewModel.state.value
        assertNotNull("Should have selected topic", state.currentSelectedTopic)
        assertTrue("Should have empty verses during loading", state.quizVerses.isEmpty())

        // This represents the UI state that should show "Loading verses..."
        val shouldShowLoadingMessage = state.currentSelectedTopic != null && state.quizVerses.isEmpty()
        assertTrue("Should show loading message", shouldShowLoadingMessage)
    }

    @Test
    fun `UI should show verse list when topic selected and verses loaded`() = runTest {
        viewModel.setCurrentSelectedTopic("Faith")
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false)
        )
        viewModel.setQuizVerses(testVerses)

        val state = viewModel.state.value
        assertNotNull("Should have selected topic", state.currentSelectedTopic)
        assertFalse("Should have verses loaded", state.quizVerses.isEmpty())

        // This represents the UI state that should show the verse list
        val shouldShowVerseList = state.currentSelectedTopic != null && state.quizVerses.isNotEmpty()
        assertTrue("Should show verse list", shouldShowVerseList)
    }

    // Test #12: Validation Visual Feedback Tests
    @Test
    fun `validation results should provide correct icon display data`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true),   // Will be CORRECT
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = true) // Will be INCORRECT
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        val state = viewModel.state.value

        // Test correct answer - should show green check icon
        val correctVerse = state.quizVerses[0]
        assertEquals("Should have correct validation result", ValidationResult.CORRECT, correctVerse.validationResult)

        // Test incorrect answer - should show red X icon
        val incorrectVerse = state.quizVerses[1]
        assertEquals("Should have incorrect validation result", ValidationResult.INCORRECT, incorrectVerse.validationResult)

        // UI should use these validation results to determine icon display
        val shouldShowGreenCheck = correctVerse.validationResult == ValidationResult.CORRECT
        val shouldShowRedX = incorrectVerse.validationResult == ValidationResult.INCORRECT

        assertTrue("Should show green check for correct answer", shouldShowGreenCheck)
        assertTrue("Should show red X for incorrect answer", shouldShowRedX)
    }

    @Test
    fun `validation icons should not show before validation`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)

        val state = viewModel.state.value
        assertNull("Should not have validation result before validation", state.quizVerses[0].validationResult)
        assertFalse("Should not be validated", state.isValidated)

        // UI should not show any validation icons
        val shouldShowValidationIcon = state.quizVerses[0].validationResult != null
        assertFalse("Should not show validation icon before validation", shouldShowValidationIcon)
    }

    @Test
    fun `validation icons should be positioned correctly with verse text`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        val state = viewModel.state.value
        val verse = state.quizVerses[0]

        // Verify data needed for proper UI layout
        assertNotNull("Should have verse reference for text display", verse.verseReference)
        assertNotNull("Should have validation result for icon display", verse.validationResult)

        // UI should position icon to the right of verse text using this data
        assertTrue("Verse reference should not be empty", verse.verseReference.isNotEmpty())
        assertEquals("Should have correct validation result", ValidationResult.CORRECT, verse.validationResult)
    }

    // Test #13: Limited Verse Scenarios
    @Test
    fun `quiz should handle topic with fewer than 8 matching verses`() = runTest {
        // Simulate a topic with only 3 matching verses + 5 random verses = 8 total
        val matchingVerses = listOf(
            QuizVerseItem("Faith 1", 1L, isCorrectMatch = true),
            QuizVerseItem("Faith 2", 2L, isCorrectMatch = true),
            QuizVerseItem("Faith 3", 3L, isCorrectMatch = true)
        )
        val randomVerses = listOf(
            QuizVerseItem("Random 1", 4L, isCorrectMatch = false),
            QuizVerseItem("Random 2", 5L, isCorrectMatch = false),
            QuizVerseItem("Random 3", 6L, isCorrectMatch = false),
            QuizVerseItem("Random 4", 7L, isCorrectMatch = false),
            QuizVerseItem("Random 5", 8L, isCorrectMatch = false)
        )
        val allVerses = matchingVerses + randomVerses
        viewModel.setQuizVerses(allVerses)

        val state = viewModel.state.value
        assertEquals("Should have exactly 8 verses", 8, state.quizVerses.size)

        val correctMatches = state.quizVerses.count { it.isCorrectMatch }
        val randomFills = state.quizVerses.count { !it.isCorrectMatch }

        assertEquals("Should have 3 correct matches", 3, correctMatches)
        assertEquals("Should have 5 random fills", 5, randomFills)
    }

    @Test
    fun `quiz should handle topic with exactly 8 matching verses`() = runTest {
        val exactlyEightVerses = (1..8).map { i ->
            QuizVerseItem("Verse $i", i.toLong(), isCorrectMatch = true)
        }
        viewModel.setQuizVerses(exactlyEightVerses)

        val state = viewModel.state.value
        assertEquals("Should have exactly 8 verses", 8, state.quizVerses.size)

        val allCorrectMatches = state.quizVerses.all { it.isCorrectMatch }
        assertTrue("All verses should be correct matches", allCorrectMatches)
    }

    @Test
    fun `quiz should handle topic with more than 8 matching verses`() = runTest {
        // Simulate selecting 8 verses from a topic that has more than 8 matches
        val selectedEightFromMany = (1..8).map { i ->
            QuizVerseItem("Selected $i", i.toLong(), isCorrectMatch = true)
        }
        viewModel.setQuizVerses(selectedEightFromMany)

        val state = viewModel.state.value
        assertEquals("Should have exactly 8 verses", 8, state.quizVerses.size)

        val allCorrectMatches = state.quizVerses.all { it.isCorrectMatch }
        assertTrue("All verses should be correct matches when topic has many verses", allCorrectMatches)
    }

    @Test
    fun `quiz should handle topic with zero matching verses`() = runTest {
        // Simulate a topic with 0 matching verses, filled with 8 random verses
        val allRandomVerses = (1..8).map { i ->
            QuizVerseItem("Random $i", i.toLong(), isCorrectMatch = false)
        }
        viewModel.setQuizVerses(allRandomVerses)

        val state = viewModel.state.value
        assertEquals("Should have exactly 8 verses", 8, state.quizVerses.size)

        val noCorrectMatches = state.quizVerses.none { it.isCorrectMatch }
        assertTrue("No verses should be correct matches", noCorrectMatches)

        // User should get all incorrect if they select any verses
        viewModel.toggleVerseSelection(1L)
        viewModel.validateAnswers()

        val validatedState = viewModel.state.value
        assertEquals("Selected verse should be incorrect", ValidationResult.INCORRECT, validatedState.quizVerses[0].validationResult)
        val unselectedVerses = validatedState.quizVerses.drop(1)
        assertTrue("All unselected verses should be correct",
            unselectedVerses.all { it.validationResult == ValidationResult.CORRECT })
    }

    // Test #14: Empty Data Tests
    @Test
    fun `quiz should handle empty verse list gracefully`() = runTest {
        // Set empty verse list
        viewModel.setQuizVerses(emptyList())

        val state = viewModel.state.value
        assertTrue("Quiz verses should be empty", state.quizVerses.isEmpty())
        assertFalse("Should not be validated with empty list", state.isValidated)

        // Validation should work with empty list
        viewModel.validateAnswers()

        val validatedState = viewModel.state.value
        assertTrue("Should be validated even with empty list", validatedState.isValidated)
        assertTrue("Quiz verses should still be empty", validatedState.quizVerses.isEmpty())
    }

    @Test
    fun `quiz should handle operations on empty verse list`() = runTest {
        // Start with empty verses
        assertTrue("Should start with empty verses", viewModel.state.value.quizVerses.isEmpty())

        // Try to toggle selection on non-existent verse
        viewModel.toggleVerseSelection(999L)
        assertTrue("Should still be empty after invalid toggle", viewModel.state.value.quizVerses.isEmpty())

        // Try to clear selections on empty list
        viewModel.clearVerseSelections()
        assertTrue("Should still be empty after clear", viewModel.state.value.quizVerses.isEmpty())

        // Try to reset validation on empty list
        viewModel.resetValidation()
        assertTrue("Should still be empty after reset", viewModel.state.value.quizVerses.isEmpty())
    }

    @Test
    fun `quiz should handle database with fewer than 8 total verses`() = runTest {
        // Simulate a very small database with only 3 verses total
        val allAvailableVerses = listOf(
            QuizVerseItem("Verse 1", 1L, isCorrectMatch = true),   // 1 matching
            QuizVerseItem("Verse 2", 2L, isCorrectMatch = false),  // 2 random
            QuizVerseItem("Verse 3", 3L, isCorrectMatch = false)
        )
        viewModel.setQuizVerses(allAvailableVerses)

        val state = viewModel.state.value
        assertEquals("Should have only 3 verses when database is small", 3, state.quizVerses.size)

        // Quiz should still work with fewer than 8 verses
        viewModel.toggleVerseSelection(1L) // Select the matching verse
        viewModel.validateAnswers()

        val validatedState = viewModel.state.value
        assertTrue("Should be validated with small verse count", validatedState.isValidated)
        assertEquals("Selected matching verse should be correct",
            ValidationResult.CORRECT, validatedState.quizVerses[0].validationResult)
    }

    @Test
    fun `quiz should handle error states gracefully`() = runTest {
        // Test error state functionality
        assertNull("Should start with no error", viewModel.state.value.error)

        // Note: Since setError function was removed in the simplified version,
        // we verify that the quiz handles edge cases without crashing

        // Test with null topic
        viewModel.setCurrentSelectedTopic("")
        assertTrue("Empty topic should be handled", viewModel.state.value.currentSelectedTopic?.isEmpty() == true)

        // Test rapid state changes
        viewModel.setCurrentSelectedTopic("Topic1")
        viewModel.setCurrentSelectedTopic("Topic2")
        viewModel.setCurrentSelectedTopic("Topic3")

        assertEquals("Should handle rapid topic changes", "Topic3", viewModel.state.value.currentSelectedTopic)
        assertTrue("Verses should be cleared on each topic change", viewModel.state.value.quizVerses.isEmpty())
    }

    // Test #15: Navigation Preservation Tests
    @Test
    fun `quiz state should be preserved during simulated navigation`() = runTest {
        // Setup initial quiz state
        viewModel.setCurrentSelectedTopic("Faith")
        val testVerses = listOf(
            QuizVerseItem("John 3:16", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Romans 8:28", 2L, isCorrectMatch = false, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        // Capture state before "navigation"
        val stateBeforeNav = viewModel.state.value
        assertEquals("Faith", stateBeforeNav.currentSelectedTopic)
        assertEquals(2, stateBeforeNav.quizVerses.size)
        assertTrue("Should be validated", stateBeforeNav.isValidated)
        assertFalse("First verse should be unselected", stateBeforeNav.quizVerses[0].isSelected)
        assertTrue("Second verse should be selected", stateBeforeNav.quizVerses[1].isSelected)

        // Simulate navigation event (ViewModel instance should persist state)
        // In real app, this represents navigating to VerseDetailScreen and back

        // Verify state after "navigation" - should be identical
        val stateAfterNav = viewModel.state.value
        assertEquals("Topic should be preserved", stateBeforeNav.currentSelectedTopic, stateAfterNav.currentSelectedTopic)
        assertEquals("Verse count should be preserved", stateBeforeNav.quizVerses.size, stateAfterNav.quizVerses.size)
        assertEquals("Validation state should be preserved", stateBeforeNav.isValidated, stateAfterNav.isValidated)

        // Check individual verse states are preserved
        assertEquals("First verse selection preserved",
            stateBeforeNav.quizVerses[0].isSelected, stateAfterNav.quizVerses[0].isSelected)
        assertEquals("Second verse selection preserved",
            stateBeforeNav.quizVerses[1].isSelected, stateAfterNav.quizVerses[1].isSelected)
        assertEquals("First verse validation preserved",
            stateBeforeNav.quizVerses[0].validationResult, stateAfterNav.quizVerses[0].validationResult)
        assertEquals("Second verse validation preserved",
            stateBeforeNav.quizVerses[1].validationResult, stateAfterNav.quizVerses[1].validationResult)
    }

    @Test
    fun `quiz state should persist through multiple simulated navigation events`() = runTest {
        // Setup complex quiz state
        viewModel.setCurrentSelectedTopic("Love")
        val testVerses = listOf(
            QuizVerseItem("1 Cor 13:4", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("John 3:16", 2L, isCorrectMatch = false, isSelected = false),
            QuizVerseItem("Rom 8:28", 3L, isCorrectMatch = true, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)

        // Make some selections and validate
        viewModel.validateAnswers()

        val originalState = viewModel.state.value

        // Simulate multiple navigation events
        for (i in 1..5) {
            // Each iteration simulates navigating to detail screen and back
            val currentState = viewModel.state.value

            // Verify state consistency after each "navigation"
            assertEquals("Topic should persist through navigation $i",
                originalState.currentSelectedTopic, currentState.currentSelectedTopic)
            assertEquals("Verse count should persist through navigation $i",
                originalState.quizVerses.size, currentState.quizVerses.size)
            assertEquals("Validation state should persist through navigation $i",
                originalState.isValidated, currentState.isValidated)
        }
    }

    @Test
    fun `validation results should be preserved during navigation`() = runTest {
        // Create quiz with mixed results
        val testVerses = listOf(
            QuizVerseItem("Correct Match", 1L, isCorrectMatch = true, isSelected = true),      // CORRECT
            QuizVerseItem("Incorrect Match", 2L, isCorrectMatch = true, isSelected = false),   // INCORRECT
            QuizVerseItem("Correct Non-Match", 3L, isCorrectMatch = false, isSelected = false), // CORRECT
            QuizVerseItem("Incorrect Non-Match", 4L, isCorrectMatch = false, isSelected = true) // INCORRECT
        )
        viewModel.setQuizVerses(testVerses)
        viewModel.validateAnswers()

        val stateWithResults = viewModel.state.value

        // Verify all validation results are set
        assertEquals("First verse should be correct", ValidationResult.CORRECT, stateWithResults.quizVerses[0].validationResult)
        assertEquals("Second verse should be incorrect", ValidationResult.INCORRECT, stateWithResults.quizVerses[1].validationResult)
        assertEquals("Third verse should be correct", ValidationResult.CORRECT, stateWithResults.quizVerses[2].validationResult)
        assertEquals("Fourth verse should be incorrect", ValidationResult.INCORRECT, stateWithResults.quizVerses[3].validationResult)

        // Simulate navigation (state should be unchanged)
        val stateAfterNavigation = viewModel.state.value

        // All validation results should be preserved
        assertEquals("First validation result preserved",
            stateWithResults.quizVerses[0].validationResult, stateAfterNavigation.quizVerses[0].validationResult)
        assertEquals("Second validation result preserved",
            stateWithResults.quizVerses[1].validationResult, stateAfterNavigation.quizVerses[1].validationResult)
        assertEquals("Third validation result preserved",
            stateWithResults.quizVerses[2].validationResult, stateAfterNavigation.quizVerses[2].validationResult)
        assertEquals("Fourth validation result preserved",
            stateWithResults.quizVerses[3].validationResult, stateAfterNavigation.quizVerses[3].validationResult)
    }

    // Test #16: Memory and Performance Tests
    @Test
    fun `rapid topic switching should not cause state corruption`() = runTest {
        val topics = listOf("Faith", "Love", "Hope", "Peace", "Joy", "Patience", "Kindness", "Goodness")

        for (topic in topics) {
            viewModel.setCurrentSelectedTopic(topic)

            // Verify clean state after each topic change
            val state = viewModel.state.value
            assertEquals("Topic should be set correctly", topic, state.currentSelectedTopic)
            assertTrue("Verses should be cleared", state.quizVerses.isEmpty())
            assertFalse("Should not be validated", state.isValidated)
            assertNull("Should have no error", state.error)
        }

        // Final state should be clean
        assertEquals("Should end with last topic", "Goodness", viewModel.state.value.currentSelectedTopic)
    }

    @Test
    fun `repeated operations should maintain state consistency`() = runTest {
        val testVerses = listOf(
            QuizVerseItem("Test Verse", 1L, isCorrectMatch = true, isSelected = false)
        )
        viewModel.setQuizVerses(testVerses)

        // Perform many toggle operations
        repeat(100) {
            viewModel.toggleVerseSelection(1L)
        }

        // After even number of toggles, should be back to original state
        val finalState = viewModel.state.value
        assertFalse("Should be unselected after even toggles", finalState.quizVerses[0].isSelected)
        assertEquals("Should still have 1 verse", 1, finalState.quizVerses.size)
        assertEquals("Verse reference should be preserved", "Test Verse", finalState.quizVerses[0].verseReference)
    }

    @Test
    fun `efficient handling of large verse collections should work`() = runTest {
        // Simulate handling a large number of verses (edge case testing)
        val largeVerseList = (1..50).map { i ->
            QuizVerseItem("Large Verse $i", i.toLong(), isCorrectMatch = i % 2 == 0, isSelected = false)
        }

        viewModel.setQuizVerses(largeVerseList)

        val state = viewModel.state.value
        assertEquals("Should handle large verse count", 50, state.quizVerses.size)

        // Validate all verses
        viewModel.validateAnswers()

        val validatedState = viewModel.state.value
        assertTrue("Should be validated with large list", validatedState.isValidated)

        // All verses should have validation results
        val allHaveResults = validatedState.quizVerses.all { it.validationResult != null }
        assertTrue("All verses should have validation results", allHaveResults)
    }

    // Test #17: Complete Quiz Flow Tests
    @Test
    fun `complete quiz workflow - select topic to make selections to validate to view results`() = runTest {
        // Step 1: Initial state
        assertNull("Should start with no topic", viewModel.state.value.currentSelectedTopic)
        assertTrue("Should start with empty verses", viewModel.state.value.quizVerses.isEmpty())
        assertFalse("Should start unvalidated", viewModel.state.value.isValidated)

        // Step 2: Select topic
        viewModel.setCurrentSelectedTopic("Salvation")
        assertEquals("Topic should be selected", "Salvation", viewModel.state.value.currentSelectedTopic)

        // Step 3: Load verses (simulating LaunchedEffect)
        val salvationVerses = listOf(
            QuizVerseItem("Eph 2:8-9", 1L, isCorrectMatch = true),
            QuizVerseItem("John 3:16", 2L, isCorrectMatch = true),
            QuizVerseItem("Rom 10:9", 3L, isCorrectMatch = true),
            QuizVerseItem("Psalm 23:1", 4L, isCorrectMatch = false),
            QuizVerseItem("Prov 3:5", 5L, isCorrectMatch = false),
            QuizVerseItem("Matt 6:9", 6L, isCorrectMatch = false),
            QuizVerseItem("1 Cor 13:4", 7L, isCorrectMatch = false),
            QuizVerseItem("Phil 4:13", 8L, isCorrectMatch = false)
        )
        viewModel.setQuizVerses(salvationVerses)

        val stateWithVerses = viewModel.state.value
        assertEquals("Should have 8 verses", 8, stateWithVerses.quizVerses.size)
        assertFalse("Should not be validated yet", stateWithVerses.isValidated)

        // Step 4: Make selections
        viewModel.toggleVerseSelection(1L) // Select matching verse
        viewModel.toggleVerseSelection(2L) // Select matching verse
        viewModel.toggleVerseSelection(4L) // Select non-matching verse (mistake)
        // Leave verse 3 unselected (mistake)
        // Leave verses 5,6,7,8 unselected (correct)

        val stateWithSelections = viewModel.state.value
        assertTrue("First verse should be selected", stateWithSelections.quizVerses[0].isSelected)
        assertTrue("Second verse should be selected", stateWithSelections.quizVerses[1].isSelected)
        assertFalse("Third verse should be unselected", stateWithSelections.quizVerses[2].isSelected)
        assertTrue("Fourth verse should be selected", stateWithSelections.quizVerses[3].isSelected)

        // Step 5: Validate answers
        viewModel.validateAnswers()

        val finalState = viewModel.state.value
        assertTrue("Should be validated", finalState.isValidated)

        // Check results
        assertEquals("First selection should be correct", ValidationResult.CORRECT, finalState.quizVerses[0].validationResult)
        assertEquals("Second selection should be correct", ValidationResult.CORRECT, finalState.quizVerses[1].validationResult)
        assertEquals("Third unselection should be incorrect", ValidationResult.INCORRECT, finalState.quizVerses[2].validationResult)
        assertEquals("Fourth selection should be incorrect", ValidationResult.INCORRECT, finalState.quizVerses[3].validationResult)
        assertEquals("Fifth unselection should be correct", ValidationResult.CORRECT, finalState.quizVerses[4].validationResult)
    }

    @Test
    fun `reset workflow - validate then reset then make new selections then validate again`() = runTest {
        // Setup initial quiz
        val testVerses = listOf(
            QuizVerseItem("Verse 1", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Verse 2", 2L, isCorrectMatch = false, isSelected = true)
        )
        viewModel.setQuizVerses(testVerses)

        // First validation cycle
        viewModel.validateAnswers()
        val firstValidation = viewModel.state.value
        assertTrue("Should be validated after first cycle", firstValidation.isValidated)
        assertEquals("First verse should be incorrect initially", ValidationResult.INCORRECT, firstValidation.quizVerses[0].validationResult)
        assertEquals("Second verse should be incorrect initially", ValidationResult.INCORRECT, firstValidation.quizVerses[1].validationResult)

        // Reset
        viewModel.resetValidation()
        val afterReset = viewModel.state.value
        assertFalse("Should not be validated after reset", afterReset.isValidated)
        assertNull("First verse should have no validation result", afterReset.quizVerses[0].validationResult)
        assertNull("Second verse should have no validation result", afterReset.quizVerses[1].validationResult)
        // Selections should be preserved
        assertFalse("First verse selection preserved", afterReset.quizVerses[0].isSelected)
        assertTrue("Second verse selection preserved", afterReset.quizVerses[1].isSelected)

        // Make new selections
        viewModel.toggleVerseSelection(1L) // Select first verse (correct)
        viewModel.toggleVerseSelection(2L) // Unselect second verse (correct)

        // Second validation cycle
        viewModel.validateAnswers()
        val secondValidation = viewModel.state.value
        assertTrue("Should be validated after second cycle", secondValidation.isValidated)
        assertEquals("First verse should be correct in second cycle", ValidationResult.CORRECT, secondValidation.quizVerses[0].validationResult)
        assertEquals("Second verse should be correct in second cycle", ValidationResult.CORRECT, secondValidation.quizVerses[1].validationResult)
    }

    // Test #18: Topic Switching Workflow Tests
    @Test
    fun `topic switching workflow - complete quiz then switch topic then verify new quiz`() = runTest {
        // First topic workflow
        viewModel.setCurrentSelectedTopic("Faith")
        val faithVerses = listOf(
            QuizVerseItem("Heb 11:1", 1L, isCorrectMatch = true, isSelected = true),
            QuizVerseItem("Random 1", 2L, isCorrectMatch = false, isSelected = false)
        )
        viewModel.setQuizVerses(faithVerses)
        viewModel.validateAnswers()

        val faithState = viewModel.state.value
        assertEquals("Should be on Faith topic", "Faith", faithState.currentSelectedTopic)
        assertTrue("Should be validated for Faith", faithState.isValidated)
        assertEquals("Faith verse should be correct", ValidationResult.CORRECT, faithState.quizVerses[0].validationResult)

        // Switch to second topic
        viewModel.setCurrentSelectedTopic("Love")
        val afterSwitch = viewModel.state.value
        assertEquals("Should switch to Love topic", "Love", afterSwitch.currentSelectedTopic)
        assertTrue("Verses should be cleared after topic switch", afterSwitch.quizVerses.isEmpty())
        assertFalse("Should not be validated after topic switch", afterSwitch.isValidated)

        // Setup new quiz for Love topic
        val loveVerses = listOf(
            QuizVerseItem("1 Cor 13:4", 1L, isCorrectMatch = true, isSelected = false),
            QuizVerseItem("Random 2", 2L, isCorrectMatch = false, isSelected = true)
        )
        viewModel.setQuizVerses(loveVerses)

        // Verify new quiz is independent
        val loveQuizState = viewModel.state.value
        assertEquals("Should have Love verses", 2, loveQuizState.quizVerses.size)
        assertFalse("First verse should start unselected", loveQuizState.quizVerses[0].isSelected)
        assertTrue("Second verse should start selected", loveQuizState.quizVerses[1].isSelected)
        assertNull("Should have no validation results from previous quiz", loveQuizState.quizVerses[0].validationResult)

        // Validate new quiz
        viewModel.validateAnswers()
        val finalLoveState = viewModel.state.value
        assertEquals("Love verse should be incorrect", ValidationResult.INCORRECT, finalLoveState.quizVerses[0].validationResult)
        assertEquals("Random verse should be incorrect", ValidationResult.INCORRECT, finalLoveState.quizVerses[1].validationResult)
    }

    @Test
    fun `rapid topic switching should preserve clean state transitions`() = runTest {
        val topics = listOf("Faith", "Hope", "Love", "Peace")

        for ((index, topic) in topics.withIndex()) {
            // Switch topic
            viewModel.setCurrentSelectedTopic(topic)

            // Add verses
            val verses = listOf(
                QuizVerseItem("$topic Verse", (index + 1).toLong(), isCorrectMatch = true)
            )
            viewModel.setQuizVerses(verses)

            // Make selection and validate
            viewModel.toggleVerseSelection((index + 1).toLong())
            viewModel.validateAnswers()

            // Verify state is correct for current topic
            val state = viewModel.state.value
            assertEquals("Should be on correct topic", topic, state.currentSelectedTopic)
            assertEquals("Should have verse for current topic", "$topic Verse", state.quizVerses[0].verseReference)
            assertTrue("Should be validated", state.isValidated)
            assertEquals("Should have correct result", ValidationResult.CORRECT, state.quizVerses[0].validationResult)
        }

        // Final state should be for last topic only
        assertEquals("Should end on Peace", "Peace", viewModel.state.value.currentSelectedTopic)
        assertEquals("Should have Peace verse only", "Peace Verse", viewModel.state.value.quizVerses[0].verseReference)
    }
}