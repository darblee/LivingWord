package com.darblee.livingword.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                horizontalArrangement = Arrangement.Start // Align content to the left
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                            append("Verse:  ")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Thin, fontSize = 15.sp)) {
                            append(book)
                        }
                        withStyle(style = SpanStyle(
                            fontWeight = FontWeight.Thin,
                            fontSize = 15.sp,
                            color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                        )) {
                            append(" [Chapter] [Verse - Verse]")
                        }
                    }
                )
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

                Text(
                    text = "Select Chapter",
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

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
                                route = Screen.LearnScreen, // Destination to pop up to
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
                        Text("Learn") // Shortened text
                    }

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
                        Text("Book") // Shortened text
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
        ProvideTextStyle(
            value = TextStyle(
                fontSize = 21.sp,
                color = Color.White
            )
        ) {
            // Display the abbreviation from BookInfo
            Text(text = chapter.toString())
        }
    }
}
