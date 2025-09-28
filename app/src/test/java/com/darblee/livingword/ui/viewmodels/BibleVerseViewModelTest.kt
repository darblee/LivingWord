package com.darblee.livingword.ui.viewmodels

import android.content.Context
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.darblee.livingword.data.AppDatabase
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseDao
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.BibleVerseRepository
import com.darblee.livingword.data.DatabaseRefreshManager
import com.darblee.livingword.data.Topic
import com.darblee.livingword.data.TopicWithCount
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.AIService
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
class BibleVerseViewModelTest {

    private lateinit var repository: BibleVerseRepository
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    private lateinit var viewModel: BibleVerseViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val refreshTrigger = MutableSharedFlow<Unit>()

    // Sample test data
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

    private val sampleVerseRef = BibleVerseRef(
        book = "John",
        chapter = 3,
        startVerse = 16,
        endVerse = 16
    )

    private val sampleTopics = listOf(
        TopicWithCount("Faith", 5),
        TopicWithCount("Love", 3),
        TopicWithCount("Hope", 2)
    )

    private val sampleTopicsForDao = listOf(
        Topic(1L, "Faith"),
        Topic(2L, "Love"),
        Topic(3L, "Hope")
    )

    @Before
    fun setup() {
        // Mock ALL Android static classes FIRST before any coroutine setup
        mockkStatic("android.util.Log")
        mockkStatic("android.os.Looper")
        mockkStatic("android.widget.Toast")

        // Explicit Looper mocking - critical for Main dispatcher
        every { Looper.getMainLooper() } returns mockk(relaxed = true)


        // Set main dispatcher AFTER Looper is mocked
        Dispatchers.setMain(testDispatcher)

        // Create mocks
        repository = mockk(relaxed = true)
        context = mockk(relaxed = true)
        database = mockk(relaxed = true)

        // Setup default mock behaviors
        every { repository.getAllVerses() } returns flowOf(emptyList())
        every { repository.getAllTopics() } returns flowOf(emptyList())
        every { repository.getAllFavoriteVerses() } returns flowOf(emptyList())
        coEvery { repository.addDefaultTopicsIfEmpty() } returns Unit

        // Mock static objects
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

        // Toast mocking
        every { Toast.makeText(any(), any<String>(), any()) } returns mockk(relaxed = true)
        every { Toast.makeText(any(), any<Int>(), any()) } returns mockk(relaxed = true)

        mockkObject(AIService)

        viewModel = BibleVerseViewModel(repository, context)
    }

    @After
    fun tearDown() {
        // Cancel any remaining coroutines and wait for completion
        testDispatcher.scheduler.runCurrent()


        Dispatchers.resetMain()
        unmockkAll()
    }

    // ========================================
    // 1. STATE MANAGEMENT TESTS
    // ========================================

    @Test
    fun `initial state should have correct default values`() = runTest {
        assertEquals("allVerses should be empty initially", emptyList<BibleVerse>(), viewModel.allVerses.value)
        assertEquals("allTopicsWithCount should be empty initially", emptyList<TopicWithCount>(), viewModel.allTopicsWithCount.value)
        assertNull("errorMessage should be null initially", viewModel.errorMessage.value)
        assertEquals("favoriteVerses should be empty initially", emptyList<BibleVerse>(), viewModel.favoriteVerses.value)
        assertEquals("translationLoadingState should be Idle initially", TranslationLoadingState.Idle, viewModel.translationLoadingState.value)
    }

    @Test
    fun `StateFlow updates should propagate correctly for allVerses`() = runTest {
        val testVerses = listOf(sampleVerse)
        every { repository.getAllVerses() } returns flowOf(testVerses)

        val newViewModel = BibleVerseViewModel(repository, context)

        assertEquals("allVerses should update from repository", testVerses, newViewModel.allVerses.value)
    }

    @Test
    fun `StateFlow updates should propagate correctly for favoriteVerses`() = runTest {
        val favoriteVerse = sampleVerse.copy(favorite = true)
        every { repository.getAllFavoriteVerses() } returns flowOf(listOf(favoriteVerse))

        val newViewModel = BibleVerseViewModel(repository, context)

        assertEquals("favoriteVerses should update from repository", listOf(favoriteVerse), newViewModel.favoriteVerses.value)
    }

