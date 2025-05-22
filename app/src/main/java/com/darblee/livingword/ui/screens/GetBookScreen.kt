package com.darblee.livingword.ui.screens


import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.BookInfo

@Composable
fun GetBookScreen(navController: NavHostController) {

    // Fetch book lists from BibleData. Remember them to avoid fetching on every recomposition.
    // Note: This assumes BibleData.init() has been called earlier.
    val oldTestamentBooks = remember { BibleData.getOldTestamentBooks() }
    val newTestamentBooks = remember { BibleData.getNewTestamentBooks() }

    // Remember the scroll state for the main Column
    val scrollState = rememberScrollState()

    // Get current configuration to check orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Determine grid height based on orientation
    val gridHeight = if (isLandscape) 150.dp else 300.dp

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
                        withStyle(style = SpanStyle(
                            fontWeight = FontWeight.Thin,
                            fontSize = 15.sp,
                            color = LocalContentColor.current.copy(alpha = 0.5f)  // Set alpha for 50% transparency
                        )) {
                            append("[Book] [Chapter] [Verse - Verse]") // Non-bold part
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
                    .fillMaxWidth() // Take remaining width
                    .weight(1f) // Take remaining height
                    .verticalScroll(scrollState), // Make this part scrollable
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp) // Adjusted spacing
            ) {
                Text(
                    "Old Testament",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )

                val verticalSpacing = 15 // Use a fixed vertical spacing

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = (Global.BUTTON_WIDTH + 5).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
                    modifier = Modifier.height(gridHeight+50.dp),
                    content = {
                        // Use itemsIndexed to get index for color logic
                        itemsIndexed(oldTestamentBooks) { index, bookInfo ->
                            // Color logic based on index within the OT list
                            val buttonColor = when (index) {
                                in 0..4 -> Color.Yellow // Pentateuch (adjust count if needed)
                                in 5..16 -> Color.Green // History (adjust count if needed)
                                in 17..21 -> Color(0xFF800080) // Wisdom/Poetry (adjust count if needed)
                                in 22..26 -> Color.Red // Major Prophets (adjust count if needed)
                                else -> Color.Blue // Minor Prophets
                            }
                            bookButton(navController, bookInfo, buttonColor)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(4.dp)) // Add space between sections

                Text(
                    "New Testament",
                    style = TextStyle(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = (Global.BUTTON_WIDTH + 5).dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp),
                    modifier = Modifier.height(gridHeight-25.dp),
                    content = {
                        // Use itemsIndexed for the NT list as well
                        itemsIndexed(newTestamentBooks) { index, bookInfo ->
                            // Color logic based on index within the NT list
                            val buttonColor = when (index) {
                                in 0..3 -> Color.Yellow // Gospels
                                4 -> Color.Green // Acts (History)
                                in 5..13 -> Color(0xFF800080) // Pauline Epistles (General)
                                in 14..17 -> Color(0xFFFFA500) // Pauline Epistles (Pastoral - Orange example)
                                in 18..25 -> Color.Red // General Epistles
                                else -> Color.Blue // Revelation (Apocalyptic)
                            }
                            bookButton(navController, bookInfo, buttonColor)
                        }
                    }
                )

                Button(onClick = {
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
                    Text("Learn")
                }
            }
        }
    }
}

// Reusable composable for book buttons
@Composable
private fun bookButton(
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
        ProvideTextStyle(
            value = TextStyle(
                fontSize = 21.sp,
                color = buttonColor // Set text color based on logic
            )
        ) {
            // Display the abbreviation from BookInfo
            Text(bookInfo.abbreviation)
        }
    }
}
