package com.darblee.livingword.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.getTargetScreen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BookInfo
import com.darblee.livingword.data.ScriptureTaskType
import com.darblee.livingword.ui.theme.ColorThemeOption

/**
 * Composable function for the Verse Picker Book Screen.
 * This screen allows the user to select a book from the Old or New Testament.
 * It displays books in a tabbed layout (Old Testament and New Testament).
 *
 * @param navController The NavHostController for navigation.
 * @param currentTheme The current color theme of the application.
 * @param returnScreen The route of the screen to return to after selection or cancellation.
 */
@Composable
fun VersePickerBookScreen(
    navController: NavHostController,
    currentTheme: ColorThemeOption,
    returnScreen: String,
    scriptureTaskType: ScriptureTaskType
) {
    // State to keep track of the selected tab index
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("Old Testament", "New Testament")

    // Fetch book lists from BibleData. Remember them to avoid fetching on every recomposition.
    // Note: This assumes BibleData.init() has been called earlier.
    val oldTestamentBooks = remember { BibleData.getOldTestamentBooks() }
    val newTestamentBooks = remember { BibleData.getNewTestamentBooks() }

    Scaffold{ paddingValues ->
        Column (
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp) // Padding for the overall column
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            style = MaterialTheme.typography.titleMedium.toSpanStyle().copy(
                                color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                            )
                        ) {
                            append("[Book] [Chapter] [Verse - Verse]") // Non-bold part
                        }
                    }
                )

                // Spacer to push the Cancel button all the way to the right of screen
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

            TabRow(selectedTabIndex = selectedTabIndex) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp)

            val currentBooks = if (selectedTabIndex == 0) oldTestamentBooks else newTestamentBooks

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = (Global.BUTTON_WIDTH + 5).dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(15.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                itemsIndexed(currentBooks) { index, bookInfo ->

                    val buttonColor = if (currentTheme == ColorThemeOption.Light) {
                        Color.Blue
                    } else {
                        if (currentBooks == oldTestamentBooks)
                            BibleData.oldTestamentBookColor(index)
                        else
                            BibleData.newTestamentBookColor(index)
                    }

                    BookButton(
                        bookInfo = bookInfo,
                        onClick = {
                            // Navigate to chapter selection with the selected book
                            navController.navigate(
                                Screen.VersePickerChapterScreen(
                                    returnScreen,
                                    bookInfo.fullName,
                                    scriptureTaskType
                                )
                            )
                        },
                        buttonColor = buttonColor
                    )
                }
            }
        }
    }
}

/**
 * A Composable function that displays a button for a book of the Bible.
 *
 * This button, when clicked, will trigger the provided `onClick` action, which typically
 * navigates to the chapter selection screen for the given book.
 *
 * @param bookInfo The [BookInfo] object containing details about the book (e.g., full name).
 * @param onClick A lambda function to be executed when the button is clicked.
 * @param buttonColor The [Color] to be used for the text of the button.
 */
@Composable
private fun BookButton(
    bookInfo: BookInfo,
    onClick: () -> Unit,
    buttonColor: Color,
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(Global.BUTTON_WIDTH.dp)
            .height(Global.BUTTON_HEIGHT.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), // Make button transparent
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(color = buttonColor))
            {
                Text(bookInfo.fullName)
            }
        }
    }
}