package com.qi.smb_share_android.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 统一的错误提示 Snackbar 组件
 * 
 * @param error 错误消息，null 表示没有错误
 * @param onDismiss 关闭 Snackbar 时的回调
 * @param onRetry 可选的重试回调，如果提供则显示重试按钮
 * @param snackbarHostState Snackbar 的状态管理器
 * @param modifier 修饰符
 */
@Composable
fun ErrorSnackbar(
    error: String?,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    // 当错误消息变化时显示 Snackbar
    LaunchedEffect(error) {
        if (error != null) {
            val result = snackbarHostState.showSnackbar(
                message = error,
                actionLabel = if (onRetry != null) "重试" else null,
                duration = if (onRetry != null) {
                    SnackbarDuration.Indefinite  // 有重试按钮时不自动消失
                } else {
                    SnackbarDuration.Long  // 至少 3 秒
                },
                withDismissAction = true
            )
            
            // 处理用户操作
            when (result) {
                androidx.compose.material3.SnackbarResult.ActionPerformed -> {
                    // 用户点击了重试按钮
                    onRetry?.invoke()
                }
                androidx.compose.material3.SnackbarResult.Dismissed -> {
                    // Snackbar 被关闭
                    onDismiss()
                }
            }
        }
    }
    
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            actionColor = MaterialTheme.colorScheme.error,
            dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

/**
 * 简化版的错误提示组件
 * 直接显示错误消息，不需要 SnackbarHost
 * 
 * 使用示例：
 * ```
 * Box(modifier = Modifier.fillMaxSize()) {
 *     // 主要内容
 *     
 *     // 错误提示
 *     if (errorMessage != null) {
 *         SimpleErrorSnackbar(
 *             message = errorMessage,
 *             onDismiss = { errorMessage = null },
 *             onRetry = { /* 重试逻辑 */ },
 *             modifier = Modifier.align(Alignment.BottomCenter)
 *         )
 *     }
 * }
 * ```
 */
@Composable
fun SimpleErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier,
        action = if (onRetry != null) {
            {
                TextButton(onClick = {
                    onRetry()
                    onDismiss()
                }) {
                    Text(
                        text = "重试",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else null,
        dismissAction = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "关闭",
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        actionContentColor = MaterialTheme.colorScheme.error,
        dismissActionContentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(text = message)
    }
}
