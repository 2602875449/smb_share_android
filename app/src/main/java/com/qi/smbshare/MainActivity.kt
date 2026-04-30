package com.qi.smbshare

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.ui.connection.ConnectionScreen
import com.qi.smbshare.ui.connection.ConnectionViewModel
import com.qi.smbshare.ui.connection.EditConnectionScreen
import com.qi.smbshare.ui.filelist.FileListScreen
import com.qi.smbshare.ui.filelist.FileListViewModel
import com.qi.smbshare.ui.components.PredictiveBackAnimatedContent
import com.qi.smbshare.ui.settings.AboutScreen
import com.qi.smbshare.ui.settings.PrivacyPolicyScreen
import com.qi.smbshare.ui.settings.SettingsScreen
import com.qi.smbshare.ui.settings.SettingsViewModel
import com.qi.smbshare.ui.theme.SmbShareAndroidTheme
import com.qi.smbshare.ui.transfer.TransferManagerScreen
import com.qi.smbshare.ui.transfer.TransferManagerViewModel
import com.qi.smbshare.util.ApkInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalConfiguration
import com.qi.smbshare.util.LanguageHelper

private const val TAG = "MainActivity"

enum class NavigationTab {
    CONNECTION, FILE, TRANSFER_MANAGER, SETTINGS
}

enum class SettingsDestination {
    MAIN, PRIVACY_POLICY, ABOUT
}

class MainActivity : ComponentActivity() {
    private val apkInstaller by lazy { ApkInstaller(this) }

