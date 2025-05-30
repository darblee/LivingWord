package com.darblee.livingword.ui.screens


import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer // Import SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.Screen
import com.darblee.livingword.R
import com.darblee.livingword.domain.model.TTSViewModel
import java.text.BreakIterator
import java.util.Locale

@Composable
fun ProcessMorningPrayer(
    modifier: Modifier = Modifier, // Use the modifier passed from parent (e.g., for width)
    viewModel: TTSViewModel = viewModel(),
    textToSpeak: String,
) {
    // Get the initialization state from the ViewModel
    // collectAsStateWithLifecycle is generally safer for collecting flows in UI
    val isTtsInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()

    val currentlySpeakingIndex by viewModel.currentSentenceInBlockIndex.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle() // Get speaking state
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()

    val context = LocalContext.current // Needed for locale

    // Remember the scroll state
    val scrollState = rememberScrollState()

    // Split text into sentences for display using BreakIterator
    // Remember this based on the text and current locale
    val displaySentences = remember(textToSpeak, Locale.getDefault()) {
        splitIntoSentences(textToSpeak, Locale.getDefault()) // Use local or ViewModel's split logic
    }

    // Build the AnnotatedString for highlighting
    val annotatedText = buildAnnotatedString {
        displaySentences.forEachIndexed { index, sentence ->

            // Highlight if speaking and not paused, or if paused at this index
            val shouldHighlight = (isSpeaking && !isPaused && index == currentlySpeakingIndex) ||
                    (isPaused && index == currentlySpeakingIndex)

            if (shouldHighlight) {
                withStyle(style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)) { // Or another highlight color
                    append(sentence)
                }
            } else {
                append(sentence)
            }
            if (index < displaySentences.size - 1) {
                append("\n") // Or " " for space, ensure consistent with splitting for highlighting
            }
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 20.dp) // Internal padding for content
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Wrap the Text composable with SelectionContainer
        SelectionContainer {
            Text(annotatedText, style = MaterialTheme.typography.bodyLarge) // Apply a base style
        }
    }
}

