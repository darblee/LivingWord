package com.darblee.livingword.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.navigation.NavController
import com.darblee.livingword.Global
import com.darblee.livingword.Screen
import com.darblee.livingword.domain.model.DriveFile
import com.darblee.livingword.domain.model.ExportImportViewModel
import com.darblee.livingword.domain.model.OperationState
import com.darblee.livingword.ui.components.AppScaffold
import com.darblee.livingword.ui.theme.ColorThemeOption
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog

private val ExportImportViewModel = ExportImportViewModel()

@Composable
fun GoogleDriveOpsScreen(
    navController: NavController,
    onColorThemeUpdated: (ColorThemeOption) -> Unit,
    currentTheme: ColorThemeOption
) {
    var signedInCredential by remember { mutableStateOf<GoogleIdTokenCredential?>(null) }
    val SERVER_CLIENT_ID = "71543984425-75k2ckdalftk0scd58j3r8nit863kjc3.apps.googleusercontent.com"


    var userName by rememberSaveable { mutableStateOf<String?>(null) }
    var googleAccountID by rememberSaveable { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Observe the states from the ExportImportViewModel
    val exportState by ExportImportViewModel.exportState.collectAsState()
    val importState by ExportImportViewModel.importState.collectAsState()

    // State for managing the import dialog
    var showImportDialog by remember { mutableStateOf(false) }
    var availableImports by remember { mutableStateOf<List<DriveFile>>(emptyList()) }

    // State for managing the exit dialog
    var showExitDialog by remember { mutableStateOf(false) }

    /**
     * The following is used to start an activity (e.g. Google sign-in permission) and process the outcome when
     * the user returns to your app.
     */
    val authorizationLauncher = rememberLauncherForActivityResult(
        /**
         * his specifies what kind of action will be performed. StartActivityForResult is a generic contract.
         * It means the launcher will be used to start a new screen (an Activity), and it expects to receive a
         * result back once that screen is closed. In this app, it's used to launch the Google sign-in or
         * permission screen.
         */
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "Permission granted! Please try again.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Permission denied.", Toast.LENGTH_SHORT).show()
        }

        /**
         * Regardless of whether the permission was granted or denied, these lines reset the state in the ViewModel.
         * This is important to ensure the UI returns to a neutral state, allowing the user to try the export or
         * import action again without the app being stuck in a "waiting for permission" state.
         */
        ExportImportViewModel.resetExportState()
        ExportImportViewModel.resetImportState()
    }

    // Effect to handle state changes that require action
    LaunchedEffect(exportState) {
        handleOperationState(exportState, context,
            launchIntent = { intent -> authorizationLauncher.launch(intent) },
            onComplete = { ExportImportViewModel.resetExportState() }
        )
    }

    // Effect to handle imports state changes
    LaunchedEffect(importState) {
        when (val state = importState) {
            is OperationState.RequiresPermissions -> {
                authorizationLauncher.launch(state.intent)
            }
            is OperationState.Complete -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                showImportDialog = false // Hide dialog on completion
                ExportImportViewModel.resetImportState()
            }
            is OperationState.ImportFileSelection -> {
                availableImports = state.files
                showImportDialog = true
                ExportImportViewModel.resetImportState() // Reset state so dialog doesn't reopen
            }
            else -> {} // Do nothing for NotStarted or InProgress
        }
    }


    LaunchedEffect(importState) {
        when (val state = importState) {
            is OperationState.RequiresPermissions -> {
                authorizationLauncher.launch(state.intent)
            }
            is OperationState.Complete -> {
                showImportDialog = false // Hide import selection dialog on completion
                if (state.message == "IMPORT_SUCCESS_RESTART_REQUIRED") {
                    showExitDialog = true
                } else {
                    // Show a toast for any other completion messages (like errors)
                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                }
                ExportImportViewModel.resetImportState()
            }
            is OperationState.ImportFileSelection -> {
                availableImports = state.files
                showImportDialog = true
                ExportImportViewModel.resetImportState() // Reset state so dialog doesn't reopen
            }
            else -> {} // Do nothing for NotStarted or InProgress
        }
    }

    // Show the dialog when `showImportDialog` is true
    if (showImportDialog) {
        ImportSelectionDialog(
            files = availableImports,
            onFileSelected = { fileId ->
                showImportDialog = false
                signedInCredential?.let { credential ->
                    ExportImportViewModel.importSelectedDatabase(context, credential, fileId)
                }
            },
            onDismissRequest = {
                showImportDialog = false
            }
        )
    }

    // Show the exit dialog when `showExitDialog` is true
    if (showExitDialog) {
        ExitAppDialog(
            onDismissRequest = {
                showExitDialog = false
                // फिनिश एक्टिविटी ऑन डिसमिस
                (context as? Activity)?.finish()
            }
        )
    }

    AppScaffold(
        title = { Text("Google Drive Export / Import" ) },
        navController = navController,
        currentScreenInstance = Screen.GoogleDriveOpsScreen, // Pass the actual Screen instance
        onColorThemeUpdated = onColorThemeUpdated,
        currentTheme = currentTheme,
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (signedInCredential == null) {
                    Button(
                        onClick = {
                            scope.launch {
                                val credential = signIn(context, SERVER_CLIENT_ID)
                                if (credential != null) {
                                    googleAccountID = credential.id
                                    userName = credential.displayName.toString()
                                    signedInCredential = credential
                                    Toast.makeText(context, "Sign-in successful: ${credential.displayName}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Sign-in failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Sign In"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In")
                    }
                } else {

                    Button(
                        onClick = {
                            scope.launch {
                                signOut(context)
                                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()
                                signedInCredential = null
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Sign Out"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")

                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    Text("Signed In: $googleAccountID ($userName)")
                }

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(20.dp))

                // Informational text about the export/import process
                Text(
                    style = MaterialTheme.typography.bodySmall,
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("EXPORT")
                        }
                        append(": A new file \"${Global.DATABASE_NAME}-<time-stamp>\" will be created in your Google Drive located at \"${Global.BACKUP_FOLDER_PATH}\" directory.\n\n")

                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("IMPORT")
                        }
                        append(": You can import any compatible database files from same Google Drive folder location.\n\n")

                        append("This is useful for sharing your content or creating a backup.")
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Show progress indicator if an operation is in progress, otherwise show buttons
                if (exportState is OperationState.InProgress || importState is OperationState.InProgress) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else {
                    // Row to hold the large square buttons for Export and Import
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export Button
                        Button(
                            onClick = {
                                signedInCredential?.let { credential ->
                                    ExportImportViewModel.exportDatabase(context, credential)
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.size(140.dp),
                            enabled = ((exportState is OperationState.NotStarted) && (signedInCredential != null)),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudUpload,
                                    contentDescription = "EXPORT",
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("EXPORT")
                            }
                        }

                        // Import Button
                        Button(
                            onClick = {
                                signedInCredential?.let { credential ->
                                    ExportImportViewModel.listAvailableBackups(context, credential)
                                }
                            },
                            modifier = Modifier.size(140.dp),
                            shape = RoundedCornerShape(16.dp),
                            enabled = ((importState is OperationState.NotStarted) && (signedInCredential != null)),
                        )
                        {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = "IMPORT",
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("IMPORT")
                            }
                        }
                    }
                }
            }
        }
    )


}

