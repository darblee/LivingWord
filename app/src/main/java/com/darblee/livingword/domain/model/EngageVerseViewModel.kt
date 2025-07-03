package com.darblee.livingword.domain.model

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
        val isAiServiceReady: Boolean = false // To track AI service status

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
                aiResponseLoading = false // ensure loading is off
            )
        }
    }

    fun resetScore() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    aiResponseLoading = true,
                    directQuoteScore = -1,
                    contextScore = -1,
                    aiDirectQuoteExplanationText = null,
                    aiContextExplanationText = null,
                    applicationFeedback = null,
                    aiResponseError = null
                )
            }
        }
    }

    fun getAIFeedback(verse: BibleVerseRef, userMemorizedScripture: String, userApplicationContent: String) {
        updateAiServiceStatus()
        val geminiReady = _state.value.isAiServiceReady

        if (!geminiReady) {
            Log.w("MemorizedVerseViewModel", "Skipping score retry as GeminiAIService is not initialized.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() // Access directly
                )
            }
            return
        }

        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiDirectQuoteExplanationText = "Getting score ...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {

            _state.update {
                it.copy(
                    aiResponseLoading = true,
                    directQuoteScore = -1,
                    contextScore = -1,
                    aiDirectQuoteExplanationText = null,
                    aiContextExplanationText = null,
                    applicationFeedback = null,
                    aiResponseError = null
                )
            }
            // Call the GeminiAIService
            when (val scoreData = geminiService.getAIScore(verseRef, userMemorizedScripture, userApplicationContent)) {
                is AiServiceResult.Success -> {

                    try {

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                directQuoteScore = scoreData.data.DirectQuoteScore,
                                contextScore = scoreData.data.ContextScore,
                                aiDirectQuoteExplanationText = scoreData.data.DirectQuoteExplanation,
                                aiContextExplanationText = scoreData.data.ContextExplanation,
                                applicationFeedback = scoreData.data.ApplicationFeedback,
                                aiResponseError = null
                            )
                        }

                    } catch (e: Exception) {
                        println("Error parsing JSON: ${e.message}")
                        // Handle the exception appropriately in your application

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                directQuoteScore = -1,
                                contextScore = -1,
                                aiDirectQuoteExplanationText = null,
                                aiContextExplanationText = null,
                                applicationFeedback = null,
                                aiResponseError = "Unable to parse AI response"
                            )
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            directQuoteScore = -1,
                            contextScore = -1,
                            aiDirectQuoteExplanationText = "", // Clear score on error
                            aiContextExplanationText = "",
                            applicationFeedback = "",
                            aiResponseError = scoreData.message
                        )
                    }
                }
            }
        }
    }
}