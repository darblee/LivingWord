package com.darblee.livingword.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.lazy.items // For LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption

// Define a new data class for display purposes in this screen
data class TopicWithUiCount(
    val name: String,
    val uiVerseCount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenameTopicDialog(
    currentTopicName: String,
    allExistingTopicNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (newName: String, isMerge: Boolean) -> Unit
) {
    var newTopicName by rememberSaveable(currentTopicName) { mutableStateOf(currentTopicName) }
    var uiErrorMessage by remember { mutableStateOf<String?>(null) }
    var showMergeWarning by remember { mutableStateOf(false) }
    var potentialMergeTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(newTopicName, currentTopicName, allExistingTopicNames) {
        val trimmedNew = newTopicName.trim()
        uiErrorMessage = null
        showMergeWarning = false
        potentialMergeTarget = null

        if (trimmedNew.isBlank()) {
            uiErrorMessage = "Topic name cannot be blank."
        } else if (trimmedNew.length > 50) {
            uiErrorMessage = "Topic name is too long (max 50 chars)."
        } else if (!trimmedNew.equals(currentTopicName, ignoreCase = false)) {
            val existingMatch = allExistingTopicNames.find { dbTopic ->
                dbTopic.equals(trimmedNew, ignoreCase = true) && !dbTopic.equals(currentTopicName, ignoreCase = true)
            }

            if (existingMatch != null) {
                if (Global.DEFAULT_TOPICS.any { it.equals(existingMatch, ignoreCase = true) }) {
                    uiErrorMessage = "Cannot merge into default topic: \"$existingMatch\"."
                    showMergeWarning = false
                } else {
                    showMergeWarning = true
                    potentialMergeTarget = existingMatch
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
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedNewName = newTopicName.trim()
                    if (uiErrorMessage == null && trimmedNewName.isNotBlank()) {
                        onConfirm(trimmedNewName, showMergeWarning)
                    } else if (uiErrorMessage == null && trimmedNewName.isBlank()){
                        uiErrorMessage = "Topic name cannot be blank."
                    }
                },
                enabled = uiErrorMessage == null || (showMergeWarning && uiErrorMessage?.startsWith("Cannot merge into default topic") == false)
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
fun TopicScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {
    val context = LocalContext.current

    val allDbTopicsWithCount by bibleViewModel.allTopicsWithCount.collectAsState() //
    val allVerseToList by bibleViewModel.allVerses.collectAsState() //

    // Recalculate counts based on ListVerses logic
    val topicsForDisplay: List<TopicWithUiCount> = remember(allDbTopicsWithCount, allVerseToList) {
        allDbTopicsWithCount.map { dbTopicWithCount ->
            val uiCount = allVerseToList.count { verseItem ->
                verseItem.topics.any { verseTopicInItem ->
                    verseTopicInItem.equals(dbTopicWithCount.topic, ignoreCase = true)
                }
            }
            TopicWithUiCount(name = dbTopicWithCount.topic, uiVerseCount = uiCount)
        }
    }

    var selectedTopics by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() } //
    val errorMessage by bibleViewModel.errorMessage.collectAsState() //

    LaunchedEffect(errorMessage) { //
        errorMessage?.let { //
            snackbarHostState.showSnackbar(it) //
            bibleViewModel.clearErrorMessage() //
        }
    }


    val showErrorDialog = remember { mutableStateOf(false) }
    val errMessage = remember { mutableStateOf("Something went wrong!") }

    AppScaffold(
        title = { Text("Meditate God's Word by Topic(s)") },
        navController = navController,
        currentScreenInstance = Screen.TopicScreen,
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues) //
                    .fillMaxSize() //
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box { //
                    Text(
                        text = "Select one or more topics to perform task.", //
                        style = MaterialTheme.typography.bodyLarge, //
                        modifier = Modifier.padding(top = 16.dp, start = 8.dp) //
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2), //
                        modifier = Modifier
                            .heightIn(max = (4 * 48).dp) //
                            .border(width = 1.dp, color = Color.Gray) //
                            .padding(top = 35.dp) //
                    ) {
                        items(topicsForDisplay.size) { index -> // Use topicsForDisplay
                            val topicToDisplay = topicsForDisplay[index]
                            val isSelected = selectedTopics.contains(topicToDisplay.name) //
                            Row(
                                modifier = Modifier
                                    .selectable( //
                                        selected = isSelected, //
                                        onClick = { //
                                            // Pass topicToDisplay.name instead of TopicWithCount object
                                            selectedTopics = onSelectTopicCheckBox(
                                                !isSelected,
                                                selectedTopics,
                                                topicToDisplay.name // Pass name
                                            )
                                        }
                                    )
                                    .padding(vertical = 0.dp), //
                                verticalAlignment = Alignment.CenterVertically //
                            ) {
                                Checkbox(
                                    checked = isSelected, //
                                    onCheckedChange = { isChecked -> //
                                        selectedTopics = onSelectTopicCheckBox(
                                            isChecked,
                                            selectedTopics,
                                            topicToDisplay.name // Pass name
                                        )
                                    }
                                )
                                Text(
                                    // Use topicToDisplay.name and topicToDisplay.uiVerseCount
                                    text = "${topicToDisplay.name} (${topicToDisplay.uiVerseCount})",
                                    style = MaterialTheme.typography.bodyLarge, //
                                    modifier = Modifier.padding(start = 0.dp) //
                                )
                            }
                        }
                    }

                    Text(
                        // Use topicsForDisplay.size
                        text = "${topicsForDisplay.size} topics",
                        style = MaterialTheme.typography.labelLarge, //
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), //
                        modifier = Modifier
                            .offset(x = 12.dp, y = (-8).dp) //
                            .background( //
                                color = MaterialTheme.colorScheme.surface, //
                                shape = RoundedCornerShape(2.dp) //
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp) //
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
                                    val topicName = selectedTopics.first()
                                    if (Global.DEFAULT_TOPICS.any { defaultTopic -> defaultTopic.equals(topicName, ignoreCase = true) }) {
                                        bibleViewModel.postUserMessage("Default topic \"$topicName\" cannot be renamed.")
                                        Log.i("ShowVerseBYTopic", "Default topic \"$topicName\" cannot be renamed." )
                                        errMessage.value = "Default topic \"$topicName\" cannot be renamed."
                                        showErrorDialog.value = true
                                    } else {
                                        showRenameDialog = true
                                    }
                                }
                            },
                            enabled = selectedTopics.count() == 1, //
                            modifier = Modifier
                                .weight(1f) //
                                .padding(start = 8.dp) //
                        ) {
                            Text("Rename topic") //
                        }

                        Spacer(modifier = Modifier.width(8.dp)) //

                        Button(
                            onClick = { //
                                // Use topicsForDisplay to check uiVerseCount for deletion eligibility
                                val topicsPotentiallyDeletable = selectedTopics.filter { selectedTopicName ->
                                    topicsForDisplay.find { it.name == selectedTopicName }?.uiVerseCount == 0
                                }

                                if (topicsPotentiallyDeletable.isNotEmpty()) {
                                    val defaultTopicsAttempted = topicsPotentiallyDeletable.filter { topicName ->
                                        Global.DEFAULT_TOPICS.any { defaultTopic -> defaultTopic.equals(topicName, ignoreCase = true) } //
                                    }

                                    var messageToUser = ""

                                    if (defaultTopicsAttempted.isNotEmpty()) {
                                        val names = defaultTopicsAttempted.joinToString(", ") { "\"$it\"" }
                                        messageToUser = "Default topic(s) $names cannot be deleted. " //
                                        Log.i("ShowVerseBYTopic", "Default topic $names cannot be deleted." )
                                        errMessage.value = "Default topic $names cannot be deleted."
                                        showErrorDialog.value = true
                                    }

                                    val actualTopicsToDelete = topicsPotentiallyDeletable.filterNot { topicName ->
                                        defaultTopicsAttempted.contains(topicName)
                                    }

                                    if (actualTopicsToDelete.isNotEmpty()) {
                                        bibleViewModel.deleteTopics(actualTopicsToDelete) //
                                        selectedTopics = selectedTopics.filterNot { actualTopicsToDelete.contains(it) } //
                                        messageToUser += if (defaultTopicsAttempted.isEmpty()) "Selected topic(s) with 0 verses submitted for deletion." else "Other selected topics with 0 verses submitted for deletion." //
                                    }

                                    if (messageToUser.isNotBlank()) {
                                        bibleViewModel.postUserMessage(messageToUser.trim()) //
                                    }
                                }
                            },
                            // Update enabled logic to use topicsForDisplay and uiVerseCount
                            enabled = selectedTopics.isNotEmpty() && selectedTopics.all { selTopic ->
                                topicsForDisplay.find { it.name == selTopic }?.uiVerseCount == 0
                            },
                            modifier = Modifier
                                .weight(1f) //
                                .padding(end = 8.dp) //
                        ) {
                            Text("Delete Topic(s)") //
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (showRenameDialog && selectedTopics.count() == 1) { //
                    val topicToRename = selectedTopics.first() //
                    RenameTopicDialog(
                        currentTopicName = topicToRename, //
                        allExistingTopicNames = topicsForDisplay.map { it.name }, // Use names from topicsForDisplay
                        onDismiss = { showRenameDialog = false }, //
                        onConfirm = { newName, isMerge -> //
                            showRenameDialog = false //
                            bibleViewModel.renameOrMergeTopic(topicToRename, newName, isMerge) //
                            if (selectedTopics.contains(topicToRename)) { //
                                selectedTopics = (selectedTopics.filterNot { it == topicToRename } + newName).distinct() //
                            }
                        }
                    )
                }
                ListVerses(selectedTopics, allVerseToList, navController) //
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

    ErrorDialog(
        showDialog = showErrorDialog,
        errorMessage = errMessage.value
    )
}


@Composable
private fun ColumnScope.ListVerses(
    selectedTopics: List<String>,
    allVerseToList: List<BibleVerse>,
    navController: NavController
) {
    if (selectedTopics.isNotEmpty()) {
        val filteredVerses = allVerseToList.filter { verseItem -> //
            selectedTopics.any { selectedTopicNormalized -> //
                verseItem.topics.any { verseTopicFromDb -> //
                    verseTopicFromDb.equals(selectedTopicNormalized, ignoreCase = true) //
                }
            }
        }

        Log.i("ShowVerseByTopic", "Found ${filteredVerses.size} verses matching ANY selected topics.") //

        if (filteredVerses.isNotEmpty()) {
            LazyColumn(modifier = Modifier.weight(1f)) { //
                items(filteredVerses) { verseItem -> //
                    VerseCard(verseItem, navController) //
                    Spacer(modifier = Modifier.height(2.dp)) //
                }
            }
        } else {
            Text(
                text = "No verses found matching any of the selected topics.", //
                style = MaterialTheme.typography.bodyLarge, //
                modifier = Modifier
                    .padding(vertical = 16.dp) //
                    .align(Alignment.CenterHorizontally) //
            )
        }
    }
}

// Modify onSelectTopicCheckBox to accept topicName: String
private fun onSelectTopicCheckBox(
    isChecked: Boolean,
    existingSelectedTopics: List<String>,
    topicName: String // Changed from TopicWithCount
): List<String> {
    val newSelectedTopics = existingSelectedTopics.toMutableList() //
    if (isChecked) {
        if (!newSelectedTopics.any { it.equals(topicName, ignoreCase = false) }) { //
            newSelectedTopics.add(topicName) //
        }
    } else {
        newSelectedTopics.removeAll { it.equals(topicName, ignoreCase = false) } //
    }
    return newSelectedTopics.toList() //
}