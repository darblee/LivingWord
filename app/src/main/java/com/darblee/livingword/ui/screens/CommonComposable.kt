package com.darblee.livingword.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Reusable composable for a labeled outlined box container.
 * (No changes needed here)
 */
@Composable
fun LabeledOutlinedBox(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium, // Or labelSmall/labelLarge
            color = MaterialTheme.colorScheme.primary, // Or onSurfaceVariant
            modifier = Modifier.padding(bottom = 4.dp), // Space between label and box
            fontSize = 15.sp
        )
        Box(
            modifier = Modifier
                .fillMaxWidth() // Fill width within the Column
                .heightIn(min = 48.dp) // Ensure a minimum height, adjust as needed
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(8.dp) // Consistent corner rounding
                )
                .clip(RoundedCornerShape(8.dp)) // Clip content to rounded shape
                .padding(horizontal = 12.dp, vertical = 8.dp), // Padding inside the box
            contentAlignment = Alignment.TopStart // Align content (e.g., BasicTextField)
        ) {
            content() // Render the content passed into the box
        }
    }
}

@Composable
fun ErrorDialog(
    showDialog: MutableState<Boolean>,
    errorMessage: String
) {
    if (showDialog.value) {
        AlertDialog(
            onDismissRequest = {
                // Optionally, you can allow dismissing by clicking outside
                // showDialog.value = false
            },
            title = {
                Text(text = "Error")
            },
            text = {
                Text(text = errorMessage)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog.value = false
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}