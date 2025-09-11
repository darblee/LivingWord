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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    private val aIService = AIService // Singleton instance

    init {
        updateAiServiceStatus()
    }

    fun addVOTDToDB(
        verse: BibleVerseRef?,
        verseContent: List<Verse>,
        translation: String
    ) {
        _state.update {
            it.copy(
                selectedVOTD = verse,
                scriptureVerses = verseContent,
                translation = translation,
                loadingStage = LoadingStage.NONE,
                aiTakeAwayText = "",
                aiResponseError = null,
                isContentSaved = false,
                isVotdAddInitiated = true
            )
        }
    }

    private fun updateAiServiceStatus() {
        val isReady = aIService.isInitialized()
        val initError = if (!isReady) aIService.getInitializationError() else null
        _state.update {
            it.copy(
                isAiServiceReady = isReady,
                aiResponseError = if (it.aiResponseError == null && initError != null) initError else it.aiResponseError,
                generalError = if (initError?.contains("Failed to initialize AI Model", ignoreCase = true) == true || initError?.contains("API Key missing", ignoreCase = true) == true) initError else null
            )
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
            Toast.makeText(getApplication(), "Verse is added", Toast.LENGTH_SHORT).show()
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