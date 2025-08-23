package com.darblee.livingword.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.AIService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddVerseByDescriptionViewModel (application: Application) : AndroidViewModel(application) {

    private val hybridService = AIService // Singleton instance

    private val _uiState = MutableStateFlow(AddVerseByDescriptionUiState())
    val uiState: StateFlow<AddVerseByDescriptionUiState> = _uiState.asStateFlow()
    private val preferenceStore = PreferenceStore(application)

    fun onDescriptionChange(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onVerseSelected(verse: BibleVerseRef) {
        _uiState.value = _uiState.value.copy(selectedVerse = verse)
    }

    fun onPreviewVerseJson(verse: BibleVerseRef) {
        viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()

            _uiState.value = _uiState.value.copy(
                isPreviewLoading = true,
                previewVerse = verse,
                previewError = null,
                translation = translation
            )
            when (val result = hybridService.fetchScripture(verse, translation)) {

                is AiServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewError = result.message
                    )
                }
                is AiServiceResult.Success<List<Verse>> -> {
                    val scriptureVerses = result.data
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewScriptureVerses = scriptureVerses,
                        translation = translation
                    )
                }
            }
        }
    }

    fun onPreviewTranslationChange(newTranslation: String) {
        val currentVerseRef = _uiState.value.previewVerse ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isPreviewLoading = true,
                previewError = null,
                translation = newTranslation,
                previewScriptureVerses = emptyList()
            )

            when (val result = hybridService.fetchScripture(currentVerseRef, newTranslation)) {
                is AiServiceResult.Success<List<Verse>> -> {
                    val scriptureVerses = result.data
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewScriptureVerses = scriptureVerses
                    )
                }
                is AiServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewError = result.message
                    )
                }
            }
        }
    }

    fun dismissPreview() {
        _uiState.value = _uiState.value.copy(
            previewVerse = null,
            previewContent = null,
            previewError = null,
            isPreviewLoading = false
        )
    }

    fun getVerses() {
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(loading = true, error = null, selectedVerse = null)
            when (val result =
                AIService.getNewVersesBasedOnDescription(_uiState.value.description)) {
                is AiServiceResult.Success -> {
                    _uiState.value = _uiState.value.copy(loading = false, verses = result.data)
                }

                is AiServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(loading = false, error = result.message)
                }
            }
        }
    }
}

data class AddVerseByDescriptionUiState(
    val description: String = "",
    val loading: Boolean = false,
    val verses: List<BibleVerseRef> = emptyList(),
    val selectedVerse: BibleVerseRef? = null,
    val error: String? = null,
    val previewVerse: BibleVerseRef? = null,
    val previewContent: String? = null,
    val previewScriptureVerses: List<Verse> = emptyList(),
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val translation: String = ""
)