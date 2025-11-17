package com.qi.smb_share_android.ui.transfer

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.TransferStatus
import com.qi.smb_share_android.data.model.TransferType
import java.io.File

/**
 * 传输管理主界面
 * 提供统一的上传/下载任务管理功能
 * 
 * @param viewModel 传输管理 ViewModel
 * @param onInstallApk APK 安装回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferManagerScreen(
    viewModel: TransferManagerViewModel,
    onInstallApk: (File) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    
    // Pager 状态，用于实现左右滑动切换
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { TransferTab.entries.size }
    )
    
    // 同步 pager 状态和 tab 选择状态
    LaunchedEffect(pagerState.currentPage) {
        val newTab = TransferTab.entries[pagerState.currentPage]
        if (newTab != state.selectedTab) {
            viewModel.handleIntent(TransferManagerIntent.SwitchTab(newTab))
        }
    }
    
    LaunchedEffect(state.selectedTab) {
        val targetPage = state.selectedTab.ordinal
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }
    
    // 显示删除确认对话框的状态
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTaskId by remember { mutableStateOf<String?>(null) }
    
    // 显示批量删除确认对话框的状态
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    
    // 显示错误和消息的 Snackbar
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.handleIntent(TransferManagerIntent.ClearError)
        }
    }
    
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.handleIntent(TransferManagerIntent.ClearMessage)
        }
    }
    
    // 拦截返回键，在多选模式下先退出多选
    BackHandler(enabled = state.isMultiSelectMode) {
        viewModel.handleIntent(TransferManagerIntent.ExitMultiSelectMode)
    }
    
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 导航图标（多选模式下显示关闭按钮）
                    if (state.isMultiSelectMode) {
                        IconButton(
                            onClick = {
                                viewModel.handleIntent(TransferManagerIntent.ExitMultiSelectMode)
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "退出多选模式",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // 标题
                    Text(
                        text = if (state.isMultiSelectMode) {
                            "已选择 ${state.selectedTaskCount} 项"
                        } else {
                            "传输管理"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // 操作按钮
                    if (state.isMultiSelectMode) {
                        // 多选模式下的操作按钮
                        IconButton(
                            onClick = {
                                viewModel.handleIntent(TransferManagerIntent.SelectAllTasks)
                            }
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "全选",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        // 正常模式下的操作按钮
                        IconButton(
                            onClick = {
                                viewModel.handleIntent(TransferManagerIntent.RefreshTasks)
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
        floatingActionButton = {
            // 多选模式下显示批量操作按钮
            if (state.isMultiSelectMode && state.hasSelectedTasks) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 根据当前 Tab 显示不同的批量操作按钮
                    when (state.selectedTab) {
                        TransferTab.COMPLETED -> {
                            // 已完成任务：显示批量删除按钮
                            FloatingActionButton(
                                onClick = {
                                    showBatchDeleteDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "批量删除"
                                )
                            }
                        }
                        TransferTab.DOWNLOADING, TransferTab.UPLOADING -> {
                            // 活动任务：显示批量取消按钮
                            FloatingActionButton(
                                onClick = {
                                    viewModel.handleIntent(TransferManagerIntent.CancelSelectedTasks)
                                },
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                Icon(
                                    Icons.Default.Cancel,
                                    contentDescription = "批量取消"
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val selectedTabIndex = state.selectedTab.ordinal
            // Tab 导航栏（可滑动）
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {} // 去掉默认底部分割线，避免出现额外细线
            ) {
                Tab(
                    selected = state.selectedTab == TransferTab.DOWNLOADING,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(TransferTab.DOWNLOADING.ordinal)
                        }
                    },
                    text = {
                        TabLabelWithBadge(
                            label = "下载中",
                            count = state.downloadingTasks.size
                        )
                    }
                )
                
                Tab(
                    selected = state.selectedTab == TransferTab.UPLOADING,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(TransferTab.UPLOADING.ordinal)
                        }
                    },
                    text = {
                        TabLabelWithBadge(
                            label = "上传中",
                            count = state.uploadingTasks.size
                        )
                    }
                )
                
                Tab(
                    selected = state.selectedTab == TransferTab.COMPLETED,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(TransferTab.COMPLETED.ordinal)
                        }
                    },
                    text = {
                        TabLabelWithBadge(
                            label = "已完成",
                            count = state.completedTasks.size,
                            showBadge = false
                        )
                    }
                )
            }
            
            // 任务列表内容区域（支持左右滑动切换）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val targetTab = TransferTab.entries[page]
                val tasks = when (targetTab) {
                    TransferTab.DOWNLOADING -> state.downloadingTasks
                    TransferTab.UPLOADING -> state.uploadingTasks
                    TransferTab.COMPLETED -> state.completedTasks
                }
                
                if (tasks.isEmpty()) {
                    // 空状态显示
                    EmptyState(tab = targetTab)
                } else {
                    // 任务列表
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = tasks,
                            key = { task -> task.id }
                        ) { task ->
                            // 检查文件是否有效（仅对已完成的下载任务）
                            val isFileValid = remember(task.id, task.localPath, task.status) {
                                if (task.type == TransferType.DOWNLOAD && 
                                    task.status == TransferStatus.COMPLETED) {
                                    // 打印 localPath 用于调试
                                    Log.d("TransferManagerScreen", "检查文件有效性 - 任务ID: ${task.id}, 文件名: ${task.fileName}, localPath: ${task.localPath}")
                                    
                                    // 使用 StorageHelper 检查文件是否存在（支持 URI 格式）
                                    val exists = com.qi.smb_share_android.util.StorageHelper.fileExists(
                                        context,
                                        task.localPath
                                    )
                                    Log.d("TransferManagerScreen", "文件存在性检查结果: $exists")
                                    exists
                                } else {
                                    true
                                }
                            }
                            
                            TransferTaskItem(
                                task = task,
                                isMultiSelectMode = state.isMultiSelectMode,
                                isSelected = task.id in state.selectedTaskIds,
                                isFileValid = isFileValid,
                                onItemClick = {
                                    when {
                                        state.isMultiSelectMode -> {
                                            // 多选模式：切换选中状态
                                            viewModel.handleIntent(
                                                TransferManagerIntent.ToggleTaskSelection(task.id)
                                            )
                                        }
                                        task.type == TransferType.DOWNLOAD && 
                                        task.status == TransferStatus.COMPLETED && 
                                        isFileValid -> {
                                            // 已完成的下载任务且文件有效：打开文件
                                            openFile(context, task, onInstallApk)
                                        }
                                        else -> {
                                            // 其他情况：不做任何操作
                                        }
                                    }
                                },
                                onItemLongClick = {
                                    if (!state.isMultiSelectMode) {
                                        viewModel.handleIntent(TransferManagerIntent.EnterMultiSelectMode)
                                        viewModel.handleIntent(
                                            TransferManagerIntent.ToggleTaskSelection(task.id)
                                        )
                                    }
                                },
                                onPause = {
                                    viewModel.handleIntent(TransferManagerIntent.PauseTransfer(task.id))
                                },
                                onResume = {
                                    viewModel.handleIntent(TransferManagerIntent.ResumeTransfer(task.id))
                                },
                                onCancel = {
                                    viewModel.handleIntent(TransferManagerIntent.CancelTransfer(task.id))
                                },
                                onRetry = {
                                    viewModel.handleIntent(TransferManagerIntent.RetryTransfer(task.id))
                                },
                                onDelete = {
                                    deleteTaskId = task.id
                                    showDeleteDialog = true
                                },
                                onOpenFile = {
                                    if (isFileValid) {
                                        openFile(context, task, onInstallApk)
                                    }
                                },
                                onOpenFolder = {
                                    openFolder(context, task)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    if (showDeleteDialog && deleteTaskId != null) {
        DeleteConfirmDialog(
            onConfirm = { deleteFile ->
                deleteTaskId?.let { taskId ->
                    viewModel.handleIntent(
                        TransferManagerIntent.DeleteTransfer(taskId, deleteFile)
                    )
                }
                showDeleteDialog = false
                deleteTaskId = null
            },
            onDismiss = {
                showDeleteDialog = false
                deleteTaskId = null
            }
        )
    }
    
    // 批量删除确认对话框
    if (showBatchDeleteDialog) {
        DeleteConfirmDialog(
            isBatch = true,
            selectedCount = state.selectedTaskCount,
            onConfirm = { deleteFiles ->
                viewModel.handleIntent(
                    TransferManagerIntent.DeleteSelectedTasks(deleteFiles)
                )
                showBatchDeleteDialog = false
            },
            onDismiss = {
                showBatchDeleteDialog = false
            }
        )
    }
}

/**
 * 空状态显示组件
 * 根据不同的 Tab 显示不同的空状态提示
 */
