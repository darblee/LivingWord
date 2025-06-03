package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Screen
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.data.TopicWithCount
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowVerseByTopicScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {

    val allVerseToList by bibleViewModel.allVerses.collectAsState()
    val allTopicsWithCount by bibleViewModel.allTopicsWithCount.collectAsState()
    var selectedTopics by rememberSaveable { mutableStateOf(emptyList<String>()) }

    AppScaffold(
        title = { Text("Meditate God's Word by Topic(s)") },
        navController = navController,
        currentScreenInstance = Screen.VerseByTopicScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = (3 * 48).dp)
                        .border(width = 1.dp, color = Color.White)
                        .padding(0.dp)
                ) {
                    Log.i("ShowVerseByTopic", "All Topics with Count: ${allTopicsWithCount}")

                    items(allTopicsWithCount.size) { index -> // Updated this line
                        val topicWithCount = allTopicsWithCount[index] // Updated this line
                        val isSelected =
                            selectedTopics.contains(topicWithCount.topic) // Updated this line
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTopics = onSelectTopicCheckBox(
                                            !isSelected,
                                            selectedTopics,
                                            topicWithCount
                                        ) // Updated this line
                                    }
                                )
                                .padding(vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { isChecked ->
                                    selectedTopics = onSelectTopicCheckBox(
                                        isChecked,
                                        selectedTopics,
                                        topicWithCount
                                    ) // Updated this line
                                }
                            )
                            Text(
                                text = "${topicWithCount.topic} (${topicWithCount.verseCount})", // Updated this line
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 0.dp)
                            )
                        }
                    }
                }

                // Rest of the code remains the same...
                Log.i("ShowVerseByTopic", "SelectedTopics: $selectedTopics")

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTopics.isNotEmpty()) {
                    val filteredVerses = allVerseToList.filter { verseItem ->
                        selectedTopics.any { selectedTopicNormalized ->
                            verseItem.topics.any { verseTopicFromDb ->
                                verseTopicFromDb.equals(selectedTopicNormalized, ignoreCase = true)
                            }
                        }
                    }

                    Log.i(
                        "ShowVerseByTopic",
                        "Found ${filteredVerses.size} verses matching ANY selected topics."
                    )

                    if (filteredVerses.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredVerses) { verseItem ->
                                VerseCard(verseItem, navController)
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "No verses found matching any of the selected topics.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 16.dp).weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = "Please select one or more topics to see matching verses.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp).weight(1f)
                    )
                }
            }
        }
    )

    // Handle back press to navigate to Home and clear backstack
    BackPressHandler {
        navController.navigate(Screen.Home) {
            /**
             * Clears the entire back stack before navigating to All Verses screen. navController.graph.id
             * refers to the root of your navigation graph.
             */
            popUpTo(navController.graph.id) { // Pop the entire back stack
                inclusive = true
            }
            /**
             * launchSingleTop = true ensures that if HomeScreen is already at the top of the stack
             * (which it won't be in this specific scenario after popping everything, but it's good
             * practice for navigations to Home), a new instance isn't created.
             */
            launchSingleTop = true // Avoid multiple instances of Home Screen
        }
    }
}

// Update the helper function to work with TopicWithCount:
private fun onSelectTopicCheckBox(
    isChecked: Boolean,
    existingSelectedTopics: List<String>,
    topicWithCount: TopicWithCount // Changed parameter type
): List<String> {
    var newSelectedTopics = existingSelectedTopics
    if (isChecked) {
        if (!newSelectedTopics.contains(topicWithCount.topic)) { // Access .topic property
            newSelectedTopics = newSelectedTopics + topicWithCount.topic // Access .topic property
        }
    } else {
        newSelectedTopics = newSelectedTopics.filter { it != topicWithCount.topic } // Access .topic property
    }
    return newSelectedTopics
}