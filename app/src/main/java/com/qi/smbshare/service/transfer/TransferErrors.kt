package com.qi.smbshare.service.transfer

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 传输错误类型，用于区分不同类型的错误并应用不同的重试策略。
 */
enum class TransferErrorType {
    NETWORK_ERROR,
    TIMEOUT_ERROR,
    FILE_ERROR,
    AUTH_ERROR,
    UNKNOWN_ERROR
}

/**
 * 传输异常，包含错误类型和详细信息。
 */
class TransferException(
    val errorType: TransferErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

object TransferErrorMapper {
    fun mapReadWriteException(exception: Exception): TransferException {
        return when (exception) {
            is TransferException -> exception
            is SocketTimeoutException -> TransferException(
                TransferErrorType.TIMEOUT_ERROR,
                "连接超时",
                exception
            )
            is UnknownHostException -> TransferException(
                TransferErrorType.NETWORK_ERROR,
                "无法连接到服务器",
                exception
            )
            is SecurityException -> TransferException(
                TransferErrorType.FILE_ERROR,
                "文件读写权限不足",
                exception
            )
            is IOException -> {
                if (exception.message?.contains("network", ignoreCase = true) == true ||
                    exception.message?.contains("connection", ignoreCase = true) == true
                ) {
                    TransferException(
                        TransferErrorType.NETWORK_ERROR,
                        "网络连接中断",
                        exception
                    )
                } else {
                    TransferException(
                        TransferErrorType.FILE_ERROR,
                        "文件读写错误: ${exception.message}",
                        exception
                    )
                }
            }
            else -> TransferException(
                TransferErrorType.UNKNOWN_ERROR,
                exception.message ?: "未知错误",
                exception
            )
        }
    }

    fun mapUnknownException(exception: Exception): TransferException {
        return if (exception is TransferException) {
            exception
        } else {
            TransferException(
                TransferErrorType.UNKNOWN_ERROR,
                exception.message ?: "未知错误",
                exception
            )
        }
    }
}
