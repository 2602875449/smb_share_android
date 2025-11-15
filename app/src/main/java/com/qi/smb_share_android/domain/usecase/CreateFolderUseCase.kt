package com.qi.smb_share_android.domain.usecase

import android.util.Log
import com.qi.smb_share_android.data.repository.SMBFileRepository
import java.io.IOException

private const val TAG = "CreateFolderUseCase"

class CreateFolderUseCase(
    private val fileRepository: SMBFileRepository
) {
    suspend fun execute(
        folderName: String,
        parentPath: String = ""
    ): Result<Unit> {
        Log.d(TAG, "UseCase: 开始创建文件夹")
        Log.d(TAG, "文件夹名称: $folderName")
        Log.d(TAG, "父路径: $parentPath")
        
        return try {
            fileRepository.createFolder(folderName, parentPath)
            Log.d(TAG, "UseCase: 文件夹创建成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 创建文件夹IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 创建文件夹异常", e)
            val ioException = IOException("创建文件夹失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}

