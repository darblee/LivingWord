package com.darblee.livingword.domain.model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.ScriptureContent
import com.darblee.livingword.data.ScriptureUtils.scriptureContentToString
import com.darblee.livingword.data.remote.AiServiceResult
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
class NewVerseViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * Represents the UI state for the LearnScreen.
     */
    data class NewVerseScreenState(
        // State related to topic selection for the *current* item
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false, // Loading state for topic-based content

        // State for the currently displayed single verse
        val selectedVerse: BibleVerseRef? = null,
        val scriptureText: String = "",
        val scriptureJson: ScriptureContent = ScriptureContent(translation = "", verses = emptyList()),
        val aiResponseText: String = "",
        val isScriptureLoading: Boolean = false,
        val aiResponseLoading: Boolean = false,
        val scriptureError: String? = null,
        val aiResponseError: String? = null,
        val generalError: String? = null, // For other errors like Gemini init
        val isContentSaved: Boolean = false, // Track if current content has been saved
        val newlySavedVerseId: Long? = null,
        val isLoading: Boolean = false,
        val isAiServiceReady: Boolean = false, // To track AI service status
        val translation: String = ""
    )

    private val _state = MutableStateFlow(NewVerseScreenState())
    val state: StateFlow<NewVerseScreenState> = _state.asStateFlow()

    private var fetchDataJob: Job? = null

    private val preferenceStore = PreferenceStore(application)
    private val geminiService = GeminiAIService // Singleton instance

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

    fun updateSelectedTopics(selectedTopics: List<String>) {
        selectedTopics.forEach {
            Log.i("NewVerseViewModel", "Topic: $it")
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
        updateAiServiceStatus() // Refresh AI status
        val currentAiStatus = _state.value
        _state.update {
            it.copy(
                selectedVerse = null,
                scriptureText = "",
                aiResponseText = "",
                selectedTopics = emptyList(),
                isScriptureLoading = false,
                aiResponseLoading = false,
                scriptureError = null,
                aiResponseError = currentAiStatus.aiResponseError, // Use refreshed error status
                generalError = currentAiStatus.generalError,
                isContentSaved = false,
                newlySavedVerseId = null,
            )
        }
    }

    fun setSelectedVerseAndFetchData(verse: BibleVerseRef?) {
        if (verse == null) {
            clearVerseData()
            return
        }

        updateAiServiceStatus() // Refresh AI status before fetching
        val geminiReady = _state.value.isAiServiceReady
        val geminiInitError = _state.value.aiResponseError ?: _state.value.generalError

        val currentState = _state.value

        // Avoid re-fetching if the exact same verse is already selected and loaded without errors
        if (verse == currentState.selectedVerse &&
            currentState.scriptureText.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiResponseText.isNotEmpty() || currentState.aiResponseError != null || !geminiReady)
        ) {
            Log.d("NewVerseViewModel", "Verse $verse already loaded. Skipping fetch.")
            if (geminiReady && currentState.aiResponseText.isEmpty() && currentState.aiResponseError != null && !currentState.aiResponseLoading) {
                Log.d("NewVerseViewModel", "Retrying AI response fetch for $verse.")
                fetchKeyTakeAwayOnly(verse)
            }
            return
        }

        fetchDataJob?.cancel()

        _state.update {
            it.copy(
                selectedVerse = verse,
                isScriptureLoading = true,
                aiResponseLoading = geminiReady,
                scriptureText = "Fetching Scripture...",
                aiResponseText = if (geminiReady) "Fetching insights from AI..." else (geminiInitError ?: "AI Service not ready."),
                scriptureError = null,
                aiResponseError = if (geminiReady) null else geminiInitError,
                generalError = if (geminiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || geminiInitError?.contains("API Key missing", ignoreCase = true) == true) geminiInitError else null,
                isContentSaved = false,
            )
        }

        fetchDataJob = viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()

            if (!geminiReady) {
                _state.update {
                    it.copy(
                        aiResponseLoading = false,
                        aiResponseError = it.aiResponseError ?: "Cannot fetch data as AI service is not ready."
                    )
                }
                return@launch
            }

            when (val scriptureResult = geminiService.fetchScriptureJson(verse, translation)) {
                is AiServiceResult.Success -> {
                    val scriptureContent = scriptureResult.data
                    _state.update {
                        it.copy(
                            isScriptureLoading = false,
                            scriptureText = scriptureContentToString(scriptureContent),
                            translation = translation,
                            scriptureJson = scriptureContent
                        )
                    }
                }

                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            isScriptureLoading = false,
                            scriptureText = "",
                            scriptureError = scriptureResult.message
                        )
                    }
                }
            }

            val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"
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
                            aiResponseText = "",
                            aiResponseError = takeAwayResult.message
                        )
                    }
                }
            }
        }
    }

    private fun fetchKeyTakeAwayOnly(verse: BibleVerseRef) {
        updateAiServiceStatus() // Refresh AI status
        if (!_state.value.isAiServiceReady) {
            Log.w("NewVerseViewModel", "Skipping take-away retry as GeminiAIService is not initialized/configured.")
            _state.update {
                it.copy(
                    aiResponseLoading = false,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() ?: "AI Service not ready."
                )
            }
            return
        }

        _state.update { it.copy(aiResponseLoading = true, aiResponseError = null, aiResponseText = "Getting key take-away...") }

        val verseRef = "${verse.book} ${verse.chapter}:${verse.startVerse}-${verse.endVerse}"

        viewModelScope.launch {
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
                            aiResponseText = "",
                            aiResponseError = takeAwayResult.message
                        )
                    }
                }
            }
        }
    }

    // Call this method if AI settings might have changed externally (e.g. from settings screen)
    // to re-evaluate the AI service status.
    fun refreshAiServiceStatus() {
        updateAiServiceStatus()
        // If a verse is selected and AI was previously not ready, but now is, consider re-fetching AI data.
        val currentSt = _state.value
        if (currentSt.selectedVerse != null && currentSt.isAiServiceReady && currentSt.aiResponseText.isBlank() && currentSt.aiResponseError != null) {
            Log.d("NewVerseViewModel", "AI service is now ready. Attempting to fetch AI data for ${currentSt.selectedVerse.book} ${currentSt.selectedVerse.chapter}:${currentSt.selectedVerse.startVerse}")
            fetchKeyTakeAwayOnly(currentSt.selectedVerse)
        }
    }


    fun resetNavigationState() {
        _state.update {
            it.copy(newlySavedVerseId = null, isContentSaved = false)
        }
    }
}
