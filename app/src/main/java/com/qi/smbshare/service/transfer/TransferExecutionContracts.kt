package com.qi.smbshare.service.transfer

import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.repository.TransferRepository

interface TransferTaskUpdater {
    suspend fun updateProgress(
        taskId: String,
        progress: Int,
        transferredBytes: Long,
        speed: Long
    )

    suspend fun updateTaskLocalPath(taskId: String, localPath: String)
}

class RepositoryTransferTaskUpdater(
    private val repository: TransferRepository
) : TransferTaskUpdater {
    override suspend fun updateProgress(
        taskId: String,
        progress: Int,
        transferredBytes: Long,
        speed: Long
    ) {
        repository.updateProgress(taskId, progress, transferredBytes, speed)
    }

    override suspend fun updateTaskLocalPath(taskId: String, localPath: String) {
        repository.updateTaskLocalPath(taskId, localPath)
    }
}

interface TransferControl {
    suspend fun waitWhilePaused(taskId: String)
    fun ensureTaskNotCancelled(taskId: String)
}

enum class TransferDirection {
    DOWNLOAD,
    UPLOAD
}

fun interface TransferProgressNotifier {
    fun onProgress(task: TransferTask, direction: TransferDirection, progress: Int)
}
