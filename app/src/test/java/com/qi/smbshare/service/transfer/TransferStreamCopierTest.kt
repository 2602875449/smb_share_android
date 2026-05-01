package com.qi.smbshare.service.transfer

import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferStreamCopierTest {

    @Test
    fun `copy should reuse shared progress and final update logic`() = runTest {
        val taskUpdater = FakeTransferTaskUpdater()
        val notifier = RecordingNotifier()
        var currentTime = 0L
        val copier = TransferStreamCopier(
            taskUpdater = taskUpdater,
            transferControl = NoopTransferControl,
            progressNotifier = notifier,
            clock = {
                val value = currentTime
                currentTime += 1000L
                value
            },
            bufferSize = 4,
            progressUpdateIntervalMillis = 1000L
        )
        val task = sampleTask(fileSize = 10)
        val outputStream = ByteArrayOutputStream()

        val totalBytes = copier.copy(
            inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)),
            outputStream = outputStream,
            task = task,
            direction = TransferDirection.DOWNLOAD,
            finalProgress = TransferStreamCopier::downloadFinalProgress
        )

        assertEquals(10L, totalBytes)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), outputStream.toByteArray())
        assertEquals(
            listOf(
                ProgressCall(task.id, 40, 4, 4),
                ProgressCall(task.id, 80, 8, 4),
                ProgressCall(task.id, 100, 10, 2),
                ProgressCall(task.id, 100, 10, 0)
            ),
            taskUpdater.progressCalls
        )
        assertEquals(
            listOf(
                NotificationCall(TransferDirection.DOWNLOAD, 40),
                NotificationCall(TransferDirection.DOWNLOAD, 80),
                NotificationCall(TransferDirection.DOWNLOAD, 100)
            ),
            notifier.calls
        )
    }

    @Test
    fun `upload final progress should clamp transferred bytes to file size`() {
        val snapshot = TransferStreamCopier.uploadFinalProgress(
            transferredBytes = 120,
            fileSize = 100
        )

        assertEquals(100, snapshot.progress)
        assertEquals(100L, snapshot.transferredBytes)
        assertEquals(0L, snapshot.speed)
    }

    @Test
    fun `copy should propagate cancellation without writing failure progress`() = runTest {
        val taskUpdater = FakeTransferTaskUpdater()
        val copier = TransferStreamCopier(
            taskUpdater = taskUpdater,
            transferControl = CancellingTransferControl,
            progressNotifier = RecordingNotifier(),
            bufferSize = 4
        )

        val exception = runCatching {
            copier.copy(
                inputStream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)),
                outputStream = ByteArrayOutputStream(),
                task = sampleTask(fileSize = 4),
                direction = TransferDirection.UPLOAD,
                finalProgress = TransferStreamCopier::uploadFinalProgress
            )
        }.exceptionOrNull()

        assertTrue(exception is CancellationException)
        assertEquals(emptyList<ProgressCall>(), taskUpdater.progressCalls)
    }

    @Test
    fun `default buffer should stay at 256KB`() {
        assertEquals(256 * 1024, TransferStreamCopier.DEFAULT_BUFFER_SIZE)
    }

    private fun sampleTask(fileSize: Long): TransferTask {
        return TransferTask(
            id = "task-1",
            type = TransferType.DOWNLOAD,
            fileName = "sample.bin",
            fileSize = fileSize,
            remotePath = "remote/sample.bin",
            localPath = "/tmp/sample.bin",
            config = SMBConfig(serverAddress = "192.168.0.10", shareName = "share"),
            status = TransferStatus.ACTIVE
        )
    }

    private data class ProgressCall(
        val taskId: String,
        val progress: Int,
        val transferredBytes: Long,
        val speed: Long
    )

    private data class NotificationCall(
        val direction: TransferDirection,
        val progress: Int
    )

    private class FakeTransferTaskUpdater : TransferTaskUpdater {
        val progressCalls = mutableListOf<ProgressCall>()

        override suspend fun updateProgress(
            taskId: String,
            progress: Int,
            transferredBytes: Long,
            speed: Long
        ) {
            progressCalls += ProgressCall(taskId, progress, transferredBytes, speed)
        }

        override suspend fun updateTaskLocalPath(taskId: String, localPath: String) = Unit
    }

    private object NoopTransferControl : TransferControl {
        override suspend fun waitWhilePaused(taskId: String) = Unit
        override fun ensureTaskNotCancelled(taskId: String) = Unit
    }

    private object CancellingTransferControl : TransferControl {
        override suspend fun waitWhilePaused(taskId: String) = Unit

        override fun ensureTaskNotCancelled(taskId: String) {
            throw CancellationException("任务已取消")
        }
    }

    private class RecordingNotifier : TransferProgressNotifier {
        val calls = mutableListOf<NotificationCall>()

        override fun onProgress(task: TransferTask, direction: TransferDirection, progress: Int) {
            calls += NotificationCall(direction, progress)
        }
    }
}
