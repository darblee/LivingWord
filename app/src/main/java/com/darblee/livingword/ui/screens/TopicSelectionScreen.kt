package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.darblee.livingword.Global
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.domain.model.TopicSelectionViewModel
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Composable
fun TopicSelectionScreen(
    topicSelectionViewModel: TopicSelectionViewModel = viewModel(),
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    selectedTopicsJson: String?
) {
    val state by topicSelectionViewModel.state.collectAsState()

    // Get the snapshot of all topic items
    val allTopicItems by bibleViewModel.allTopicItems.collectAsState()

    // Convert the snapshot list of topic items into a list of topic names
    val allTopics: List<String> = allTopicItems.map { it.topic.trim() }

    // Deserialize selectedTopicsJson if it's not null
    val preSelectedTopics = remember(selectedTopicsJson) {

        if (selectedTopicsJson != null) {
            Log.i("TopicSelectionScreen", "Received the JSON topics: $selectedTopicsJson")

            try {
                Json.decodeFromString(ListSerializer(String.serializer()), selectedTopicsJson)
            } catch (e: Exception) {
                Log.e("TopicSelectionScreen", "Error deserializing selected topics: ${e.message}", e)
                emptyList<String>() // Return empty list on error
            }
        } else {
            emptyList<String>()
        }
    }

    LaunchedEffect(allTopics, preSelectedTopics) { // Use allTopics and preSelectedTopics as keys
        Log.i("TopicSelectionScreen", "Received the topics: $preSelectedTopics, count : ${preSelectedTopics.size}")
        topicSelectionViewModel.initializeTopics(allTopics + Global.DEFAULT_TOPICS, preSelectedTopics) // Modify initializeTopics
    }

    LaunchedEffect(Unit) {
        topicSelectionViewModel.initializeTopics(allTopics + Global.DEFAULT_TOPICS, preSelectedTopics)
    }

    TopicSelectionContent(
        topicSelections = state.topicSelections,
        onTopicToggled = topicSelectionViewModel::toggleTopic,
        onAddNewTopic = topicSelectionViewModel::addNewTopic,
        onConfirm = { selectedTopics ->

            // Need to send the data back to the destination screen

            // 1. Serialize the list to JSON String using kotlinx.serialization
            val resultJson = try {
                Json.encodeToString(ListSerializer(String.serializer()), selectedTopics)
            } catch (e: Exception) {
                Log.e("TopicSelectionScreen", "Error serializing topics: ${e.message}", e)
                "" // Return empty string on error
            }

            // 2. Get the SavedStateHandle of the destination screen
            try {
                val prevScreenBackStackEntry = navController.previousBackStackEntry
                if (prevScreenBackStackEntry != null) {

                    val prevScreenSavedStateHandle = prevScreenBackStackEntry.savedStateHandle

                    // 3. Set the JSON String result
                    prevScreenSavedStateHandle[Global.TOPIC_SELECTION_RESULT_KEY] = resultJson

                    // 4. Pop back stack to previous screen
                    navController.popBackStack()
                } else {
                    Log.e("TopicSelectionScreen", "No previous screen found on back stack.")
                    // Handle error or navigate to a default screen
                    navController.popBackStack()
                }
            } catch (e: IllegalStateException) {
                Log.e("TopicSelectionScreen", "Could not find previous screen on back stack.", e)
                // Handle error - maybe just pop?
                navController.popBackStack()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicSelectionContent(
    topicSelections: Map<String, Boolean>,
    onTopicToggled: (String) -> Unit,
    onAddNewTopic: (String) -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    // Retrieves the current keyboard controller instance that you can use in your composable
    // In this case, it needs to hide the keyboard after the user submits a text
    val keyboardController = LocalSoftwareKeyboardController.current
    var newTopicText by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) } // Added state for dialog

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Topic Selection") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
        ) {
            Text(
                text = "Select Topics. Add new one if needed",
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = newTopicText,
                    onValueChange = { newTopicText = it },
                    label = { Text("Add New Topic") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,

                    /***
                     * "IME" stands for "Input Method Editor" - essentially the software keyboard
                     *
                     *  When the user taps on our "Add New Topic" text field, they'll see a keyboard where the bottom-right key (usually Enter/Return)
                     *  is replaced with a button labeled "Done". This visually indicates to the user that pressing this button will complete their
                     *  current task of entering a new topic.
                     *
                     *  By itself, setting imeAction = ImeAction.Done only changes how the button looks - it doesn't define what happens when it's pressed.
                     *  That's why in our code, we pair it with KeyboardActions:
                     */
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (newTopicText.isNotBlank()) {
                                onAddNewTopic(newTopicText.trim())
                                newTopicText = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        if (newTopicText.isNotBlank()) {
                            onAddNewTopic(newTopicText.trim())
                            newTopicText = ""
                            keyboardController?.hide()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Topic"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // 2 columns
                verticalArrangement = Arrangement.spacedBy(1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                topicSelections.keys.toList().sorted().forEach { topic ->
                    item {
                        TopicCheckboxItem(
                            topic = topic,
                            isChecked = topicSelections[topic] == true,
                            onCheckedChange = { onTopicToggled(topic) }
                        )
                    }
                    Log.i("TopicSelection", "topic = $topicSelections")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Convert selections to list of selected topics
                    val selectedTopics = topicSelections.filter { it.value }.keys.toList()
                    Log.i("TopicSelectionContent", "Number of topic selected: ${selectedTopics.size}")
                    if (selectedTopics.isEmpty()) { // Check if no topics are selected
                        showDialog = true
                    } else {
                        onConfirm(selectedTopics)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Confirm Selection")
            }

            // Dialog to show when no topics are selected
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("No Topic Selected") },
                    text = { Text("Please select at least one topic to continue.") },
                    confirmButton = {
                        TextButton(onClick = { showDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TopicCheckboxItem(
    topic: String,
    isChecked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange() }
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onCheckedChange() }
        )

        Spacer(modifier = Modifier.width(1.dp))

        Text(
            text = topic,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}