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
import com.darblee.livingword.getTargetScreen
import com.darblee.livingword.data.BibleData

/**
 * Composable function for the Verse Picker Chapter Screen.
 * This screen allows the user to select a chapter from the specified book.
 *
 * @param navController The NavHostController for navigation.
 * @param returnScreen The screen to return to after selection or cancellation.
 * @param book The name of the book for which to display chapters.
 */
@Composable
fun VersePickerChapterScreen(
    navController: NavHostController,
    returnScreen: String,
    book: String
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
                                Log.i("VersePickerChapterScreen", "Navigate Book")
                                navController.navigate(Screen.VersePickerBookScreen(returnScreen))
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

                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleMedium.toSpanStyle().copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f)
                        )) {
                            append(" [Chapter] [Verse - Verse]")
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                Button(
                    onClick = {
                        // Navigate back to original screen
                        navController.navigate(getTargetScreen(returnScreen)) {
                            popUpTo(Screen.VersePickerBookScreen(returnScreen)) {
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
                val chapters: List<Int> = remember(book) {
                    BibleData.getChaptersForBook(book)
                }

                Text("Select Chapter", style = MaterialTheme.typography.headlineMedium)

                Spacer(modifier = Modifier.height(8.dp))

                if (chapters.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = Global.BUTTON_WIDTH.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(chapters) { chapter ->
                            ChapterButton(chapter = chapter) { selectedChapter ->
                                navController.navigate(
                                    Screen.VersePickerStartVerseScreen(
                                        returnScreen = returnScreen,
                                        book = book,
                                        chapter = chapter
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No chapter data found for $book.",
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
                            navController.navigate(Screen.VersePickerBookScreen(returnScreen))
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
                }
            }
        }
    }
}

/**
 * A composable function that creates a button for a chapter.
 *
 * @param chapter The chapter number.
 * @param onClick A callback function that is invoked when the button is clicked.
 *                It receives the chapter number as a parameter.
 */
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
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
            Text(text = chapter.toString(), color = MaterialTheme.colorScheme.primary)
        }
    }
}