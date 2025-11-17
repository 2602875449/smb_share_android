package com.qi.smb_share_android.ui.transfer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smb_share_android.data.model.TransferStatus
import com.qi.smb_share_android.data.model.TransferType
import com.qi.smb_share_android.data.repository.TransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 传输管理器 ViewModel
 * 负责管理传输任务的 UI 状态和业务逻辑
 */
class TransferManagerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val transferRepository = TransferRepository(application)
    
    private val _state = MutableStateFlow(TransferManagerState())
    val state: StateFlow<TransferManagerState> = _state.asStateFlow()
    
    init {
        observeTransferTasks()
    }
    
    /**
     * 处理用户意图
     * 统一入口处理所有用户操作
     */
    fun handleIntent(intent: TransferManagerIntent) {
        when (intent) {
            is TransferManagerIntent.SwitchTab -> {
                switchTab(intent.tab)
            }
            is TransferManagerIntent.PauseTransfer -> {
                pauseTransfer(intent.taskId)
            }
            is TransferManagerIntent.ResumeTransfer -> {
                resumeTransfer(intent.taskId)
            }
            is TransferManagerIntent.CancelTransfer -> {
                cancelTransfer(intent.taskId)
            }
            is TransferManagerIntent.RetryTransfer -> {
                retryTransfer(intent.taskId)
            }
            is TransferManagerIntent.DeleteTransfer -> {
                deleteTransfer(intent.taskId, intent.deleteFile)
            }
            is TransferManagerIntent.EnterMultiSelectMode -> {
                enterMultiSelectMode()
            }
            is TransferManagerIntent.ExitMultiSelectMode -> {
                exitMultiSelectMode()
            }
            is TransferManagerIntent.ToggleTaskSelection -> {
                toggleTaskSelection(intent.taskId)
            }
            is TransferManagerIntent.SelectAllTasks -> {
                selectAllTasks()
            }
            is TransferManagerIntent.DeleteSelectedTasks -> {
                deleteSelectedTasks(intent.deleteFiles)
            }
            is TransferManagerIntent.CancelSelectedTasks -> {
                cancelSelectedTasks()
            }
            is TransferManagerIntent.ClearError -> {
                _state.value = _state.value.copy(error = null)
            }
            is TransferManagerIntent.ClearMessage -> {
                _state.value = _state.value.copy(message = null)
            }
            is TransferManagerIntent.RefreshTasks -> {
                // 任务列表通过 Flow 自动更新，无需手动刷新
            }
        }
    }
    
    /**
     * 监听数据库变化，更新任务列表
     * 将所有任务按类型和状态分组
     */
    private fun observeTransferTasks() {
        viewModelScope.launch {
            // 组合所有任务流和活动任务数量流
            combine(
                transferRepository.allTasks,
                transferRepository.activeTransferCount
            ) { tasks, activeCount ->
                Pair(tasks, activeCount)
            }.collect { (tasks, activeCount) ->
                // 按类型和状态分组任务
                val downloadingTasks = tasks.filter { task ->
                    task.type == TransferType.DOWNLOAD &&
                    task.status in listOf(TransferStatus.PENDING, TransferStatus.ACTIVE, TransferStatus.PAUSED)
                }
                
                val uploadingTasks = tasks.filter { task ->
                    task.type == TransferType.UPLOAD &&
                    task.status in listOf(TransferStatus.PENDING, TransferStatus.ACTIVE, TransferStatus.PAUSED)
                }
                
                val completedTasks = tasks.filter { task ->
                    task.status in listOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)
                }
                
                // 更新状态
                _state.value = _state.value.copy(
                    downloadingTasks = downloadingTasks,
                    uploadingTasks = uploadingTasks,
                    completedTasks = completedTasks,
                    activeTransferCount = activeCount
                )
            }
        }
    }
    
    /**
     * 切换 Tab
     * 切换到指定的 Tab 并退出多选模式
     */
    private fun switchTab(tab: TransferTab) {
        _state.value = _state.value.copy(
            selectedTab = tab,
            isMultiSelectMode = false,
            selectedTaskIds = emptySet()
        )
    }
    
    /**
     * 暂停传输任务
     */
    private fun pauseTransfer(taskId: String) {
        viewModelScope.launch {
            try {
                transferRepository.pauseTransfer(taskId)
                _state.value = _state.value.copy(message = "任务已暂停")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "暂停失败: ${e.message}")
            }
        }
    }
    
    /**
     * 恢复传输任务
     */
    private fun resumeTransfer(taskId: String) {
        viewModelScope.launch {
            try {
                transferRepository.resumeTransfer(taskId)
                _state.value = _state.value.copy(message = "任务已恢复")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "恢复失败: ${e.message}")
            }
        }
    }
    
    /**
     * 取消传输任务
     */
    private fun cancelTransfer(taskId: String) {
        viewModelScope.launch {
            try {
                transferRepository.cancelTransfer(taskId)
                _state.value = _state.value.copy(message = "任务已取消")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "取消失败: ${e.message}")
            }
        }
    }
    
    /**
     * 重试失败的传输任务
     */
    private fun retryTransfer(taskId: String) {
        viewModelScope.launch {
            try {
                val newTaskId = transferRepository.retryTransfer(taskId)
                if (newTaskId != null) {
                    _state.value = _state.value.copy(message = "任务已重新开始")
                } else {
                    _state.value = _state.value.copy(error = "重试失败：任务状态不正确")
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "重试失败: ${e.message}")
            }
        }
    }
    
    /**
     * 删除传输任务
     * @param taskId 任务 ID
     * @param deleteFile 是否同时删除本地文件
     */
    private fun deleteTransfer(taskId: String, deleteFile: Boolean) {
        viewModelScope.launch {
            try {
                var fileDeleted = false
                
                // 如果需要删除文件，先获取任务信息
                if (deleteFile) {
                    val task = transferRepository.getTaskById(taskId)
                    task?.let {
                        // 只删除下载任务的本地文件
                        if (it.type == TransferType.DOWNLOAD) {
                            // 使用 StorageHelper 删除文件（支持 URI 格式）
                            fileDeleted = com.qi.smb_share_android.util.StorageHelper.deleteFile(
                                getApplication(),
                                it.localPath
                            )
                        }
                    }
                }
                
                // 删除任务记录
                transferRepository.deleteTransfer(taskId)
                
                // 显示成功消息
                val message = if (deleteFile && fileDeleted) {
                    "任务和文件已删除"
                } else if (deleteFile) {
                    "任务已删除（文件删除失败）"
                } else {
                    "任务已删除"
                }
                
                _state.value = _state.value.copy(message = message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "删除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 进入多选模式
     */
    private fun enterMultiSelectMode() {
        _state.value = _state.value.copy(
            isMultiSelectMode = true,
            selectedTaskIds = emptySet()
        )
    }
    
    /**
     * 退出多选模式
     */
    private fun exitMultiSelectMode() {
        _state.value = _state.value.copy(
            isMultiSelectMode = false,
            selectedTaskIds = emptySet()
        )
    }
    
    /**
     * 切换任务的选中状态
     */
    private fun toggleTaskSelection(taskId: String) {
        val currentSelection = _state.value.selectedTaskIds
        val newSelection = if (taskId in currentSelection) {
            currentSelection - taskId
        } else {
            currentSelection + taskId
        }
        
        _state.value = _state.value.copy(selectedTaskIds = newSelection)
    }
    
    /**
     * 全选当前 Tab 的任务
     */
    private fun selectAllTasks() {
        val currentTabTasks = _state.value.currentTabTasks
        val allTaskIds = currentTabTasks.map { it.id }.toSet()
        
        _state.value = _state.value.copy(selectedTaskIds = allTaskIds)
    }
    
    /**
     * 批量删除选中的任务
     * @param deleteFiles 是否同时删除本地文件
     */
    private fun deleteSelectedTasks(deleteFiles: Boolean) {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedTaskIds.toList()
                
                if (selectedIds.isEmpty()) {
                    _state.value = _state.value.copy(error = "请先选择要删除的任务")
                    return@launch
                }
                
                // 如果需要删除文件，先获取所有任务信息
                var deletedCount = 0
                if (deleteFiles) {
                    selectedIds.forEach { taskId ->
                        val task = transferRepository.getTaskById(taskId)
                        task?.let {
                            // 只删除下载任务的本地文件
                            if (it.type == TransferType.DOWNLOAD) {
                                // 使用 StorageHelper 删除文件（支持 URI 格式）
                                val deleted = com.qi.smb_share_android.util.StorageHelper.deleteFile(
                                    getApplication(),
                                    it.localPath
                                )
                                if (deleted) {
                                    deletedCount++
                                }
                            }
                        }
                    }
                }
                
                // 批量删除任务记录
                transferRepository.deleteTransfers(selectedIds)
                
                // 退出多选模式
                val message = if (deleteFiles) {
                    if (deletedCount == selectedIds.size) {
                        "已删除 ${selectedIds.size} 个任务和文件"
                    } else if (deletedCount > 0) {
                        "已删除 ${selectedIds.size} 个任务（${deletedCount} 个文件删除成功）"
                    } else {
                        "已删除 ${selectedIds.size} 个任务（文件删除失败）"
                    }
                } else {
                    "已删除 ${selectedIds.size} 个任务"
                }
                
                _state.value = _state.value.copy(
                    isMultiSelectMode = false,
                    selectedTaskIds = emptySet(),
                    message = message
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "批量删除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 批量取消选中的任务
     */
    private fun cancelSelectedTasks() {
        viewModelScope.launch {
            try {
                val selectedIds = _state.value.selectedTaskIds.toList()
                
                if (selectedIds.isEmpty()) {
                    _state.value = _state.value.copy(error = "请先选择要取消的任务")
                    return@launch
                }
                
                // 逐个取消任务
                var successCount = 0
                selectedIds.forEach { taskId ->
                    try {
                        transferRepository.cancelTransfer(taskId)
                        successCount++
                    } catch (e: Exception) {
                        // 继续处理其他任务
                    }
                }
                
                // 退出多选模式
                _state.value = _state.value.copy(
                    isMultiSelectMode = false,
                    selectedTaskIds = emptySet(),
                    message = "已取消 $successCount 个任务"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "批量取消失败: ${e.message}")
            }
        }
    }
}
