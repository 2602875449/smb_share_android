package com.qi.smb_share_android

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qi.smb_share_android.data.local.DataStoreManager
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.ui.connection.ConnectionScreen
import com.qi.smb_share_android.ui.connection.ConnectionViewModel
import com.qi.smb_share_android.ui.connection.EditConnectionScreen
import com.qi.smb_share_android.ui.download.DownloadHistoryScreen
import com.qi.smb_share_android.ui.download.DownloadHistoryViewModel
import com.qi.smb_share_android.ui.filelist.FileListScreen
import com.qi.smb_share_android.ui.filelist.FileListViewModel
import com.qi.smb_share_android.ui.theme.SmbShareAndroidTheme
import com.qi.smb_share_android.util.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MainActivity"

enum class NavigationTab {
    CONNECTION, FILE, DOWNLOAD_HISTORY
}

class MainActivity : ComponentActivity() {
    private val apkInstaller by lazy { ApkInstaller(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmbShareAndroidTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        onInstallApk = { file ->
                            apkInstaller.installApk(file)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppContent(onInstallApk: (File) -> Unit) {
    var selectedTab by remember { mutableStateOf(NavigationTab.CONNECTION) }
    var currentConfig: SMBConfig? by remember { mutableStateOf(null) }
    var editConfig: SMBConfig? by remember { mutableStateOf<SMBConfig?>(null) }
    var showEditScreen by remember { mutableStateOf(false) }
    var initialPath by remember { mutableStateOf("") }
    var isRestoringLastAccess by remember { mutableStateOf(true) }
    val connectionViewModel: ConnectionViewModel = viewModel()
    val downloadHistoryViewModel: DownloadHistoryViewModel = viewModel()
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    val context = androidx.compose.ui.platform.LocalContext.current

    // 监听连接配置列表加载
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    
    // 启动时恢复最后访问的服务器和路径
    LaunchedEffect(connectionState.savedConfigs) {
        if (isRestoringLastAccess && connectionState.savedConfigs.isNotEmpty()) {
            isRestoringLastAccess = false
            val dataStoreManager = DataStoreManager(context)
            val (lastConfigId, lastPath) = withContext(Dispatchers.IO) {
                dataStoreManager.getLastAccess()
            }
            
            if (lastConfigId != null) {
                // 查找对应的配置
                val foundConfig = connectionState.savedConfigs.find { it.id == lastConfigId }
                
                if (foundConfig != null) {
                    currentConfig = foundConfig
                    initialPath = lastPath ?: ""
                    selectedTab = NavigationTab.FILE
                }
            } else {
                isRestoringLastAccess = false
            }
        } else if (isRestoringLastAccess && connectionState.savedConfigs.isEmpty()) {
            // 如果配置列表为空，也停止恢复尝试
            isRestoringLastAccess = false
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .height(70.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    )
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "连接管理") },
                    label = { Text("连接管理") },
                    selected = selectedTab == NavigationTab.CONNECTION,
                    onClick = { selectedTab = NavigationTab.CONNECTION },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Folder, contentDescription = "文件") },
                    label = { Text("文件") },
                    selected = selectedTab == NavigationTab.FILE,
                    enabled = currentConfig != null,
                    onClick = { 
                        if (currentConfig != null) {
                            selectedTab = NavigationTab.FILE
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, contentDescription = "下载历史") },
                    label = { Text("下载历史") },
                    selected = selectedTab == NavigationTab.DOWNLOAD_HISTORY,
                    onClick = { selectedTab = NavigationTab.DOWNLOAD_HISTORY },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                showEditScreen -> {
                    // 显示编辑/新建连接页面
                    EditConnectionScreen(
                        viewModel = connectionViewModel,
                        configToEdit = editConfig,
                        onBack = {
                            showEditScreen = false
                            editConfig = null
                        },
                        onSaveSuccess = {
                            showEditScreen = false
                            editConfig = null
                        },
                        onConnectSuccess = { config ->
                            showEditScreen = false
                            editConfig = null
                            currentConfig = config
                            initialPath = "" // 从编辑页连接时，重置路径
                            selectedTab = NavigationTab.FILE
                        }
                    )
                }
                selectedTab == NavigationTab.CONNECTION -> {
                    // 显示连接配置列表界面
                    ConnectionScreen(
                        viewModel = connectionViewModel,
                        onNavigateToFileList = { config ->
                            currentConfig = config
                            initialPath = "" // 从连接列表页导航时，重置路径
                            selectedTab = NavigationTab.FILE
                        },
                        onNavigateToEdit = { config ->
                            editConfig = config
                            showEditScreen = true
                        },
                        onExit = {
                            activity?.finish()
                        }
                    )
                }
                selectedTab == NavigationTab.FILE -> {
                    // 显示文件列表界面
                    if (currentConfig != null) {
                        val fileListViewModel: FileListViewModel = viewModel(
                            factory = FileListViewModelFactory(
                                application = androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application,
                                config = currentConfig!!,
                                initialPath = initialPath
                            ),
                            key = "${currentConfig!!.id}_$initialPath"
                        )
                        
                        // 监听连接错误，如果连接失败则返回连接列表页
                        val state by fileListViewModel.state.collectAsStateWithLifecycle()
                        LaunchedEffect(state.error) {
                            if (state.error != null && state.error!!.contains("连接失败")) {
                                // 连接失败，返回连接列表页
                                selectedTab = NavigationTab.CONNECTION
                                currentConfig = null
                                initialPath = ""
                            }
                        }
                        
                        FileListScreen(
                            viewModel = fileListViewModel,
                            onInstallApk = onInstallApk,
                            config = currentConfig!!,
                            onBack = {
                                // 文件页的返回按钮可以返回到连接管理页
                                selectedTab = NavigationTab.CONNECTION
                                currentConfig = null
                                initialPath = ""
                            }
                        )
                    } else {
                        // 如果没有连接，显示提示并切换到连接管理页
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "请先连接服务器",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Button(
                                    onClick = { selectedTab = NavigationTab.CONNECTION }
                                ) {
                                    Text("前往连接管理")
                                }
                            }
                        }
                    }
                }
                selectedTab == NavigationTab.DOWNLOAD_HISTORY -> {
                    // 显示下载历史界面
                    DownloadHistoryScreen(
                        viewModel = downloadHistoryViewModel,
                        onInstallApk = onInstallApk
                    )
                }
            }
        }
    }
}

class FileListViewModelFactory(
    private val application: android.app.Application,
    private val config: SMBConfig,
    private val initialPath: String = ""
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileListViewModel(application, config, initialPath) as T
        }
        Log.e(TAG, "创建ViewModel失败: 未知的ViewModel类 ${modelClass.simpleName}")
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
    }
}

