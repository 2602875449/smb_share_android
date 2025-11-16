package com.qi.smb_share_android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 权限类型枚举
 */
enum class PermissionType {
    STORAGE_DOWNLOAD,  // 下载文件需要的存储权限
    STORAGE_UPLOAD     // 上传文件需要的存储权限
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
    val (icon, title, message) = when (permissionType) {
        PermissionType.STORAGE_DOWNLOAD -> Triple(
            Icons.Default.Download,
            "需要存储权限",
            "为了将文件下载到您的设备，应用需要访问存储空间的权限。\n\n" +
            "您的文件将保存在 Download 文件夹中，您可以随时查看和管理这些文件。"
        )
        PermissionType.STORAGE_UPLOAD -> Triple(
            Icons.Default.Upload,
            "需要存储权限",
            "为了上传文件到 SMB 服务器，应用需要读取您设备上文件的权限。\n\n" +
            "应用只会读取您选择的文件，不会访问其他文件。"
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
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "允许",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "拒绝",
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
    val message = when (permissionType) {
        PermissionType.STORAGE_DOWNLOAD -> 
            "您已拒绝存储权限，无法下载文件。\n\n" +
            "如需使用下载功能，请前往设置页面手动授予存储权限。"
        PermissionType.STORAGE_UPLOAD -> 
            "您已拒绝存储权限，无法上传文件。\n\n" +
            "如需使用上传功能，请前往设置页面手动授予存储权限。"
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
                text = "权限被拒绝",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text(
                    text = "前往设置",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
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
