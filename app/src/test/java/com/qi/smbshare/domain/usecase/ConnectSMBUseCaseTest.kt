package com.qi.smbshare.domain.usecase

import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 针对 SMB 连接用例的行为校验，确保异常在业务层被正确包装。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectSMBUseCaseTest {

    private lateinit var connectionManager: SMBConnectionManager
    private lateinit var useCase: ConnectSMBUseCase
    private lateinit var config: SMBConfig

    @Before
    fun setUp() {
        connectionManager = mockk(relaxed = true)
        useCase = ConnectSMBUseCase(connectionManager)
        config = SMBConfig(
            serverAddress = "192.168.0.100",
            shareName = "share",
            username = "user",
            password = "pass"
        )
    }

    @Test
    fun `execute success returns Result#success`() = runTest {
        coEvery { connectionManager.connect(config) } returns mockk<DiskShare>()

        val result = useCase.execute(config)

        assertTrue(result.isSuccess)
        coVerify { connectionManager.connect(config) }
    }

    @Test
    fun `execute io failure propagates as failure result`() = runTest {
        coEvery { connectionManager.connect(config) } throws IOException("io error")

        val result = useCase.execute(config)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `unexpected exception will be wrapped as IOException`() = runTest {
        coEvery { connectionManager.connect(config) } throws IllegalStateException("boom")

        val result = useCase.execute(config)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `testConnection success preserves positive result`() = runTest {
        coEvery { connectionManager.testConnection(config) } returns true

        val result = useCase.testConnection(config)

        assertTrue(result.getOrThrow())
    }

    @Test
    fun `testConnection failure returns failure result`() = runTest {
        coEvery { connectionManager.testConnection(config) } throws IOException("network down")

        val result = useCase.testConnection(config)

        assertTrue(result.isFailure)
        assertFalse(result.exceptionOrNull()?.message.isNullOrEmpty())
    }
}
