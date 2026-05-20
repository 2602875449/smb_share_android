package com.qi.smbshare.ui.transfer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qi.smbshare.R
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.util.FileTypeHelper

/**
 * 传输任务项组件
 * 显示单个传输任务的详细信息和操作按钮
 *
 * @param task 传输任务数据
 * @param isMultiSelectMode 是否处于多选模式
 * @param isSelected 是否被选中
 * @param isFileValid 文件是否有效（未被移动或删除）
 * @param onItemClick 点击事件
 * @param onItemLongClick 长按事件
 * @param onPause 暂停操作
 * @param onResume 恢复操作
 * @param onCancel 取消操作
 * @param onRetry 重试操作
 * @param onDelete 删除操作
 * @param onOpenFile 打开文件操作
 * @param onOpenFolder 打开文件夹操作
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransferTaskItem(
    task: TransferTask,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isFileValid: Boolean = true,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onItemClick, onLongClick = onItemLongClick)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface
            )
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 多选复选框 or 传输类型图标
            if (isMultiSelectMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onItemClick() })
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.extraSmall
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (task.type == TransferType.DOWNLOAD) Icons.Default.FileDownload else Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 文件名 + 状态/大小信息
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // 状态标签
                    if (task.status != TransferStatus.COMPLETED) {
                        StatusChip(status = task.status)
                    }
                    // 进行中实时信息
                    if (task.status == TransferStatus.ACTIVE) {
                        Text(
                            text = "${task.progress}%  ${FileTypeHelper.formatFileSize(task.transferredBytes)}/${FileTypeHelper.formatFileSize(task.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.speed > 0) {
                            Text(
                                text = "${FileTypeHelper.formatFileSize(task.speed)}/s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Text(
                            text = FileTypeHelper.formatFileSize(task.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                // 失败信息
                if (task.status == TransferStatus.FAILED && task.errorMessage != null) {
                    Text(
                        text = stringResource(R.string.transfer_error_prefix, task.errorMessage),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 文件失效提示
                if (task.type == TransferType.DOWNLOAD && task.status == TransferStatus.COMPLETED && !isFileValid) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                        Text(
                            text = stringResource(R.string.transfer_file_invalid_message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 操作按钮（右侧，仅非多选模式）
            if (!isMultiSelectMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        TransferStatus.ACTIVE -> IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(R.string.transfer_action_pause), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        TransferStatus.PAUSED -> IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.transfer_action_resume), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        TransferStatus.FAILED -> IconButton(onClick = onRetry) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.transfer_action_retry), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                        TransferStatus.COMPLETED -> if (task.type == TransferType.DOWNLOAD) {
                            OpenFileMenu(onOpenFile = onOpenFile, onOpenFolder = onOpenFolder)
                        }
                        else -> {}
                    }
                    if (task.status in listOf(TransferStatus.ACTIVE, TransferStatus.PAUSED, TransferStatus.PENDING)) {
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Cancel, contentDescription = stringResource(R.string.action_cancel), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (task.status in listOf(TransferStatus.COMPLETED, TransferStatus.FAILED, TransferStatus.CANCELLED)) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // 内嵌进度条（进行中，贴底，无圆角）
        if (task.status == TransferStatus.ACTIVE) {
            LinearProgressIndicator(
                progress = { task.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)))
        }
    }
}

/**
 * 状态标签组件
 * 根据传输状态显示不同颜色和文字的标签
 */
@Composable
private fun StatusChip(status: TransferStatus) {
    val (text, color) = when (status) {
        TransferStatus.PENDING -> stringResource(R.string.transfer_status_pending) to MaterialTheme.colorScheme.onSurfaceVariant
        TransferStatus.ACTIVE -> stringResource(R.string.transfer_status_active) to MaterialTheme.colorScheme.primary
        TransferStatus.PAUSED -> stringResource(R.string.transfer_status_paused) to MaterialTheme.colorScheme.tertiary
        TransferStatus.COMPLETED -> stringResource(R.string.transfer_status_completed) to MaterialTheme.colorScheme.primary
        TransferStatus.FAILED -> stringResource(R.string.transfer_status_failed) to MaterialTheme.colorScheme.error
        TransferStatus.CANCELLED -> stringResource(R.string.transfer_status_cancelled) to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .background(color = color.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 格式化剩余时间
 * 将毫秒转换为易读的时间格式
 */
@Composable
private fun formatRemainingTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    return when {
        seconds < 60 -> stringResource(R.string.transfer_time_seconds, seconds)
        seconds < 3600 -> stringResource(R.string.transfer_time_minutes, seconds / 60)
        else -> stringResource(R.string.transfer_time_hours, seconds / 3600)
    }
}

/**
 * 打开文件/文件夹菜单组件
 * 提供下拉菜单让用户选择打开文件或打开文件夹
 */
@Composable
private fun OpenFileMenu(
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_more_options),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(stringResource(R.string.transfer_action_open_file))
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.onSurface
                ),
                onClick = {
                    expanded = false
                    onOpenFile()
                }
            )

            DropdownMenuItem(
                text = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Text(stringResource(R.string.transfer_action_open_folder))
                    }
                },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.onSurface
                ),
                onClick = {
                    expanded = false
                    onOpenFolder()
                }
            )
        }
    }
}
