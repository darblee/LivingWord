package com.darblee.livingword.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import com.darblee.livingword.ui.viewmodels.NewVerseViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

enum class FilterOption {
    ALL,
    FAVORITES,
    TOPIC
}

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
    val allTopics by bibleViewModel.allTopicsWithCount.collectAsState()
    val favoriteVerses by bibleViewModel.favoriteVerses.collectAsState()
    var versesToDisplay by remember { mutableStateOf<List<BibleVerse>>(emptyList()) }
    var filterOption by remember { mutableStateOf(FilterOption.ALL) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }

    val versesForTopic by remember(selectedTopic) {
        selectedTopic?.let { bibleViewModel.getVersesByTopic(it) } ?: flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    LaunchedEffect(allVerses, favoriteVerses, filterOption, versesForTopic) {
        versesToDisplay = when (filterOption) {
            FilterOption.ALL -> allVerses
            FilterOption.FAVORITES -> favoriteVerses
            FilterOption.TOPIC -> versesForTopic
        }
    }

    var showRetrievingDataDialog by remember { mutableStateOf(false) }
    val newVerseViewModel: NewVerseViewModel = viewModel(
        // Scope to the navigation host instead of this composable so it survives navigation
        viewModelStoreOwner = LocalActivity.current as ComponentActivity
    )

    // Reset navigation state when entering AllVersesScreen without newVerseJson
    // This prevents bounce-back navigation from bottom nav bar
    LaunchedEffect(Unit) {
        if (newVerseJson == null) {
            // We arrived here via bottom navigation, not verse creation flow
            // Clear any pending navigation state to prevent auto-navigation to VerseDetailScreen
            newVerseViewModel.resetNavigationState()
        }
    }

    var showFilterDialog by remember { mutableStateOf(false) }

    if (showFilterDialog) {
        FilterDialog(
            topics = allTopics.map { it.topic },
            onDismiss = { showFilterDialog = false },
            onApplyFilter = { newFilterOption, newSelectedTopic ->
                filterOption = newFilterOption
                selectedTopic = newSelectedTopic
                when (newFilterOption) {
                    FilterOption.ALL -> {
                        bibleViewModel.getAllVerses()
                    }
                    FilterOption.FAVORITES -> {
                        bibleViewModel.getAllFavoriteVerses()
                    }
                    FilterOption.TOPIC -> {
                        // The LaunchedEffect will handle updating versesToDisplay
                    }
                }
                showFilterDialog = false
            }
        )
    }


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

    if (showAIErrorDialog) {
        AlertDialog(
            onDismissRequest = {
                showAIErrorDialog = false
                newVerseViewModel.clearVerseData()
            },
            title = { Text("AI Error") },
            text = { Text(aiErrorMessage ?: "An unknown error occurred.") },
            confirmButton = {
                TextButton(onClick = {
                    showAIErrorDialog = false
                    newVerseViewModel.clearVerseData()
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Early save when scripture is ready
    LaunchedEffect(newVerseState.loadingStage, newVerseState.selectedVerse, newVerseState.scriptureVerses.size) {
        Log.d("AllVersesScreen", "LaunchedEffect(Early save) triggered - loadingStage: ${newVerseState.loadingStage}")
        Log.d("AllVersesScreen", "Early save check - readyForEarlySave: ${readyForEarlySave(newVerseState)}, selectedVerse: ${newVerseState.selectedVerse}, scriptureVerses: ${newVerseState.scriptureVerses.size}, scriptureError: ${newVerseState.scriptureError}")
        if (readyForEarlySave(newVerseState)) {
            Log.d("AllVersesScreen", "Triggering early save for verse: ${newVerseState.selectedVerse}")
            bibleViewModel.saveNewVerse(
                verse = (newVerseState.selectedVerse!!),
                aiTakeAwayResponse = "", // Empty for now, will be updated later
                topics = newVerseState.selectedTopics,
                newVerseViewModel = newVerseViewModel,
                translation = newVerseState.translation,
                scriptureVerses = newVerseState.scriptureVerses,
                isEarlySave = true
            )
        }
    }


    // Track if we've already navigated to prevent multiple navigations
    var hasNavigated by remember { mutableStateOf(false) }

    // New verse is saved. Now navigate to edit verse screen  
    LaunchedEffect(newVerseState.newlySavedVerseId, hasNavigated) {
        Log.d("AllVersesScreen", "LaunchedEffect(Navigation) triggered - newlySavedVerseId: ${newVerseState.newlySavedVerseId}, hasNavigated: $hasNavigated")
        if (newVerseState.newlySavedVerseId != null && !hasNavigated) {
            showRetrievingDataDialog = false // Hide dialog before navigating
            navController.navigate(Screen.VerseDetailScreen(verseID = newVerseState.newlySavedVerseId!!, editMode = true)) {
                popUpTo(Screen.Home)
            }
            hasNavigated = true // Mark as navigated to prevent re-navigation
        }
    }

    // Reset navigation state after final save completes
    LaunchedEffect(newVerseState.isContentSaved) {
        if (newVerseState.isContentSaved && newVerseState.newlySavedVerseId != null) {
            newVerseViewModel.resetNavigationState()
            hasNavigated = false // Reset for next verse
        }
    }

    // Show the transient dialog
    if (showRetrievingDataDialog) {
        // Derive the message from the ViewModel's state
        val loadingMessage = when {
            newVerseState.loadingStage == NewVerseViewModel.LoadingStage.FETCHING_SCRIPTURE -> "Fetching scripture..."
            newVerseState.loadingStage == NewVerseViewModel.LoadingStage.SCRIPTURE_READY -> "Scripture ready, navigating..."
            newVerseState.loadingStage == NewVerseViewModel.LoadingStage.FETCHING_TAKEAWAY -> "Fetching insights from AI..."
            newVerseState.loadingStage == NewVerseViewModel.LoadingStage.VALIDATING_TAKEAWAY -> "Validating insights..."
            newVerseState.loadingStage == NewVerseViewModel.LoadingStage.NONE && newVerseState.newlySavedVerseId != null && !newVerseState.isContentSaved -> "Saving AI content..."
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
            Image(
                painter = painterResource(id = R.drawable.bible),
                contentDescription = "Bible Background",
                modifier = Modifier.fillMaxSize().zIndex(0f),
                contentScale = ContentScale.Crop,
                alpha = 0.2f
            )
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().zIndex(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    newVerseViewModel.resetNavigationState()
                                    navController.navigate(Screen.GetBookScreen)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Add new verse ...",
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                            Button(
                                onClick = { navController.navigate(Screen.AddVerseByDescriptionScreen) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Add by\ndescription", // Manual line break
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                            Button(
                                onClick = { showFilterDialog = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    "Custom listing",
                                    textAlign = TextAlign.Center,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    if (versesToDisplay.isEmpty()) {
                        Text("No verses to display.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Box(modifier = Modifier.weight(1f)) {
                            val listState = rememberLazyListState()
                            val scope = rememberCoroutineScope()
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(versesToDisplay) { verseItem ->
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
fun FilterDialog(
    topics: List<String>,
    onDismiss: () -> Unit,
    onApplyFilter: (FilterOption, String?) -> Unit
) {
    var selectedOption by remember { mutableStateOf(FilterOption.ALL) }
    var expanded by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verses Listing") },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == FilterOption.ALL),
                            onClick = { selectedOption = FilterOption.ALL }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == FilterOption.ALL),
                        onClick = { selectedOption = FilterOption.ALL }
                    )
                    Text(
                        text = "All Verses",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == FilterOption.FAVORITES),
                            onClick = { selectedOption = FilterOption.FAVORITES }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == FilterOption.FAVORITES),
                        onClick = { selectedOption = FilterOption.FAVORITES }
                    )
                    Text(
                        text = "Favorites Only",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Favorite",
                        tint = Color.Red
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (selectedOption == FilterOption.TOPIC),
                            onClick = { selectedOption = FilterOption.TOPIC }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (selectedOption == FilterOption.TOPIC),
                        onClick = { selectedOption = FilterOption.TOPIC }
                    )
                    Text(
                        text = "By Topic:",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        TextButton(onClick = { expanded = true }) {
                            Text(selectedTopic ?: "Select Topic")
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown"
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            topics.forEach { topic ->
                                DropdownMenuItem(
                                    text = { Text(topic) },
                                    onClick = {
                                        selectedTopic = topic
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApplyFilter(selectedOption, selectedTopic) },
                enabled = selectedOption != FilterOption.TOPIC || selectedTopic != null
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


// Helper function to determine if content is ready to be saved
// Helper function to determine if scripture is ready for early save
private fun readyForEarlySave(state: NewVerseViewModel.NewVerseScreenState): Boolean {
    return (state.selectedVerse != null &&
            state.loadingStage == NewVerseViewModel.LoadingStage.SCRIPTURE_READY &&
            state.newlySavedVerseId == null &&
            state.scriptureVerses.isNotEmpty() &&
            state.scriptureError == null)
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