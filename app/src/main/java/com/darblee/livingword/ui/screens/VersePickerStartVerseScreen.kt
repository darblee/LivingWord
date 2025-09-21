package com.darblee.livingword.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.getTargetScreen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.ScriptureTaskType
import com.darblee.livingword.data.BibleVerseRef
import kotlinx.serialization.json.Json

/**
 * Composable function for the Verse Picker Start Verse Screen.
 * This screen allows the user to select the starting verse of a Bible reference.
 * It displays a grid of verse numbers for the selected book and chapter.
 *
 * Users can:
 * - Single-click a verse to select it as the starting verse and navigate to the end verse selection screen.
 * - Double-click a verse to select it as both the starting and ending verse, and return to the originating screen with the selected Bible reference.
 * - Navigate back to the book selection screen.
 * - Navigate back to the chapter selection screen.
 * - Cancel the verse selection and return to the originating screen.
 *
 * @param navController The NavHostController for navigation.
 * @param returnScreen The route of the screen to return to after verse selection.
 * @param book The selected Bible book.
 * @param chapter The selected chapter number.
 */
@Composable
fun VersePickerStartVerseScreen(
    navController: NavHostController,
    returnScreen: String,
    book: String,
    chapter: Int,
    scriptureTaskType: ScriptureTaskType
) {
    val scrollState = rememberScrollState()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                navController.navigate(Screen.VersePickerBookScreen(returnScreen, scriptureTaskType))
                            },
                            role = Role.Button,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle()) {
                            append("$book ")
                        }
                    }
                    Text(text = annotatedString)
                }

                Box(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                navController.navigate(Screen.VersePickerChapterScreen(returnScreen, book, scriptureTaskType))
                            },
                            role = Role.Button,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle()) {
                            append("$chapter ")
                        }
                    }
                    Text(text = annotatedString)
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle().copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )) {
                            append(" [Verse - Verse]")
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Navigate back to original screen
                        navController.navigate(getTargetScreen(returnScreen)) {
                            popUpTo(Screen.VersePickerBookScreen(returnScreen, scriptureTaskType)) {
                                inclusive = true
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                ) {
                    Text("Cancel")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollable(state = scrollState, orientation = Orientation.Vertical),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val verses: List<Int> = remember(book, chapter) {
                    BibleData.getVersesForBookChapter(book, chapter)
                }

                Text("Select Starting Verse", style = MaterialTheme.typography.headlineMedium)

                Text(
                    text = "(Double-click to select one verse, Single-click to select range)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (verses.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 60.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(verses) { verse ->
                            val onDoubleClickAction: (Int) -> Unit = { _: Int ->
                                // Return BibleVerseRef for single verse (start = end)
                                val result = BibleVerseRef(
                                    book = book,
                                    chapter = chapter,
                                    startVerse = verse,
                                    endVerse = verse
                                )

                                // Save result to savedStateHandle for the originating screen
                                val resultJson = Json.encodeToString(BibleVerseRef.serializer(), result)
                                // Find the FIRST (original) target screen in the back stack, not the current duplicate
                                val targetBackStackEntry = navController.currentBackStack.value.firstOrNull { entry ->
                                    entry.destination.route?.contains(returnScreen) == true
                                }
                                targetBackStackEntry?.savedStateHandle?.set(Global.VERSE_RESULT_KEY, resultJson)

                                // Convert string back to Screen object and navigate
                                navController.popBackStack(route = getTargetScreen(returnScreen), inclusive = false)
                            }

                            StartVerseButton(verse = verse, onClick = { _: Int ->
                                navController.navigate(
                                    Screen.VersePickerEndVerseScreen(
                                        returnScreen = returnScreen,
                                        book = book,
                                        chapter = chapter,
                                        startVerse = verse,
                                        scriptureTaskType = scriptureTaskType
                                    )
                                )
                            }, onDoubleClick = onDoubleClickAction)
                        }
                    }
                } else {
                    Text(
                        text = "No verse data found for $book $chapter.",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            navController.navigate(Screen.VersePickerBookScreen(returnScreen, scriptureTaskType))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Global.SMALL_ACTION_BUTTON_MODIFIER,
                        contentPadding = Global.SMALL_ACTION_BUTTON_PADDING
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text("Select Book")
                    }

                    Button(
                        onClick = {
                            navController.navigate(Screen.VersePickerChapterScreen(returnScreen, book, scriptureTaskType))
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Global.SMALL_ACTION_BUTTON_MODIFIER,
                        contentPadding = Global.SMALL_ACTION_BUTTON_PADDING
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Text("Select Chapter")
                    }
                }
            }
        }
    }
}

/**
 * Composable function for a button that represents a starting verse.
 *
 * This button displays the verse number and handles both single and double clicks.
 * A single click triggers the [onClick] lambda, typically used to navigate to select an end verse.
 * A double click triggers the [onDoubleClick] lambda, typically used to select a single verse.
 *
 * @param verse The verse number to display on the button.
 * @param onClick A lambda function to be invoked when the button is single-clicked.
 *                It receives the verse number as a parameter.
 * @param onDoubleClick A lambda function to be invoked when the button is double-clicked.
 *                      It receives the verse number as a parameter.
 */
@Composable
private fun StartVerseButton(verse: Int, onClick: (Int) -> Unit, onDoubleClick: (Int) -> Unit) {
    Button(
        onClick = { /* Handled by combinedClickable on inner Box */ },
        modifier = Modifier
            .width(Global.BUTTON_WIDTH.dp)
            .height(Global.BUTTON_HEIGHT.dp),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = { onClick(verse) },
                    onDoubleClick = { onDoubleClick(verse) }
                ),
            contentAlignment = Alignment.Center
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                Text(text = verse.toString(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}