@Composable
private fun TabLabelWithBadge(
    label: String,
    count: Int,
    showBadge: Boolean = true
) {
    // 角标与文字横向排列，避免遮挡
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        if (showBadge && count > 0) {
            Badge {
                Text(count.toString())
            }
        }
    }
}

@Composable
private fun EmptyState(tab: TransferTab) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = when (tab) {
                    TransferTab.DOWNLOADING -> Icons.Default.Refresh
                    TransferTab.UPLOADING -> Icons.Default.Refresh
                    TransferTab.COMPLETED -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            Text(
                text = when (tab) {
                    TransferTab.DOWNLOADING -> "暂无下载任务"
                    TransferTab.UPLOADING -> "暂无上传任务"
                    TransferTab.COMPLETED -> "暂无历史记录"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 删除确认对话框
 * 通过勾选项决定是否同时删除本地文件
 */
@Composable
private fun DeleteConfirmDialog(
    isBatch: Boolean = false,
    selectedCount: Int = 0,
    onConfirm: (deleteFile: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    // 通过复选框统一管理是否删除本地文件，减少多按钮误触
    var deleteLocalFile by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = if (isBatch) "批量删除确认" else "删除确认"
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isBatch) {
                        "确定要删除选中的 $selectedCount 个任务吗？"
                    } else {
                        "确定要删除此任务吗？"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = deleteLocalFile,
                        onCheckedChange = { deleteLocalFile = it }
                    )
                    Text(
                        text = "同时删除本地文件",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(deleteLocalFile) }
            ) {
                Text(
                    "删除",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 打开文件
 * 根据文件类型使用合适的方式打开
 */
private fun openFile(
    context: android.content.Context,
    task: com.qi.smb_share_android.data.model.TransferTask,
    onInstallApk: (File) -> Unit
) {
    val file = File(task.localPath)
    
    if (!file.exists()) {
        // 文件不存在，无法打开
        return
    }
    
    // 如果是 APK 文件，使用安装回调
    if (task.fileName.endsWith(".apk", ignoreCase = true)) {
        onInstallApk(file)
        return
    }
    
    // 其他文件类型，使用系统默认应用打开
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(task.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    } catch (e: Exception) {
        // 无法打开文件
        e.printStackTrace()
    }
}

/**
 * 打开文件所在的文件夹
 * 使用系统文件管理器打开文件所在目录
 * 统一指向 Download/SMBShare，确保用户打开后就是我们的默认目录
 */
private fun openFolder(
    context: android.content.Context,
    @Suppress("UNUSED_PARAMETER") task: com.qi.smb_share_android.data.model.TransferTask
) {
    try {
        val defaultFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SMBShare"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
        
        // 方法1: 尝试使用 DocumentsContract 构建目录 URI（最可靠的方法）
        // 尝试两种格式：Download（标准）和 Downloads（某些文件管理器可能需要）
        val docIds = listOf(
            "primary:${Environment.DIRECTORY_DOWNLOADS}/SMBShare",  // Download/SMBShare
            "primary:Downloads/SMBShare"  // 某些文件管理器可能需要复数形式
        )
        
        for (docId in docIds) {
            val documentsUri = runCatching {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    docId
                )
            }.getOrNull()
            
            if (documentsUri != null) {
                // 优先尝试使用 ACTION_VIEW 直接打开目录
                val documentsIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(documentsUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(documentsIntent)
                    Log.d("TransferManagerScreen", "成功通过 DocumentsContract 打开目录: $docId")
                    return
                } catch (e: Exception) {
                    Log.w("TransferManagerScreen", "无法通过 DocumentsContract 打开目录 ($docId): ${e.message}")
                    // 继续尝试下一个格式
                }
            }
        }
        
        // 如果直接打开目录失败，尝试使用 EXTRA_INITIAL_URI（Android 8.0+）
        val documentsUri = runCatching {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:${Environment.DIRECTORY_DOWNLOADS}/SMBShare"
            )
        }.getOrNull()
        
        // 方法2: Android 8.0+ 使用 ACTION_OPEN_DOCUMENT 配合 EXTRA_INITIAL_URI
        // 注意：EXTRA_INITIAL_URI 在某些文件管理器中可能不被支持，会打开 Downloads 而不是 Downloads/SMBShare
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && documentsUri != null) {
            val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, documentsUri)
            }
            try {
                context.startActivity(pickerIntent)
                Log.d("TransferManagerScreen", "成功通过 ACTION_OPEN_DOCUMENT 打开目录")
                return
            } catch (e: Exception) {
                Log.w("TransferManagerScreen", "无法通过 ACTION_OPEN_DOCUMENT 打开目录: ${e.message}")
            }
        }
        
        // 方法3: 通过 FileProvider 暴露下载目录
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            defaultFolder
        )
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
            Log.d("TransferManagerScreen", "成功通过 FileProvider 打开目录")
        } catch (e: Exception) {
            Log.e("TransferManagerScreen", "所有方法都失败，无法打开文件夹: ${e.message}", e)
        }
    } catch (e: Exception) {
        Log.e("TransferManagerScreen", "打开文件夹时发生异常: ${e.message}", e)
    }
}

/**
 * 根据文件名获取 MIME 类型
 */
private fun getMimeType(fileName: String): String {
    return when {
        fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
        fileName.endsWith(".txt", ignoreCase = true) -> "text/plain"
        fileName.endsWith(".jpg", ignoreCase = true) ||
        fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
        fileName.endsWith(".png", ignoreCase = true) -> "image/png"
        fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
        fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
        fileName.endsWith(".zip", ignoreCase = true) -> "application/zip"
        else -> "*/*"
    }
}
