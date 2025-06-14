package com.darblee.livingword.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.NewVerseViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVersesScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {
    val context = LocalContext.current

    val allVerses by bibleViewModel.allVerses.collectAsState()

    var savingComplete by remember { mutableStateOf(false) }
    var showRetreivingDataDialog by remember { mutableStateOf(false) }

    // Use LaunchedEffect tied to lifecycle to observe results from SavedStateHandle
    // --- Handle Navigation Results ---
    val newVerseViewModel: NewVerseViewModel = viewModel()
    val newVerseState by newVerseViewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedStateHandle = currentBackStackEntry?.savedStateHandle

    var showExistingVerseDialog by remember { mutableStateOf(false) }
    var existingVerseForDialog by remember { mutableStateOf<BibleVerse?>(null) }

    LaunchedEffect(savedStateHandle, lifecycleOwner.lifecycle) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Observe Verse Result
            launch {
                savedStateHandle?.getStateFlow<String?>(VERSE_RESULT_KEY, null)
                    ?.filterNotNull() // Process only non-null results
                    ?.collect { resultJson ->
                        Log.d("AllVerseScreen", "Received verse JSON: $resultJson")
                        try {
                            val selectedVerseRef = Json.decodeFromString<BibleVerseRef>(resultJson)
                            Log.d("AllVerseScreen", "Successfully deserialized: $selectedVerseRef")

                            // Check if the verse exists in the database
                            val existingVerse = bibleViewModel.findExistingVerse(
                                book = selectedVerseRef.book,
                                chapter = selectedVerseRef.chapter,
                                startVerse = selectedVerseRef.startVerse
                            )

                            if (existingVerse != null) {
                                Log.i(
                                    "AllVerseScreen",
                                    "Verse ${existingVerse.book} ${existingVerse.chapter}:${existingVerse.startVerse} already exists in DB with ID: ${existingVerse.id}."
                                )

                                existingVerseForDialog = existingVerse // Store for dialog
                                showExistingVerseDialog = true

                                // Navigation to VerseDetail will happen when dialog is dismissed
                            } else {
                                // Verse does not exist, proceed with fetching take-away
                                Log.i(
                                    "AllVerseScreen",
                                    "Verse ${selectedVerseRef.book} ${selectedVerseRef.chapter}:${selectedVerseRef.startVerse} not found in DB. Fetching new data."
                                )

                                showRetreivingDataDialog = true
                                newVerseViewModel.setSelectedVerseAndFetchData(selectedVerseRef)

                                // Now ask user to select the topic

                                Log.i("AllVerseScreen", "Start to fetch the take-away info. Book = ${selectedVerseRef.book}, chapter = ${selectedVerseRef.chapter}")
                            }

                        } catch (e: Exception) {
                            Log.e("NewVerseScreen", "Error deserializing BibleVerse: ${e.message}", e)
                            newVerseViewModel.setSelectedVerseAndFetchData(null) // Clear data on error
                        } finally {
                            // Important: Remove the result from SavedStateHandle to prevent reprocessing
                            Log.d("NewVerseScreen", "Removing key $VERSE_RESULT_KEY from SavedStateHandle")
                            savedStateHandle.remove<String>(VERSE_RESULT_KEY)
                        }
                    }
            }
        }
    }
    // --- End Handle Navigation Results ---

    LaunchedEffect(newVerseState) {
        if (readyToSave(newVerseState) && !newVerseState.isContentSaved) {
            bibleViewModel.saveNewVerse(
                verse = (newVerseState.selectedVerse!!),
                scripture = newVerseState.scriptureText,
                aiResponse = newVerseState.aiResponseText,
                topics = newVerseState.selectedTopics,
                newVerseViewModel = newVerseViewModel
            )
        }
    }


    // New verse is saved. Now navigate to edit verse screen
    LaunchedEffect(newVerseState.newlySavedVerseId) {
        if (newVerseState.newlySavedVerseId != null) {
            navController.navigate(Screen.VerseDetailScreen(verseID = newVerseState.newlySavedVerseId!!, editMode = true)) {
                popUpTo(Screen.Home) // pop up to home after navigating
            }
            newVerseViewModel.resetNavigationState() // Reset the ID and message flag in ViewModel after navigation
            savingComplete = true
        }
    }

    // Show the transient dialog
    if (showRetreivingDataDialog) {
        TransientRetrievingDataDialog(
            savingComplete = savingComplete,
            onDismiss = { showRetreivingDataDialog = false }
        )
    }


    if (showExistingVerseDialog && existingVerseForDialog != null) {
        val verseToShow = existingVerseForDialog!! // Safe due to the check
        AlertDialog(
            onDismissRequest = {
                // This might be called if the user clicks outside the dialog or presses back.
                // Decide if you want to navigate even then, or only on "OK".
                // For this request, we only navigate on "OK".
                showExistingVerseDialog = false
                existingVerseForDialog = null
                newVerseViewModel.clearVerseData() // Clear any pending fetches as we are not proceeding with a new fetch
            },
            title = { Text("Verse Exists") },
            text = { Text("The verse '${verseToShow.book} ${verseToShow.chapter}:${verseToShow.startVerse}' " +
                    "already exists in your collection. You will be taken to the verse detail for option to edit it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingVerseDialog = false
                        navController.navigate(Screen.VerseDetailScreen(verseID = verseToShow.id, editMode = false)) {
                            popUpTo(Screen.Home) // Ensure consistent behavior
                        }
                        newVerseViewModel.clearVerseData() // Clear any pending fetches
                        existingVerseForDialog = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    AppScaffold(
        title = { Text("Bible Verse Listing") }, // Define the title for this screen
        navController = navController,
        currentScreenInstance = Screen.AllVersesScreen, // Pass the actual Screen instance
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues -> // Content lambda receives padding values
            Column(
                modifier = Modifier
                    .padding(paddingValues) // Apply padding from AppScaffold
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            newVerseViewModel.resetNavigationState() // Reset the ID and message flag in ViewModel after navigation
                            navController.navigate(Screen.GetBookScreen) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add specific verse...")
                    }
                    Button(
                        onClick = { navController.navigate(Screen.AddVerseByDescriptionScreen) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add verse by description ...", textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = { /* No action yet */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Filter listing")
                    }
                }

                if (allVerses.isEmpty()) {
                    Text("No verses added yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn {
                        items(allVerses) { verseItem ->
                            VerseCard(verseItem, navController) // Assuming VerseCard is defined elsewhere
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    )

    val activity = LocalActivity.current
    var backPressedTime by remember { mutableStateOf(0L) }
    BackPressHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}


// Helper function to determine if content is ready to be saved
private fun readyToSave(state: NewVerseViewModel.NewVerseScreenState): Boolean{
    return ((state.selectedVerse != null) &&
            (!state.aiResponseLoading) &&
            (!state.isScriptureLoading))
}

@Composable
fun TransientRetrievingDataDialog(
    savingComplete: Boolean,
    onDismiss: () -> Unit
) {
    // This LaunchedEffect will run when this composable enters the composition
    // and will re-run whenever the 'savingComplete' value changes.
    LaunchedEffect(savingComplete) {
        if (savingComplete) {
            // Add a small delay to allow the user to read the final message if needed
            delay(500)
            onDismiss()
        }
    }

    Dialog(onDismissRequest = { /* We want to control dismissal via savingComplete */ }) {
        Surface(
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Retrieving data....")
            }
        }
    }
}
