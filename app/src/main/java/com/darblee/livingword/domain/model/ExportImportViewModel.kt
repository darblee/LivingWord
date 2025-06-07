package com.darblee.livingword.domain.model

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
                _exportState.value = OperationState.Complete(true, "Backup Successful!")

            } catch (e: UserRecoverableAuthIOException) {
                _exportState.value = OperationState.RequiresPermissions(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _exportState.value = OperationState.Complete(false, "Backup Failed: ${e.message}")
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

    // This new function restores the database file selected by the user
    fun importSelectedDatabase(context: Context, tokenCredential: GoogleIdTokenCredential, fileId: String) {
        _importState.value = OperationState.InProgress
        viewModelScope.launch {
            try {
                val credential = getCredential(context, tokenCredential)
                val driveService = GoogleDriveService(credential)

                withContext(Dispatchers.IO) {
                    val dbPath = context.getDatabasePath(Global.DATABASE_NAME)
                    AppDatabase.getDatabase(context).close() // Close DB before overwriting
                    val outputStream = java.io.FileOutputStream(dbPath)
                    driveService.downloadFile(fileId, outputStream)
                }
                _importState.value = OperationState.Complete(true, "Restore successful! Please restart the app.")

            } catch (e: UserRecoverableAuthIOException) {
                _importState.value = OperationState.RequiresPermissions(e.intent)
            } catch (e: Exception) {
                e.printStackTrace()
                _importState.value = OperationState.Complete(false, "Restore Failed: ${e.message}")
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

