package com.darblee.livingword.ui.screens


import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PauseCircleOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.darblee.livingword.data.VotdService
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.data.remote.GeminiAIService
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.data.remote.AiServiceResult
import androidx.compose.material3.OutlinedCard
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.zIndex
import com.darblee.livingword.BackPressHandler
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Box
import com.darblee.livingword.Global
import com.darblee.livingword.R
import com.darblee.livingword.data.Verse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.HomeViewModel
import com.darblee.livingword.domain.model.TTSViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.text.BreakIterator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    bibleVerseViewModel: BibleVerseViewModel
) {
    val TTSViewModel: TTSViewModel = viewModel()
    val isTtsInitialized by TTSViewModel.isInitialized.collectAsStateWithLifecycle()

    val homeViewModel: HomeViewModel = viewModel()

    val context = LocalContext.current
    val preferenceStore = remember { PreferenceStore(context) }
    var selectedTranslation by remember { mutableStateOf("KJV") }
    var expandedTranslation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        selectedTranslation = preferenceStore.readTranslationFromSetting()
    }
    val morningPrayerText = remember {
        """
            Thank you Lord for the privilege of another day.
            
            Heavenly Father, I come to you with a heart full of gratitude this morning.
            
            Thank you Lord for waking me up. For the breadth in my lungs, for the strength in my body, and the clarity in my mind.
            You did not have to do it, but you did. And I am humbled by your grace.
            
            Father, I acknowledge this day as a gift and opportunity to walk in your purpose, to reflect your goodness, and be a light in this world.
            
            I know the path may not be always easy, but I trust that You have already gone before me clearing the way.  
            
            Help me to lean into your wisdom today, to walk with integrity, and carry myself with the humility of someone who knows where their blessings come from.
            
            Lord guard my hearts, my thoughts, .... and my words.         
            
            Let me speak light and bring peace wherever I go.           
            
            Give me the courage to stand firm where challenges arise and the discernment to make choices that honor You.          
            
            Thank you for the people that you have placed in my life those I meet today, and to those I will impact whether I know it or not.                      
            
            I commit this day to You. May I bring you Glory.
            
            Amen.
            """.trimIndent()
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
    var currentTtsTextId by remember { mutableStateOf<String?>(null) }

    var verseOfTheDayReference by remember { mutableStateOf("Loading...") }
    var verseContent by remember { mutableStateOf<List<Verse>>(emptyList()) }

    var showRetrievingDataDialog by remember { mutableStateOf(false) }
    var loadingMessage by remember { mutableStateOf("") }

    LaunchedEffect(selectedTranslation) {
        val currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cachedVotd = preferenceStore.readVotdCache()

        if (cachedVotd != null && cachedVotd.date == currentDate && cachedVotd.translation == selectedTranslation) {
            verseOfTheDayReference = cachedVotd.reference
            // Need to parse the cached content back into List<Verse>
            val parsedVerses = try {
                Json.decodeFromString<List<Verse>>(cachedVotd.content)
            } catch (e: Exception) {
                Log.e("HomeScreen", "Error parsing cached VOTD content: ${e.message}", e)
                emptyList()
            }
            verseContent = parsedVerses
        } else {
            val reference = VotdService.fetchVerseOfTheDayReference()
            verseOfTheDayReference = reference ?: "Error loading Verse of the Day"

            if (verseOfTheDayReference != "Loading..." && (reference != null)) {
                val parts = verseOfTheDayReference.split(":")
                if (parts.size != 2) {
                    verseContent = emptyList()
                    return@LaunchedEffect
                }
                val bookAndChapterPartRaw = parts[0].trim()
                val versePartRaw = parts[1].trim()

                val lastSpaceIndex = bookAndChapterPartRaw.lastIndexOf(' ')
                if (lastSpaceIndex == 0) {
                    verseContent = emptyList()
                    return@LaunchedEffect
                }
                val book = bookAndChapterPartRaw.substring(0, lastSpaceIndex)
                val chapterString = bookAndChapterPartRaw.substring(lastSpaceIndex + 1)
                val chapter = chapterString.toIntOrNull()

                val verseRange = versePartRaw.split("-")
                val startVerse = verseRange[0].toIntOrNull()
                val endVerse = if (verseRange.size > 1) verseRange[1].toIntOrNull() else startVerse

                if (chapter != null && startVerse != null && endVerse != null) {
                    val bibleVerseRef = BibleVerseRef(book, chapter, startVerse, endVerse)
                    val result = withTimeoutOrNull(10000) {
                        GeminiAIService.fetchScripture(bibleVerseRef, selectedTranslation)
                    }
                    if (result == null) {
                        verseContent = emptyList()
                        Log.e("HomeScreen", "Error fetching scripture: Timeout")
                    } else {
                        when (result) {
                            is AiServiceResult.Success -> {
                                val fetchedVerses = result.data
                                verseContent = fetchedVerses
                                // Save the List<Verse> as a JSON string
                                val jsonContent = Json.encodeToString(fetchedVerses)
                                preferenceStore.saveVotdCache(
                                    verseOfTheDayReference,
                                    jsonContent,
                                    selectedTranslation,
                                    currentDate
                                )
                            }

                            is AiServiceResult.Error -> {
                                // Handle error, maybe set verseContent to an error message
                                verseContent = emptyList() // Or a list with an error Verse
                                Log.e(
                                    "HomeScreen",
                                    "Error fetching scripture: ${result.message}"
                                )
                            }
                        }
                    }
                } else {
                    verseContent = emptyList() // Or a list with an error Verse
                    Log.e("HomeScreen", "Error parsing verse reference.")
                }
            }
        }
    }

    // LaunchedEffect will run once and collect the flow from the ViewModel.
    // Every time the ViewModel's StateFlow emits a new value, this block will execute
    // and update our local 'currentlySpeakingIndex' state, forcing a recomposition.
    LaunchedEffect(TTSViewModel.currentSentenceInBlockIndex) {
        TTSViewModel.currentSentenceInBlockIndex.collect { newIndex ->
            Log.d(
                "ProcessMorningPrayer",
                "Manual collector received new index: $newIndex. Updating local state."
            )
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

    LaunchedEffect(Unit) {
        homeViewModel.state.collect { state ->
            Log.i("HomeScreen", "HomeViewModel state collected. LoadingStage: ${state.loadingStage}, GeneralError: ${state.generalError}, AiResponseError: ${state.aiResponseError}")

            showRetrievingDataDialog = when (state.loadingStage) {
                HomeViewModel.LoadingStage.NONE -> false
                else -> true
            }

            loadingMessage = when (state.loadingStage) {
                HomeViewModel.LoadingStage.FETCHING_SCRIPTURE -> "Fetching scripture..."
                HomeViewModel.LoadingStage.FETCHING_TAKEAWAY -> "Fetching insights from AI..."
                HomeViewModel.LoadingStage.VALIDATING_TAKEAWAY -> "Validating insights..."
                else -> "Processing..."
            }

            Log.i("HomeScreen", "Loading message: $loadingMessage, aiResponseText.isNotEmpty(): ${state.aiTakeAwayText.isNotEmpty()}, scriptureVerses.isNotEmpty(): ${state.scriptureVerses.isNotEmpty()} state.isContentSaved: ${state.isContentSaved}")

            if (state.aiTakeAwayText.isNotEmpty() && state.scriptureVerses.isNotEmpty() && !state.isContentSaved && state.selectedVOTD != null && state.loadingStage == HomeViewModel.LoadingStage.NONE) {
                Log.i("HomeScreen", "Adding new verse to database")
                bibleVerseViewModel.saveNewVerseHome(
                    verse = state.selectedVOTD,
                    aiTakeAwayResponse = state.aiTakeAwayText,
                    topics = emptyList(), // Assuming no topics for VOTD
                    translation = state.translation,
                    scriptureVerses = state.scriptureVerses,
                    homeViewModel = homeViewModel // Pass the homeViewModel instance
                )
            }

            if (state.newlySavedVerseId != null) {
                showRetrievingDataDialog = false // Hide dialog before navigating
                navController.navigate(Screen.VerseDetailScreen(verseID = state.newlySavedVerseId, editMode = true)) {
                    popUpTo(Screen.Home)
                }
            }

            if (state.generalError != null) {
                Toast.makeText(context, state.generalError, Toast.LENGTH_LONG).show()
                homeViewModel.clearVerseData() // Clear error after showing
            } else if (state.aiResponseError != null) {
                Toast.makeText(context, state.aiResponseError, Toast.LENGTH_LONG).show()
                homeViewModel.clearVerseData() // Clear error after showing
            } else if (state.isContentSaved) {
                // Verse successfully saved, reset the flag
                homeViewModel.resetNavigationState() // This will reset isVotdAddInitiated in HomeViewModel
            }
        }
    }

    if (showRetrievingDataDialog) {
        TransientRetrievingDataDialog(loadingMessage = loadingMessage)
    }

    val displaySentences = remember(morningPrayerText, Locale.getDefault()) {
        splitIntoSentences(morningPrayerText, Locale.getDefault())
    }

    val annotatedText = buildAnnotatedString {
        displaySentences.forEachIndexed { index, sentence ->
            val shouldHighlight =
                (isSpeaking && !isPaused && index == currentlySpeakingIndex && currentTtsTextId == "morningPrayer") ||
                        (isPaused && index == currentlySpeakingIndex && currentTtsTextId == "morningPrayer")

            if (shouldHighlight) {
                withStyle(style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)) {
                    append(sentence)
                }
            } else {
                append(sentence)
            }
        }
    }
    val scope = rememberCoroutineScope()

    AppScaffold(
        title = { Text("Prepare your heart") },
        navController = navController,
        currentScreenInstance = Screen.Home,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Image(
                painter = painterResource(id = R.drawable.votd_calendar),
                contentDescription = "Verse of the Day Background",
                modifier = Modifier.fillMaxSize().zIndex(0f),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
            Column(
                modifier = Modifier.fillMaxSize()
                    .zIndex(1f)
                    .padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                VerseOfTheDaySection(
                    verseOfTheDayReference = verseOfTheDayReference,
                    verseContent = verseContent,
                    currentlySpeakingIndex = currentlySpeakingIndex,
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    currentTtsTextId = currentTtsTextId,
                    translation = selectedTranslation,
                    expanded = expandedTranslation,
                    onExpandedChange = { expandedTranslation = it },
                    onTranslationSelected = { newTranslation ->
                        if (selectedTranslation != newTranslation) {
                            selectedTranslation = newTranslation
                            scope.launch {
                                preferenceStore.saveTranslationToSetting(newTranslation)
                            }
                        }
                    },
                    onPlayPauseClick = {
                        if (!isTtsInitialized) {
                            Toast
                                .makeText(
                                    context,
                                    "TTS initializing...",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
                            // Speak the reference first, then the content
                            val fullVotdText = "$verseOfTheDayReference. " + verseContent.joinToString(" ") { it.verseString }
                            TTSViewModel.togglePlayPauseResumeSingleText(fullVotdText)
                            currentTtsTextId = "votd"
                        }
                    },
                    onAddClick = {
                        // Parse verseOfTheDayReference into BibleVerseRef
                        val parts = verseOfTheDayReference.split(" ")
                        if (parts.size >= 2) {
                            val book = parts[0]
                            val chapterAndVerse = parts[1].split(":")
                            if (chapterAndVerse.size == 2) {
                                val chapter = chapterAndVerse[0].toIntOrNull()
                                val verseRange = chapterAndVerse[1].split("-")
                                val startVerse = verseRange[0].toIntOrNull()
                                val endVerse =
                                    if (verseRange.size > 1) verseRange[1].toIntOrNull() else startVerse

                                if (chapter != null && startVerse != null && endVerse != null) {
                                    scope.launch {
                                        val existingVerse =
                                            bibleVerseViewModel.findExistingVerse(
                                                book,
                                                chapter,
                                                startVerse
                                            )
                                        if (existingVerse != null) {
                                            Toast.makeText(
                                                context,
                                                "Verse already exists in your list.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            if (!homeViewModel.state.value.isVotdAddInitiated) {
                                                val votdBibleVerseRef =
                                                    BibleVerseRef(
                                                        book,
                                                        chapter,
                                                        startVerse,
                                                        endVerse
                                                    )
                                                homeViewModel.setSelectedVerseAndFetchData(
                                                    votdBibleVerseRef
                                                )
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Verse addition already in progress.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Error parsing VOTD reference for navigation.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Error parsing VOTD reference for navigation.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                context,
                                "Error parsing VOTD reference for navigation.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )

                DailyPrayerSection(
                    annotatedText = annotatedText,
                    isSpeaking = isSpeaking,
                    isPaused = isPaused,
                    currentTtsTextId = currentTtsTextId,
                    onPlayPauseClick = {
                        if (!isTtsInitialized) {
                            Toast
                                .makeText(
                                    context,
                                    "TTS initializing...",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                        } else {
                            TTSViewModel.togglePlayPauseResumeSingleText(morningPrayerText)
                            currentTtsTextId = "morningPrayer"
                        }
                    }
                )
            }
        }
    )

    val activity = LocalActivity.current
    var backPressedTime by remember { mutableStateOf(0L) }
    BackPressHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

fun buildAnnotatedStringForScripture(verseOfTheDayReference: String, verses: List<Verse>, currentlySpeakingIndex: Int, isSpeaking: Boolean, isPaused: Boolean, currentTtsTextId: String?,
                                     highlightStyle: SpanStyle): AnnotatedString {
    val referenceSentences = splitIntoSentences(verseOfTheDayReference, Locale.getDefault())
    val adjustedSpeakingIndex = currentlySpeakingIndex - referenceSentences.size
    return buildAnnotatedString {

        verses.forEachIndexed { index, verse ->
            val shouldHighlight = (isSpeaking && !isPaused && index == adjustedSpeakingIndex && currentTtsTextId == "votd") ||
                    (isPaused && index == adjustedSpeakingIndex && currentTtsTextId == "votd")

            withStyle(
                style = SpanStyle(
                    color = Color.Red,
                    fontSize = 10.sp,
                    baselineShift = BaselineShift.Superscript
                )
            ) {
                append(verse.verseNum.toString())
            }
            append(" ")


            if (shouldHighlight) {
                withStyle(style = highlightStyle) {
                    append(verse.verseString)
                }
            } else {
                append(verse.verseString)
            }
            append(" ")
        }
    }
}

@Composable
fun VerseOfTheDaySection(
    verseOfTheDayReference: String,
    verseContent: List<Verse>,
    currentlySpeakingIndex: Int,
    isSpeaking: Boolean,
    isPaused: Boolean,
    currentTtsTextId: String?,
    onPlayPauseClick: () -> Unit,
    onAddClick: () -> Unit,
    translation: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onTranslationSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Verse of the Day",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "(Powered by BibleGateway.com)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    Button(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = when {
                                isSpeaking && !isPaused && currentTtsTextId == "votd" -> Icons.Default.PauseCircleOutline
                                isPaused && currentTtsTextId == "votd" -> Icons.Default.PlayCircleOutline
                                else -> Icons.Filled.Headset
                            },
                            contentDescription = when {
                                isSpeaking && !isPaused && currentTtsTextId == "votd" -> "Pause VOTD"
                                isPaused && currentTtsTextId == "votd" -> "Resume VOTD"
                                else -> "Read VOTD"
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAddClick,
                        enabled = verseOfTheDayReference != "Loading..." && verseOfTheDayReference != "Error loading Verse of the Day"
                    ) {
                        Text("Add")
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = verseOfTheDayReference,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Box {
                    TextButton(
                        onClick = { onExpandedChange(true) },
                        modifier = Modifier.heightIn(max = 24.dp),
                        contentPadding = PaddingValues(start = 4.dp, end = 4.dp)
                    ) {
                        Text(
                            text = "($translation)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) }
                    ) {
                        Global.bibleTranslations.forEach { translationItem ->
                            DropdownMenuItem(
                                text = { Text(translationItem) },
                                onClick = {
                                    onTranslationSelected(translationItem)
                                    onExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            }
            Text(
                text = "Date: ${SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date())}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                    text = buildAnnotatedStringForScripture(
                        verseOfTheDayReference,
                        verseContent,
                        currentlySpeakingIndex,
                        isSpeaking,
                        isPaused,
                        currentTtsTextId,
                        highlightStyle = SpanStyle(
                            background = MaterialTheme.colorScheme.primaryContainer,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
        }
    }
}

@Composable
fun DailyPrayerSection(
    annotatedText: AnnotatedString,
    isSpeaking: Boolean,
    isPaused: Boolean,
    currentTtsTextId: String?,
    onPlayPauseClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline)
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
                    text = "Daily Prayer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = when {
                            isSpeaking && !isPaused && currentTtsTextId == "morningPrayer" -> Icons.Default.PauseCircleOutline
                            isPaused && currentTtsTextId == "morningPrayer" -> Icons.Default.PlayCircleOutline
                            else -> Icons.Filled.Headset
                        },
                        contentDescription = when {
                            isSpeaking && !isPaused && currentTtsTextId == "morningPrayer" -> "Pause Speech"
                            isPaused && currentTtsTextId == "morningPrayer" -> "Resume Speech"
                            else -> "Play Speech"
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(annotatedText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
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