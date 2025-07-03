package com.darblee.livingword.domain.model

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.Long

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    enum class LoadingStage {
        NONE,
        FETCHING_SCRIPTURE,
        FETCHING_TAKEAWAY,
        VALIDATING_TAKEAWAY
    }

    data class HomeViewModelState(
        val selectedVOTD: BibleVerseRef? = null,
        val scriptureVerses: List<Verse> = emptyList(),
        val aiTakeAwayText: String = "",
        val loadingStage: LoadingStage = LoadingStage.NONE,
        val scriptureError: String? = null,
        val aiResponseError: String? = null,
        val generalError: String? = null,
        val isContentSaved: Boolean = false,
        val isLoading: Boolean = false,
        val isAiServiceReady: Boolean = false,
        val translation: String = "",
        val newlySavedVerseId: Long? = null,
        val isVotdAddInitiated: Boolean = false
    )

    private val _state = MutableStateFlow(HomeViewModelState())
    val state: StateFlow<HomeViewModelState> = _state.asStateFlow()

    private var fetchDataJob: Job? = null

    private val preferenceStore = PreferenceStore(application)
    private val geminiService = GeminiAIService // Singleton instance

    init {
        updateAiServiceStatus()
    }

    fun setSelectedVerseAndFetchData(verse: BibleVerseRef?) {
        if (verse == null) {
            clearVerseData()
            return
        }
        Log.i("HomeViewModel", "setSelectedVerseAndFetchData: $verse")

        updateAiServiceStatus()
        val geminiReady = _state.value.isAiServiceReady
        val geminiInitError = _state.value.aiResponseError ?: _state.value.generalError

        val currentState = _state.value

        if (verse == currentState.selectedVOTD &&
            currentState.scriptureVerses.isNotEmpty() && currentState.scriptureError == null &&
            (currentState.aiTakeAwayText.isNotEmpty() || currentState.aiResponseError != null || !geminiReady)
        ) {
            Log.d("HomeViewModel", "Verse $verse already loaded. Skipping fetch.")
            if (geminiReady && currentState.aiTakeAwayText.isEmpty() && currentState.aiResponseError != null &&
                currentState.loadingStage != LoadingStage.FETCHING_TAKEAWAY && currentState.loadingStage != LoadingStage.VALIDATING_TAKEAWAY
            ) {
                Log.d("HomeViewModel", "Retrying AI response fetch for $verse.")
                fetchKeyTakeAwayOnly(verse)
            }
            return
        }

        fetchDataJob?.cancel()

        Log.d("HomeViewModel", "Before initial state update. Current loadingStage: ${_state.value.loadingStage}")
        _state.update {
            val newLoadingStage = if (geminiReady) LoadingStage.FETCHING_SCRIPTURE else LoadingStage.NONE
            Log.i("HomeViewModel", "Updating state for $verse. Setting loadingStage to $newLoadingStage")
            it.copy(
                selectedVOTD = verse,
                loadingStage = newLoadingStage,
                aiTakeAwayText = if (geminiReady) "Starting process..." else (geminiInitError ?: "AI Service not ready."),
                scriptureError = null,
                aiResponseError = if (geminiReady) null else geminiInitError,
                generalError = if (geminiInitError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || geminiInitError?.contains("API Key missing", ignoreCase = true) == true) "initError" else null,
                isContentSaved = false,
                isVotdAddInitiated = true
            )
        }
        Log.d("HomeViewModel", "After initial state update. New loadingStage: ${_state.value.loadingStage}")

        if (!geminiReady) return


        fetchDataJob = viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()
            delay(100)


            when (val scriptureResult = geminiService.fetchScripture(verse, translation)) {
                is AiServiceResult.Success -> {
                    Log.i("HomeViewModel", "Fetched scripture for $verse")
                    _state.update { it.copy(
                        scriptureVerses = scriptureResult.data,
                        translation = translation
                        )
                    }
                }
                is AiServiceResult.Error -> {
                    Log.d("HomeViewModel", "Before updating to NONE (scripture error). Current loadingStage: ${_state.value.loadingStage}")
                    _state.update { it.copy(loadingStage = LoadingStage.NONE, scriptureError = scriptureResult.message, isVotdAddInitiated = false) }
                    Log.d("HomeViewModel", "After updating to NONE (scripture error). New loadingStage: ${_state.value.loadingStage}")
                    return@launch
                }
            }

            // STAGE 2: Get Key Takeaway
            Log.d("HomeViewModel", "Before updating to FETCHING_TAKEAWAY. Current loadingStage: ${_state.value.loadingStage}")
            _state.update { it.copy(loadingStage = LoadingStage.FETCHING_TAKEAWAY, isVotdAddInitiated = true) }
            Log.d("HomeViewModel", "After updating to FETCHING_TAKEAWAY. New loadingStage: ${_state.value.loadingStage}")
            val verseRef = verseReferenceBibleVerseRef(verse)

            delay(100)

            when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                is AiServiceResult.Success -> {
                    val takeawayResponseText = takeAwayResult.data

                    // STAGE 3: Validate Takeaway
                    Log.d("HomeViewModel", "Before updating to VALIDATING_TAKEAWAY. Current loadingStage: ${_state.value.loadingStage}")
                    _state.update { it.copy(loadingStage = LoadingStage.VALIDATING_TAKEAWAY) }
                    Log.d("HomeViewModel", "After updating to VALIDATING_TAKEAWAY. New loadingStage: ${_state.value.loadingStage}")
                    when (val validationResult = GeminiAIService.validateKeyTakeawayResponse(verseRef, takeawayResponseText)) {
                        is AiServiceResult.Success -> {
                            if (validationResult.data) {
                                Log.i("HomeViewModel", "Takeaway for $verseRef is acceptable: $takeawayResponseText")
                                Log.d("HomeViewModel", "Before updating to NONE (acceptable). Current loadingStage: ${_state.value.loadingStage}")
                                _state.update {
                                    it.copy(
                                        loadingStage = LoadingStage.NONE,
                                        aiTakeAwayText = takeawayResponseText,
                                        aiResponseError = null,
                                    )
                                }
                                Log.d("HomeViewModel", "After updating to NONE (acceptable). New loadingStage: ${_state.value.loadingStage}")
                            } else {
                                Log.w("HomeViewModel", "Takeaway for $verseRef was rejected.")
                                Log.d("HomeViewModel", "Before updating to NONE (rejected). Current loadingStage: ${_state.value.loadingStage}")
                                _state.update {
                                    it.copy(
                                        loadingStage = LoadingStage.NONE,
                                        aiResponseError = "The AI-generated insight was rejected by the validator. Please try again.",
                                        isVotdAddInitiated = false
                                    )
                                }
                                Log.d("HomeViewModel", "After updating to NONE (rejected). New loadingStage: ${_state.value.loadingStage}")
                            }
                        }
                        is AiServiceResult.Error -> {
                            Log.e("MyApp", "Validation failed: ${validationResult.message}")
                            Log.d("HomeViewModel", "Before updating to NONE (validation error). Current loadingStage: ${_state.value.loadingStage}")
                            _state.update { it.copy(loadingStage = LoadingStage.NONE, aiResponseError = "Validation failed: ${validationResult.message}", isVotdAddInitiated = false) }
                            Log.d("HomeViewModel", "After updating to NONE (validation error). New loadingStage: ${_state.value.loadingStage}")
                        }
                    }
                }
                is AiServiceResult.Error -> {
                    Log.d("HomeViewModel", "Before updating to NONE (takeaway error). Current loadingStage: ${_state.value.loadingStage}")
                    _state.update { it.copy(loadingStage = LoadingStage.NONE, aiResponseError = takeAwayResult.message, isVotdAddInitiated = false) }
                    Log.d("HomeViewModel", "After updating to NONE (takeaway error). New loadingStage: ${_state.value.loadingStage}")
                }
            }
            }
        }

    private fun updateAiServiceStatus() {
        val isReady = geminiService.isInitialized()
        val initError = if (!isReady) geminiService.getInitializationError() else null
        _state.update {
            it.copy(
                isAiServiceReady = isReady,
                aiResponseError = if (it.aiResponseError == null && initError != null) initError else it.aiResponseError,
                generalError = if (initError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || initError?.contains("API Key missing", ignoreCase = true) == true) initError else null
            )
        }
    }

    private fun fetchKeyTakeAwayOnly(verse: BibleVerseRef) {
        updateAiServiceStatus()
        if (!_state.value.isAiServiceReady) {
            Log.w("HomeViewModel", "Skipping take-away retry as GeminiAIService is not initialized/configured.")
            _state.update {
                it.copy(
                    loadingStage = LoadingStage.NONE,
                    aiResponseError = it.aiResponseError ?: geminiService.getInitializationError() ?: "AI Service not ready."
                )
            }
            return
        }

        Log.d("HomeViewModel", "Before updating to FETCHING_TAKEAWAY (fetchKeyTakeAwayOnly). Current loadingStage: ${_state.value.loadingStage}")
        _state.update { it.copy(loadingStage = LoadingStage.FETCHING_TAKEAWAY, aiResponseError = null, aiTakeAwayText = "Getting key take-away...") }
        Log.d("HomeViewModel", "After updating to FETCHING_TAKEAWAY (fetchKeyTakeAwayOnly). New loadingStage: ${_state.value.loadingStage}")

        val verseRef = verseReferenceBibleVerseRef(verse)

        viewModelScope.launch {
            when (val takeAwayResult = geminiService.getKeyTakeaway(verseRef)) {
                is AiServiceResult.Success -> {
                    Log.d("HomeViewModel", "Before updating to NONE (fetchKeyTakeAwayOnly success). Current loadingStage: ${_state.value.loadingStage}")
                    _state.update {
                        it.copy(
                            loadingStage = LoadingStage.NONE,
                            aiTakeAwayText = takeAwayResult.data,
                            aiResponseError = null,
                            isVotdAddInitiated = false
                        )
                    }
                    Log.d("HomeViewModel", "After updating to NONE (fetchKeyTakeAwayOnly success). New loadingStage: ${_state.value.loadingStage}")
                }
                is AiServiceResult.Error -> {
                    Log.d("HomeViewModel", "Before updating to NONE (fetchKeyTakeAwayOnly error). Current loadingStage: ${_state.value.loadingStage}")
                    _state.update {
                        it.copy(
                            loadingStage = LoadingStage.NONE,
                            aiTakeAwayText = "",
                            aiResponseError = takeAwayResult.message,
                            isVotdAddInitiated = false
                        )
                    }
                    Log.d("HomeViewModel", "After updating to NONE (fetchKeyTakeAwayOnly error). New loadingStage: ${_state.value.loadingStage}")
                }
            }
        }
    }

    fun clearVerseData() {
        fetchDataJob?.cancel()
        updateAiServiceStatus()
        val currentAiStatus = _state.value
        Log.d("HomeViewModel", "Before clearVerseData update. Current loadingStage: ${_state.value.loadingStage}")
        _state.update {
            it.copy(
                selectedVOTD = null,
                aiTakeAwayText = "",
                loadingStage = LoadingStage.NONE,
                scriptureVerses = emptyList(),
                scriptureError = null,
                aiResponseError = currentAiStatus.aiResponseError,
                generalError = currentAiStatus.generalError,
                isContentSaved = false,
                isVotdAddInitiated = false,
                newlySavedVerseId = null,
                )
        }
        Log.d("HomeViewModel", "After clearVerseData update. New loadingStage: ${_state.value.loadingStage}")
    }

    fun resetNavigationState() {
        _state.update {
            it.copy(
                isContentSaved = false,
                isVotdAddInitiated = false,
                selectedVOTD = null,
                scriptureVerses = emptyList(),
                aiTakeAwayText = "",
                newlySavedVerseId = null,
            )
        }
    }

    fun contentSavedSuccessfully(newVerseId: Long) {
        _state.update {
            it.copy(
                isContentSaved = true,
                isLoading = false,
                isVotdAddInitiated = false,
                loadingStage = LoadingStage.NONE,
                newlySavedVerseId = newVerseId,
                )
        }

        viewModelScope.launch {
            SnackBarController.showMessage("Verse is added")
        }
    }

    fun updateGeneralError(message: String) {
        _state.update {
            it.copy(
                generalError = message,
                isLoading = false,
                isVotdAddInitiated = false
            )
        }
    }
}
