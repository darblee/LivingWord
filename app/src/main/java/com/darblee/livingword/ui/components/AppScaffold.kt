package com.darblee.livingword.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.darblee.livingword.AIServiceType
import com.darblee.livingword.AISettings
import com.darblee.livingword.BuildConfig
import com.darblee.livingword.Global
import com.darblee.livingword.PreferenceStore
import com.darblee.livingword.R
import com.darblee.livingword.Screen // Your sealed class for routes
import com.darblee.livingword.click
import com.darblee.livingword.data.remote.AIServiceRegistry
import com.darblee.livingword.data.remote.AiServiceResult
import com.darblee.livingword.data.remote.GeminiAIService
import com.darblee.livingword.data.remote.AIService
import com.darblee.livingword.ui.theme.ColorThemeOption
import kotlinx.coroutines.launch



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: @Composable () -> Unit,
    navController: NavController,
    currentScreenInstance: Screen, // The current screen route/object
    content: @Composable (PaddingValues) -> Unit,
    onColorThemeUpdated: (colorThemeSetting: ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialogBox by remember { mutableStateOf(false) }
    var showSettingDialogBox by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val preferenceStore = remember { PreferenceStore(context.applicationContext) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { title() },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = colorScheme.primaryContainer
                ),
                navigationIcon = {
                    val showBackButton = currentScreenInstance is Screen.VerseDetailScreen ||
                            currentScreenInstance is Screen.EngageScreen ||
                            currentScreenInstance is Screen.NewVerseScreen ||
                            currentScreenInstance is Screen.AddVerseByDescriptionScreen

                    if (showBackButton) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
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
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            text = { Text("Settings") },
                            onClick = {
                                showSettingDialogBox = true
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Cloud,
                                    contentDescription = "Google Drive backup/restore"
                                )
                            },
                            text = { Text("Google Drive") },
                            onClick = {
                                navController.navigate(route = Screen.GoogleDriveOpsScreen)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(imageVector = Icons.Filled.Info, contentDescription = "About")
                            },
                            text = { Text("About") },
                            onClick = {
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
                            navController.navigate(route = Screen.Home) {
                                popUpTo(navController.graph.startDestinationId) { // Or your specific graph's route name if not nested
                                    saveState = true // Optional if you don't need to save its state
                                }
                                launchSingleTop = true // Avoid multiple instances of Home Screen
                            }
                        }
                    },
                    label = { Text("Home") },
                    icon = { Icon(imageVector = Icons.Filled.Home, contentDescription = "Home") }
                )
                NavigationBarItem(
                    selected = currentScreenInstance is Screen.AllVersesScreen,
                    onClick = {
                        if (currentScreenInstance !is Screen.AllVersesScreen) {
                            navController.navigate(route = Screen.AllVersesScreen) {
                                popUpTo(navController.graph.findStartDestination().id) { // Or your specific graph's route name if not nested
                                    saveState = true // Optional if you don't need to save its state
                                }
                                launchSingleTop = true // Avoid multiple instances of Home Screen
                            }
                        }
                    },
                    label = { Text("Verses") },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_meditate_custom),
                            contentDescription = "Review all verses",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                )
                NavigationBarItem(
                    selected = currentScreenInstance is Screen.TopicScreen,
                    onClick = {
                        if (currentScreenInstance !is Screen.TopicScreen) {
                            navController.navigate(route = Screen.TopicScreen) {
                                popUpTo(navController.graph.findStartDestination().id) { // Or your specific graph's route name if not nested
                                    saveState = true // Optional if you don't need to save its state
                                }
                                launchSingleTop = true // Avoid multiple instances of Home Screen
                            }
                        }
                    },
                    label = { Text("Topics") },
                    icon = { Icon(Icons.Filled.Bookmarks, contentDescription = "Topic") }
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

    if (showSettingDialogBox) {
        SettingPopup(
            preferenceStore = preferenceStore,
            onDismissRequest = { showSettingDialogBox = false },
            onConfirmation = { newSettings ->
                // This lambda is called when settings are confirmed and saved
                GeminiAIService.configure(newSettings) // Reconfigure the service
                showSettingDialogBox = false
            },
            onColorThemeUpdated = onColorThemeUpdated,
            currentTheme = currentTheme,
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
            dismissOnClickOutside = false // Changed to false to require explicit dismissal
        )
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .width(300.dp) // Adjusted width for better content fit
                .padding(0.dp)
                .wrapContentHeight() // Use wrapContentHeight
                .border(0.dp, color = colorScheme.outline, shape = RoundedCornerShape(20.dp)),
            elevation = CardDefaults.cardElevation(defaultElevation = 5.dp)
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally // Center content
            ) {
                Row(
                    modifier = Modifier.padding(16.dp), // Add padding
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_meditate_custom),
                        contentDescription = "Living Word Logo",
                        modifier = Modifier.size(60.dp) // Adjust size
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = stringResource(id = R.string.app_name), // Use app_name string
                            style = MaterialTheme.typography.headlineSmall, // Use a larger style
                            color = colorScheme.primary
                        )
                        Text(
                            text = "${stringResource(id = R.string.version_prefix)} ${BuildConfig.VERSION_NAME}", // Use string resource
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = BuildConfig.BUILD_TIME,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.outlineVariant // Use a slightly different color for divider
                )
                TextButton(
                    onClick = { onConfirmation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingPopup(
    preferenceStore: PreferenceStore,
    onDismissRequest: () -> Unit,
    onConfirmation: (AISettings) -> Unit, // Callback with new settings
    onColorThemeUpdated: (colorThemeType: ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
) {
    var currentAISettings by remember { mutableStateOf<AISettings?>(null) }
    var translation by remember { mutableStateOf(PreferenceStore.DEFAULT_TRANSLATION) }
    val scope = rememberCoroutineScope()

    // Load initial AI settings
    LaunchedEffect(key1 = Unit) {
        currentAISettings = preferenceStore.readAISettings()
        translation = preferenceStore.readTranslationFromSetting()
    }

    Dialog(
        onDismissRequest = { onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false // Require explicit action
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(350.dp) // Keep width
                .wrapContentHeight() // Allow height to adjust
                .border(
                    0.dp, // No border needed if card has elevation
                    color = colorScheme.outline,
                    shape = RoundedCornerShape(16.dp)
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp) // Add elevation
        ) {
            // Only show content when settings are loaded
            currentAISettings?.let { loadedSettings ->
                var aiSettings by remember { mutableStateOf(loadedSettings) }

                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()), // Make settings scrollable
                    verticalArrangement = Arrangement.spacedBy(16.dp), // Increase spacing
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("App Settings", style = MaterialTheme.typography.titleLarge) // Title for the dialog

                    ColorThemeSetting(onColorThemeUpdated, currentTheme, preferenceStore)

                    HorizontalDivider()

                    TranslationSetting(
                        currentTranslation = translation,
                        onTranslationChange = { translation = it },
                        preferenceStore = preferenceStore
                    )

                    HorizontalDivider()

                    AIModelSetting(
                        aiSettings = aiSettings,
                        onAISettingsChange = { aiSettings = it }
                    )

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(), // Fill width for button arrangement
                        horizontalArrangement = Arrangement.End // Align buttons to the end
                    ) {
                        TextButton(onClick = { onDismissRequest() }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    preferenceStore.saveAISettings(aiSettings)
                                    onConfirmation(aiSettings) // Pass new settings back
                                }
                            },
                            enabled = when (aiSettings.selectedService) {
                                AIServiceType.GEMINI -> aiSettings.geminiConfig.apiKey.isNotBlank() && aiSettings.geminiConfig.modelName.isNotBlank()
                                AIServiceType.OPENAI -> aiSettings.openAiConfig.apiKey.isNotBlank() && aiSettings.openAiConfig.modelName.isNotBlank()
                            }
                        ) {
                            Text(stringResource(id = R.string.OK))
                        }
                    }
                }
            } ?: run {
                // Show a loading indicator while settings are being loaded
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun ColorThemeSetting(
    onColorThemeUpdated: (colorThemeType: ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption,
    preferenceStore: PreferenceStore // Pass PreferenceStore
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) { // Use Column for better structure
        Text(
            text = "Color Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .border(1.dp, colorScheme.outlineVariant, shape = RoundedCornerShape(8.dp)) // Softer border
                .fillMaxWidth()
                .padding(8.dp), // Add padding inside the border
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val colorThemeOptionsStringValues = listOf(
                ColorThemeOption.System.toString(),
                ColorThemeOption.Light.toString(),
                ColorThemeOption.Dark.toString()
            )

            val (selectedOption, onOptionSelected) = remember(currentTheme) { // React to currentTheme changes
                mutableStateOf(currentTheme.toString())
            }

            // Radio buttons on the right
            Column(Modifier.selectableGroup()) {
                colorThemeOptionsStringValues.forEach { curColorString ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (curColorString == selectedOption),
                                onClick = {
                                    view.click()
                                    onOptionSelected(curColorString)
                                    val newSelectedTheme =
                                        when (curColorString) {
                                            ColorThemeOption.System.toString() -> ColorThemeOption.System
                                            ColorThemeOption.Light.toString() -> ColorThemeOption.Light
                                            else -> ColorThemeOption.Dark
                                        }
                                    onColorThemeUpdated(newSelectedTheme)
                                    scope.launch {
                                        // Use the passed preferenceStore instance
                                        preferenceStore.saveColorModeToSetting(newSelectedTheme)
                                    }
                                },
                                role = androidx.compose.ui.semantics.Role.RadioButton // For accessibility
                            )
                            .padding(vertical = 4.dp), // Adjust padding
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = (curColorString == selectedOption),
                            onClick = null
                        )
                        Text(
                            text = curColorString,
                            style = MaterialTheme.typography.bodyLarge, // Slightly larger text
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationSetting(
    currentTranslation: String,
    onTranslationChange: (String) -> Unit,
    preferenceStore: PreferenceStore
) {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Current Translation",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = currentTranslation,
                onValueChange = {},
                readOnly = true,
                label = { Text("Translation") },
                trailingIcon = {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 500), label = ""
                    )
                    Icon(Icons.Filled.ArrowDropDown, "Dropdown Arrow", Modifier.rotate(rotation))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.animateContentSize()
            ) {
                Global.bibleTranslations.forEach { translation ->
                    DropdownMenuItem(
                        text = { Text(translation) },
                        onClick = {
                            onTranslationChange(translation)
                            expanded = false
                            scope.launch {
                                preferenceStore.saveTranslationToSetting(translation)
                            }
                        }
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AIModelSetting(
    aiSettings: AISettings,
    onAISettingsChange: (AISettings) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AI Assistant Configuration",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // AI Service Selection Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = aiSettings.selectedService.displayName,
                onValueChange = {},
                readOnly = true,
                label = { Text("AI Service") },
                trailingIcon = {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        animationSpec = tween(durationMillis = 500), label = ""
                    )
                    Icon(Icons.Filled.ArrowDropDown, "Dropdown Arrow", Modifier.rotate(rotation))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.animateContentSize()
            ) {
                // Dynamically build dropdown from registered providers
                val availableProviders = remember { 
                    // Ensure registry is initialized
                    AIServiceRegistry.initialize()
                    AIServiceRegistry.getAllProviders() 
                }
                availableProviders.forEach { provider ->
                    DropdownMenuItem(
                        text = { 
                            Row {
                                Text(provider.displayName)
                                if (!provider.isInitialized()) {
                                    Text(
                                        " (Not Available)", 
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        },
                        onClick = {
                            onAISettingsChange(aiSettings.copy(selectedService = provider.serviceType))
                            testResult = null
                            testSuccess = null
                            expanded = false
                        }
                    )
                }
            }
        }

        // Current Service Configuration
        val currentConfig = when (aiSettings.selectedService) {
            AIServiceType.GEMINI -> aiSettings.geminiConfig
            AIServiceType.OPENAI -> aiSettings.openAiConfig
        }

        Text(
            text = "${aiSettings.selectedService.displayName} Settings",
            style = MaterialTheme.typography.titleSmall,
            color = colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp)
        )

        // Model Name Field
        OutlinedTextField(
            value = currentConfig.modelName,
            onValueChange = { newModelName ->
                val newConfig = currentConfig.copy(modelName = newModelName)
                val updatedSettings = when (aiSettings.selectedService) {
                    AIServiceType.GEMINI -> aiSettings.copy(geminiConfig = newConfig)
                    AIServiceType.OPENAI -> aiSettings.copy(openAiConfig = newConfig)
                }
                onAISettingsChange(updatedSettings)
            },
            label = { Text("Model Name") },
            placeholder = { 
                val defaultModel = remember(aiSettings.selectedService) {
                    AIServiceRegistry.getProvidersByType(aiSettings.selectedService)
                        .firstOrNull()?.defaultModel ?: aiSettings.selectedService.defaultModel
                }
                Text(defaultModel) 
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // API Key Field
        OutlinedTextField(
            value = currentConfig.apiKey,
            onValueChange = { newApiKey ->
                val newConfig = currentConfig.copy(apiKey = newApiKey)
                val updatedSettings = when (aiSettings.selectedService) {
                    AIServiceType.GEMINI -> aiSettings.copy(geminiConfig = newConfig)
                    AIServiceType.OPENAI -> aiSettings.copy(openAiConfig = newConfig)
                }
                onAISettingsChange(updatedSettings)
            },
            label = { Text("API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                val image = if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility
                val description = if (apiKeyVisible) "Hide API Key" else "Show API Key"
                IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                    Icon(imageVector = image, description)
                }
            }
        )
        
        if (currentConfig.apiKey.isBlank()) {
            Text(
                "API Key is required for AI features.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.error,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // Temperature Field
        OutlinedTextField(
            value = currentConfig.temperature.toString(),
            onValueChange = { tempInput ->
                val newTemp = tempInput.toFloatOrNull()?.coerceIn(0f, 1f) ?: currentConfig.temperature
                val newConfig = currentConfig.copy(temperature = newTemp)
                val updatedSettings = when (aiSettings.selectedService) {
                    AIServiceType.GEMINI -> aiSettings.copy(geminiConfig = newConfig)
                    AIServiceType.OPENAI -> aiSettings.copy(openAiConfig = newConfig)
                }
                onAISettingsChange(updatedSettings)
            },
            label = { Text("Temperature (0.0 - 1.0)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        // Test Configuration Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isTestingConnection = true
                        testResult = null
                        testSuccess = null

                        try {
                            // Configure the service with current settings
                            AIService.configure(aiSettings)

                            // Test the connection with a simple prompt
                            val result = AIService.getKeyTakeaway("John 3:16")

                            when (result) {
                                is AiServiceResult.Success -> {
                                    testSuccess = true
                                    testResult = "Connection successful! ${aiSettings.selectedService.displayName} is working properly."
                                }
                                is AiServiceResult.Error -> {
                                    testSuccess = false
                                    testResult = "Connection failed: ${result.message}"
                                }
                            }
                        } catch (e: Exception) {
                            testSuccess = false
                            testResult = "Test failed: ${e.message}"
                        } finally {
                            isTestingConnection = false
                        }
                    }
                },
                enabled = !isTestingConnection && currentConfig.apiKey.isNotBlank() && currentConfig.modelName.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                if (isTestingConnection) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.onPrimary
                        )
                        Text("Testing...")
                    }
                } else {
                    Text("Test Configuration")
                }
            }
        }

        // Display test result
        testResult?.let { result ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (testSuccess == true) {
                        colorScheme.primaryContainer
                    } else {
                        colorScheme.errorContainer
                    }
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (testSuccess == true) Icons.Filled.CheckCircle else Icons.Filled.Error,
                        contentDescription = if (testSuccess == true) "Success" else "Error",
                        tint = if (testSuccess == true) {
                            colorScheme.primary
                        } else {
                            colorScheme.error
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (testSuccess == true) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onErrorContainer
                        }
                    )
                }
            }
        }
    }
}