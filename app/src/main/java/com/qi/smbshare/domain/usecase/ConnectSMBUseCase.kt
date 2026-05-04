package com.qi.smbshare.domain.usecase

import android.util.Log
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "ConnectSMBUseCase"

class ConnectSMBUseCase @Inject constructor(
    private val connectionManager: SMBConnectionManager
) {
    suspend fun execute(config: SMBConfig): Result<Unit> {
        Log.d(TAG, "UseCase: 开始执行连接")
        Log.d(TAG, "服务器: ${config.serverAddress}:${config.port}, 共享: ${config.shareName}")
        return try {
            withContext(Dispatchers.IO) {
                connectionManager.connect(config)
            }
            Log.d(TAG, "UseCase: 连接成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 连接IO异常", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 连接异常", e)
            val ioException = IOException("连接失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }

    suspend fun testConnection(config: SMBConfig): Result<Boolean> {
        Log.d(TAG, "UseCase: 开始测试连接")
        return try {
            val result = withContext(Dispatchers.IO) {
                connectionManager.testConnection(config)
            }
            Log.d(TAG, "UseCase: 连接测试完成，结果=$result")
            Result.success(result)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 连接测试IO异常", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 连接测试异常", e)
            Result.failure(IOException("连接测试失败: ${e.message}", e))
        }
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    fun isConnected(): Boolean {
        return connectionManager.isConnected()
    }
}
