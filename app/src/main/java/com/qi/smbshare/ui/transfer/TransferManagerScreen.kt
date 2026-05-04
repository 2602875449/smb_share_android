package com.qi.smbshare.ui.transfer

import android.content.Intent
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
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
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
        mutableStateOf<TransferTask?>(null)
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
    val requestDownloadTreePermission: (TransferTask, Uri?) -> Unit =
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
                            // 文件有效性由 ViewModel 在 IO 线程统一检查
                            val isFileValid = state.fileValidityMap[task.id] ?: true
                            
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
