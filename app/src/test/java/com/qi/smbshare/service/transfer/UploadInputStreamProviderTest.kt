package com.qi.smbshare.service.transfer

import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UploadInputStreamProviderTest {

    @Test
    fun `open should read normal local file`() {
        val tempFile = File.createTempFile("upload-provider", ".txt")
        tempFile.writeText("hello")

        try {
            val provider = UploadInputStreamProvider { null }

            val bytes = provider.open(sampleTask(localPath = tempFile.absolutePath)).use {
                it.readBytes()
            }

            assertEquals("hello", bytes.decodeToString())
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `open should fail when local file does not exist`() {
        val provider = UploadInputStreamProvider { null }

        val exception = runCatching {
            provider.open(sampleTask(localPath = "/tmp/not-exist-upload-file.bin"))
        }.exceptionOrNull()

        assertTrue(exception is TransferException)
        assertEquals(TransferErrorType.FILE_ERROR, (exception as TransferException).errorType)
    }

    @Test
    fun `open should fail when content uri returns null stream`() {
        val provider = UploadInputStreamProvider { null }

        val exception = runCatching {
            provider.open(sampleTask(localPath = "content://com.qi.smbshare.test/file/1"))
        }.exceptionOrNull()

        assertTrue(exception is TransferException)
        assertEquals(TransferErrorType.FILE_ERROR, (exception as TransferException).errorType)
    }

    @Test
    fun `open should fail with file error when content uri has no permission`() {
        val provider = UploadInputStreamProvider {
            throw SecurityException("permission denied")
        }

        val exception = runCatching {
            provider.open(sampleTask(localPath = "content://com.qi.smbshare.test/file/permission"))
        }.exceptionOrNull()

        assertTrue(exception is TransferException)
        assertEquals(TransferErrorType.FILE_ERROR, (exception as TransferException).errorType)
    }

    @Test
    fun `open should use content uri opener`() {
        val provider = UploadInputStreamProvider {
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }

        val bytes = provider.open(
            sampleTask(localPath = "content://com.qi.smbshare.test/file/2")
        ).use {
            it.readBytes()
        }

        assertEquals(listOf(1, 2, 3), bytes.map { it.toInt() })
    }

    private fun sampleTask(localPath: String): TransferTask {
        return TransferTask(
            id = "upload-task",
            type = TransferType.UPLOAD,
            fileName = "sample.txt",
            fileSize = 3,
            remotePath = "remote/sample.txt",
            localPath = localPath,
            config = SMBConfig(serverAddress = "192.168.0.10", shareName = "share"),
            status = TransferStatus.ACTIVE
        )
    }
}
