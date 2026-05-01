package com.qi.smbshare.service.transfer

import android.content.Context
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.util.StorageHelper
import kotlinx.coroutines.CancellationException
import com.hierynomus.smbj.share.File as SMBFile

private const val DOWNLOAD_TAG = "DownloadExecutor"

class DownloadExecutor(
    private val context: Context,
    private val taskUpdater: TransferTaskUpdater,
    private val streamCopier: TransferStreamCopier,
    private val connectionProvider: ServiceSmbConnectionProvider
) {
    suspend fun execute(task: TransferTask, config: SMBConfig) {
        Log.d(DOWNLOAD_TAG, "开始下载: ${task.fileName}")

        var remoteFile: SMBFile? = null

        try {
            val diskShare: DiskShare = connectionProvider.acquire(config)
            Log.d(DOWNLOAD_TAG, "SMB 连接成功")

            val fileWriteInfo = StorageHelper.createDownloadFileOutputStream(
                context,
                task.fileName
            ).getOrElse {
                throw TransferException(
                    TransferErrorType.FILE_ERROR,
                    "无法创建文件: ${it.message}",
                    it
                )
            }

            val actualFilePath = resolveDownloadPath(fileWriteInfo)
            if (actualFilePath != task.localPath) {
                Log.d(DOWNLOAD_TAG, "文件路径已更新: ${task.localPath} -> $actualFilePath")
                taskUpdater.updateTaskLocalPath(task.id, actualFilePath)
            }

            val normalizedPath = TransferPathUtils.normalizeSmbPath(task.remotePath)
            Log.d(DOWNLOAD_TAG, "打开远程文件: $normalizedPath")
            remoteFile = diskShare.openFile(
                normalizedPath,
                setOf(AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                null,
                null
            )

            Log.d(DOWNLOAD_TAG, "远程文件打开成功，开始下载数据")

            try {
                fileWriteInfo.outputStream.use { outputStream ->
                    remoteFile.inputStream.use { inputStream ->
                        val totalBytesRead = streamCopier.copy(
                            inputStream = inputStream,
                            outputStream = outputStream,
                            task = task,
                            direction = TransferDirection.DOWNLOAD,
                            finalProgress = TransferStreamCopier::downloadFinalProgress
                        )
                        Log.d(DOWNLOAD_TAG, "下载完成，总字节数: $totalBytesRead")
                    }
                }

                val finalFilePath = resolveFinalDownloadPath(fileWriteInfo, actualFilePath)
                if (finalFilePath != task.localPath && finalFilePath != actualFilePath) {
                    Log.d(DOWNLOAD_TAG, "下载完成后更新文件路径: ${task.localPath} -> $finalFilePath")
                    taskUpdater.updateTaskLocalPath(task.id, finalFilePath)
                } else if (finalFilePath.contains(".pending-")) {
                    Log.w(DOWNLOAD_TAG, "警告：最终路径仍包含 .pending- 前缀: $finalFilePath")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TransferErrorMapper.mapReadWriteException(e)
            }

            Log.d(DOWNLOAD_TAG, "下载完成: ${task.fileName}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: TransferException) {
            throw e
        } catch (e: Exception) {
            throw TransferErrorMapper.mapUnknownException(e)
        } finally {
            try {
                remoteFile?.close()
            } catch (e: Exception) {
                Log.w(DOWNLOAD_TAG, "关闭远程文件时出错: ${e.message}")
            }
            connectionProvider.release(config)
        }
    }

    private fun resolveDownloadPath(fileWriteInfo: StorageHelper.FileWriteInfo): String {
        return if (fileWriteInfo.uri != null) {
            val pathFromUri = StorageHelper.getFilePathFromUri(context, fileWriteInfo.uri)
            pathFromUri ?: fileWriteInfo.uri.toString()
        } else {
            fileWriteInfo.filePath
        }
    }

    private fun resolveFinalDownloadPath(
        fileWriteInfo: StorageHelper.FileWriteInfo,
        actualFilePath: String
    ): String {
        return if (fileWriteInfo.uri != null) {
            val pathAfterFinish = StorageHelper.finishDownloadFile(context, fileWriteInfo.uri)
            if (pathAfterFinish != null) {
                Log.d(DOWNLOAD_TAG, "finishDownloadFile 返回的最终路径: $pathAfterFinish")
                pathAfterFinish
            } else {
                val pathFromUri = StorageHelper.getFilePathFromUri(context, fileWriteInfo.uri)
                pathFromUri ?: fileWriteInfo.uri.toString()
            }
        } else {
            actualFilePath
        }
    }
}
