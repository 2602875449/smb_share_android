package com.qi.smb_share_android.ui.download

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smb_share_android.data.repository.DownloadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "DownloadHistoryViewModel"

class DownloadHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val downloadRepository = DownloadRepository(application)
    
    private val _state = MutableStateFlow(DownloadHistoryState())
    val state: StateFlow<DownloadHistoryState> = _state.asStateFlow()
    
    init {
        // 监听下载历史变化
        viewModelScope.launch {
            downloadRepository.downloadHistory.collect { history ->
                _state.value = _state.value.copy(
                    downloadHistory = history,
                    isLoading = false
                )
            }
        }
    }
    
    fun handleIntent(intent: DownloadHistoryIntent) {
        when (intent) {
            is DownloadHistoryIntent.OpenFile -> {
                openFile(intent.item)
            }
            is DownloadHistoryIntent.RetryDownload -> {
                retryDownload(intent.item)
            }
            is DownloadHistoryIntent.OpenFileLocation -> {
                openFileLocation(intent.item)
            }
            is DownloadHistoryIntent.DeleteHistory -> {
                deleteHistory(intent.itemId)
            }
            is DownloadHistoryIntent.ClearError -> {
                _state.value = _state.value.copy(error = null)
            }
        }
    }
    
    private fun openFile(item: com.qi.smb_share_android.data.model.DownloadItem) {
        val file = File(item.localPath)
        if (!file.exists()) {
            _state.value = _state.value.copy(error = "文件不存在")
            return
        }
        
        try {
            val context = getApplication<Application>()
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.name))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开文件失败", e)
            _state.value = _state.value.copy(error = "打开文件失败: ${e.message}")
        }
    }
    
    private fun retryDownload(item: com.qi.smb_share_android.data.model.DownloadItem) {
        // 重试下载需要在文件列表页实现，因为需要SMB连接
        _state.value = _state.value.copy(error = "请在文件页面重试下载")
    }
    
    private fun openFileLocation(item: com.qi.smb_share_android.data.model.DownloadItem) {
        val file = File(item.localPath)
        val parentDir = file.parentFile ?: return
        
        try {
            val context = getApplication<Application>()
            val uri = Uri.parse("content://com.android.externalstorage.documents/document/primary:${parentDir.absolutePath.replace("/storage/emulated/0/", "")}")
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果上面的方法失败，尝试使用文件管理器
            try {
                val context = getApplication<Application>()
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse("file://${parentDir.absolutePath}"), "resource/folder")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "打开文件所在目录失败", e2)
                _state.value = _state.value.copy(error = "无法打开文件所在目录")
            }
        }
    }
    
    private fun deleteHistory(itemId: String) {
        viewModelScope.launch {
            try {
                downloadRepository.deleteDownloadHistory(itemId)
            } catch (e: Exception) {
                Log.e(TAG, "删除下载历史失败", e)
                _state.value = _state.value.copy(error = "删除失败: ${e.message}")
            }
        }
    }
    
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "*/*"
        }
    }
}

