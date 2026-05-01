package com.qi.smbshare.util

import android.content.Context
import androidx.annotation.StringRes
import com.qi.smbshare.R
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 统一的错误处理工具类
 * 负责将异常转换为用户友好的错误消息
 */
object ErrorHandler {

    enum class AppErrorType {
        NETWORK,
        AUTHENTICATION,
        PERMISSION,
        FILE_OPERATION,
        UNKNOWN
    }
    
    /**
     * 应用错误类型
     */
    sealed class AppError(
        val type: AppErrorType,
        @param:StringRes val messageResId: Int,
        val fallbackMessage: String
    ) {
        data class NetworkError(
            @param:StringRes val resId: Int,
            val message: String
        ) : AppError(AppErrorType.NETWORK, resId, message)

        data class AuthenticationError(
            @param:StringRes val resId: Int,
            val message: String
        ) : AppError(AppErrorType.AUTHENTICATION, resId, message)

        data class PermissionError(
            @param:StringRes val resId: Int,
            val message: String
        ) : AppError(AppErrorType.PERMISSION, resId, message)

        data class FileOperationError(
            @param:StringRes val resId: Int,
            val message: String
        ) : AppError(AppErrorType.FILE_OPERATION, resId, message)

        data class UnknownError(
            val detail: String
        ) : AppError(AppErrorType.UNKNOWN, R.string.error_operation_failed, "操作失败，请稍后重试")
    }
    
    /**
     * 将异常转换为 AppError
     * @param exception 捕获的异常
     * @return 对应的 AppError 类型
     */
    fun handleException(exception: Exception): AppError {
        return when (exception) {
            is SocketTimeoutException -> {
                AppError.NetworkError(
                    R.string.error_network_timeout,
                    "连接超时，请检查网络或服务器地址"
                )
            }
            is UnknownHostException -> {
                AppError.NetworkError(
                    R.string.error_network_unknown_host,
                    "无法找到服务器，请检查服务器地址"
                )
            }
            is FileNotFoundException -> {
                AppError.FileOperationError(
                    R.string.error_file_not_found,
                    "文件不存在或已被删除"
                )
            }
            is IOException -> {
                val cause = exception.cause
                val message = exception.message.orEmpty()
                val causeMessage = cause?.message.orEmpty()
                fun containsKeyword(vararg keywords: String): Boolean {
                    return keywords.any { keyword ->
                        message.contains(keyword, ignoreCase = true) ||
                        causeMessage.contains(keyword, ignoreCase = true)
                    }
                }

                // SMBJ 经常把底层网络异常包装成 IOException，这里通过 cause 和关键字还原更准确的错误类型
                when {
                    cause is SocketTimeoutException ||
                    containsKeyword("timeout", "超时") -> {
                        AppError.NetworkError(
                            R.string.error_network_timeout,
                            "连接超时，请检查网络或服务器地址"
                        )
                    }
                    cause is UnknownHostException ||
                    containsKeyword("unknown host", "no address associated", "无法找到服务器") -> {
                        AppError.NetworkError(
                            R.string.error_network_unknown_host,
                            "无法找到服务器，请检查服务器地址"
                        )
                    }
                    containsKeyword("Authentication", "认证", "password", "密码") -> {
                        AppError.AuthenticationError(
                            R.string.error_authentication_failed,
                            "用户名或密码错误"
                        )
                    }
                    containsKeyword("Permission", "权限") -> {
                        AppError.PermissionError(
                            R.string.error_permission_storage,
                            "没有存储权限，无法完成操作"
                        )
                    }
                    containsKeyword(
                        "Network",
                        "网络",
                        "连接SMB服务器失败",
                        "连接失败",
                        "未连接",
                        "closed"
                    ) -> {
                        AppError.NetworkError(
                            R.string.error_network_unavailable,
                            "网络连接不可用，请检查网络设置"
                        )
                    }
                    containsKeyword("failed to connect", "unreachable", "refused") -> {
                        AppError.NetworkError(
                            R.string.error_network_server_unreachable,
                            "无法连接服务器，请检查网络或服务器状态"
                        )
                    }
                    else -> {
                        AppError.FileOperationError(
                            R.string.error_file_operation_failed,
                            "文件操作失败，请稍后重试"
                        )
                    }
                }
            }
            is SecurityException -> {
                AppError.PermissionError(
                    R.string.error_permission_storage,
                    "没有存储权限，无法完成操作"
                )
            }
            else -> {
                // 尝试从异常消息中提取有用信息
                val message = exception.message ?: "未知错误"
                when {
                    message.contains("timeout", ignoreCase = true) ||
                    message.contains("超时", ignoreCase = true) -> {
                        AppError.NetworkError(
                            R.string.error_network_timeout,
                            "连接超时，请检查网络或服务器地址"
                        )
                    }
                    message.contains("authentication", ignoreCase = true) ||
                    message.contains("认证", ignoreCase = true) ||
                    message.contains("password", ignoreCase = true) ||
                    message.contains("密码", ignoreCase = true) -> {
                        AppError.AuthenticationError(
                            R.string.error_authentication_failed,
                            "用户名或密码错误"
                        )
                    }
                    message.contains("permission", ignoreCase = true) ||
                    message.contains("权限", ignoreCase = true) -> {
                        AppError.PermissionError(
                            R.string.error_permission_storage,
                            "没有存储权限，无法完成操作"
                        )
                    }
                    message.contains("network", ignoreCase = true) ||
                    message.contains("网络", ignoreCase = true) ||
                    message.contains("连接SMB服务器失败", ignoreCase = true) ||
                    message.contains("连接失败", ignoreCase = true) ||
                    message.contains("未连接", ignoreCase = true) ||
                    message.contains("closed", ignoreCase = true) -> {
                        AppError.NetworkError(
                            R.string.error_network_unavailable,
                            "网络连接不可用，请检查网络设置"
                        )
                    }
                    message.contains("file", ignoreCase = true) ||
                    message.contains("文件", ignoreCase = true) -> {
                        AppError.FileOperationError(
                            R.string.error_file_operation_failed,
                            "文件操作失败，请稍后重试"
                        )
                    }
                    else -> {
                        AppError.UnknownError(message)
                    }
                }
            }
        }
    }
    