// Helper function (can be private or moved to a utility file)
// Note: Duplicated here for composable preview/remember; ViewModel has its own copy.
// Consider placing in a shared utility object if preferred.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {

    // Get the current configuration
    val configuration = LocalConfiguration.current

    // Get ViewModel instance to access state for the Icon
    val viewModel: TTSViewModel = viewModel()

    // Collect necessary states from the ViewModel
    val isTtsInitialized by viewModel.isInitialized.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val isPaused by viewModel.isPaused.collectAsStateWithLifecycle()
    val context = LocalContext.current // For Toast messages

    // Hardcoded text to be spoken
    var textToSpeak = remember { // Use remember so it's not recreated on every recomposition
        """
            Thank you Lord for the privilege of another day.
            Heavenly Father, I come to you with a heart full of gratitude this morning.
            Thank you Lord for waking me up. For the breadth in my lungs, for the strength in my body, and the clarity in my mind.
            You did not have to do it, but you did. And I am humbled by your grace.
            Father, I acknowledge this day as a gift and opportunity to walk in your purpose, to reflect your goodness, and be a light in this dark world.
            I know the path may not be always easy, but I trust that You have already gone before me clearing the way.  
            Help me to lean into your wisdom today, to walk with integrity, and carry myself with the humility of someone who knows where their blessings come from.
            Lord guard my hearts, my thoughts, .... and my words.         
            Let me speak light and bring peace wherever I go.           
            Give me the courage to stand firm where challenges arise and the discernment to make choices that honor You.          
            Thank you for the people that you have placed in my life those I meet today, and to those I will impact whether I know it or not.                      
            I commit this day to You. May I bring you Glory. Amen.
            """.trimIndent()
            .replace(Regex("\\s+\\."), ".") // Clean up space before periods for better splitting
    }

    DisposableEffect(Unit) {
        onDispose {
            // This will run when HomeScreen leaves the composition,
            // including navigating away.
            Log.d("HomeScreen", "Disposing HomeScreen, stopping TTS.")
            // Call a method in your ViewModel to stop TTS
            // Ensure this method exists in your TtsViewModel
            viewModel.stopAllSpeaking()
        }
    }

    var currentScreen by remember { mutableStateOf(Screen.Home) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Prepare your heart") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                // Optional: Add colors, navigation icon, actions etc.
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
                    selected = currentScreen == Screen.AllVersesScreen,
                    onClick = { navController.navigate(Screen.AllVersesScreen) },
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
                    onClick = { navController.navigate(Screen.VerseByTopicScreen) },
                    label = { Text("Verse By Topics") },
                    icon = { Icon(Icons.Filled.Church, contentDescription = "Topic") }
                )
            }
        }
    ) { paddingValues -> // Content lambda receives padding values
        // Determine layout based on orientation
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                // Landscape: Logo on Left, Text on Right
                Row(
                    // Apply padding from Scaffold, fill screen, add overall padding
                    modifier = Modifier
                        .padding(paddingValues) // Apply Scaffold padding
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically // Center items vertically
                ) {
                    // Column for Logo and Icons (Stacked Vertically)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { // Center icons under logo
                        DisplayAppLogo()
                        Spacer(Modifier.height(4.dp)) // Add a small vertical space
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DisplayPlayPauseIcon(isSpeaking, isPaused, isTtsInitialized, context, viewModel, textToSpeak)
                            Spacer(Modifier.width(8.dp)) // Space between play/pause and restart icons
                            // DisplayRestartIcon(isTtsInitialized, context, viewModel, textToSpeak)
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp)) // Space between logo/icon group and text

                    // Prayer Text (Right) - Takes remaining space
                    // The ProcessMorningPrayer already has vertical scroll
                    ProcessMorningPrayer(
                        modifier = Modifier.weight(1f).fillMaxHeight(), // Allow text to use full height,
                        textToSpeak = textToSpeak
                    )
                }
            }

            else -> { // Default to Portrait layout
                // Portrait: Logo on Top, Text Below
                Column(
                    modifier = Modifier
                        .padding(paddingValues) // Apply Scaffold padding
                        .fillMaxSize(), // Apply padding from Scaffold, fill screen
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top // Keep logo/icon  at the top
                ) {
                    // Row for Logo and Icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            top = 32.dp,
                            bottom = 16.dp
                        ) // Padding for the top row
                    ) {
                        DisplayAppLogo()
                        Spacer(Modifier.width(8.dp)) // Space between logo and icon

                        // Column for pause/resume icon and replay icon (STacked vertically)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {  // Center icons
                            DisplayPlayPauseIcon(isSpeaking, isPaused, isTtsInitialized, context, viewModel, textToSpeak)
                            Spacer(Modifier.height(16.dp)) // Space between play/pause and replay icons
                            // DisplayRestartIcon(isTtsInitialized, context, viewModel, textToSpeak)
                        }
                    }

                    // Prayer Text (Below)
                    ProcessMorningPrayer(
                        // Use fillMaxWidth with a fraction for 95% width
                        modifier = Modifier.fillMaxWidth(0.95f).weight(1f), // Allow text to take remaining space
                        textToSpeak = textToSpeak
                    )
                }
            }
        }
    }
}

