package com.qi.smbshare.domain.usecase

import android.util.Log
import com.qi.smbshare.data.repository.SMBFileRepository
import java.io.IOException
import javax.inject.Inject

private const val TAG = "RenameFileUseCase"

class RenameFileUseCase @Inject constructor(
    private val fileRepository: SMBFileRepository
) {
    suspend fun execute(
        oldPath: String,
        newName: String
    ): Result<Unit> {
        Log.d(TAG, "UseCase: 开始重命名文件/文件夹")
        Log.d(TAG, "旧路径: $oldPath")
        Log.d(TAG, "新名称: $newName")
        
        return try {
            fileRepository.renameFileOrFolder(oldPath, newName)
            Log.d(TAG, "UseCase: 重命名成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 重命名IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 重命名异常", e)
            val ioException = IOException("重命名失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}
