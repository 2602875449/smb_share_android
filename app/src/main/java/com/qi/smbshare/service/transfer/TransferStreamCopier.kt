package com.qi.smbshare.service.transfer

import com.qi.smbshare.data.model.TransferTask
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

data class TransferProgressSnapshot(
    val progress: Int,
    val transferredBytes: Long,
    val speed: Long
)

class TransferStreamCopier(
    private val taskUpdater: TransferTaskUpdater,
    private val transferControl: TransferControl,
    private val progressNotifier: TransferProgressNotifier,
    private val clock: () -> Long = System::currentTimeMillis,
    private val bufferSize: Int = DEFAULT_BUFFER_SIZE,
    private val progressUpdateIntervalMillis: Long = DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS
) {
    suspend fun copy(
        inputStream: InputStream,
        outputStream: OutputStream,
        task: TransferTask,
        direction: TransferDirection,
        finalProgress: (transferredBytes: Long, fileSize: Long) -> TransferProgressSnapshot
    ): Long {
        val buffer = ByteArray(bufferSize)
        var totalBytesRead = 0L
        var lastUpdateTime = clock()
        var lastBytesRead = 0L

        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) break

            transferControl.waitWhilePaused(task.id)
            transferControl.ensureTaskNotCancelled(task.id)
            coroutineContext.ensureActive()

            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead

            val currentTime = clock()
            if (currentTime - lastUpdateTime >= progressUpdateIntervalMillis) {
                val progress = calculateProgress(totalBytesRead, task.fileSize)
                val speed = calculateSpeed(
                    currentBytes = totalBytesRead,
                    previousBytes = lastBytesRead,
                    currentTime = currentTime,
                    previousTime = lastUpdateTime
                )

                taskUpdater.updateProgress(task.id, progress, totalBytesRead, speed)
                progressNotifier.onProgress(task, direction, progress)

                lastUpdateTime = currentTime
                lastBytesRead = totalBytesRead
            }
        }

        val snapshot = finalProgress(totalBytesRead, task.fileSize)
        taskUpdater.updateProgress(
            task.id,
            snapshot.progress,
            snapshot.transferredBytes,
            snapshot.speed
        )
        return totalBytesRead
    }

    companion object {
        const val DEFAULT_PROGRESS_UPDATE_INTERVAL_MILLIS = 1000L
        const val DEFAULT_BUFFER_SIZE = 256 * 1024
        /** 预览场景使用较小缓冲区，避免 ViewModel 内单次分配过大堆内存 */
        const val PREVIEW_BUFFER_SIZE = 64 * 1024

        fun calculateProgress(transferredBytes: Long, fileSize: Long): Int {
            return if (fileSize > 0) {
                ((transferredBytes * 100) / fileSize).toInt().coerceIn(0, 100)
            } else {
                0
            }
        }

        fun calculateSpeed(
            currentBytes: Long,
            previousBytes: Long,
            currentTime: Long,
            previousTime: Long
        ): Long {
            return if (currentTime > previousTime) {
                ((currentBytes - previousBytes) * 1000) / (currentTime - previousTime)
            } else {
                0
            }
        }

        fun downloadFinalProgress(
            transferredBytes: Long,
            fileSize: Long
        ): TransferProgressSnapshot {
            val progress = if (fileSize in 1..transferredBytes) {
                100
            } else if (fileSize > 0) {
                calculateProgress(transferredBytes, fileSize)
            } else {
                100
            }
            return TransferProgressSnapshot(progress, transferredBytes, 0)
        }

        fun uploadFinalProgress(
            transferredBytes: Long,
            fileSize: Long
        ): TransferProgressSnapshot {
            val finalTransferredBytes = if (fileSize > 0) {
                transferredBytes.coerceAtMost(fileSize)
            } else {
                transferredBytes
            }
            return TransferProgressSnapshot(100, finalTransferredBytes, 0)
        }
    }
}
