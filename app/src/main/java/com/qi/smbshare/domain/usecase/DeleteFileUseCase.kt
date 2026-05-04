package com.qi.smbshare.domain.usecase

import android.util.Log
import com.qi.smbshare.data.repository.SMBFileRepository
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "DeleteFileUseCase"

class DeleteFileUseCase @Inject constructor(
    private val fileRepository: SMBFileRepository
) {
    suspend fun execute(path: String): Result<Unit> {
        Log.d(TAG, "UseCase: 开始删除文件/文件夹")
        Log.d(TAG, "路径: $path")
        
        return try {
            withContext(Dispatchers.IO) {
                fileRepository.deleteFileOrFolder(path)
            }
            Log.d(TAG, "UseCase: 删除成功")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 删除IO异常", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 删除异常", e)
            val ioException = IOException("删除失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}
