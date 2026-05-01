package com.qi.smbshare.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.MainActivity
import com.qi.smbshare.R
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.data.repository.TransferRepository
import com.qi.smbshare.util.StorageHelper
import com.qi.smbshare.util.toSMBConfigOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import kotlin.coroutines.coroutineContext
import com.hierynomus.smbj.share.File as SMBFile

/**
 * 传输错误类型
 * 用于区分不同类型的错误并应用不同的重试策略
 */
enum class TransferErrorType {
    NETWORK_ERROR,      // 网络错误（连接失败、网络中断等）
    TIMEOUT_ERROR,      // 超时错误
    FILE_ERROR,         // 文件错误（文件不存在、权限不足等）
    AUTH_ERROR,         // 认证错误
    UNKNOWN_ERROR       // 未知错误
}

/**
 * 传输异常
 * 包含错误类型和详细信息
 */
class TransferException(
    val errorType: TransferErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 传输服务
 * 负责在后台执行文件上传和下载任务
 * 使用前台服务确保长时间运行不被系统杀死
 */
class TransferService : Service() {

    companion object {
        private const val TAG = "TransferService"
        private const val NOTIFICATION_CHANNEL_ID = "transfer_service_channel"
        private const val NOTIFICATION_ID = 1001
        
        // Intent Actions
        const val ACTION_START_TRANSFER = "action_start_transfer"
        const val ACTION_PAUSE_TRANSFER = "action_pause_transfer"
        const val ACTION_RESUME_TRANSFER = "action_resume_transfer"
        const val ACTION_CANCEL_TRANSFER = "action_cancel_transfer"
        
        // Intent Extras
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_CONFIG = "extra_config"
        
        // 最大并发传输数量
        private const val MAX_CONCURRENT_TRANSFERS = 3
        
        // 进度更新间隔（毫秒）
        private const val PROGRESS_UPDATE_INTERVAL = 1000L
        
        // 文件传输使用 256KB 缓冲区，减少大文件读写时的系统调用次数，同时避免单任务占用过多内存。
        private const val BUFFER_SIZE = 256 * 1024
        
        // 重试配置
        private const val MAX_NETWORK_ERROR_RETRIES = 3  // 网络错误最大重试次数
        private const val MAX_TIMEOUT_RETRIES = 2        // 超时错误最大重试次数
        private const val RETRY_DELAY_MS = 5000L         // 重试延迟（毫秒）
    }
    
    private lateinit var repository: TransferRepository
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 并发控制信号量，限制最多 3 个并发传输
    private val transferSemaphore = Semaphore(MAX_CONCURRENT_TRANSFERS)
    
    // 活动传输任务的 Job 映射
    private val activeTransferJobs = mutableMapOf<String, Job>()
    
    // 暂停标志映射
    private val pausedTasks = mutableSetOf<String>()

    // 取消标志映射
    private val cancelledTasks = mutableSetOf<String>()
    
    // 任务配置映射（存储每个任务的 SMBConfig）
    private val taskConfigs = mutableMapOf<String, SMBConfig>()
    
    // 网络状态监听
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TransferService onCreate")
        
        repository = TransferRepository(applicationContext)
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        createNotificationChannel()
        // Android 14+ (API 34+) 要求使用三参数版本的 startForeground，传入 FGS Type
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            // Android 13 及以下版本使用两参数版本
            startForeground(NOTIFICATION_ID, createNotification())
        }
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        intent?.let {
            when (it.action) {
                ACTION_START_TRANSFER -> {
                    val taskId = it.getStringExtra(EXTRA_TASK_ID)
                    val configJson = it.getStringExtra(EXTRA_CONFIG)
                    if (taskId != null && configJson != null) {
                        val config = configJson.toSMBConfigOrNull()
                        if (config != null) {
                            startTransfer(taskId, config)
                        } else {
                            Log.e(TAG, "无法解析 SMBConfig")
                        }
                    } else {
                        Log.e(TAG, "缺少必要参数: taskId=$taskId, configJson=${configJson != null}")
                    }
                }
                ACTION_PAUSE_TRANSFER -> {
                    val taskId = it.getStringExtra(EXTRA_TASK_ID)
                    if (taskId != null) {
                        pauseTransfer(taskId)
                    }
                }
                ACTION_RESUME_TRANSFER -> {
                    val taskId = it.getStringExtra(EXTRA_TASK_ID)
                    if (taskId != null) {
                        resumeTransfer(taskId)
                    }
                }
                ACTION_CANCEL_TRANSFER -> {
                    val taskId = it.getStringExtra(EXTRA_TASK_ID)
                    if (taskId != null) {
                        cancelTransfer(taskId)
                    }
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        Log.d(TAG, "TransferService onDestroy")
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.transfer_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.transfer_notification_channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    /**
     * 创建持久通知
     */
    private fun createNotification(
        title: String = getString(R.string.transfer_notification_service_title),
        content: String = getString(R.string.transfer_notification_service_content)
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 更新通知内容
     */
    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 开始传输任务
     */
    private fun startTransfer(taskId: String, config: SMBConfig) {
        // 如果任务已经在运行，不重复启动
        if (activeTransferJobs.containsKey(taskId)) {
            Log.w(TAG, "任务 $taskId 已在运行中")
            return
        }

        cancelledTasks.remove(taskId)
        // 存储配置以便恢复时使用
        taskConfigs[taskId] = config

        val job = serviceScope.launch {
            try {
                // 获取任务信息
                val task = repository.getTaskById(taskId)
                if (task == null) {
                    Log.e(TAG, "任务 $taskId 不存在")
                    return@launch
                }
                
                // 等待获取传输槽位（最多 3 个并发）
                transferSemaphore.acquire()

                try {
                    ensureTaskNotCancelled(taskId)
                    // 更新状态为进行中
                    repository.updateTaskStatus(taskId, TransferStatus.ACTIVE)

                    // 执行传输（带重试机制）
                    executeTransferWithRetry(task, config)

                    // 传输完成
                    if (!pausedTasks.contains(taskId) && !cancelledTasks.contains(taskId)) {
                        repository.updateTaskStatus(taskId, TransferStatus.COMPLETED)
                        Log.d(TAG, "任务 $taskId 完成")
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "任务 $taskId 已中断: ${e.message}")
                } catch (e: TransferException) {
                    Log.e(TAG, "任务 $taskId 失败: ${e.message}", e)
                    val errorMessage = formatErrorMessage(e)
                    repository.updateTaskStatus(
                        taskId,
                        TransferStatus.FAILED,
                        errorMessage
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "任务 $taskId 失败: ${e.message}", e)
                    repository.updateTaskStatus(
                        taskId,
                        TransferStatus.FAILED,
                        getString(R.string.transfer_error_unknown)
                    )
                } finally {
                    transferSemaphore.release()
                    activeTransferJobs.remove(taskId)
                    if (cancelledTasks.remove(taskId)) {
                        pausedTasks.remove(taskId)
                        taskConfigs.remove(taskId)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动任务 $taskId 时出错: ${e.message}", e)
            }
        }
        
        activeTransferJobs[taskId] = job
    }
    
    /**
     * 执行传输任务（带重试机制）
     * 根据错误类型自动重试
     */
    private suspend fun executeTransferWithRetry(task: TransferTask, config: SMBConfig) {
        var networkRetryCount = 0
        var timeoutRetryCount = 0

        while (true) {
            waitWhilePaused(task.id)
            ensureTaskNotCancelled(task.id)

            try {
                // 执行传输
                when (task.type) {
                    TransferType.DOWNLOAD -> executeDownload(task, config)
                    TransferType.UPLOAD -> executeUpload(task, config)
                }
                // 成功完成，退出循环
                return
            } catch (e: TransferException) {
                // 根据错误类型决定是否重试
                val shouldRetry = when (e.errorType) {
                    TransferErrorType.NETWORK_ERROR -> {
                        networkRetryCount++
                        if (networkRetryCount <= MAX_NETWORK_ERROR_RETRIES) {
                            Log.w(TAG, "网络错误，第 $networkRetryCount 次重试: ${e.message}")
                            true
                        } else {
                            Log.e(TAG, "网络错误重试次数已达上限")
                            false
                        }
                    }
                    TransferErrorType.TIMEOUT_ERROR -> {
                        timeoutRetryCount++
                        if (timeoutRetryCount <= MAX_TIMEOUT_RETRIES) {
                            Log.w(TAG, "超时错误，第 $timeoutRetryCount 次重试: ${e.message}")
                            true
                        } else {
                            Log.e(TAG, "超时错误重试次数已达上限")
                            false
                        }
                    }
                    TransferErrorType.FILE_ERROR,
                    TransferErrorType.AUTH_ERROR,
                    TransferErrorType.UNKNOWN_ERROR -> {
                        // 这些错误不重试
                        Log.e(TAG, "不可重试的错误: ${e.errorType} - ${e.message}")
                        false
                    }
                }

                if (!shouldRetry) {
                    throw e
                }

                var waited = 0L
                while (waited < RETRY_DELAY_MS) {
                    waitWhilePaused(task.id)
                    ensureTaskNotCancelled(task.id)
                    delay(200)
                    waited += 200
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 将未知异常转换为 TransferException
                throw TransferException(
                    TransferErrorType.UNKNOWN_ERROR,
                    e.message ?: "未知错误",
                    e
                )
            }
        }
    }
    
    /**
     * 格式化错误消息
     * 将错误类型转换为用户友好的消息
     */
    private fun formatErrorMessage(exception: TransferException): String {
        return when (exception.errorType) {
            TransferErrorType.NETWORK_ERROR -> getString(R.string.transfer_error_network)
            TransferErrorType.TIMEOUT_ERROR -> getString(R.string.transfer_error_timeout)
            TransferErrorType.FILE_ERROR -> getString(R.string.transfer_error_file)
            TransferErrorType.AUTH_ERROR -> getString(R.string.transfer_error_auth)
            TransferErrorType.UNKNOWN_ERROR -> getString(R.string.transfer_error_unknown)
        }
    }
    
    /**
     * 暂停传输任务
     */
    private fun pauseTransfer(taskId: String) {
        Log.d(TAG, "暂停任务: $taskId")
        pausedTasks.add(taskId)

        serviceScope.launch {
            repository.updateTaskStatus(taskId, TransferStatus.PAUSED)
        }
    }
    
    /**
     * 恢复传输任务
     */
    private fun resumeTransfer(taskId: String) {
        Log.d(TAG, "恢复任务: $taskId")
        pausedTasks.remove(taskId)

        if (activeTransferJobs.containsKey(taskId)) {
            return
        }

        val cachedConfig = taskConfigs[taskId]
        if (cachedConfig != null) {
            startTransfer(taskId, cachedConfig)
            return
        }
        
        serviceScope.launch {
            val task = repository.getTaskById(taskId)
            val resolvedConfig = task?.config
            if (resolvedConfig != null) {
                startTransfer(taskId, resolvedConfig)
            } else {
                repository.updateTaskStatus(
                    taskId,
                    TransferStatus.FAILED,
                    "无法恢复任务: 配置信息丢失"
                )
            }
        }
    }
    
    /**
     * 取消传输任务
     */
    private fun cancelTransfer(taskId: String) {
        Log.d(TAG, "取消任务: $taskId")

        cancelledTasks.add(taskId)
        pausedTasks.remove(taskId)

        // 取消 Job
        activeTransferJobs[taskId]?.cancel()
        activeTransferJobs.remove(taskId)

        // 清理配置
        taskConfigs.remove(taskId)

        serviceScope.launch {
            repository.updateTaskStatus(taskId, TransferStatus.CANCELLED)
        }
    }

    /**
     * 暂停期间阻塞传输循环，直到用户恢复或任务被取消
     */
    private suspend fun waitWhilePaused(taskId: String) {
        while (pausedTasks.contains(taskId)) {
            ensureTaskNotCancelled(taskId)
            coroutineContext.ensureActive()
            delay(100)
        }
    }

    /**
     * 如果任务已取消，立即中断当前协程，避免错误地写成失败状态
     */
    private fun ensureTaskNotCancelled(taskId: String) {
        if (cancelledTasks.contains(taskId)) {
            throw CancellationException("任务已取消")
        }
    }

    /**
     * 执行下载任务
     */
    private suspend fun executeDownload(task: TransferTask, config: SMBConfig) {
        Log.d(TAG, "开始下载: ${task.fileName}")
        
        val smbManager = SMBConnectionManager()
        var diskShare: DiskShare? = null
        var remoteFile: SMBFile? = null
        
        try {
            // 连接到 SMB 服务器
            diskShare = smbManager.connect(config)
            Log.d(TAG, "SMB 连接成功")
            
            // 使用 StorageHelper 创建文件输出流（兼容 Android 10+）
            val fileWriteInfo = StorageHelper.createDownloadFileOutputStream(
                applicationContext,
                task.fileName
            ).getOrElse {
                throw TransferException(
                    TransferErrorType.FILE_ERROR,
                    "无法创建文件: ${it.message}",
                    it
                )
            }
            
            // 如果实际路径与任务路径不同，更新任务路径
            // 优先使用 URI 格式路径（Android 10+），如果不可用则使用文件路径
            val actualFilePath = if (fileWriteInfo.uri != null) {
                // Android 10+：优先使用 URI 格式，如果无法获取文件路径则使用 URI
                val pathFromUri = StorageHelper.getFilePathFromUri(applicationContext, fileWriteInfo.uri)
                pathFromUri ?: fileWriteInfo.uri.toString()
            } else {
                fileWriteInfo.filePath
            }
            
            if (actualFilePath != task.localPath) {
                Log.d(TAG, "文件路径已更新: ${task.localPath} -> $actualFilePath")
                // 更新任务中的本地路径
                repository.updateTaskLocalPath(task.id, actualFilePath)
            }
            
            // 规范化远程路径
            val normalizedPath = normalizePath(task.remotePath)
            Log.d(TAG, "打开远程文件: $normalizedPath")
            
            // 打开远程文件进行读取
            remoteFile = diskShare.openFile(
                normalizedPath,
                setOf(AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                null,
                null
            )
            
            Log.d(TAG, "远程文件打开成功，开始下载数据")
            
            // 使用 StorageHelper 创建的 OutputStream 写入本地文件
            try {
                fileWriteInfo.outputStream.use { outputStream ->
                    remoteFile.inputStream.use { inputStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        var lastUpdateTime = System.currentTimeMillis()
                        var lastBytesRead = 0L
                        
                        // 读取并写入数据
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            waitWhilePaused(task.id)
                            ensureTaskNotCancelled(task.id)
                            coroutineContext.ensureActive()
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            // 每秒更新一次进度
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL) {
                                val progress = if (task.fileSize > 0) {
                                    ((totalBytesRead * 100) / task.fileSize).toInt().coerceIn(0, 100)
                                } else {
                                    0
                                }
                                val speed = if (currentTime > lastUpdateTime) {
                                    ((totalBytesRead - lastBytesRead) * 1000) / (currentTime - lastUpdateTime)
                                } else {
                                    0
                                }
                                
                                repository.updateProgress(task.id, progress, totalBytesRead, speed)
                                updateNotification(
                                    getString(R.string.transfer_notification_downloading),
                                    getString(R.string.transfer_notification_progress, task.fileName, progress)
                                )
                                
                                lastUpdateTime = currentTime
                                lastBytesRead = totalBytesRead
                            }
                        }
                        
                        // 最终进度更新
                        val finalProgress = if (task.fileSize in 1..totalBytesRead) {
                            100
                        } else if (task.fileSize > 0) {
                            ((totalBytesRead * 100) / task.fileSize).toInt().coerceIn(0, 100)
                        } else {
                            100
                        }
                        repository.updateProgress(task.id, finalProgress, totalBytesRead, 0)
                        Log.d(TAG, "下载完成，总字节数: $totalBytesRead")
                    }
                }
                
                // Android 10+ 需要完成文件写入（将 IS_PENDING 设置为 0）
                // finishDownloadFile 会返回更新后的文件路径（去除 .pending- 前缀）
                val finalFilePath = if (fileWriteInfo.uri != null) {
                    // Android 10+：调用 finishDownloadFile 获取最终路径
                    val pathAfterFinish = StorageHelper.finishDownloadFile(applicationContext, fileWriteInfo.uri)
                    if (pathAfterFinish != null) {
                        Log.d(TAG, "finishDownloadFile 返回的最终路径: $pathAfterFinish")
                        pathAfterFinish
                    } else {
                        // 如果 finishDownloadFile 返回 null，尝试重新查询
                        val pathFromUri = StorageHelper.getFilePathFromUri(applicationContext, fileWriteInfo.uri)
                        pathFromUri ?: fileWriteInfo.uri.toString()
                    }
                } else {
                    actualFilePath
                }
                
                // 确保更新任务路径为最终路径（去除 .pending- 前缀）
                if (finalFilePath != task.localPath && finalFilePath != actualFilePath) {
                    Log.d(TAG, "下载完成后更新文件路径: ${task.localPath} -> $finalFilePath")
                    repository.updateTaskLocalPath(task.id, finalFilePath)
                } else if (finalFilePath.contains(".pending-")) {
                    Log.w(TAG, "警告：最终路径仍包含 .pending- 前缀: $finalFilePath")
                }
            } catch (e: java.net.SocketTimeoutException) {
                throw TransferException(
                    TransferErrorType.TIMEOUT_ERROR,
                    "连接超时",
                    e
                )
            } catch (e: java.net.UnknownHostException) {
                throw TransferException(
                    TransferErrorType.NETWORK_ERROR,
                    "无法连接到服务器",
                    e
                )
            } catch (e: java.io.IOException) {
                // 判断是否为网络相关的 IO 错误
                if (e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true) {
                    throw TransferException(
                        TransferErrorType.NETWORK_ERROR,
                        "网络连接中断",
                        e
                    )
                } else {
                    throw TransferException(
                        TransferErrorType.FILE_ERROR,
                        "文件读写错误: ${e.message}",
                        e
                    )
                }
            }
            
            Log.d(TAG, "下载完成: ${task.fileName}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransferException) {
            // 直接抛出 TransferException
            throw e
        } catch (e: Exception) {
            // 将其他异常转换为 TransferException
            throw TransferException(
                TransferErrorType.UNKNOWN_ERROR,
                e.message ?: "未知错误",
                e
            )
        } finally {
            try {
                remoteFile?.close()
            } catch (e: Exception) {
                Log.w(TAG, "关闭远程文件时出错: ${e.message}")
            }
            try {
                smbManager.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "断开连接时出错: ${e.message}")
            }
        }
    }
    
    /**
     * 规范化 SMB 路径，统一使用反斜杠并移除多余前缀。
     */
    private fun normalizePath(path: String): String {
        return path
            .replace("/", "\\")
            .trimStart('\\')
    }
    
    /**
     * 执行上传任务
     */
    private suspend fun executeUpload(task: TransferTask, config: SMBConfig) {
        Log.d(TAG, "开始上传: ${task.fileName}")
        
        val smbManager = SMBConnectionManager()
        var remoteFile: SMBFile? = null
        
        try {
            // 连接到 SMB 服务器
            val diskShare = smbManager.connect(config)
            val normalizedPath = normalizePath(task.remotePath)
            Log.d(TAG, "打开远程文件用于上传: $normalizedPath")
            remoteFile = diskShare.openFile(
                normalizedPath,
                setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )

            // 使用 URI/InputStream 直接上传，避免先复制到缓存目录造成额外 IO 和空间占用
            try {
                openTaskInputStream(task).use { inputStream ->
                    remoteFile.outputStream.use { outputStream ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    var lastUpdateTime = System.currentTimeMillis()
                    var lastBytesRead = 0L
                    
                    // 读取并写入数据
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        waitWhilePaused(task.id)
                        ensureTaskNotCancelled(task.id)
                        coroutineContext.ensureActive()

                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // 每秒更新一次进度
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= PROGRESS_UPDATE_INTERVAL) {
                            val progress = if (task.fileSize > 0) {
                                ((totalBytesRead * 100) / task.fileSize).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            val speed = if (currentTime > lastUpdateTime) {
                                ((totalBytesRead - lastBytesRead) * 1000) / (currentTime - lastUpdateTime)
                            } else {
                                0
                            }
                            
                            repository.updateProgress(task.id, progress, totalBytesRead, speed)
                            updateNotification(
                                getString(R.string.transfer_notification_uploading),
                                getString(R.string.transfer_notification_progress, task.fileName, progress)
                            )
                            
                            lastUpdateTime = currentTime
                            lastBytesRead = totalBytesRead
                        }
                    }
                    
                    // 最终进度更新
                        val finalTransferredBytes = if (task.fileSize > 0) {
                            totalBytesRead.coerceAtMost(task.fileSize)
                        } else {
                            totalBytesRead
                        }
                        repository.updateProgress(task.id, 100, finalTransferredBytes, 0)
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                throw TransferException(
                    TransferErrorType.TIMEOUT_ERROR,
                    "连接超时",
                    e
                )
            } catch (e: java.net.UnknownHostException) {
                throw TransferException(
                    TransferErrorType.NETWORK_ERROR,
                    "无法连接到服务器",
                    e
                )
            } catch (e: java.io.IOException) {
                // 判断是否为网络相关的 IO 错误
                if (e.message?.contains("network", ignoreCase = true) == true ||
                    e.message?.contains("connection", ignoreCase = true) == true) {
                    throw TransferException(
                        TransferErrorType.NETWORK_ERROR,
                        "网络连接中断",
                        e
                    )
                } else {
                    throw TransferException(
                        TransferErrorType.FILE_ERROR,
                        "文件读写错误: ${e.message}",
                        e
                    )
                }
            }
            
            Log.d(TAG, "上传完成: ${task.fileName}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransferException) {
            // 直接抛出 TransferException
            throw e
        } catch (e: Exception) {
            // 将其他异常转换为 TransferException
            throw TransferException(
                TransferErrorType.UNKNOWN_ERROR,
                e.message ?: "未知错误",
                e
            )
        } finally {
            try {
                remoteFile?.close()
            } catch (e: Exception) {
                Log.w(TAG, "关闭远程文件时出错: ${e.message}")
            }
            try {
                smbManager.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "断开连接时出错: ${e.message}")
            }
        }
    }

    /**
     * 为上传任务创建输入流，兼容普通文件路径和系统文件选择器返回的 content URI。
     */
    private fun openTaskInputStream(task: TransferTask): InputStream {
        return if (task.localPath.startsWith("content://")) {
            val uri = Uri.parse(task.localPath)
            applicationContext.contentResolver.openInputStream(uri) ?: throw TransferException(
                TransferErrorType.FILE_ERROR,
                "无法读取所选文件"
            )
        } else {
            val localFile = File(task.localPath)
            if (!localFile.exists()) {
                throw TransferException(
                    TransferErrorType.FILE_ERROR,
                    "本地文件不存在: ${task.localPath}"
                )
            }

            if (!localFile.canRead()) {
                throw TransferException(
                    TransferErrorType.FILE_ERROR,
                    "无法读取本地文件: 权限不足"
                )
            }

            FileInputStream(localFile)
        }
    }

    /**
     * 注册网络状态监听
     * 监听 WiFi 连接状态变化，因为 SMB 通常在局域网内使用
     * 当 WiFi 断开或切换时暂停传输，避免在移动网络下继续传输
     */
    private fun registerNetworkCallback() {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // 检查是否还有其他可用的网络连接
                val activeNetwork = connectivityManager.activeNetwork
                val capabilities = activeNetwork?.let { 
                    connectivityManager.getNetworkCapabilities(it) 
                }
                
                // 如果没有 WiFi 连接了，暂停所有传输
                val hasWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                
                if (!hasWifi) {
                    Log.w(TAG, "WiFi 连接丢失，暂停所有传输")
                    serviceScope.launch {
                        // 暂停所有活动任务
                        activeTransferJobs.keys.forEach { taskId ->
                            pauseTransfer(taskId)
                        }
                        updateNotification(
                            getString(R.string.transfer_notification_wifi_lost_title),
                            getString(R.string.transfer_notification_wifi_lost_content)
                        )
                    }
                }
            }
            
            override fun onAvailable(network: Network) {
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                
                if (isWifi) {
                    Log.d(TAG, "WiFi 连接可用")
                    serviceScope.launch {
                        // 通知用户 WiFi 已连接
                        updateNotification(
                            getString(R.string.transfer_notification_wifi_available_title),
                            getString(R.string.transfer_notification_wifi_available_content)
                        )
                        // 注意：不自动恢复传输，因为可能切换到了不同的 WiFi 网络
                        // 需要用户确认是否在同一局域网内
                    }
                }
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                // 监听网络能力变化，例如从 WiFi 切换到移动网络
                val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                val hasCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                
                if (!hasWifi && hasCellular) {
                    // 从 WiFi 切换到移动网络，暂停传输
                    Log.w(TAG, "网络从 WiFi 切换到移动网络，暂停所有传输")
                    serviceScope.launch {
                        activeTransferJobs.keys.forEach { taskId ->
                            pauseTransfer(taskId)
                        }
                        updateNotification(
                            getString(R.string.transfer_notification_cellular_title),
                            getString(R.string.transfer_notification_cellular_content)
                        )
                    }
                }
            }
        }
        
        // 监听 WiFi 网络变化
        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    
    /**
     * 取消注册网络状态监听
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(TAG, "取消注册网络监听失败: ${e.message}")
            }
        }
    }
}
