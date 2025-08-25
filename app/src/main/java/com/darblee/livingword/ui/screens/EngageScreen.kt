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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import com.darblee.livingword.data.verseReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.ui.viewmodels.EngageVerseViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import com.darblee.livingword.ui.viewmodels.TTSViewModel
import java.text.BreakIterator
import java.util.Locale


// Enum to track which text block is targeted for single TTS playback in this screen
enum class EngageSingleTtsTarget { NONE, SCRIPTURE, TAKE_AWAY }


private fun splitIntoSentences(text: String, locale: Locale): List<String> {
    if (text.isBlank()) return emptyList()
    val iterator = BreakIterator.getSentenceInstance(locale)
    iterator.setText(text)
    val sentenceList = mutableListOf<String>()
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val sentence = text.substring(start, end)
        if (sentence.isNotBlank()) {
            sentenceList.add(sentence)
        }
        start = end
        end = iterator.next()
    }
    return sentenceList
}

private fun AnnotatedString.Builder.appendFormattedSentence(sentence: String, boldColor: Color) {
    val processedSentence = if (sentence.trim().startsWith("* ")) {
        "• " + sentence.trim().substring(1).trim()
    } else {
        sentence
    }

    val boldRegex = """\*\*(.*?)\*\*""".toRegex()
    var lastIndex = 0
    boldRegex.findAll(processedSentence).forEach { matchResult ->
        val range = matchResult.range
        append(processedSentence.substring(lastIndex, range.first))
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
            append(matchResult.groupValues[1])
        }
        lastIndex = range.last + 1
    }
    if (lastIndex < processedSentence.length) {
        append(processedSentence.substring(lastIndex))
    }
}

// Helper function to build annotated string for TTS highlighting
private fun buildAnnotatedStringForScoreDialog(
    aiContextExplanation: String,
    applicationFeedback: String,
    currentlySpeakingIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    currentTtsTextId: String?,
    highlightStyle: SpanStyle,
    baseTextColor: Color,
    boldColor: Color
): List<AnnotatedString> {
    val locale = Locale.getDefault()

    // Split into sections based on content
    val contextFeedbackText = "Context Feedback: $aiContextExplanation"
    val applicationFeedbackText = "Application Feedback: ${applicationFeedback.replace("###", "")}"
    val contextSentences = splitIntoSentences(contextFeedbackText, locale)
    val applicationSentences = splitIntoSentences(applicationFeedbackText, locale)

    // Calculate offset indices for each section
    val contextStartIndex = 1 // After "Context Score: X."
    val applicationStartIndex = contextStartIndex + contextSentences.size

    // Build annotated strings for each section
    val contextAnnotated = buildAnnotatedString {
        contextSentences.forEachIndexed { index, sentence ->
            val globalIndex = contextStartIndex + index
            val shouldHighlight = (isSpeaking && !isPaused && globalIndex == currentlySpeakingIndex && currentTtsTextId == "scoreDialog") ||
                    (isPaused && globalIndex == currentlySpeakingIndex && currentTtsTextId == "scoreDialog")

            if (shouldHighlight) {
                withStyle(style = highlightStyle) {
                    appendFormattedSentence(sentence, boldColor)
                }
            } else {
                withStyle(style = SpanStyle(color = baseTextColor)) {
                    appendFormattedSentence(sentence, boldColor)
                }
            }
        }
    }

    val applicationAnnotated = buildAnnotatedString {
        applicationSentences.forEachIndexed { index, sentence ->
            val globalIndex = applicationStartIndex + index
            val shouldHighlight = (isSpeaking && !isPaused && globalIndex == currentlySpeakingIndex && currentTtsTextId == "scoreDialog") ||
                    (isPaused && globalIndex == currentlySpeakingIndex && currentTtsTextId == "scoreDialog")

            if (shouldHighlight) {
                withStyle(style = highlightStyle) {
                    appendFormattedSentence(sentence, boldColor)
                }
            } else {
                withStyle(style = SpanStyle(color = baseTextColor)) {
                    appendFormattedSentence(sentence, boldColor)
                }
            }
        }
    }

    return listOf(contextAnnotated, applicationAnnotated)
}

// Helper function to build AnnotatedString with potential TTS highlighting & Markdown
@Composable
private fun buildAnnotatedStringForEngageTTS(
    fullText: String,
    isTargeted: Boolean,
    highlightSentenceIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    baseStyle: SpanStyle, // Base style for normal text (includes color from theme)
    highlightStyle: SpanStyle, // Style for highlighted TTS sentence
): AnnotatedString {
    val sentences = remember(fullText, Locale.getDefault()) {
        splitIntoSentences(fullText, Locale.getDefault())
    }

    return buildAnnotatedString {
        var currentIndex = 0
        for ((sentenceIndex, sentence) in sentences.iterator().withIndex()) {
            val startIndex = fullText.indexOf(sentence, currentIndex)
            if (startIndex != -1) {
                // Append any text between the last sentence and this one (e.g., whitespace)
                if (startIndex > currentIndex) {
                    withStyle(style = baseStyle) {
                        append(fullText.substring(currentIndex, startIndex))
                    }
                }

                val shouldHighlight = isTargeted &&
                        ((isSpeaking && !isPaused && sentenceIndex == highlightSentenceIndex) ||
                                (isPaused && sentenceIndex == highlightSentenceIndex))

                withStyle(style = if (shouldHighlight) highlightStyle else baseStyle) {
                    append(sentence)
                }
                currentIndex = startIndex + sentence.length
            }
        }
        // Append any remaining text after the last sentence
        if (currentIndex < fullText.length) {
            withStyle(style = baseStyle) {
                append(fullText.substring(currentIndex))
            }
        }
    }
}

