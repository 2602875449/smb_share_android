package com.qi.smbshare.ui.transfer

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.R
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.data.repository.TransferRepository
import com.qi.smbshare.util.ErrorHandler
import com.qi.smbshare.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 传输管理器 ViewModel
 * 负责管理传输任务的 UI 状态和业务逻辑
 */
@HiltViewModel
class TransferManagerViewModel @Inject constructor(
    application: Application,
    private val transferRepository: TransferRepository
) : AndroidViewModel(application) {

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
                
                // 在 IO 线程检查已完成下载任务的文件有效性，避免主线程阻塞
                val fileValidityMap = withContext(Dispatchers.IO) {
                    completedTasks
                        .filter { it.type == TransferType.DOWNLOAD }
                        .associate { task ->
                            task.id to StorageHelper.fileExists(getApplication(), task.localPath)
                        }
                }

                // 更新状态
                _state.value = _state.value.copy(
                    downloadingTasks = downloadingTasks,
                    uploadingTasks = uploadingTasks,
                    completedTasks = completedTasks,
                    activeTransferCount = activeCount,
                    fileValidityMap = fileValidityMap
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
                _state.value = _state.value.copy(message = text(R.string.transfer_task_paused))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_pause_transfer_failed))
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
                _state.value = _state.value.copy(message = text(R.string.transfer_task_resumed))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_resume_transfer_failed))
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
                _state.value = _state.value.copy(message = text(R.string.transfer_task_cancelled))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_cancel_transfer_failed))
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
                    _state.value = _state.value.copy(message = text(R.string.transfer_task_restarted))
                } else {
                    _state.value = _state.value.copy(error = text(R.string.error_retry_transfer_invalid_state))
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_retry_transfer_failed))
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
                            fileDeleted = StorageHelper.deleteFile(
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
                    text(R.string.transfer_task_and_file_deleted)
                } else if (deleteFile) {
                    text(R.string.transfer_task_deleted_file_failed)
                } else {
                    text(R.string.transfer_task_deleted)
                }
                
                _state.value = _state.value.copy(message = message)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_delete_transfer_failed))
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
                    _state.value = _state.value.copy(error = text(R.string.transfer_selected_delete_empty))
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
                                val deleted = StorageHelper.deleteFile(
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
                        text(R.string.transfer_delete_batch_tasks_and_files, selectedIds.size)
                    } else if (deletedCount > 0) {
                        text(R.string.transfer_delete_batch_tasks_file_partial, selectedIds.size, deletedCount)
                    } else {
                        text(R.string.transfer_delete_batch_tasks_file_failed, selectedIds.size)
                    }
                } else {
                    text(R.string.transfer_delete_batch_tasks, selectedIds.size)
                }
                
                _state.value = _state.value.copy(
                    isMultiSelectMode = false,
                    selectedTaskIds = emptySet(),
                    message = message
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_delete_selected_failed))
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
                    _state.value = _state.value.copy(error = text(R.string.transfer_selected_cancel_empty))
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
                    message = text(R.string.transfer_cancel_batch_tasks, successCount)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = formatError(e, R.string.error_cancel_selected_failed))
            }
        }
    }

    private fun text(@StringRes resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    private fun formatError(error: Throwable, @StringRes fallbackResId: Int): String {
        return if (error is Exception) {
            ErrorHandler.getErrorMessageFromException(
                context = getApplication(),
                exception = error,
                fallbackMessageResId = fallbackResId
            )
        } else {
            text(fallbackResId)
        }
    }
}
