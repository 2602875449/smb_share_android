package com.qi.smbshare.ui.transfer

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
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
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：文件信息和操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 多选模式下的复选框
                    if (isMultiSelectMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onItemClick() }
                        )
                    }

                    // 传输类型图标
                    Icon(
                        imageVector = when (task.type) {
                            TransferType.DOWNLOAD -> Icons.Default.FileDownload
                            TransferType.UPLOAD -> Icons.Default.FileUpload
                        },
                        contentDescription = when (task.type) {
                            TransferType.DOWNLOAD -> stringResource(R.string.transfer_type_download)
                            TransferType.UPLOAD -> stringResource(R.string.transfer_type_upload)
                        },
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )

                    // 文件名和基本信息
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.fileName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        // 文件大小
                        Text(
                            text = FileTypeHelper.formatFileSize(task.fileSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // 右侧操作按钮区域
                if (!isMultiSelectMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (task.status) {
                            TransferStatus.ACTIVE -> {
                                // 进行中：显示暂停按钮
                                IconButton(onClick = onPause) {
                                    Icon(
                                        Icons.Default.Pause,
                                        contentDescription = stringResource(R.string.transfer_action_pause),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            TransferStatus.PAUSED -> {
                                // 已暂停：显示继续按钮
                                IconButton(onClick = onResume) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = stringResource(R.string.transfer_action_resume),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            TransferStatus.FAILED -> {
                                // 失败：显示重试按钮
                                IconButton(onClick = onRetry) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(R.string.transfer_action_retry),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            TransferStatus.COMPLETED -> {
                                // 已完成：显示打开文件/文件夹菜单（仅下载任务）
                                if (task.type == TransferType.DOWNLOAD) {
                                    OpenFileMenu(
                                        onOpenFile = onOpenFile,
                                        onOpenFolder = onOpenFolder
                                    )
                                }
                            }

                            TransferStatus.PENDING, TransferStatus.CANCELLED -> {
                                // 等待中或已取消：不显示特殊操作按钮
                            }
                        }

                        // 取消按钮（活动任务）
                        if (task.status == TransferStatus.ACTIVE ||
                            task.status == TransferStatus.PAUSED ||
                            task.status == TransferStatus.PENDING
                        ) {
                            IconButton(onClick = onCancel) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.action_cancel),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        // 删除按钮（已完成、失败、已取消的任务）
                        if (task.status == TransferStatus.COMPLETED ||
                            task.status == TransferStatus.FAILED ||
                            task.status == TransferStatus.CANCELLED
                        ) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // 第二行：状态标签和实时信息
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                maxItemsInEachRow = Int.MAX_VALUE
            ) {
                // 状态标签（已完成状态不显示角标）
                if (task.status != TransferStatus.COMPLETED) {
                    StatusChip(status = task.status)
                }

                // 进行中时显示实时信息
                if (task.status == TransferStatus.ACTIVE) {
                    // 使用 FlowRow 让实时信息在窄屏下自动换行，避免挤在进度条上方
                    val remainingTimeText = if (task.estimatedTimeRemaining > 0) {
                        formatRemainingTime(task.estimatedTimeRemaining)
                    } else {
                        null
                    }
                    val infoItems = buildList {
                        add("${task.progress}%" to MaterialTheme.colorScheme.onSurfaceVariant)
                        add(
                            "${FileTypeHelper.formatFileSize(task.transferredBytes)} / ${
                                FileTypeHelper.formatFileSize(task.fileSize)
                            }" to MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (task.speed > 0) {
                            add(
                                "${FileTypeHelper.formatFileSize(task.speed)}/s" to
                                    MaterialTheme.colorScheme.primary
                            )
                        }

                        if (remainingTimeText != null) {
                            add(
                                remainingTimeText to
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    infoItems.forEach { (text, color) ->
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            color = color
                        )
                    }
                }
            }

            // 第三行：进度条（仅进行中时显示）
            if (task.status == TransferStatus.ACTIVE) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            // 错误信息（失败时显示）
            if (task.status == TransferStatus.FAILED && task.errorMessage != null) {
                Text(
                    text = stringResource(R.string.transfer_error_prefix, task.errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // 文件失效提示（已完成的下载任务且文件不存在）
            if (task.type == TransferType.DOWNLOAD &&
                task.status == TransferStatus.COMPLETED &&
                !isFileValid
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.transfer_file_invalid),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.transfer_file_invalid_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
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

    Surface(
        color = color.copy(alpha = 0.2f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
