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
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.TextStyle
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizeScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    verseID: Long
) {
    var verse by remember { mutableStateOf<BibleVerse?>(null) }
    var memorizedTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val coroutineScope = rememberCoroutineScope()
    var partialText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isSpeechRecognitionAvailable by remember { mutableStateOf(false) }
    val memorizedTextBoxScrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // State to track if the memorized text field is focused
    var isMemorizedTextFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            Log.e("MemorizeScreen", "Speech recognition is not available")
            return@LaunchedEffect
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("Speech", "Ready for speech")
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                Log.d("Speech", "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                Log.d("Speech", "End of speech")
                // isListening = false; // Optionally set to false, or let onError/onResults handle continuous listening
            }

            override fun onError(error: Int) {
                Log.e("Speech", "Error: $error")
                isListening = false
                partialText = ""
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
                        // if (isListening) { // This might be problematic if isListening was set to false right above
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
                val recognizedTextList = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("Speech", "Results: $recognizedTextList")
                if (!recognizedTextList.isNullOrEmpty()) {
                    val newRecognizedText = recognizedTextList[0]
                    val currentTextInField = memorizedTextFieldValue.text
                    val currentSelectionInField = memorizedTextFieldValue.selection

                    val newFullText: String
                    val newCursorPosition: Int

                    if (isMemorizedTextFieldFocused) {
                        // Field is focused: Insert at current cursor position
                        val cursorPosition = currentSelectionInField.start
                        val textBeforeCursor = currentTextInField.substring(0, cursorPosition)
                        val textAfterCursor = currentTextInField.substring(cursorPosition)

                        val processedText = processNewText(newRecognizedText, textBeforeCursor)
                        val finalText = processPunctuation(processedText, textBeforeCursor) // existingText in processPunctuation is unused

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
                        val finalText = processPunctuation(processedText, currentTextInField) // existingText in processPunctuation is unused

                        val textToAppend = if (currentTextInField.isEmpty() || currentTextInField.endsWith(" ") || currentTextInField.endsWith("\n")) {
                            finalText
                        } else {
                            " $finalText"
                        }
                        newFullText = currentTextInField + textToAppend
                        newCursorPosition = newFullText.length // Cursor at the very end
                    }

                    memorizedTextFieldValue = TextFieldValue(
                        text = newFullText,
                        selection = TextRange(newCursorPosition)
                    )
                    partialText = "" // Clear partial text as we have final results
                }
                // Continue listening if still desired (isListening reflects the state before results)
                if (isListening) {
                    startListening(context, speechRecognizer)
                } else {
                    partialText = "" // Ensure partial text is cleared if listening stopped during results
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val recognizedTextList =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!recognizedTextList.isNullOrEmpty()) {
                    val newPartialRecognizedText = recognizedTextList[0]
                    val currentCommittedText = memorizedTextFieldValue.text
                    val currentSelection = memorizedTextFieldValue.selection

                    // Determine context for processing partial text based on focus
                    val contextForProcessing: String = if (isMemorizedTextFieldFocused) {
                        currentCommittedText.substring(0, currentSelection.start)
                    } else {
                        currentCommittedText // When not focused, context is the entire current text
                    }

                    val processedText = processNewText(newPartialRecognizedText, contextForProcessing)
                    partialText = processPunctuation(processedText, contextForProcessing) // existingText in processPunctuation is unused
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
        }
    }

    // Combine memorized text and partial text for display or for enabling buttons
    val combinedDisplayAnnotatedText = remember(memorizedTextFieldValue, partialText, isMemorizedTextFieldFocused) {
        buildAnnotatedString {
            val currentText = memorizedTextFieldValue.text
            val selection = memorizedTextFieldValue.selection

            if (isMemorizedTextFieldFocused) {
                // Focused: Show partial text at cursor
                val cursorPosition = selection.start
                val textBeforeCursor = currentText.substring(0, cursorPosition)
                val textAfterCursor = currentText.substring(cursorPosition)

                if (textBeforeCursor.isNotEmpty()) append(textBeforeCursor)

                if (partialText.isNotEmpty()) {
                    val needsSpaceBefore = textBeforeCursor.isNotEmpty() &&
                            !textBeforeCursor.endsWith(" ") && !textBeforeCursor.endsWith("\n") &&
                            !partialText.startsWith(".") && !partialText.startsWith("?") && !partialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(partialText) }
                }

                if (textAfterCursor.isNotEmpty()) {
                    // Check if partial text already ends with a character that implies a following space,
                    // or if textAfterCursor starts with a space.
                    val partialEndsWithSpaceLike = partialText.endsWith(" ") || partialText.endsWith("\n")
                    val textAfterStartsWithSpaceLike = textAfterCursor.startsWith(" ") || textAfterCursor.startsWith("\n")
                    val needsSpaceAfter = partialText.isNotEmpty() && !partialEndsWithSpaceLike &&
                            !textAfterStartsWithSpaceLike &&
                            !textAfterCursor.startsWith(".") && !textAfterCursor.startsWith("?") && !textAfterCursor.startsWith("!") &&
                            !partialText.endsWith(".") && !partialText.endsWith("?") && !partialText.endsWith("!")


                    if (needsSpaceAfter) append(" ")
                    append(textAfterCursor)
                }
            } else {
                // Not focused: Show partial text appended at the end
                append(currentText)
                if (partialText.isNotEmpty()) {
                    val needsSpaceBefore = currentText.isNotEmpty() &&
                            !currentText.endsWith(" ") && !currentText.endsWith("\n") &&
                            !partialText.startsWith(".") && !partialText.startsWith("?") && !partialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(partialText) }
                }
            }
        }
    }


    // Effect to auto-scroll (if memorizedTextBoxScrollState were used by a scrollable container for the TextField)
    // Note: OutlinedTextField scrolls internally. This might be for a different setup.
    LaunchedEffect(combinedDisplayAnnotatedText.text) { // Key on the text content
        if (combinedDisplayAnnotatedText.text.isNotEmpty()) {
            // If memorizedTextBoxScrollState was attached to a parent Column with .verticalScroll, this would work.
            // For OutlinedTextField, internal scrolling is handled.
            // To scroll OutlinedTextField, you might need to manage its TextFieldValue's selection or use its own scroll state if available.
            // For now, this will scroll memorizedTextBoxScrollState if it's used elsewhere.
            memorizedTextBoxScrollState.animateScrollTo(memorizedTextBoxScrollState.maxValue)
        }
    }

    var currentScreen by remember { mutableStateOf(Screen.MemorizeScreen) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Memorize Verse") },
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
        val scriptureScrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LabeledOutlinedBox(
                label = "Scripture",
                modifier = Modifier.fillMaxWidth().weight(0.1f).heightIn(min = 40.dp)
            ) {
                BasicTextField(
                    value = verse?.scripture ?: "Loading...",
                    onValueChange = { /* Read-only */ },
                    modifier = Modifier.fillMaxSize().verticalScroll(scriptureScrollState),
                    readOnly = true,
                    textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    minLines = 6,
                    maxLines = 6
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LabeledOutlinedBox(
                label = "Memorized Verse",
                modifier = Modifier.fillMaxWidth().weight(0.4f).heightIn(min = 50.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
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
                            modifier = Modifier.padding(4.dp),
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = memorizedTextFieldValue,
                        onValueChange = { newValue ->
                            memorizedTextFieldValue = newValue
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { focusState -> // Update focus state here
                                isMemorizedTextFieldFocused = focusState.isFocused
                                if (!focusState.isFocused && partialText.isNotEmpty()) {
                                    // Optional: If field loses focus and there's partial text,
                                    // you might want to commit it or clear it.
                                    // For now, partialText remains until next speech result.
                                }
                            },
                        placeholder = {
                            // Display combinedDisplayAnnotatedText as placeholder if you want to see partial text preview
                            // However, this makes the placeholder an AnnotatedString.
                            // A simpler placeholder:
                            Text(text = "Type or speak to add text...", style = TextStyle(color = Color.Gray.copy(alpha = 0.5f)))
                            // If you want the gray partial text to appear IN the text field itself,
                            // the value of OutlinedTextField would need to be `combinedDisplayAnnotatedText` (or similar).
                            // But then `onValueChange` would give you this combined text, complicating manual typing.
                            // The current setup keeps `memorizedTextFieldValue` for actual committed text,
                            // and `partialText` is separate. The `combinedDisplayAnnotatedText` is for other UI logic (buttons).
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        minLines = 6,
                        maxLines = 6
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = {
                                if (!hasPermission) {
                                    // TODO: Implement permission request flow
                                    // For now, it just returns. You'll need a permission launcher.
                                    Log.w("MemorizeScreen", "Record audio permission not granted.")
                                    return@FloatingActionButton
                                }

                                if (isListening) {
                                    stopListening(speechRecognizer)
                                    isListening = false // Manually update state
                                    partialText = ""    // Clear partial text when explicitly stopping
                                } else {
                                    // Request focus to the text field when starting to listen? Optional.
                                    // focusRequester.requestFocus()
                                    startListening(context, speechRecognizer)
                                    // isListening will be set to true in onReadyForSpeech
                                }
                            },
                            Modifier
                                .weight(1f)
                                .height(40.dp),
                            containerColor = if (isListening)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.primary
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                                contentDescription = if (isListening) "Stop listening" else "Start listening"
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (combinedDisplayAnnotatedText.text.isNotEmpty()) { // Use the combined text for this check
                                    memorizedTextFieldValue = TextFieldValue("")
                                    partialText = ""
                                }
                            },
                            Modifier
                                .weight(1f)
                                .height(40.dp),
                            containerColor = if (combinedDisplayAnnotatedText.text.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        ) {
                            Text(
                                "Clear",
                                color = if (combinedDisplayAnnotatedText.text.isNotEmpty()) { // Use the combined text
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                if (combinedDisplayAnnotatedText.text.length >= 5) { // Use the combined text
                                    Log.i("Memorized Screen", "Do the evaluation with text: ${combinedDisplayAnnotatedText.text}")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp),
                            containerColor = if (combinedDisplayAnnotatedText.text.length >= 5) { // Use the combined text
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            }
                        ) {
                            Text(
                                "Evaluate",
                                color = if (combinedDisplayAnnotatedText.text.length >= 5) { // Use the combined text
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                }
                            )
                        }
                    }

                    if (!hasPermission) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Microphone permission is required for live transcription. Please grant permission in app settings.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        // TODO: Add a button to request permission or guide user to settings.
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }
}

// Removed startSpeechRecognition as it's unused. startListening is used.

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

private fun processPunctuation(text: String, existingTextContext: String): String {
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
    MemorizeScreen(navController = navController, bibleViewModel = bibleViewModel, verseID = 1L)
}