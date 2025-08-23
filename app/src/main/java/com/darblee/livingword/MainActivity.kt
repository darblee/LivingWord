package com.darblee.livingword

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.data.remote.AIService
import com.darblee.livingword.ui.viewmodels.BibleVerseViewModel
import com.darblee.livingword.ui.components.AIDisclaimerDialog
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.darblee.livingword.ui.theme.SetColorTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, user can start transcription
        }
    }

    private lateinit var preferenceStore: PreferenceStore // Declare here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceStore = PreferenceStore(applicationContext) // Initialize here

        // Check for microphone permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        // Initialize BibleData with application context
        BibleData.init(applicationContext)

        // Configure AIService (Gemini + OpenAI fallback) on startup
        lifecycleScope.launch {
            val aiSettings = preferenceStore.readAISettings()
            AIService.configure(aiSettings)
        }


        enableEdgeToEdge()
        setContent {
            ForcePortraitMode()

            var colorTheme by remember {
                mutableStateOf(ColorThemeOption.System)
            }

            var showAIDisclaimer by remember { mutableStateOf(false) }

            // Load color theme and AI disclaimer status from preferences
            LaunchedEffect(Unit) {
                colorTheme = preferenceStore.readColorModeFromSetting()
                val disclaimerShown = preferenceStore.readAIDisclaimerShown()
                showAIDisclaimer = !disclaimerShown
            }

            SetColorTheme(colorTheme) {
                var currentSnackBarEvent by remember { mutableStateOf<SnackBarEvent?>(null) }

                // Observe SnackBar events
                ObserveAsEvents(
                    flow = SnackBarController.events
                ) { event ->
                    currentSnackBarEvent = event
                }

                MainViewImplementation(
                    currentTheme = colorTheme,
                    onColorThemeUpdated = { newColorThemeSetting ->
                        colorTheme = newColorThemeSetting
                        // No need to save here, AppScaffold's SettingPopup handles it
                    },
                )

                CustomSnackBarHost(
                    snackBarEvent = currentSnackBarEvent,
                    onDismiss = { currentSnackBarEvent = null }
                )

                // Show AI Disclaimer Dialog
                if (showAIDisclaimer) {
                    AIDisclaimerDialog(
                        onDismiss = { doNotShowAgain ->
                            showAIDisclaimer = false
                            if (doNotShowAgain) {
                                lifecycleScope.launch {
                                    preferenceStore.saveAIDisclaimerShown(true)
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun MainViewImplementation(
        onColorThemeUpdated: (colorThemeSetting: ColorThemeOption) -> Unit,
        currentTheme: ColorThemeOption,
    ) {
        val navController = rememberNavController()

        Scaffold(
            modifier = Modifier.fillMaxSize()
        ) { innerPadding -> // innerPadding is now handled by AppScaffold

            val bibleViewModel: BibleVerseViewModel =
                viewModel(factory = BibleVerseViewModel.Factory(applicationContext))

            // Pass preferenceStore to SetUpNavGraph if needed, or ensure AppScaffold has access
            SetUpNavGraph(
                bibleViewModel = bibleViewModel,
                navController = navController,
                onColorThemeUpdated = onColorThemeUpdated,
                currentTheme = currentTheme,
                paddingValues = innerPadding
            )
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    fun ForcePortraitMode() {
        LocalActivity.current?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}