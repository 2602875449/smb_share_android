package com.qi.smb_share_android.data.repository

import android.content.Context
import android.os.Environment
import android.util.Log
import com.qi.smb_share_android.data.local.DataStoreManager
import com.qi.smb_share_android.data.model.DownloadItem
import com.qi.smb_share_android.data.model.DownloadStatus
import com.qi.smb_share_android.util.FileTypeHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private const val TAG = "DownloadRepository"

class DownloadRepository(private val context: Context) {
    private val dataStoreManager = DataStoreManager(context)
    private val _currentDownload = MutableStateFlow<DownloadItem?>(null)
    val currentDownload: StateFlow<DownloadItem?> = _currentDownload.asStateFlow()
    
    // 下载历史记录（按时间倒序）
    val downloadHistory: Flow<List<DownloadItem>> = dataStoreManager.downloadHistory
        .map { it.sortedByDescending { item -> item.timestamp } }

    /**
     * 获取下载目录
     * 优先使用系统公共下载目录，如果不可用则使用应用私有目录
     */
    private fun getDownloadDirectory(): File {
        // 尝试使用系统公共下载目录
        val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDownloadDir = File(publicDownloadDir, "SMBShare")
        
        return if (publicDownloadDir.exists() || publicDownloadDir.mkdirs()) {
            // 在公共下载目录下创建应用专属文件夹
            if (!appDownloadDir.exists()) {
                appDownloadDir.mkdirs()
            }
            appDownloadDir
        } else {
            // 如果公共目录不可用，使用应用私有目录作为备选
            val privateDir = File(context.getExternalFilesDir(null), "downloads")
            if (!privateDir.exists()) {
                privateDir.mkdirs()
            }
            privateDir
        }
    }

    /**
     * 下载文件
     */
    suspend fun downloadFile(
        inputStream: java.io.InputStream,
        fileName: String,
        remotePath: String,
        onProgress: (Int, Long, Long) -> Unit
    ): Result<File> {
        Log.d(TAG, "开始下载文件: $fileName")
        Log.d(TAG, "远程路径: $remotePath")
        
        val downloadDir = getDownloadDirectory()
        Log.d(TAG, "下载目录: ${downloadDir.absolutePath}")

        val localFile = File(downloadDir, fileName)
        Log.d(TAG, "本地保存路径: ${localFile.absolutePath}")
        
        val downloadItem = DownloadItem(
            fileName = fileName,
            remotePath = remotePath,
            localPath = localFile.absolutePath,
            status = DownloadStatus.DOWNLOADING
        )
        _currentDownload.value = downloadItem

        return try {
            var totalBytes = 0L
            var downloadedBytes = 0L

            FileOutputStream(localFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int

                // 先读取一次以获取总大小（如果可能）
                // 注意：SMB流可能不支持获取总大小，所以我们需要边读边写
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    totalBytes = maxOf(totalBytes, downloadedBytes)

                    // 更新进度（如果知道总大小，可以计算百分比）
                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt()
                    } else {
                        -1 // 未知总大小
                    }
                    onProgress(progress, downloadedBytes, totalBytes)

                    // 更新下载项
                    _currentDownload.value = downloadItem.copy(
                        progress = progress,
                        downloadedBytes = downloadedBytes,
                        totalBytes = totalBytes
                    )
                }
            }

            val completedItem = downloadItem.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes
            )
            _currentDownload.value = completedItem
            
            // 保存到下载历史
            saveToHistory(completedItem)

            Log.d(TAG, "文件下载成功: ${localFile.absolutePath}")
            Log.d(TAG, "总大小: ${FileTypeHelper.formatFileSize(downloadedBytes)}")
            Result.success(localFile)
        } catch (e: Exception) {
            Log.e(TAG, "文件下载失败", e)
            Log.e(TAG, "文件名: $fileName")
            Log.e(TAG, "本地路径: ${localFile.absolutePath}")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            val failedItem = downloadItem.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message
            )
            _currentDownload.value = failedItem
            
            // 保存到下载历史
            saveToHistory(failedItem)
            
            Result.failure(IOException("下载失败: ${e.message}", e))
        }
    }

    /**
     * 清除当前下载状态
     */
    fun clearDownload() {
        _currentDownload.value = null
    }
    
    /**
     * 保存下载项到历史记录
     */
    private suspend fun saveToHistory(item: DownloadItem) {
        val currentHistory = dataStoreManager.downloadHistory.first().toMutableList()
        // 如果已存在相同ID的记录，更新它；否则添加新记录
        val existingIndex = currentHistory.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            currentHistory[existingIndex] = item
        } else {
            currentHistory.add(item)
        }
        dataStoreManager.saveDownloadHistory(currentHistory)
    }
    
    /**
     * 重试下载
     */
    suspend fun retryDownload(item: DownloadItem): Result<File> {
        // 更新状态为PENDING
        val updatedItem = item.copy(
            status = DownloadStatus.PENDING,
            progress = 0,
            errorMessage = null
        )
        saveToHistory(updatedItem)
        
        // 这里需要重新触发下载，但需要文件输入流
        // 实际的重试逻辑应该在UseCase层处理
        return Result.failure(IOException("重试功能需要在UseCase层实现"))
    }
    
    /**
     * 删除下载历史记录
     */
    suspend fun deleteDownloadHistory(itemId: String) {
        val currentHistory = dataStoreManager.downloadHistory.first().toMutableList()
        currentHistory.removeAll { it.id == itemId }
        dataStoreManager.saveDownloadHistory(currentHistory)
    }
}

