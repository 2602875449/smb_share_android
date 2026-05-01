package com.qi.smbshare.service.transfer

import android.content.Context
import android.net.Uri
import com.qi.smbshare.data.model.TransferTask
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class UploadInputStreamProvider(
    private val contentInputStreamOpener: (Uri) -> InputStream?
) {
    constructor(context: Context) : this({ uri ->
        context.contentResolver.openInputStream(uri)
    })

    /**
     * 为上传任务创建输入流，兼容普通文件路径和系统文件选择器返回的 content URI。
     */
    fun open(task: TransferTask): InputStream {
        try {
            return if (task.localPath.startsWith("content://")) {
                val uri = Uri.parse(task.localPath)
                contentInputStreamOpener(uri) ?: throw TransferException(
                    TransferErrorType.FILE_ERROR,
                    "无法读取所选文件"
                )
            } else {
                val localFile = File(task.localPath)
                if (!localFile.exists()) {
                    throw TransferException(
                        TransferErrorType.FILE_ERROR,
                        "本地文件不存在: ${task.localPath}"
                    )
                }

                if (!localFile.canRead()) {
                    throw TransferException(
                        TransferErrorType.FILE_ERROR,
                        "无法读取本地文件: 权限不足"
                    )
                }

                FileInputStream(localFile)
            }
        } catch (e: TransferException) {
            throw e
        } catch (e: SecurityException) {
            throw TransferException(
                TransferErrorType.FILE_ERROR,
                "无法读取本地文件: 权限不足",
                e
            )
        }
    }
}
