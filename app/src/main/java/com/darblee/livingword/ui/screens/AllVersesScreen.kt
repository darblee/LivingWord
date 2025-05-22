package com.darblee.livingword.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.R
import com.darblee.livingword.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllVersesScreen(navController: NavController, bibleViewModel: BibleVerseViewModel) {

    val allVerses by bibleViewModel.allVerses.collectAsState()

    var currentScreen by remember { mutableStateOf(Screen.MeditateScreen) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Meditate God's Word") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                // Optional: Add colors, navigation icon, actions etc.
            )
        },
        bottomBar = {
            // NavigationBar for switching between screens
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == Screen.Home,
                    onClick = { navController.navigate(Screen.Home) },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.MeditateScreen,
                    onClick = { navController.navigate(Screen.MeditateScreen) },
                    label = { Text("Meditate") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Meditate",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreen == Screen.VerseByTopicScreen,
                    onClick = { navController.navigate(Screen.VerseByTopicScreen) },
                    label = { Text("Verse By Topics") },
                    icon = { Icon(Icons.Filled.Church, contentDescription = "Topic") }
                )
            }
        }
    ) { paddingValues -> // Content lambda receives padding values
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (allVerses.isEmpty()) {
                Text("No verses added yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn {
                    items(allVerses) { verseItem ->
                        VerseCard(verseItem, navController)
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
            Button(onClick = { navController.navigate(Screen.LearnScreen) }) {
                Text("Add new verse...")
            }
        }
    }
}

