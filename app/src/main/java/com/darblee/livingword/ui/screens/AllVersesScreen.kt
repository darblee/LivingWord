package com.darblee.livingword.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
    newVerseJson: String? = null
) {
    val context = LocalContext.current

    val allVerses by bibleViewModel.allVerses.collectAsState()

    var showRetrievingDataDialog by remember { mutableStateOf(false) }
    val newVerseViewModel: NewVerseViewModel = viewModel()

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
                bibleViewModel.saveNewVerse(
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

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedStateHandle = currentBackStackEntry?.savedStateHandle

    var showExistingVerseDialog by remember { mutableStateOf(false) }
    var existingVerseForDialog by remember { mutableStateOf<BibleVerse?>(null) }

    /**
     * Handle Navigation results
     *
     * If the keys (savedStateHandle, lifecycleOwner.lifecycle) changes, the existing co-routine
     * is canceled,and a new one is launched with the new key values. This ensures the effect
     * always has the correct savedStateHandle and lifecycleOwner to work with.
     */
    LaunchedEffect(savedStateHandle, lifecycleOwner.lifecycle) {
        /**
         * This line, nested inside the LaunchedEffect, is the modern, recommended way to safely
         * collect data from a Flow in a way that respects the component's lifecycle.
         *
         * Purpose: It creates a new coroutine that only runs the code inside its block when the
         * screen's lifecycle is at least in the STARTED state. The STARTED state means the UI is
         * visible to the user (even if partially, like in split-screen mode).
         *
         * Safety and Efficiency:
         * When the user navigates away from AllVersesScreen (and its lifecycle state drops below
         * STARTED, e.g., to CREATED), the coroutine collecting the data is automatically canceled.
         *
         * When the user navigates back to AllVersesScreen (and the state becomes STARTED again),
         * a new coroutine is automatically launched to resume listening for the result.
         *
         * This prevents the app from wasting resources trying to process a result when the screen
         * isn't visible and avoids potential crashes from trying to update a UI that is no longer
         * on screen.
         */
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
                                // Verse does not exist, proceed with fetching all the date -
                                // scripture and take-away
                                Log.i(
                                    "AllVerseScreen",
                                    "Verse ${selectedVerseRef.book} ${selectedVerseRef.chapter}:${selectedVerseRef.startVerse} not found in DB. Fetching new data."
                                )

                                showRetrievingDataDialog = true
                                newVerseViewModel.setSelectedVerseAndFetchData(selectedVerseRef)

                                Log.i("AllVerseScreen", "Start to fetch the scripture and take-away for Book = ${selectedVerseRef.book}, chapter = ${selectedVerseRef.chapter}")
                            }

                        } catch (e: Exception) {
                            Log.e("AllVerseScreen", "Error deserializing BibleVerse: ${e.message}", e)
                            newVerseViewModel.setSelectedVerseAndFetchData(null) // Clear data on error
                        } finally {
                            // Important: Remove the result from SavedStateHandle to prevent reprocessing
                            Log.d("AllVerseScreen", "Removing key $VERSE_RESULT_KEY from SavedStateHandle")
                            savedStateHandle.remove<String>(VERSE_RESULT_KEY)
                        }
                    }// savedStateHandle?.getStateFlow<String?>(VERSE_RESULT_KEY, null)
            }  // launch()
        } // lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
    }
    // --- End Handle Navigation Results ---

    LaunchedEffect(newVerseState) {
        if (readyToSave(newVerseState) && !newVerseState.isContentSaved) {
            bibleViewModel.saveNewVerse(
                verse = (newVerseState.selectedVerse!!),
                aiTakeAwayResponse = newVerseState.aiResponseText,
                topics = newVerseState.selectedTopics,
                newVerseViewModel = newVerseViewModel,
                translation = newVerseState.translation,
                scriptureVerses = newVerseState.scriptureVerses,
            )
        }
    }

    // New verse is saved. Now navigate to edit verse screen
    LaunchedEffect(newVerseState.newlySavedVerseId) {
        if (newVerseState.newlySavedVerseId != null) {
            showRetrievingDataDialog = false // Hide dialog before navigating
            navController.navigate(Screen.VerseDetailScreen(verseID = newVerseState.newlySavedVerseId!!, editMode = true)) {
                popUpTo(Screen.Home)
            }
            newVerseViewModel.resetNavigationState()
        }
    }

    // Show the transient dialog
    if (showRetrievingDataDialog) {
        // Derive the message from the ViewModel's state
        val loadingMessage = when (newVerseState.loadingStage) {
            NewVerseViewModel.LoadingStage.FETCHING_SCRIPTURE -> "Fetching scripture..."
            NewVerseViewModel.LoadingStage.FETCHING_TAKEAWAY -> "Fetching insights from AI..."
            NewVerseViewModel.LoadingStage.VALIDATING_TAKEAWAY -> "Validating insights..."
            else -> "Finalizing..." // Fallback message
        }
        TransientRetrievingDataDialog(loadingMessage = loadingMessage)
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
        title = { Text("Bible Verse Listing") },
        navController = navController,
        currentScreenInstance = Screen.AllVersesScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
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
                            newVerseViewModel.resetNavigationState()
                            navController.navigate(Screen.GetBookScreen) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add new verse...")
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
                    Box(modifier = Modifier.weight(1f)) {
                        val listState = rememberLazyListState()
                        val scope = rememberCoroutineScope()
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(allVerses) { verseItem ->
                                VerseCard(verseItem, navController)
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        val showScrollDownIndicator by remember {
                            derivedStateOf {
                                listState.canScrollForward
                            }
                        }
                        val showScrollUpIndicator by remember {
                            derivedStateOf {
                                listState.firstVisibleItemIndex > 0
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollUpIndicator,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Scroll Up",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollDownIndicator,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        listState.animateScrollToItem(allVerses.size - 1)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll Down",
                                    tint = MaterialTheme.colorScheme.onSecondary,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
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
private fun readyToSave(state: NewVerseViewModel.NewVerseScreenState): Boolean {
    return (state.selectedVerse != null &&
            state.loadingStage == NewVerseViewModel.LoadingStage.NONE &&
            !state.isContentSaved &&
            state.scriptureError == null &&
            state.aiResponseError == null)
}

@Composable
fun TransientRetrievingDataDialog(
    loadingMessage: String
) {
    Dialog(onDismissRequest = { /* Dialog is controlled by state, not user dismissal */ }) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium, // Give it some shape
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                // Use the dynamic message passed into the function
                Text(text = loadingMessage)
            }
        }
    }
}
