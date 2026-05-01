package com.qi.smbshare.ui.filelist

import com.qi.smbshare.data.model.FileItem
import com.qi.smbshare.util.ErrorHandler
import java.io.File

/** 文件预览的加载状态 */
sealed class PreviewState {
    object Idle : PreviewState()
    object Loading : PreviewState()
    /** 图片预览：持有本地缓存文件，避免大图一次性读入 JVM 内存 */
    data class ImageReady(val cacheFile: File) : PreviewState()
    /** 文本预览：持有解码后的字符串内容，isTruncated 表示是否因超限而截断 */
    data class TextReady(val content: String, val isTruncated: Boolean = false) : PreviewState()
    /**
     * 视频缓存中：将 SMB 文件流式写入本地临时文件以便 ExoPlayer 播放。
     * progress 为 0.0 ~ 1.0 的写入进度；文件大小未知时为 -1。
     */
    data class VideoDownloading(val progress: Float) : PreviewState()
    /**
     * 视频就绪：本地临时缓存文件已写完，ExoPlayer 可直接读取。
     * 关闭预览或 ViewModel 销毁时由 ViewModel 负责删除该文件。
     */
    data class VideoReady(val cacheFile: File) : PreviewState()
    data class Error(val message: String) : PreviewState()
}

data class FileListState(
    val files: List<FileItem> = emptyList(),
    val currentPath: String = "",
    val pathHistory: List<String> = emptyList(),
    val isLoading: Boolean = false, // 用于列表加载
    val isOperating: Boolean = false, // 用于单次操作（创建/删除/重命名）
    val isUploading: Boolean = false,
    val error: String? = null,
    val connectionErrorType: ErrorHandler.AppErrorType? = null,
    val message: String? = null, // 成功消息提示
    val searchQuery: String = "", // 搜索关键词
    val isSearchActive: Boolean = false, // 是否正在搜索
    val showCreateFolderDialog: Boolean = false, // 显示创建文件夹对话框
    val showRenameDialog: Boolean = false, // 显示重命名对话框
    val renameFilePath: String = "", // 要重命名的文件路径
    val renameCurrentName: String = "", // 当前文件名
    val fileMenuPath: String? = null, // 显示文件操作菜单的文件路径
    val previewFileName: String? = null, // 正在预览的文件名（非空表示预览页可见）
    val previewState: PreviewState = PreviewState.Idle // 预览内容加载状态
) {
    val canGoBack: Boolean get() = pathHistory.isNotEmpty()
    val filteredFiles: List<FileItem> get() = 
        if (searchQuery.isBlank()) files
        else files.filter { it.name.contains(searchQuery, ignoreCase = true) }
}
