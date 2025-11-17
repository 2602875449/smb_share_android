package com.qi.smb_share_android.data.model

import java.util.UUID

/**
 * 传输任务数据模型
 * 统一管理上传和下载任务
 */
data class TransferTask(
    val id: String = UUID.randomUUID().toString(),
    val type: TransferType,              // 传输类型：下载或上传
    val fileName: String,                // 文件名
    val fileSize: Long,                  // 文件大小（字节）
    val remotePath: String,              // SMB 服务器路径
    val localPath: String,               // 本地文件路径
    val config: SMBConfig,               // 执行任务所需的连接配置
    val status: TransferStatus,          // 传输状态
    val progress: Int = 0,               // 进度 0-100
    val transferredBytes: Long = 0,      // 已传输字节数
    val speed: Long = 0,                 // 传输速度（字节/秒）
    val estimatedTimeRemaining: Long = 0, // 预计剩余时间（毫秒）
    val errorMessage: String? = null,    // 错误信息
    val retryCount: Int = 0,             // 重试次数
    val createdAt: Long = System.currentTimeMillis(),    // 创建时间
    val startedAt: Long? = null,         // 开始时间
    val completedAt: Long? = null,       // 完成时间
    val lastUpdatedAt: Long = System.currentTimeMillis() // 最后更新时间
)

/**
 * 传输类型枚举
 */
enum class TransferType {
    DOWNLOAD,  // 下载
    UPLOAD     // 上传
}

/**
 * 传输状态枚举
 */
enum class TransferStatus {
    PENDING,    // 等待中
    ACTIVE,     // 进行中
    PAUSED,     // 已暂停
    COMPLETED,  // 已完成
    FAILED,     // 失败
    CANCELLED   // 已取消
}
