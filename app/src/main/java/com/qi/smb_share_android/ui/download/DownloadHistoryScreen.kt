package com.qi.smb_share_android.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.DownloadStatus
import com.qi.smb_share_android.util.FileTypeHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    viewModel: DownloadHistoryViewModel,
    onInstallApk: (File) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = Color.Transparent,
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "下载历史",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.downloadHistory.isEmpty() -> {
                    // 空状态
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "暂无下载记录",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(state.downloadHistory) { item ->
                            DownloadHistoryItem(
                                item = item,
                                onOpenFile = {
                                    if (FileTypeHelper.isApkFile(item.fileName)) {
                                        val file = File(item.localPath)
                                        if (file.exists()) {
                                            onInstallApk(file)
                                        }
                                    } else {
                                        viewModel.handleIntent(DownloadHistoryIntent.OpenFile(item))
                                    }
                                },
                                onRetry = {
                                    viewModel.handleIntent(DownloadHistoryIntent.RetryDownload(item))
                                },
                                onOpenLocation = {
                                    viewModel.handleIntent(
                                        DownloadHistoryIntent.OpenFileLocation(
                                            item
                                        )
                                    )
                                },
                                onDelete = {
                                    viewModel.handleIntent(DownloadHistoryIntent.DeleteHistory(item.id))
                                }
                            )
                        }
                    }
                }
            }

            // 错误提示
            state.error?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.handleIntent(DownloadHistoryIntent.ClearError) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadHistoryItem(
    item: com.qi.smb_share_android.data.model.DownloadItem,
    onOpenFile: () -> Unit,
    onRetry: () -> Unit,
    onOpenLocation: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTimestamp(item.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // 状态和大小
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusChip(status = item.status)
                        if (item.totalBytes > 0) {
                            Text(
                                text = FileTypeHelper.formatFileSize(item.downloadedBytes) +
                                        " / " + FileTypeHelper.formatFileSize(item.totalBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // 错误信息
                    item.errorMessage?.let { error ->
                        Text(
                            text = "错误: $error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (item.status == DownloadStatus.COMPLETED) {
                            DropdownMenuItem(
                                text = { Text("打开文件") },
                                onClick = {
                                    showMenu = false
                                    onOpenFile()
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("打开所在目录") },
                                onClick = {
                                    showMenu = false
                                    onOpenLocation()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                }
                            )
                        }
                        if (item.status == DownloadStatus.FAILED) {
                            DropdownMenuItem(
                                text = { Text("重试") },
                                onClick = {
                                    showMenu = false
                                    onRetry()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }

            // 下载进度条（仅在进行中时显示）
            if (item.status == DownloadStatus.DOWNLOADING && item.progress >= 0) {
                LinearProgressIndicator(
                    progress = { item.progress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: DownloadStatus) {
    val (text, color) = when (status) {
        DownloadStatus.COMPLETED -> "成功" to MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> "失败" to MaterialTheme.colorScheme.error
        DownloadStatus.DOWNLOADING -> "下载中" to MaterialTheme.colorScheme.tertiary
        DownloadStatus.PENDING -> "等待中" to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.CANCELLED -> "已取消" to MaterialTheme.colorScheme.onSurfaceVariant
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

private fun formatTimestamp(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

