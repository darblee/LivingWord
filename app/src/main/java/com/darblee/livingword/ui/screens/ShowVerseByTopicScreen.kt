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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.R
import com.darblee.livingword.Screen
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.data.Topic
import com.darblee.livingword.ui.components.AppScaffold


// TODO: Add BackPress handler to navigate back to home. See logic in New Verse Screen.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowVerseByTopicScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
) {

    val allVerseToList  by bibleViewModel.allVerses.collectAsState()
    val allTopicItems by bibleViewModel.allTopicItems.collectAsState()
    var selectedTopics by rememberSaveable { mutableStateOf(emptyList<String>()) }

    AppScaffold(
        title = { Text("Meditate God's Word by Topic(s)") }, // Define the title for this screen
        navController = navController,
        currentScreenInstance = Screen.VerseByTopicScreen, // Pass the actual Screen instance
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues) // Apply padding from Scaffold
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Use a radio group for the list of topics
                // Use LazyVerticalGrid for 3-column layout

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.heightIn(max = (3 * 48).dp)
                        .border(width = 1.dp, color = Color.White )
                        .padding(0.dp)
                ) {
                    Log.i("ShowVerseByTopic", "All selected Topics: ${allTopicItems}")

                    items(allTopicItems.size) { index ->
                        val topicItem = allTopicItems[index]
                        val isSelected = selectedTopics.contains(topicItem.topic)
                        Row(
                            modifier = Modifier
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTopics = onSelectTopicCheckBox(!isSelected, selectedTopics, topicItem)
                                    }
                                )
                                .padding(vertical = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { isChecked ->
                                    selectedTopics = onSelectTopicCheckBox(isChecked, selectedTopics, topicItem)
                                }
                            )
                            Text(
                                text = topicItem.topic,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 0.dp)
                            )
                        }
                    }
                }

                Log.i("ShowVerseByTopic", "SelectedTopics: $selectedTopics")

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedTopics.isNotEmpty()) {
                    val filteredVerses = allVerseToList.filter { verseItem ->
                        // Check if the verseItem.topics (trimmed, original case)
                        // contains ANY of the selectedTopics (normalized: lowercase, trimmed).
                        selectedTopics.any { selectedTopicNormalized -> // Iterate through selected topics
                            verseItem.topics.any { verseTopicFromDb -> // Check if any of verse's topics match
                                // Compare verseTopicFromDb (already trimmed by TypeConverter)
                                // with selectedTopicNormalized (already trimmed and lowercased)
                                verseTopicFromDb.equals(selectedTopicNormalized, ignoreCase = true)
                            }
                        }
                    }

                    Log.i("ShowVerseByTopic", "Found ${filteredVerses.size} verses matching ANY selected topics.")

                    if (filteredVerses.isNotEmpty()) {

                        // Use weight if it's in a Column that should expand
                        // TODO: For now, let's assume you keeping the forEach for VerseCard.
                        // If performance becomes an issue with many verses, consider a Lazy layout.
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredVerses) { verseItem ->
                                VerseCard(verseItem, navController)
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }
                    } else {
                        Text( // Updated feedback text
                            text = "No verses found matching any of the selected topics.",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 16.dp).weight(1f)
                        )
                    }
                } else {
                    Text(
                        text = "Please select one or more topics to see matching verses.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 16.dp).weight(1f) // Use weight to push button down
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

private fun onSelectTopicCheckBox(
    isChecked: Boolean,
    existingSelectedTopics: List<String>,
    topicItem: Topic
): List<String> {
    var newSelectedTopics = existingSelectedTopics
    if (isChecked) {
        if (!newSelectedTopics.contains(topicItem.topic)) {
            newSelectedTopics = newSelectedTopics + topicItem.topic
        }
    } else {
        newSelectedTopics = newSelectedTopics.filter { it != topicItem.topic }
    }
    return newSelectedTopics
}


