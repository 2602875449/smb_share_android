package com.qi.smb_share_android.ui.transfer

import com.qi.smb_share_android.data.model.TransferTask

/**
 * 传输管理器 UI 状态
 * 定义传输管理页面的所有状态信息
 */
data class TransferManagerState(
    val selectedTab: TransferTab = TransferTab.DOWNLOADING,
    val downloadingTasks: List<TransferTask> = emptyList(),
    val uploadingTasks: List<TransferTask> = emptyList(),
    val completedTasks: List<TransferTask> = emptyList(),
    val isMultiSelectMode: Boolean = false,
    val selectedTaskIds: Set<String> = emptySet(),
    val activeTransferCount: Int = 0,
    val error: String? = null,
    val message: String? = null
) {
    /**
     * 获取当前 Tab 显示的任务列表
     */
    val currentTabTasks: List<TransferTask>
        get() = when (selectedTab) {
            TransferTab.DOWNLOADING -> downloadingTasks
            TransferTab.UPLOADING -> uploadingTasks
            TransferTab.COMPLETED -> completedTasks
        }
    
    /**
     * 是否有选中的任务
     */
    val hasSelectedTasks: Boolean
        get() = selectedTaskIds.isNotEmpty()
    
    /**
     * 选中的任务数量
     */
    val selectedTaskCount: Int
        get() = selectedTaskIds.size
}

/**
 * Tab 枚举
 * 定义传输管理页面的三个 Tab
 */
enum class TransferTab {
    DOWNLOADING,  // 下载中
    UPLOADING,    // 上传中
    COMPLETED     // 已完成
}
