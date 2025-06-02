package com.darblee.livingword.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.BackPressHandler
import com.darblee.livingword.Screen
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVersesScreen(
    navController: NavController,
    bibleViewModel: BibleVerseViewModel,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {

    val allVerses by bibleViewModel.allVerses.collectAsState()

    AppScaffold(
        title = { Text("Meditate God's Word") }, // Define the title for this screen
        navController = navController,
        currentScreenInstance = Screen.AllVersesScreen, // Pass the actual Screen instance
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues -> // Content lambda receives padding values
            Column(
                modifier = Modifier
                    .padding(paddingValues) // Apply padding from AppScaffold
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { navController.navigate(Screen.NewVerseScreen) }) {
                    Text("Add new verse...")
                }

                if (allVerses.isEmpty()) {
                    Text("No verses added yet.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn {
                        items(allVerses) { verseItem ->
                            VerseCard(verseItem, navController) // Assuming VerseCard is defined elsewhere
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            }
        }
    )

    // Handle back press to navigate to Home and clear backstack
    BackPressHandler {
        navController.navigate(Screen.Home) {
            /**
             * Clears the entire back stack before navigating to All Verses screen. navController.graph.id
             * refers to the root of your navigation graph.
             */
            popUpTo(navController.graph.id) { // Pop the entire back stack
                inclusive = true
            }
            /**
             * launchSingleTop = true ensures that if HomeScreen is already at the top of the stack
             * (which it won't be in this specific scenario after popping everything, but it's good
             * practice for navigations to Home), a new instance isn't created.
             */
            launchSingleTop = true // Avoid multiple instances of Home Screen
        }
    }
}

