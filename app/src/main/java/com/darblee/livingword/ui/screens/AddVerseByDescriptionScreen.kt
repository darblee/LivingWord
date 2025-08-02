package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.darblee.livingword.Global
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.Verse
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.ui.viewmodels.AddVerseByDescriptionViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json


@Composable
fun AddVerseByDescriptionScreen(
    navController: NavController,
    viewModel: AddVerseByDescriptionViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption
) {
    val context = LocalContext.current
    val preferenceStore = remember { PreferenceStore(context) }

    val uiState by viewModel.uiState.collectAsState()

    if (uiState.previewVerse != null) {
        ScripturePreviewDialogJson(
            verseRef = uiState.previewVerse!!,
            scriptureVerses = uiState.previewScriptureVerses,
            isLoading = uiState.isPreviewLoading,
            error = uiState.previewError,
            onDismiss = { viewModel.dismissPreview() },
            translation = uiState.translation,
            onTranslationSelected = { viewModel.onPreviewTranslationChange(it) },
            preferenceStore
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
                    Log.i("AddVerseByDescriptionScreen", "Could not get verse. Error message: $it")
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
                                    Button(onClick = { viewModel.onPreviewVerseJson(verse) }) {
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
fun ScripturePreviewDialogJson(
    verseRef: BibleVerseRef,
    scriptureVerses: List<Verse>,
    isLoading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    translation: String,
    onTranslationSelected: (String) -> Unit,
    preferenceStore: PreferenceStore
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}-${verseRef.endVerse}") },
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
                        Text("Fetching scripture...")
                    }
                }
                error != null -> {
                    Text(text = "Error: $error", color = MaterialTheme.colorScheme.error)
                }
                scriptureVerses.isNotEmpty() -> {
                    Column {
                        var expanded by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "Scripture",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 0.dp),
                                fontSize = 15.sp
                            )
                            Box {
                                TextButton(
                                    onClick = { expanded = true },
                                    shape = RoundedCornerShape(0.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier.heightIn(max = 24.dp).widthIn(max = 40.dp)
                                ) {
                                    Text(
                                        text = "($translation)",
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal)
                                }

                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    Global.bibleTranslations.forEach { translationItem ->
                                        DropdownMenuItem(
                                            text = { Text(translationItem) },
                                            onClick = {
                                                scope.launch {
                                                    preferenceStore.saveTranslationToSetting(
                                                        translation
                                                    )
                                                }
                                                onTranslationSelected(translationItem)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Re-implementing the Box from LabeledOutlinedBox to maintain style
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .border(
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            val annotatedVerse = buildAnnotatedVerseString(scriptureVerses)
                            Text(
                                text = annotatedVerse,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
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

fun buildAnnotatedVerseString(verses: List<Verse>) : AnnotatedString  {
    return buildAnnotatedString {
        verses.forEach { verse ->
            // Style for the verse number (superscript, red, small font)
            withStyle(
                style = SpanStyle(
                    color = Color.Red,
                    fontSize = 10.sp,
                    baselineShift = BaselineShift.Superscript
                )
            ) {
                append(verse.verseNum.toString())
            }
            // Add a space after the verse number.
            append(" ")

            // The verse content itself. It will inherit the style from the Text composable.
            append(verse.verseString)

            // Add a space after the verse content to separate it from the next verse.
            append(" ")
        }
    }
}