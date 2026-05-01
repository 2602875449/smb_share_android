package com.qi.smbshare.domain.usecase

import android.util.Log
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.repository.ConnectionRepository
import javax.inject.Inject

private const val TAG = "SaveConnectionUseCase"

class SaveConnectionUseCase @Inject constructor(
    private val connectionRepository: ConnectionRepository
) {
    suspend fun execute(config: SMBConfig): Result<Unit> {
        Log.d(TAG, "UseCase: 开始保存连接配置")
        Log.d(TAG, "配置ID: ${config.id}, 名称: ${config.name}")
        return try {
            connectionRepository.saveConfig(config)
            Log.d(TAG, "UseCase: 连接配置保存成功")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 保存连接配置失败", e)
            Log.e(TAG, "配置ID: ${config.id}")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            Result.failure(e)
        }
    }
}