    /**
     * 获取错误的用户友好描述
     * @param error AppError 对象
     * @return 用户友好的错误消息
     */
    fun getErrorMessage(error: AppError): String {
        return when (error) {
            is AppError.NetworkError -> error.fallbackMessage
            is AppError.AuthenticationError -> error.fallbackMessage
            is AppError.PermissionError -> error.fallbackMessage
            is AppError.FileOperationError -> error.fallbackMessage
            is AppError.UnknownError -> error.fallbackMessage
        }
    }

    /**
     * 获取跟随当前语言的用户友好错误文案。
     */
    fun getErrorMessage(context: Context, error: AppError): String {
        return context.getString(error.messageResId)
    }
    
    /**
     * 判断错误是否可重试
     * @param error AppError 对象
     * @return true 如果错误可以重试，false 否则
     */
    fun isRetryable(error: AppError): Boolean {
        return when (error) {
            is AppError.NetworkError -> true  // 网络错误可以重试
            is AppError.FileOperationError -> {
                // 文件操作错误，除了文件不存在，其他可以重试
                error.messageResId != R.string.error_file_not_found
            }
            is AppError.AuthenticationError -> false  // 认证错误需要修改配置，不能直接重试
            is AppError.PermissionError -> false  // 权限错误需要用户授权，不能直接重试
            is AppError.UnknownError -> false  // 未知错误不建议重试
        }
    }
    
    /**
     * 从异常直接获取用户友好的错误消息
     * 这是一个便捷方法，组合了 handleException 和 getErrorMessage
     * 
     * @param exception 捕获的异常
     * @return 用户友好的错误消息
     */
    fun getErrorMessageFromException(exception: Exception): String {
        val error = handleException(exception)
        return getErrorMessage(error)
    }

    /**
     * 从异常获取本地化错误文案。无法归类的异常使用当前操作的兜底提示，避免直接暴露底层异常文本。
     */
    fun getErrorMessageFromException(
        context: Context,
        exception: Exception,
        @StringRes fallbackMessageResId: Int? = null
    ): String {
        val error = handleException(exception)
        val shouldUseOperationFallback = error is AppError.UnknownError ||
            (error is AppError.FileOperationError && error.messageResId == R.string.error_file_operation_failed)

        return if (shouldUseOperationFallback && fallbackMessageResId != null) {
            context.getString(fallbackMessageResId)
        } else {
            getErrorMessage(context, error)
        }
    }
    
    /**
     * 从异常判断是否可重试
     * 这是一个便捷方法，组合了 handleException 和 isRetryable
     * 
     * @param exception 捕获的异常
     * @return true 如果错误可以重试，false 否则
     */
    fun isExceptionRetryable(exception: Exception): Boolean {
        val error = handleException(exception)
        return isRetryable(error)
    }
}
