package com.qi.smb_share_android.domain.usecase

import android.util.Log
import com.qi.smb_share_android.data.repository.ConnectionRepository

private const val TAG = "DeleteConnectionUseCase"

class DeleteConnectionUseCase(private val connectionRepository: ConnectionRepository) {
    suspend fun execute(configId: String): Result<Unit> {
        Log.d(TAG, "UseCase: 开始删除连接配置")
        Log.d(TAG, "配置ID: $configId")
        return try {
            connectionRepository.deleteConfig(configId)
            Log.d(TAG, "UseCase: 连接配置删除成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 删除连接配置失败", e)
            Log.e(TAG, "配置ID: $configId")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            Result.failure(e)
        }
    }
}

