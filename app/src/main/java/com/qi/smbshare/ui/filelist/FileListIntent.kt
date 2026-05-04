package com.qi.smbshare.ui.filelist

import android.net.Uri

sealed class FileListIntent {
    object LoadFiles : FileListIntent()
    data class EnterDirectory(val path: String) : FileListIntent()
    data class JumpToPath(val path: String) : FileListIntent()
    object GoBack : FileListIntent()
    data class DownloadFile(val filePath: String, val fileName: String) : FileListIntent()
    object ClearError : FileListIntent()
    object ClearMessage : FileListIntent()
    data class UpdateSearchQuery(val query: String) : FileListIntent()
    object ToggleSearch : FileListIntent()
    data class UploadFile(
        val uri: Uri,
        val displayName: String,
        val size: Long
    ) : FileListIntent()
    data class CreateFolder(val folderName: String) : FileListIntent()
    data class DeleteFile(val filePath: String, val isDirectory: Boolean) : FileListIntent()
    data class RenameFile(val filePath: String, val newName: String) : FileListIntent()
    object ShowCreateFolderDialog : FileListIntent()
    object HideCreateFolderDialog : FileListIntent()
    data class ShowRenameDialog(val filePath: String, val currentName: String) : FileListIntent()
    object HideRenameDialog : FileListIntent()
    data class ShowFileMenu(val filePath: String) : FileListIntent()
    object HideFileMenu : FileListIntent()
    data class PreviewFile(val filePath: String, val fileName: String) : FileListIntent()
    object ClosePreview : FileListIntent()
}
