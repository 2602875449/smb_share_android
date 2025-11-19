package com.qi.smbshare.ui.transfer

/**
 * 传输管理器用户意图
 * 定义用户在传输管理页面可以执行的所有操作
 */
sealed class TransferManagerIntent {
    /**
     * 切换 Tab
     */
    data class SwitchTab(val tab: TransferTab) : TransferManagerIntent()
    
    /**
     * 暂停传输任务
     */
    data class PauseTransfer(val taskId: String) : TransferManagerIntent()
    
    /**
     * 恢复传输任务
     */
    data class ResumeTransfer(val taskId: String) : TransferManagerIntent()
    
    /**
     * 取消传输任务
     */
    data class CancelTransfer(val taskId: String) : TransferManagerIntent()
    
    /**
     * 重试失败的传输任务
     */
    data class RetryTransfer(val taskId: String) : TransferManagerIntent()
    
    /**
     * 删除传输任务
     * @param taskId 任务 ID
     * @param deleteFile 是否同时删除本地文件
     */
    data class DeleteTransfer(val taskId: String, val deleteFile: Boolean = false) : TransferManagerIntent()
    
    /**
     * 进入多选模式
     */
    object EnterMultiSelectMode : TransferManagerIntent()
    
    /**
     * 退出多选模式
     */
    object ExitMultiSelectMode : TransferManagerIntent()
    
    /**
     * 切换任务的选中状态
     */
    data class ToggleTaskSelection(val taskId: String) : TransferManagerIntent()
    
    /**
     * 全选当前 Tab 的任务
     */
    object SelectAllTasks : TransferManagerIntent()
    
    /**
     * 批量删除选中的任务
     * @param deleteFiles 是否同时删除本地文件
     */
    data class DeleteSelectedTasks(val deleteFiles: Boolean = false) : TransferManagerIntent()
    
    /**
     * 批量取消选中的任务
     */
    object CancelSelectedTasks : TransferManagerIntent()
    
    /**
     * 清空错误信息
     */
    object ClearError : TransferManagerIntent()
    
    /**
     * 清空消息
     */
    object ClearMessage : TransferManagerIntent()
    
    /**
     * 刷新任务列表
     */
    object RefreshTasks : TransferManagerIntent()
}
