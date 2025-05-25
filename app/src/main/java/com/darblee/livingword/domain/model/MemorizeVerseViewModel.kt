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
    val Score: Int,
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
        val score: Int = -1,
        val verse: BibleVerseT? = null,
        val aiResponseText : String? = null,
        val aiResponseLoading: Boolean = false,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isLoading: Boolean = false
    )

    private val _state = MutableStateFlow(MemorizedVerseScreenState())
    val state: StateFlow<MemorizedVerseScreenState> = _state.asStateFlow()

    private val geminiService: GeminiAIService = GeminiAIService()

    fun fetchMemorizedScore(verse: BibleVerseT, memorizedText: String) {
        if (!geminiService.isInitialized()) {
            Log.w("MemorizedVerseViewModel", "Skipping memorized score retry as GeminiAIService is not initialized.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() // Ensure init error is shown
                )
            }
            return
        }

        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiResponseText = "Getting score ...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {

            _state.update {
                it.copy(
                    aiResponseLoading = true,
                    score = -1,
                    aiResponseText = null,
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
                                score = scoreData.Score,
                                aiResponseText = scoreData.Explanation,
                                aiResponseError = null
                            )
                        }

                    } catch (e: Exception) {
                        println("Error parsing JSON: ${e.message}")
                        // Handle the exception appropriately in your application

                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                score = -1,
                                aiResponseText = null,
                                aiResponseError = "Unable to parse AI response"
                            )
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            score = -1,
                            aiResponseText = "", // Clear score on error
                            aiResponseError = memorizedScoreResult.message
                        )
                    }
                }
            }
        }
    }
}