package com.darblee.livingword.ui.screens


import android.util.Log
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
import com.darblee.livingword.Global.VERSE_RESULT_KEY
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BibleVerseRef
import kotlinx.serialization.json.Json

@Composable
fun GetStartVerseNumberScreen(
    navController: NavHostController,
    book: String,    // Argument received automatically
    chapter: Int   // Argument received automatically
) {
    val scrollState = rememberScrollState() // Scroll state for take-away BasicTextField

    Scaffold { paddingValues ->
        // Main Column for the entire screen content
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp) // Padding for the overall column
        ) {
            // Top Row for "Get Verse: ..." text, left-aligned
            Row(
                modifier = Modifier.fillMaxWidth(), // Take full width
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                Log.i("GetStartVerseNUmberScreen", "Navigate Book")
                                navController.navigate(route = Screen.GetBookScreen)
                            },
                            role = Role.Button, // For accessibility
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleLarge.toSpanStyle()) {
                            append("$book ")
                        }
                    }
                    Text(text = annotatedString)
                }

                Box(
                    modifier = Modifier
                        .clickable(
                            onClick = {
                                Log.i("GetVerseNUmberScreen", "Navigate chapter")
                                navController.navigate(route = Screen.GetChapterScreen(book = book))
                            },
                            role = Role.Button, // For accessibility
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                    // .padding(horizontal = 4.dp) // Deliberately no padding here
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleLarge.toSpanStyle()) {
                            append("$chapter ")
                        }
                    }
                    Text(text = annotatedString)
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleLarge.toSpanStyle().copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                        )) {
                            append(" [Verse - Verse]")
                        }
                    }
                )
                // Spacer to push the button to the right
                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        navController.popBackStack(
                            route = Screen.AllVersesScreen, // Destination to pop up to
                            inclusive = false
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                )
                {
                    Text("Cancel") // Shortened text
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Space before divider

            // Horizontal Divider line
            HorizontalDivider(thickness = 1.dp)

            Spacer(modifier = Modifier.height(16.dp)) // Space after divider

            // Inner Column for the scrollable content (headers and grids)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scrollable(state = scrollState, orientation = Orientation.Vertical ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                // Get the list of verse numbers for the selected book and chapter
                val verses: List<Int> =
                    remember(book, chapter) { // Recalculate if book/chapter changes
                        BibleData.getVersesForBookChapter(book, chapter)
                    }

                Text("Select Starting Verse", style = MaterialTheme.typography.headlineMedium)

                Text(
                    text = "(Double-click to select one verse, Single-click to select range)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp)) // Add space between sections

                // Grid for displaying verse buttons
                if (verses.isNotEmpty()) {
                    LazyVerticalGrid(
                        // Adjust column size for potentially smaller numbers
                        columns = GridCells.Adaptive(minSize = 60.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), // Allow grid to take available vertical space,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp) // Add padding at the bottom of the grid
                    ) {
                        items(verses) { verse ->
                            val onDoubleClickAction: (Int) -> Unit = { selectedVerse ->
                                // --- Pass Result Back ---
                                // 1. Create the result data object
                                val result = BibleVerseRef(
                                    book = book,
                                    chapter = chapter,
                                    startVerse = verse,
                                    endVerse = verse
                                )

                                val resultJson =
                                    Json.encodeToString(BibleVerseRef.serializer(), result)

                                val newVerseScreenBackStackEntry =
                                    navController.getBackStackEntry(Screen.AllVersesScreen)
                                val newVerseScreenSavedStateHandle =
                                    newVerseScreenBackStackEntry.savedStateHandle

                                // VERSE_RESULT_KEY needs to be accessible here or defined globally
                                newVerseScreenSavedStateHandle[VERSE_RESULT_KEY] = resultJson

                                navController.popBackStack(
                                    route = Screen.AllVersesScreen,
                                    inclusive = false
                                )
                            }

                            VerseButton(verse = verse, onClick = { selectedVerse ->
                                navController.navigate(
                                    Screen.GetEndVerseNumberScreen(
                                        book = book,
                                        chapter = chapter,
                                        startVerse = verse
                                    )
                                )
                            }, onDoubleClick = onDoubleClickAction)
                        }
                    }
                } else {
                    // Show message if no verses are found (data issue)
                    Text(
                        text = "No verse data found for $book $chapter.",
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 32.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), // Padding around the row
                    horizontalArrangement = Arrangement.SpaceEvenly, // Distribute buttons evenly
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            navController.popBackStack(
                                route = Screen.GetBookScreen, // Destination to pop up to
                                inclusive = false
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Global.SMALL_ACTION_BUTTON_MODIFIER,
                        contentPadding = Global.SMALL_ACTION_BUTTON_PADDING
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null, // Description is implied by text now
                            modifier = Modifier.size(ButtonDefaults.IconSize) // Use default icon size
                        )
                        Text("Select Book") // Shortened text
                    }

                    Button(
                        onClick = {
                            navController.popBackStack(
                                route = Screen.GetChapterScreen(book = book), // Destination to pop up to
                                inclusive = false
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Global.SMALL_ACTION_BUTTON_MODIFIER,
                        contentPadding = Global.SMALL_ACTION_BUTTON_PADDING
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null, // Description is implied by text now
                            modifier = Modifier.size(ButtonDefaults.IconSize) // Use default icon size
                        )
                        Text("Select Chapter") // Shortened text
                    }
                }
            }
        }
    }
}

@Composable
fun VerseButton(verse: Int, onClick: (Int) -> Unit, onDoubleClick: (Int) -> Unit) {
    Button(
        onClick = { /* Handled by combinedClickable on inner Box */ },
        modifier = Modifier
            .width(Global.BUTTON_WIDTH.dp)
            .height(Global.BUTTON_HEIGHT.dp),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), // Make button transparent
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
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge)
            {
                Text(text = verse.toString(), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
