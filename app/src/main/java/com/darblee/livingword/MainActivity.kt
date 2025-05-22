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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.darblee.livingword.data.BibleData
import com.darblee.livingword.domain.model.BibleVerseViewModel
import com.darblee.livingword.ui.theme.LivingWordTheme

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, user can start transcription
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        enableEdgeToEdge()
        setContent {
            ForcePortraitMode()

            LivingWordTheme {
                var currentSnackBarEvent by remember { mutableStateOf<SnackBarEvent?>(null) }

                // Observe SnackBar events
                ObserveAsEvents(
                    flow = SnackBarController.events
                ) { event ->
                    // When a new event comes in, update our state
                    currentSnackBarEvent = event
                }

                // Our custom snackbar will be positioned over the scaffold content
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize()
                    ) { innerPadding ->
                        val navController = rememberNavController()

                        val bibleViewModel: BibleVerseViewModel =
                            viewModel(factory = BibleVerseViewModel.Factory(applicationContext))

                        SetUpNavGraph(
                            modifier = Modifier.padding(innerPadding),
                            bibleViewModel = bibleViewModel,
                            navController = navController
                        )
                    }

                    // Our custom snackBar host that will show messages
                    CustomSnackBarHost(
                        snackBarEvent = currentSnackBarEvent,
                        onDismiss = { currentSnackBarEvent = null }
                    )
                }
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    fun ForcePortraitMode() {
        LocalActivity.current?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}