    override fun attachBaseContext(newBase: Context) {
        // 在 Activity 附着 Context 时同步应用选择的语言，避免重启后依旧是系统默认语言
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 读取主题设置
            val dataStoreManager = remember { DataStoreManager(this) }
            val themeMode by dataStoreManager.getThemeMode().collectAsStateWithLifecycle(initialValue = com.qi.smbshare.data.model.ThemeMode.SYSTEM)
            
            // 根据主题模式决定是否使用深色主题
            val darkTheme = when (themeMode) {
                com.qi.smbshare.data.model.ThemeMode.LIGHT -> false
                com.qi.smbshare.data.model.ThemeMode.DARK -> true
                com.qi.smbshare.data.model.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            SmbShareAndroidTheme(darkTheme = darkTheme) {
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

@SuppressLint("ContextCastToActivity")
@Composable
fun AppContent(onInstallApk: (File) -> Unit) {
    var selectedTab by remember { mutableStateOf(NavigationTab.CONNECTION) }
    var currentConfig: SMBConfig? by remember { mutableStateOf(null) }
    var editConfig: SMBConfig? by remember { mutableStateOf(null) }
    var showEditScreen by remember { mutableStateOf(false) }
    var initialPath by remember { mutableStateOf("") }
    var isRestoringLastAccess by remember { mutableStateOf(true) }
    var settingsDestination by remember { mutableStateOf(SettingsDestination.MAIN) }
    var isFilePreviewVisible by remember { mutableStateOf(false) }
    val connectionViewModel: ConnectionViewModel = viewModel()
    val transferManagerViewModel: TransferManagerViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = LocalConfiguration.current
    
    // 获取当前语言环境，判断是否为中文
    val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    val isChinese = currentLocale.language == "zh"
    
    // 监听活动任务数量
    val transferState by transferManagerViewModel.state.collectAsStateWithLifecycle()
    val activeTransferCount = transferState.activeTransferCount

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
        } else if (isRestoringLastAccess) {
            // 如果配置列表为空，也停止恢复尝试
            isRestoringLastAccess = false
        }
    }

    // 处理设置页面的系统返回键
    if (selectedTab == NavigationTab.SETTINGS) {
        // 从设置主页返回到连接管理标签
        if (settingsDestination == SettingsDestination.MAIN) {
            BackHandler {
                selectedTab = NavigationTab.CONNECTION
                isFilePreviewVisible = false
            }
        }
    }

    LaunchedEffect(selectedTab, currentConfig, showEditScreen) {
        if (selectedTab != NavigationTab.FILE || currentConfig == null || showEditScreen) {
            isFilePreviewVisible = false
        }
    }

    val useFileListImmersiveBars = selectedTab == NavigationTab.FILE &&
        currentConfig != null &&
        !showEditScreen

    Scaffold(
        contentWindowInsets = if (useFileListImmersiveBars) {
            WindowInsets(0.dp)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {
            if (!isFilePreviewVisible) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .height(if (isChinese) 70.dp else 64.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Cloud, contentDescription = stringResource(R.string.nav_connection)) },
                        label = if (isChinese) { { Text(stringResource(R.string.nav_connection)) } } else null,
                        selected = selectedTab == NavigationTab.CONNECTION,
                        onClick = {
                            selectedTab = NavigationTab.CONNECTION
                            isFilePreviewVisible = false
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.nav_files)) },
                        label = if (isChinese) { { Text(stringResource(R.string.nav_files)) } } else null,
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
                        icon = {
                            BadgedIcon(
                                icon = Icons.Default.SwapVert,
                                badgeCount = activeTransferCount,
                                hasActiveTransfers = activeTransferCount > 0
                            )
                        },
                        label = if (isChinese) { { Text(stringResource(R.string.nav_transfer_manager)) } } else null,
                        selected = selectedTab == NavigationTab.TRANSFER_MANAGER,
                        onClick = {
                            selectedTab = NavigationTab.TRANSFER_MANAGER
                            isFilePreviewVisible = false
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.nav_settings)) },
                        label = if (isChinese) { { Text(stringResource(R.string.nav_settings)) } } else null,
                        selected = selectedTab == NavigationTab.SETTINGS,
                        onClick = {
                            selectedTab = NavigationTab.SETTINGS
                            settingsDestination = SettingsDestination.MAIN
                            isFilePreviewVisible = false
                        },
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
                                isFilePreviewVisible = false
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
                                isFilePreviewVisible = false
                            },
                            onPreviewVisibilityChange = { visible ->
                                isFilePreviewVisible = visible
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
                                    text = stringResource(R.string.msg_please_connect_server),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Button(
                                    onClick = { selectedTab = NavigationTab.CONNECTION }
                                ) {
                                    Text(stringResource(R.string.action_go_to_connection))
                                }
                            }
                        }
                    }
                }
                selectedTab == NavigationTab.TRANSFER_MANAGER -> {
                    // 显示传输管理界面
                    TransferManagerScreen(
                        viewModel = transferManagerViewModel,
                        onInstallApk = onInstallApk
                    )
                }
                selectedTab == NavigationTab.SETTINGS -> {
                    // 显示设置相关界面
                    when (settingsDestination) {
                        SettingsDestination.MAIN -> {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateToPrivacyPolicy = {
                                    settingsDestination = SettingsDestination.PRIVACY_POLICY
                                },
                                onNavigateToAbout = {
                                    settingsDestination = SettingsDestination.ABOUT
                                }
                            )
                        }
                        SettingsDestination.PRIVACY_POLICY -> {
                            PredictiveBackAnimatedContent(
                                onBack = { settingsDestination = SettingsDestination.MAIN }
                            ) { predictiveBackModifier ->
                                PrivacyPolicyScreen(
                                    onBack = {
                                        settingsDestination = SettingsDestination.MAIN
                                    },
                                    modifier = predictiveBackModifier
                                )
                            }
                        }
                        SettingsDestination.ABOUT -> {
                            PredictiveBackAnimatedContent(
                                onBack = { settingsDestination = SettingsDestination.MAIN }
                            ) { predictiveBackModifier ->
                                AboutScreen(
                                    onBack = {
                                        settingsDestination = SettingsDestination.MAIN
                                    },
                                    onNavigateToPrivacyPolicy = {
                                        settingsDestination = SettingsDestination.PRIVACY_POLICY
                                    },
                                    modifier = predictiveBackModifier
                                )
                            }
                        }
                    }
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

/**
 * 带徽章和动画的图标组件
 * 支持徽章数量变化动画和活动任务时的脉冲动画
 */
@Composable
fun BadgedIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeCount: Int,
    hasActiveTransfers: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 脉冲动画 - 当有活动任务时
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.15f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )
        
        // 应用脉冲动画（仅在有活动任务时）
        val scale = if (hasActiveTransfers) pulseScale else 1f
        
        Icon(
            imageVector = icon,
            contentDescription = "传输管理",
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
        
        // 徽章 - 显示活动任务数量
        if (badgeCount > 0) {
            // 徽章数量变化动画
            val animatedBadgeCount by animateIntAsState(
                targetValue = badgeCount,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "badgeCount"
            )
            
            // 徽章缩放动画
            val badgeScale by animateFloatAsState(
                targetValue = if (badgeCount > 0) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "badgeScale"
            )
            
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-4).dp)
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = badgeScale
                        scaleY = badgeScale
                    }
                    .background(
                        color = MaterialTheme.colorScheme.error,
                        shape = CircleShape
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = if (animatedBadgeCount > 99) "99+" else animatedBadgeCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun animateIntAsState(
    targetValue: Int,
    animationSpec: AnimationSpec<Float>,
    label: String
): State<Int> {
    val animatedFloat by animateFloatAsState(
        targetValue = targetValue.toFloat(),
        animationSpec = animationSpec,
        label = label
    )
    return remember { derivedStateOf { animatedFloat.toInt() } }
}
