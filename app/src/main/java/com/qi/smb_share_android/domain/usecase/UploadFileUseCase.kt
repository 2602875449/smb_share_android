package com.qi.smb_share_android.domain.usecase

import android.util.Log
import com.qi.smb_share_android.data.repository.SMBFileRepository
import java.io.File
import java.io.IOException

private const val TAG = "UploadFileUseCase"

class UploadFileUseCase(
    private val fileRepository: SMBFileRepository
) {
    suspend fun execute(
        localFile: File,
        remotePath: String,
        onProgress: (Long, Long) -> Unit
    ): Result<Unit> {
        Log.d(TAG, "UseCase: 开始上传文件")
        Log.d(TAG, "本地文件: ${localFile.absolutePath}")
        Log.d(TAG, "远程路径: $remotePath")
        
        return try {
            fileRepository.uploadFile(localFile, remotePath, onProgress)
            Log.d(TAG, "UseCase: 文件上传成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 上传文件IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 上传文件异常", e)
            val ioException = IOException("上传文件失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}