/***
 * The primary purpose of this function is to act as a centralized, reusable handler
 * that responds to changes in the state of a long-running operation, such as a file backup. It is designed to be
 * triggered when the operation's state changes and to perform specific actions based on that new state.
 *
 * @param state The current state of the operation (e.g., requires permission, is complete).
 * @param context The Android context, needed to show messages (Toasts).
 * @param launchIntent A function that is passed in to launch a new screen (Activity). In this code, it's
 * used to trigger the authorizationLauncher.
 * @param onComplete A callback function that is executed when the operation is complete, used for cleanup
 * tasks like resetting the state.
 */
fun handleOperationState(state: OperationState, context: Context, launchIntent: (Intent) -> Unit, onComplete: () -> Unit) {
    when (state) {
        is OperationState.RequiresPermissions -> {
            launchIntent(state.intent)
        }
        is OperationState.Complete -> {
            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            onComplete()
        }
        else -> {} // Do nothing for other states as they are handled elsewhere
    }
}

@Composable
fun ImportSelectionDialog(
    files: List<DriveFile>,
    onFileSelected: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp), // Set a max height for the dialog
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "Select a database to import",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) { // Make the list scrollable
                    items(files) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFileSelected(file.id) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Import",
                                modifier = Modifier.padding(end = 16.dp)
                            )
                            Column {
                                Text(file.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    file.modifiedTime,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(horizontal = 16.dp)
                ) {
                    Text("CANCEL")
                }
            }
        }
    }
}

suspend fun signIn(context: Context, serverClientId: String): GoogleIdTokenCredential? {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(serverClientId)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
    return try {
        val result = credentialManager.getCredential(context, request)
        GoogleIdTokenCredential.createFrom(result.credential.data)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun signOut(context: Context) {
    val credentialManager = CredentialManager.create(context)
    credentialManager.clearCredentialState(ClearCredentialStateRequest())
}

/**
 * A dialog to inform the user that the import was successful and the app needs to be restarted.
 * Provides an "EXIT" button to close the application.
 */
@Composable
fun ExitAppDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(all = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Import Successful",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "The app must now be restarted for the changes to take effect.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = {
                        // Close the activity, which effectively exits the app
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Exit")
                }
            }
        }
    }
}