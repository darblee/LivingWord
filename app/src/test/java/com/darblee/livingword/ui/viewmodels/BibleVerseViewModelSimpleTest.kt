package com.darblee.livingword.ui.viewmodels

import android.content.Context
import android.os.Looper
import android.util.Log
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseRepository
import com.darblee.livingword.data.DatabaseRefreshManager
import com.darblee.livingword.data.Verse
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BibleVerseViewModelSimpleTest {

    private lateinit var repository: BibleVerseRepository
    private lateinit var context: Context
    private lateinit var viewModel: BibleVerseViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val refreshTrigger = MutableSharedFlow<Unit>()

    private val sampleVerse = BibleVerse(
        id = 1L,
        book = "John",
        chapter = 3,
        startVerse = 16,
        endVerse = 16,
        translation = "ESV",
        scriptureVerses = listOf(Verse(16, "For God so loved the world...")),
        aiTakeAwayResponse = "Test takeaway",
        topics = listOf("Faith", "Love"),
        favorite = false,
        userDirectQuote = "",
        userContext = "",
        userContextScore = 0,
        aiContextExplanationText = "",
        applicationFeedback = "",
        lastModified = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        // Mock ALL Android static classes FIRST before any coroutine setup
        mockkStatic("android.util.Log")
        mockkStatic("android.os.Looper")

        // Explicit Looper mocking - critical for Main dispatcher
        every { Looper.getMainLooper() } returns mockk(relaxed = true)


        // Set main dispatcher AFTER Looper is mocked
        Dispatchers.setMain(testDispatcher)

        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { repository.getAllVerses() } returns flowOf(emptyList())
        every { repository.getAllTopics() } returns flowOf(emptyList())
        every { repository.getAllFavoriteVerses() } returns flowOf(emptyList())
        coEvery { repository.addDefaultTopicsIfEmpty() } returns Unit

        mockkObject(DatabaseRefreshManager)
        every { DatabaseRefreshManager.refreshTrigger } returns refreshTrigger

        // All Log methods with comprehensive mocking
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.d(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.i(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.v(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0

        viewModel = BibleVerseViewModel(repository, context)
    }

    @After
    fun tearDown() {
        // Cancel any remaining coroutines and wait for completion
        testDispatcher.scheduler.runCurrent()


        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `initial state should have correct default values`() = runTest {
        assertEquals("allVerses should be empty initially", emptyList<BibleVerse>(), viewModel.allVerses.value)
        assertNull("errorMessage should be null initially", viewModel.errorMessage.value)
        assertEquals("translationLoadingState should be Idle initially", TranslationLoadingState.Idle, viewModel.translationLoadingState.value)
    }

    @Test
    fun `error message state updates correctly`() = runTest {
        viewModel.postUserMessage("Test error message")
        assertEquals("Error message should be set", "Test error message", viewModel.errorMessage.value)

        viewModel.clearErrorMessage()
        assertNull("Error message should be cleared", viewModel.errorMessage.value)
    }

    @Test
    fun `getVerseFlow returns correct Flow from repository`() = runTest {
        every { repository.getVerseFlow(1L) } returns flowOf(sampleVerse)

        val verseFlow = viewModel.getVerseFlow(1L)
        verseFlow.collect { verse ->
            assertEquals("Should return verse from repository", sampleVerse, verse)
        }

        verify { repository.getVerseFlow(1L) }
    }
}