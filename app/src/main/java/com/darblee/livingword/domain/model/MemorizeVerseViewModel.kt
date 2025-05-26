package com.darblee.livingword.domain.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.BibleVerseT
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ScoreData(
    val DirectQuoteScore: Int,
    val ContextScore: Int,
    val Explanation: String
)


class MemorizeVerseViewModel() : ViewModel(){

    /**
     * Represents the UI state for the LearnScreen.
     */
    data class MemorizedVerseScreenState(
        // State related to topic selection for the *current* item
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false, // Loading state for topic-based content

        // State for the currently displayed single verse
        val directQuoteScore: Int = -1,
        val contextScore: Int = -1,
        val verse: BibleVerseT? = null,
        val aiExplanationText : String? = null,
        val aiResponseLoading: Boolean = false,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isLoading: Boolean = false
    )

    private val _state = MutableStateFlow(MemorizedVerseScreenState())
    val state: StateFlow<MemorizedVerseScreenState> = _state.asStateFlow()

    // Use the GeminiAIService object directly
    private val geminiService = GeminiAIService // Changed from: GeminiAIService()

    // Optional: Add init block to check Gemini initialization if needed for this ViewModel specifically
    init {
        if (!geminiService.isInitialized()) { // Access directly
            val initError = geminiService.getInitializationError() // Access directly
            _state.update {
                it.copy(
                    aiResponseError = initError,
                    generalError = if (initError?.contains("Failed to initialize AI Model", ignoreCase = true) == true) initError else null
                )
            }
        }
    }

    fun fetchMemorizedScore(verse: BibleVerseT, memorizedText: String) {
        if (!geminiService.isInitialized()) { // Access directly
            Log.w("MemorizedVerseViewModel", "Skipping memorized score retry as GeminiAIService is not initialized.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() // Access directly
                )
            }
            return
        }

        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiExplanationText = "Getting score ...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {

            _state.update {
                it.copy(
                    aiResponseLoading = true,
                    directQuoteScore = -1,
                    contextScore = -1,
                    aiExplanationText = null,
                    aiResponseError = null
                )
            }
            // Call the GeminiAIService
            when (val memorizedScoreResult = geminiService.getMemorizedScore(verseRef, memorizedText)) {
                is AiServiceResult.Success -> {

                    var responseJSONText = (memorizedScoreResult.data).removePrefix("```json").removeSuffix("```").trim()

                    // Create a Json parser instance
                    val json = Json { ignoreUnknownKeys = true } // ignoreUnknownKeys is good practice

                    try {
                        // Deserialize the JSON string into an instance of ScoreData
                        val scoreData = json.decodeFromString<ScoreData>(responseJSONText)

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                directQuoteScore = scoreData.DirectQuoteScore,
                                contextScore = scoreData.ContextScore,
                                aiExplanationText = scoreData.Explanation,
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
                                aiExplanationText = null,
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
                            aiExplanationText = "", // Clear score on error
                            aiResponseError = memorizedScoreResult.message
                        )
                    }
                }
            }
        }
    }
}