@Composable
fun EngageScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    verseID: Long,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption
) {
    val engageVerseViewModel : EngageVerseViewModel = viewModel()
    val state by engageVerseViewModel.state.collectAsStateWithLifecycle()

    // Add TTS ViewModel
    val ttsViewModel: TTSViewModel = viewModel()
    val isTtsInitialized by ttsViewModel.isInitialized.collectAsStateWithLifecycle()

    var verse by remember { mutableStateOf<BibleVerse?>(null) }
    var directQuoteTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var userApplicationTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val coroutineScope = rememberCoroutineScope()

    var directQuotePartialText by remember { mutableStateOf("") }
    var userApplicationPartialText by remember { mutableStateOf("") }

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

    // State to track which content to show in the scripture box: "scripture" or "takeAway"
    var scriptureBoxContentMode by remember { mutableStateOf("scripture") }

    // State to control the visibility of the score dialog
    var showScoreDialog by remember { mutableStateOf(false) }

    // State to track if saved data has been loaded and modified
    var isSavedDataLoaded by remember { mutableStateOf(false) }
    var showCompareDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }

    // TTS state for score dialog
    var currentlySpeakingIndex by remember { mutableIntStateOf(-1) }
    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var currentTtsTextId by remember { mutableStateOf<String?>(null) }
    // State for the dropdown menu
    var showScriptureDropdownMenu by remember { mutableStateOf(false) }
    var scriptureDropdownMenuOffset by remember { mutableStateOf(Offset.Zero) }
    var showMemorizedContentDropdownMenu by remember { mutableStateOf(false) }

    val localDensity = LocalDensity.current

    // State to track which block (Scripture or AI) is the target for single TTS playback
    var activeSingleTtsTextBlock by remember { mutableStateOf(EngageSingleTtsTarget.NONE) }

    // Collect TTS state
    LaunchedEffect(ttsViewModel.currentSentenceInBlockIndex) {
        ttsViewModel.currentSentenceInBlockIndex.collect { newIndex ->
            currentlySpeakingIndex = newIndex
        }
    }

    LaunchedEffect(ttsViewModel.isSpeaking) {
        ttsViewModel.isSpeaking.collect { newIsSpeaking ->
            isSpeaking = newIsSpeaking
        }
    }

    LaunchedEffect(ttsViewModel.isPaused) {
        ttsViewModel.isPaused.collect { newIsPaused ->
            isPaused = newIsPaused
        }
    }

    /***
     * Set up listeners for speech recognition - directQuoteSpeechRecognizer
     */
    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            Log.e("EngageScreen", "Speech recognition is not available")
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

    /***
     * Set up listeners for speech recognition - contextSpeechRecognizer
     */
    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            Log.e("EngageScreen", "Speech recognition is not available")
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
                userApplicationPartialText = ""
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
                    val currentTextInField = userApplicationTextFieldValue.text

                    val currentSelectionInField = userApplicationTextFieldValue.selection

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

                    userApplicationTextFieldValue = TextFieldValue(
                        text = newFullText,
                        selection = TextRange(newCursorPosition)
                    )
                    userApplicationPartialText = "" // Clear partial text as we have final results
                }
                // Continue listening if still desired (isListening reflects the state before results)
                if (isListening) {
                    startListening(context, contextSpeechRecognizer)
                } else {
                    userApplicationPartialText = "" // Ensure partial text is cleared if listening stopped during results
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val recognizedContextTextList =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!recognizedContextTextList.isNullOrEmpty()) {
                    val newPartialRecognizedText = recognizedContextTextList[0]
                    val currentCommittedContextText = userApplicationTextFieldValue.text
                    val currentContextSelection = userApplicationTextFieldValue.selection

                    // Determine context for processing partial text based on focus
                    val textForProcessing: String = if (isContextTextFieldFocused) {
                        currentCommittedContextText.substring(0, currentContextSelection.start)
                    } else {
                        currentCommittedContextText // When not focused, context is the entire current text
                    }

                    val processedText = processNewText(newPartialRecognizedText, textForProcessing)
                    userApplicationPartialText = processPunctuation(processedText) // existingText in processPunctuation is unused
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
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
    val combinedContextDisplayAnnotatedText = remember(userApplicationTextFieldValue, userApplicationPartialText, isContextTextFieldFocused) {
        buildAnnotatedString {
            val currentText = userApplicationTextFieldValue.text
            val selection = userApplicationTextFieldValue.selection

            if (isContextTextFieldFocused) {
                // Focused: Show partial text at cursor
                val cursorPosition = selection.start
                val textBeforeCursor = currentText.substring(0, cursorPosition)
                val textAfterCursor = currentText.substring(cursorPosition)

                if (textBeforeCursor.isNotEmpty()) append(textBeforeCursor)

                if (userApplicationPartialText.isNotEmpty()) {
                    val needsSpaceBefore = textBeforeCursor.isNotEmpty() &&
                            !textBeforeCursor.endsWith(" ") && !textBeforeCursor.endsWith("\n") &&
                            !userApplicationPartialText.startsWith(".") && !userApplicationPartialText.startsWith("?") && !userApplicationPartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(userApplicationPartialText) }
                }

                if (textAfterCursor.isNotEmpty()) {
                    // Check if partial text already ends with a character that implies a following space,
                    // or if textAfterCursor starts with a space.
                    val partialEndsWithSpaceLike = userApplicationPartialText.endsWith(" ") || userApplicationPartialText.endsWith("\n")
                    val textAfterStartsWithSpaceLike = textAfterCursor.startsWith(" ") || textAfterCursor.startsWith("\n")
                    val needsSpaceAfter = userApplicationPartialText.isNotEmpty() && !partialEndsWithSpaceLike &&
                            !textAfterStartsWithSpaceLike &&
                            !textAfterCursor.startsWith(".") && !textAfterCursor.startsWith("?") && !textAfterCursor.startsWith("!") &&
                            !userApplicationPartialText.endsWith(".") && !userApplicationPartialText.endsWith("?") && !userApplicationPartialText.endsWith("!")

                    if (needsSpaceAfter) append(" ")
                    append(textAfterCursor)
                }
            } else {
                // Not focused: Show partial text appended at the end
                append(currentText)
                if (userApplicationPartialText.isNotEmpty()) {
                    val needsSpaceBefore = currentText.isNotEmpty() &&
                            !currentText.endsWith(" ") && !currentText.endsWith("\n") &&
                            !userApplicationPartialText.startsWith(".") && !userApplicationPartialText.startsWith("?") && !userApplicationPartialText.startsWith("!")
                    if (needsSpaceBefore) append(" ")
                    withStyle(style = SpanStyle(color = Color.Gray)) { append(userApplicationPartialText) }
                }
            }
        }
    }
    LaunchedEffect(combinedContextDisplayAnnotatedText.text) {
        if (combinedContextDisplayAnnotatedText.text.isNotEmpty()) {
            contextTextBoxScrollState.animateScrollTo(contextTextBoxScrollState.maxValue)
        }
    }

    // State to control the visibility of the copy confirmation dialog
    var showCopyConfirmDialog by remember { mutableStateOf(false) }

    AppScaffold(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (verse != null) {
                    Text("Engage : ${verseReference(verse!!)}" )
                } else {
                    Text("Engage Verse")
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (verse != null) {
                    IconButton(onClick = {
                        bibleViewModel.updateFavoriteStatus(verse!!.id, !verse!!.favorite)
                        // Optimistically update the local state
                        verse = verse?.copy(favorite = !verse!!.favorite)
                    }) {
                        Icon(
                            imageVector = if (verse!!.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (verse!!.favorite) Color.Red else LocalContentColor.current
                        )
                    }
                    val shareEnabled = directQuoteTextFieldValue.text.isNotEmpty() && userApplicationTextFieldValue.text.isNotEmpty()
                    IconButton(
                        onClick = { showShareDialog = true },
                        enabled = shareEnabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = if (shareEnabled) LocalContentColor.current else Color.Gray
                        )
                    }
                }
            }
        },
        navController = navController,
        currentScreenInstance = Screen.EngageScreen(verseID = verseID), // Pass the actual Screen instance
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LabeledOutlinedBox(
                    label = "Personalized context",
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .weight(0.7F)
                ) {
                    Column(modifier = Modifier.padding(4.dp).fillMaxSize()) {
                        DisplayListeningStatus(isListening)

                        Spacer(modifier = Modifier.height(8.dp))

                        // Memorized Content: This Row arranges the TextField and the Button Column side-by-side
                        Row(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5F),
                            verticalAlignment = Alignment.Top // Align items to the top of the Row
                        ) {
                            // OutlinedTextField takes 80% of the width
                            // Replace both OutlinedTextField instances with this structure:

                            Box(
                                modifier = Modifier.weight(0.8f) // Takes 80% of the width
                            ) {
                                // Text field with border
                                BasicTextField(
                                    value = directQuoteTextFieldValue,
                                    onValueChange = { newValue ->
                                        directQuoteTextFieldValue = newValue
                                        engageVerseViewModel.resetScore()
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("directQuoteTextField")
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
                                                    text = "Type or speak (🎤) ... \n\n" +
                                                            "Boost your memory of the scripture by writing it down..\n\n" +
                                                            "Select \"Feedback\" to get score assessment",
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
                                Row(
                                    modifier = Modifier
                                        .offset(x = 12.dp, y = (-8).dp) // Position on top-left border
                                        .background(
                                            color = MaterialTheme.colorScheme.surface, // Background to "cut" the border line
                                            shape = RoundedCornerShape(2.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp), // Padding around the content
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (state.directQuoteScore >= 0) "Memorization Score (Direct Quote:${state.directQuoteScore} Context:${state.contextScore})" else "Memorized Content",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (isDirectQuoteTextFieldFocused)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    // Only show menu button when not in scoring mode and text is not empty
                                    if (state.directQuoteScore < 0 && directQuoteTextFieldValue.text.isNotBlank()) {
                                        IconButton(
                                            onClick = { showMemorizedContentDropdownMenu = true },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.MoreVert,
                                                contentDescription = "Memorized Content Options",
                                                modifier = Modifier.size(12.dp),
                                                tint = if (isDirectQuoteTextFieldFocused)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Dropdown menu for memorized content actions
                            DropdownMenu(
                                expanded = showMemorizedContentDropdownMenu,
                                onDismissRequest = { showMemorizedContentDropdownMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Read text") },
                                    enabled = isTtsInitialized && directQuoteTextFieldValue.text.isNotBlank(),
                                    onClick = {
                                        showMemorizedContentDropdownMenu = false
                                        if (isTtsInitialized && directQuoteTextFieldValue.text.isNotBlank()) {
                                            if (isSpeaking || isPaused) {
                                                ttsViewModel.stopAllSpeaking()
                                            }
                                            ttsViewModel.restartSingleText(directQuoteTextFieldValue.text)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showMemorizedContentDropdownMenu = false
                                        focusRequester.requestFocus()
                                    }
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
                                            Log.w("EngageScreen", "Record audio permission not granted.")
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
                                    modifier = Modifier.fillMaxWidth().height(30.dp),
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

                        // Application Content: This Row arranges the TextField and the Button Column side-by-side
                        Row(
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8F),
                            verticalAlignment = Alignment.Top // Align items to the top of the Row
                        ) {
                            // OutlinedTextField takes 80% of the width
                            Box(
                                modifier = Modifier.weight(0.8f) // Takes 80% of the width
                            ) {
                                // Text field with border
                                BasicTextField(
                                    value = userApplicationTextFieldValue,
                                    onValueChange = { newValue ->
                                        userApplicationTextFieldValue = newValue
                                        engageVerseViewModel.resetScore()
                                    },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("userApplicationTextField")
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
                                            if (userApplicationTextFieldValue.text.isEmpty()) {
                                                Text(
                                                    text = "Type or speak (🎤) ...\n\n" +
                                                            "Describe specific moments that show this verse is at work in you.",
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    ),
                                                    modifier = Modifier.padding(top = 20.dp) // Move placeholder down to avoid label overlap
                                                )
                                            }
                                            Box(
                                                modifier = Modifier.padding(top = if (userApplicationTextFieldValue.text.isEmpty()) 20.dp else 0.dp)
                                            ) {
                                                innerTextField()
                                            }
                                        }
                                    }
                                )

                                // Overlapping label positioned at top-left corner on the border
                                Text(
                                    text = "Personal Application",
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
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Spacer(modifier = Modifier.height(2.dp)) // Spacer after the Row

                                // Annotate via voice
                                FloatingActionButton(
                                    onClick = {
                                        if (!hasPermission) {
                                            Log.w("EngageScreen", "Record audio permission not granted.")
                                            // TODO: Implement permission request flow if not already present elsewhere
                                            return@FloatingActionButton
                                        }

                                        if (isListening) {
                                            stopListening(contextSpeechRecognizer)
                                            isListening = false // Manually update state
                                            userApplicationPartialText = ""    // Clear partial text when explicitly stopping
                                        } else {
                                            // Request focus to the text field when starting to listen? Optional.
                                            // focusRequester.requestFocus()
                                            startListening(context, contextSpeechRecognizer)
                                            // isListening will be set to true in onReadyForSpeech
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(30.dp),
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

                                Spacer(modifier = Modifier.height(1.dp)) // Spacer after the Row

                                // Clear the box
                                FloatingActionButton(
                                    onClick = {
                                        val textToCheck = userApplicationTextFieldValue.text + userApplicationPartialText // Consider combined text
                                        if (textToCheck.isNotEmpty()) {
                                            if (textToCheck.length < 10) {
                                                userApplicationTextFieldValue = TextFieldValue("")
                                                userApplicationPartialText = ""
                                            } else {
                                                processDeletingContextText = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(30.dp),
                                    containerColor = if ((userApplicationTextFieldValue.text + userApplicationPartialText).isNotEmpty()) { // Use the combined text
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                    }
                                ) {
                                    Text(
                                        "Clear",
                                        color = if (userApplicationTextFieldValue.text.isNotEmpty()) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp)) // Spacer after the Row

                        // Determine button states
                        val hasSavedData = remember(verse) {
                            verse?.let { bibleViewModel.hasUserData(it) } ?: false
                        }
                        // "Show Saved" mode: if data exists, fields are blank, and data hasn't been loaded yet in this session.
                        val isShowDataMode = hasSavedData && directQuoteTextFieldValue.text.isBlank() && !isSavedDataLoaded

                        // "Compare" mode: if saved data has been loaded and the user has since changed the text.
                        val isCompareMode = verse?.let { ((directQuoteTextFieldValue.text != verse!!.userDirectQuote) ||
                                (userApplicationTextFieldValue.text != verse!!.userContext)) } ?: false

                        // "Evaluate" button enabled logic
                        val evaluateButtonEnabled =
                            verse?.let { ((directQuoteTextFieldValue.text + directQuotePartialText).isNotEmpty()) &&
                                    ((userApplicationTextFieldValue.text + userApplicationPartialText).isNotEmpty())
                                     } ?: false

                        val saveButtonEnabled = verse?.let { currentVerse ->
                            val hasDirectQuote = ((directQuoteTextFieldValue.text + directQuotePartialText).isNotEmpty())
                            val hasApplicationContent = ((userApplicationTextFieldValue.text + userApplicationPartialText).isNotEmpty())
                            
                            // Get current content including partial text
                            val currentDirectQuote = (directQuoteTextFieldValue.text + directQuotePartialText).trim()
                            val currentApplication = (userApplicationTextFieldValue.text + userApplicationPartialText).trim()
                            
                            // Check if current content differs from saved content
                            val directQuoteChanged = currentDirectQuote != currentVerse.userDirectQuote
                            val applicationChanged = currentApplication != currentVerse.userContext
                            val aiContentChanged = (state.aiContextExplanationText ?: "") != currentVerse.aiContextExplanationText ||
                                                  (state.applicationFeedback ?: "") != currentVerse.applicationFeedback
                            
                            val hasContentChanged = directQuoteChanged || applicationChanged || aiContentChanged
                            
                            val enabled = hasDirectQuote && hasApplicationContent && hasContentChanged
                            if (enabled && state.directQuoteScore < 0 && state.contextScore < 0) {
                                Log.d("EngageScreen", "Save button enabled without AI scores - user can override/save content")
                            }
                            if (!hasContentChanged && hasDirectQuote && hasApplicationContent) {
                                Log.d("EngageScreen", "Save button disabled - no changes detected from saved content")
                            }
                            enabled
                        } ?: false

                        // Second button ("Save"/"Show"/"Compare") text logic
                        val secondButtonText = if (isShowDataMode) "Show Saved" else "Compare w/ saved"

                        // Second button enabled logic
                        val secondButtonEnabled = isShowDataMode || isCompareMode

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    if (evaluateButtonEnabled) {
                                        val directQuoteToEvaluate = (directQuoteTextFieldValue.text + directQuotePartialText).trim()
                                        val userApplicationComment = (userApplicationTextFieldValue.text + userApplicationPartialText).trim()
                                        if (verse != null) {
                                            val verseInfo = BibleVerseRef(
                                                book = verse!!.book,
                                                chapter = verse!!.chapter,
                                                startVerse = verse!!.startVerse,
                                                endVerse = verse!!.endVerse
                                            )
                                            // Pass the cached BibleVerse object to enable caching
                                            engageVerseViewModel.getAIFeedback(
                                                verseInfo,
                                                directQuoteToEvaluate,
                                                userApplicationComment,
                                                cachedBibleVerse = verse // Pass the verse object for caching
                                            )
                                            showScoreDialog = true
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp),
                                containerColor = if (evaluateButtonEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ) {
                                Text(
                                    text = "Feedback",
                                    color = if (evaluateButtonEnabled) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    },
                                    textAlign = TextAlign.Center
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    if (saveButtonEnabled) {
                                        verse?.let { currentVerse ->
                                            val directQuoteToSave = (directQuoteTextFieldValue.text + directQuotePartialText).trim()
                                            val contextToSave = (userApplicationTextFieldValue.text + userApplicationPartialText).trim()
                                            // Save actual scores or -1 if no AI evaluation was performed
                                            val directQuoteScoreToSave = if (state.directQuoteScore >= 0) state.directQuoteScore else -1
                                            val contextScoreToSave = if (state.contextScore >= 0) state.contextScore else -1

                                            Log.d("EngageScreen", "Saving user data - DirectQuoteScore: $directQuoteScoreToSave, ContextScore: $contextScoreToSave")
                                            Log.d("EngageScreen", "AI Explanations - Direct: '${(state.aiDirectQuoteExplanationText ?: "").take(30)}...', Context: '${(state.aiContextExplanationText ?: "").take(30)}...', App: '${(state.applicationFeedback ?: "").take(30)}...'")

                                            bibleViewModel.updateUserData(
                                                verseId = currentVerse.id,
                                                userDirectQuote = directQuoteToSave,
                                                userDirectQuoteScore = directQuoteScoreToSave,
                                                userContext = contextToSave,
                                                userContextScore = contextScoreToSave,
                                                aiDirectQuoteExplanationText = state.aiDirectQuoteExplanationText ?: "",
                                                aiContextExplanationText = state.aiContextExplanationText ?: "",
                                                applicationFeedback = state.applicationFeedback ?: ""
                                            )

                                            // Optimistically update all relevant local state.
                                            // This triggers a recomposition where `saveButtonEnabled` will evaluate to false.
                                            directQuoteTextFieldValue = TextFieldValue(
                                                text = directQuoteToSave,
                                                selection = TextRange(directQuoteToSave.length)
                                            )
                                            userApplicationTextFieldValue = TextFieldValue(
                                                text = contextToSave,
                                                selection = TextRange(contextToSave.length)
                                            )
                                            directQuotePartialText = ""
                                            userApplicationPartialText = ""

                                            // Update the local verse object to match the saved data.
                                            verse = currentVerse.copy(
                                                userDirectQuote = directQuoteToSave,
                                                userContext = contextToSave,
                                                userDirectQuoteScore = directQuoteScoreToSave,
                                                userContextScore = contextScoreToSave
                                            )

                                            // Reset the change tracking flags
                                            isSavedDataLoaded = true

                                            // The coroutine now serves to verify and sync with the database post-write.
                                            coroutineScope.launch {
                                                delay(200) // Allow DB write to complete.
                                                verse = bibleViewModel.getVerseById(currentVerse.id)
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp),
                                containerColor = if (saveButtonEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ) {
                                Text(
                                    text = if (verse != null) { if (verse!!.userDirectQuote.isNotEmpty())  "Save current" else "Save" } else "Save",
                                    textAlign = TextAlign.Center,
                                    color = if (saveButtonEnabled) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    }
                                )
                            }

                            FloatingActionButton(
                                onClick = {
                                    when {
                                        isShowDataMode -> {
                                            // "Show saved data" action: Load data from verse object into the UI state
                                            verse?.let { savedVerse ->
                                                directQuoteTextFieldValue = TextFieldValue(
                                                    text = savedVerse.userDirectQuote,
                                                    selection = TextRange(savedVerse.userDirectQuote.length)
                                                )
                                                userApplicationTextFieldValue = TextFieldValue(
                                                    text = savedVerse.userContext,
                                                    selection = TextRange(savedVerse.userContext.length)
                                                )
                                                engageVerseViewModel.loadScores(
                                                    directQuoteScore = savedVerse.userDirectQuoteScore,
                                                    contextScore = savedVerse.userContextScore
                                                )
                                                isSavedDataLoaded = true
                                            }
                                        }
                                        isCompareMode -> {
                                            showCompareDialog = true
                                        }
                                        else -> {}
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp),
                                containerColor = if (secondButtonEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                }
                            ) {
                                Text(
                                    text = secondButtonText,
                                    color = if (secondButtonEnabled) {
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

                        if (showCompareDialog) {
                            AlertDialog(
                                properties = DialogProperties(usePlatformDefaultWidth = false),
                                onDismissRequest = { showCompareDialog = false },
                                title = { Text("Compare With Saved") },
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Saved Content Column
                                        Column(
                                            modifier = Modifier
                                                .weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text("Saved", style = MaterialTheme.typography.titleMedium)

                                            // Box for Saved Direct Quote
                                            val savedDirectQuoteLabel = "Direct Quote" + if ((verse?.userDirectQuoteScore ?: -1) >= 0) " (${verse!!.userDirectQuoteScore})" else ""
                                            CompareContentBox(label = savedDirectQuoteLabel, content = verse?.userDirectQuote ?: "")

                                            // Box for Saved Context
                                            val savedContextLabel = "Context" + if ((verse?.userContextScore ?: -1) >= 0) " (${verse!!.userContextScore})" else ""
                                            CompareContentBox(label = savedContextLabel, content = verse?.userContext ?: "")
                                        }

                                        // Current Content Column
                                        Column(
                                            modifier = Modifier
                                                .weight(1f),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            Text("Current", style = MaterialTheme.typography.titleMedium)

                                            // Box for Current Direct Quote
                                            val currentDirectQuoteLabel = "Direct Quote" + if (state.directQuoteScore >= 0) " (${state.directQuoteScore})" else ""
                                            CompareContentBox(label = currentDirectQuoteLabel, content = directQuoteTextFieldValue.text)

                                            // Box for Current Context
                                            val currentContextLabel = "Context" + if (state.contextScore >= 0) " (${state.contextScore})" else ""
                                            CompareContentBox(label = currentContextLabel, content = userApplicationTextFieldValue.text)
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showCompareDialog = false }) {
                                        Text("OK")
                                    }
                                }
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
                                            userApplicationTextFieldValue = TextFieldValue("")
                                            userApplicationPartialText = ""
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

                        if (showCopyConfirmDialog) {
                            AlertDialog(
                                onDismissRequest = { showCopyConfirmDialog = false },
                                title = { Text("Confirm Override") },
                                text = { Text("The context box is not empty. Do you want to override its content?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            userApplicationTextFieldValue = directQuoteTextFieldValue.copy(
                                                selection = TextRange(directQuoteTextFieldValue.text.length)
                                            )
                                            userApplicationPartialText = "" // Clear any partial text in context field
                                            showCopyConfirmDialog = false
                                        }
                                    ) {
                                        Text("Override")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { showCopyConfirmDialog = false }
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LabeledOutlinedBox(
                    label =  if (verse != null) "Scripture (${verse?.translation})" else "",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.3F)
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
                                val baseTextColor = MaterialTheme.typography.bodyLarge.color.takeOrElse { LocalContentColor.current }
                                val scriptureAnnotatedText = when (scriptureBoxContentMode) {
                                    "scripture" -> {
                                        val isScriptureTargetedForTts = activeSingleTtsTextBlock == EngageSingleTtsTarget.SCRIPTURE
                                        verse?.let {
                                            buildAnnotatedStringForScripture(
                                                scriptureVerses = it.scriptureVerses,
                                                isTargeted = isScriptureTargetedForTts,
                                                highlightSentenceIndex = currentlySpeakingIndex,
                                                isSpeaking = isSpeaking,
                                                isPaused = isPaused,
                                                baseStyle = SpanStyle(color = baseTextColor),
                                                highlightStyle = SpanStyle(
                                                    background = MaterialTheme.colorScheme.primaryContainer,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        } ?: buildAnnotatedString { append("Loading scripture....") }
                                    }
                                    "takeAway" -> {
                                        val isTakeAwayTargetedForTts = activeSingleTtsTextBlock == EngageSingleTtsTarget.TAKE_AWAY
                                        verse?.let {
                                            buildAnnotatedStringForEngageTTS(
                                                fullText = it.aiTakeAwayResponse,
                                                isTargeted = isTakeAwayTargetedForTts,
                                                highlightSentenceIndex = currentlySpeakingIndex,
                                                isSpeaking = isSpeaking,
                                                isPaused = isPaused,
                                                baseStyle = SpanStyle(color = baseTextColor),
                                                highlightStyle = SpanStyle(
                                                    background = MaterialTheme.colorScheme.primaryContainer,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            )
                                        } ?: buildAnnotatedString { append("Loading take-away....") }
                                    }
                                    else -> buildAnnotatedString { append("Loading content....") }
                                }

                                Box(modifier = Modifier.fillMaxSize()) { // Box to anchor dropdown
                                    Text(
                                        text = scriptureAnnotatedText,
                                        style = MaterialTheme.typography.bodyMedium, // Adjusted for potentially longer text
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(8.dp)
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onLongPress = { touchOffset -> // touchOffset is the raw pixel offset
                                                        if (isTtsInitialized && verse != null) {
                                                            scriptureDropdownMenuOffset =
                                                                touchOffset // Store the touch position
                                                            showScriptureDropdownMenu = true
                                                        }
                                                    }
                                                )
                                            }
                                    )
                                    DropdownMenu(
                                        expanded = showScriptureDropdownMenu,
                                        onDismissRequest = { showScriptureDropdownMenu = false },
                                        offset = DpOffset(
                                            x = with(localDensity) { scriptureDropdownMenuOffset.x.toDp() },
                                            y = with(localDensity) { scriptureDropdownMenuOffset.y.toDp() }
                                        )
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                val textToShow = if (scriptureBoxContentMode == "scripture") "Read scripture" else "Read take-away"
                                                Text(textToShow)
                                            },
                                            onClick = {
                                                showScriptureDropdownMenu = false
                                                if (isTtsInitialized && verse != null) {
                                                    if (scriptureBoxContentMode == "scripture") {
                                                        activeSingleTtsTextBlock = EngageSingleTtsTarget.SCRIPTURE
                                                        val textToRead = verse!!.scriptureVerses.joinToString(" ") { it.verseString }
                                                        ttsViewModel.restartSingleText(textToRead)
                                                    } else {
                                                        activeSingleTtsTextBlock = EngageSingleTtsTarget.TAKE_AWAY
                                                        val textToRead = verse!!.aiTakeAwayResponse
                                                        ttsViewModel.restartSingleText(textToRead)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
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
                                onClick = {
                                    isScriptureVisible = true
                                    scriptureBoxContentMode = "scripture"
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            {
                                Text(text = "Scripture")
                            }
                            Button(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                onClick = {
                                    isScriptureVisible = true
                                    scriptureBoxContentMode = "takeAway"
                                },
                                shape = RoundedCornerShape(4.dp)
                            )
                            {
                                Text(text = "Take-Away")
                            }
                            Button(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth() // Ensure button text fits
                                    .height(48.dp),
                                onClick = { isScriptureVisible = false },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(text = "Hide")
                            }
                        }
                    }
                }

                if (showShareDialog) {
                    ShareDialog(
                        onDismiss = { showShareDialog = false },
                        onCopy = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val textToCopy = buildShareText(
                                directQuoteText = directQuoteTextFieldValue.text,
                                userApplicationText = userApplicationTextFieldValue.text,
                                directQuoteScore = state.directQuoteScore,
                                contextScore = state.contextScore,
                                aiDirectQuoteExplanation = state.aiDirectQuoteExplanationText,
                                aiContextExplanation = state.aiContextExplanationText,
                                applicationFeedback = state.applicationFeedback
                            )
                            val clip = ClipData.newPlainText("Memorized Content Clipboard", textToCopy)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            showShareDialog = false
                        },
                        onSendEmail = {
                            val shareText = buildShareText(
                                directQuoteText = directQuoteTextFieldValue.text,
                                userApplicationText = userApplicationTextFieldValue.text,
                                directQuoteScore = state.directQuoteScore,
                                contextScore = state.contextScore,
                                aiDirectQuoteExplanation = state.aiDirectQuoteExplanationText,
                                aiContextExplanation = state.aiContextExplanationText,
                                applicationFeedback = state.applicationFeedback
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                if (verse != null) {
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        "My Bible Verse Memorization for ${verseReference(verse!!)}"
                                    )
                                } else {
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        "My Bible Verse Memorization"
                                    )
                                }
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Send Email"))
                            showShareDialog = false
                        }
                    )
                }

                // Dialog to display score and AI explanation
                if (showScoreDialog) {
                    // Build the combined text for TTS
                    val combinedScoreDialogText = buildString {
                        append("Context Score: ${state.contextScore}. ")
                        append("Context Feedback: ${state.aiContextExplanationText ?: ""} ")
                        append("Application Feedback: ${state.applicationFeedback ?: ""}")
                    }

                    // Build annotated strings for highlighting (now only 2 sections: Context + Application)
                    val annotatedStrings = buildAnnotatedStringForScoreDialog(
                        aiContextExplanation = state.aiContextExplanationText ?: "",
                        applicationFeedback = state.applicationFeedback ?: "",
                        currentlySpeakingIndex = currentlySpeakingIndex,
                        isSpeaking = isSpeaking,
                        isPaused = isPaused,
                        currentTtsTextId = currentTtsTextId,
                        highlightStyle = SpanStyle(
                            background = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        baseTextColor = MaterialTheme.colorScheme.onSurface,
                        boldColor = MaterialTheme.colorScheme.primary
                    )

                    AlertDialog(
                        modifier = Modifier.padding(4.dp),
                        onDismissRequest = {
                            // Only allow dismiss if not loading, or handle dismiss during loading appropriately
                            if (!state.aiResponseLoading) {
                                showScoreDialog = false
                                ttsViewModel.stopAllSpeaking() // Ensure TTS is stopped
                            }
                        },

                        title = {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (state.aiResponseLoading) "Getting feedback..." else "Score / Feedback",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    // TTS Play/Pause button - only show when not loading and content is available
                                    if (!state.aiResponseLoading &&
                                        !state.aiContextExplanationText.isNullOrEmpty() &&
                                        !state.applicationFeedback.isNullOrEmpty()
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (!isTtsInitialized) {
                                                    Toast.makeText(
                                                        context,
                                                        "TTS initializing...",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    ttsViewModel.togglePlayPauseResumeSingleText(combinedScoreDialogText)
                                                    currentTtsTextId = "scoreDialog"
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = when {
                                                    isSpeaking && !isPaused && currentTtsTextId == "scoreDialog" -> Icons.Default.PauseCircleOutline
                                                    isPaused && currentTtsTextId == "scoreDialog" -> Icons.Default.PlayCircleOutline
                                                    else -> Icons.Filled.Headset
                                                },
                                                contentDescription = when {
                                                    isSpeaking && !isPaused && currentTtsTextId == "scoreDialog" -> "Pause Score Dialog"
                                                    isPaused && currentTtsTextId == "scoreDialog" -> "Resume Score Dialog"
                                                    else -> "Read Score Dialog"
                                                },
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }

                                // Add cached results indicator
                                if (state.isUsingCachedResults && !state.aiResponseLoading) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "📋 Using cached results",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally // Center progress indicator
                            ) {
                                if (state.aiResponseLoading) {
                                    CircularProgressIndicator(modifier = Modifier.padding(vertical = 16.dp))
                                } else if (state.aiResponseError != null) {
                                    Text(
                                        text = "Error: ${state.aiResponseError}",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    ScrollableTitledOutlinedBoxWithTTS(
                                        label = "Context Score: ${state.contextScore}",
                                        content = annotatedStrings[0],
                                        modifier = Modifier.weight(1f)
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    ScrollableTitledOutlinedBoxWithTTS(
                                        label = "Application Feedback",
                                        content = annotatedStrings[1],
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            if (!state.aiResponseLoading) { // Show OK button only when not loading
                                TextButton(onClick = {
                                    showScoreDialog = false
                                    ttsViewModel.stopAllSpeaking() // Ensure TTS is stopped
                                }) {
                                    Text("OK")
                                }
                            }
                        },
                        dismissButton = { // Optionally, provide a dismiss button if needed during loading
                            if (state.aiResponseLoading) {
                                TextButton(onClick = {
                                    // Handle cancel/dismiss during loading if necessary
                                    // e.g., engageViewModel.cancelScoreCalculation()
                                    showScoreDialog = false // For now, just dismiss
                                    ttsViewModel.stopAllSpeaking() // Ensure TTS is stopped
                                }) {
                                    Text("Cancel")
                                }
                            }
                        },
                    )
                }
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            directQuoteSpeechRecognizer.destroy()
            contextSpeechRecognizer.destroy()
            ttsViewModel.stopAllSpeaking()
        }
    }
}

@Composable
fun ScrollableTitledOutlinedBoxWithTTS(
    label: String,
    content: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // The main container with border and internal padding for content.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(top = 8.dp) // Padding to avoid content overlapping with the label area
        ) {
            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }

        // The overlapping label that "cuts" the border.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = 12.dp, y = (-8).dp) // Position on the top-left of the border
                .background(
                    // Dialogs use Surface color, so this creates the "cut-out" effect
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
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
            text = if (isListening) "Recording mode: 🎤 on ..." else "Recording mode: ⏸️",
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
        // If it's just word "period", it might not be intended as a punctuation. This logic is tricky.
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

@Composable
private fun CompareContentBox(label: String, content: String) {
    Box {
        // The main container with border and internal padding for content.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp) // Set a minimum height to ensure it's not too small
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 12.dp, vertical = 16.dp) // Padding for the content text
        ) {
            Text(text = content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.verticalScroll(
                rememberScrollState()))
        }

        // The overlapping label that "cuts" the border.
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .offset(x = 12.dp, y = (-8).dp) // Position on the top-left of the border
                .background(
                    // Dialogs use Surface color, so this creates the "cut-out" effect
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(2.dp)
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

private fun buildShareText(
    directQuoteText: String,
    userApplicationText: String,
    directQuoteScore: Int,
    contextScore: Int,
    aiDirectQuoteExplanation: String?,
    aiContextExplanation: String?,
    applicationFeedback: String?
): String {
    val stringBuilder = StringBuilder()

    // Always include the basic content
    stringBuilder.append("User Quote\n")
    stringBuilder.append(directQuoteText)
    stringBuilder.append("\n\n")

    stringBuilder.append("User Application\n")
    stringBuilder.append(userApplicationText)

    // Add AI feedback content if it exists
    val hasAiFeedback = directQuoteScore >= 0 || contextScore >= 0 ||
            !aiDirectQuoteExplanation.isNullOrBlank() ||
            !aiContextExplanation.isNullOrBlank() ||
            !applicationFeedback.isNullOrBlank()

    if (hasAiFeedback) {
        stringBuilder.append("\n\n--- AI Feedback ---\n")

        // Add Direct Quote Score and explanation if available
        if (directQuoteScore >= 0) {
            stringBuilder.append("\nDirect Quote Score: $directQuoteScore")
            if (!aiDirectQuoteExplanation.isNullOrBlank()) {
                stringBuilder.append("\nDirect Quote Feedback:\n")
                stringBuilder.append(aiDirectQuoteExplanation.trim())
            }
        }

        // Add Context Score and explanation if available
        if (contextScore >= 0) {
            stringBuilder.append("\n\nContext Score: $contextScore")
            if (!aiContextExplanation.isNullOrBlank()) {
                stringBuilder.append("\nContext Feedback:\n")
                stringBuilder.append(aiContextExplanation.trim())
            }
        }

        // Add Application Feedback if available
        if (!applicationFeedback.isNullOrBlank()) {
            stringBuilder.append("\n\nFeedback on Application:\n")
            stringBuilder.append(applicationFeedback.trim())
        }
    }

    return stringBuilder.toString()
}