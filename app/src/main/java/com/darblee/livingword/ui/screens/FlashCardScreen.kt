package com.darblee.livingword.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.ScriptureTaskType
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import kotlinx.serialization.json.Json
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.ui.viewmodels.NewVerseViewModel



@Composable
fun FlashCardScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    bibleVerseViewModel: BibleVerseViewModel,
    newVerseJson: String? = null
) {
    val context = LocalContext.current

    val newVerseViewModel: NewVerseViewModel = viewModel(
        // Scope to the navigation host instead of this composable so it survives navigation
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )

    LaunchedEffect(newVerseJson) {
        newVerseJson?.let {
            try {
                val bibleVerseRef = Json.decodeFromString<BibleVerseRef>(it)
                // Here, you would call the function to add the verse to your database
                // For example, if bibleViewModel has an addVerse function:
                // bibleViewModel.addVerse(bibleVerseRef)
                // For now, let's just show a toast
                Toast.makeText(context, "Attempting to add verse: ${bibleVerseRef.book} ${bibleVerseRef.chapter}:${bibleVerseRef.startVerse}", Toast.LENGTH_LONG).show()

                // You'll need to decide how to handle the actual saving. If it's a simple add:
                bibleVerseViewModel.saveNewVerse(
                    verse = bibleVerseRef,
                    aiTakeAwayResponse = "", // You might need to fetch this or pass it
                    topics = emptyList(), // You might need to fetch this or pass it
                    newVerseViewModel = newVerseViewModel, // Pass the NewVerseViewModel instance
                    translation = "KJV", // You might need to pass this
                    scriptureVerses = emptyList() // You might need to fetch this or pass it
                )

            } catch (e: Exception) {
                Log.e("AllVersesScreen", "Error parsing newVerseJson: ${e.message}", e)
                Toast.makeText(context, "Error adding verse: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Use LaunchedEffect tied to lifecycle to observe results from SavedStateHandle
    // --- Handle Navigation Results ---
    val newVerseState by newVerseViewModel.state.collectAsStateWithLifecycle()

    var showRetrievingDataDialog by remember { mutableStateOf(false) }

    var showExistingVerseDialog by remember { mutableStateOf(false) }
    var existingVerseForDialog by remember { mutableStateOf<BibleVerse?>(null) }

    // Check for VersePicker result - use the back stack size as a trigger
    var selectedVerse by remember { mutableStateOf<BibleVerseRef?>(null) }
    val backStackSize = navController.currentBackStack.collectAsState().value.size
    LaunchedEffect(backStackSize) {
        try {
            val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
            savedStateHandle?.get<String>(Global.VERSE_RESULT_KEY)?.let { resultJson ->
                val verseRef = Json.decodeFromString<BibleVerseRef>(resultJson)
                selectedVerse = verseRef

                // Clear the result to prevent reprocessing
                savedStateHandle.remove<String>(Global.VERSE_RESULT_KEY)
            }

            if (selectedVerse == null) {
                newVerseViewModel.setSelectedVerseAndFetchData(null) // Clear data on error
                return@LaunchedEffect
            }

            if (selectedVerse!!.scriptureTaskType == ScriptureTaskType.GetScriptureRefOnly) {
                Log.i("FlashCardScreen", "VersePicker returned ScriptureTaskType.GetScriptureRefOnly.")
            } else {
                Log.i("FlashCardScreen", "VersePicker returned ScriptureTaskType.GetScriptureText.")

                val existingVerse = bibleVerseViewModel.findExistingVerse(
                    book = selectedVerse!!.book,
                    chapter = selectedVerse!!.chapter,
                    startVerse = selectedVerse!!.startVerse
                )

                if (existingVerse != null) {
                    Log.i(
                        "AllVerseScreen",
                        "Verse \"${existingVerse.book}\" ${existingVerse.chapter}:${existingVerse.startVerse} already exists in DB with ID: ${existingVerse.id}."
                    )

                    existingVerseForDialog = existingVerse // Store for dialog
                    showExistingVerseDialog = true

                    // Navigation to VerseDetail will happen when dialog is dismissed
                } else {
                    // Verse does not exist, proceed with fetching all the date - scripture and take-away
                    Log.i(
                        "AllVerseScreen",
                        "Verse \"${selectedVerse!!.book}\" ${selectedVerse!!.chapter}:${selectedVerse!!.startVerse} not found in DB. Fetching new data."
                    )

                    /*                showRetrievingDataDialog = true
                newVerseViewModel.setSelectedVerseAndFetchData(selectedVerseFromAddVerse)*/
                }
            }
        } catch (e: Exception) {
            Log.e("AllVerseScreen", "Error deserializing BibleVerse: ${e.message}", e)
            newVerseViewModel.setSelectedVerseAndFetchData(null) // Clear data on error
            return@LaunchedEffect
        }
    }


    var showAIErrorDialog by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(newVerseState.scriptureError, newVerseState.aiResponseError) {
        val error = newVerseState.scriptureError ?: newVerseState.aiResponseError
        if (error != null) {
            aiErrorMessage = error
            showAIErrorDialog = true
            showRetrievingDataDialog = false
        }
    }

    if (showRetrievingDataDialog) {
        TransientDialog(loadingMessage = "Fetching scripture ...")
    }

    if (showExistingVerseDialog && existingVerseForDialog != null) {
        val verseToShow = existingVerseForDialog!! // Safe due to the check
        AlertDialog(
            onDismissRequest = {
                showExistingVerseDialog = false
                existingVerseForDialog = null
                newVerseViewModel.clearVerseData()
            },
            title = { Text("Verse Exists") },
            text = { Text("The verse '${verseToShow.book} ${verseToShow.chapter}:${verseToShow.startVerse}' " +
                    "already exists in your collection. You will be taken to the verse detail for option to edit it.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingVerseDialog = false
                        navController.navigate(Screen.VerseDetailScreen(verseID = verseToShow.id, editMode = false)) {
                            popUpTo(Screen.Home)
                        }
                        newVerseViewModel.clearVerseData()
                        existingVerseForDialog = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    AppScaffold(
        title = { Text("Quiz Flash Card") },
        navController = navController,
        currentScreenInstance = Screen.FlashCardScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HandleScriptureRef(selectedVerse, navController)
            }
        }
    )

    val activity = LocalActivity.current
    var backPressedTime by remember { mutableLongStateOf(0L) }
    BackPressHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun HandleScriptureRef(selectedVerse: BibleVerseRef?, navController: NavController) {

    Button(
        onClick = {
            val versePickerRoute = BibleData.createVersePickerRoute(Screen.FlashCardScreen, ScriptureTaskType.GetScriptureRefOnly)
            navController.navigate(versePickerRoute)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Select Verse for Quiz")
    }

    selectedVerse?.let { verseRef ->
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Selected Verse:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Book: ${verseRef.book}",
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Chapter: ${verseRef.chapter}",
                    style = MaterialTheme.typography.bodyLarge
                )

                val verseRange = if (verseRef.startVerse == verseRef.endVerse) {
                    "Verse: ${verseRef.startVerse}"
                } else {
                    "Verses: ${verseRef.startVerse}-${verseRef.endVerse}"
                }

                Text(
                    text = verseRange,
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = "Reference: ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}" +
                            if (verseRef.startVerse != verseRef.endVerse) "-${verseRef.endVerse}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    } ?: run {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "No verse selected yet.\nTap the button above to select a verse for your quiz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}