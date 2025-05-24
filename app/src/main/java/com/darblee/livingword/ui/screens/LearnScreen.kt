package com.darblee.livingword.ui.screens


import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.BibleVerseT
import com.darblee.livingword.Global
import com.darblee.livingword.Global.TOPIC_SELECTION_RESULT_KEY
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.LearnViewModel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
) {
    val learnViewModel: LearnViewModel = viewModel()
    // Observe the state flow from the ViewModel safely with the lifecycle
    val state by learnViewModel.state.collectAsStateWithLifecycle()

    // Remember scroll states for the text fields (UI concern)
    val scriptureScrollState = rememberScrollState()
    val aiResponseScrollState = rememberScrollState()

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
                        Log.d("LearnScreen", "Received verse JSON: $resultJson")
                        try {
                            val selectedVerseInfo = Json.decodeFromString<BibleVerseT>(resultJson)
                            Log.d("LearnScreen", "Successfully deserialized: $selectedVerseInfo")

                            // Check if the verse exists in the database
                            val existingVerse = bibleViewModel.findExistingVerse(
                                book = selectedVerseInfo.book,
                                chapter = selectedVerseInfo.chapter,
                                startVerse = selectedVerseInfo.startVerse
                            )

                            if (existingVerse != null) {
                                Log.i(
                                    "LearnScreen",
                                    "Verse ${existingVerse.book} ${existingVerse.chapter}:${existingVerse.startVerse} already exists in DB with ID: ${existingVerse.id}."
                                )

                                existingVerseForDialog = existingVerse // Store for dialog
                                showExistingVerseDialog = true

                                // Navigation to VerseDetail will happen when dialog is dismissed
                            } else {
                                // Verse does not exist, proceed with fetching
                                Log.i(
                                    "LearnScreen",
                                    "Verse ${selectedVerseInfo.book} ${selectedVerseInfo.chapter}:${selectedVerseInfo.startVerse} not found in DB. Fetching new data."
                                )
                                learnViewModel.setSelectedVerseAndFetchData(selectedVerseInfo)
                            }
                        } catch (e: Exception) {
                            Log.e("LearnScreen", "Error deserializing BibleVerse: ${e.message}", e)
                            learnViewModel.setSelectedVerseAndFetchData(null) // Clear data on error
                        } finally {
                            // Important: Remove the result from SavedStateHandle to prevent reprocessing
                            Log.d("LearnScreen", "Removing key $VERSE_RESULT_KEY from SavedStateHandle")
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
                            Log.d("LearnScreen", "Received topics JSON: $resultJson")
                            try {
                                val selectedTopics = Json.decodeFromString(ListSerializer(String.serializer()), resultJson)
                                learnViewModel.updateSelectedTopics(selectedTopics) // Update ViewModel
                            } catch (e: Exception) {
                                Log.e("LearnScreen", "Error deserializing topics: ${e.message}", e)
                                // TODO: Show a Snackbar or other transient error message
                            } finally {
                                // Important: Remove the result
                                Log.d("LearnScreen", "Removing key $TOPIC_SELECTION_RESULT_KEY from SavedStateHandle")
                                savedStateHandle.remove<String>(TOPIC_SELECTION_RESULT_KEY)
                            }
                        } else {
                            // Handle case where the result might be an empty string if that's possible
                            Log.d("LearnScreen", "Received empty topics JSON, removing key $TOPIC_SELECTION_RESULT_KEY")
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
            learnViewModel.resetNavigationState() // Reset the ID and message flag in ViewModel after navigation
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
                learnViewModel.clearVerseData() // Clear any pending fetches as we are not proceeding with a new fetch
            },
            title = { Text("Verse Exists") },
            text = { Text("The verse '${verseToShow.book} ${verseToShow.chapter}:${verseToShow.startVerse}' " +
                    "already exists in your collection. You will be taken to the saved verse.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExistingVerseDialog = false
                        navController.navigate(Screen.VerseDetailScreen(verseID = verseToShow.id)) {
                            // Optional: Configure popUpTo or other navigation options
                        }
                        learnViewModel.clearVerseData() // Clear any pending fetches
                        existingVerseForDialog = null
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }

    var currentScreen by remember { mutableStateOf(Screen.LearnScreen) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Learn God's Word") },
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
                    onClick = { navController.navigate(Screen.Home) },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.MeditateScreen,
                    onClick = { navController.navigate(Screen.MeditateScreen) },
                    label = { Text("Meditate") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Meditate",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.VerseByTopicScreen,
                    onClick = { navController.navigate(Screen.VerseByTopicScreen) },
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

            // --- Verse Selection Row ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Space between elements
            ) {
                // Clickable Box wrapping the TextField for navigation
                Box(
                    modifier = Modifier
                        .weight(1f) // Allow TextField to take available space
                        .clickable(
                            /***

                            That line of code, interactionSource = remember { MutableInteractionSource() }, is used within the clickable modifier in Jetpack Compose. Here's what it does:

                            MutableInteractionSource: This is an interface that allows components to emit streams of interaction events (like Press, Hover, Focus, Drag). Think of it as a
                            way for a component to broadcast what's happening to it in terms of user interaction.remember { ... }: This is a standard Compose function. It ensures that the
                            object created inside the lambda (in this case, MutableInteractionSource()) is created only once when the composable first enters the composition. On subsequent
                            recompositions, it remembers and reuses the same instance. This is crucial for maintaining state and avoiding unnecessary object creation.

                            interactionSource = ...: This is a parameter of the clickable modifier (and many other interactive Material components). By providing your own MutableInteractionSource,
                            you gain more control over the interaction state.

                            Often, MutableInteractionSource is passed along with indication = null. The indication parameter controls the visual feedback for interactions (like the ripple
                            effect on click). Setting indication = null disables this default visual feedback. Providing your own interactionSource in this context is standard practice when
                            you want to disable or customize the indication. It ensures the clickable modifier still functions correctly even without the default visual effect. In short,
                            interactionSource = remember { MutableInteractionSource() } creates a stable source for interaction events for that specific clickable area, and in your case,
                            it's primarily used in conjunction with indication = null to make the OutlinedTextField clickable without showing the default ripple effect.
                             */
                            interactionSource = remember { MutableInteractionSource() },

                            indication = null, // Disable ripple effect
                            onClick = {
                                // Clear existing verse data in ViewModel before navigating
                                learnViewModel.clearVerseData()
                                // Navigate to the book selection screen
                                navController.navigate(Screen.GetBookScreen)
                            }
                        )
                ) {
                    OutlinedTextField(
                        // Read selected verse display text from ViewModel state
                        value = state.selectedVerse?.let { "${it.book} ${it.chapter}:${it.startVerse}-${it.endVerse}" }
                            ?: "",
                        onValueChange = {}, // Input is read-only
                        modifier = Modifier.fillMaxWidth(), // Fill width inside the Box
                        label = { Text("Selected Verse") },
                        placeholder = {
                            Text(
                                text = "Click here to select verse range",
                                color = LocalContentColor.current.copy(alpha = 0.5f) // Hint color
                            )
                        },
                        readOnly = true,
                        enabled = false, // Disable the TextField itself to make the Box clickable
                        colors = OutlinedTextFieldDefaults.colors( // Custom disabled colors
                            disabledTextColor = LocalContentColor.current.copy(alpha = if (state.selectedVerse == null) 0.5f else 1f),
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                            disabledPlaceholderColor = LocalContentColor.current.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp), // Rounded corners
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp) // Text style
                    )
                }

                // Show Edit and Topic buttons only if a verse is selected in the ViewModel state
                if (state.selectedVerse != null) {
                    // Edit Button
                    Button(
                        onClick = {
                            state.selectedVerse?.let { verse ->
                                // Clear previous data before navigating to edit end verse
                                learnViewModel.clearVerseData()
                                navController.navigate(
                                    Screen.GetEndVerseNumberScreen(
                                        book = verse.book,
                                        chapter = verse.chapter,
                                        startVerse = verse.startVerse
                                    )
                                )
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Global.SMALL_ACTION_BUTTON_MODIFIER, // Use global modifier if defined
                        contentPadding = Global.SMALL_ACTION_BUTTON_PADDING // Use global padding if defined
                    ) {
                        Text("Edit")
                    }

                    // Topic Button
                    Button(
                        onClick = {

                            // Serialize current topics to pass to TopicSelectionScreen
                            val currentSelectedTopicsJson = try {
                                Json.encodeToString(ListSerializer(String.serializer()), state.selectedTopics)
                            } catch (e: Exception) {
                                Log.e("LearnScreen", "Error serializing current topics: ${e.message}", e)
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
                        Text("Topic(s)")
                    }
                }
            }
            // --- End Verse Selection Row ---

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

            // --- Scripture Box ---
            // Use weight modifier on the Column containing the boxes and button
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
                        val textToShow = state.scriptureError ?: state.scriptureText
                        val hasError = state.scriptureError != null
                        val isEmpty =
                            textToShow.isEmpty() && !hasError && state.selectedVerse != null
                        val placeholderText = when {
                            state.selectedVerse == null -> "Scripture appears after selecting a verse..."
                            hasError -> "" // Error is shown directly in textToShow
                            isEmpty -> " " // Use a space to prevent collapse if empty after load
                            else -> ""
                        }

                        BasicTextField(
                            value = textToShow,
                            onValueChange = { /* Read-only */ },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scriptureScrollState), // Make scrollable
                            readOnly = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = if (hasError) MaterialTheme.colorScheme.error else LocalContentColor.current
                            ), // Use error color if needed
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), // Cursor style
                            decorationBox = { innerTextField ->
                                // Show placeholder only when appropriate
                                if (textToShow.isEmpty() && placeholderText.isNotEmpty()) {
                                    Text(
                                        placeholderText,
                                        style = LocalTextStyle.current.copy(
                                            color = LocalContentColor.current.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                                innerTextField() // The actual text field content
                            }
                        )
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
                        val hasError = state.aiResponseError != null
                        val isEmpty =
                            textToShow.isEmpty() && !hasError && state.selectedVerse != null
                        val placeholderText = when {
                            state.selectedVerse == null -> "AI insights appear after selecting a verse..."
                            // Specific placeholder if AI model failed init or key missing
                            state.aiResponseError?.contains("API Key") == true -> "Gemini API Key missing."
                            state.aiResponseError?.contains("initialize AI Model") == true -> "AI Model failed to initialize."
                            hasError -> "" // Other errors shown directly
                            isEmpty -> " " // Use space if empty after load
                            else -> ""
                        }
                        // Determine if the text field should be considered "empty" for placeholder logic
                        val showPlaceholder = textToShow.isEmpty() && placeholderText.isNotEmpty()
                        val baseTextColor = MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }
                        val newBaseStyle = SpanStyle(color = baseTextColor)


                        val scriptureAnnotatedText = buildAnnotatedStringForTts(
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

                        Box(modifier = Modifier.fillMaxSize()) { // Box to anchor dropdown
                            Text(
                                text = scriptureAnnotatedText,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())

                            )
                        }
                    }
                }
                // --- End Key Take-Away Box ---

                // --- Selected Topics Box ---
                // Only show if there are selected topics
                if (state.selectedTopics.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp)) // Space before topics box

                    val topicScrollState = rememberScrollState()
                    var topicLabel = "Selected Topic"
                    if (state.selectedTopics.size > 1) topicLabel = "${state.selectedTopics.count()} Selected Topics"

                    LabeledOutlinedBox(
                        label = topicLabel,
                        modifier = Modifier.fillMaxWidth().height(100.dp)
                        // Adjust height as needed, e.g., .heightIn(min = 56.dp)
                    ) {
                        Column (
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
                            learnViewModel = learnViewModel
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
}

// Helper function to determine if content is ready to be saved
fun readyToSave(state: LearnViewModel.LearnScreenState): Boolean{
    return ((state.selectedVerse != null) &&
            (state.scriptureText.isNotEmpty()) &&
            (state.aiResponseText.isNotEmpty()) &&
            (state.selectedTopics.isNotEmpty()))
}

/**
 * Reusable composable for a labeled outlined box container.
 * (No changes needed here)
 */
@Composable
fun LabeledOutlinedBox(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium, // Or labelSmall/labelLarge
            color = MaterialTheme.colorScheme.primary, // Or onSurfaceVariant
            modifier = Modifier.padding(bottom = 4.dp), // Space between label and box
            fontSize = 15.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth() // Fill width within the Column
                .heightIn(min = 48.dp) // Ensure a minimum height, adjust as needed
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp) // Consistent corner rounding
                )
                .clip(RoundedCornerShape(8.dp)) // Clip content to rounded shape
                .padding(horizontal = 12.dp, vertical = 8.dp), // Padding inside the box
            contentAlignment = Alignment.TopStart // Align content (e.g., BasicTextField)
        ) {
            content() // Render the content passed into the box
        }
    }
}
