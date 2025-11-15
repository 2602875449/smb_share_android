package com.qi.smb_share_android.ui.download

sealed class DownloadHistoryIntent {
    data class OpenFile(val item: com.qi.smb_share_android.data.model.DownloadItem) : DownloadHistoryIntent()
    data class RetryDownload(val item: com.qi.smb_share_android.data.model.DownloadItem) : DownloadHistoryIntent()
    data class OpenFileLocation(val item: com.qi.smb_share_android.data.model.DownloadItem) : DownloadHistoryIntent()
    data class DeleteHistory(val itemId: String) : DownloadHistoryIntent()
    object ClearError : DownloadHistoryIntent()
}

