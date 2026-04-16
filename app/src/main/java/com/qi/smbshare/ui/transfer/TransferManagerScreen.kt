package com.qi.smbshare.ui.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.ui.components.PermissionPermanentlyDeniedDialog
import com.qi.smbshare.ui.components.PermissionRationaleDialog
import com.qi.smbshare.ui.components.PermissionType
import com.qi.smbshare.util.PermissionManager
import com.qi.smbshare.R
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
    val activity = context as? ComponentActivity
    
    var permissionManagerRef by remember { mutableStateOf<PermissionManager?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManagerRef?.handlePermissionResult(permissions)
    }
    val permissionManager = remember(activity, permissionLauncher) {
        activity?.let {
            PermissionManager(it, permissionLauncher).also { manager ->
                permissionManagerRef = manager
            }
        }
    }
    var notificationPermissionStatus by remember { mutableStateOf(PermissionManager.PermissionStatus.GRANTED) }
    var hasPromptedNotification by remember { mutableStateOf(false) }
    var showNotificationRationale by remember { mutableStateOf(false) }
    var showNotificationDenied by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var savedDownloadTreeUri by remember {
        mutableStateOf(getPersistedDownloadTreeUri(context))
    }
    var pendingFolderTask by remember {
        mutableStateOf<com.qi.smbshare.data.model.TransferTask?>(null)
    }
    val downloadFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
                persistDownloadTreeUri(context, uri)
                savedDownloadTreeUri = uri
                pendingFolderTask?.let { task ->
                    openFolder(
                        context = context,
                        task = task,
                        savedTreeUri = uri,
                        onRequestTreePermission = {}
                    )
                }
            } catch (e: SecurityException) {
                Log.e("TransferManagerScreen", "无法获取下载目录权限: ${e.message}", e)
            }
        }
        pendingFolderTask = null
    }
    val requestDownloadTreePermission: (com.qi.smbshare.data.model.TransferTask, Uri?) -> Unit =
        remember(downloadFolderLauncher) {
            { task, initialUri ->
                pendingFolderTask = task
                downloadFolderLauncher.launch(initialUri)
            }
        }
    
    fun handleNotificationPermissionStatus(
        status: PermissionManager.PermissionStatus,
        allowAutoPrompt: Boolean
    ) {
        notificationPermissionStatus = status
        if (status == PermissionManager.PermissionStatus.GRANTED) {
            showNotificationRationale = false
            showNotificationDenied = false
            if (allowAutoPrompt) {
                hasPromptedNotification = true
            }
            return
        }
        
        if (allowAutoPrompt && !hasPromptedNotification) {
            when (status) {
                PermissionManager.PermissionStatus.DENIED -> {
                    showNotificationRationale = true
                }
                PermissionManager.PermissionStatus.PERMANENTLY_DENIED -> {
                    showNotificationDenied = true
                }
                else -> Unit
            }
            hasPromptedNotification = true
        }
    }
    
    LaunchedEffect(permissionManager) {
        permissionManager?.let { manager ->
            if (!PermissionManager.needsNotificationPermission()) {
                handleNotificationPermissionStatus(PermissionManager.PermissionStatus.GRANTED, false)
            } else {
                handleNotificationPermissionStatus(manager.checkNotificationPermission(), true)
            }
        }
    }
    
    DisposableEffect(lifecycleOwner, permissionManager) {
        val manager = permissionManager
        if (manager == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME && PermissionManager.needsNotificationPermission()) {
                    handleNotificationPermissionStatus(manager.checkNotificationPermission(), false)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }
    
    val requestNotificationPermission = {
        val manager = permissionManager
        if (manager == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.transfer_snackbar_notification_unavailable)
                )
            }
        } else {
            showNotificationRationale = false
            manager.requestNotificationPermission(
                onGranted = {
                    handleNotificationPermissionStatus(PermissionManager.PermissionStatus.GRANTED, false)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.transfer_snackbar_notification_enabled)
                        )
                    }
                },
                onDenied = {
                    handleNotificationPermissionStatus(PermissionManager.PermissionStatus.DENIED, false)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.transfer_snackbar_notification_denied)
                        )
                    }
                },
                onPermanentlyDenied = {
                    handleNotificationPermissionStatus(PermissionManager.PermissionStatus.PERMANENTLY_DENIED, false)
                    showNotificationDenied = true
                }
            )
        }
    }
    
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
                                contentDescription = stringResource(R.string.transfer_cd_exit_multi_select),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // 标题
                    Text(
                        text = if (state.isMultiSelectMode) {
                            stringResource(
                                R.string.transfer_app_bar_selected,
                                state.selectedTaskCount
                            )
                        } else {
                            stringResource(R.string.transfer_app_bar_title)
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
                                contentDescription = stringResource(R.string.transfer_cd_select_all),
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
                                contentDescription = stringResource(R.string.transfer_cd_refresh),
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
                                    contentDescription = stringResource(R.string.transfer_cd_bulk_delete)
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
                                    contentDescription = stringResource(R.string.transfer_cd_bulk_cancel)
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
        if (PermissionManager.needsNotificationPermission() &&
            notificationPermissionStatus != PermissionManager.PermissionStatus.GRANTED
        ) {
            NotificationPermissionBanner(
                status = notificationPermissionStatus,
                onRequestPermission = { showNotificationRationale = true },
                onOpenSettings = {
                    permissionManager?.openAppSettings()
                }
            )
        }
        
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
                            label = stringResource(R.string.transfer_tab_downloading),
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
                            label = stringResource(R.string.transfer_tab_uploading),
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
                            label = stringResource(R.string.transfer_tab_completed),
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
                                    val exists = com.qi.smbshare.util.StorageHelper.fileExists(
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
                                    val effectiveTreeUri = savedDownloadTreeUri?.takeIf {
                                        hasPersistedDownloadTreePermission(context, it)
                                    } ?: run {
                                        if (savedDownloadTreeUri != null) {
                                            clearPersistedDownloadTreeUri(context)
                                            savedDownloadTreeUri = null
                                        }
                                        null
                                    }
                                    openFolder(
                                        context = context,
                                        task = task,
                                        savedTreeUri = effectiveTreeUri,
                                        onRequestTreePermission = { initialUri ->
                                            requestDownloadTreePermission(task, initialUri)
                                        }
                                    )
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
    
    if (showNotificationRationale) {
        PermissionRationaleDialog(
            permissionType = PermissionType.NOTIFICATION,
            onConfirm = {
                requestNotificationPermission()
            },
            onDismiss = {
                showNotificationRationale = false
            }
        )
    }
    
    if (showNotificationDenied) {
        PermissionPermanentlyDeniedDialog(
            permissionType = PermissionType.NOTIFICATION,
            onOpenSettings = {
                showNotificationDenied = false
                permissionManager?.openAppSettings()
            },
            onDismiss = {
                showNotificationDenied = false
            }
        )
    }
}

@Composable
private fun NotificationPermissionBanner(
    status: PermissionManager.PermissionStatus,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val description = if (status == PermissionManager.PermissionStatus.PERMANENTLY_DENIED) {
        stringResource(R.string.transfer_notification_banner_description_denied)
    } else {
        stringResource(R.string.transfer_notification_banner_description_rationale)
    }
    val actionText = if (status == PermissionManager.PermissionStatus.PERMANENTLY_DENIED) {
        stringResource(R.string.transfer_notification_banner_action_settings)
    } else {
        stringResource(R.string.transfer_notification_banner_action_allow)
    }
    val onAction = if (status == PermissionManager.PermissionStatus.PERMANENTLY_DENIED) {
        onOpenSettings
    } else {
        onRequestPermission
    }
    
    Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.transfer_notification_banner_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.9f)
            )
            TextButton(
                onClick = onAction,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(actionText)
            }
        }
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
                    TransferTab.DOWNLOADING -> stringResource(R.string.transfer_empty_downloading)
                    TransferTab.UPLOADING -> stringResource(R.string.transfer_empty_uploading)
                    TransferTab.COMPLETED -> stringResource(R.string.transfer_empty_completed)
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
                text = if (isBatch) {
                    stringResource(R.string.transfer_dialog_delete_batch_title)
                } else {
                    stringResource(R.string.transfer_dialog_delete_single_title)
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isBatch) {
                        stringResource(
                            R.string.transfer_dialog_delete_batch_message,
                            selectedCount
                        )
                    } else {
                        stringResource(R.string.transfer_dialog_delete_single_message)
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
                        text = stringResource(R.string.transfer_dialog_delete_remove_file),
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
                    stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
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
    task: com.qi.smbshare.data.model.TransferTask,
    onInstallApk: (File) -> Unit
) {
    val isContentUri = task.localPath.startsWith("content://")

    // 如果是 APK 文件，使用安装回调
    if (task.fileName.endsWith(".apk", ignoreCase = true)) {
        if (isContentUri) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(task.localPath), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            val file = File(task.localPath)
            if (!file.exists()) {
                return
            }
            onInstallApk(file)
        }
        return
    }

    // 其他文件类型，使用系统默认应用打开
    try {
        val uri = if (isContentUri) {
            Uri.parse(task.localPath)
        } else {
            val file = File(task.localPath)
            if (!file.exists()) {
                return
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

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
    task: com.qi.smbshare.data.model.TransferTask,
    savedTreeUri: Uri?,
    onRequestTreePermission: (Uri?) -> Unit
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

        val storageAuthority = "com.android.externalstorage.documents"
        val downloadDocId = "primary:${Environment.DIRECTORY_DOWNLOADS}"
        val docIdCandidates = listOf(
            "$downloadDocId/SMBShare",
            "primary:Downloads/SMBShare"
        )

        // 使用 LinkedHashSet 保证尝试顺序，同时去重
        val candidateUris = linkedSetOf<Uri>()
        var permissionRequired = false

        val persistedTreeUri = savedTreeUri?.takeIf {
            hasPersistedDownloadTreePermission(context, it)
        }
        persistedTreeUri?.let { treeUri ->
            // 使用用户授权的目录，优先确保权限可用
            val currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            runCatching {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
            }.getOrNull()?.let(candidateUris::add)
            docIdCandidates.forEach { docId ->
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }.getOrNull()?.let(candidateUris::add)
            }
        }

        // 方法1：构建树形 URI，再生成目录 Document URI，提高 ACTION_VIEW 打开的成功率
        val downloadTreeUri = runCatching {
            DocumentsContract.buildTreeDocumentUri(storageAuthority, downloadDocId)
        }.getOrNull()
        if (downloadTreeUri != null) {
            docIdCandidates.forEach { docId ->
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(downloadTreeUri, docId)
                }.getOrNull()?.let(candidateUris::add)
            }
        }

        // 方法2：直接构建 Document URI（部分 ROM 只支持此形式）
        docIdCandidates.forEach { docId ->
            runCatching {
                DocumentsContract.buildDocumentUri(storageAuthority, docId)
            }.getOrNull()?.let(candidateUris::add)
        }

        // 方法3：兜底使用 FileProvider 暴露目录
        runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                defaultFolder
            )
        }.getOrNull()?.let(candidateUris::add)

        // 逐个尝试使用 ACTION_VIEW 打开，避免直接进入“选择文件”模式
        candidateUris.forEach { uri ->
            when (tryOpenFolderWithViewIntent(context, uri)) {
                FolderOpenResult.SUCCESS -> {
                    Log.d("TransferManagerScreen", "成功通过 ACTION_VIEW 打开目录: $uri")
                    return
                }
                FolderOpenResult.PERMISSION_REQUIRED -> {
                    permissionRequired = true
                }
                FolderOpenResult.FAILED -> {}
            }
        }

        if (permissionRequired && persistedTreeUri == null) {
            Log.w("TransferManagerScreen", "缺少目录访问权限，准备请求用户授权 Download/SMBShare")
            onRequestTreePermission(buildDownloadInitialUri(context))
            return
        }

        // 方法4：仍无法直接定位时，退回到 ACTION_OPEN_DOCUMENT（会进入选择界面，但至少定位到目录）
        val initialUri = runCatching {
            DocumentsContract.buildDocumentUri(storageAuthority, "$downloadDocId/SMBShare")
        }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
            val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            context.startActivity(pickerIntent)
            Log.d("TransferManagerScreen", "回退到 ACTION_OPEN_DOCUMENT，已定位到 SMBShare 目录")
            return
        }

        Log.e("TransferManagerScreen", "所有方式均失败，无法打开 SMBShare 目录")
    } catch (e: Exception) {
        Log.e("TransferManagerScreen", "打开文件夹时发生异常: ${e.message}", e)
    }
}

