package com.darblee.livingword.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.darblee.livingword.Screen
import com.darblee.livingword.data.BibleVerse
import com.darblee.livingword.data.verseReference

@Composable
fun VerseCard(verseItem: BibleVerse, navController: NavController) {
    var showVerseDetail by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showVerseDetail = true }
            .padding(2.dp)
            .zIndex(1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            var fullText = ""
            verseItem.scriptureVerses.forEach { verse ->
                fullText = verse.verseString + " " + fullText
            }

            Text(
                buildAnnotatedString {
                    // Apply default style or theme style implicitly
                    append("")
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.surfaceTint)) {
                        append(verseReference(verseItem))
                    }
                    append(" $fullText")
                },
                modifier = Modifier
                    .fillMaxWidth() // Use fillMaxWidth
                    .padding(1.dp), // Add some padding
                maxLines = 1, // Restrict to a 2 lines
                overflow = TextOverflow.Ellipsis, // Truncate with "..."
                style = MaterialTheme.typography.bodyMedium // Use a default theme typography style
            )

            Spacer(modifier = Modifier.height(1.dp)) // Space between lines

            Text(text = "${verseItem.topics}",
                modifier = Modifier
                    .fillMaxWidth() // Use fillMaxWidth
                    .padding(1.dp), // Add some padding
                maxLines = 1, // Restrict to a 2 lines
                overflow = TextOverflow.Ellipsis, // Truncate with "..."
                style = MaterialTheme.typography.bodyMedium // Use a default theme typography style
            )
        }
    }

    if (showVerseDetail) {
        navController.navigate(Screen.VerseDetailScreen(verseID = verseItem.id, editMode = false))
    }
}