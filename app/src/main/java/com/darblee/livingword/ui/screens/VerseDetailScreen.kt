package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import com.darblee.livingword.Global
import com.darblee.livingword.Global.TOPIC_SELECTION_RESULT_KEY
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.Screen
import com.darblee.livingword.SnackBarController
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.Verse
import com.darblee.livingword.data.verseReference
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.TTSViewModel
import com.darblee.livingword.domain.model.TTS_OperationMode
import com.darblee.livingword.domain.model.VerseDetailSequencePart
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption

// Helper extension function to append text with verse number styling
fun AnnotatedString.Builder.appendWithVerseStyling(
    text: String,
    style: SpanStyle // This is the base style (e.g., from TTS highlight, markdown)
) {
    val passageNumberRegex = Regex("""\[(\d+)]""")
    var lastIndex = 0
    passageNumberRegex.findAll(text).forEach { matchResult ->
        // Append text before the verse number with the current style
        if (matchResult.range.first > lastIndex) {
            withStyle(style) {
                append(text.substring(lastIndex, matchResult.range.first))
            }
        }
        // Append the verse number itself (digits only) with red subscript style
        // This style overrides the incoming 'style' for the verse number part.
        withStyle(
            style = SpanStyle(
                color = Color.Red,
                fontSize = 10.sp,
                baselineShift = BaselineShift.Superscript,
                fontWeight = FontWeight.Bold)
        ) {
            append(matchResult.groupValues[1]) // Append only the number, not the brackets
        }
        lastIndex = matchResult.range.last + 1
    }
    // Append remaining text after the last verse number with the current style
    if (lastIndex < text.length) {
        withStyle(style) {
            append(text.substring(lastIndex))
        }
    }
}

