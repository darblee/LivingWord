package com.darblee.livingword.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ScoreData(
    val DirectQuoteScore: Int,
    val ContextScore: Int,
    val DirectQuoteExplanation: String,
    val ContextExplanation: String,
    var ApplicationFeedback: String
)

class EngageVerseViewModel() : ViewModel(){

    /**
     * Represents the UI state for the NewVerse screen.
     */
    data class MemorizedVerseScreenState(
        val applicationFeedback: String? = null,
        // State related to topic selection for the *current* item
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false, // Loading state for topic-based content

        // State for the currently displayed single verse
        val directQuoteScore: Int = -1,
        val contextScore: Int = -1,
        val verse: BibleVerseRef? = null,
        val aiDirectQuoteExplanationText : String? = null,
        val aiContextExplanationText : String? = null,
        val aiResponseLoading: Boolean = false,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isLoading: Boolean = false,
        val isAiServiceReady: Boolean = false, // To track AI service status

        // Cache tracking fields
        val lastEvaluatedDirectQuote: String = "",
        val lastEvaluatedUserApplication: String = "",
        val isUsingCachedResults: Boolean = false
    )

    private val _state = MutableStateFlow(MemorizedVerseScreenState())
    val state: StateFlow<MemorizedVerseScreenState> = _state.asStateFlow()

    // Use the GeminiAIService object directly
    private val geminiService = GeminiAIService // Changed from: GeminiAIService()

    init {
        updateAiServiceStatus()
    }

    private fun updateAiServiceStatus() {
        val isReady = geminiService.isInitialized()
        val initError = if (!isReady) geminiService.getInitializationError() else null
        _state.update {
            it.copy(
                isAiServiceReady = isReady,
                aiResponseError = if (it.aiResponseError == null && initError != null) initError else it.aiResponseError, // Preserve existing errors unless this is the first check
                generalError = if (initError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || initError?.contains("API Key missing", ignoreCase = true) == true) initError else null
            )
        }
    }

    fun loadScores(directQuoteScore: Int, contextScore: Int) {
        _state.update {
            it.copy(
                directQuoteScore = directQuoteScore,
                contextScore = contextScore,
                // Reset explanations as they are tied to a specific evaluation
                aiDirectQuoteExplanationText = null,
                aiContextExplanationText = null,
                applicationFeedback = null,
                aiResponseError = null,
                aiResponseLoading = false, // ensure loading is off
                isUsingCachedResults = false // Reset cache flag when manually loading scores
            )
        }
    }

