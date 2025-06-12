package com.darblee.livingword.ui.screens


import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.domain.model.TTSViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import java.text.BreakIterator
import java.util.Locale

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
fun HomeScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption
) {
    val TTSViewModel: TTSViewModel = viewModel()
    val isTtsInitialized by TTSViewModel.isInitialized.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val textToSpeak = remember {
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
            .replace(Regex("\\s+\\."), ".")
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("HomeScreen", "Disposing HomeScreen, stopping TTS.")
            TTSViewModel.stopAllSpeaking()
        }
    }

    // --- NEW EXPLICIT STATE COLLECTION ---
    // Instead of using collectAsStateWithLifecycle, we will manage the state manually
    // to ensure updates are always processed.
    var currentlySpeakingIndex by remember { mutableIntStateOf(-1) }

    var isSpeaking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }

    // LaunchedEffect will run once and collect the flow from the ViewModel.
    // Every time the ViewModel's StateFlow emits a new value, this block will execute
    // and update our local 'currentlySpeakingIndex' state, forcing a recomposition.
    LaunchedEffect(TTSViewModel.currentSentenceInBlockIndex) {
        TTSViewModel.currentSentenceInBlockIndex.collect { newIndex ->
            Log.d("ProcessMorningPrayer", "Manual collector received new index: $newIndex. Updating local state.")
            currentlySpeakingIndex = newIndex
        }
    }

    LaunchedEffect(TTSViewModel.isSpeaking) {
        TTSViewModel.isSpeaking.collect { newIsSpeaking ->
            Log.d(
                "ProcessMorningPrayer",
                "Manual collector received new isSpeaking: $newIsSpeaking. Updating local state."
            )
            isSpeaking = newIsSpeaking
        }
    }

    LaunchedEffect(TTSViewModel.isPaused) {
        TTSViewModel.isPaused.collect { newIsPaused ->
            Log.d(
                "ProcessMorningPrayer",
                "Manual collector received new isPaused: $newIsPaused. Updating local state."
            )
            isPaused = newIsPaused
        }
    }

    val scrollState = rememberScrollState()
    val displaySentences = remember(textToSpeak, Locale.getDefault()) {
        splitIntoSentences(textToSpeak, Locale.getDefault())
    }

    val annotatedText = buildAnnotatedString {
        displaySentences.forEachIndexed { index, sentence ->
            val shouldHighlight = (isSpeaking && !isPaused && index == currentlySpeakingIndex) ||
                    (isPaused && index == currentlySpeakingIndex)

            if (shouldHighlight) {
                withStyle(style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)) {
                    append(sentence)
                }
            } else {
                append(sentence)
            }
            if (index < displaySentences.size - 1) {
                append("\n")
            }
        }
    }

    AppScaffold(
        title = { Text("Prepare your heart") },
        navController = navController,
        currentScreenInstance = Screen.Home,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 32.dp, bottom = 16.dp)
                    ) {
                        DisplayAppLogo()
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DisplayPlayPauseIcon(isSpeaking, isPaused, isTtsInitialized, context, TTSViewModel, textToSpeak)
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        SelectionContainer {
                            Text(annotatedText, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
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
    Icon(
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
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        Log.d("TTS_UI", "Icon single-tapped.")
                        if (!isTtsInitialized) {
                            Toast
                                .makeText(
                                    context,
                                    "TTS initializing...",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
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
                            Log.d("TTS_UI", "Calling ViewModel to restart from icon double-tap...")
                            viewModel.restartSingleText(textToSpeak)
                        }
                    }
                )
            }
    )
}

@Composable
private fun DisplayAppLogo() {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher_foreground),
        contentDescription = "App Logo",
        modifier = Modifier.size(125.dp)
    )
}

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