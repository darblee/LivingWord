package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.text.BreakIterator
import java.util.Locale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.res.painterResource
import com.darblee.livingword.Global.TOPIC_SELECTION_RESULT_KEY
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.verseReference
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.TTSViewModel
import com.darblee.livingword.domain.model.TTS_OperationMode
import com.darblee.livingword.domain.model.VerseDetailSequencePart

// Enum to track which text block is targeted for single TTS playback in this screen
enum class VerseDetailSingleTtsTarget { NONE, SCRIPTURE, AI_RESPONSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseDetailScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    ttsViewModel: TTSViewModel = viewModel(),
    verseID: Long // Argument of the BibleVerse entity in "BibleVerse_Item" Database
) {
    // Remember scroll states for the text fields (UI concern)
    val scriptureScrollState = rememberScrollState()
    val aiResponseScrollState = rememberScrollState()

    // Use produceState to asynchronously load the BibleVerse
    val verseItemState = produceState<BibleVerse?>(initialValue = null, verseID) {
        value = bibleViewModel.getVerseById(verseID)
    }

    val verseItem = verseItemState.value

    // State for controlling the edit mode
    var inEditMode by remember { mutableStateOf(false) }

    // State to hold the current values, so they can be edited and saved.
    // Initialize with default values, will be updated by LaunchedEffect
    var editedAiResponse by remember { mutableStateOf("") }
    var editedTopics by remember { mutableStateOf(emptyList<String>()) }
    var selectedTopics by remember { mutableStateOf(emptyList<String>()) }
    var processEditTopics by remember { mutableStateOf(false) }
    var newContentNeedToBeSaved by remember { mutableStateOf(false) }

    val editModeColor = Color(0xFF00BCD4) // A distinct cyan for edit mode

    var processDeletion by remember { mutableStateOf(false) }
    var confirmExitWithoutSaving by remember { mutableStateOf(false) }

    // TTS States
    val isTtsInitialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()
    val currentTtsSentenceIndex by ttsViewModel.currentSentenceInBlockIndex.collectAsStateWithLifecycle()
    val isTtsSpeaking by ttsViewModel.isSpeaking.collectAsStateWithLifecycle()
    val isTtsPaused by ttsViewModel.isPaused.collectAsStateWithLifecycle()

    // Collect current operation mode and sequence part from TtsViewModel
    // These are exposed as StateFlows in your TtsViewModel
    val currentTtsOperationMode by ttsViewModel.currentOperationMode.collectAsStateWithLifecycle()
    val currentTtsSequencePart by ttsViewModel.sequenceCurrentPart.collectAsStateWithLifecycle()

    // State to track which block (Scripture or AI) is the target for single TTS playback
    var activeSingleTtsTextBlock by remember { mutableStateOf(VerseDetailSingleTtsTarget.NONE) }

    // State for the dropdown menu
    var showButtonDropdownMenu by remember { mutableStateOf(false) }

    // States for dropdown menus on text boxes
    var showScriptureDropdownMenu by remember { mutableStateOf(false) }
    var scriptureDropdownMenuOffset by remember { mutableStateOf(Offset.Zero) }
    var showAiResponseDropdownMenu by remember { mutableStateOf(false) }
    var aiResponseDropdownMenuOffset by remember { mutableStateOf(Offset.Zero) }

    val localDensity = LocalDensity.current

    fun enterEditMode() {
        ttsViewModel.stopAllSpeaking() // Stop any TTS when entering edit mode
        activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
        inEditMode = true
    }

    fun exitEditMode() {
        inEditMode = false
        processDeletion = false
        processEditTopics = false
        newContentNeedToBeSaved = false

        // TTS state is not reset here as user might want to continue listening
    }

    // LaunchedEffect to update editedAiResponse and editedTopics when verseItem is loaded
    LaunchedEffect(verseItem) {
        verseItem?.let {
            editedAiResponse = it.aiResponse

            // Only reset topics if not returning from topic selection
            if (!processEditTopics) editedTopics = it.topics
        }
    }

    // Effect to reset edited values if verseItem changes and we are not in edit mode
    LaunchedEffect(verseItem, inEditMode) {
        if (!inEditMode) {
            verseItem?.let {
                editedAiResponse = it.aiResponse
                editedTopics = it.topics
                newContentNeedToBeSaved = false // Reset flag as content matches source
            }
        }
    }

    // --- Handle Navigation Results ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedStateHandle = currentBackStackEntry?.savedStateHandle

    // Use LaunchedEffect tied to lifecycle to observe results from SavedStateHandle
    LaunchedEffect(savedStateHandle, lifecycleOwner.lifecycle) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {

            // Observe Topic Result
            launch {
                savedStateHandle?.getStateFlow<String?>(TOPIC_SELECTION_RESULT_KEY, null)
                    ?.filterNotNull() // Process only non-null results
                    ?.collect { resultJson ->
                        if (resultJson.isNotEmpty()) { // Additional check for empty string
                            Log.d("VerseDetail", "Received topics JSON: $resultJson")
                            try {
                                selectedTopics = Json.decodeFromString(
                                    ListSerializer(String.serializer()),
                                    resultJson
                                )
                                editedTopics = selectedTopics

                                // Need to explicitly remain into edit mode to process new topic selection.
                                // Otherwise it will recompose and reset edit mode to false
                                enterEditMode()

                                processEditTopics = true
                                newContentNeedToBeSaved = true
                            } catch (e: Exception) {
                                Log.e("VerseDetail", "Error deserializing topics: ${e.message}", e)
                            }
                        } else {
                            // Handle case where the result might be an empty string if that's possible
                            Log.d("VerseDetail", "Received empty topics JSON, removing key $TOPIC_SELECTION_RESULT_KEY")
                            editedTopics = emptyList()
                        }

                        // Always remove the result from SavedStateHandle after processing (successfully or not)
                        Log.d("VerseDetail", "Removing key $TOPIC_SELECTION_RESULT_KEY from SavedStateHandle")

                        savedStateHandle.remove<String>(TOPIC_SELECTION_RESULT_KEY)
                    }
            }  // launch
        }
    }

    /***
     *
     * It's good practice to stop TTS when the screen is disposed if it shouldn't continue playing.
     * However, your TtsViewModel's onCleared handles shutdown, so direct calls might only be
     * needed if you want to stop speech specifically when leaving this screen but not others.
     *
     * If you want speech to stop when navigating away from VerseDetailScreen:
     */
    DisposableEffect(Unit) {
        onDispose {
            // Decide if you want to stop speech when leaving this screen.
            // If TtsViewModel is shared and meant to play across screens, don't stop here.
            // If speech is specific to this verse detail, then stop.
            Log.d("VerseDetailScreen", "Disposing VerseDetailScreen, stopping all TTS.")
            ttsViewModel.stopAllSpeaking() // Uncomment if speech should stop on leaving this screen

            // ViewModel's onCleared should handle TTS shutdown when ViewModel is cleared.

        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    var titleString =
                        if (verseItem != null) {
                            verseReference(verseItem)
                        } else {
                            "Loading..."
                        }
                    Text(
                        buildAnnotatedString {
                            append(titleString)
                            if (inEditMode) {
                                withStyle(style = SpanStyle(color = editModeColor)) { // Add this style for red color
                                    append(" [Edit Mode]")
                                }
                            }
                        }
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(Screen.Home) },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = false,
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
                    selected = false,
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
            val scriptureTextContent = verseItem?.scripture ?: "Loading scripture...."

            // --- Scripture Box ---
            LabeledOutlinedBox(
                label = "Scripture",
                modifier = Modifier.fillMaxWidth().weight(0.4f).heightIn(min = 50.dp)
            ) {
                // Scripture Box
                if (inEditMode) {
                    BasicTextField(
                        value = scriptureTextContent,
                        onValueChange = { /* Read-only */ },
                        modifier = Modifier.fillMaxSize().verticalScroll(scriptureScrollState),
                        readOnly = true,
                        textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    val baseTextColor = MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }

                    // Determine if scripture should be highlighted (either single TTS or sequence TTS)
                    val isScriptureTargetedForTts =
                        (activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.SCRIPTURE && currentTtsOperationMode == TTS_OperationMode.SINGLE_TEXT) ||
                                (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && currentTtsSequencePart == VerseDetailSequencePart.SCRIPTURE)

                    val scriptureAnnotatedText = buildAnnotatedStringForTts(
                        fullText = scriptureTextContent,
                        isTargeted = isScriptureTargetedForTts,
                        highlightSentenceIndex = currentTtsSentenceIndex,
                        isSpeaking = isTtsSpeaking,
                        isPaused = isTtsPaused,
                        baseStyle = SpanStyle(color = baseTextColor),
                        highlightStyle = SpanStyle(
                            background = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        applyMarkdownFormatting = true
                    )

                    Box(modifier = Modifier.fillMaxSize()) { // Box to anchor dropdown
                        Text(
                            text = scriptureAnnotatedText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { touchOffset -> // touchOffset is the raw pixel offset
                                            if (isTtsInitialized && scriptureTextContent.isNotBlank()) {
                                                scriptureDropdownMenuOffset = touchOffset // Store the touch position
                                                showScriptureDropdownMenu = true
                                            }
                                        }
                                    )
                                }
                        )

                        // Approximate height of a single menu item plus some padding
                        val menuItemVerticalShift = 300.dp

                        DropdownMenu(
                            expanded = showScriptureDropdownMenu,
                            onDismissRequest = { showScriptureDropdownMenu = false },
                            offset = DpOffset(
                                x = with(localDensity) { scriptureDropdownMenuOffset.x.toDp() },
                                y = with(localDensity) { scriptureDropdownMenuOffset.y.toDp() } - menuItemVerticalShift
                            )
                        ) {
                            DropdownMenuItem(
                                text = { Text("Read from beginning") },
                                onClick = {
                                    showScriptureDropdownMenu = false
                                    if (isTtsInitialized && scriptureTextContent.isNotBlank()) {
                                        activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.SCRIPTURE
                                        // Stop any other TTS (like sequence) before starting this single text
                                        if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                            ttsViewModel.stopAllSpeaking()
                                        }
                                        ttsViewModel.restartSingleText(scriptureTextContent)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Space between boxes

            // --- Key Take-away Box ---
            LabeledOutlinedBox(
                label = "Key Take-away",
                modifier = Modifier.fillMaxWidth().weight(0.4f).heightIn(min = 50.dp)
            ) {
                if (inEditMode) {
                    BasicTextField(
                        value = editedAiResponse,
                        onValueChange = {
                            editedAiResponse = it
                            newContentNeedToBeSaved = true
                        },
                        modifier = Modifier.fillMaxSize().verticalScroll(aiResponseScrollState),
                        readOnly = false, // Editable in edit mode
                        textStyle = LocalTextStyle.current.copy(color = editModeColor),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    )
                } else {
                    val baseTextColor =
                        MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }

                    val isAiResponseTargetedForTts =
                        (activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.AI_RESPONSE && currentTtsOperationMode == TTS_OperationMode.SINGLE_TEXT) ||
                                (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && currentTtsSequencePart == VerseDetailSequencePart.AI_RESPONSE)

                    val aiResponseAnnotatedText = buildAnnotatedStringForTts(
                        fullText = editedAiResponse,
                        isTargeted = isAiResponseTargetedForTts,
                        highlightSentenceIndex = currentTtsSentenceIndex,
                        isSpeaking = isTtsSpeaking,
                        isPaused = isTtsPaused,
                        baseStyle = SpanStyle(color = baseTextColor),
                        highlightStyle = SpanStyle(
                            background = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        applyMarkdownFormatting = true
                    )

                    Box(modifier = Modifier.fillMaxSize()) { // Box to anchor dropdown
                        Text(
                            text = aiResponseAnnotatedText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { offset ->
                                            aiResponseDropdownMenuOffset = offset
                                            showAiResponseDropdownMenu = true
                                        }
                                    )
                                }
                        )
                        DropdownMenu(
                            expanded = showAiResponseDropdownMenu,
                            onDismissRequest = { showAiResponseDropdownMenu = false },
                            offset = DpOffset(x = with(localDensity){aiResponseDropdownMenuOffset.x.toDp()}, y = with(localDensity){aiResponseDropdownMenuOffset.y.toDp()})
                        ) {
                            DropdownMenuItem(
                                text = { Text("Read from beginning") },
                                enabled = isTtsInitialized && editedAiResponse.isNotBlank(),
                                onClick = {
                                    showAiResponseDropdownMenu = false
                                    if (isTtsInitialized && editedAiResponse.isNotBlank()) {
                                        activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.AI_RESPONSE
                                        if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                            ttsViewModel.stopAllSpeaking()
                                        }
                                        ttsViewModel.restartSingleText(editedAiResponse)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    showAiResponseDropdownMenu = false
                                    enterEditMode()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Space between "Key Take-away" box and "topic" box

            // --- Topics Box ---

            val topicScrollState = rememberScrollState()

            var topicLabel = "Topic"
            if (editedTopics.size > 1) topicLabel = "${editedTopics.count()} Topics"
            LabeledOutlinedBox(
                label = topicLabel,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                // Use FlowRow to allow chips to wrap to the next line
                if ((inEditMode) || (processEditTopics)) {
                    Column (
                        modifier = Modifier
                            .heightIn(max = (48 * 2).dp) // Set maximum height
                            .verticalScroll(topicScrollState) // Enable vertical scrolling
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp), // Space between chips horizontally
                            verticalArrangement = Arrangement.spacedBy(2.dp) // Space between rows of chips
                        ) {
                            editedTopics.forEach { topic ->
                                // Display each topic as a chip
                                SuggestionChip(
                                    onClick =
                                        {
                                            // Serialize editedTopics to JSON string
                                            val editedTopicsJson = try {
                                                Json.encodeToString(
                                                    ListSerializer(String.serializer()),
                                                    editedTopics
                                                )
                                            } catch (e: Exception) {
                                                Log.e(
                                                    "VerseDetail",
                                                    "Error serializing edited topics: ${e.message}",
                                                    e
                                                )
                                                null // Pass null on error
                                            }
                                            // Navigate with the JSON string as an argument
                                            navController.navigate(
                                                Screen.TopicSelectionScreen(
                                                    selectedTopicsJson = editedTopicsJson
                                                )
                                            )
                                        },
                                    label = {
                                        Text(
                                            topic,
                                            color = if (inEditMode) editModeColor else LocalContentColor.current
                                        )
                                    },
                                    colors = if (inEditMode) { // Conditionally apply chip colors
                                        SuggestionChipDefaults.suggestionChipColors(
                                            containerColor = editModeColor.copy(alpha = 0.1f), // Example: Light red background
                                            labelColor = editModeColor // Text color handled in Text composable
                                        )
                                    } else {
                                        SuggestionChipDefaults.suggestionChipColors() // Use default colors when not editing
                                    }
                                )
                            }
                        }
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp), // Space between chips horizontally
                        verticalArrangement = Arrangement.spacedBy(4.dp) // Space between rows of chips
                    ) {
                        editedTopics.forEach { topic ->
                            // Display each topic as a read-only chip
                            SuggestionChip(
                                onClick = { /* Read-only, do nothing on click */ },
                                label = { Text(topic) },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp)) // Space before the button

            // --- Action Buttons ===
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            )
            {
                val snackBarScope = rememberCoroutineScope()

                // Save Button
                Button(
                    onClick = {
                        if (inEditMode) {
                            if (newContentNeedToBeSaved) { // Check if there are actual changes to save
                                verseItem?.let {
                                    // Perform save operation
                                    if (processEditTopics) editedTopics = selectedTopics

                                    bibleViewModel.updateVerse(
                                        verseItem.copy(
                                            aiResponse = editedAiResponse,
                                            topics = editedTopics  // Use the latest editedTopics
                                        )
                                    )

                                    snackBarScope.launch {
                                        SnackBarController.showMessage("Verse is saved")
                                    }

                                    // The following will trigger display save content and
                                    // do final clean-up for these flags:
                                    //  - inEditingMode will set to false
                                    //  - processEditTopic will set to false
                                    //  - isUpdatedContent will be set to false
                                    exitEditMode()
                                }
                            } else {
                                exitEditMode() // No changes, just exit edit mode
                            }
                        } else {
                            enterEditMode()
                        }
                    },
                    enabled = verseItem != null,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (inEditMode) {
                        Text(if (newContentNeedToBeSaved) "Save" else "Done")
                    } else {
                        Icon(imageVector = Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete / Read Aloud Button (Box for dropdown)
                // Use a Box to anchor the DropdownMenu to the Button
                Box(modifier = Modifier.weight(1f)) {
                    Button(
                        onClick = { /* onTap in pointerInput handles clicks */ },
                        enabled = (verseItem != null && (!inEditMode || isTtsInitialized)), // Enable read aloud if not in edit mode
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        // Determine Icon based on state
                        val iconToShow = when {
                            inEditMode -> Icons.Filled.Delete // Or your preferred delete icon
                            isTtsSpeaking && !isTtsPaused -> Icons.Filled.Pause
                            isTtsPaused -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.RecordVoiceOver // Default "Read Aloud" icon
                        }
                        val contentDescription = when {
                            inEditMode -> "Delete"
                            isTtsSpeaking && !isTtsPaused -> "Pause"
                            isTtsPaused -> "Resume"
                            else -> "Read Aloud"
                        }
                        Icon(imageVector = iconToShow, contentDescription = contentDescription)
                    }

                    // Add a transparent overlay for detecting gestures
                    Box(modifier = Modifier
                        .matchParentSize()  // Match the size of the parent Box
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    showButtonDropdownMenu = true
                                    Log.d("VerseDetailScreen", "Long press detected!")
                                },


                                onTap = {
                                    if (inEditMode) {
                                        processDeletion = true
                                    } else {
                                        // TTS related actions
                                        if (isTtsInitialized && verseItem != null) {
                                            val currentMode = currentTtsOperationMode
                                            val speaking = isTtsSpeaking
                                            val paused = isTtsPaused

                                            // Scenario 1: Single text TTS is active for a block on THIS screen
                                            if (currentMode == TTS_OperationMode.SINGLE_TEXT &&
                                                (activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.AI_RESPONSE ||
                                                        activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.SCRIPTURE) &&
                                                (speaking || paused) // Make sure it's actually running/paused for single text
                                            ) {
                                                val textToToggle = if (activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.AI_RESPONSE) {
                                                    editedAiResponse
                                                } else {
                                                    scriptureTextContent
                                                }
                                                ttsViewModel.togglePlayPauseResumeSingleText(textToToggle)
                                            }
                                            // Scenario 2: Verse detail sequence is active (speaking or paused)
                                            else if (currentMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (speaking || paused)) {
                                                activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE // Clear single text target
                                                ttsViewModel.togglePlayPauseResumeVerseDetailSequence()
                                            }
                                            // Scenario 3: No specific TTS is active/paused for this screen's content, or TTS is stopped
                                            // Start the full verse detail sequence.
                                            else {
                                                val scripture = verseItem.scripture
                                                val aiTakeaway = verseItem.aiResponse
                                                if (scripture.isNotBlank()) { // Ensure there's scripture to read
                                                    activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
                                                    ttsViewModel.startVerseDetailSequence(
                                                        scripture = scripture,
                                                        aiResponse = aiTakeaway,
                                                        verseItem = verseItem
                                                    )
                                                } else {
                                                    Log.w("VerseDetailScreen", "Scripture is blank, cannot start sequence.")
                                                }
                                            }
                                        } else if (!isTtsInitialized) {
                                            Log.w("VerseDetailScreen", "TTS not initialized. Cannot perform TTS action.")
                                            snackBarScope.launch { SnackBarController.showMessage("TTS is not ready yet.") }
                                        }
                                    }   // !inEditMode
                                }  // OnTap
                            )  // detectTapGestures
                        }   // ,pointerInput
                    )  // Box

                    // DropdownMenu for long press, anchored to the Button's parent Box
                    DropdownMenu(
                        expanded = showButtonDropdownMenu,
                        onDismissRequest = { showButtonDropdownMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All") }, // Clarified label
                            enabled = isTtsInitialized && verseItem != null && verseItem.scripture.isNotBlank(),
                            onClick = {
                                showButtonDropdownMenu = false
                                if (isTtsInitialized && verseItem != null) {
                                    activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
                                    ttsViewModel.startVerseDetailSequence(
                                        scripture = verseItem.scripture,
                                        aiResponse = verseItem.aiResponse,
                                        verseItem = verseItem
                                    )
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Scripture (Only)") },
                            enabled = isTtsInitialized && scriptureTextContent.isNotBlank(),
                            onClick = {
                                showButtonDropdownMenu = false
                                activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.SCRIPTURE
                                // If a sequence is running, stop it before starting single text.
                                if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                    ttsViewModel.stopAllSpeaking()
                                }
                                ttsViewModel.restartSingleText(scriptureTextContent) // Use restart to ensure clean start
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Key Take-Away (Only)") },
                            enabled = isTtsInitialized && editedAiResponse.isNotBlank(),
                            onClick = {
                                showButtonDropdownMenu = false
                                activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.AI_RESPONSE
                                if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                    ttsViewModel.stopAllSpeaking()
                                }
                                ttsViewModel.restartSingleText(editedAiResponse) // Use restart
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Stop Speaking") },
                            enabled = isTtsInitialized && (isTtsSpeaking || isTtsPaused),
                            onClick = {
                                showButtonDropdownMenu = false
                                ttsViewModel.stopAllSpeaking()
                                activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
                            }
                        )
                    }
                }  // End of Box for Delete/Read Aloud Button

                Spacer(modifier = Modifier.width(8.dp))

                // Memorize/Cancel Button
                Button(
                    onClick = {
                        if (inEditMode) {
                            if (newContentNeedToBeSaved) {
                                confirmExitWithoutSaving = true
                            } else {
                                exitEditMode()
                            }
                        } else {
                            // If Memorize also uses TTS, you might want to stop current TTS
                            ttsViewModel.stopAllSpeaking() // Stop any TTS before memorize
                            navController.navigate(Screen.MemorizeScreen(verseID))
                        }
                    },
                    enabled = (!inEditMode) || (newContentNeedToBeSaved),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val buttonString = if (inEditMode) {
                        "Cancel"
                    } else {
                        "Memorize"
                    }
                    Text(buttonString)
                }
            }

            // Confirmation Dialog to delete the verse
            if (processDeletion && (verseItem != null)) {
                val snackBarScope = rememberCoroutineScope()

                AlertDialog(
                    onDismissRequest = { processDeletion = false },
                    title = { Text("Confirm Delete") },
                    text = { Text("Are you sure you want to delete this verse?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                bibleViewModel.deleteVerse(verseItem)

                                snackBarScope.launch {
                                    SnackBarController.showMessage("Verse is deleted")
                                }

                                // The following will do the clean-up for these flags
                                //  - inEditingMode will set to false
                                //  - processDeletion will set to false
                                //  - isUpdatedContent will be set to false
                                exitEditMode() // Should navigate away or clear verse after deletion
                                navController.navigate(Screen.MeditateScreen)
                            }
                        ) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { processDeletion = false }
                        ) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if ((confirmExitWithoutSaving) && (newContentNeedToBeSaved)) {
                AlertDialog(
                    onDismissRequest = {
                        confirmExitWithoutSaving = false
                    },
                    title = { Text("Confirm Cancel") },
                    text = {
                        Text(
                            "You have made changes that has not been saved. " +
                                    "Are you sure you want to cancel?"
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                // Restore original values
                                verseItem?.let {
                                    editedAiResponse = it.aiResponse
                                    editedTopics = it.topics
                                }
                                confirmExitWithoutSaving = false
                                exitEditMode()
                            }
                        ) {
                            Text("Yes, discard changes. Exit edit mode")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { confirmExitWithoutSaving = false }
                        ) {
                            Text("Cancel. Back to edit mode")
                        }
                    }
                )
            }
        }
    }
}

// Helper function to build AnnotatedString with potential TTS highlighting & Markdown
@Composable
private fun buildAnnotatedStringForTts(
    fullText: String,
    isTargeted: Boolean,
    highlightSentenceIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    baseStyle: SpanStyle,
    highlightStyle: SpanStyle,
    applyMarkdownFormatting: Boolean = false
): AnnotatedString {

    val textToProcess = fullText.replace(".**", "**.")

    val sentences = remember(textToProcess, Locale.getDefault()){
        splitIntoSentences(textToProcess, Locale.getDefault())
    }

    // This list will store the start and end indices of each sentence within the original text.
    val sentenceRanges = remember(textToProcess, sentences) {
        val ranges = mutableListOf<IntRange>()
        var currentSearchStart = 0
        sentences.forEach { sentence ->
            // Find the sentence in the original text, ignoring leading/trailing whitespace
            val startIndex = textToProcess.indexOf(sentence.trim(), currentSearchStart)
            if (startIndex != -1) {
                val endIndex = startIndex + sentence.trim().length - 1
                ranges.add(IntRange(startIndex, endIndex))
                currentSearchStart = endIndex + 1 // Start searching for the next sentence after this one
            } else {
                // Fallback for cases where sentence might not be found exactly (e.g., due to split differences)
                // This might happen if BreakIterator splits differently than how it appears in the raw text.
                // For simplicity, we'll just advance search start. A more robust solution might involve
                // more complex string matching.
                currentSearchStart += sentence.length // Just advance by expected length
                ranges.add(IntRange(0, -1)) // Add an invalid range to maintain index
            }
        }
        ranges
    }

    return buildAnnotatedString {
        var currentTextIndex = 0
        while (currentTextIndex < textToProcess.length) {
            var appliedStyle = baseStyle
            var textSegment = ""
            var foundSentence = false

            // Find which sentence this currentTextIndex falls into
            for (i in sentences.indices) {
                if (i < sentenceRanges.size && sentenceRanges[i].first <= currentTextIndex && currentTextIndex <= sentenceRanges[i].last) {
                    val sentence = sentences[i]
                    val shouldHighlightThisSentence = isTargeted &&
                            ((isSpeaking && !isPaused && i == highlightSentenceIndex) ||
                                    (isPaused && i == highlightSentenceIndex))

                    appliedStyle = if (shouldHighlightThisSentence) highlightStyle else baseStyle
                    textSegment = textToProcess.substring(currentTextIndex, sentenceRanges[i].last + 1)
                    currentTextIndex = sentenceRanges[i].last + 1
                    foundSentence = true
                    break
                }
            }

            if (!foundSentence) {
                // If currentTextIndex is not part of any tracked sentence, it's likely whitespace or
                // a newline that BreakIterator ignored, or text before the first sentence.
                // Find the next sentence start or the end of the string.
                var nextSentenceStart = textToProcess.length
                for (i in sentences.indices) {
                    if (i < sentenceRanges.size && sentenceRanges[i].first > currentTextIndex) {
                        nextSentenceStart = sentenceRanges[i].first
                        break
                    }
                }
                textSegment = textToProcess.substring(currentTextIndex, nextSentenceStart)
                currentTextIndex = nextSentenceStart
            }

            if (applyMarkdownFormatting) {
                withStyle(appliedStyle) {
                    // Process bullet points for paragraphs starting with "* "
                    val lines = textSegment.split("\n")
                    val processedSegment = lines.joinToString("\n") { line ->
                        if (line.trim().startsWith("* ")) {
                            "• " + line.trim().substring(2)
                        } else {
                            line
                        }
                    }

                    val textToProcess = processedSegment

                    // Completely rewritten approach to handle both bold and italic formatting
                    // This approach ensures consistent handling of all formatting instances

                    // Step 1: Create a list of all formatting markers with their positions and types
                    data class Marker(val position: Int, val isStart: Boolean, val type: String)
                    val markers = mutableListOf<Marker>()

                    // Find all bold markers (**) and add them to the list
                    val boldRegex = Regex("""\*\*""")
                    boldRegex.findAll(textToProcess).forEach { match ->
                        val position = match.range.first
                        // Count the number of ** before this position to determine if it's even (start) or odd (end)
                        val previousMarkers = boldRegex.findAll(textToProcess.substring(0, position)).count()
                        val isStart = previousMarkers % 2 == 0
                        markers.add(Marker(position, isStart, "bold"))
                    }

                    // Find all italic markers (*) that are not part of bold markers
                    val italicRegex = Regex("""\*(?!\*)""")
                    italicRegex.findAll(textToProcess).forEach { match ->
                        val position = match.range.first
                        // Check if this * is part of a ** (bold marker)
                        val isBoldMarker = textToProcess.substring(position, minOf(position + 2, textToProcess.length)) == "**" ||
                                (position > 0 && textToProcess.substring(position - 1, position + 1) == "**")

                        if (!isBoldMarker) {
                            // Count previous standalone * markers to determine if start or end
                            val previousMarkers = italicRegex.findAll(textToProcess.substring(0, position)).count {
                                val pos = it.range.first
                                val isPartOfBold = textToProcess.substring(pos, minOf(pos + 2, textToProcess.length)) == "**" ||
                                        (pos > 0 && textToProcess.substring(pos - 1, pos + 1) == "**")
                                !isPartOfBold
                            }
                            val isStart = previousMarkers % 2 == 0
                            markers.add(Marker(position, isStart, "italic"))
                        }
                    }

                    // Sort markers by position
                    val sortedMarkers = markers.sortedBy { it.position }

                    // Step 2: Process the text using the markers
                    var currentPos = 0
                    var boldActive = false
                    var italicActive = false

                    // Create stacks to track active formatting
                    val formattingStack = mutableListOf<String>() // "bold" or "italic"

                    // Break down text into segments and process each with appropriate formatting
                    for (i in sortedMarkers.indices) {
                        val marker = sortedMarkers[i]

                        // Append text before this marker with current formatting
                        if (marker.position > currentPos) {
                            val textBefore = textToProcess.substring(currentPos, marker.position)

                            // Apply current formatting
                            if (boldActive && italicActive) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color=Color.Yellow)) {
                                    append(textBefore)
                                }
                            } else if (boldActive) {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Yellow)) {
                                    append(textBefore)
                                }
                            } else if (italicActive) {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(textBefore)
                                }
                            } else {
                                append(textBefore)
                            }
                        }

                        // Update the current position based on marker type
                        if (marker.type == "bold") {
                            currentPos = marker.position + 2 // Skip past "**"
                        } else {
                            currentPos = marker.position + 1 // Skip past "*"
                        }

                        // Process the marker - update formatting state
                        if (marker.type == "bold") {
                            if (marker.isStart) {
                                boldActive = true
                                formattingStack.add("bold")
                            } else {
                                boldActive = false
                                // Remove the last "bold" from the stack
                                val lastBoldIndex = formattingStack.lastIndexOf("bold")
                                if (lastBoldIndex != -1) {
                                    formattingStack.removeAt(lastBoldIndex)
                                }
                            }
                        } else if (marker.type == "italic") {
                            if (marker.isStart) {
                                italicActive = true
                                formattingStack.add("italic")
                            } else {
                                italicActive = false
                                // Remove the last "italic" from the stack
                                val lastItalicIndex = formattingStack.lastIndexOf("italic")
                                if (lastItalicIndex != -1) {
                                    formattingStack.removeAt(lastItalicIndex)
                                }
                            }
                        }
                    }

                    // Append any remaining text after the last marker
                    if (currentPos < textToProcess.length) {
                        val remainingText = textToProcess.substring(currentPos)

                        // Apply current formatting to remaining text
                        if (boldActive && italicActive) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                                append(remainingText)
                            }
                        } else if (boldActive) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(remainingText)
                            }
                        } else if (italicActive) {
                            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                append(remainingText)
                            }
                        } else {
                            append(remainingText)
                        }
                    }
                }
            } else {
                withStyle(appliedStyle) {
                    append(textSegment)
                }
            }
        }
    }
}

private fun splitIntoSentences(text: String, locale: Locale): List<String> {
    if (text.isBlank()) return emptyList()
    val iterator = BreakIterator.getSentenceInstance(locale)
    iterator.setText(text)
    val sentenceList = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val sentence = text.substring(start, end).trim()
        if (sentence.isNotEmpty()) {
            sentenceList.add(sentence)
        }
        start = end
        end = iterator.next()
    }
    return sentenceList
}