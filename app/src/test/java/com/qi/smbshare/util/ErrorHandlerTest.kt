package com.qi.smbshare.util

import com.qi.smbshare.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 验证 ErrorHandler 将各类异常正确归类为对应错误类型，并准确判断可重试性。
 */
class ErrorHandlerTest {

    // ==================== handleException 分类测试 ====================

    @Test
    fun `SocketTimeoutException 归类为 NetworkError`() {
        val error = ErrorHandler.handleException(SocketTimeoutException("timeout"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
        assertTrue(error.type == ErrorHandler.AppErrorType.NETWORK)
    }

    @Test
    fun `UnknownHostException 归类为 NetworkError`() {
        val error = ErrorHandler.handleException(UnknownHostException("host not found"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
    }

    @Test
    fun `FileNotFoundException 归类为 FileOperationError`() {
        val error = ErrorHandler.handleException(FileNotFoundException("file.txt"))
        assertTrue(error is ErrorHandler.AppError.FileOperationError)
    }

    @Test
    fun `SecurityException 归类为 PermissionError`() {
        val error = ErrorHandler.handleException(SecurityException("no permission"))
        assertTrue(error is ErrorHandler.AppError.PermissionError)
    }

    @Test
    fun `IOException 含 timeout 关键字时归类为 NetworkError`() {
        val error = ErrorHandler.handleException(IOException("connection timeout"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
    }

    @Test
    fun `IOException 含 Authentication 关键字时归类为 AuthenticationError`() {
        val error = ErrorHandler.handleException(IOException("Authentication failed"))
        assertTrue(error is ErrorHandler.AppError.AuthenticationError)
        assertTrue(error.type == ErrorHandler.AppErrorType.AUTHENTICATION)
    }

    @Test
    fun `IOException 含 password 关键字时归类为 AuthenticationError`() {
        val error = ErrorHandler.handleException(IOException("incorrect password"))
        assertTrue(error is ErrorHandler.AppError.AuthenticationError)
    }

    @Test
    fun `IOException 包装 SocketTimeoutException 归类为 NetworkError`() {
        val cause = SocketTimeoutException("timed out")
        val error = ErrorHandler.handleException(IOException("wrapped", cause))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
    }

    @Test
    fun `IOException 包装 UnknownHostException 归类为 NetworkError`() {
        val cause = UnknownHostException("no address")
        val error = ErrorHandler.handleException(IOException("wrapped", cause))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
    }

    @Test
    fun `IOException 含 refused 关键字时归类为 NetworkError`() {
        val error = ErrorHandler.handleException(IOException("connection refused"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
    }

    @Test
    fun `IOException 含未连接关键字时归类为 NetworkError`() {
        val error = ErrorHandler.handleException(IOException("未连接到SMB服务器"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
        assertTrue(error.type == ErrorHandler.AppErrorType.NETWORK)
    }

    @Test
    fun `IOException 含连接失败关键字时归类为 NetworkError`() {
        val error = ErrorHandler.handleException(IOException("连接SMB服务器失败: closed"))
        assertTrue(error is ErrorHandler.AppError.NetworkError)
        assertTrue(error.type == ErrorHandler.AppErrorType.NETWORK)
    }

    @Test
    fun `未知 RuntimeException 归类为 UnknownError`() {
        val error = ErrorHandler.handleException(RuntimeException("unexpected boom"))
        assertTrue(error is ErrorHandler.AppError.UnknownError)
    }

    // ==================== isRetryable 测试 ====================

    @Test
    fun `NetworkError 可重试`() {
        assertTrue(ErrorHandler.isRetryable(ErrorHandler.AppError.NetworkError(0, "net")))
    }

    @Test
    fun `AuthenticationError 不可重试`() {
        assertFalse(ErrorHandler.isRetryable(ErrorHandler.AppError.AuthenticationError(0, "auth")))
    }

    @Test
    fun `PermissionError 不可重试`() {
        assertFalse(ErrorHandler.isRetryable(ErrorHandler.AppError.PermissionError(0, "perm")))
    }

    @Test
    fun `UnknownError 不可重试`() {
        assertFalse(ErrorHandler.isRetryable(ErrorHandler.AppError.UnknownError("unknown")))
    }

    @Test
    fun `FileOperationError 不含 不存在 时可重试`() {
        assertTrue(ErrorHandler.isRetryable(ErrorHandler.AppError.FileOperationError(0, "写入失败")))
    }

    @Test
    fun `FileOperationError 使用文件不存在资源时不可重试`() {
        assertFalse(
            ErrorHandler.isRetryable(
                ErrorHandler.AppError.FileOperationError(R.string.error_file_not_found, "文件不存在")
            )
        )
    }

    // ==================== 便捷方法测试 ====================

    @Test
    fun `isExceptionRetryable SocketTimeoutException 返回 true`() {
        assertTrue(ErrorHandler.isExceptionRetryable(SocketTimeoutException("timeout")))
    }

    @Test
    fun `isExceptionRetryable SecurityException 返回 false`() {
        assertFalse(ErrorHandler.isExceptionRetryable(SecurityException("denied")))
    }

    @Test
    fun `getErrorMessage 返回非空字符串`() {
        val error = ErrorHandler.handleException(IOException("some io error"))
        val message = ErrorHandler.getErrorMessage(error)
        assertTrue(message.isNotEmpty())
    }
}
