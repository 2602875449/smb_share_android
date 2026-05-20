package com.qi.smbshare.ui.filelist

import android.content.ClipData
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.R
import com.qi.smbshare.data.model.FileItem
import com.qi.smbshare.ui.components.PredictiveBackAnimatedContent
import com.qi.smbshare.ui.components.PermissionPermanentlyDeniedDialog
import com.qi.smbshare.ui.components.PermissionRationaleDialog
import com.qi.smbshare.ui.components.PermissionType
import com.qi.smbshare.util.FileTypeHelper
import com.qi.smbshare.util.PermissionManager
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun FileListScreen(
    viewModel: FileListViewModel,
    onInstallApk: (File) -> Unit,
    config: com.qi.smbshare.data.model.SMBConfig,
    onBack: () -> Unit = {},
    onPreviewVisibilityChange: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var folderName by remember { mutableStateOf("") }
    var renameName by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pathCopiedMessage = stringResource(R.string.msg_path_copied)
    val topBarColor = MaterialTheme.colorScheme.surface
    val useLightStatusBarIcons = topBarColor.luminance() > 0.5f

    // edge-to-edge 已在 Theme 全局启用，此处仅根据 toolbar 亮度调整状态栏图标颜色
    DisposableEffect(activity, view, useLightStatusBarIcons) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                useLightStatusBarIcons
        }

        onDispose {
            // 离开文件列表时恢复为跟随主题的状态栏图标颜色
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    useLightStatusBarIcons
            }
        }
    }

    // 权限相关状态
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPermissionDenied by remember { mutableStateOf(false) }
    var permissionType by remember { mutableStateOf(PermissionType.STORAGE_DOWNLOAD) }
    var pendingDownloadFile by remember { mutableStateOf<Pair<String, String>?>(null) }

    // 权限管理器引用 - 用于在启动器回调中访问
    var permissionManagerRef by remember { mutableStateOf<PermissionManager?>(null) }

    // 权限请求启动器 - 必须在 Composable 顶层创建
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 处理权限请求结果
        permissionManagerRef?.handlePermissionResult(permissions)
    }

    // 权限管理器 - 使用启动器创建
    val permissionManager = remember(activity, permissionLauncher) {
        activity?.let { it ->
            PermissionManager(it, permissionLauncher).also {
                permissionManagerRef = it
            }
        }
    }

    // 权限检查和下载文件的辅助函数
    val checkPermissionAndDownload = { filePath: String, fileName: String ->
        permissionManager?.let { pm ->
            when (pm.checkDownloadPermission()) {
                PermissionManager.PermissionStatus.GRANTED -> {
                    // 权限已授予，直接下载
                    viewModel.handleIntent(FileListIntent.DownloadFile(filePath, fileName))
                }
                PermissionManager.PermissionStatus.DENIED -> {
                    // 需要请求权限
                    pendingDownloadFile = Pair(filePath, fileName)
                    permissionType = PermissionType.STORAGE_DOWNLOAD
                    showPermissionRationale = true
                }
                PermissionManager.PermissionStatus.PERMANENTLY_DENIED -> {
                    // 权限被永久拒绝
                    permissionType = PermissionType.STORAGE_DOWNLOAD
                    showPermissionDenied = true
                }
            }
        } ?: run {
            // 如果无法获取权限管理器，直接尝试下载
            viewModel.handleIntent(FileListIntent.DownloadFile(filePath, fileName))
        }
    }

    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // 直接使用系统返回的 URI，避免为上传先复制一份大文件到缓存目录
                val (fileName, fileSize) = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        val resolvedName = if (nameIndex >= 0) {
                            cursor.getString(nameIndex)
                        } else {
                            null
                        } ?: "uploaded_file_${System.currentTimeMillis()}"
                        val resolvedSize = if (sizeIndex >= 0) {
                            cursor.getLong(sizeIndex)
                        } else {
                            -1L
                        }
                        resolvedName to resolvedSize
                    } else {
                        null
                    }
                } ?: ("uploaded_file_${System.currentTimeMillis()}" to -1L)

                viewModel.handleIntent(
                    FileListIntent.UploadFile(
                        uri = uri,
                        displayName = fileName,
                        size = fileSize
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // 监听 message 变化，显示 Snackbar 提示
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            // 清除 message，避免重复显示
            viewModel.handleIntent(FileListIntent.ClearMessage)
        }
    }

    // 普通错误统一用 Snackbar，避免在文件列表上堆叠固定错误卡片
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.handleIntent(FileListIntent.ClearError)
        }
    }

    val isPreviewVisible = state.previewFileName != null
    LaunchedEffect(isPreviewVisible) {
        onPreviewVisibilityChange(isPreviewVisible)
    }

    DisposableEffect(Unit) {
        onDispose {
            onPreviewVisibilityChange(false)
        }
    }

    // 预览页覆盖在文件列表之上（仿照 MainActivity 状态机模式）
    if (isPreviewVisible) {
        // 必须在 return 之前注册返回处理，否则系统返回键会直接退出 App
        PredictiveBackAnimatedContent(
            onBack = { viewModel.handleIntent(FileListIntent.ClosePreview) }
        ) { predictiveBackModifier ->
            FilePreviewScreen(
                fileName = state.previewFileName!!,
                previewState = state.previewState,
                onClose = { viewModel.handleIntent(FileListIntent.ClosePreview) },
                modifier = predictiveBackModifier
            )
        }
        return
    }

    val handleFileListBack = {
        if (state.canGoBack) {
            // 如果有上级目录，返回上级目录
            viewModel.handleIntent(FileListIntent.GoBack)
        } else {
            // 否则返回主页
            onBack()
        }
    }

    // 搜索激活时聚焦输入框
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) searchFocusRequester.requestFocus()
    }

    // 子目录返回上级 / 搜索关闭走普通 BackHandler，不带预测式动画
    BackHandler(enabled = isSearchActive || state.canGoBack) {
        when {
            isSearchActive -> {
                isSearchActive = false
                viewModel.handleIntent(FileListIntent.UpdateSearchQuery(""))
                focusManager.clearFocus()
            }
            state.canGoBack -> viewModel.handleIntent(FileListIntent.GoBack)
            else -> { /* 不会到达，由下方 PredictiveBackAnimatedContent 处理 */ }
        }
    }

    // 仅根文件页返回连接页参与预测式返回动画（页面级返回）
    PredictiveBackAnimatedContent(
        enabled = !state.canGoBack && !isSearchActive,
        onBack = onBack
    ) { predictiveBackModifier ->
        Scaffold(
            modifier = predictiveBackModifier,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // 工具栏（双模式：普通 / 搜索）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    if (isSearchActive) {
                        // 搜索模式工具栏
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(52.dp)
                                .padding(end = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                isSearchActive = false
                                viewModel.handleIntent(FileListIntent.UpdateSearchQuery(""))
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                            }
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.handleIntent(FileListIntent.UpdateSearchQuery(it)) },
                                placeholder = { Text(stringResource(R.string.hint_search_file), style = MaterialTheme.typography.bodyMedium) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(searchFocusRequester),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                            if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    viewModel.handleIntent(FileListIntent.UpdateSearchQuery(""))
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.desc_clear), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    } else {
                        // 普通模式工具栏：back | breadcrumb | 搜索 | ⋮
                        var showOverflowMenu by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(52.dp)
                                .padding(end = 0.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                if (state.canGoBack) viewModel.handleIntent(FileListIntent.GoBack) else onBack()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back_to_connection))
                            }
                            // 面包屑横向滚动区域
                            val breadcrumbScrollState = rememberScrollState()
                            val breadcrumbRootLabel = stringResource(R.string.breadcrumb_root)
                            val breadcrumbSegments = remember(state.currentPath, breadcrumbRootLabel) {
                                buildBreadcrumbSegments(state.currentPath, breadcrumbRootLabel)
                            }
                            LaunchedEffect(state.currentPath, breadcrumbScrollState.maxValue) {
                                if (breadcrumbScrollState.maxValue > 0) breadcrumbScrollState.animateScrollTo(breadcrumbScrollState.maxValue)
                            }
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(breadcrumbScrollState),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                breadcrumbSegments.forEachIndexed { index, segment ->
                                    val isLast = index == breadcrumbSegments.lastIndex
                                    if (index == 0) {
                                        Icon(
                                            Icons.Default.Home,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable(enabled = !isLast) { viewModel.handleIntent(FileListIntent.JumpToPath(segment.path)) },
                                            tint = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Icon(
                                            Icons.AutoMirrored.Filled.NavigateNext,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                        )
                                        Text(
                                            text = segment.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable(enabled = !isLast) { viewModel.handleIntent(FileListIntent.JumpToPath(segment.path)) }
                                        )
                                    }
                                }
                            }
                            // 搜索图标
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.desc_search), modifier = Modifier.size(20.dp))
                            }
                            // 溢出菜单（刷新/上传/新建文件夹/复制路径）
                            Box {
                                IconButton(onClick = { showOverflowMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
                                }
                                DropdownMenu(
                                    expanded = showOverflowMenu,
                                    onDismissRequest = { showOverflowMenu = false },
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.desc_refresh), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = { viewModel.handleIntent(FileListIntent.LoadFiles); showOverflowMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_upload_file), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = { Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                                type = "*/*"
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                            }
                                            filePickerLauncher.launch(intent)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_new_folder), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = { viewModel.handleIntent(FileListIntent.ShowCreateFolderDialog); showOverflowMenu = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.desc_copy_path), style = MaterialTheme.typography.bodyMedium) },
                                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                        onClick = {
                                            showOverflowMenu = false
                                            val fullPath = buildFullSmbPath(
                                                "${config.serverAddress}:${config.port}/${config.shareName}",
                                                state.currentPath
                                            )
                                            val clipboardManager = context.getSystemService(android.content.ClipboardManager::class.java)
                                            clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.label_path), fullPath))
                                            coroutineScope.launch { snackbarHostState.showSnackbar(pathCopiedMessage) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)
                }

                // 操作进度条
                if (state.isOperating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                }

                // 文件列表内容
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (state.filteredFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) stringResource(R.string.empty_search_result) else stringResource(R.string.empty_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { focusManager.clearFocus() },
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        items(state.filteredFiles) { file ->
                            FileItemRow(
                                file = file,
                                onClick = {
                                    focusManager.clearFocus()
                                    if (file.isDirectory) {
                                        viewModel.handleIntent(FileListIntent.EnterDirectory(file.name))
                                    } else {
                                        viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                    }
                                },
                                onLongClick = {
                                    focusManager.clearFocus()
                                    viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                },
                                showMenu = state.fileMenuPath == file.path,
                                onMenuDismiss = { viewModel.handleIntent(FileListIntent.HideFileMenu) },
                                onDownload = {
                                    checkPermissionAndDownload(file.path, file.name)
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onPreview = { viewModel.handleIntent(FileListIntent.PreviewFile(file.path, file.name)) },
                                onDelete = {
                                    viewModel.handleIntent(FileListIntent.DeleteFile(file.path, file.isDirectory))
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onRename = {
                                    viewModel.handleIntent(FileListIntent.ShowRenameDialog(file.path, file.name))
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 52.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }

                // 底部状态栏：显示项目数量
                if (!state.isLoading) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), thickness = 0.5.dp)
                    Text(
                        text = stringResource(R.string.file_list_item_count, state.filteredFiles.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // 对话框区域（覆盖在内容上方）
            Box {

                // 操作结果和普通错误都通过 Snackbar 显示，页面内只保留内容状态

                // 创建文件夹对话框
                if (state.showCreateFolderDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.handleIntent(FileListIntent.HideCreateFolderDialog)
                            folderName = ""
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text(stringResource(R.string.dialog_title_new_folder)) },
                        text = {
                            TextField(
                                value = folderName,
                                onValueChange = { folderName = it },
                                placeholder = { Text(stringResource(R.string.hint_folder_name)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (folderName.isNotBlank()) {
                                        viewModel.handleIntent(FileListIntent.CreateFolder(folderName))
                                        folderName = ""
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.action_create), color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.handleIntent(FileListIntent.HideCreateFolderDialog)
                                    folderName = ""
                                }
                            ) {
                                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                // 重命名对话框
                if (state.showRenameDialog) {
                    LaunchedEffect(state.renameCurrentName) {
                        renameName = state.renameCurrentName
                    }
                    AlertDialog(
                        onDismissRequest = {
                            viewModel.handleIntent(FileListIntent.HideRenameDialog)
                            renameName = ""
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        title = { Text(stringResource(R.string.dialog_title_rename)) },
                        text = {
                            TextField(
                                value = renameName,
                                onValueChange = { renameName = it },
                                placeholder = { Text(stringResource(R.string.hint_new_name)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                )
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    if (renameName.isNotBlank()) {
                                        viewModel.handleIntent(
                                            FileListIntent.RenameFile(state.renameFilePath, renameName)
                                        )
                                        renameName = ""
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.action_confirm), color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.handleIntent(FileListIntent.HideRenameDialog)
                                    renameName = ""
                                }
                            ) {
                                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                // 权限说明对话框
                if (showPermissionRationale) {
                    PermissionRationaleDialog(
                        permissionType = permissionType,
                        onConfirm = {
                            showPermissionRationale = false
                            permissionManager?.requestDownloadPermission(
                                onGranted = {
                                    // 权限授予后执行待处理的操作
                                    pendingDownloadFile?.let { (path, name) ->
                                        viewModel.handleIntent(FileListIntent.DownloadFile(path, name))
                                        pendingDownloadFile = null
                                    }
                                },
                                onDenied = {
                                    // 用户拒绝了权限
                                    pendingDownloadFile = null
                                },
                                onPermanentlyDenied = {
                                    // 用户永久拒绝了权限
                                    showPermissionDenied = true
                                    pendingDownloadFile = null
                                }
                            )
                        },
                        onDismiss = {
                            showPermissionRationale = false
                            pendingDownloadFile = null
                        }
                    )
                }

                // 权限永久拒绝对话框
                if (showPermissionDenied) {
                    PermissionPermanentlyDeniedDialog(
                        permissionType = permissionType,
                        onOpenSettings = {
                            showPermissionDenied = false
                            permissionManager?.openAppSettings()
                        },
                        onDismiss =  {
                            showPermissionDenied = false
                        }
                    )
                }
            }
        }
    }
}

private data class BreadcrumbSegment(
    val name: String,
    val path: String
)

private fun buildBreadcrumbSegments(currentPath: String, rootLabel: String): List<BreadcrumbSegment> {
    val parts = currentPath
        .trim('\\', '/')
        .split('\\', '/')
        .filter { it.isNotBlank() }

    return buildList {
        add(BreadcrumbSegment(rootLabel, ""))
        var path = ""
        parts.forEach { part ->
            path = if (path.isEmpty()) part else "$path\\$part"
            add(BreadcrumbSegment(part, path))
        }
    }
}

private fun buildFullSmbPath(serverPath: String, currentPath: String): String {
    val normalizedPath = currentPath
        .trim('\\', '/')
        .replace('\\', '/')

    return if (normalizedPath.isEmpty()) {
        "smb://$serverPath/"
    } else {
        "smb://$serverPath/$normalizedPath"
    }
}

@Composable
private fun FileItemRow(
    file: FileItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showMenu: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onDownload: () -> Unit = {},
    onPreview: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 文件类型色标图标
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(
                    color = fileIconTint(file).copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.extraSmall
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fileIcon(file),
                contentDescription = null,
                tint = fileIconTint(file),
                modifier = Modifier.size(16.dp)
            )
        }

        // 文件名 + 元信息（右侧）
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!file.isDirectory) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = FileTypeHelper.formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    file.lastModified?.let { date ->
                        Text(
                            text = FileTypeHelper.formatDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        // ⋮ 操作菜单
        Box {
            IconButton(onClick = onLongClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = onMenuDismiss,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                if (!file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_download), style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface, leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
                        onClick = { onDownload(); onMenuDismiss() }
                    )
                    if (FileTypeHelper.isPreviewable(file.name)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_preview), style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface, leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            onClick = { onPreview(); onMenuDismiss() }
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_rename), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.onSurface, leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    onClick = { onRename(); onMenuDismiss() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error),
                    onClick = { onDelete(); onMenuDismiss() }
                )
            }
        }
    }
}

@Composable
private fun fileIcon(file: FileItem) = when {
    file.isDirectory -> Icons.Default.Folder
    FileTypeHelper.isImageFile(file.name) -> Icons.Default.Image
    FileTypeHelper.isVideoFile(file.name) -> Icons.Default.VideoFile
    FileTypeHelper.isTextFile(file.name) -> Icons.AutoMirrored.Filled.InsertDriveFile
    file.name.lowercase().endsWith(".pdf") -> Icons.Default.PictureAsPdf
    file.name.lowercase().let { it.endsWith(".mp3") || it.endsWith(".flac") || it.endsWith(".aac") || it.endsWith(".ogg") } -> Icons.Default.AudioFile
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

@Composable
private fun fileIconTint(file: FileItem) = when {
    file.isDirectory -> MaterialTheme.colorScheme.primary
    FileTypeHelper.isImageFile(file.name) -> MaterialTheme.colorScheme.tertiary
    FileTypeHelper.isVideoFile(file.name) -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
