package com.darblee.livingword.ui.viewmodels

import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.darblee.livingword.Global
import com.darblee.livingword.data.AppDatabase
import com.darblee.livingword.data.DatabaseRefreshManager
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
    
    // Signal for database refresh after import
    private val _databaseRefreshed = MutableStateFlow(0L)
    val databaseRefreshed = _databaseRefreshed.asStateFlow()

    fun resetExportState() { _exportState.value = OperationState.NotStarted }
    fun resetImportState() { _importState.value = OperationState.NotStarted }

    fun exportDatabase(context: Context, tokenCredential: GoogleIdTokenCredential) {
        _exportState.value = OperationState.InProgress
        viewModelScope.launch {
            try {
                // Generate a timestamped filename
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.getDefault()).format(Date())
                val exportFileName = "${Global.DATABASE_NAME}-$timestamp.db"

                // CRITICAL FIX: Force a WAL checkpoint BEFORE export to ensure all data 
                // (including new v2 fields like applicationFeedback) is written to the main DB file
                val database = AppDatabase.getDatabase(context)
                
                // First, ensure all Room operations are completed by accessing the database
                database.bibleVerseDao()
                
                // CRITICAL FIX: Force all database operations to complete and checkpoint WAL
                android.util.Log.d("ExportDB", "Starting database export with checkpoint procedures")
                
                // 1. Force Room operations to complete by closing and reopening the database
                try {
                    database.close()
                    android.util.Log.d("ExportDB", "Database closed for checkpoint")
                } catch (e: Exception) {
                    android.util.Log.d("ExportDB", "Database close failed (may be normal): ${e.message}")
                }
                
                // 2. Reopen database to get fresh connection and force WAL checkpoint
                val freshDatabase = AppDatabase.getDatabase(context)
                val db = freshDatabase.openHelper.writableDatabase
                
                // 3. Simple checkpoint that should work - Room handles WAL mode automatically
                try {
                    db.execSQL("PRAGMA wal_checkpoint")
                    android.util.Log.d("ExportDB", "Basic WAL checkpoint executed")
                } catch (e: Exception) {
                    android.util.Log.d("ExportDB", "WAL checkpoint failed: ${e.message}")
                }
                
                // 4. Force a sync to disk
                try {
                    db.execSQL("PRAGMA synchronous = FULL")
                    android.util.Log.d("ExportDB", "Synchronous mode set to FULL")
                } catch (e: Exception) {
                    android.util.Log.d("ExportDB", "Synchronous mode setting failed: ${e.message}")
                }
                
                // 5. Brief pause to ensure all operations complete
                Thread.sleep(200)
                android.util.Log.d("ExportDB", "Database checkpoint procedures completed")

                val credential = getCredential(context, tokenCredential)
                val driveService = GoogleDriveService(credential)
                val dbPath = context.getDatabasePath(Global.DATABASE_NAME).absolutePath

                withContext(Dispatchers.IO) {
                    val folderId = driveService.getOrCreateFolderStructure(Global.BACKUP_FOLDER_PATH)
                    if (folderId == null) {
                        _exportState.value = OperationState.Complete(false, "Failed to create backup folder.")
                        return@withContext
                    }
                    
                    // Debug: Check if database file exists and log its info
                    val dbFile = java.io.File(dbPath)
                    if (!dbFile.exists()) {
                        _exportState.value = OperationState.Complete(false, "Database file not found at: $dbPath")
                        return@withContext
                    }
                    
                    // Log database file details for debugging
                    android.util.Log.d("ExportDB", "Database file size: ${dbFile.length()} bytes")
                    android.util.Log.d("ExportDB", "Database path: $dbPath")
                    
                    // Check for WAL and SHM files
                    val walFile = java.io.File("$dbPath-wal")
                    val shmFile = java.io.File("$dbPath-shm")
                    android.util.Log.d("ExportDB", "WAL file exists: ${walFile.exists()}, size: ${if(walFile.exists()) walFile.length() else 0}")
                    android.util.Log.d("ExportDB", "SHM file exists: ${shmFile.exists()}, size: ${if(shmFile.exists()) shmFile.length() else 0}")
                    
                    // Use the new dynamic filename for the upload
                    driveService.uploadFile(exportFileName, dbPath, folderId)
                }
                _exportState.value = OperationState.Complete(true, "Export Successful.\nCreated file: $exportFileName")

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
                    // CRITICAL FIX: Close the database connection BEFORE overwriting the file
                    try {
                        val database = AppDatabase.getDatabase(context)
                        database.close()
                        android.util.Log.d("ImportDB", "Database closed before import")
                    } catch (e: Exception) {
                        android.util.Log.d("ImportDB", "Database close failed: ${e.message}")
                    }
                    
                    val dbPath = context.getDatabasePath(Global.DATABASE_NAME)
                    android.util.Log.d("ImportDB", "Importing to: $dbPath")
                    
                    val outputStream = FileOutputStream(dbPath)
                    driveService.downloadFile(fileId, outputStream)
                    outputStream.close()
                    
                    android.util.Log.d("ImportDB", "Database file imported successfully")
                    
                    // Force a brief delay to ensure file system operations complete
                    Thread.sleep(500)
                }
                
                // Refresh the database instance to load the imported data
                try {
                    AppDatabase.refreshDatabase(context)
                    android.util.Log.d("ImportDB", "Database refreshed successfully")
                    
                    // Signal all ViewModels to refresh their data
                    DatabaseRefreshManager.triggerRefresh()
                    
                    _importState.value = OperationState.Complete(true, "Import successful! Data has been refreshed.")
                } catch (e: Exception) {
                    android.util.Log.e("ImportDB", "Database refresh failed: ${e.message}")
                    _importState.value = OperationState.Complete(false, "Import completed but refresh failed. Please restart the app.")
                }

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

