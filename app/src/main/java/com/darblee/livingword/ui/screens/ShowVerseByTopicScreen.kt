package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog // Added
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField // Added
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect // Added
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext // Added
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState // Added
import androidx.compose.material3.Scaffold // For Snackbar
import androidx.compose.material3.SnackbarHost // Added
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.data.TopicWithCount
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameTopicDialog(
    currentTopicName: String,
    allExistingTopicNames: List<String>, // Pass all topic names
    onDismiss: () -> Unit,
    onConfirm: (newName: String, isMerge: Boolean) -> Unit
) {
    var newTopicName by rememberSaveable(currentTopicName) { mutableStateOf(currentTopicName) }
    var uiErrorMessage by remember { mutableStateOf<String?>(null) }
    var showMergeWarning by remember { mutableStateOf(false) }
    var potentialMergeTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(newTopicName, currentTopicName, allExistingTopicNames) {
        val trimmedNew = newTopicName.trim()
        uiErrorMessage = null // Reset error
        showMergeWarning = false
        potentialMergeTarget = null

        if (trimmedNew.isBlank()) {
            uiErrorMessage = "Topic name cannot be blank."
        } else if (trimmedNew.length > 50) {
            uiErrorMessage = "Topic name is too long (max 50 chars)."
        } else if (!trimmedNew.equals(currentTopicName, ignoreCase = false)) {
            // Check if it matches an existing topic that is NOT the current topic (even by case)
            val existingMatch = allExistingTopicNames.find { dbTopic ->
                dbTopic.equals(trimmedNew, ignoreCase = true) && !dbTopic.equals(currentTopicName, ignoreCase = true)
            }

            if (existingMatch != null) {
                // New name matches a *different* existing topic. This is a potential merge.
                if (Global.DEFAULT_TOPICS.any { it.equals(existingMatch, ignoreCase = true) }) {
                    uiErrorMessage = "Cannot merge into default topic: \"$existingMatch\"."
                    showMergeWarning = false // Merge not allowed with default topic
                } else {
                    showMergeWarning = true
                    potentialMergeTarget = existingMatch // Store the actual cased name from DB
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (showMergeWarning) "Merge Topic?" else "Rename Topic") },
        text = {
            Column {
                Text("Current name: \"$currentTopicName\"")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newTopicName,
                    onValueChange = { newTopicName = it },
                    label = { Text("New topic name") },
                    singleLine = true,
                    isError = uiErrorMessage != null
                )
                uiErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (showMergeWarning && potentialMergeTarget != null && uiErrorMessage == null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Warning: Topic \"$potentialMergeTarget\" already exists. " +
                                "This will merge all verses from \"$currentTopicName\" into \"$potentialMergeTarget\". " +
                                "\"$currentTopicName\" will then be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary // A distinct color for warning
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedNewName = newTopicName.trim()
                    if (uiErrorMessage == null && trimmedNewName.isNotBlank()) { // Check uiErrorMessage which now includes merge validation
                        onConfirm(trimmedNewName, showMergeWarning)
                    } else if (uiErrorMessage == null && trimmedNewName.isBlank()){ // Set error if confirm clicked when blank
                        uiErrorMessage = "Topic name cannot be blank."
                    }
                },
                enabled = uiErrorMessage == null || (showMergeWarning && uiErrorMessage?.startsWith("Cannot merge into default topic") == false) // Enable if no error OR if it's a valid merge warning
            ) {
                Text(if (showMergeWarning) "Merge & Rename" else "Rename")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowVerseByTopicScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {

    val allTopicsWithCountState by bibleViewModel.allTopicsWithCount.collectAsState()

    var selectedTopics by rememberSaveable { mutableStateOf(emptyList<String>()) }

    var showRenameDialog by remember { mutableStateOf(false) } // Added state for rename dialog

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage by bibleViewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            bibleViewModel.clearErrorMessage() // Clear the message after showing
        }
    }

    // Using AppScaffold which likely has its own Scaffold internally.
    // If AppScaffold doesn't provide a SnackbarHost, this would need adjustment.
    // For this example, assuming AppScaffold can host or we use a local Scaffold if needed.
    // For simplicity, we'll assume AppScaffold takes care of the scaffold structure.
    // If not, the content lambda of AppScaffold would need to be wrapped in another Scaffold
    // that includes the SnackbarHost.

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
                // Box to contain both the label and the grid
                Box {
                    Text(
                        text = "Select one or more topics to perform task.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp, start = 8.dp)
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .heightIn(max = (4 * 48).dp) // Max height for approx 4 items per column visible
                            .border(width = 1.dp, color = Color.Gray) // Changed border color for visibility
                            .padding(top = 35.dp)
                    ) {
                        items(allTopicsWithCountState.size) { index ->
                            val topicWithCount = allTopicsWithCountState[index]
                            val isSelected = selectedTopics.contains(topicWithCount.topic)
                            Row(
                                modifier = Modifier
                                    .selectable(
                                        selected = isSelected,
                                        onClick = {
                                            selectedTopics = onSelectTopicCheckBox(
                                                !isSelected,
                                                selectedTopics,
                                                topicWithCount
                                            )
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
                                        )
                                    }
                                )
                                Text(
                                    text = "${topicWithCount.topic} (${topicWithCount.verseCount})",
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 0.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "${allTopicsWithCountState.size} topics",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .offset(x = 12.dp, y = (-8).dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(2.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (selectedTopics.count() == 1) {
                                    showRenameDialog = true // Show dialog
                                }
                            },
                            enabled = selectedTopics.count() == 1,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
                            Text("Rename topic")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val topicsToDelete = selectedTopics.filter { selectedTopic ->
                                    allTopicsWithCountState.find { it.topic == selectedTopic }?.verseCount == 0
                                }
                                if (topicsToDelete.isNotEmpty()) {
                                    bibleViewModel.deleteTopics(topicsToDelete)
                                    selectedTopics =
                                        selectedTopics.filterNot { topicsToDelete.contains(it) }
                                }
                            },
                            enabled = selectedTopics.isNotEmpty() && selectedTopics.any { selTopic ->
                                allTopicsWithCountState.find { it.topic == selTopic }?.verseCount == 0
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text("Delete Topic(s)")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (showRenameDialog && selectedTopics.count() == 1) {
                    val topicToRename = selectedTopics.first()
                    RenameTopicDialog(
                        currentTopicName = topicToRename,
                        allExistingTopicNames = allTopicsWithCountState.map { it.topic },
                        onDismiss = { showRenameDialog = false },
                        onConfirm = { newName, isMerge ->
                            showRenameDialog = false
                            // old name is topicToRename, new name is newName
                            bibleViewModel.renameOrMergeTopic(topicToRename, newName, isMerge)

                            // Update selection: if old topic was selected, it should now be the newName (target of rename/merge)
                            if (selectedTopics.contains(topicToRename)) {
                                selectedTopics = (selectedTopics.filterNot { it == topicToRename } + newName).distinct()
                            }
                        }
                    )
                }

                val allVerseToList by bibleViewModel.allVerses.collectAsState()
                ListVerses(selectedTopics, allVerseToList, navController)
            }
        }
    )

    BackPressHandler {
        navController.navigate(Screen.Home) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }
}


@Composable
private fun ColumnScope.ListVerses(
    selectedTopics: List<String>,
    allVerseToList: List<BibleVerse>,
    navController: NavController
) {
    if (selectedTopics.isNotEmpty()) {
        val filteredVerses = allVerseToList.filter { verseItem ->
            selectedTopics.any { selectedTopicNormalized ->
                verseItem.topics.any { verseTopicFromDb ->
                    verseTopicFromDb.equals(selectedTopicNormalized, ignoreCase = true)
                }
            }
        }

        Log.i("ShowVerseByTopic", "Found ${filteredVerses.size} verses matching ANY selected topics.")

        if (filteredVerses.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredVerses) { verseItem ->
                    VerseCard(verseItem, navController) // Assuming VerseCard is defined elsewhere
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        } else {
            Text(
                text = "No verses found matching any of the selected topics.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .align(Alignment.CenterHorizontally) // Center if no verses
            )
        }
    }
}

private fun onSelectTopicCheckBox(
    isChecked: Boolean,
    existingSelectedTopics: List<String>,
    topicWithCount: TopicWithCount
): List<String> {
    var newSelectedTopics = existingSelectedTopics.toMutableList() // Work with mutable list
    if (isChecked) {
        if (!newSelectedTopics.any { it.equals(topicWithCount.topic, ignoreCase = false) }) {
            newSelectedTopics.add(topicWithCount.topic)
        }
    } else {
        newSelectedTopics.removeAll { it.equals(topicWithCount.topic, ignoreCase = false) }
    }
    return newSelectedTopics.toList()
}
