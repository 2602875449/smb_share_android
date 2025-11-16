package com.qi.smb_share_android.ui.filelist

import androidx.compose.ui.graphics.Color
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.FileItem
import com.qi.smb_share_android.ui.components.PermissionPermanentlyDeniedDialog
import com.qi.smb_share_android.ui.components.PermissionRationaleDialog
import com.qi.smb_share_android.ui.components.PermissionType
import com.qi.smb_share_android.util.FileTypeHelper
import com.qi.smb_share_android.util.PermissionManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: FileListViewModel,
    onInstallApk: (File) -> Unit,
    config: com.qi.smb_share_android.data.model.SMBConfig,
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var folderName by remember { mutableStateOf("") }
    var renameName by remember { mutableStateOf("") }
    var showFabMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    
    // 权限相关状态
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPermissionDenied by remember { mutableStateOf(false) }
    var permissionType by remember { mutableStateOf(PermissionType.STORAGE_DOWNLOAD) }
    var pendingDownloadFile by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingUploadFile by remember { mutableStateOf<File?>(null) }
    
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
        activity?.let { 
            PermissionManager(it, permissionLauncher).also { 
                permissionManagerRef = it 
            }
        }
    }

    // 权限检查和下载文件的辅助函数
    val checkPermissionAndDownload = { filePath: String, fileName: String ->
        permissionManager?.let { pm ->
            when (pm.checkStoragePermission()) {
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
    
    // 权限检查和上传文件的辅助函数
    val checkPermissionAndUpload = { file: File ->
        permissionManager?.let { pm ->
            when (pm.checkStoragePermission()) {
                PermissionManager.PermissionStatus.GRANTED -> {
                    // 权限已授予，直接上传
                    viewModel.handleIntent(FileListIntent.UploadFile(file))
                }
                PermissionManager.PermissionStatus.DENIED -> {
                    // 需要请求权限
                    pendingUploadFile = file
                    permissionType = PermissionType.STORAGE_UPLOAD
                    showPermissionRationale = true
                }
                PermissionManager.PermissionStatus.PERMANENTLY_DENIED -> {
                    // 权限被永久拒绝
                    permissionType = PermissionType.STORAGE_UPLOAD
                    showPermissionDenied = true
                }
            }
        } ?: run {
            // 如果无法获取权限管理器，直接尝试上传
            viewModel.handleIntent(FileListIntent.UploadFile(file))
        }
    }
    
    // 文件选择器
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.data?.let { uri ->
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = uri.lastPathSegment ?: "uploaded_file"
            val tempFile = File(context.cacheDir, fileName)
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            // 使用权限检查函数
            checkPermissionAndUpload(tempFile)
        }
    }

    // 处理系统返回键 - 如果有上级目录则返回上级目录，否则返回主页
    BackHandler {
        if (state.canGoBack) {
            // 如果有上级目录，返回上级目录
            viewModel.handleIntent(FileListIntent.GoBack)
        } else {
            // 否则返回主页
            onBack()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = Color.Transparent,
                tonalElevation = 4.dp
            ) {
                // 普通模式：显示标题和路径
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回连接配置")
                    }
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "文件列表",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${config.serverAddress}:${config.port}/${config.shareName}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.currentPath.isNotEmpty()) {
                            Text(
                                text = state.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (state.canGoBack) {
                        IconButton(onClick = { viewModel.handleIntent(FileListIntent.GoBack) }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "上级目录")
                        }
                    }
                    IconButton(onClick = { viewModel.handleIntent(FileListIntent.LoadFiles) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            }
        },
        floatingActionButton = {
            // FAB菜单
            if (showFabMenu) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    // 上传文件按钮（放在上方）
                    FloatingActionButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "*/*"
                                addCategory(Intent.CATEGORY_OPENABLE)
                            }
                            filePickerLauncher.launch(intent)
                            showFabMenu = false
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "上传文件")
                            Text("上传", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    // 新建文件夹按钮（放在下方，更靠近主FAB）
                    FloatingActionButton(
                        onClick = {
                            viewModel.handleIntent(FileListIntent.ShowCreateFolderDialog)
                            showFabMenu = false
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                            Text("新建", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            // 主FAB按钮
            FloatingActionButton(
                onClick = { showFabMenu = !showFabMenu }
            ) {
                Icon(
                    if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = if (showFabMenu) "关闭菜单" else "打开菜单"
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 搜索栏 - 始终显示在文件列表上方
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = { query ->
                                viewModel.handleIntent(FileListIntent.UpdateSearchQuery(query))
                            },
                            placeholder = { Text("搜索文件...") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.handleIntent(
                                            FileListIntent.UpdateSearchQuery("")
                                        )
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                // 文件列表内容
                if (state.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (state.filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (state.searchQuery.isNotEmpty()) "未找到匹配的文件" else "文件夹为空",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) {
                                // 点击列表空白区域时清除焦点，关闭键盘
                                focusManager.clearFocus()
                            },
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredFiles) { file ->
                            FileItemRow(
                                file = file,
                                onClick = {
                                    // 清除搜索框焦点
                                    focusManager.clearFocus()
                                    if (file.isDirectory) {
                                        viewModel.handleIntent(FileListIntent.EnterDirectory(file.name))
                                    } else {
                                        // 点击文件显示操作菜单
                                        viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                    }
                                },
                                onLongClick = {
                                    // 清除搜索框焦点
                                    focusManager.clearFocus()
                                    // 长按显示操作菜单
                                    viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                },
                                showMenu = state.fileMenuPath == file.path,
                                onMenuDismiss = {
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onDownload = {
                                    checkPermissionAndDownload(file.path, file.name)
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onDelete = {
                                    viewModel.handleIntent(FileListIntent.DeleteFile(file.path))
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onRename = {
                                    viewModel.handleIntent(
                                        FileListIntent.ShowRenameDialog(file.path, file.name)
                                    )
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
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
                            onClick = { viewModel.handleIntent(FileListIntent.ClearError) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                }
            }

            // 下载进度
            state.downloadItem?.let { downloadItem ->
                if (downloadItem.status == com.qi.smb_share_android.data.model.DownloadStatus.DOWNLOADING) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "正在下载: ${downloadItem.fileName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (downloadItem.progress >= 0) {
                                LinearProgressIndicator(
                                    progress = { downloadItem.progress / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "${downloadItem.progress}% - ${
                                        FileTypeHelper.formatFileSize(downloadItem.downloadedBytes)
                                    } / ${FileTypeHelper.formatFileSize(downloadItem.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    text = "已下载: ${FileTypeHelper.formatFileSize(downloadItem.downloadedBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }

            // 下载完成提示
            state.downloadedFile?.let { file ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "下载完成: ${file.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (FileTypeHelper.isApkFile(file.name)) {
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    onInstallApk(file)
                                    viewModel.handleIntent(FileListIntent.ClearDownload)
                                }
                            ) {
                                Text("安装APK")
                            }
                        }
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { viewModel.handleIntent(FileListIntent.ClearDownload) }
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }

            // 创建文件夹对话框
            if (state.showCreateFolderDialog) {
                AlertDialog(
                    onDismissRequest = {
                        viewModel.handleIntent(FileListIntent.HideCreateFolderDialog)
                        folderName = ""
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("新建文件夹") },
                    text = {
                        TextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            placeholder = { Text("请输入文件夹名称") },
                            singleLine = true
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
                            Text("创建", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.handleIntent(FileListIntent.HideCreateFolderDialog)
                                folderName = ""
                            }
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    title = { Text("重命名") },
                    text = {
                        TextField(
                            value = renameName,
                            onValueChange = { renameName = it },
                            placeholder = { Text("请输入新名称") },
                            singleLine = true
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
                            Text("确定", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.handleIntent(FileListIntent.HideRenameDialog)
                                renameName = ""
                            }
                        ) {
                            Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
            }
        }
        
        // 权限说明对话框
        if (showPermissionRationale) {
            PermissionRationaleDialog(
                permissionType = permissionType,
                onConfirm = {
                    showPermissionRationale = false
                    permissionManager?.requestStoragePermission(
                        onGranted = {
                            // 权限授予后执行待处理的操作
                            pendingDownloadFile?.let { (path, name) ->
                                viewModel.handleIntent(FileListIntent.DownloadFile(path, name))
                                pendingDownloadFile = null
                            }
                            pendingUploadFile?.let { file ->
                                viewModel.handleIntent(FileListIntent.UploadFile(file))
                                pendingUploadFile = null
                            }
                        },
                        onDenied = {
                            // 用户拒绝了权限
                            pendingDownloadFile = null
                            pendingUploadFile = null
                        },
                        onPermanentlyDenied = {
                            // 用户永久拒绝了权限
                            showPermissionDenied = true
                            pendingDownloadFile = null
                            pendingUploadFile = null
                        }
                    )
                },
                onDismiss = {
                    showPermissionRationale = false
                    pendingDownloadFile = null
                    pendingUploadFile = null
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
                onDismiss = {
                    showPermissionDenied = false
                }
            )
        }
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
    onDelete: () -> Unit = {},
    onRename: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(
                if (!file.isDirectory) {
                    Modifier.clickable(onClick = onLongClick, onClickLabel = "下载")
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = if (file.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!file.isDirectory) {
                    Text(
                        text = FileTypeHelper.formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                file.lastModified?.let { date ->
                    Text(
                        text = date.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!file.isDirectory) {
                Box {
                    IconButton(onClick = onLongClick) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = onMenuDismiss
                    ) {
                        DropdownMenuItem(
                            text = { Text("下载") },
                            onClick = {
                                onDownload()
                                onMenuDismiss()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                onRename()
                                onMenuDismiss()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Edit, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                onDelete()
                                onMenuDismiss()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}

