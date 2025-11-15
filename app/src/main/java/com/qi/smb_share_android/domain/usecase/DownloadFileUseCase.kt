package com.qi.smb_share_android.domain.usecase

import android.util.Log
import com.qi.smb_share_android.data.repository.DownloadRepository
import com.qi.smb_share_android.data.repository.SMBFileRepository
import java.io.File
import java.io.IOException

private const val TAG = "DownloadFileUseCase"

class DownloadFileUseCase(
    private val fileRepository: SMBFileRepository,
    private val downloadRepository: DownloadRepository
) {
    suspend fun execute(
        filePath: String,
        fileName: String,
        onProgress: (Int, Long, Long) -> Unit
    ): Result<File> {
        Log.d(TAG, "UseCase: 开始下载文件")
        Log.d(TAG, "文件路径: $filePath")
        Log.d(TAG, "文件名: $fileName")
        return try {
            val inputStream = fileRepository.getFileInputStream(filePath)
            Log.d(TAG, "UseCase: 文件输入流获取成功，开始下载")
            val result = downloadRepository.downloadFile(
                inputStream = inputStream,
                fileName = fileName,
                remotePath = filePath,
                onProgress = onProgress
            )
            result.onSuccess {
                Log.d(TAG, "UseCase: 文件下载成功: ${it.absolutePath}")
            }.onFailure {
                Log.e(TAG, "UseCase: 文件下载失败", it)
            }
            result
        } catch (e: IOException) {
            Log.e(TAG, "UseCase: 下载文件IO异常", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "UseCase: 下载文件异常", e)
            val ioException = IOException("下载文件失败: ${e.message}", e)
            Result.failure(ioException)
        }
    }
}

