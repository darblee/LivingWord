package com.darblee.livingword.ui.viewmodels

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.AIService
import com.darblee.livingword.data.verseReferenceBibleVerseRef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        SCRIPTURE_READY,
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
    private val aiService = AIService // Singleton instance

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
            Toast.makeText(getApplication(), "Verse saved", Toast.LENGTH_SHORT).show()
        }
    }

    fun scriptureSavedSuccessfully(newVerseId: Long) {
        _state.update {
            it.copy(
                newlySavedVerseId = newVerseId,
                isContentSaved = false // Still processing AI
            )
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
        val aiReady = _state.value.isAiServiceReady
        val aiInitError = _state.value.aiResponseError ?: _state.value.generalError

        val currentState = _state.value

        // Avoid re-fetching if the exact same verse is already selected and loaded without errors
        if (verse == currentState.selectedVerse &&
            currentState.scriptureVerses.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiResponseText.isNotEmpty() || currentState.aiResponseError != null || !aiReady)
        ) {
            Log.d("NewVerseViewModel", "Verse $verse already loaded. Skipping fetch.")
            if (aiReady && currentState.aiResponseText.isEmpty() && currentState.aiResponseError != null &&
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
                loadingStage = if (aiReady) LoadingStage.FETCHING_SCRIPTURE else LoadingStage.NONE,
                aiResponseText = if (aiReady) "Starting process..." else (aiInitError ?: "AI Service not ready."),
                scriptureError = null,
                aiResponseError = if (aiReady) null else aiInitError,
                generalError = if (aiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || aiInitError?.contains("API Key missing", ignoreCase = true) == true) aiInitError else null,
                isContentSaved = false,
            )
        }

        if (!aiReady) return

        fetchDataJob = viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()

            // STAGE 1: Fetch Scripture
            _state.update { it.copy(loadingStage = LoadingStage.FETCHING_SCRIPTURE) }
            when (val scriptureResult = aiService.fetchScripture(verse, translation)) {
                is AiServiceResult.Success -> {
                    _state.update { it.copy(
                        scriptureVerses = scriptureResult.data,
                        translation = translation,
                        loadingStage = LoadingStage.SCRIPTURE_READY
                        )
                    }
                    
                    // Give UI time to process SCRIPTURE_READY state and trigger early save
                    delay(100) // Small delay to allow LaunchedEffect to trigger
                    
                    // Stop here - AI takeaway will be handled in VerseDetailScreen
                    _state.update { it.copy(loadingStage = LoadingStage.NONE) }
                    return@launch
                }
                is AiServiceResult.Error -> {
                    _state.update { it.copy(loadingStage = LoadingStage.NONE, scriptureError = scriptureResult.message) }
                    return@launch // Stop the process on error
                }
            }
        }
    }

    private fun fetchKeyTakeAwayOnly(verse: BibleVerseRef) {
        updateAiServiceStatus() // Refresh AI status
        if (!_state.value.isAiServiceReady) {
            Log.w("NewVerseViewModel", "Skipping take-away retry as AIService is not initialized/configured.")
            _state.update {
                it.copy(
                    loadingStage = LoadingStage.NONE,
                    aiResponseError = it.aiResponseError ?: aiService.getInitializationError() ?: "AI Service not ready."
                )
            }
            return
        }

        _state.update { it.copy(loadingStage = LoadingStage.FETCHING_TAKEAWAY, aiResponseError = null, aiResponseText = "Getting key take-away...") }

        val verseRef = verseReferenceBibleVerseRef(verse)

        viewModelScope.launch {
            when (val takeAwayResult = aiService.getKeyTakeaway(verseRef)) {
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

    fun resetNavigationState() {
        Log.d("NewVerseViewModel", "=== RESET NAVIGATION STATE CALLED ===")
        val currentState = _state.value
        Log.d("NewVerseViewModel", "State before reset:")
        Log.d("NewVerseViewModel", "  - newlySavedVerseId: ${currentState.newlySavedVerseId}")
        Log.d("NewVerseViewModel", "  - isContentSaved: ${currentState.isContentSaved}")
        Log.d("NewVerseViewModel", "  - loadingStage: ${currentState.loadingStage}")
        
        _state.update {
            it.copy(newlySavedVerseId = null, isContentSaved = false)
        }
        
        val newState = _state.value
        Log.d("NewVerseViewModel", "State after reset:")
        Log.d("NewVerseViewModel", "  - newlySavedVerseId: ${newState.newlySavedVerseId}")
        Log.d("NewVerseViewModel", "  - isContentSaved: ${newState.isContentSaved}")
        Log.d("NewVerseViewModel", "  - loadingStage: ${newState.loadingStage}")
    }
}