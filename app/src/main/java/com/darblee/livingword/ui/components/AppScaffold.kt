package com.darblee.livingword.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert // Icon for dropdown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.darblee.livingword.R
import com.darblee.livingword.Screen // Your sealed class for routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: @Composable () -> Unit,
    navController: NavController,
    currentScreenInstance: Screen, // The current screen route/object
    content: @Composable (PaddingValues) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = title,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ), //
                actions = {
                    // This is where you define your "same icon, same dropdown menu"
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "More options"
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Add your consistent DropdownMenuItems here
                        // Example:
                        DropdownMenuItem(
                            text = { Text("Settings") }, // Replace with actual action
                            onClick = {
                                // Handle Settings action
                                // e.g., navController.navigate("settings_route")
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("About") }, // Replace with actual action
                            onClick = {
                                // Handle About action
                                showMenu = false
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreenInstance is Screen.Home,
                    onClick = {
                        if (currentScreenInstance !is Screen.Home) {
                            navController.navigate(Screen.Home) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") } //
                )
                NavigationBarItem(
                    selected = currentScreenInstance is Screen.AllVersesScreen,
                    onClick = {
                        if (currentScreenInstance !is Screen.AllVersesScreen) {
                            navController.navigate(Screen.AllVersesScreen) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    label = { Text("All Verses") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom), //
                            contentDescription = "Review all verses",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreenInstance is Screen.VerseByTopicScreen,
                    onClick = {
                        if (currentScreenInstance !is Screen.VerseByTopicScreen) {
                            navController.navigate(Screen.VerseByTopicScreen) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    label = { Text("Verse By Topics") },
                    icon = { Icon(Icons.Filled.Church, contentDescription = "Topic") } //
                )
            }
        }
    ) { innerPadding ->
        content(innerPadding)
    }
}