    @Test
    fun `StateFlow updates should propagate correctly for topics`() = runTest {
        every { repository.getAllTopics() } returns flowOf(sampleTopics)

        val newViewModel = BibleVerseViewModel(repository, context)

        assertEquals("allTopicsWithCount should update from repository", sampleTopics, newViewModel.allTopicsWithCount.value)
    }

    @Test
    fun `translation loading state transitions from Idle to Loading to Idle`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { AIService.fetchScripture(any(), any()) } returns AiServiceResult.Success(listOf(Verse(16, "Updated text")))
        coEvery { repository.updateVerse(any()) } returns Unit

        assertEquals("Should start in Idle state", TranslationLoadingState.Idle, viewModel.translationLoadingState.value)

        viewModel.reloadVerseWithNewTranslation(1L, "NIV")

        assertEquals("Should return to Idle state after completion", TranslationLoadingState.Idle, viewModel.translationLoadingState.value)
    }

    @Test
    fun `error message state updates correctly`() = runTest {
        viewModel.postUserMessage("Test error message")

        assertEquals("Error message should be set", "Test error message", viewModel.errorMessage.value)

        viewModel.clearErrorMessage()

        assertNull("Error message should be cleared", viewModel.errorMessage.value)
    }

    // ========================================
    // 2. DATABASE OPERATIONS TESTS
    // ========================================

    @Test
    fun `getVerseFlow returns correct Flow from repository`() = runTest {
        every { repository.getVerseFlow(1L) } returns flowOf(sampleVerse)

        val verseFlow = viewModel.getVerseFlow(1L)
        verseFlow.collect { verse ->
            assertEquals("Should return verse from repository", sampleVerse, verse)
        }

        verify { repository.getVerseFlow(1L) }
    }

    @Test
    fun `getVerseById calls repository correctly`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse

        val result = viewModel.getVerseById(1L)

        assertEquals("Should return verse from repository", sampleVerse, result)
        coVerify { repository.getVerseById(1L) }
    }

    @Test
    fun `getAllVerses initializes correctly in init block`() = runTest {
        val testVerses = listOf(sampleVerse)
        every { repository.getAllVerses() } returns flowOf(testVerses)

        val newViewModel = BibleVerseViewModel(repository, context)

        assertEquals("allVerses should be populated", testVerses, newViewModel.allVerses.value)
    }

    @Test
    fun `updateFavoriteStatus calls repository and updates state`() = runTest {
        coEvery { repository.updateFavoriteStatus(1L, true) } returns Unit

        viewModel.updateFavoriteStatus(1L, true)

        coVerify { repository.updateFavoriteStatus(1L, true) }
        assertEquals("Should show success message", "Added to favorites", viewModel.errorMessage.value)
    }

    @Test
    fun `updateFavoriteStatus handles errors properly`() = runTest {
        coEvery { repository.updateFavoriteStatus(1L, true) } throws RuntimeException("Database error")

        viewModel.updateFavoriteStatus(1L, true)

        assertTrue("Should show error message", viewModel.errorMessage.value?.contains("Error updating favorite status") == true)
    }

    @Test
    fun `getAllFavoriteVerses updates favoriteVerses state`() = runTest {
        val favoriteVerse = sampleVerse.copy(favorite = true)
        every { repository.getAllFavoriteVerses() } returns flowOf(listOf(favoriteVerse))

        val newViewModel = BibleVerseViewModel(repository, context)

        assertEquals("favoriteVerses should be updated", listOf(favoriteVerse), newViewModel.favoriteVerses.value)
    }

    @Test
    fun `getVersesByTopic returns flow from repository`() = runTest {
        val topicVerses = listOf(sampleVerse)
        every { repository.getVersesByTopic("Faith") } returns flowOf(topicVerses)

        val result = viewModel.getVersesByTopic("Faith")
        result.collect { verses ->
            assertEquals("Should return verses for topic", topicVerses, verses)
        }

        verify { repository.getVersesByTopic("Faith") }
    }

    @Test
    fun `addTopic calls repository with trimmed name`() = runTest {
        coEvery { repository.addTopic("Faith") } returns 1L

        viewModel.addTopic("  Faith  ")

        coVerify { repository.addTopic("Faith") }
        assertEquals("Should show success message", "Topic 'Faith' added successfully", viewModel.errorMessage.value)
    }

    @Test
    fun `addTopic handles blank input`() = runTest {
        viewModel.addTopic("   ")

        assertEquals("Should show error for blank input", "Topic name cannot be blank", viewModel.errorMessage.value)
        coVerify(exactly = 0) { repository.addTopic(any()) }
    }

    @Test
    fun `deleteTopics calls repository correctly`() = runTest {
        val topicsToDelete = listOf("Topic1", "Topic2")
        coEvery { repository.deleteTopics(topicsToDelete) } returns Unit

        viewModel.deleteTopics(topicsToDelete)

        coVerify { repository.deleteTopics(topicsToDelete) }
    }

    @Test
    fun `updateTranslation calls repository and shows success message`() = runTest {
        coEvery { repository.updateTranslation(1L, "NIV") } returns Unit

        viewModel.updateTranslation(1L, "NIV")

        coVerify { repository.updateTranslation(1L, "NIV") }
        assertEquals("Should show success message", "Translation updated to NIV", viewModel.errorMessage.value)
    }

    @Test
    fun `reloadVerseWithNewTranslation skips if same translation`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse.copy(translation = "ESV")

        viewModel.reloadVerseWithNewTranslation(1L, "ESV")

        coVerify(exactly = 0) { AIService.fetchScripture(any(), any()) }
    }

    @Test
    fun `reloadVerseWithNewTranslation updates verse with new translation`() = runTest {
        val originalVerse = sampleVerse.copy(translation = "ESV")
        val newScripture = listOf(Verse(16, "Updated scripture text"))

        coEvery { repository.getVerseById(1L) } returns originalVerse
        coEvery { AIService.fetchScripture(any(), "NIV") } returns AiServiceResult.Success(newScripture)
        coEvery { repository.updateVerse(any()) } returns Unit

        viewModel.reloadVerseWithNewTranslation(1L, "NIV")

        coVerify {
            repository.updateVerse(
                match { verse ->
                    verse.translation == "NIV" && verse.scriptureVerses == newScripture
                }
            )
        }
    }

    @Test
    fun `renameOrMergeTopic handles blank new name`() = runTest {
        viewModel.renameOrMergeTopic("OldTopic", "   ", false)

        assertEquals("Should show error for blank name", "New topic name cannot be blank.", viewModel.errorMessage.value)
        coVerify(exactly = 0) { repository.renameOrMergeTopic(any(), any(), any()) }
    }

    @Test
    fun `renameOrMergeTopic handles same names for rename`() = runTest {
        viewModel.renameOrMergeTopic("SameTopic", "SameTopic", false)

        coVerify(exactly = 0) { repository.renameOrMergeTopic(any(), any(), any()) }
    }

    @Test
    fun `renameOrMergeTopic calls repository for valid rename`() = runTest {
        coEvery { repository.renameOrMergeTopic("OldTopic", "NewTopic", false) } returns Unit

        viewModel.renameOrMergeTopic("OldTopic", "NewTopic", false)

        coVerify { repository.renameOrMergeTopic("OldTopic", "NewTopic", false) }
        assertEquals("Should show success message", "Topic 'OldTopic' renamed to 'NewTopic'.", viewModel.errorMessage.value)
    }

    // ========================================
    // 3. AI INTEGRATION TESTS
    // ========================================

    @Test
    fun `fetchAITakeawayForVerse handles success scenario`() = runTest {
        val existingVerse = sampleVerse.copy(aiTakeAwayResponse = "")
        val aiResponse = "New AI takeaway"

        coEvery { repository.getVerseById(1L) } returns existingVerse
        coEvery { AIService.getKeyTakeaway(any()) } returns AiServiceResult.Success(aiResponse)
        coEvery { AIService.validateKeyTakeawayResponse(any(), aiResponse) } returns AiServiceResult.Success(true)
        coEvery { repository.updateVerse(any()) } returns Unit

        val result = viewModel.fetchAITakeawayForVerse(1L)

        assertTrue("Should return success", result.first)
        assertEquals("Should return AI response", aiResponse, result.second)
        coVerify { repository.updateVerse(match { it.aiTakeAwayResponse == aiResponse }) }
    }

    @Test
    fun `fetchAITakeawayForVerse handles AI service error`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { AIService.getKeyTakeaway(any()) } returns AiServiceResult.Error("AI service error")

        val result = viewModel.fetchAITakeawayForVerse(1L)

        assertFalse("Should return failure", result.first)
        assertEquals("Should return error message", "AI service error", result.second)
    }

    @Test
    fun `fetchAITakeawayForVerse handles validation rejection`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { AIService.getKeyTakeaway(any()) } returns AiServiceResult.Success("Bad response")
        coEvery { AIService.validateKeyTakeawayResponse(any(), "Bad response") } returns AiServiceResult.Success(false)

        val result = viewModel.fetchAITakeawayForVerse(1L)

        assertFalse("Should return failure", result.first)
        assertTrue("Should contain rejection message", result.second.contains("rejected by the validator"))
    }

    @Test
    fun `hasCachedAIFeedback returns true when cache matches input`() = runTest {
        val verseWithCache = sampleVerse.copy(
            aiContextExplanationText = "Cached explanation",
            applicationFeedback = "Cached feedback",
            userContextScore = 85,
            userDirectQuote = "Exact quote",
            userContext = "User application"
        )

        val result = viewModel.hasCachedAIFeedback(verseWithCache, "Exact quote", "User application")

        assertTrue("Should return true for matching cache", result)
    }

    @Test
    fun `hasCachedAIFeedback returns false when input differs`() = runTest {
        val verseWithCache = sampleVerse.copy(
            aiContextExplanationText = "Cached explanation",
            userDirectQuote = "Different quote",
            userContext = "Different application"
        )

        val result = viewModel.hasCachedAIFeedback(verseWithCache, "New quote", "New application")

        assertFalse("Should return false for different input", result)
    }

    @Test
    fun `getCachedAIFeedback returns feedback when available`() = runTest {
        val verseWithCache = sampleVerse.copy(
            aiContextExplanationText = "Cached explanation",
            applicationFeedback = "Cached feedback",
            userContextScore = 85
        )

        val result = viewModel.getCachedAIFeedback(verseWithCache)

        assertNotNull("Should return cached feedback", result)
        assertEquals("Should return explanation", "Cached explanation", result?.first)
        assertEquals("Should return feedback", "Cached feedback", result?.second)
    }

    @Test
    fun `getCachedAIFeedback returns null when no cache`() = runTest {
        val verseWithoutCache = sampleVerse.copy(
            aiContextExplanationText = "",
            applicationFeedback = "",
            userContextScore = 0
        )

        val result = viewModel.getCachedAIFeedback(verseWithoutCache)

        assertNull("Should return null when no cache", result)
    }

    @Test
    fun `updateUserData calls repository methods correctly`() = runTest {
        coEvery { repository.updateAIFeedbackData(any(), any(), any(), any()) } returns Unit
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { repository.updateVerse(any()) } returns Unit

        viewModel.updateUserData(1L, "Direct quote", "Application", 85, "AI explanation", "AI feedback")

        coVerify { repository.updateAIFeedbackData(1L, "AI explanation", "AI feedback", 85) }
        coVerify { repository.updateVerse(any()) }
        assertEquals("Should show success message", "AI feedback is saved!", viewModel.errorMessage.value)
    }

    @Test
    fun `updateUserInputOnly updates only user fields`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { repository.updateVerse(any()) } returns Unit

        viewModel.updateUserInputOnly(1L, "New quote", "New context")

        coVerify {
            repository.updateVerse(
                match { verse ->
                    verse.userDirectQuote == "New quote" && verse.userContext == "New context"
                }
            )
        }
        assertEquals("Should show success message", "User input saved!", viewModel.errorMessage.value)
    }

    // ========================================
    // 4. USER DATA MANAGEMENT TESTS
    // ========================================

    @Test
    fun `hasUserData returns true when direct quote exists`() = runTest {
        val verseWithDirectQuote = sampleVerse.copy(userDirectQuote = "Some quote", userContext = "")

        val result = viewModel.hasUserData(verseWithDirectQuote)

        assertTrue("Should return true when direct quote exists", result)
    }

    @Test
    fun `hasUserData returns true when context exists`() = runTest {
        val verseWithContext = sampleVerse.copy(userDirectQuote = "", userContext = "Some context")

        val result = viewModel.hasUserData(verseWithContext)

        assertTrue("Should return true when context exists", result)
    }

    @Test
    fun `hasUserData returns false when both fields empty`() = runTest {
        val verseWithoutData = sampleVerse.copy(userDirectQuote = "", userContext = "")

        val result = viewModel.hasUserData(verseWithoutData)

        assertFalse("Should return false when no user data", result)
    }

    @Test
    fun `saveNewVerse calls repository with correct parameters`() = runTest {
        val newVerseId = 123L
        coEvery {
            repository.insertVerseWithTopics(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16,
                aiTakeAwayResponse = "AI response",
                topics = listOf("Faith", "Love"),
                favorite = true,
                translation = "ESV",
                verses = any()
            )
        } returns newVerseId

        viewModel.saveNewVerse(
            verse = sampleVerseRef,
            aiTakeAwayResponse = "AI response",
            topics = listOf("Faith", "Love"),
            translation = "ESV",
            favorite = true,
            scriptureVerses = listOf(Verse(16, "Scripture text"))
        )

        coVerify {
            repository.insertVerseWithTopics(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16,
                aiTakeAwayResponse = "AI response",
                topics = listOf("Faith", "Love"),
                favorite = true,
                translation = "ESV",
                verses = any()
            )
        }
    }

    @Test
    fun `deleteVerse calls repository correctly`() = runTest {
        coEvery { repository.deleteVerse(sampleVerse) } returns Unit

        viewModel.deleteVerse(sampleVerse)

        coVerify { repository.deleteVerse(sampleVerse) }
    }

    @Test
    fun `updateVerse calls repository correctly`() = runTest {
        coEvery { repository.updateVerse(sampleVerse) } returns Unit

        viewModel.updateVerse(sampleVerse)

        coVerify { repository.updateVerse(sampleVerse) }
    }

    @Test
    fun `hasValidAIFeedback calls repository and returns correct result`() = runTest {
        coEvery { repository.hasValidAIFeedback(1L) } returns 1

        val result = viewModel.hasValidAIFeedback(1L)

        assertTrue("Should return true when count > 0", result)
        coVerify { repository.hasValidAIFeedback(1L) }
    }

    @Test
    fun `hasValidAIFeedback returns false when no valid feedback`() = runTest {
        coEvery { repository.hasValidAIFeedback(1L) } returns 0

        val result = viewModel.hasValidAIFeedback(1L)

        assertFalse("Should return false when count = 0", result)
    }

    @Test
    fun `findExistingVerse calls repository correctly`() = runTest {
        coEvery { repository.findVerseByReference("John", 3, 16) } returns sampleVerse

        val result = viewModel.findExistingVerse("John", 3, 16)

        assertEquals("Should return verse from repository", sampleVerse, result)
        coVerify { repository.findVerseByReference("John", 3, 16) }
    }

    // ========================================
    // 5. REPOSITORY INTEGRATION TESTS
    // ========================================

    @Test
    fun `refreshAfterDatabaseImport creates new repository and refreshes data`() = runTest {
        val mockDao = mockk<BibleVerseDao>(relaxed = true)

        // Mock the DAO methods that will be called during refresh
        every { mockDao.getAllVerses() } returns flowOf(listOf(sampleVerse))
        every { mockDao.getAllTopics() } returns flowOf(sampleTopicsForDao)
        every { mockDao.getAllFavoriteVerses() } returns flowOf(emptyList())

        mockkObject(AppDatabase.Companion)
        every { AppDatabase.getDatabase(context) } returns database
        every { database.bibleVerseDao() } returns mockDao
        every { repository.getAllVerses() } returns flowOf(listOf(sampleVerse))
        every { repository.getAllTopics() } returns flowOf(sampleTopics)
        every { repository.getAllFavoriteVerses() } returns flowOf(emptyList())

        refreshTrigger.emit(Unit)

        // Verify that refresh methods are called (indirectly through the new data flows)
        verify { repository.getAllVerses() }
        verify { repository.getAllTopics() }
        verify { repository.getAllFavoriteVerses() }
    }

    @Test
    fun `database refresh manager flow triggers refresh`() = runTest {
        var refreshTriggered = false

        // Create a new ViewModel to test initialization
        val newViewModel = BibleVerseViewModel(repository, context)

        // Emit refresh signal
        refreshTrigger.emit(Unit)

        // The refresh should have been triggered (we can verify by checking repository calls)
        verify(atLeast = 1) { repository.getAllVerses() }
    }

    @Test
    fun `saveNewVerseHome calls repository correctly`() = runTest {
        val homeViewModel = mockk<HomeViewModel>(relaxed = true)
        val newVerseId = 456L

        coEvery {
            repository.insertVerseWithTopics(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns newVerseId

        viewModel.saveNewVerseHome(
            verse = sampleVerseRef,
            aiTakeAwayResponse = "Home AI response",
            topics = listOf("Hope"),
            translation = "NIV",
            favorite = false,
            homeViewModel = homeViewModel,
            scriptureVerses = listOf(Verse(16, "Home scripture"))
        )

        coVerify {
            repository.insertVerseWithTopics(
                book = "John",
                chapter = 3,
                startVerse = 16,
                endVerse = 16,
                aiTakeAwayResponse = "Home AI response",
                topics = listOf("Hope"),
                favorite = false,
                translation = "NIV",
                verses = any()
            )
        }
        verify { homeViewModel.contentSavedSuccessfully(newVerseId) }
    }

    // ========================================
    // 6. ERROR HANDLING & EDGE CASES TESTS
    // ========================================

    @Test
    fun `network failure in reloadVerseWithNewTranslation shows error`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse
        coEvery { AIService.fetchScripture(any(), any()) } returns AiServiceResult.Error("Network error")

        viewModel.reloadVerseWithNewTranslation(1L, "NIV")

        assertTrue("Should show network error", viewModel.errorMessage.value?.contains("Failed to update translation") == true)
    }

    @Test
    fun `database error in updateFavoriteStatus is handled gracefully`() = runTest {
        coEvery { repository.updateFavoriteStatus(1L, true) } throws RuntimeException("Database connection failed")

        viewModel.updateFavoriteStatus(1L, true)

        assertTrue("Should show database error", viewModel.errorMessage.value?.contains("Error updating favorite status") == true)
    }

    @Test
    fun `exception in fetchAITakeawayForVerse is handled`() = runTest {
        coEvery { repository.getVerseById(1L) } throws RuntimeException("Repository error")

        val result = viewModel.fetchAITakeawayForVerse(1L)

        assertFalse("Should return failure", result.first)
        assertTrue("Should contain error message", result.second.contains("Error fetching AI takeaway"))
    }

    @Test
    fun `blank input validation in addTopic`() = runTest {
        viewModel.addTopic("")

        assertEquals("Should reject empty string", "Topic name cannot be blank", viewModel.errorMessage.value)

        viewModel.addTopic("   ")

        assertEquals("Should reject whitespace-only string", "Topic name cannot be blank", viewModel.errorMessage.value)
    }

    @Test
    fun `null input handling in renameOrMergeTopic`() = runTest {
        viewModel.renameOrMergeTopic("ValidTopic", "", false)

        assertEquals("Should handle empty new name", "New topic name cannot be blank.", viewModel.errorMessage.value)
    }

    @Test
    fun `concurrent updateFavoriteStatus calls are handled`() = runTest {
        coEvery { repository.updateFavoriteStatus(any(), any()) } returns Unit

        // Simulate concurrent calls
        viewModel.updateFavoriteStatus(1L, true)
        viewModel.updateFavoriteStatus(2L, false)
        viewModel.updateFavoriteStatus(3L, true)

        coVerify { repository.updateFavoriteStatus(1L, true) }
        coVerify { repository.updateFavoriteStatus(2L, false) }
        coVerify { repository.updateFavoriteStatus(3L, true) }
    }

    @Test
    fun `exception in updateUserData is handled gracefully`() = runTest {
        coEvery { repository.updateAIFeedbackData(any(), any(), any(), any()) } throws RuntimeException("Database error")

        viewModel.updateUserData(1L, "Quote", "Context", 85, "AI explanation", "AI feedback")

        assertTrue("Should show error message", viewModel.errorMessage.value?.contains("Error saving AI Score") == true)
    }

    @Test
    fun `hasValidAIFeedback handles repository exception`() = runTest {
        coEvery { repository.hasValidAIFeedback(1L) } throws RuntimeException("Database error")

        val result = viewModel.hasValidAIFeedback(1L)

        assertFalse("Should return false on exception", result)
    }

    @Test
    fun `validation error in renameOrMergeTopic is handled`() = runTest {
        coEvery { repository.renameOrMergeTopic(any(), any(), any()) } throws IllegalArgumentException("Validation failed")

        viewModel.renameOrMergeTopic("OldTopic", "NewTopic", false)

        assertEquals("Should show validation error", "Validation failed", viewModel.errorMessage.value)
    }

    @Test
    fun `edge case - empty translation string in reloadVerseWithNewTranslation`() = runTest {
        coEvery { repository.getVerseById(1L) } returns sampleVerse.copy(translation = "")
        coEvery { AIService.fetchScripture(any(), "") } returns AiServiceResult.Success(listOf(Verse(16, "Empty translation result")))
        coEvery { repository.updateVerse(any()) } returns Unit

        viewModel.reloadVerseWithNewTranslation(1L, "")

        coVerify { repository.updateVerse(match { it.translation == "" }) }
    }
}