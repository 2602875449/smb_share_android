package com.qi.smb_share_android.ui.filelist

import java.io.File

sealed class FileListIntent {
    object LoadFiles : FileListIntent()
    data class EnterDirectory(val path: String) : FileListIntent()
    object GoBack : FileListIntent()
    data class DownloadFile(val filePath: String, val fileName: String) : FileListIntent()
    object ClearError : FileListIntent()
    object ClearDownload : FileListIntent()
    data class UpdateSearchQuery(val query: String) : FileListIntent()
    object ToggleSearch : FileListIntent()
    data class UploadFile(val file: File) : FileListIntent()
    data class CreateFolder(val folderName: String) : FileListIntent()
    data class DeleteFile(val filePath: String) : FileListIntent()
    data class RenameFile(val filePath: String, val newName: String) : FileListIntent()
    object ShowCreateFolderDialog : FileListIntent()
    object HideCreateFolderDialog : FileListIntent()
    data class ShowRenameDialog(val filePath: String, val currentName: String) : FileListIntent()
    object HideRenameDialog : FileListIntent()
    data class ShowFileMenu(val filePath: String) : FileListIntent()
    object HideFileMenu : FileListIntent()
}

