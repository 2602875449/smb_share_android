package com.qi.smbshare.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.qi.smbshare.R

/**
 * 权限类型枚举
 */
enum class PermissionType {
    STORAGE_DOWNLOAD,  // 下载文件需要的存储权限
    STORAGE_UPLOAD,    // 上传文件需要的存储权限
    NOTIFICATION       // 前台服务通知权限
}

/**
 * 权限说明对话框
 * 用于向用户解释为什么需要特定权限
 * 
 * @param permissionType 权限类型
 * @param onConfirm 用户点击"允许"按钮的回调
 * @param onDismiss 用户点击"拒绝"按钮或关闭对话框的回调
 */
@Composable
fun PermissionRationaleDialog(
    permissionType: PermissionType,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (icon, titleRes, messageRes) = when (permissionType) {
        PermissionType.STORAGE_DOWNLOAD -> Triple(
            Icons.Default.Download,
            R.string.permission_title_storage_download,
            R.string.permission_message_storage_download
        )
        PermissionType.STORAGE_UPLOAD -> Triple(
            Icons.Default.Upload,
            R.string.permission_title_storage_upload,
            R.string.permission_message_storage_upload
        )
        PermissionType.NOTIFICATION -> Triple(
            Icons.Default.NotificationsActive,
            R.string.permission_title_notification,
            R.string.permission_message_notification
        )
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.permission_button_allow),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.permission_button_deny),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        iconContentColor = MaterialTheme.colorScheme.primary,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 权限永久拒绝对话框
 * 当用户永久拒绝权限后，引导用户前往设置页面
 * 
 * @param permissionType 权限类型
 * @param onOpenSettings 用户点击"前往设置"按钮的回调
 * @param onDismiss 用户点击"取消"按钮或关闭对话框的回调
 */
@Composable
fun PermissionPermanentlyDeniedDialog(
    permissionType: PermissionType,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val messageRes = when (permissionType) {
        PermissionType.STORAGE_DOWNLOAD -> R.string.permission_message_storage_download_denied
        PermissionType.STORAGE_UPLOAD -> R.string.permission_message_storage_upload_denied
        PermissionType.NOTIFICATION -> R.string.permission_message_notification_denied
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(
                text = stringResource(R.string.permission_title_denied),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = stringResource(R.string.permission_button_go_to_settings),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        iconContentColor = MaterialTheme.colorScheme.error,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
