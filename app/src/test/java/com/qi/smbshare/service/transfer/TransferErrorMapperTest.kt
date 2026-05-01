package com.qi.smbshare.service.transfer

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class TransferErrorMapperTest {

    @Test
    fun `read write mapper should classify timeout`() {
        val exception = TransferErrorMapper.mapReadWriteException(SocketTimeoutException("timeout"))

        assertEquals(TransferErrorType.TIMEOUT_ERROR, exception.errorType)
    }

    @Test
    fun `read write mapper should classify unknown host as network`() {
        val exception = TransferErrorMapper.mapReadWriteException(UnknownHostException("nas"))

        assertEquals(TransferErrorType.NETWORK_ERROR, exception.errorType)
    }

    @Test
    fun `read write mapper should classify connection io as network`() {
        val exception = TransferErrorMapper.mapReadWriteException(IOException("connection reset"))

        assertEquals(TransferErrorType.NETWORK_ERROR, exception.errorType)
    }

    @Test
    fun `read write mapper should classify permission failures as file errors`() {
        val exception = TransferErrorMapper.mapReadWriteException(SecurityException("permission denied"))

        assertEquals(TransferErrorType.FILE_ERROR, exception.errorType)
    }

    @Test
    fun `read write mapper should classify other io as file`() {
        val exception = TransferErrorMapper.mapReadWriteException(IOException("disk full"))

        assertEquals(TransferErrorType.FILE_ERROR, exception.errorType)
    }

    @Test
    fun `unknown mapper should preserve existing transfer exception`() {
        val original = TransferException(TransferErrorType.AUTH_ERROR, "认证失败")

        val mapped = TransferErrorMapper.mapUnknownException(original)

        assertEquals(original, mapped)
    }
}
