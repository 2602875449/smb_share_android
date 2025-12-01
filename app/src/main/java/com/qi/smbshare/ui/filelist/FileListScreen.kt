package com.qi.smbshare.ui.filelist

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.R
import com.qi.smbshare.data.model.FileItem
import com.qi.smbshare.ui.components.PermissionPermanentlyDeniedDialog
import com.qi.smbshare.ui.components.PermissionRationaleDialog
import com.qi.smbshare.ui.components.PermissionType
import com.qi.smbshare.util.FileTypeHelper
import com.qi.smbshare.util.PermissionManager
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(
    viewModel: FileListViewModel,
    onInstallApk: (File) -> Unit,
    config: com.qi.smbshare.data.model.SMBConfig,
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
    val snackbarHostState = remember { SnackbarHostState() }
    
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
        activity?.let { it ->
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
            try {
                // 获取真实文件名
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                } ?: "uploaded_file_${System.currentTimeMillis()}"
                
                // 创建临时文件并复制内容
                val tempFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                // 验证文件是否成功创建且有内容
                if (tempFile.exists() && tempFile.length() > 0) {
                    // 使用权限检查函数
                    checkPermissionAndUpload(tempFile)
                }
            } catch (e: Exception) {
                // 异常会在 ViewModel 层处理
                e.printStackTrace()
            }
        }
    }

    // 监听 message 变化，显示 Snackbar 提示
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            if (message.contains("下载已开始") || message.contains("上传已开始")) {
                snackbarHostState.showSnackbar(message)
                // 清除 message，避免重复显示
                viewModel.handleIntent(FileListIntent.ClearMessage)
            }
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
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.desc_back_to_connection))
                    }
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.title_file_list),
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
                            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.desc_parent_directory))
                        }
                    }
                    IconButton(onClick = { viewModel.handleIntent(FileListIntent.LoadFiles) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.desc_refresh))
                    }
                }
            }
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // FAB菜单项 - 只在展开时显示
                if (showFabMenu) {
                    // 上传文件按钮
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
                        Icon(Icons.Default.Upload, contentDescription = stringResource(R.string.action_upload_file))
                    }
                    // 新建文件夹按钮
                    FloatingActionButton(
                        onClick = {
                            viewModel.handleIntent(FileListIntent.ShowCreateFolderDialog)
                            showFabMenu = false
                        },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.action_new_folder))
                    }
                }
                // 主FAB按钮 - 始终显示
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu }
                ) {
                    Icon(
                        if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (showFabMenu) stringResource(R.string.desc_close_menu) else stringResource(R.string.desc_open_menu)
                    )
                }
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
                            placeholder = { Text(stringResource(R.string.hint_search_file)) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusRequester),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.desc_search))
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.handleIntent(
                                            FileListIntent.UpdateSearchQuery("")
                                        )
                                        focusManager.clearFocus()
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.desc_clear))
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

                // 操作进度提示 - 轻量级顶部进度条
                if (state.isOperating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
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
                            text = if (state.searchQuery.isNotEmpty()) stringResource(R.string.empty_search_result) else stringResource(R.string.empty_folder),
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
                                    // 长按显示操作菜单（文件和目录都支持）
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
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    }
                }
            }

            // 成功消息提示已改为使用 Snackbar 显示，不再使用 Card

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
                    onDismiss =  {
                        showPermissionDenied = false
                    }
                )
            }
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
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
                    Icons.AutoMirrored.Filled.InsertDriveFile
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
                    maxLines = 2,
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
                        text = FileTypeHelper.formatDate(date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // 文件和目录都显示操作菜单按钮
            Box {
                IconButton(onClick = onLongClick) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = onMenuDismiss,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    // 只有文件才显示下载选项
                    if (!file.isDirectory) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_download)) },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.onSurface,
                                leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                onDownload()
                                onMenuDismiss()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Download, contentDescription = null)
                            }
                        )
                    }
                    // 重命名选项（文件和目录都支持）
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.onSurface,
                            leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        onClick = {
                            onRename()
                            onMenuDismiss()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    // 删除选项（文件和目录都支持）
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
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

