package com.qi.smb_share_android.data.model

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val remotePath: String, // SMB服务器上的路径
    val localPath: String, // 本地保存路径
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Int = 0, // 下载进度 0-100
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis() // 下载时间戳
)

