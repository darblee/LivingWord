package com.darblee.livingword.ui.screens

import android.widget.Toast
import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

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

    val activity = LocalActivity.current
    var backPressedTime by remember { mutableStateOf(0L) }
    BackPressHandler {
        if (System.currentTimeMillis() - backPressedTime < 2000) {
            activity?.finish()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
}

