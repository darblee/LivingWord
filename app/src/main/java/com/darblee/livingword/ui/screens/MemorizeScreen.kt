package com.darblee.livingword.ui.screens


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged // Import for onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.R
import com.darblee.livingword.data.verseReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darblee.livingword.BibleVerseRef
import com.darblee.livingword.domain.model.MemorizeVerseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizeScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    verseID: Long
) {
    val memorizedViewModel : MemorizeVerseViewModel = viewModel()
    val state by memorizedViewModel.state.collectAsStateWithLifecycle()

    /**
     * We need to access topics (list of strings) that is fetched asynchronously
     * UI reacts to changes in the fetch data (topics).
     * Re-fetching data when verseID changes.
     * This potentially allowing the topics to be modified independently elsewhere if needed.
     *
     * produceState: This is a Composable function designed to convert non-Compose state
     * (often from asynchronous operations like network calls or database queries) into Compose
     * State. It launches a coroutine when it enters the composition and can update its
     * value over time.
     *
     * verseID: This is a key. If the value of verseID changes between recompositions, the
     * produceState block will cancel its current coroutine (if any) and re-launch the producer
     * lambda with the new verseID. This is crucial for re-fetching data when an identifier
     * changes (e.g., user navigates to a different verse).
     *
     */
    var topics: List<String>? by remember { mutableStateOf(emptyList<String>()) }
    val verseItemState = produceState<BibleVerse?>(initialValue = null, verseID) {
        value = bibleViewModel.getVerseById(verseID)
    }
    topics = verseItemState.value?.topics

    var verse by remember { mutableStateOf<BibleVerse?>(null) }
    var directQuoteTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var contextTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val coroutineScope = rememberCoroutineScope()

    var directQuotePartialText by remember { mutableStateOf("") }
    var contextPartialText by remember { mutableStateOf("") }

    var isListening by remember { mutableStateOf(false) }
    var processDeletingDirectQuoteText by remember { mutableStateOf(false) }
    var processDeletingContextText by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val directQuoteSpeechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val contextSpeechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isSpeechRecognitionAvailable by remember { mutableStateOf(false) }
    val directQuoteTextBoxScrollState = rememberScrollState()
    val contextTextBoxScrollState = rememberScrollState()

    val focusRequester = remember { FocusRequester() }

    // State to track if the text field is focused or not
    var isDirectQuoteTextFieldFocused by remember { mutableStateOf(false) }
    var isContextTextFieldFocused by remember { mutableStateOf(false) }

    // State to control scripture visibility
    var isScriptureVisible by remember { mutableStateOf(false) } // Added state

    // State to control the visibility of the score dialog
    var showScoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            Log.e("MemorizeScreen", "Speech recognition is not available")
            return@LaunchedEffect
        }

        directQuoteSpeechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("Speech", "Ready for Direct Quote speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("Speech", "Beginning of Direct Quote speech")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d("Speech", "End of Direct Quote speech")
                // isListening = false; // Optionally set to false, or let onError/onResults handle continuous listening
            }

            override fun onError(error: Int) {
                Log.e("Speech", "Error: $error")
                isListening = false
                directQuotePartialText = ""
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error != SpeechRecognizer.ERROR_CLIENT && // Avoid loop on some persistent client errors
                    error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) {
                    coroutineScope.launch {
                        delay(1000)
                        // Check isListening again, user might have stopped it manually
                        // However, original code implies it should restart if it was listening.
                        // For safety, let's assume if an error occurs, we might not want to auto-restart
                        // unless explicitly started again by the user or a more robust logic.
                        // For now, sticking to original logic of restarting if it *was* listening:
                        // if (isListening) { startListening(speechRecognizer) } -> this `isListening` would be stale.
                        // To robustly restart, the FAB should set a "userWantsToListen" state.
                        // Or, simply attempt restart if not a "final" error.
                        // Let's re-evaluate: if an error occurs, stop. User can restart.
                        // The original code had:
                        // if (isListening) { startListening(speechRecognizer) } -> this `isListening` would be stale.
                        // For now, let's not auto-restart on error to prevent error loops. User can tap mic again.
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val recognizedDirectQuoteTextList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("Speech", "Results: $recognizedDirectQuoteTextList")
                if (!recognizedDirectQuoteTextList.isNullOrEmpty()) {
                    val newRecognizedText = recognizedDirectQuoteTextList[0]
                    val currentTextInField = directQuoteTextFieldValue.text
                    val currentSelectionInField = directQuoteTextFieldValue.selection

                    val newFullText: String
                    val newCursorPosition: Int

                    if (isDirectQuoteTextFieldFocused) {
                        // Field is focused: Insert at current cursor position
                        val cursorPosition = currentSelectionInField.start
                        val textBeforeCursor = currentTextInField.substring(0, cursorPosition)
                        val textAfterCursor = currentTextInField.substring(cursorPosition)

                        val processedText = processNewText(newRecognizedText, textBeforeCursor)
                        val finalText = processPunctuation(processedText) // existingText in processPunctuation is unused

                        val textToInsert = if (textBeforeCursor.isEmpty() || textBeforeCursor.endsWith(" ") || textBeforeCursor.endsWith("\n")) {
                            finalText
                        } else {
                            " $finalText"
                        }
                        newFullText = textBeforeCursor + textToInsert + textAfterCursor
                        newCursorPosition = (textBeforeCursor + textToInsert).length
                    } else {
                        // Field is NOT focused: Append to the end of the current text
                        val processedText = processNewText(newRecognizedText, currentTextInField) // Context for capitalization is the whole current text
                        val finalText = processPunctuation(processedText) // existingText in processPunctuation is unused

                        val textToAppend = if (currentTextInField.isEmpty() || currentTextInField.endsWith(" ") || currentTextInField.endsWith("\n")) {
                            finalText
                        } else {
                            " $finalText"
                        }
                        newFullText = currentTextInField + textToAppend
                        newCursorPosition = newFullText.length // Cursor at the very end
                    }

                    directQuoteTextFieldValue = TextFieldValue(
                        text = newFullText,
                        selection = TextRange(newCursorPosition)
                    )
                    directQuotePartialText = "" // Clear partial text as we have final results
                }
                // Continue listening if still desired (isListening reflects the state before results)
                if (isListening) {
                    startListening(context, directQuoteSpeechRecognizer)
                } else {
                    directQuotePartialText = "" // Ensure partial text is cleared if listening stopped during results
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val recognizedDirectQuoteTextList =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!recognizedDirectQuoteTextList.isNullOrEmpty()) {
                    val newPartialRecognizedText = recognizedDirectQuoteTextList[0]
                    val currentCommittedDirectQuoteText = directQuoteTextFieldValue.text
                    val currentDirectQuoteSelection = directQuoteTextFieldValue.selection

                    // Determine context for processing partial text based on focus
                    val textForProcessing: String = if (isDirectQuoteTextFieldFocused) {
                        currentCommittedDirectQuoteText.substring(0, currentDirectQuoteSelection.start)
                    } else {
                        currentCommittedDirectQuoteText // When not focused, context is the entire current text
                    }

                    val processedText = processNewText(newPartialRecognizedText, textForProcessing)
                    directQuotePartialText = processPunctuation(processedText) // existingText in processPunctuation is unused
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
        }
    }

    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            Log.e("MemorizeScreen", "Speech recognition is not available")
            return@LaunchedEffect
        }

        contextSpeechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("Speech", "Ready for Context speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("Speech", "Beginning of Context speech")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d("Speech", "End of Context speech")
                // isListening = false; // Optionally set to false, or let onError/onResults handle continuous listening
            }

            override fun onError(error: Int) {
                Log.e("Speech", "Error: $error")
                isListening = false
                contextPartialText = ""
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error != SpeechRecognizer.ERROR_CLIENT && // Avoid loop on some persistent client errors
                    error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) {
                    coroutineScope.launch {
                        delay(1000)
                        // Check isListening again, user might have stopped it manually
                        // However, original code implies it should restart if it was listening.
                        // For safety, let's assume if an error occurs, we might not want to auto-restart
                        // unless explicitly started again by the user or a more robust logic.
                        // For now, sticking to original logic of restarting if it *was* listening:
                        // if (isListening) { startListening(speechRecognizer) } -> this `isListening` would be stale.
                        // To robustly restart, the FAB should set a "userWantsToListen" state.
                        // Or, simply attempt restart if not a "final" error.
                        // Let's re-evaluate: if an error occurs, stop. User can restart.
                        // The original code had:
                        // if (isListening) { startListening(speechRecognizer) } -> this `isListening` would be stale.
                        // For now, let's not auto-restart on error to prevent error loops. User can tap mic again.
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val recognizedContextTextList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("Speech", "Results: $recognizedContextTextList")
                if (!recognizedContextTextList.isNullOrEmpty()) {
                    val newRecognizedText = recognizedContextTextList[0]
                    val currentTextInField = contextTextFieldValue.text

                    val currentSelectionInField = contextTextFieldValue.selection

                    val newFullText: String
                    val newCursorPosition: Int

                    if (isContextTextFieldFocused) {

                        // Field is focused: Insert at current cursor position
                        val cursorPosition = currentSelectionInField.start
                        val textBeforeCursor = currentTextInField.substring(0, cursorPosition)
                        val textAfterCursor = currentTextInField.substring(cursorPosition)

                        val processedText = processNewText(newRecognizedText, textBeforeCursor)
                        val finalText = processPunctuation(processedText) // existingText in processPunctuation is unused

                        val textToInsert = if (textBeforeCursor.isEmpty() || textBeforeCursor.endsWith(" ") || textBeforeCursor.endsWith("\n")) {
                            finalText
                        } else {
                            " $finalText"
                        }
                        newFullText = textBeforeCursor + textToInsert + textAfterCursor
                        newCursorPosition = (textBeforeCursor + textToInsert).length
                    } else {
                        // Field is NOT focused: Append to the end of the current text
                        val processedText = processNewText(newRecognizedText, currentTextInField) // Context for capitalization is the whole current text
                        val finalText = processPunctuation(processedText) // existingText in processPunctuation is unused

                        val textToAppend = if (currentTextInField.isEmpty() || currentTextInField.endsWith(" ") || currentTextInField.endsWith("\n")) {
                            finalText
                        } else {
                            " $finalText"
                        }
                        newFullText = currentTextInField + textToAppend
                        newCursorPosition = newFullText.length // Cursor at the very end
                    }

                    contextTextFieldValue = TextFieldValue(
                        text = newFullText,
                        selection = TextRange(newCursorPosition)
                    )
                    contextPartialText = "" // Clear partial text as we have final results
                }
                // Continue listening if still desired (isListening reflects the state before results)
                if (isListening) {
                    startListening(context, contextSpeechRecognizer)
                } else {
                    contextPartialText = "" // Ensure partial text is cleared if listening stopped during results
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val recognizedContextTextList =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!recognizedContextTextList.isNullOrEmpty()) {
                    val newPartialRecognizedText = recognizedContextTextList[0]
                    val currentCommittedContextText = contextTextFieldValue.text
                    val currentContextSelection = contextTextFieldValue.selection

                    // Determine context for processing partial text based on focus
                    val textForProcessing: String = if (isContextTextFieldFocused) {
                        currentCommittedContextText.substring(0, currentContextSelection.start)
                    } else {
                        currentCommittedContextText // When not focused, context is the entire current text
                    }

                    val processedText = processNewText(newPartialRecognizedText, textForProcessing)
                    contextPartialText = processPunctuation(processedText) // existingText in processPunctuation is unused
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
        }
    }

    // LaunchedEffect to observe changes in score and AI response text to show the dialog
    LaunchedEffect(state.contextScore, state.aiExplanationText, state.aiResponseLoading) {
        if (state.aiResponseLoading || (state.contextScore >= 0)) {
            showScoreDialog = true
        }
    }

    // Combine direct quote text and partial text for display or for enabling buttons
    val combinedDirectQuoteDisplayAnnotatedText = remember(directQuoteTextFieldValue, directQuotePartialText, isDirectQuoteTextFieldFocused) {
        buildAnnotatedString {
            val currentText = directQuoteTextFieldValue.text
            val selection = directQuoteTextFieldValue.selection

            if (isDirectQuoteTextFieldFocused) {
                // Focused: Show partial text at cursor
                val cursorPosition = selection.start
                val textBeforeCursor = currentText.substring(0, cursorPosition)
                val textAfterCursor = currentText.substring(cursorPosition)

                if (textBeforeCursor.isNotEmpty()) append(textBeforeCursor)

                if (directQuotePartialText.isNotEmpty()) {
                    val needsSpaceBefore = textBeforeCursor.isNotEmpty() &&
                            !textBeforeCursor.endsWith(" ") && !textBeforeCursor.endsWith("\n") &&
                            !directQuotePartialText.startsWith(".") && !directQuotePartialText.startsWith("?") && !directQuotePartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(directQuotePartialText) }
                }

                if (textAfterCursor.isNotEmpty()) {
                    // Check if partial text already ends with a character that implies a following space,
                    // or if textAfterCursor starts with a space.
                    val partialEndsWithSpaceLike = directQuotePartialText.endsWith(" ") || directQuotePartialText.endsWith("\n")
                    val textAfterStartsWithSpaceLike = textAfterCursor.startsWith(" ") || textAfterCursor.startsWith("\n")
                    val needsSpaceAfter = directQuotePartialText.isNotEmpty() && !partialEndsWithSpaceLike &&
                            !textAfterStartsWithSpaceLike &&
                            !textAfterCursor.startsWith(".") && !textAfterCursor.startsWith("?") && !textAfterCursor.startsWith("!") &&
                            !directQuotePartialText.endsWith(".") && !directQuotePartialText.endsWith("?") && !directQuotePartialText.endsWith("!")

                    if (needsSpaceAfter) append(" ")
                    append(textAfterCursor)
                }
            } else {
                // Not focused: Show partial text appended at the end
                append(currentText)
                if (directQuotePartialText.isNotEmpty()) {
                    val needsSpaceBefore = currentText.isNotEmpty() &&
                            !currentText.endsWith(" ") && !currentText.endsWith("\n") &&
                            !directQuotePartialText.startsWith(".") && !directQuotePartialText.startsWith("?") && !directQuotePartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(directQuotePartialText) }
                }
            }
        }
    }
    LaunchedEffect(combinedDirectQuoteDisplayAnnotatedText.text) {
        if (combinedDirectQuoteDisplayAnnotatedText.text.isNotEmpty()) {
            directQuoteTextBoxScrollState.animateScrollTo(directQuoteTextBoxScrollState.maxValue)
        }
    }

    // Combine Context text and partial text for display or for enabling buttons
    val combinedContextDisplayAnnotatedText = remember(contextTextFieldValue, contextPartialText, isContextTextFieldFocused) {
        buildAnnotatedString {
            val currentText = contextTextFieldValue.text
            val selection = contextTextFieldValue.selection

            if (isContextTextFieldFocused) {
                // Focused: Show partial text at cursor
                val cursorPosition = selection.start
                val textBeforeCursor = currentText.substring(0, cursorPosition)
                val textAfterCursor = currentText.substring(cursorPosition)

                if (textBeforeCursor.isNotEmpty()) append(textBeforeCursor)

                if (contextPartialText.isNotEmpty()) {
                    val needsSpaceBefore = textBeforeCursor.isNotEmpty() &&
                            !textBeforeCursor.endsWith(" ") && !textBeforeCursor.endsWith("\n") &&
                            !contextPartialText.startsWith(".") && !contextPartialText.startsWith("?") && !contextPartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(contextPartialText) }
                }

                if (textAfterCursor.isNotEmpty()) {
                    // Check if partial text already ends with a character that implies a following space,
                    // or if textAfterCursor starts with a space.
                    val partialEndsWithSpaceLike = contextPartialText.endsWith(" ") || contextPartialText.endsWith("\n")
                    val textAfterStartsWithSpaceLike = textAfterCursor.startsWith(" ") || textAfterCursor.startsWith("\n")
                    val needsSpaceAfter = contextPartialText.isNotEmpty() && !partialEndsWithSpaceLike &&
                            !textAfterStartsWithSpaceLike &&
                            !textAfterCursor.startsWith(".") && !textAfterCursor.startsWith("?") && !textAfterCursor.startsWith("!") &&
                            !contextPartialText.endsWith(".") && !contextPartialText.endsWith("?") && !contextPartialText.endsWith("!")

                    if (needsSpaceAfter) append(" ")
                    append(textAfterCursor)
                }
            } else {
                // Not focused: Show partial text appended at the end
                append(currentText)
                if (contextPartialText.isNotEmpty()) {
                    val needsSpaceBefore = currentText.isNotEmpty() &&
                            !currentText.endsWith(" ") && !currentText.endsWith("\n") &&
                            !contextPartialText.startsWith(".") && !contextPartialText.startsWith("?") && !contextPartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(contextPartialText) }
                }
            }
        }
    }
    LaunchedEffect(combinedContextDisplayAnnotatedText.text) {
        if (combinedContextDisplayAnnotatedText.text.isNotEmpty()) {
            contextTextBoxScrollState.animateScrollTo(contextTextBoxScrollState.maxValue)
        }
    }

    var currentScreen by remember { mutableStateOf(Screen.MemorizeScreen) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (verse != null) {
                        Text("Memorize : ${verseReference(verse!!)}" )
                    } else {
                        Text("Memorize Verse")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Home,
                    onClick = { navController.navigate(Screen.Home) },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.AllVersesScreen,
                    onClick = { navController.navigate(Screen.AllVersesScreen) },
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
        val scriptureScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LabeledOutlinedBox(
                label = "Memorized Verse (with context)",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    DisplayListeningStatus(isListening)

                    Spacer(modifier = Modifier.height(8.dp))

                    // Direct Quote: This Row arranges the TextField and the Button Column side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top // Align items to the top of the Row
                    ) {
                        // OutlinedTextField takes 80% of the width
                        // Replace both OutlinedTextField instances with this structure:

                        Box(
                            modifier = Modifier.weight(0.8f).height(125.dp) // Takes 80% of the width
                        ) {
                            // Text field with border
                            BasicTextField(
                                value = directQuoteTextFieldValue,
                                onValueChange = { newValue ->
                                    directQuoteTextFieldValue = newValue
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = if (isDirectQuoteTextFieldFocused) 2.dp else 1.dp,
                                        color = if (isDirectQuoteTextFieldFocused)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(12.dp) // Internal padding
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        isDirectQuoteTextFieldFocused = focusState.isFocused
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (directQuoteTextFieldValue.text.isEmpty()) {
                                            Text(
                                                text = "Type or speak (🎤) ...",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                ),
                                                modifier = Modifier.padding(top = 20.dp) // Move placeholder down to avoid label overlap
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.padding(top = if (directQuoteTextFieldValue.text.isEmpty()) 20.dp else 0.dp)
                                        ) {
                                            innerTextField()
                                        }
                                    }
                                }
                            )

                            // Overlapping label positioned at top-left corner on the border
                            Text(
                                text = "Direct Quote",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isDirectQuoteTextFieldFocused)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset(x = 12.dp, y = (-8).dp) // Position on top-left border
                                    .background(
                                        color = MaterialTheme.colorScheme.surface, // Background to "cut" the border line
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp) // Padding around the text
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp)) // Spacing between TextField and Button Column

                        // This Column takes 20% of the width and stacks the buttons vertically
                        Column(
                            modifier = Modifier
                                .weight(0.2f) // Takes 20% of the width
                                .padding(start = 4.dp), // Optional: adjust as needed
                            horizontalAlignment = Alignment.CenterHorizontally, // Centers buttons if they don't fill width
                            // verticalArrangement arranges the buttons within this column.
                            // spacedBy adds space between the buttons vertically.
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Spacer(modifier = Modifier.height(2.dp)) // Spacer after the Row

                            FloatingActionButton(
                                onClick = {
                                    if (!hasPermission) {
                                        Log.w("MemorizeScreen", "Record audio permission not granted.")
                                        // TODO: Implement permission request flow if not already present elsewhere
                                        return@FloatingActionButton
                                    }

                                    if (isListening) {
                                        stopListening(directQuoteSpeechRecognizer)
                                        isListening = false // Manually update state
                                        directQuotePartialText = ""    // Clear partial text when explicitly stopping
                                    } else {
                                        // Request focus to the text field when starting to listen? Optional.
                                        // focusRequester.requestFocus()
                                        startListening(context, directQuoteSpeechRecognizer)
                                        // isListening will be set to true in onReadyForSpeech
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                containerColor = if (isListening)
                                    MaterialTheme.colorScheme.errorContainer // Use container colors for consistency
                                else
                                    MaterialTheme.colorScheme.primary
                            ) {
                                if (isListening) {
                                    Icon(
                                        imageVector =  Icons.Filled.MicOff,
                                        contentDescription = "Start listening",
                                    )
                                } else {
                                    Text(text = "🎤", fontSize = 20.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp)) // Spacer after the Row

                            FloatingActionButton(
                                onClick = {
                                    val textToCheck = directQuoteTextFieldValue.text + directQuotePartialText // Consider combined text
                                    if (textToCheck.isNotEmpty()) {
                                        if (textToCheck.length < 10) {
                                            directQuoteTextFieldValue = TextFieldValue("")
                                            directQuotePartialText = ""
                                        } else {
                                            processDeletingDirectQuoteText = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                containerColor = if ((directQuoteTextFieldValue.text + directQuotePartialText).isNotEmpty()) { // Use the combined text
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ) {
                                Text(
                                    "Clear",
                                    color = if ((directQuoteTextFieldValue.text + directQuotePartialText).isNotEmpty()) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp)) // Spacer after the Row

                    // Context: This Row arranges the TextField and the Button Column side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top // Align items to the top of the Row
                    ) {
                        // OutlinedTextField takes 80% of the width
                        Box(
                            modifier = Modifier.weight(0.8f).height(125.dp) // Takes 80% of the width
                        ) {
                            // Text field with border
                            BasicTextField(
                                value = contextTextFieldValue,
                                onValueChange = { newValue ->
                                    contextTextFieldValue = newValue
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = if (isContextTextFieldFocused) 2.dp else 1.dp,
                                        color = if (isContextTextFieldFocused)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(12.dp) // Internal padding
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        isContextTextFieldFocused = focusState.isFocused
                                    },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Sentences,
                                    imeAction = ImeAction.Default
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (contextTextFieldValue.text.isEmpty()) {
                                            Text(
                                                text = "Type or speak (🎤) ...",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                ),
                                                modifier = Modifier.padding(top = 20.dp) // Move placeholder down to avoid label overlap
                                            )
                                        }
                                        Box(
                                            modifier = Modifier.padding(top = if (contextTextFieldValue.text.isEmpty()) 20.dp else 0.dp)
                                        ) {
                                            innerTextField()
                                        }
                                    }
                                }
                            )

                            // Overlapping label positioned at top-left corner on the border
                            Text(
                                text = "Context",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isContextTextFieldFocused)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .offset(x = 12.dp, y = (-8).dp) // Position on top-left border
                                    .background(
                                        color = MaterialTheme.colorScheme.surface, // Background to "cut" the border line
                                        shape = RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp) // Padding around the text
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp)) // Spacing between TextField and Button Column

                        // This Column takes 20% of the width and stacks the buttons vertically
                        Column(
                            modifier = Modifier
                                .weight(0.2f) // Takes 20% of the width
                                .padding(start = 4.dp), // Optional: adjust as needed
                            horizontalAlignment = Alignment.CenterHorizontally, // Centers buttons if they don't fill width
                            // verticalArrangement arranges the buttons within this column.
                            // spacedBy adds space between the buttons vertically.
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Spacer(modifier = Modifier.height(2.dp)) // Spacer after the Row

                            FloatingActionButton(
                                onClick = {
                                    if (!hasPermission) {
                                        Log.w("MemorizeScreen", "Record audio permission not granted.")
                                        // TODO: Implement permission request flow if not already present elsewhere
                                        return@FloatingActionButton
                                    }

                                    if (isListening) {
                                        stopListening(contextSpeechRecognizer)
                                        isListening = false // Manually update state
                                        contextPartialText = ""    // Clear partial text when explicitly stopping
                                    } else {
                                        // Request focus to the text field when starting to listen? Optional.
                                        // focusRequester.requestFocus()
                                        startListening(context, contextSpeechRecognizer)
                                        // isListening will be set to true in onReadyForSpeech
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                containerColor = if (isListening)
                                    MaterialTheme.colorScheme.errorContainer // Use container colors for consistency
                                else
                                    MaterialTheme.colorScheme.primary
                            ) {
                                if (isListening) {
                                    Icon(
                                        imageVector =  Icons.Filled.MicOff,
                                        contentDescription = "Start listening",
                                    )
                                } else {
                                    Text(text = "🎤", fontSize = 20.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp)) // Spacer after the Row

                            FloatingActionButton(
                                onClick = {
                                    val textToCheck = contextTextFieldValue.text + contextPartialText // Consider combined text
                                    if (textToCheck.isNotEmpty()) {
                                        if (textToCheck.length < 10) {
                                            contextTextFieldValue = TextFieldValue("")
                                            contextPartialText = ""
                                        } else {
                                            processDeletingContextText = true
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(35.dp),
                                containerColor = if ((contextTextFieldValue.text + contextPartialText).isNotEmpty()) { // Use the combined text
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ) {
                                Text(
                                    "Clear",
                                    color = if ((contextTextFieldValue.text + contextPartialText).isNotEmpty()) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp)) // Spacer after the Row

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = {
                                val directQuoteToEvaluate = directQuoteTextFieldValue.text + directQuotePartialText // Consider combined text
                                val contextToEvaluate = contextTextFieldValue.text + contextPartialText // Consider combined text

                                if (directQuoteToEvaluate.length >= 5) {
                                    Log.i("Memorize Screen", "Do the evaluation with text: $directQuoteToEvaluate")
                                    if (verse != null) {
                                        val verseInfo = BibleVerseRef(
                                            book = verse!!.book,
                                            chapter = verse!!.chapter,
                                            startVerse = verse!!.startVerse,
                                            endVerse = verse!!.endVerse
                                        )
                                        memorizedViewModel.fetchMemorizedScore(
                                            verseInfo,
                                            directQuoteToEvaluate,
                                            contextToEvaluate
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            containerColor = if ((directQuoteTextFieldValue.text + directQuotePartialText).length >= 5) { // Use the combined text
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            }
                        ) {
                            Text(
                                "Evaluate",
                                color = if ((directQuoteTextFieldValue.text + directQuotePartialText).length >= 5) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }

                    if (!hasPermission) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Microphone permission is required for live transcription. Please grant permission in app settings.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (processDeletingDirectQuoteText) {
                        AlertDialog(
                            onDismissRequest = { processDeletingDirectQuoteText = false },
                            title = { Text("Confirm Erase Direct Quote") },
                            text = { Text("Are you sure you want to clear direct quote?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        directQuoteTextFieldValue = TextFieldValue("")
                                        directQuotePartialText = ""
                                        processDeletingDirectQuoteText = false
                                    }
                                ) {
                                    Text("Clear")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { processDeletingDirectQuoteText = false }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (processDeletingContextText) {
                        AlertDialog(
                            onDismissRequest = { processDeletingContextText = false },
                            title = { Text("Confirm Erase Context Info") },
                            text = { Text("Are you sure you want to clear context info?") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        contextTextFieldValue = TextFieldValue("")
                                        contextPartialText = ""
                                        processDeletingContextText = false
                                    }
                                ) {
                                    Text("Clear")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { processDeletingContextText = false }
                                ) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LabeledOutlinedBox(
                label = "Scripture",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp) // Ensure enough height for button and text
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Column(
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(4.dp)
                            .fillMaxWidth()
                            .height(100.dp)
                    )
                    {
                        if (isScriptureVisible) {
                            BasicTextField(
                                value = verse?.scripture ?: "Loading...",
                                onValueChange = { /* Read-only */ },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scriptureScrollState)
                                    .padding(8.dp) // Add some padding inside
                                    .clickable { /* Do nothing */ },
                                readOnly = true,
                                textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                minLines = 5,
                                maxLines = 5
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)) // Opaque box
                                    .clickable { isScriptureVisible = true } // Click to reveal
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Click to reveal scripture verse",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Adjusted spacer

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = { isScriptureVisible = true },
                            enabled = !isScriptureVisible,
                            shape = RoundedCornerShape(4.dp)
                        )
                        {
                            Text(text = "Reveal")
                        }
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            onClick = { isScriptureVisible = false },
                            enabled = isScriptureVisible,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(text = "Hide")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Space before the new button

            // New Button to navigate back
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to detail screen",
                    modifier = Modifier.size(ButtonDefaults.IconSize)
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text("back to detail screen")
            }

            Spacer(modifier = Modifier.height(16.dp)) // Space after new button (optional)

            // Dialog to display score and AI explanation
            if (showScoreDialog) {
                AlertDialog(
                    modifier = Modifier.padding(8.dp),
                    onDismissRequest = {
                        // Only allow dismiss if not loading, or handle dismiss during loading appropriately
                        if (!state.aiResponseLoading) {
                            showScoreDialog = false
                        }
                    },
                    title = {
                        Text(
                            text = if (state.aiResponseLoading) "Calculating Score..." else "Contextual Score: ${state.contextScore}\nDirect Quote Score: ${state.directQuoteScore}",
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally // Center progress indicator
                        ) {
                            if (state.aiResponseLoading) {
                                CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                            } else {
                                // Explanation Label
                                Text(
                                    text = "Explanation",
                                    style = MaterialTheme.typography.labelMedium, // Or your preferred style
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                )
                                // Box with border for the TextField
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 100.dp, max = 400.dp)
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline, // Use theme color for border
                                            RoundedCornerShape(4.dp)
                                        )
                                ) {
                                    BasicTextField(
                                        value = state.aiExplanationText.toString(),
                                        onValueChange = {}, // Read-only
                                        readOnly = true,
                                        modifier = Modifier
                                            .fillMaxSize() // Fill the box
                                            .padding(8.dp) // Inner padding for text
                                            .verticalScroll(rememberScrollState()),
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = LocalContentColor.current // Ensure text color is appropriate
                                        ),
                                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary) // Not visible for readOnly
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        if (!state.aiResponseLoading) { // Show OK button only when not loading
                            TextButton(onClick = { showScoreDialog = false }) {
                                Text("OK")
                            }
                        }
                    },
                    dismissButton = { // Optionally, provide a dismiss button if needed during loading
                        if (state.aiResponseLoading) {
                            TextButton(onClick = {
                                // Handle cancel/dismiss during loading if necessary
                                // e.g., memorizedViewModel.cancelScoreCalculation()
                                showScoreDialog = false // For now, just dismiss
                            }) {
                                Text("Cancel")
                            }
                        }
                    },
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            directQuoteSpeechRecognizer.destroy()
            contextSpeechRecognizer.destroy()
        }
    }
}

@Composable
private fun DisplayListeningStatus(isListening: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isListening)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Text(
            text = if (isListening) "🎤 Listening..." else "⏸️ Not listening",
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth(), // Added fillMaxWidth
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun startListening(context: Context, speechRecognizer: SpeechRecognizer?) {
    if (speechRecognizer == null) {
        Log.e("SpeechUtil", "SpeechRecognizer is null, cannot start listening.")
        return
    }

    // Use the passed context for the availability check
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        Log.e("SpeechUtil", "Speech recognition not available on this device.")
        // You could also notify the user here through a Toast or a state update
        return
    }

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US") // Specify language
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1) // Get the best result
        // EXTRA_PROMPT is optional, typically handled by UI
        // putExtra(RecognizerIntent.EXTRA_PROMPT, "Listening...")
    }
    try {
        speechRecognizer.startListening(intent)
        Log.d("SpeechUtil", "Started listening.")
    } catch (e: Exception) {
        Log.e("SpeechUtil", "Error starting listening: ${e.message}", e)
    }
}

private fun stopListening(speechRecognizer: SpeechRecognizer?) {
    speechRecognizer?.stopListening()
    Log.d("SpeechUtil", "Stopped listening.")
}

private fun processNewText(newText: String, existingText: String): String {
    if (newText.isEmpty()) return newText

    val trimmedExistingText = existingText.trim()
    // Capitalize if existing text is empty, or ends with a sentence terminator, or new line.
    val shouldCapitalize = trimmedExistingText.isEmpty() ||
            trimmedExistingText.endsWith('.') ||
            trimmedExistingText.endsWith('?') ||
            trimmedExistingText.endsWith('!') || // Added exclamation mark
            existingText.endsWith('\n') // Check original existingText for newline

    return if (shouldCapitalize) {
        newText.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        newText
    }
}

private fun processPunctuation(text: String): String {
    // existingTextContext is not used in the current implementation of processPunctuation
    // It was in the signature but not the body.
    val trimmedInputText = text.trim() // Trim the incoming text first

    // Handle "new line" -> \n
    // This check should be early, as "new line" is a command.
    if (trimmedInputText.equals("new line", ignoreCase = true)) {
        return "\n" // Return the newline character
    }

    var processedText = trimmedInputText // Use the trimmed text for further processing

    // Handle "period" -> "."
    if (processedText.equals("period", ignoreCase = true)) {
        return "."
    }
    if (processedText.endsWith(" period", ignoreCase = true)) {
        processedText = processedText.substring(0, processedText.length - " period".length) + "."
    } else if (processedText.endsWith("period", ignoreCase = true) && processedText.length > "period".length) {
        // Check if "period" is a separate word (preceded by space)
        val beforePeriod = processedText.substring(0, processedText.length - "period".length)
        if (beforePeriod.endsWith(" ")) {
            processedText = beforePeriod.trimEnd() + "."
        }
        // If it's just "wordperiod", it might not be intended as a punctuation. This logic is tricky.
    }

    // Handle "question mark"
    if (processedText.equals("question mark", ignoreCase = true)) {
        return "?"
    }
    if (processedText.endsWith(" question mark", ignoreCase = true)) {
        processedText = processedText.substring(0, processedText.length - " question mark".length) + "?"
    }

    // Handle "exclamation mark" or "exclamation point"
    if (processedText.equals("exclamation mark", ignoreCase = true) || processedText.equals("exclamation point", ignoreCase = true)) {
        return "!"
    }
    if (processedText.endsWith(" exclamation mark", ignoreCase = true)) {
        processedText = processedText.substring(0, processedText.length - " exclamation mark".length) + "!"
    }
    if (processedText.endsWith(" exclamation point", ignoreCase = true)) {
        processedText = processedText.substring(0, processedText.length - " exclamation point".length) + "!"
    }

    return processedText
}

@Preview(showBackground = true)
@Composable
fun MemorizeScreenPreview() {
    val navController = rememberNavController()
    // A simple way to provide a context for preview that doesn't rely on full DI for ViewModel
    val context = LocalContext.current
    val factory = BibleVerseViewModel.Factory(context.applicationContext)
    val bibleViewModel: BibleVerseViewModel = viewModel(factory = factory)
    MaterialTheme { // Wrap with MaterialTheme for preview
        MemorizeScreen(navController = navController, bibleViewModel = bibleViewModel, verseID = 1L)
    }
}