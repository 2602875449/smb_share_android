package com.qi.smbshare.domain.usecase

import android.util.Log
import com.qi.smbshare.data.model.FileItem
import com.qi.smbshare.data.repository.SMBFileRepository
import java.io.IOException

private const val TAG = "ListFilesUseCase"

class ListFilesUseCase(private val fileRepository: SMBFileRepository) {
    suspend fun execute(path: String = ""): Result<List<FileItem>> {
        Log.d(TAG, "UseCase: 开始获取文件列表，路径: $path")
        return try {
            val files = fileRepository.listFiles(path)
            Log.d(TAG, "UseCase: 获取文件列表成功，共 ${files.size} 个文件/文件夹")
            Result.success(files)
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 获取文件列表IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 获取文件列表异常", e)
            val ioException = IOException("获取文件列表失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}

