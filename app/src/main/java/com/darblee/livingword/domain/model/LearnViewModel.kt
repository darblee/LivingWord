package com.darblee.livingword.domain.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.BibleVerseT
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.ESVBibleLookupService
import com.darblee.livingword.data.remote.GeminiAIService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Learn Screen, responsible for managing state and fetching data for a
 * single verse display.
 */
class LearnViewModel() : ViewModel() {

    /**
     * Represents the UI state for the LearnScreen.
     */
    data class LearnScreenState(
        // State related to topic selection for the *current* item
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false, // Loading state for topic-based content

        // State for the currently displayed single verse
        val selectedVerse: BibleVerseT? = null,
        val scriptureText: String = "",
        val aiResponseText: String = "",
        val isScriptureLoading: Boolean = false,
        val aiResponseLoading: Boolean = false,
        val scriptureError: String? = null,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isContentSaved: Boolean = false, // Track if current content has been saved
        val newlySavedVerseId: Long? = null,
        val isLoading: Boolean = false
    )

    private val _state = MutableStateFlow(LearnScreenState())
    val state: StateFlow<LearnScreenState> = _state.asStateFlow()

    private var fetchDataJob: Job? = null

    private val esvBibleLookupService: ESVBibleLookupService = ESVBibleLookupService()

    // Initialize GeminiAIService
    private val geminiService: GeminiAIService = GeminiAIService()

    init {
        // Check Gemini service initialization status and update state if there's an error
        if (!geminiService.isInitialized()) {
            val initError = geminiService.getInitializationError()
            _state.update {
                it.copy(
                    aiResponseError = initError,
                    // If the error is specifically "Failed to initialize AI Model", set generalError
                    generalError = if (initError?.contains("Failed to initialize AI Model", ignoreCase = true) == true) initError else null
                )
            }
        }
    }

    fun updateSelectedTopics(selectedTopics: List<String>) {
        selectedTopics.forEach {
            Log.i("LearnViewModel", "Topic: $it")
        }
        _state.update { currentState ->
            currentState.copy(
                selectedTopics = selectedTopics,
                isContentSaved = false,
            )
        }
    }

    fun contentSavedSuccessfully(newVerseId: Long) {
        _state.update {
            it.copy(
                selectedTopics = emptyList(),
                selectedVerse = null,
                scriptureText = "",
                aiResponseText = "",
                isContentSaved = true,
                newlySavedVerseId = newVerseId,
                isLoading = false,
            )
        }
        viewModelScope.launch {
            SnackBarController.showMessage("Verse is saved")
        }
    }

    fun clearVerseData() {
        fetchDataJob?.cancel()
        _state.update { currentState ->
            val geminiInitError = if (!geminiService.isInitialized()) geminiService.getInitializationError() else null
            currentState.copy(
                selectedVerse = null,
                scriptureText = "",
                aiResponseText = "",
                selectedTopics = emptyList(),
                isScriptureLoading = false,
                aiResponseLoading = false,
                scriptureError = null,
                aiResponseError = geminiInitError, // Reflect current Gemini init status
                generalError = if (geminiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true) geminiInitError else null,
                isContentSaved = false,
                newlySavedVerseId = null,
            )
        }
    }

    fun setSelectedVerseAndFetchData(verse: BibleVerseT?) {
        if (verse == null) {
            clearVerseData()
            return
        }

        val currentState = _state.value
        val geminiReady = geminiService.isInitialized()
        val geminiInitError = if (!geminiReady) geminiService.getInitializationError() else null

        // Avoid re-fetching if the exact same verse is already selected and loaded without errors
        if (verse == currentState.selectedVerse &&
            currentState.scriptureText.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiResponseText.isNotEmpty() || currentState.aiResponseError != null || !geminiReady)
        ) {
            Log.d("LearnViewModel", "Verse $verse already loaded. Skipping fetch.")
            // If AI response had an error before (and model exists), maybe retry?
            if (geminiReady && currentState.aiResponseText.isEmpty() && currentState.aiResponseError != null && !currentState.aiResponseLoading) {
                Log.d("LearnViewModel", "Retrying AI response fetch for $verse.")
                fetchKeyTakeAwayOnly(verse)
            }
            return
        }

        fetchDataJob?.cancel()

        _state.update {
            it.copy(
                selectedVerse = verse,
                isScriptureLoading = true,
                aiResponseLoading = geminiReady, // Start AI loading only if service is initialized
                scriptureText = "Loading Scripture...",
                aiResponseText = if (geminiReady) "Getting AI Response ..." else (geminiInitError ?: ""),
                scriptureError = null,
                aiResponseError = if (geminiReady) null else geminiInitError, // Clear previous errors only if Gemini is ready
                generalError = if (geminiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true) geminiInitError else null,
                isContentSaved = false,
            )
        }

        fetchDataJob = viewModelScope.launch {
            val scriptureResult = esvBibleLookupService.fetchScripture(verse)

            _state.update {
                it.copy(
                    isScriptureLoading = false,
                    scriptureText = if (scriptureResult.isSuccess) scriptureResult.getOrDefault("") else "",
                    scriptureError = if (scriptureResult.isFailure) scriptureResult.exceptionOrNull()?.message else null
                )
            }

            if (scriptureResult.isSuccess && geminiReady) {
                val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
                // Call the GeminiAIService
                when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                    is AiServiceResult.Success -> {
                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                aiResponseText = takeAwayResult.data,
                                aiResponseError = null
                            )
                        }
                    }
                    is AiServiceResult.Error -> {
                        _state.update {
                            it.copy(
                                aiResponseLoading = false,
                                aiResponseText = "", // Clear text on error
                                aiResponseError = takeAwayResult.message
                            )
                        }
                    }
                }
            } else if (geminiReady) { // Scripture fetch failed, but Gemini service was ready.
                _state.update {
                    it.copy(
                        aiResponseLoading = false,
                        aiResponseError = it.aiResponseError ?: "Cannot fetch take-away due to scripture error."
                    )
                }
            } else { // Gemini service not ready, ensure loading is false
                _state.update { it.copy(aiResponseLoading = false) }
            }
        }
    }

    private fun fetchKeyTakeAwayOnly(verse: BibleVerseT) {
        if (!geminiService.isInitialized()) {
            Log.w("LearnViewModel", "Skipping take-away retry as GeminiAIService is not initialized.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() // Ensure init error is shown
                )
            }
            return
        }

        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiResponseText = "Getting key take-away...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {
            // Call the GeminiAIService
            when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                is AiServiceResult.Success -> {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            aiResponseText = takeAwayResult.data,
                            aiResponseError = null
                        )
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            aiResponseLoading = false,
                            aiResponseText = "", // Clear text on error
                            aiResponseError = takeAwayResult.message
                        )
                    }
                }
            }
        }
    }

    fun resetNavigationState() {
        _state.update {
            it.copy(newlySavedVerseId = null, isContentSaved = false)
        }
    }
}