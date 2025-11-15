package com.qi.smb_share_android.ui.filelist

import androidx.compose.ui.graphics.Color
import android.content.Intent
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.FileItem
import com.qi.smb_share_android.util.FileTypeHelper
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
    val context = androidx.compose.ui.platform.LocalContext.current

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
            viewModel.handleIntent(FileListIntent.UploadFile(tempFile))
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
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = "搜索")
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        viewModel.handleIntent(
                                            FileListIntent.UpdateSearchQuery("")
                                        )
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
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.filteredFiles) { file ->
                            FileItemRow(
                                file = file,
                                onClick = {
                                    if (file.isDirectory) {
                                        viewModel.handleIntent(FileListIntent.EnterDirectory(file.name))
                                    } else {
                                        // 点击文件显示操作菜单
                                        viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                    }
                                },
                                onLongClick = {
                                    // 长按显示操作菜单
                                    viewModel.handleIntent(FileListIntent.ShowFileMenu(file.path))
                                },
                                showMenu = state.fileMenuPath == file.path,
                                onMenuDismiss = {
                                    viewModel.handleIntent(FileListIntent.HideFileMenu)
                                },
                                onDownload = {
                                    viewModel.handleIntent(
                                        FileListIntent.DownloadFile(file.path, file.name)
                                    )
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
                            Text("创建")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.handleIntent(FileListIntent.HideCreateFolderDialog)
                                folderName = ""
                            }
                        ) {
                            Text("取消")
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
                            Text("确定")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                viewModel.handleIntent(FileListIntent.HideRenameDialog)
                                renameName = ""
                            }
                        ) {
                            Text("取消")
                        }
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