// Enum to track which text block is targeted for single TTS playback in this screen
enum class VerseDetailSingleTtsTarget { NONE, SCRIPTURE, AI_RESPONSE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerseDetailScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    ttsViewModel: TTSViewModel = viewModel(),
    verseID: Long,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    editMode: Boolean,

    ) {
    // Remember scroll states for the text fields (UI concern)
    val aiResponseScrollState = rememberScrollState()

    // Observe the verse as a state from a Flow. This will automatically update
    // when the data changes in the database.
    val verseItem by bibleViewModel.getVerseFlow(verseID).collectAsStateWithLifecycle(initialValue = null)


    Log.i("VerseDetailScreen", "Translation = ${verseItem?.translation}")

    // State for controlling the edit mode
    var inEditMode by remember { mutableStateOf(editMode) }

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
    var expandedTranslation by remember { mutableStateOf(false) }

    val localDensity = LocalDensity.current

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val preferenceStore = remember { PreferenceStore(context) }

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
            editedAiResponse = it.aiTakeAwayResponse

            // Only reset topics if not returning from topic selection
            if (!processEditTopics) editedTopics = it.topics
        }
    }

    // --- Handle Navigation Results ---
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackStackEntry = navController.currentBackStackEntry
    val savedStateHandle = currentBackStackEntry?.savedStateHandle

    // Use LaunchedEffect tied to lifecycle to observe results from SavedStateHandle
    LaunchedEffect(savedStateHandle, lifecycleOwner.lifecycle, verseItem) {
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

    AppScaffold(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val titleString =
                    if (verseItem != null) {
                        verseReference(verseItem!!)
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
                Spacer(modifier = Modifier.width(8.dp))
                if (verseItem != null) {
                    IconButton(onClick = {
                        bibleViewModel.updateFavoriteStatus(verseItem!!.id, !verseItem!!.favorite)
                    }) {
                        Icon(
                            imageVector = if (verseItem!!.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (verseItem!!.favorite) Color.Red else LocalContentColor.current
                        )
                    }
                }
            }
        },
        navController = navController,
        currentScreenInstance = Screen.VerseDetailScreen(verseID, inEditMode),
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = {  paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues) // Apply padding from Scaffold
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Adjusted vertical padding
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // --- Scripture Box ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .heightIn(min = 50.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Scripture",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, end = 0.dp),
                            fontSize = 15.sp
                        )

                        // Dropdown for translation selection
                        val selectedTranslation = verseItem?.translation ?: ""

                        TextButton(
                            onClick = { expandedTranslation = true },
                            shape = RoundedCornerShape(0.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.heightIn(max = 24.dp).widthIn(max = 40.dp)) // Adjust height as needed
                        {
                            Text(text = "($selectedTranslation)",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal)
                        }
                        DropdownMenu(
                            expanded = expandedTranslation,
                            onDismissRequest = { expandedTranslation = false }
                        ) {
                            Global.bibleTranslations.forEach { translation ->
                                DropdownMenuItem(
                                    text = { Text(translation) }, // Display the translation name
                                    onClick = { // This lambda is executed when the item is clicked
                                        expandedTranslation = false // Close the dropdown
                                        if (verseItem != null && verseItem?.translation != translation) {
                                            // Save the preference and reload the verse content
                                            coroutineScope.launch {
                                                preferenceStore.saveTranslationToSetting(translation)
                                            }
                                            bibleViewModel.reloadVerseWithNewTranslation(verseItem!!.id, translation)
                                        }
                                    })
                            }
                        }
                    }

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
                        // Scripture Box
                        val baseTextColor = MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }

                        // Determine if scripture should be highlighted (either single TTS or sequence TTS)
                        val isScriptureTargetedForTts =
                            (activeSingleTtsTextBlock == VerseDetailSingleTtsTarget.SCRIPTURE && currentTtsOperationMode == TTS_OperationMode.SINGLE_TEXT) ||
                                    (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && currentTtsSequencePart == VerseDetailSequencePart.SCRIPTURE)


                        // Use the new composable for scriptureJson
                        val scriptureAnnotatedText = if (verseItem != null) {
                            buildAnnotatedStringForScripture(
                                scriptureVerses = verseItem!!.scriptureVerses,
                                isTargeted = isScriptureTargetedForTts,
                                highlightSentenceIndex = currentTtsSentenceIndex,
                                isSpeaking = isTtsSpeaking,
                                isPaused = isTtsPaused,
                                baseStyle = SpanStyle(color = baseTextColor),
                                highlightStyle = SpanStyle(
                                    background = MaterialTheme.colorScheme.primaryContainer,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        } else {
                            buildAnnotatedString { append("Loading scripture....") }
                        }

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
                                                if (isTtsInitialized && verseItem != null) {
                                                    scriptureDropdownMenuOffset =
                                                        touchOffset // Store the touch position
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
                                        if (isTtsInitialized && verseItem != null) {
                                            activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.SCRIPTURE
                                            // Stop any other TTS (like sequence) before starting this single text
                                            if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                                ttsViewModel.stopAllSpeaking()
                                            }
                                            val fullScriptureTextForTTS = verseItem!!.scriptureVerses.joinToString(" ") { it.verseString }
                                            ttsViewModel.restartSingleText(fullScriptureTextForTTS)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(aiResponseScrollState),
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

                        val aiResponseAnnotatedText = buildAnnotatedStringForTTS(
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
                                .verticalScroll(topicScrollState), // Enable vertical scrolling
                        ) {
                            if ((editedTopics.count() == 1) && (editedTopics[0] == "")) {
                                Text(text = "Click here to add topic(s)", modifier = Modifier.clickable { // Serialize editedTopics to JSON string
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
                                    ) })
                            } else {
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
                    // Save Button
                    Button(
                        onClick = {
                            if (inEditMode) {
                                if (newContentNeedToBeSaved) { // Check if there are actual changes to save
                                    verseItem?.let {
                                        coroutineScope.launch {
                                            // Perform save operation
                                            if (processEditTopics) editedTopics = selectedTopics

                                            Log.i(
                                                "VerseDetailScreen",
                                                "Passing in take away text to BibleVIewModel : $editedAiResponse"
                                            )
                                            bibleViewModel.updateVerse(
                                                it.copy(
                                                    aiTakeAwayResponse = editedAiResponse,
                                                    topics = editedTopics  // Use the latest editedTopics
                                                )
                                            )

                                            // Since updateVerse is a suspend function, it will not return until
                                            // verse has been updated in database.
                                            // Now, we cam  notify user and do cleam-up

                                            SnackBarController.showMessage("Verse is saved")

                                            // Do final clean-up for these flags:
                                            //  - inEditingMode will set to false
                                            //  - processEditTopic will set to false
                                            //  - isUpdatedContent will be set to false
                                            exitEditMode()
                                        }
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
                                else -> Icons.Filled.Headset // Default "Read Aloud" icon
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
                                                        verseItem!!.scriptureVerses.joinToString(" ") { it.verseString }
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
                                                    val aiTakeaway = editedAiResponse
                                                    if (verseItem!!.scriptureVerses.isNotEmpty()) { // Ensure there's scripture to read
                                                        activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
                                                        ttsViewModel.startVerseDetailSequence(
                                                            aiResponse = aiTakeaway,
                                                            verseItem = verseItem!!
                                                        )
                                                    } else {
                                                        Log.w(
                                                            "VerseDetailScreen",
                                                            "Scripture is blank, cannot start sequence."
                                                        )
                                                    }
                                                }
                                            } else if (!isTtsInitialized) {
                                                Log.w(
                                                    "VerseDetailScreen",
                                                    "TTS not initialized. Cannot perform TTS action."
                                                )
                                                coroutineScope.launch { SnackBarController.showMessage("TTS is not ready yet.") }
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
                                enabled = isTtsInitialized && verseItem != null && verseItem!!.scriptureVerses.isNotEmpty(),
                                onClick = {
                                    showButtonDropdownMenu = false
                                    if (isTtsInitialized && verseItem != null) {
                                        activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.NONE
                                        ttsViewModel.startVerseDetailSequence(
                                            aiResponse = verseItem!!.aiTakeAwayResponse,
                                            verseItem = verseItem!!
                                        )
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Scripture (Only)") },
                                enabled = isTtsInitialized && verseItem != null && verseItem!!.scriptureVerses.isNotEmpty(),
                                onClick = {
                                    showButtonDropdownMenu = false
                                    activeSingleTtsTextBlock = VerseDetailSingleTtsTarget.SCRIPTURE
                                    // If a sequence is running, stop it before starting single text.
                                    if (currentTtsOperationMode == TTS_OperationMode.VERSE_DETAIL_SEQUENCE && (isTtsSpeaking || isTtsPaused)) {
                                        ttsViewModel.stopAllSpeaking()
                                    }
                                    val fullScriptureTextForTTS = verseItem?.scriptureVerses?.joinToString(" ") { it.verseString } ?: ""
                                    ttsViewModel.restartSingleText(fullScriptureTextForTTS) // Use restart to ensure clean start
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

                    // Engage/Cancel Button
                    Button(
                        onClick = {
                            if (inEditMode) {
                                if (newContentNeedToBeSaved) {
                                    confirmExitWithoutSaving = true
                                } else {
                                    exitEditMode()
                                }
                            } else {
                                // If Engage Screen also uses TTS, you might want to stop current TTS
                                ttsViewModel.stopAllSpeaking() // Stop any TTS before Engage Screen
                                navController.navigate(Screen.EngageScreen(verseID))
                            }
                        },
                        enabled = (!inEditMode) || (newContentNeedToBeSaved),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        val buttonString = if (inEditMode) {
                            "Cancel"
                        } else {
                            "Engage"
                        }
                        Text(buttonString)
                    }
                }

                // Confirmation Dialog to delete the verse
                if (processDeletion && (verseItem != null)) {
                    AlertDialog(
                        onDismissRequest = { processDeletion = false },
                        title = { Text("Confirm Delete") },
                        text = { Text("Are you sure you want to delete this verse?") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        bibleViewModel.deleteVerse(verseItem!!)

                                        // NOTE: deleteVerse() is a suspend call. Ii will wait
                                        // until delete operation is completed before reaching here.
                                        SnackBarController.showMessage("Verse is deleted")

                                        // The following will do the clean-up for these flags
                                        //  - inEditingMode will set to false
                                        //  - processDeletion will set to false
                                        //  - isUpdatedContent will be set to false
                                        exitEditMode() // Should navigate away or clear verse after deletion
                                        navController.navigate(Screen.AllVersesScreen)
                                    }
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
                                        editedAiResponse = it.aiTakeAwayResponse
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
    )
}

@Composable
fun buildAnnotatedStringForScripture(
    scriptureVerses: List<Verse>,
    isTargeted: Boolean,
    highlightSentenceIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    baseStyle: SpanStyle,
    highlightStyle: SpanStyle
): AnnotatedString {
    return buildAnnotatedString {
        scriptureVerses.forEachIndexed { index, verse ->
            // Determine if this verse should be highlighted
            val shouldHighlight = isTargeted &&
                    ((isSpeaking && !isPaused && index == highlightSentenceIndex) ||
                            (isPaused && index == highlightSentenceIndex))

            val currentStyle = if (shouldHighlight) highlightStyle else baseStyle

            // Append the verse number with superscript styling
            withStyle(
                style = SpanStyle(
                    color = Color.Red,
                    fontSize = 10.sp,
                    baselineShift = BaselineShift.Superscript,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(" ${verse.verseNum} ") // Add spacing around number for readability
            }

            // Append the verse text with the determined style (normal or highlighted)
            withStyle(style = currentStyle) {
                append(verse.verseString)
            }
        }
    }
}


// Helper function to build AnnotatedString with potential TTS highlighting & Markdown
@Composable
internal fun buildAnnotatedStringForTTS(
    fullText: String,
    isTargeted: Boolean,
    highlightSentenceIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    baseStyle: SpanStyle, // Base style for normal text (includes color from theme)
    highlightStyle: SpanStyle, // Style for highlighted TTS sentence
): AnnotatedString {

    // This text is what splitIntoSentences will work on. It includes [N].
    val textToProcess = fullText.replace(".**", "**.")

    val sentences = remember(textToProcess, Locale.getDefault()){
        splitIntoSentences(textToProcess, Locale.getDefault())
    }

    val sentenceRanges = remember(textToProcess, sentences) {
        val ranges = mutableListOf<IntRange>()
        var currentSearchStart = 0
        sentences.forEach { sentence ->
            val startIndex = textToProcess.indexOf(sentence.trim(), currentSearchStart)
            if (startIndex != -1) {
                val endIndex = startIndex + sentence.trim().length - 1
                ranges.add(IntRange(startIndex, endIndex))
                currentSearchStart = endIndex + 1
            } else {
                currentSearchStart += sentence.length
                ranges.add(IntRange(0, -1)) // Invalid range
            }
        }
        ranges
    }

    return buildAnnotatedString {
        var currentTextIndex = 0
        while (currentTextIndex < textToProcess.length) {
            var styleForCurrentSegment = baseStyle // Default to baseStyle
            var textSegmentToFormat = ""
            var foundSentence = false

            // Determine if the current part of the text falls within a sentence to be highlighted
            for (i in sentences.indices) {
                if (i < sentenceRanges.size && sentenceRanges[i].first <= currentTextIndex && currentTextIndex <= sentenceRanges[i].last) {
                    val shouldHighlightThisSentence = isTargeted &&
                            ((isSpeaking && !isPaused && i == highlightSentenceIndex) ||
                                    (isPaused && i == highlightSentenceIndex))

                    styleForCurrentSegment = if (shouldHighlightThisSentence) highlightStyle else baseStyle
                    textSegmentToFormat = textToProcess.substring(currentTextIndex, sentenceRanges[i].last + 1)
                    currentTextIndex = sentenceRanges[i].last + 1
                    foundSentence = true
                    break
                }
            }

            if (!foundSentence) {
                // Text is not part of a tracked sentence (e.g., whitespace between sentences)
                var nextSentenceStart = textToProcess.length
                for (i in sentences.indices) {
                    if (i < sentenceRanges.size && sentenceRanges[i].first > currentTextIndex) {
                        nextSentenceStart = sentenceRanges[i].first
                        break
                    }
                }
                textSegmentToFormat = textToProcess.substring(currentTextIndex, nextSentenceStart)
                currentTextIndex = nextSentenceStart
                styleForCurrentSegment = baseStyle // Non-sentence parts use base style
            }

            // Process bullet points first
            val lines = textSegmentToFormat.split("\n")
            val segmentWithBulletsHandled = lines.joinToString("\n") { line ->
                if (line.trim().startsWith("* ")) {
                    "• " + line.trim().substring(2)
                } else {
                    line
                }
            }

            // Now, apply markdown (bold/italic) and verse number styling to segmentWithBulletsHandled
            // The `styleForCurrentSegment` (from TTS highlighting) is the base for these further stylings.

            data class Marker(val position: Int, val isStart: Boolean, val type: String) // "bold" or "italic"
            val markers = mutableListOf<Marker>()

            val boldRegex = Regex("""\*\*""")
            boldRegex.findAll(segmentWithBulletsHandled).forEach { match ->
                val position = match.range.first
                val previousMarkersCount = boldRegex.findAll(segmentWithBulletsHandled.substring(0, position)).count()
                markers.add(Marker(position, previousMarkersCount % 2 == 0, "bold"))
            }

            val italicRegex = Regex("""\*(?!\*)""") // Negative lookahead for single *
            italicRegex.findAll(segmentWithBulletsHandled).forEach { match ->
                val position = match.range.first
                // Ensure this '*' is not part of a '**'
                val isPartOfBold = segmentWithBulletsHandled.substring(position, minOf(position + 2, segmentWithBulletsHandled.length)) == "**" ||
                        (position > 0 && segmentWithBulletsHandled.substring(position - 1, position + 1) == "**")
                if (!isPartOfBold) {
                    val previousItalicMarkersCount = italicRegex.findAll(segmentWithBulletsHandled.substring(0, position)).count {
                        val pos = it.range.first
                        val isItselfPartOfBold = segmentWithBulletsHandled.substring(pos, minOf(pos + 2, segmentWithBulletsHandled.length)) == "**" ||
                                (pos > 0 && segmentWithBulletsHandled.substring(pos - 1, pos + 1) == "**")
                        !isItselfPartOfBold // Count only standalone italics
                    }
                    markers.add(Marker(position, previousItalicMarkersCount % 2 == 0, "italic"))
                }
            }
            val sortedMarkers = markers.sortedBy { it.position }

            var currentPosInSegment = 0
            var isBoldActive = false
            var isItalicActive = false

            for (marker in sortedMarkers) {
                if (marker.position > currentPosInSegment) {
                    val textPart = segmentWithBulletsHandled.substring(currentPosInSegment, marker.position)
                    var finalStyleForPart = styleForCurrentSegment // Start with TTS style
                    if (isBoldActive) finalStyleForPart = finalStyleForPart.merge(SpanStyle(fontWeight = FontWeight.Bold))
                    if (isItalicActive) finalStyleForPart = finalStyleForPart.merge(SpanStyle(fontStyle = FontStyle.Italic))
                    appendWithVerseStyling(textPart, finalStyleForPart) // Handles verse numbers within this part
                }

                if (marker.type == "bold") {
                    isBoldActive = marker.isStart
                    currentPosInSegment = marker.position + 2 // Skip "**"
                } else { // italic
                    isItalicActive = marker.isStart
                    currentPosInSegment = marker.position + 1 // Skip "*"
                }
            }

            // Append any remaining text after the last marker in the current segment
            if (currentPosInSegment < segmentWithBulletsHandled.length) {
                val remainingTextPart = segmentWithBulletsHandled.substring(currentPosInSegment)
                var finalStyleForPart = styleForCurrentSegment // Start with TTS style
                if (isBoldActive) finalStyleForPart = finalStyleForPart.merge(SpanStyle(fontWeight = FontWeight.Bold))
                if (isItalicActive) finalStyleForPart = finalStyleForPart.merge(SpanStyle(fontStyle = FontStyle.Italic))
                appendWithVerseStyling(remainingTextPart, finalStyleForPart) // Handles verse numbers
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