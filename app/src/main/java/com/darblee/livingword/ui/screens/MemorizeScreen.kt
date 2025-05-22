package com.darblee.livingword.ui.screens


import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    var memorizedText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var partialText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
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

    // State to track if speech recognition is available
    var isSpeechRecognitionAvailable by remember { mutableStateOf(false) }

    // Launcher for requesting the RECORD_AUDIO permission
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            if (isGranted) {
                startSpeechRecognition(speechRecognizer)
            } else {
                // Handle permission denial (e.g., show a message to the user)
                Log.e("MemorizeScreen", "Record audio permission denied")
            }
        }
    )

    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }

    // Initialize speech recognizer and check availability only once
    LaunchedEffect(Unit) {
        isSpeechRecognitionAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        if (!isSpeechRecognitionAvailable) {
            // Handle case where speech recognition is not available
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
            }

            override fun onError(error: Int) {
                Log.e("Speech", "Error: $error")
                isListening = false
                partialText = "" // Clear partial text on error
                // Restart listening if there was an error (except for no speech)
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Restart after a short delay using proper coroutine scope
                    coroutineScope.launch {
                        delay(1000)
                        if (isListening) {
                            startListening(speechRecognizer)
                        }
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                Log.d("Speech", "Results: $recognizedText")
                if (!recognizedText.isNullOrEmpty()) {
                    val newText = recognizedText[0]
                    val processedText = processNewText(newText, memorizedText)
                    val finalText = processPunctuation(processedText, memorizedText)
                    memorizedText = if (memorizedText.isEmpty()) {
                        finalText
                    } else {

                        val combinedText = "$memorizedText $finalText"
                        // Handle punctuation attachment for the combined text
                        if (finalText.startsWith(".") || finalText.startsWith("?") || finalText.startsWith("!")) {
                            memorizedText.trimEnd() + finalText
                        } else {
                            combinedText
                        }
                    }
                    // Clear partial text since we have final results
                    partialText = ""
                    // Exit edit mode when new transcription arrives
                    isEditing = false
                }
                // Continue listening
                if (isListening) {
                    startListening(speechRecognizer)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val recognizedText =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!recognizedText.isNullOrEmpty()) {
                    val newPartialText = recognizedText[0]
                    val processedText = processNewText(newPartialText, memorizedText)
                    partialText = processPunctuation(processedText, memorizedText)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Fetch the verse
        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
        }
    }

    // Combine memorized text and partial text for display
    val displayText = remember(memorizedText, partialText) {
        buildAnnotatedString {
            if (memorizedText.isNotEmpty()) {
                append(memorizedText)
                if (partialText.isNotEmpty()) {
                    // Check if we need to add space or if punctuation should be directly attached
                    val needsSpace = !partialText.startsWith(".") &&
                            !partialText.startsWith("?") &&
                            !partialText.startsWith("!")
                    if (needsSpace) {
                        append(" ")
                    }
                }
            }
            if (partialText.isNotEmpty()) {
                withStyle(style = SpanStyle(color = androidx.compose.ui.graphics.Color.Gray)) {
                    append(partialText)
                }
            }
        }
    }

    // Effect to auto-scroll when new text is added
    LaunchedEffect(displayText) {
        if (displayText.isNotEmpty() && !isEditing) {
            scrollState.animateScrollTo(scrollState.maxValue)
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
            // NavigationBar for switching between screens
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Home,
                    onClick = { navController.navigate(Screen.Home) },
                    label = { Text("Home") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Meditate",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.MeditateScreen,
                    onClick = { navController.navigate(Screen.MeditateScreen) },
                    label = { Text("Meditate") },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Meditate") }
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
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top box: Read-only scripture
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = verse?.scripture ?: "Loading...",
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Status indicator
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListening)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = if (isListening) "🎤 Listening..." else "⏸️ Not listening",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Middle box: Memorized content with speak-to-text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEditing) "Edit Text:" else "Transcribed Text:",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (!isEditing) {
                            IconButton(
                                onClick = {
                                    isEditing = true
                                    coroutineScope.launch {
                                        delay(100) // Small delay to ensure UI is ready
                                        focusRequester.requestFocus()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit text",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isEditing) {
                        OutlinedTextField(
                            value = memorizedText,
                            onValueChange = { memorizedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = {
                                Text("Type or speak to add text...")
                            },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Default
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            minLines = 8,
                            maxLines = 12
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    isEditing = false
                                    partialText = "" // Clear partial text when done editing
                                }
                            ) {
                                Text("Done")
                            }
                        }
                    } else {
                        if (displayText.isEmpty()) {
                            Text(
                                text = "Start speaking or tap the edit icon to type your text...",
                                fontSize = 16.sp,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = displayText,
                                fontSize = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Start/Stop button
                FloatingActionButton(
                    onClick = {
                        if (!hasPermission) {
                            // Request permission
                            return@FloatingActionButton
                        }

                        if (isListening) {
                            stopListening(speechRecognizer)
                            isListening = false
                            partialText = "" // Clear partial text when stopping
                        } else {
                            startListening(speechRecognizer)
                        }
                    },
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

                // Clear button
                Button(
                    onClick = {
                        memorizedText = ""
                        partialText = ""
                        isEditing = false
                    }
                ) {
                    Text("Clear")
                }
            }

            if (!hasPermission) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Microphone permission is required for live transcription",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            // Bottom box: Topic selection (Simplified for now)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Topic Selection (Not fully implemented)",
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    // Cleanup speechRecognizer
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }
}

private fun startSpeechRecognition(speechRecognizer: SpeechRecognizer) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_PROMPT, "Start speaking")
    }
    speechRecognizer.startListening(intent)
}


private fun startListening(speechRecognizer: SpeechRecognizer?) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
    speechRecognizer?.startListening(intent)
}

private fun stopListening(speechRecognizer: SpeechRecognizer?) {
    speechRecognizer?.stopListening()
}

private fun processNewText(newText: String, existingText: String): String {
    if (newText.isEmpty()) return newText

    // Check if we should capitalize the first letter
    val shouldCapitalize = existingText.trim().isEmpty() ||
            existingText.trim().endsWith('.') ||
            existingText.trim().endsWith('?') ||
            existingText.endsWith('\n')

    return if (shouldCapitalize) {
        newText.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    } else {
        newText
    }
}

private fun processPunctuation(text: String, existingText: String): String {
    var processedText = text

    // Handle "period" -> "."
    if (processedText.equals("period", ignoreCase = true)) {
        return "."
    }

    // Handle text that contains "period" at the end
    if (processedText.endsWith(" period", ignoreCase = true)) {
        processedText = processedText.substring(0, processedText.length - 7) + "."
    } else if (processedText.endsWith("period", ignoreCase = true) && processedText.length > 6) {
        // Check if "period" is a separate word (preceded by space or start of string)
        val beforePeriod = processedText.substring(0, processedText.length - 6)
        if (beforePeriod.endsWith(" ") || beforePeriod.isEmpty()) {
            processedText = beforePeriod.trimEnd() + "."
        }
    }

    return processedText
}

@Preview(showBackground = true)
@Composable
fun MemorizeScreenPreview() {
    val navController = rememberNavController()
    val bibleViewModel: BibleVerseViewModel =
        viewModel(factory = BibleVerseViewModel.Factory(LocalContext.current)) // Mock context if needed for preview
    //Provide a default verseID for the preview
    MemorizeScreen(navController = navController, bibleViewModel = bibleViewModel, verseID = 1)
}