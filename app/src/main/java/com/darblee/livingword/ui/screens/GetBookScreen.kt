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
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BookInfo
import com.darblee.livingword.ui.theme.ColorThemeOption

@Composable
fun GetBookScreen(
    navController: NavHostController,
    currentTheme: ColorThemeOption
) {

    // Fetch book lists from BibleData. Remember them to avoid fetching on every recomposition.
    // Note: This assumes BibleData.init() has been called earlier.
    val oldTestamentBooks = remember { BibleData.getOldTestamentBooks() }
    val newTestamentBooks = remember { BibleData.getNewTestamentBooks() }

    // State to keep track of the selected tab index
    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Old Testament", "New Testament")

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
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = MaterialTheme.typography.titleLarge.toSpanStyle().copy(
                            color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                        )) {
                            append("[Book] [Chapter] [Verse - Verse]") // Non-bold part
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

            // TabRow for switching between Old and New Testaments
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.titleSmall) }
                    )
                }
            }

            // Content of the selected tab
            when (tabIndex) {
                0 -> OldTestamentTab(navController, oldTestamentBooks, currentTheme)
                1 -> NewTestamentTab(navController, newTestamentBooks, currentTheme)
            }
        }
    }
}

@Composable
fun OldTestamentTab(
    navController: NavHostController,
    books: List<BookInfo>,
    currentTheme: ColorThemeOption
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = (Global.BUTTON_WIDTH + 5).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 16.dp),
        modifier = Modifier.fillMaxSize(),
        content = {
            // Use itemsIndexed to get index for color logic
            itemsIndexed(books) { index, bookInfo ->
                val buttonColor = if (currentTheme == ColorThemeOption.Light) {
                    Color.Blue
                } else {
                    // Color logic based on index within the OT list
                    when (index) {
                        in 0..4 -> Color.Yellow // Pentateuch
                        in 5..16 -> Color.Green // History
                        in 17..21 -> Color(0xFF800080) // Wisdom/Poetry
                        in 22..26 -> Color.Red // Major Prophets
                        else -> Color.Blue // Minor Prophets
                    }
                }
                BookButton(navController, bookInfo, buttonColor)
            }
        }
    )
}

@Composable
fun NewTestamentTab(
    navController: NavHostController,
    books: List<BookInfo>,
    currentTheme: ColorThemeOption
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = (Global.BUTTON_WIDTH + 5).dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(top = 16.dp),
        modifier = Modifier.fillMaxSize(),
        content = {
            // Use itemsIndexed for the NT list as well
            itemsIndexed(books) { index, bookInfo ->
                val buttonColor = if (currentTheme == ColorThemeOption.Light) {
                    Color.Blue
                } else {
                    // Color logic based on index within the NT list
                    when (index) {
                        in 0..3 -> Color.Yellow // Gospels
                        4 -> Color.Green // Acts (History)
                        in 5..13 -> Color(0xFF800080) // Pauline Epistles (General)
                        in 14..17 -> Color(0xFFFFA500) // Pauline Epistles (Pastoral)
                        in 18..25 -> Color.Red // General Epistles
                        else -> Color.Blue // Revelation (Apocalyptic)
                    }
                }
                BookButton(navController, bookInfo, buttonColor)
            }
        }
    )
}


// Reusable composable for book buttons
@Composable
private fun BookButton(
    navController: NavHostController,
    bookInfo: BookInfo,
    buttonColor: Color
) {
    Button(
        modifier = Modifier
            .width(Global.BUTTON_WIDTH.dp)
            .height(Global.BUTTON_HEIGHT.dp),
        onClick = {
            // Navigate using the full name from BookInfo
            navController.navigate(Screen.GetChapterScreen(book = bookInfo.fullName))
        },
        contentPadding = PaddingValues(4.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), // Make button transparent
    ) {
        ProvideTextStyle(value = MaterialTheme.typography.labelLarge.copy(color = buttonColor))
        {
            Text(bookInfo.fullName)
        }
    }
}