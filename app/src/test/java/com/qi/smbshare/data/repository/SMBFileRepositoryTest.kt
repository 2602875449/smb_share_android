package com.qi.smbshare.data.repository

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.qi.smbshare.data.local.SMBConnectionManager
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证 SMB 预览/下载读取不会遗留服务端文件句柄，避免后续删除返回共享冲突。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SMBFileRepositoryTest {

    private lateinit var connectionManager: SMBConnectionManager
    private lateinit var diskShare: DiskShare
    private lateinit var repository: SMBFileRepository

    @Before
    fun setUp() {
        connectionManager = SMBConnectionManager()
        diskShare = mockk()
        repository = SMBFileRepository(connectionManager)

        connectionManager.setPrivateField("diskShare", diskShare)
    }

    @Test
    fun `getFileInputStream closes SMBJ file handle when returned stream closes`() {
        val smbFile = mockk<File>()
        val shareAccess = slot<Set<SMB2ShareAccess>>()

        every {
            diskShare.openFile(
                eq("folder\\photo.jpg"),
                any<Set<AccessMask>>(),
                isNull<Set<FileAttributes>>(),
                capture(shareAccess),
                isNull<SMB2CreateDisposition>(),
                isNull<Set<SMB2CreateOptions>>()
            )
        } returns smbFile
        every { smbFile.inputStream } returns ByteArrayInputStream(byteArrayOf(1, 2, 3))
        every { smbFile.close() } just runs

        val stream = repository.getFileInputStream("folder/photo.jpg")

        assertEquals(3, stream.readBytes().size)
        stream.close()
        stream.close()

        assertTrue(SMB2ShareAccess.FILE_SHARE_DELETE in shareAccess.captured)
        verify(exactly = 1) { smbFile.close() }
    }

    @Test
    fun `getFileInputStream closes SMBJ file handle when input stream creation fails`() {
        val smbFile = mockk<File>()

        every {
            diskShare.openFile(
                any(),
                any<Set<AccessMask>>(),
                isNull<Set<FileAttributes>>(),
                any<Set<SMB2ShareAccess>>(),
                isNull<SMB2CreateDisposition>(),
                isNull<Set<SMB2CreateOptions>>()
            )
        } returns smbFile
        every { smbFile.inputStream } throws IllegalStateException("stream failed")
        every { smbFile.close() } just runs

        try {
            repository.getFileInputStream("broken.txt")
            fail("应抛出 IOException")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("打开文件失败"))
        }

        verify(exactly = 1) { smbFile.close() }
    }

    private fun SMBConnectionManager.setPrivateField(name: String, value: Any?) {
        val field = SMBConnectionManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }
}
