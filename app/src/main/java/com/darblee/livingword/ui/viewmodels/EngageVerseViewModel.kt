package com.darblee.livingword.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ScoreData(
    val ContextScore: Int,
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
        val contextScore: Int = -1,
        val verse: BibleVerseRef? = null,
        val aiScoreExplanationText : String? = null,
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

    // Use the AIService which handles Gemini + OpenAI fallback
    private val aiService = AIService

    init {
        updateAiServiceStatus()
    }

    private fun updateAiServiceStatus() {
        val isReady = aiService.isInitialized()
        val initError = if (!isReady) aiService.getInitializationError() else null
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
                contextScore = contextScore,
                // Reset explanations as they are tied to a specific evaluation
                aiScoreExplanationText = null,
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
                    contextScore = -1,
                    aiScoreExplanationText = null,
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
     * Load cached AI feedback from the BibleVerse if the input hasn't changed
     */
    private fun loadCachedFeedbackIfAvailable(
        verse: com.darblee.livingword.data.BibleVerse,
        userMemorizedScripture: String,
        userApplicationContent: String
    ): Boolean {
        return try {
            Log.d("EngageVerseViewModel", "Checking for cached feedback...")

            // Check if we have cached feedback data using direct property access
            val hasExistingFeedback = (verse.aiContextExplanationText.isNotEmpty() || verse.applicationFeedback.isNotEmpty()) &&
                                     verse.userContextScore > 0
            val inputMatches = verse.userDirectQuote.trim() == userMemorizedScripture.trim() &&
                              verse.userContext.trim() == userApplicationContent.trim()

            if (hasExistingFeedback && inputMatches) {
                Log.d("EngageVerseViewModel", "Loading cached AI feedback results")

                // Use direct property access for cached data
                val cachedContextExplanation = verse.aiContextExplanationText
                val cachedApplicationFeedback = verse.applicationFeedback


                // Check if the cached data is actually valid (not empty or placeholder text)
                val isValidCache = (
                    cachedContextExplanation.isNotEmpty() &&
                    cachedApplicationFeedback.isNotEmpty() &&
                    !cachedContextExplanation.contains("Getting score") &&
                    verse.userContextScore > 0
                )

                Log.d("EngageVerseViewModel", "Cache validity check: $isValidCache")

                if (isValidCache) {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            contextScore = verse.userContextScore,
                            aiScoreExplanationText = cachedContextExplanation,
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
                    aiResponseError = it.aiResponseError ?: aiService.getInitializationError()
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
            !currentState.aiScoreExplanationText.isNullOrEmpty() &&
            !currentState.applicationFeedback.isNullOrEmpty() &&
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
                contextScore = -1,
                aiScoreExplanationText = null,
                applicationFeedback = null,
                isUsingCachedResults = false
            )
        }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {
            Log.d("EngageVerseViewModel", "Calling Hybrid AI service (Gemini + OpenAI fallback) for verse: $verseRef")

            // Call the AIService (Gemini with OpenAI fallback)
            when (val scoreData = aiService.getAIScore(verseRef, userMemorizedScripture, userApplicationContent)) {
                is AiServiceResult.Success -> {
                    try {
                        Log.d("EngageVerseViewModel", "AI service returned success with score data: ${scoreData.data}")

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                contextScore = scoreData.data.ContextScore,
                                aiScoreExplanationText = scoreData.data.ContextExplanation,
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
                                contextScore = -1,
                                aiScoreExplanationText = null,
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
                            contextScore = -1,
                            aiScoreExplanationText = null,
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