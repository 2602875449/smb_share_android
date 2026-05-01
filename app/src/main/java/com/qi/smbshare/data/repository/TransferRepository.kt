package com.qi.smbshare.data.repository

import android.content.Context
import android.content.Intent
import com.qi.smbshare.data.local.TransferDatabase
import com.qi.smbshare.data.local.TransferTaskDao
import com.qi.smbshare.data.local.toEntity
import com.qi.smbshare.data.local.toModel
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.util.toJsonString
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * 传输任务仓库
 * 负责管理传输任务的 CRUD 操作，与数据库和传输服务交互
 */
class TransferRepository(
    private val context: Context,
    private val taskDao: TransferTaskDao = TransferDatabase.getInstance(context).transferTaskDao()
) {
    
    /**
     * 获取所有传输任务的 Flow
     */
    val allTasks: Flow<List<TransferTask>> = taskDao.getAllTasks()
        .map { entities -> entities.map { it.toModel() } }
    
    /**
     * 获取活动传输任务数量的 Flow
     * 用于底部导航徽章显示
     */
    val activeTransferCount: Flow<Int> = taskDao.getActiveTransferCount()
    
    /**
     * 开始下载任务
     * 创建下载任务并启动传输服务
     * 
     * @param fileName 文件名
     * @param remotePath SMB 服务器路径
     * @param localPath 本地保存路径
     * @param fileSize 文件大小
     * @param config SMB 连接配置
     * @return 任务 ID
     */
    suspend fun startDownload(
        fileName: String,
        remotePath: String,
        localPath: String,
        fileSize: Long,
        config: SMBConfig
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        // 创建下载任务
        val task = TransferTask(
            id = taskId,
            type = TransferType.DOWNLOAD,
            fileName = fileName,
            fileSize = fileSize,
            remotePath = remotePath,
            localPath = localPath,
            config = config,
            status = TransferStatus.PENDING,
            progress = 0,
            transferredBytes = 0,
            speed = 0,
            estimatedTimeRemaining = 0,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            startedAt = null,
            completedAt = null,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        // 保存到数据库
        taskDao.insertTask(task.toEntity())
        
        // 启动传输服务
        startTransferService(taskId, config)
        
        return taskId
    }

    /**
     * 开始上传任务
     * 创建上传任务并启动传输服务
     * 
     * @param fileName 文件名
     * @param localPath 本地文件路径
     * @param remotePath SMB 服务器目标路径
     * @param fileSize 文件大小
     * @param config SMB 连接配置
     * @return 任务 ID
     */
    suspend fun startUpload(
        fileName: String,
        localPath: String,
        remotePath: String,
        fileSize: Long,
        config: SMBConfig
    ): String {
        val taskId = UUID.randomUUID().toString()
        
        // 创建上传任务
        val task = TransferTask(
            id = taskId,
            type = TransferType.UPLOAD,
            fileName = fileName,
            fileSize = fileSize,
            remotePath = remotePath,
            localPath = localPath,
            config = config,
            status = TransferStatus.PENDING,
            progress = 0,
            transferredBytes = 0,
            speed = 0,
            estimatedTimeRemaining = 0,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            startedAt = null,
            completedAt = null,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        // 保存到数据库
        taskDao.insertTask(task.toEntity())
        
        // 启动传输服务
        startTransferService(taskId, config)
        
        return taskId
    }
    
    /**
     * 暂停传输任务
     * 更新任务状态为已暂停，并通知传输服务
     * 
     * @param taskId 任务 ID
     */
    suspend fun pauseTransfer(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toModel()
        
        // 只有进行中的任务才能暂停
        if (task.status != TransferStatus.ACTIVE) {
            return
        }
        
        // 更新状态为已暂停
        val updatedTask = task.copy(
            status = TransferStatus.PAUSED,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        taskDao.updateTask(updatedTask.toEntity())
        
        // 通知传输服务暂停任务
        notifyTransferService(com.qi.smbshare.service.TransferService.ACTION_PAUSE_TRANSFER, taskId)
    }
    
    /**
     * 恢复传输任务
     * 更新任务状态为进行中，并通知传输服务
     * 
     * @param taskId 任务 ID
     */
    suspend fun resumeTransfer(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toModel()
        
        // 只有已暂停的任务才能恢复
        if (task.status != TransferStatus.PAUSED) {
            return
        }
        
        // 更新状态为进行中
        val updatedTask = task.copy(
            status = TransferStatus.ACTIVE,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        taskDao.updateTask(updatedTask.toEntity())
        
        // 通知传输服务恢复任务
        notifyTransferService(com.qi.smbshare.service.TransferService.ACTION_RESUME_TRANSFER, taskId)
    }
    
    /**
     * 取消传输任务
     * 更新任务状态为已取消，并通知传输服务
     * 
     * @param taskId 任务 ID
     */
    suspend fun cancelTransfer(taskId: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toModel()
        
        // 只有等待中、进行中或已暂停的任务才能取消
        if (task.status !in listOf(TransferStatus.PENDING, TransferStatus.ACTIVE, TransferStatus.PAUSED)) {
            return
        }
        
        // 更新状态为已取消
        val updatedTask = task.copy(
            status = TransferStatus.CANCELLED,
            completedAt = System.currentTimeMillis(),
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        taskDao.updateTask(updatedTask.toEntity())
        
        // 通知传输服务取消任务
        notifyTransferService(com.qi.smbshare.service.TransferService.ACTION_CANCEL_TRANSFER, taskId)
    }
    
    /**
     * 重试失败的传输任务
     * 使用原始参数重新创建传输任务
     * 
     * @param taskId 原任务 ID
     * @return 新任务 ID，如果原任务不存在或状态不是失败则返回 null
     */
    suspend fun retryTransfer(taskId: String): String? {
        val entity = taskDao.getTaskById(taskId) ?: return null
        val task = entity.toModel()
        
        // 只有失败的任务才能重试
        if (task.status != TransferStatus.FAILED) {
            return null
        }
        
        // 创建新的任务 ID
        val newTaskId = UUID.randomUUID().toString()
        
        // 使用原始参数创建新任务，保留重试计数
        val newTask = task.copy(
            id = newTaskId,
            status = TransferStatus.PENDING,
            progress = 0,
            transferredBytes = 0,
            speed = 0,
            estimatedTimeRemaining = 0,
            errorMessage = null,
            retryCount = task.retryCount + 1,  // 增加重试计数
            createdAt = System.currentTimeMillis(),
            startedAt = null,
            completedAt = null,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        // 保存新任务到数据库
        taskDao.insertTask(newTask.toEntity())
        
        // 启动传输服务重新执行任务
        startTransferService(newTaskId, newTask.config)
        
        return newTaskId
    }

    /**
     * 删除传输任务
     * 从数据库中删除任务记录
     * 
     * @param taskId 任务 ID
     */
    suspend fun deleteTransfer(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }
    
    /**
     * 批量删除传输任务
     * 
     * @param taskIds 任务 ID 列表
     */
    suspend fun deleteTransfers(taskIds: List<String>) {
        taskDao.deleteTasksByIds(taskIds)
    }
    
    /**
     * 更新传输进度
     * 由传输服务调用，更新任务的进度信息
     * 
     * @param taskId 任务 ID
     * @param progress 进度百分比 (0-100)
     * @param transferredBytes 已传输字节数
     * @param speed 传输速度（字节/秒）
     */
    suspend fun updateProgress(
        taskId: String,
        progress: Int,
        transferredBytes: Long,
        speed: Long
    ) {
        taskDao.updateProgress(
            taskId = taskId,
            progress = progress.coerceIn(0, 100),
            transferredBytes = transferredBytes,
            speed = speed,
            lastUpdatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新任务状态
     * 由传输服务调用，更新任务的状态
     * 
     * @param taskId 任务 ID
     * @param status 新状态
     * @param errorMessage 错误信息（可选）
     */
    suspend fun updateTaskStatus(
        taskId: String,
        status: TransferStatus,
        errorMessage: String? = null
    ) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toModel()
        
        val now = System.currentTimeMillis()
        
        // 根据状态更新相应的时间戳
        val updatedTask = when (status) {
            TransferStatus.ACTIVE -> task.copy(
                status = status,
                startedAt = task.startedAt ?: now,
                errorMessage = null,
                lastUpdatedAt = now
            )
            TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED -> task.copy(
                status = status,
                completedAt = now,
                errorMessage = errorMessage,
                lastUpdatedAt = now
            )
            else -> task.copy(
                status = status,
                errorMessage = errorMessage,
                lastUpdatedAt = now
            )
        }
        
        taskDao.updateTask(updatedTask.toEntity())
    }
    
    /**
     * 更新任务的本地文件路径
     * 由传输服务调用，当实际文件路径与任务路径不同时更新
     * 
     * @param taskId 任务 ID
     * @param localPath 新的本地文件路径
     */
    suspend fun updateTaskLocalPath(taskId: String, localPath: String) {
        val entity = taskDao.getTaskById(taskId) ?: return
        val task = entity.toModel()
        
        val updatedTask = task.copy(
            localPath = localPath,
            lastUpdatedAt = System.currentTimeMillis()
        )
        
        taskDao.updateTask(updatedTask.toEntity())
    }
    
    /**
     * 获取指定 ID 的任务
     * 
     * @param taskId 任务 ID
     * @return 传输任务，如果不存在则返回 null
     */
    suspend fun getTaskById(taskId: String): TransferTask? {
        return taskDao.getTaskById(taskId)?.toModel()
    }
    
    /**
     * 获取活动任务列表
     * 
     * @return 活动任务的 Flow
     */
    fun getActiveTasks(): Flow<List<TransferTask>> {
        return taskDao.getActiveTasks()
            .map { entities -> entities.map { it.toModel() } }
    }
    
    /**
     * 根据类型获取活动任务
     * 
     * @param type 传输类型
     * @return 指定类型的活动任务 Flow
     */
    fun getActiveTasksByType(type: TransferType): Flow<List<TransferTask>> {
        return taskDao.getActiveTasksByType(type.name)
            .map { entities -> entities.map { it.toModel() } }
    }
    
    /**
     * 获取已完成任务列表
     * 
     * @return 已完成任务的 Flow
     */
    fun getCompletedTasks(): Flow<List<TransferTask>> {
        return taskDao.getCompletedTasks()
            .map { entities -> entities.map { it.toModel() } }
    }
    
    /**
     * 清空所有已完成的任务
     */
    suspend fun clearCompletedTasks() {
        taskDao.deleteAllCompletedTasks()
    }
    
    /**
     * 通知传输服务执行操作
     * 用于暂停、恢复、取消等操作
     */
    private fun notifyTransferService(action: String, taskId: String) {
        val intent = Intent(context, com.qi.smbshare.service.TransferService::class.java).apply {
            this.action = action
            putExtra(com.qi.smbshare.service.TransferService.EXTRA_TASK_ID, taskId)
        }

        context.startForegroundService(intent)
    }
    
    /**
     * 启动传输服务执行指定任务
     */
    private fun startTransferService(taskId: String, config: SMBConfig) {
        val intent = Intent(context, com.qi.smbshare.service.TransferService::class.java).apply {
            action = com.qi.smbshare.service.TransferService.ACTION_START_TRANSFER
            putExtra(com.qi.smbshare.service.TransferService.EXTRA_TASK_ID, taskId)
            putExtra(com.qi.smbshare.service.TransferService.EXTRA_CONFIG, config.toJsonString())
        }

        context.startForegroundService(intent)
    }
}