    fun resetScore() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiResponseLoading = false, // Make it false - don't show loading when just resetting
                    directQuoteScore = -1,
                    contextScore = -1,
                    aiDirectQuoteExplanationText = null,
                    aiContextExplanationText = null,
                    applicationFeedback = null,
                    aiResponseError = null,
                    lastEvaluatedDirectQuote = "",
                    lastEvaluatedUserApplication = "",
                    isUsingCachedResults = false
                )
            }
        }
    }

    /**
     * Debug method to help troubleshoot AI service issues
     * This will attempt to get a simple response from the AI service to test connectivity and parsing
     */
    fun testAIService() {
        updateAiServiceStatus()
        val geminiReady = _state.value.isAiServiceReady

        if (!geminiReady) {
            _state.update {
                it.copy(
                    aiResponseError = "AI service not ready: ${geminiService.getInitializationError()}"
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d("EngageVerseViewModel", "Testing AI service with simple request...")
            
            // Test with a simple verse and input
            when (val result = geminiService.getAIScore("John 3:16", "For God so loved the world", "This verse teaches about God's love")) {
                is AiServiceResult.Success -> {
                    _state.update {
                        it.copy(
                            aiResponseError = "AI service test successful! DirectQuoteScore: ${result.data.DirectQuoteScore}"
                        )
                    }
                    Log.d("EngageVerseViewModel", "AI service test successful: ${result.data}")
                }
                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            aiResponseError = "AI service test failed: ${result.message}"
                        )
                    }
                    Log.e("EngageVerseViewModel", "AI service test failed: ${result.message}")
                }
            }
        }
    }

    /**
     * Debug method to check current caching state
     */
    fun debugCacheState(userDirectQuote: String, userContext: String) {
        val currentState = _state.value
        Log.d("EngageVerseViewModel", "=== CACHE DEBUG STATE ===")
        Log.d("EngageVerseViewModel", "Current input:")
        Log.d("EngageVerseViewModel", "  - Direct quote: '$userDirectQuote'")
        Log.d("EngageVerseViewModel", "  - Context: '$userContext'")
        Log.d("EngageVerseViewModel", "Last evaluated input:")
        Log.d("EngageVerseViewModel", "  - Direct quote: '${currentState.lastEvaluatedDirectQuote}'")
        Log.d("EngageVerseViewModel", "  - Context: '${currentState.lastEvaluatedUserApplication}'")
        Log.d("EngageVerseViewModel", "Input matches: ${currentState.lastEvaluatedDirectQuote.trim() == userDirectQuote.trim() && currentState.lastEvaluatedUserApplication.trim() == userContext.trim()}")
        Log.d("EngageVerseViewModel", "Has valid AI data:")
        Log.d("EngageVerseViewModel", "  - DirectQuoteExplanation: ${!currentState.aiDirectQuoteExplanationText.isNullOrEmpty()}")
        Log.d("EngageVerseViewModel", "  - ContextExplanation: ${!currentState.aiContextExplanationText.isNullOrEmpty()}")
        Log.d("EngageVerseViewModel", "  - ApplicationFeedback: ${!currentState.applicationFeedback.isNullOrEmpty()}")
        Log.d("EngageVerseViewModel", "  - DirectQuoteScore: ${currentState.directQuoteScore}")
        Log.d("EngageVerseViewModel", "  - ContextScore: ${currentState.contextScore}")
        Log.d("EngageVerseViewModel", "  - No error: ${currentState.aiResponseError == null}")
        Log.d("EngageVerseViewModel", "Using cached results flag: ${currentState.isUsingCachedResults}")
        Log.d("EngageVerseViewModel", "========================")
    }

    /**
     * Load cached AI feedback from the BibleVerse if the input hasn't changed
     */
    private fun loadCachedFeedbackIfAvailable(
        verse: com.darblee.livingword.data.BibleVerse,
        userMemorizedScripture: String,
        userApplicationContent: String
    ): Boolean {
        return try {
            Log.d("EngageVerseViewModel", "Checking for cached feedback...")

            // Use the safe accessor methods from the updated BibleVerse class
            val hasExistingFeedback = verse.hasCachedAIFeedback()
            val inputMatches = verse.matchesCachedInput(userMemorizedScripture, userApplicationContent)

            Log.d("EngageVerseViewModel", "Has cached feedback: $hasExistingFeedback, Input matches: $inputMatches")
            Log.d("EngageVerseViewModel", "Cached input - Direct quote: '${verse.userDirectQuote}', Context: '${verse.userContext}'")
            Log.d("EngageVerseViewModel", "Current input - Direct quote: '$userMemorizedScripture', Context: '$userApplicationContent'")

            if (hasExistingFeedback && inputMatches) {
                Log.d("EngageVerseViewModel", "Loading cached AI feedback results")

                // Validate cached data before using it
                val cachedDirectExplanation = verse.getSafeAIDirectQuoteExplanation()
                val cachedContextExplanation = verse.getSafeAIContextExplanation()
                val cachedApplicationFeedback = verse.getSafeApplicationFeedback()

                Log.d("EngageVerseViewModel", "Cached data validation:")
                Log.d("EngageVerseViewModel", "  - DirectExplanation: '${cachedDirectExplanation.take(50)}...' (length: ${cachedDirectExplanation.length})")
                Log.d("EngageVerseViewModel", "  - ContextExplanation: '${cachedContextExplanation.take(50)}...' (length: ${cachedContextExplanation.length})")
                Log.d("EngageVerseViewModel", "  - ApplicationFeedback: '${cachedApplicationFeedback.take(50)}...' (length: ${cachedApplicationFeedback.length})")
                Log.d("EngageVerseViewModel", "  - DirectQuoteScore: ${verse.userDirectQuoteScore}")
                Log.d("EngageVerseViewModel", "  - ContextScore: ${verse.userContextScore}")

                // Check if the cached data is actually valid (not empty or placeholder text)
                val isValidCache = (
                    cachedDirectExplanation.isNotEmpty() &&
                    cachedContextExplanation.isNotEmpty() &&
                    cachedApplicationFeedback.isNotEmpty() &&
                    !cachedDirectExplanation.contains("Getting score") &&
                    !cachedContextExplanation.contains("Getting score") &&
                    verse.userDirectQuoteScore > 0 &&
                    verse.userContextScore > 0
                )
                
                Log.d("EngageVerseViewModel", "Cache validity check: $isValidCache")

                if (isValidCache) {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            directQuoteScore = verse.userDirectQuoteScore,
                            contextScore = verse.userContextScore,
                            aiDirectQuoteExplanationText = cachedDirectExplanation,
                            aiContextExplanationText = cachedContextExplanation,
                            applicationFeedback = cachedApplicationFeedback,
                            aiResponseError = null,
                            lastEvaluatedDirectQuote = userMemorizedScripture.trim(),
                            lastEvaluatedUserApplication = userApplicationContent.trim(),
                            isUsingCachedResults = true
                        )
                    }
                    Log.d("EngageVerseViewModel", "Successfully loaded valid cached feedback")
                    return true
                } else {
                    Log.d("EngageVerseViewModel", "Cached data exists but is invalid or incomplete - fetching fresh data")
                }
            } else {
                Log.d("EngageVerseViewModel", "Cache check failed:")
                Log.d("EngageVerseViewModel", "  - Has cached feedback: $hasExistingFeedback")
                Log.d("EngageVerseViewModel", "  - Input matches: $inputMatches")
                if (!inputMatches) {
                    Log.d("EngageVerseViewModel", "Input mismatch details:")
                    Log.d("EngageVerseViewModel", "  - Stored direct quote: '${verse.userDirectQuote.trim()}'")
                    Log.d("EngageVerseViewModel", "  - Current direct quote: '${userMemorizedScripture.trim()}'")
                    Log.d("EngageVerseViewModel", "  - Stored context: '${verse.userContext.trim()}'")
                    Log.d("EngageVerseViewModel", "  - Current context: '${userApplicationContent.trim()}'")
                }
            }

            Log.d("EngageVerseViewModel", "No valid cached results found - will fetch from AI service")
            return false

        } catch (e: Exception) {
            Log.e("EngageVerseViewModel", "Error loading cached feedback: ${e.message}", e)
            // If there's any serialization or data access error, fall back to fresh AI call
            return false
        }
    }

    fun getAIFeedback(
        verse: BibleVerseRef,
        userMemorizedScripture: String,
        userApplicationContent: String,
        cachedBibleVerse: com.darblee.livingword.data.BibleVerse? = null
    ) {
        Log.d("EngageVerseViewModel", "getAIFeedback called with verse: $verse")

        updateAiServiceStatus()
        val geminiReady = _state.value.isAiServiceReady

        if (!geminiReady) {
            Log.w("EngageVerseViewModel", "Skipping score retry as GeminiAIService is not initialized.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError()
                )
            }
            return
        }

        // First check if we already have valid results in our ViewModel state for the same input
        val currentState = _state.value
        val currentInputMatches = (
            currentState.lastEvaluatedDirectQuote.trim() == userMemorizedScripture.trim() &&
            currentState.lastEvaluatedUserApplication.trim() == userApplicationContent.trim()
        )
        
        val hasValidStateResults = (
            !currentState.aiDirectQuoteExplanationText.isNullOrEmpty() &&
            !currentState.aiContextExplanationText.isNullOrEmpty() &&
            !currentState.applicationFeedback.isNullOrEmpty() &&
            currentState.directQuoteScore > 0 &&
            currentState.contextScore > 0 &&
            currentState.aiResponseError == null
        )
        
        if (currentInputMatches && hasValidStateResults) {
            Log.d("EngageVerseViewModel", "Found matching results in ViewModel state, using cached results")
            _state.update {
                it.copy(
                    isUsingCachedResults = true,
                    aiResponseLoading = false,
                    aiResponseError = null
                )
            }
            return // Exit early, using state cache
        }

        // Check for cached results in database if BibleVerse is provided
        if (cachedBibleVerse != null) {
            Log.d("EngageVerseViewModel", "Checking database cache...")
            val usedCache = loadCachedFeedbackIfAvailable(cachedBibleVerse, userMemorizedScripture, userApplicationContent)
            if (usedCache) {
                Log.d("EngageVerseViewModel", "Used cached feedback from database, returning early")
                return // Exit early, cached results loaded
            }
        }
        
        // No cache available, proceed with AI call
        Log.d("EngageVerseViewModel", "No cache available, proceeding with AI call...")
        performAICall(verse, userMemorizedScripture, userApplicationContent)
    }

    private fun performAICall(
        verse: BibleVerseRef,
        userMemorizedScripture: String,
        userApplicationContent: String
    ) {

        Log.d("EngageVerseViewModel", "Making fresh AI service call...")

        // Set loading state before making the API call
        _state.update {
            it.copy(
                aiResponseLoading = true,
                aiResponseError = null,
                directQuoteScore = -1,
                contextScore = -1,
                aiDirectQuoteExplanationText = null,
                aiContextExplanationText = null,
                applicationFeedback = null,
                isUsingCachedResults = false
            )
        }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {
            Log.d("EngageVerseViewModel", "Calling Gemini API for verse: $verseRef")

            // Call the GeminiAIService
            when (val scoreData = geminiService.getAIScore(verseRef, userMemorizedScripture, userApplicationContent)) {
                is AiServiceResult.Success -> {
                    try {
                        Log.d("EngageVerseViewModel", "AI service returned success with score data: ${scoreData.data}")

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                directQuoteScore = scoreData.data.DirectQuoteScore,
                                contextScore = scoreData.data.ContextScore,
                                aiDirectQuoteExplanationText = scoreData.data.DirectQuoteExplanation,
                                aiContextExplanationText = scoreData.data.ContextExplanation,
                                applicationFeedback = scoreData.data.ApplicationFeedback,
                                aiResponseError = null,
                                lastEvaluatedDirectQuote = userMemorizedScripture.trim(),
                                lastEvaluatedUserApplication = userApplicationContent.trim(),
                                isUsingCachedResults = false
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("EngageVerseViewModel", "Error processing AI response: ${e.message}", e)

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                directQuoteScore = -1,
                                contextScore = -1,
                                aiDirectQuoteExplanationText = null,
                                aiContextExplanationText = null,
                                applicationFeedback = null,
                                aiResponseError = "Unable to parse AI response: ${e.message}",
                                isUsingCachedResults = false
                            )
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    Log.e("EngageVerseViewModel", "AI service returned error: ${scoreData.message}")

                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            directQuoteScore = -1,
                            contextScore = -1,
                            aiDirectQuoteExplanationText = null,
                            aiContextExplanationText = null,
                            applicationFeedback = null,
                            aiResponseError = scoreData.message,
                            isUsingCachedResults = false
                        )
                    }
                }
            }
        }
    }
}