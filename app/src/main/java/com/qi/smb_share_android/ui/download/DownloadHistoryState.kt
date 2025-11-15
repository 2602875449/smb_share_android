package com.qi.smb_share_android.ui.download

import com.qi.smb_share_android.data.model.DownloadItem

data class DownloadHistoryState(
    val downloadHistory: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

