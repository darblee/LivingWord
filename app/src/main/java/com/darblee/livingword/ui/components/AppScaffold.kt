package com.darblee.livingword.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert // Icon for dropdown
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.darblee.livingword.BuildConfig
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
    var showAboutDialogBox by remember { mutableStateOf(false) }
    var showSettingDialogBox by remember { mutableStateOf(false) }

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
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Info, contentDescription = "About")
                            },
                            text = { Text("About") }, // Replace with actual action
                            onClick = {
                                // Handle About action
                                showAboutDialogBox = true
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

    if (showAboutDialogBox) {
       AboutDialogPopup(
            onDismissRequest = { showAboutDialogBox = false },
            onConfirmation = { showAboutDialogBox = false },
        )
    }
}

@Composable
private fun AboutDialogPopup(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        // Draw a rectangle shape with rounded corners inside the dialog
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .width(200.dp)
                .padding(0.dp)
                .height(IntrinsicSize.Min)
                .border(0.dp, color = colorScheme.outline, shape = RoundedCornerShape(20.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
            ) {
                Row {
                    Column(
                        Modifier
                            .weight(1f)
                            .align(Alignment.CenterVertically)) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Living Word",
                            contentScale = ContentScale.Fit
                        )
                    }
                    Column(Modifier.weight(3f)) {
                        Text(
                            text = stringResource(id = R.string.version),
                            color = colorScheme.primary,
                            modifier = Modifier
                                .padding(2.dp, 4.dp, 2.dp, 0.dp)
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = BuildConfig.BUILD_TIME,
                            color = colorScheme.primary,
                            modifier = Modifier
                                .padding(4.dp, 0.dp, 4.dp, 2.dp)
                                .align(Alignment.CenterHorizontally)
                                .fillMaxWidth(),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .width(1.dp), color = colorScheme.outline
                )
                Row(Modifier.padding(top = 0.dp)) {
                    TextButton(
                        onClick = { onConfirmation() },
                        Modifier
                            .fillMaxWidth()
                            .padding(0.dp)
                            .weight(1F)
                            .border(0.dp, color = Color.Transparent)
                            .height(48.dp),
                        elevation = ButtonDefaults.elevatedButtonElevation(0.dp, 0.dp),
                        shape = RoundedCornerShape(0.dp),
                        contentPadding = PaddingValues()
                    ) {
                        Text(
                            text = stringResource(id = R.string.OK),
                            color = colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