/**
 * 尝试通过 ACTION_VIEW 打开指定 URI 对应的目录
 * 某些 ROM 需要显式声明可写/可读和前缀权限，否则会抛出 ActivityNotFoundException
 */
private fun tryOpenFolderWithViewIntent(
    context: android.content.Context,
    uri: Uri
): FolderOpenResult {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri
        // 使用目录 MIME，提示系统这是一个文件夹
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }
    return try {
        context.startActivity(intent)
        FolderOpenResult.SUCCESS
    } catch (e: ActivityNotFoundException) {
        Log.w("TransferManagerScreen", "当前 URI 无可处理的应用: ${e.message}")
        FolderOpenResult.FAILED
    } catch (e: SecurityException) {
        Log.w("TransferManagerScreen", "缺少访问目录的权限: ${e.message}")
        FolderOpenResult.PERMISSION_REQUIRED
    } catch (e: Exception) {
        Log.w("TransferManagerScreen", "ACTION_VIEW 打开目录失败: ${e.message}")
        FolderOpenResult.FAILED
    }
}

private enum class FolderOpenResult {
    SUCCESS,
    PERMISSION_REQUIRED,
    FAILED
}

/**
 * 根据文件名获取 MIME 类型
 */
private val textExtensions = setOf(
    "txt", "md", "json", "xml", "html", "csv", "log", "ini", "cfg",
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
)

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
)

