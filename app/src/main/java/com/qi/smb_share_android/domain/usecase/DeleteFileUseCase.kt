package com.qi.smb_share_android.domain.usecase

import android.util.Log
import com.qi.smb_share_android.data.repository.SMBFileRepository
import java.io.IOException

private const val TAG = "DeleteFileUseCase"

class DeleteFileUseCase(
    private val fileRepository: SMBFileRepository
) {
    suspend fun execute(path: String): Result<Unit> {
        Log.d(TAG, "UseCase: 开始删除文件/文件夹")
        Log.d(TAG, "路径: $path")
        
        return try {
            fileRepository.deleteFileOrFolder(path)
            Log.d(TAG, "UseCase: 删除成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 删除IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 删除异常", e)
            val ioException = IOException("删除失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}

