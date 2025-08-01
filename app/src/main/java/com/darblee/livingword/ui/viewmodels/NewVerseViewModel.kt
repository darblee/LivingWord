package com.darblee.livingword.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import com.darblee.livingword.data.verseReferenceBibleVerseRef
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

    enum class LoadingStage {
        NONE,
        FETCHING_SCRIPTURE,
        FETCHING_TAKEAWAY,
        VALIDATING_TAKEAWAY
    }

    /**
     * Represents the UI state for the LearnScreen.
     */
    data class NewVerseScreenState(
        val selectedTopics: List<String> = emptyList(),
        val isTopicContentLoading: Boolean = false,

        val selectedVerse: BibleVerseRef? = null,
        val scriptureVerses: List<Verse> = emptyList(),
        val aiResponseText: String = "",
        val loadingStage: LoadingStage = LoadingStage.NONE,
        val scriptureError: String? = null,
        val aiResponseError: String? = null,
        val generalError: String? = null,
        val isContentSaved: Boolean = false,
        val newlySavedVerseId: Long? = null,
        val isLoading: Boolean = false,
        val isAiServiceReady: Boolean = false,
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
                scriptureVerses = emptyList(),
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
                aiResponseText = "",
                selectedTopics = emptyList(),
                loadingStage = LoadingStage.NONE,
                scriptureVerses = emptyList(),
                scriptureError = null,
                aiResponseError = currentAiStatus.aiResponseError,
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
            currentState.scriptureVerses.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiResponseText.isNotEmpty() || currentState.aiResponseError != null || !geminiReady)
        ) {
            Log.d("NewVerseViewModel", "Verse $verse already loaded. Skipping fetch.")
            if (geminiReady && currentState.aiResponseText.isEmpty() && currentState.aiResponseError != null &&
                currentState.loadingStage != LoadingStage.FETCHING_TAKEAWAY && currentState.loadingStage != LoadingStage.VALIDATING_TAKEAWAY
            ) {
                Log.d("NewVerseViewModel", "Retrying AI response fetch for $verse.")
                fetchKeyTakeAwayOnly(verse)
            }
            return
        }

        fetchDataJob?.cancel()

        _state.update {
            it.copy(
                selectedVerse = verse,
                loadingStage = if (geminiReady) LoadingStage.FETCHING_SCRIPTURE else LoadingStage.NONE,
                aiResponseText = if (geminiReady) "Starting process..." else (geminiInitError ?: "AI Service not ready."),
                scriptureError = null,
                aiResponseError = if (geminiReady) null else geminiInitError,
                generalError = if (geminiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || geminiInitError?.contains("API Key missing", ignoreCase = true) == true) geminiInitError else null,
                isContentSaved = false,
            )
        }

        if (!geminiReady) return

        fetchDataJob = viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()

            // STAGE 1: Fetch Scripture
            _state.update { it.copy(loadingStage = LoadingStage.FETCHING_SCRIPTURE) }
            when (val scriptureResult = geminiService.fetchScripture(verse, translation)) {
                is AiServiceResult.Success -> {
                    _state.update { it.copy(
                        scriptureVerses = scriptureResult.data,
                        translation = translation
                        )
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update { it.copy(loadingStage = LoadingStage.NONE, scriptureError = scriptureResult.message) }
                    return@launch // Stop the process on error
                }
            }

            // STAGE 2: Get Key Takeaway
            _state.update { it.copy(loadingStage = LoadingStage.FETCHING_TAKEAWAY) }
            val verseRef = verseReferenceBibleVerseRef(verse)
            when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                is AiServiceResult.Success -> {
                    val takeawayResponseText = takeAwayResult.data

                    // STAGE 3: Validate Takeaway
                    _state.update { it.copy(loadingStage = LoadingStage.VALIDATING_TAKEAWAY) }
                    when (val validationResult = GeminiAIService.validateKeyTakeawayResponse(verseRef, takeawayResponseText)) {
                        is AiServiceResult.Success -> {
                            if (validationResult.data) {
                                Log.i("NewVerse", "Takeaway for $verseRef is acceptable: $takeawayResponseText")
                                _state.update {
                                    it.copy(
                                        loadingStage = LoadingStage.NONE,
                                        aiResponseText = takeawayResponseText,
                                        aiResponseError = null
                                    )
                                }
                            } else {
                                Log.w("NewVerse", "Takeaway for $verseRef was rejected.")
                                _state.update {
                                    it.copy(
                                        loadingStage = LoadingStage.NONE,
                                        aiResponseError = "The AI-generated insight was rejected by the validator. Please try again."
                                    )
                                }
                            }
                        }
                        is AiServiceResult.Error -> {
                            Log.e("MyApp", "Validation failed: ${validationResult.message}")
                            _state.update { it.copy(loadingStage = LoadingStage.NONE, aiResponseError = "Validation failed: ${validationResult.message}") }
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update { it.copy(loadingStage = LoadingStage.NONE, aiResponseError = takeAwayResult.message) }
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
                    loadingStage = LoadingStage.NONE,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() ?: "AI Service not ready."
                )
            }
            return
        }

        _state.update { it.copy(loadingStage = LoadingStage.FETCHING_TAKEAWAY, aiResponseError = null, aiResponseText = "Getting key take-away...") }

        val verseRef = verseReferenceBibleVerseRef(verse)

        viewModelScope.launch {
            when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                is AiServiceResult.Success -> {
                    _state.update {
                        it.copy(
                            loadingStage = LoadingStage.NONE,
                            aiResponseText = takeAwayResult.data,
                            aiResponseError = null
                        )
                    }
                }
                is AiServiceResult.Error -> {
                    _state.update {
                        it.copy(
                            loadingStage = LoadingStage.NONE,
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