package com.darblee.livingword.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BibleVerseRef
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import kotlinx.serialization.json.Json

@Composable
fun FlashCardScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    bibleVerseViewModel: BibleVerseViewModel
) {
    var selectedVerseRef by remember { mutableStateOf<BibleVerseRef?>(null) }

    // Check for VersePicker result - use the back stack size as a trigger
    val backStackSize = navController.currentBackStack.value.size

    LaunchedEffect(backStackSize) {
        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
        savedStateHandle?.get<String>(Global.VERSE_RESULT_KEY)?.let { resultJson ->
            val verseRef = Json.decodeFromString<BibleVerseRef>(resultJson)
            selectedVerseRef = verseRef
            // Clear the result to prevent reprocessing
            savedStateHandle.remove<String>(Global.VERSE_RESULT_KEY)
        }
    }

    AppScaffold(
        title = { Text("Quiz Flash Card") },
        navController = navController,
        currentScreenInstance = Screen.FlashCardScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val versePickerRoute = BibleData.createVersePickerRoute(Screen.FlashCardScreen)
                        navController.navigate(versePickerRoute)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Select Verse for Quiz")
                }

                // Display selected verse information
                selectedVerseRef?.let { verseRef ->
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Selected Verse:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Text(
                                text = "Book: ${verseRef.book}",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                text = "Chapter: ${verseRef.chapter}",
                                style = MaterialTheme.typography.bodyLarge
                            )

                            val verseRange = if (verseRef.startVerse == verseRef.endVerse) {
                                "Verse: ${verseRef.startVerse}"
                            } else {
                                "Verses: ${verseRef.startVerse}-${verseRef.endVerse}"
                            }

                            Text(
                                text = verseRange,
                                style = MaterialTheme.typography.bodyLarge
                            )

                            Text(
                                text = "Reference: ${verseRef.book} ${verseRef.chapter}:${verseRef.startVerse}" +
                                        if (verseRef.startVerse != verseRef.endVerse) "-${verseRef.endVerse}" else "",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                } ?: run {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "No verse selected yet.\nTap the button above to select a verse for your quiz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}