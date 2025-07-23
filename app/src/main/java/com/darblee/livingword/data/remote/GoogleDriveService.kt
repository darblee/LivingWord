package com.darblee.livingword.data.remote

import com.darblee.livingword.R
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import java.util.Collections

class GoogleDriveService(credential: GoogleAccountCredential) {
    private val drive: Drive = Drive.Builder(
        com.google.api.client.http.javanet.NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName(R.string.app_name.toString()).build()

    // Modified to upload to a specific folder
    fun uploadFile(fileName: String, filePath: String, parentFolderId: String): String? {
        val fileMetadata = File().apply {
            name = fileName
            parents = Collections.singletonList(parentFolderId)
        }
        val mediaContent = FileContent("application/x-sqlite3", java.io.File(filePath))
        // Check if a file with the same name already exists to update it
        val existingFileId = searchFileInFolder(fileName, parentFolderId)
        val file = if (existingFileId != null) {
            // Update existing file
            drive.files().update(existingFileId, fileMetadata, mediaContent).execute()
        } else {
            // Create new file
            drive.files().create(fileMetadata, mediaContent).setFields("id").execute()
        }
        return file.id
    }

    private fun searchFileInFolder(fileName: String, folderId: String): String? {
        val query = "name='$fileName' and '$folderId' in parents and trashed = false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        return result.files.firstOrNull()?.id
    }


    fun listFilesInFolder(folderId: String): List<File> {
        val query = "'$folderId' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, modifiedTime)")
            .setOrderBy("modifiedTime desc") // Most recent first
            .execute()
        return result.files ?: emptyList()
    }


    fun downloadFile(fileId: String, outputStream: java.io.OutputStream) {
        drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
    }

    private fun findFolder(name: String, parentId: String): String? {
        val query = "mimeType='application/vnd.google-apps.folder' and name='$name' and '$parentId' in parents and trashed=false"
        val result = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        return result.files.firstOrNull()?.id
    }

    private fun createFolder(name: String, parentId: String): String? {
        val fileMetadata = File().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            this.parents = listOf(parentId)
        }
        val file = drive.files().create(fileMetadata).setFields("id").execute()
        return file.id
    }

    fun getOrCreateFolderStructure(path: String): String? {
        var currentParentId = "root"
        val folders = path.split('/').filter { it.isNotEmpty() }

        for (folderName in folders) {
            val folderId = findFolder(folderName, currentParentId)
            currentParentId = if (folderId != null) {
                folderId
            } else {
                val newFolderId = createFolder(folderName, currentParentId)
                newFolderId ?: return null // Failed to create folder
            }
        }
        return currentParentId
    }

    fun findFolderIdByPath(path: String): String? {
        var currentParentId = "root"
        val folders = path.split('/').filter { it.isNotEmpty() }

        for (folderName in folders) {
            val folderId = findFolder(folderName, currentParentId)
            if (folderId != null) {
                currentParentId = folderId
            } else {
                return null // Path does not exist
            }
        }
        return currentParentId
    }
}