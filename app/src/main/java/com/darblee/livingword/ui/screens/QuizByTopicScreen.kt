package com.darblee.livingword.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.Screen
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import com.darblee.livingword.ui.viewmodels.QuizByTopicViewModel
import com.darblee.livingword.ui.viewmodels.QuizVerseItem
import com.darblee.livingword.ui.viewmodels.ValidationResult

@Composable
fun QuizByTopicScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    bibleVerseViewModel: BibleVerseViewModel,
    quizByTopicViewModel: QuizByTopicViewModel = viewModel()
) {
    val state by quizByTopicViewModel.state.collectAsState()
    var dropdownExpanded by remember { mutableStateOf(false) }

    // Get actual topics from BibleVerseViewModel
    val allTopicItems by bibleVerseViewModel.allTopicsWithCount.collectAsState()
    val availableTopics = allTopicItems.map { it.topic.trim() }.sorted()

    // Get all verses for random selection
    val allVerses by bibleVerseViewModel.allVerses.collectAsState()

    // Populate verse list when topic is selected (only if verses are empty)
    LaunchedEffect(state.currentSelectedTopic, state.quizVerses.isEmpty()) {
        state.currentSelectedTopic?.let { selectedTopic ->
            // Only populate if quiz verses are empty (new topic or first load)
            if (state.quizVerses.isEmpty()) {
                // Get verses that match the selected topic
                bibleVerseViewModel.getVersesByTopic(selectedTopic).collect { topicVerses ->
                    val matchingVerses = topicVerses.map { verse ->
                        QuizVerseItem(
                            verseReference = "${verse.book} ${verse.chapter}:${verse.startVerse}" +
                                if (verse.endVerse != verse.startVerse) "-${verse.endVerse}" else "",
                            verseId = verse.id,
                            isCorrectMatch = true
                        )
                    }

                    // Fill remaining slots with random verses (up to 8 total)
                    val totalSlotsNeeded = 8
                    val remainingSlots = totalSlotsNeeded - matchingVerses.size

                    val randomVerses = if (remainingSlots > 0 && allVerses.isNotEmpty()) {
                        val usedVerseIds = matchingVerses.map { it.verseId }.toSet()
                        val availableVerses = allVerses.filter { it.id !in usedVerseIds }

                        availableVerses.shuffled().take(remainingSlots).map { verse ->
                            QuizVerseItem(
                                verseReference = "${verse.book} ${verse.chapter}:${verse.startVerse}" +
                                    if (verse.endVerse != verse.startVerse) "-${verse.endVerse}" else "",
                                verseId = verse.id,
                                isCorrectMatch = false
                            )
                        }
                    } else emptyList()

                    // Combine and shuffle all verses
                    val allQuizVerses = (matchingVerses + randomVerses).shuffled()
                    quizByTopicViewModel.setQuizVerses(allQuizVerses)
                }
            }
        }
    }

    AppScaffold(
        title = { Text("Quiz by Topic") },
        navController = navController,
        currentScreenInstance = Screen.QuizByTopicScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Upper part (15%) - Topic selection with dropdown and Next Topic button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.1f)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown button for topic selection
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedButton(
                                onClick = { dropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.currentSelectedTopic ?: "Select Topic",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                availableTopics.forEach { topic ->
                                    DropdownMenuItem(
                                        text = { Text(topic) },
                                        onClick = {
                                            quizByTopicViewModel.setCurrentSelectedTopic(topic)
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                if (state.isValidated) {
                                    quizByTopicViewModel.resetValidation()
                                } else {
                                    quizByTopicViewModel.validateAnswers()
                                }
                            }
                        ) {
                            Text(if (state.isValidated) "Reset" else "Validate")
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    if (state.currentSelectedTopic != null && state.quizVerses.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(state.quizVerses) { quizVerse ->
                                Card(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = quizVerse.isSelected,
                                            onCheckedChange = {
                                                if (!state.isValidated) { // Disable checkbox after validation
                                                    quizByTopicViewModel.toggleVerseSelection(quizVerse.verseId)
                                                }
                                            },
                                            enabled = !state.isValidated
                                        )

                                        Text(
                                            text = quizVerse.verseReference,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .weight(1f) // Take remaining space
                                                .clickable {
                                                    navController.navigate(
                                                        Screen.VerseDetailScreen(verseID = quizVerse.verseId, editMode = false)
                                                    )
                                                }
                                        )

                                        // Show validation result icon if available
                                        quizVerse.validationResult?.let { result ->
                                            Icon(
                                                imageVector = when (result) {
                                                    ValidationResult.CORRECT -> Icons.Default.Check
                                                    ValidationResult.INCORRECT -> Icons.Default.Close
                                                },
                                                contentDescription = when (result) {
                                                    ValidationResult.CORRECT -> "Correct"
                                                    ValidationResult.INCORRECT -> "Incorrect"
                                                },
                                                tint = when (result) {
                                                    ValidationResult.CORRECT -> Color(0xFF4CAF50) // Green
                                                    ValidationResult.INCORRECT -> Color(0xFFF44336) // Red
                                                },
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Show message when no topic is selected
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (state.currentSelectedTopic == null) {
                                    "Please select a topic to start the quiz"
                                } else {
                                    "Loading verses..."
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    )
}