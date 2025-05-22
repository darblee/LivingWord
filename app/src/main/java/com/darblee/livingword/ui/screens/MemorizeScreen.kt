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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.R
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
    val context = LocalContext.current
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }

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
            }

            override fun onResults(results: Bundle?) {
                val recognizedText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0) ?: ""
                memorizedText = recognizedText
                Log.d("Speech", "Results: $recognizedText")
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        // Fetch the verse
        coroutineScope.launch {
            verse = bibleViewModel.getVerseById(verseID)
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

            // Middle box: Memorized content with speak-to-text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clickable {
                        // Check for permission and start speech recognition
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            startSpeechRecognition(speechRecognizer)
                        } else {
                            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (memorizedText.isEmpty()) {
                        Text(
                            "Tap to start memorizing...",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            memorizedText,
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
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


@Preview(showBackground = true)
@Composable
fun MemorizeScreenPreview() {
    val navController = rememberNavController()
    val bibleViewModel: BibleVerseViewModel =
        viewModel(factory = BibleVerseViewModel.Factory(LocalContext.current)) // Mock context if needed for preview
    //Provide a default verseID for the preview
    MemorizeScreen(navController = navController, bibleViewModel = bibleViewModel, verseID = 1)
}