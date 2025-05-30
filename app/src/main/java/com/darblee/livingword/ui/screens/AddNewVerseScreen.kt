package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.BibleVerseRef
import com.darblee.livingword.Global
import com.darblee.livingword.Global.TOPIC_SELECTION_RESULT_KEY
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.verseReferenceT
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.NewVerseViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Formats scripture text by converting verse numbers in brackets [1], [2] etc.
 * to red superscript numbers for display.
 */
fun formatScriptureWithVerseNumbers(scriptureText: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val regex = """\[(\d+)\]""".toRegex()
        var lastIndex = 0

        regex.findAll(scriptureText).forEach { matchResult ->
            // Append text before the verse number
            append(scriptureText.substring(lastIndex, matchResult.range.first))

            // Extract the verse number
            val verseNumber = matchResult.groupValues[1]

            // Add the verse number as red superscript
            withStyle(
                style = SpanStyle(
                    color = Color.Red,
                    fontSize = 10.sp,
                    baselineShift = BaselineShift.Superscript,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(verseNumber)
            }

            lastIndex = matchResult.range.last + 1
        }

        // Append remaining text after the last verse number
        append(scriptureText.substring(lastIndex))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewVerseScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
) {
    val newVerseViewModel: NewVerseViewModel = viewModel()
    // Observe the state flow from the ViewModel safely with the lifecycle
    val state by newVerseViewModel.state.collectAsStateWithLifecycle()

    // Remember scroll states for the text fields (UI concern)
    val scriptureScrollState = rememberScrollState()

    // --- Handle Navigation Results ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedStateHandle = currentBackStackEntry?.savedStateHandle

    var showExistingVerseDialog by remember { mutableStateOf(false) }
    var existingVerseForDialog by remember { mutableStateOf<BibleVerse?>(null) }

    // Use LaunchedEffect tied to lifecycle to observe results from SavedStateHandle
    LaunchedEffect(savedStateHandle, lifecycleOwner.lifecycle) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Observe Verse Result
            launch {
                savedStateHandle?.getStateFlow<String?>(VERSE_RESULT_KEY, null)
                    ?.filterNotNull() // Process only non-null results
                    ?.collect { resultJson ->
                        Log.d("NewVerseScreen", "Received verse JSON: $resultJson")
                        try {
                            val selectedVerseRef = Json.decodeFromString<BibleVerseRef>(resultJson)
                            Log.d("NewVerseScreen", "Successfully deserialized: $selectedVerseRef")

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
                                // Verse does not exist, proceed with fetching
                                Log.i(
                                    "NewVerseScreen",
                                    "Verse ${selectedVerseRef.book} ${selectedVerseRef.chapter}:${selectedVerseRef.startVerse} not found in DB. Fetching new data."
                                )
                                newVerseViewModel.setSelectedVerseAndFetchData(selectedVerseRef)

                                Log.i("AddNewVerse", "Saving the verse selection. Book = ${selectedVerseRef.book}, chapter = ${selectedVerseRef.chapter}")
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

            // Observe Topic Result
            launch {
                savedStateHandle?.getStateFlow<String?>(TOPIC_SELECTION_RESULT_KEY, null)
                    ?.filterNotNull() // Process only non-null results
                    ?.collect { resultJson ->
                        if (resultJson.isNotEmpty()) { // Additional check for empty string
                            Log.d("NewVerseScreen", "Received topics JSON: $resultJson")
                            try {
                                val selectedTopics = Json.decodeFromString(ListSerializer(String.serializer()), resultJson)
                                newVerseViewModel.updateSelectedTopics(selectedTopics) // Update ViewModel

                            } catch (e: Exception) {
                                Log.e("NewVerseScreen", "Error deserializing topics: ${e.message}", e)
                                // TODO: Show a Snackbar or other transient error message
                            } finally {
                                // Important: Remove the result
                                Log.d("NewVerseScreen", "Removing key $TOPIC_SELECTION_RESULT_KEY from SavedStateHandle")
                                savedStateHandle.remove<String>(TOPIC_SELECTION_RESULT_KEY)
                            }
                        } else {
                            // Handle case where the result might be an empty string if that's possible
                            Log.d("NewVerseScreen", "Received empty topics JSON, removing key $TOPIC_SELECTION_RESULT_KEY")
                            savedStateHandle.remove<String>(TOPIC_SELECTION_RESULT_KEY)
                        }
                    }
            }
        }
    }
    // --- End Handle Navigation Results ---

    // Display content is saved message and navigate
    LaunchedEffect(state.newlySavedVerseId) {
        if (state.newlySavedVerseId != null) {
            navController.navigate(Screen.VerseDetailScreen(verseID = state.newlySavedVerseId!!)) {
                popUpTo(Screen.Home) // pop up to home after navigating
            }
            newVerseViewModel.resetNavigationState() // Reset the ID and message flag in ViewModel after navigation
        }
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
                    "already exists in your collection. You will be taken to the saved verse.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingVerseDialog = false
                        navController.navigate(Screen.VerseDetailScreen(verseID = verseToShow.id)) {
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

    var currentScreen by remember { mutableStateOf(Screen.NewVerseScreen) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add New Verse") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            // NavigationBar for switching between screens
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Home,
                    onClick = {
                        navController.navigate(Screen.Home) {
                            popUpTo(navController.graph.id) { inclusive = true } // Clear entire stack
                            launchSingleTop = true
                        }
                    },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.AllVersesScreen,
                    onClick = {
                        navController.navigate(Screen.AllVersesScreen) { // Or VerseByTopicScreen
                            popUpTo(Screen.Home) // Pop back to Home, then navigate to this screen
                            launchSingleTop = true
                        }
                    },
                    label = { Text("All Verses") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Review all verses",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.VerseByTopicScreen,
                    onClick = {
                        navController.navigate(Screen.VerseByTopicScreen) { // Or VerseByTopicScreen
                            popUpTo(Screen.Home) // Pop back to Home, then navigate to this screen
                            launchSingleTop = true
                        }
                    },
                    label = { Text("Verse By Topics") },
                    icon = { Icon(Icons.Filled.Church, contentDescription = "Topic") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted vertical padding
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HandleVerseSelection(newVerseViewModel, navController, state.selectedVerse)

            Spacer(modifier = Modifier.height(8.dp)) // Space before content boxes

            // Display general errors from ViewModel if any
            state.generalError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {

                LabeledOutlinedBox(
                    label = "Scripture",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes up proportional space
                ) { // Content lambda for the Box
                    // Show loading indicator based on ViewModel state
                    if (state.isScriptureLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Display scripture text or error message from ViewModel state
                        val rawTextToShow = state.scriptureError ?: state.scriptureText
                        val hasError = state.scriptureError != null
                        val isEmpty = rawTextToShow.isEmpty() && !hasError && state.selectedVerse != null

                        val placeholderText = when {
                            state.selectedVerse == null -> "Scripture to appear after selecting a verse..."
                            hasError -> "" // Error is shown directly in rawTextToShow
                            isEmpty -> " " // Use a space to prevent collapse if empty after load
                            else -> ""
                        }

                        // Format the scripture text with verse numbers if it's not an error
                        val formattedText = if (!hasError && rawTextToShow.isNotEmpty()) {
                            formatScriptureWithVerseNumbers(rawTextToShow)
                        } else {
                            buildAnnotatedString { append(rawTextToShow) }
                        }

                        if (rawTextToShow.isNotEmpty()) {
                            // Use Text composable for formatted text instead of BasicTextField
                            Text(
                                text = formattedText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scriptureScrollState),
                                style = LocalTextStyle.current.copy(
                                    color = if (hasError) MaterialTheme.colorScheme.error else LocalContentColor.current
                                )
                            )
                        } else {
                            // Show placeholder text
                            Text(
                                text = placeholderText,
                                style = LocalTextStyle.current.copy(
                                    color = LocalContentColor.current.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp)) // Space between boxes

                // --- Key Take-Away Box ---
                LabeledOutlinedBox(
                    label = "Key Take-Away",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f) // Takes up proportional space
                ) { // Content lambda for the Box
                    // Show loading indicator based on ViewModel state
                    if (state.aiResponseLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Display take-away text or error message from ViewModel state
                        val textToShow = state.aiResponseError ?: state.aiResponseText

                        // Determine if the text field should be considered "empty" for placeholder logic
                        val baseTextColor = MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }
                        val newBaseStyle = SpanStyle(color = baseTextColor)

                        val scriptureAnnotatedText = buildAnnotatedStringForTTS(
                            fullText = textToShow,
                            isTargeted = false,
                            highlightSentenceIndex = 0,
                            isSpeaking = false,
                            isPaused = false,
                            baseStyle = newBaseStyle,
                            highlightStyle = SpanStyle(
                                background = MaterialTheme.colorScheme.primaryContainer,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            ))

                        var textToDisplay = scriptureAnnotatedText

                        if (textToShow.isEmpty()) textToDisplay =
                            buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        color = LocalContentColor.current.copy(alpha = 0.5f)
                                    )
                                ) {
                                    append("Take-away comment to appear after selecting a verse .....")
                                }
                            }

                        Box(modifier = Modifier.fillMaxSize()) { // Box to anchor dropdown
                            Text(
                                text = textToDisplay,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
                // --- End Key Take-Away Box ---

                Spacer(modifier = Modifier.height(8.dp)) // Space before topics box

                // --- Selected Topics Box ---
                val topicScrollState = rememberScrollState()

                var topicLabel = "Selected Topic"

                if (state.selectedTopics.isNotEmpty()) {
                    if (state.selectedTopics.size > 1) topicLabel =
                        "${state.selectedTopics.count()} Selected Topics"
                }

                LabeledOutlinedBox(
                    label = topicLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                    // Adjust height as needed, e.g., .heightIn(min = 56.dp)
                ) {
                    if (state.selectedTopics.isEmpty()) {
                        if (state.selectedVerse == null) {
                            Text(text = buildAnnotatedString {
                                withStyle(
                                    style = SpanStyle(
                                        color = LocalContentColor.current.copy(alpha = 0.5f)
                                    )
                                ) {
                                    append("Add topics only after verse is identified")
                                }
                            })
                        } else {
                            // Only show topic button when verse is identified
                            Button(
                                onClick = {

                                    // Serialize current topics to pass to TopicSelectionScreen
                                    val currentSelectedTopicsJson = try {
                                        Json.encodeToString(
                                            ListSerializer(String.serializer()),
                                            state.selectedTopics
                                        )
                                    } catch (e: Exception) {
                                        Log.e(
                                            "NewVerseScreen",
                                            "Error serializing current topics: ${e.message}",
                                            e
                                        )
                                        null // Fallback to null if serialization fails
                                    }

                                    // Navigate to Topic Selection Screen
                                    navController.navigate(
                                        Screen.TopicSelectionScreen(selectedTopicsJson = currentSelectedTopicsJson)
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Global.SMALL_ACTION_BUTTON_MODIFIER, // Use global modifier
                                contentPadding = Global.SMALL_ACTION_BUTTON_PADDING // Use global padding
                            ) {
                                Text("Add Topic(s)")
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(topicScrollState) // Enable vertical scrolling
                        ) {
                            // Use FlowRow to allow chips to wrap to the next line
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp), // Space between chips horizontally
                                verticalArrangement = Arrangement.spacedBy(4.dp) // Space between rows of chips
                            ) {
                                state.selectedTopics.forEach { topic ->
                                    // Display each topic as a read-only chip
                                    SuggestionChip(
                                        onClick = { /* Read-only, do nothing on click */ },
                                        label = { Text(topic) },
                                    )
                                }
                            }
                        }
                    }
                }
                // --- End Selected Topics Box ---
            }// End of weighted Column for boxes

            if (readyToSave(state) && !state.isContentSaved)
            {
                Spacer(modifier = Modifier.height(8.dp)) // Space before the button
                Button(
                    onClick = {
                        bibleViewModel.saveNewVerse(
                            verse = (state.selectedVerse!!),
                            scripture = state.scriptureText,
                            aiResponse = state.aiResponseText,
                            topics = state.selectedTopics,
                            newVerseViewModel = newVerseViewModel
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth() // Make button wide
                        .height(48.dp), // Standard button height
                    shape = RoundedCornerShape(8.dp) // Consistent shape
                ) {
                    Text("Save")
                }
            }
        }
    }

    // Handle back press to navigate to Home and clear backstack
    BackPressHandler {
        navController.navigate(Screen.AllVersesScreen) {
            /**
             * Clears the entire back stack before navigating to All Verses screen. navController.graph.id
             * refers to the root of your navigation graph.
             */
            popUpTo(navController.graph.id) { // Pop the entire back stack
                inclusive = true
            }
            /**
             * launchSingleTop = true ensures that if HomeScreen is already at the top of the stack
             * (which it won't be in this specific scenario after popping everything, but it's good
             * practice for navigations to Home), a new instance isn't created.
             */
            launchSingleTop = true // Avoid multiple instances of Home Screen
        }
    }
}

/**
 * Get verse selection. If no verse identified yet, then create a new one.
 * Otherwise, edit the existing selected verse.
 *
 */
@Composable
private fun HandleVerseSelection(
    newVerseViewModel: NewVerseViewModel,
    navController: NavController,
    saveVersePriorVerseSelection: BibleVerseRef?
) {
    if (saveVersePriorVerseSelection == null) {
        Log.i("HandleVerseSelection", "saved verse selection is empty")

        Button(
            onClick = {
                // Clear existing verse data in ViewModel before navigating
                newVerseViewModel.clearVerseData()
                // Navigate to the book selection screen
                navController.navigate(Screen.GetBookScreen)
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Click here to select specific verse reference  ...")
        }
    } else {

        Log.i("HandleVerseSelection", "saved verse selection is NOT empty. " +
                "Book: ${saveVersePriorVerseSelection.book}, chapter: ${saveVersePriorVerseSelection.chapter} ")

        val currentContentColor = LocalContentColor.current
        val scriptureRef = verseReferenceT(saveVersePriorVerseSelection)
        var verseRefToDisplay = buildAnnotatedString {
            withStyle(
                style = MaterialTheme.typography.titleLarge.toSpanStyle()
            ) {
                append(scriptureRef)
            }

            append("      ") // Optional: add a space between the two parts

            withStyle(style = SpanStyle(color = currentContentColor.copy(alpha = 0.5f))) {
                append("Click here to change")
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = Color.White)
                .clickable(
                    onClick = {
                        navController.navigate(
                            Screen.GetEndVerseNumberScreen(
                                book = saveVersePriorVerseSelection.book,
                                chapter = saveVersePriorVerseSelection.chapter,
                                startVerse = saveVersePriorVerseSelection.startVerse,
                            )
                        )
                    }
                )
        ) {
            Text(text = verseRefToDisplay, modifier = Modifier.padding(8.dp))
        }
    }
}

// Helper function to determine if content is ready to be saved
fun readyToSave(state: NewVerseViewModel.NewVerseScreenState): Boolean{
    return ((state.selectedVerse != null) &&
            (state.scriptureText.isNotEmpty()) &&
            (state.aiResponseText.isNotEmpty()) &&
            (state.selectedTopics.isNotEmpty()))
}