private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "mpeg", "3gp"
)

private val audioExtensions = setOf(
    "mp3", "aac", "wav", "flac", "ogg", "m4a", "amr"
)

/**
 * 根据文件后缀映射到大类 MIME，优先减少系统弹窗中过多的候选应用
 */
private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension in textExtensions -> "text/*"
        extension in imageExtensions -> "image/*"
        extension in videoExtensions -> "video/*"
        extension in audioExtensions -> "audio/*"
        else -> "*/*"
    }
}

private const val DOWNLOAD_TREE_PREF = "transfer_manager_prefs"
private const val KEY_DOWNLOAD_TREE_URI = "download_tree_uri"

private fun getPersistedDownloadTreeUri(context: Context): Uri? {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    val uriString = prefs.getString(KEY_DOWNLOAD_TREE_URI, null)
    return uriString?.let { Uri.parse(it) }
}

private fun persistDownloadTreeUri(context: Context, uri: Uri) {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_DOWNLOAD_TREE_URI, uri.toString()).apply()
}

private fun clearPersistedDownloadTreeUri(context: Context) {
    val prefs = context.getSharedPreferences(DOWNLOAD_TREE_PREF, Context.MODE_PRIVATE)
    prefs.edit().remove(KEY_DOWNLOAD_TREE_URI).apply()
}

private fun hasPersistedDownloadTreePermission(context: Context, uri: Uri): Boolean {
    return context.contentResolver.persistedUriPermissions.any { persisted ->
        persisted.uri == uri && persisted.isReadPermission
    }
}

private fun buildDownloadInitialUri(context: Context): Uri? {
    val storageAuthority = "com.android.externalstorage.documents"
    val downloadDocId = "primary:${Environment.DIRECTORY_DOWNLOADS}"
    return runCatching {
        DocumentsContract.buildDocumentUri(storageAuthority, downloadDocId)
    }.getOrNull()
}