@Composable
private fun DisplayRestartIcon(
    isTtsInitialized: Boolean,
    context: Context,
    viewModel: TTSViewModel,
    textToSpeak: String
) {
    Icon( // Restart Icon - CLICKABLE
        imageVector = Icons.Default.Replay,
        contentDescription = "Restart Speech",
        modifier = Modifier
            .size(48.dp) // Adjust icon size as needed
            .clickable( // Make the icon clickable
                enabled = isTtsInitialized, // Only enable if TTS is ready
                onClickLabel = "Restart Speech", // Accessibility label for action
                onClick = {
                    Log.d("TTS_UI", "Restart Icon clicked.")
                    if (!isTtsInitialized) {
                        Toast.makeText(
                            context,
                            "TTS initializing...",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Call restart function, passing the text
                        viewModel.restartSingleText(textToSpeak)
                    }
                }
            )
    )
}

@Composable
private fun DisplayPlayPauseIcon(
    isSpeaking: Boolean,
    isPaused: Boolean,
    isTtsInitialized: Boolean,
    context: Context,
    viewModel: TTSViewModel,
    textToSpeak: String
) {
    Icon( // Play/Pause Icon
        imageVector = when {
            isSpeaking && !isPaused -> Icons.Default.PauseCircleOutline
            isPaused -> Icons.Default.PlayCircleOutline
            else -> Icons.Filled.RecordVoiceOver
        },
        contentDescription = when {
            isSpeaking && !isPaused -> "Pause Speech"
            isPaused -> "Resume Speech"
            else -> "Play Speech"
        },
        modifier = Modifier
            .size(48.dp) // Adjust icon size as needed
            .pointerInput(Unit) { // Added pointerInput for double-tap
                detectTapGestures(
                    onTap = {
                        // Action to perform on single click
                        Log.d("TTS_UI", "Icon single-tapped.")
                        if (!isTtsInitialized) {
                            // Inform user if TTS isn't ready
                            Toast
                                .makeText(
                                    context,
                                    "TTS initializing...",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
                            // Call the centralized toggle function in ViewModel
                            viewModel.togglePlayPauseResumeSingleText(textToSpeak)
                        }
                    },
                    onDoubleTap = {
                        Log.d("TTS_UI", "Icon double-tapped.")
                        if (!isTtsInitialized) {
                            Toast
                                .makeText(
                                    context,
                                    "TTS initializing...",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
                            // Call restart function, passing the text
                            Log.d("TTS_UI", "Calling ViewModel to restart from icon double-tap...")
                            viewModel.restartSingleText(textToSpeak)
                        }
                    }
                )
            }
        // Removed the .clickable modifier as its functionality is now handled by detectTapGestures
    )
}

@Composable
private fun DisplayAppLogo() {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher_foreground), // Application Icon Placeholder
        contentDescription = "App Logo",
        modifier = Modifier.size(125.dp) // Logo size
    )
}


/**
 * Confirm user if it needs to exit or not
 *
 * @param onDismiss lambda function to cancel the exit
 * @param onExit lambda function to perform the exit
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExitAlertDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    Dialog(
        onDismissRequest = { onDismiss() }, properties = DialogProperties(
            dismissOnBackPress = false, dismissOnClickOutside = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(0.dp)
                .height(IntrinsicSize.Min),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                Text(
                    text = "Logout",
                    color = Color.Black,
                    modifier = Modifier
                        .padding(8.dp, 16.dp, 8.dp, 2.dp)
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(), fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Are you sure you want to exit?",
                    color = Color.Black,
                    modifier = Modifier
                        .padding(8.dp, 2.dp, 8.dp, 16.dp)
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .width(1.dp), color = Color.Gray
                )
                Row(Modifier.padding(top = 0.dp)) {

                    TextButton(
                        onClick = { onDismiss() },
                        Modifier
                            .fillMaxWidth()
                            .padding(0.dp)
                            .weight(1F)
                            .border(0.dp, Color.Transparent)
                            .height(48.dp),
                        elevation = ButtonDefaults.elevatedButtonElevation(0.dp, 0.dp),
                        shape = RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(text = "Not now", color = Color.Gray)
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp), color = Color.Gray
                    )

                    TextButton(
                        onClick = {
                            onExit.invoke()
                        },
                        Modifier
                            .fillMaxWidth()
                            .padding(0.dp)
                            .weight(1F)
                            .border(0.dp, color = Color.Transparent)
                            .height(48.dp),
                        elevation = ButtonDefaults.elevatedButtonElevation(0.dp, 0.dp),
                        shape = RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues()
                    ) {
                        Text(text = "Exit", color = Color.Red)
                    }
                }
            }
        }
    }
}