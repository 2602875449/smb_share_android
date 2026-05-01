package com.qi.smbshare.service.transfer

import android.content.Context
import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferTask
import kotlinx.coroutines.CancellationException
import com.hierynomus.smbj.share.File as SMBFile

private const val UPLOAD_TAG = "UploadExecutor"

class UploadExecutor(
    context: Context,
    private val streamCopier: TransferStreamCopier,
    private val inputStreamProvider: UploadInputStreamProvider = UploadInputStreamProvider(context),
    private val connectionManagerFactory: () -> SMBConnectionManager = { SMBConnectionManager() }
) {
    suspend fun execute(task: TransferTask, config: SMBConfig) {
        Log.d(UPLOAD_TAG, "开始上传: ${task.fileName}")

        val smbManager = connectionManagerFactory()
        var remoteFile: SMBFile? = null

        try {
            val diskShare = smbManager.connect(config)
            val normalizedPath = TransferPathUtils.normalizeSmbPath(task.remotePath)
            Log.d(UPLOAD_TAG, "打开远程文件用于上传: $normalizedPath")
            remoteFile = diskShare.openFile(
                normalizedPath,
                setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )

            try {
                inputStreamProvider.open(task).use { inputStream ->
                    remoteFile.outputStream.use { outputStream ->
                        streamCopier.copy(
                            inputStream = inputStream,
                            outputStream = outputStream,
                            task = task,
                            direction = TransferDirection.UPLOAD,
                            finalProgress = TransferStreamCopier::uploadFinalProgress
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw TransferErrorMapper.mapReadWriteException(e)
            }

            Log.d(UPLOAD_TAG, "上传完成: ${task.fileName}")
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
                Log.w(UPLOAD_TAG, "关闭远程文件时出错: ${e.message}")
            }
            try {
                smbManager.disconnect()
            } catch (e: Exception) {
                Log.w(UPLOAD_TAG, "断开连接时出错: ${e.message}")
            }
        }
    }
}
