package com.darblee.livingword.ui.screens

import android.app.Application
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AddVerseByDescriptionViewModel (application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AddVerseByDescriptionUiState())
    val uiState: StateFlow<AddVerseByDescriptionUiState> = _uiState.asStateFlow()
    private val preferenceStore = PreferenceStore(application)

    fun onDescriptionChange(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun onVerseSelected(verse: BibleVerseRef) {
        _uiState.value = _uiState.value.copy(selectedVerse = verse)
    }

    fun onPreviewVerse(verse: BibleVerseRef) {
        viewModelScope.launch {
            val translation = preferenceStore.readTranslationFromSetting()

            _uiState.value = _uiState.value.copy(
                isPreviewLoading = true,
                previewVerse = verse,
                previewError = null
            )
            when (val result = GeminiAIService.fetchScripture(verse, translation)) {
                is AiServiceResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewError = result.message
                    )
                }
                is AiServiceResult.Success<*> -> {
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        previewContent = result.data.toString(),
                        translation = translation
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
                GeminiAIService.getNewVersesBasedOnDescription(_uiState.value.description)) {
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
    val isPreviewLoading: Boolean = false,
    val previewError: String? = null,
    val translation: String = ""
)

@Composable
fun AddVerseByDescriptionScreen(
    navController: NavController,
    viewModel: AddVerseByDescriptionViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.previewVerse != null) {
        ScripturePreviewDialog(
            verseRef = uiState.previewVerse!!,
            content = uiState.previewContent,
            isLoading = uiState.isPreviewLoading,
            error = uiState.previewError,
            onDismiss = { viewModel.dismissPreview() },
            translation = uiState.translation
        )
    }

    AppScaffold(
        title = { Text("Add Verse By Description") },
        navController = navController,
        currentScreenInstance = Screen.AddVerseByDescriptionScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = uiState.description,
                    onValueChange = { viewModel.onDescriptionChange(it) },
                    label = { Text("Enter a description") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.getVerses() },
                    enabled = uiState.description.isNotBlank() && !uiState.loading
                ) {
                    Text("Find Verses")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (uiState.loading) {
                    CircularProgressIndicator()
                }

                uiState.error?.let {
                    Text("Unable find verse", color = MaterialTheme.colorScheme.error)
                    Log.i("AddVerseBYDescriptionScreen", "Could not get verse. Error message: $it")
                }

                if (uiState.verses.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(uiState.verses) { verse ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.onVerseSelected(verse) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.selectedVerse == verse,
                                        onClick = { viewModel.onVerseSelected(verse) }
                                    )
                                    Text(
                                        text = "${verse.book} ${verse.chapter}:${verse.startVerse}" +
                                                if (verse.endVerse > verse.startVerse) "-${verse.endVerse}" else "",
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 8.dp),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Button(onClick = { viewModel.onPreviewVerse(verse) }) {
                                        Text("Preview")
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                uiState.selectedVerse?.let { verse ->
                                    val resultJson = Json.encodeToString(verse)
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set(VERSE_RESULT_KEY, resultJson)
                                    navController.popBackStack()
                                }
                            },
                            enabled = uiState.selectedVerse != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select this one")
                        }
                    }

                } else if (!uiState.loading && uiState.description.isNotBlank() && uiState.verses.isEmpty() && uiState.error == null) {
                    Text("No verses found for the given description.")
                }
            }
        }
    )
}

@Composable
fun ScripturePreviewDialog(
    verseRef: BibleVerseRef,
    content: String?,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    translation: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse} ($translation)") },
        text = {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Loading preview...")
                    }
                }
                error != null -> {
                    Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
                }
                content != null -> {
                    Text(text = content, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
