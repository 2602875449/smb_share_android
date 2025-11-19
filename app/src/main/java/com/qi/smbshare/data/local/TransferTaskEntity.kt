package com.qi.smbshare.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.util.toJsonString
import com.qi.smbshare.util.toSMBConfigOrNull

/**
 * 传输任务 Room 实体类
 * 添加索引以优化查询性能
 */
@Entity(
    tableName = "transfer_tasks",
    indices = [
        Index(value = ["status"]),      // 按状态查询的索引
        Index(value = ["type"]),        // 按类型查询的索引
        Index(value = ["created_at"])   // 按创建时间排序的索引
    ]
)
data class TransferTaskEntity(
    @PrimaryKey
    val id: String,
    val type: String,                    // 传输类型：DOWNLOAD 或 UPLOAD
    val fileName: String,                // 文件名
    val fileSize: Long,                  // 文件大小（字节）
    val remotePath: String,              // SMB 服务器路径
    val localPath: String,               // 本地文件路径
    val configData: String,              // 配置 JSON
    val status: String,                  // 传输状态
    val progress: Int = 0,               // 进度 0-100
    val transferredBytes: Long = 0,      // 已传输字节数
    val speed: Long = 0,                 // 传输速度（字节/秒）
    val estimatedTimeRemaining: Long = 0, // 预计剩余时间（毫秒）
    val errorMessage: String? = null,    // 错误信息
    val retryCount: Int = 0,             // 重试次数
    val created_at: Long,                // 创建时间（用于索引）
    val startedAt: Long? = null,         // 开始时间
    val completedAt: Long? = null,       // 完成时间
    val lastUpdatedAt: Long              // 最后更新时间
)

/**
 * 将 TransferTask 转换为 TransferTaskEntity
 */
fun TransferTask.toEntity(): TransferTaskEntity {
    return TransferTaskEntity(
        id = id,
        type = type.name,
        fileName = fileName,
        fileSize = fileSize,
        remotePath = remotePath,
        localPath = localPath,
        configData = config.toJsonString(),
        status = status.name,
        progress = progress,
        transferredBytes = transferredBytes,
        speed = speed,
        estimatedTimeRemaining = estimatedTimeRemaining,
        errorMessage = errorMessage,
        retryCount = retryCount,
        created_at = createdAt,
        startedAt = startedAt,
        completedAt = completedAt,
        lastUpdatedAt = lastUpdatedAt
    )
}

/**
 * 将 TransferTaskEntity 转换为 TransferTask
 */
fun TransferTaskEntity.toModel(): TransferTask {
    val config = configData.toSMBConfigOrNull() ?: throw IllegalStateException("无法解析任务配置")
    return TransferTask(
        id = id,
        type = TransferType.valueOf(type),
        fileName = fileName,
        fileSize = fileSize,
        remotePath = remotePath,
        localPath = localPath,
        config = config,
        status = TransferStatus.valueOf(status),
        progress = progress,
        transferredBytes = transferredBytes,
        speed = speed,
        estimatedTimeRemaining = estimatedTimeRemaining,
        errorMessage = errorMessage,
        retryCount = retryCount,
        createdAt = created_at,
        startedAt = startedAt,
        completedAt = completedAt,
        lastUpdatedAt = lastUpdatedAt
    )
}
