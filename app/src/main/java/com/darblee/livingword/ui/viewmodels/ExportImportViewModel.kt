package com.darblee.livingword.ui.viewmodels

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.Global
import com.darblee.livingword.data.AppDatabase
import com.darblee.livingword.data.remote.GoogleDriveService
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.DriveScopes
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class to hold simplified file information for the UI
data class DriveFile(val id: String, val name: String, val modifiedTime: String)

// Sealed class to represent the state of an operation
sealed class OperationState {
    object NotStarted : OperationState()
    object InProgress : OperationState()
    data class RequiresPermissions(val intent: Intent) : OperationState()
    // New state to prompt user for file selection
    data class ImportFileSelection(val files: List<DriveFile>) : OperationState()
    data class Complete(val success: Boolean, val message: String) : OperationState()
}

class ExportImportViewModel : ViewModel() {

    private val _exportState = MutableStateFlow<OperationState>(OperationState.NotStarted)
    val exportState = _exportState.asStateFlow()

    private val _importState = MutableStateFlow<OperationState>(OperationState.NotStarted)
    val importState = _importState.asStateFlow()

    fun resetExportState() { _exportState.value = OperationState.NotStarted }
    fun resetImportState() { _importState.value = OperationState.NotStarted }

    fun exportDatabase(context: Context, tokenCredential: GoogleIdTokenCredential) {
        _exportState.value = OperationState.InProgress
        viewModelScope.launch {
            try {
                // Generate a timestamped filename
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
                val exportFileName = "${Global.DATABASE_NAME}-$timestamp.db"

                val credential = getCredential(context, tokenCredential)
                val driveService = GoogleDriveService(credential)
                val dbPath = context.getDatabasePath(Global.DATABASE_NAME).absolutePath

                withContext(Dispatchers.IO) {
                    val folderId = driveService.getOrCreateFolderStructure(Global.BACKUP_FOLDER_PATH)
                    if (folderId == null) {
                        _exportState.value = OperationState.Complete(false, "Failed to create backup folder.")
                        return@withContext
                    }
                    // Use the new dynamic filename for the upload
                    driveService.uploadFile(exportFileName, dbPath, folderId)
                }
                _exportState.value = OperationState.Complete(true, "Export Successful.\nCreated file: $exportFileName")

                // Force a WAL checkpoint to ensure all data is written to the main DB file.
                // This is the correct way to sync the database without closing the connection.
                AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);")

            } catch (e: UserRecoverableAuthIOException) {
                _exportState.value = OperationState.RequiresPermissions(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _exportState.value = OperationState.Complete(false, "Export Failed: ${e.message}")
            }
        }
    }

    // This function now initiates the restore process by listing available backups
    fun listAvailableBackups(context: Context, tokenCredential: GoogleIdTokenCredential) {
        _importState.value = OperationState.InProgress
        viewModelScope.launch {
            try {
                val credential = getCredential(context, tokenCredential)
                val driveService = GoogleDriveService(credential)

                val folderId = withContext(Dispatchers.IO) {
                    driveService.findFolderIdByPath(Global.BACKUP_FOLDER_PATH)
                }
                if (folderId == null) {
                    _importState.value = OperationState.Complete(false, "Backup folder not found.")
                    return@launch
                }

                val driveFiles = withContext(Dispatchers.IO) {
                    driveService.listFilesInFolder(folderId)
                }
                if (driveFiles.isEmpty()) {
                    _importState.value = OperationState.Complete(false, "No backup files found.")
                    return@launch
                }

                val backupFiles = driveFiles.map { file ->
                    val formattedDate = file.modifiedTime?.let {
                        val date = Date(it.value)
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(date)
                    } ?: "Unknown date"
                    DriveFile(file.id, file.name, formattedDate)
                }
                _importState.value = OperationState.ImportFileSelection(backupFiles)

            } catch (e: UserRecoverableAuthIOException) {
                _importState.value = OperationState.RequiresPermissions(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = OperationState.Complete(false, "Failed to list backups: ${e.message}")
            }
        }
    }

    fun importSelectedDatabase(context: Context, tokenCredential: GoogleIdTokenCredential, fileId: String) {
        _importState.value = OperationState.InProgress
        viewModelScope.launch {
            try {
                val credential = getCredential(context, tokenCredential)
                val driveService = GoogleDriveService(credential)

                withContext(Dispatchers.IO) {
                    val dbPath = context.getDatabasePath(Global.DATABASE_NAME)
                    val outputStream = FileOutputStream(dbPath)
                    driveService.downloadFile(fileId, outputStream)
                }
                // Use a specific message to signal the UI to show the exit dialog
                _importState.value = OperationState.Complete(true, "IMPORT_SUCCESS_RESTART_REQUIRED")

                // Force a WAL checkpoint to ensure the new data is loaded correctly.
                AppDatabase.getDatabase(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);")

            } catch (e: UserRecoverableAuthIOException) {
                _importState.value = OperationState.RequiresPermissions(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = OperationState.Complete(false, "Import Failed: ${e.message}")
            }
        }
    }

    private fun getCredential(context: Context, tokenCredential: GoogleIdTokenCredential): GoogleAccountCredential {
        return GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        ).setSelectedAccount(Account(tokenCredential.id, "com.google"))
    }
}

