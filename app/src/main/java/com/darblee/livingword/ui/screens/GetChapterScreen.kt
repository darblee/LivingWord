package com.darblee.livingword.ui.screens

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import com.darblee.livingword.data.BibleData

@Composable
fun GetChapterScreen(
    navController: NavHostController,
    book: String // Argument received automatically via toRoute() in NavHost
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
                                Log.i("GetChapterNUmberScreen", "Navigate Book")
                                navController.navigate(route = Screen.GetBookScreen)
                            },
                            role = Role.Button, // For accessibility
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        )
                    // .padding(horizontal = 4.dp) // Deliberately no padding here
                ) {
                    val annotatedString = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle()) {
                            append("$book ")
                        }
                    }
                    Text(text = annotatedString)
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle().copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                        )) {
                            append(" [Chapter] [Verse - Verse]")
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
                // Get the list of chapter numbers for the selected book
                val chapters: List<Int> = remember(book) { // Recalculate if book changes
                    BibleData.getChaptersForBook(book)
                }

                Text("Select Chapter", style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(8.dp)) // Add space between sections

                // Grid for displaying chapter buttons
                if (chapters.isNotEmpty()) {
                    LazyVerticalGrid(
                        // Adjust column size based on available width
                        columns = GridCells.Adaptive(minSize = Global.BUTTON_WIDTH.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), // Allow grid to take available vertical space,
                        // Spacing between grid items
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp) // Add padding at the bottom of the grid

                    ) {
                        items(chapters) { chapter ->
                            ChapterButton(chapter = chapter) { selectedChapter ->
                                navController.navigate(
                                    Screen.GetStartVerseNumberScreen(
                                        book = book,
                                        chapter = chapter
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // Show message if no chapters are found (data issue)
                    Text(
                        text = "No chapter data found for $book.",
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 32.dp)
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
                }
            }
        }
    }
}

// Reusable composable for chapter buttons
@Composable
private fun ChapterButton(chapter: Int, onClick: (Int) -> Unit) {
    Button(
        modifier = Modifier
            .width(Global.BUTTON_WIDTH.dp)
            .height(Global.BUTTON_HEIGHT.dp),
        onClick = { onClick(chapter) },
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), // Make button transparent
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge)
        {
            Text(text = chapter.toString(), color = MaterialTheme.colorScheme.primary)
        }
    }
}
