package com.qi.smb_share_android.ui.filelist

import com.qi.smb_share_android.data.model.DownloadItem
import com.qi.smb_share_android.data.model.FileItem

data class FileListState(
    val files: List<FileItem> = emptyList(),
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null,
    val downloadItem: DownloadItem? = null,
    val downloadedFile: java.io.File? = null, // 下载完成的文件，用于安装
    val searchQuery: String = "", // 搜索关键词
    val isSearchActive: Boolean = false, // 是否正在搜索
    val showCreateFolderDialog: Boolean = false, // 显示创建文件夹对话框
    val showRenameDialog: Boolean = false, // 显示重命名对话框
    val renameFilePath: String = "", // 要重命名的文件路径
    val renameCurrentName: String = "", // 当前文件名
    val fileMenuPath: String? = null // 显示文件操作菜单的文件路径
) {
    val canGoBack: Boolean get() = pathHistory.isNotEmpty()
    val filteredFiles: List<FileItem> get() = 
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
}

