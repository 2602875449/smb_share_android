package com.qi.smb_share_android.ui.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.util.FToastUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToFileList: (SMBConfig) -> Unit,
    onNavigateToEdit: (SMBConfig?) -> Unit,
    onExit: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // 删除确认对话框状态
    var configToDelete by remember { mutableStateOf<SMBConfig?>(null) }
    
    // 双击返回退出功能
    var backPressTime by remember { mutableLongStateOf(0L) }
    
    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressTime > 2000) {
            // 第一次点击，显示提示
            backPressTime = currentTime
            FToastUtil.show(context, "再按一次退出应用")
        } else {
            // 第二次点击，退出应用
            onExit()
        }
    }

    // 监听导航到文件列表
    LaunchedEffect(state.navigateToFileList) {
        state.navigateToFileList?.let { config ->
            onNavigateToFileList(config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
        }
    }

    // 监听导航到编辑页面
    LaunchedEffect(state.navigateToEdit) {
        state.navigateToEdit?.let { config ->
            onNavigateToEdit(if (config.serverAddress.isEmpty() && config.shareName.isEmpty()) null else config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
        }
    }
    
    // 监听测试结果，使用Toast显示
    LaunchedEffect(state.testResult) {
        state.testResult?.let { result ->
            FToastUtil.show(context, result)
            // 显示后清除测试结果
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SMB连接配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    onNavigateToEdit(null)
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建连接")
            }
        },
        floatingActionButtonPosition = FabPosition.End
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 错误提示（测试失败也会显示在这里）
                state.error?.let { error ->
                    Card(
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
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            IconButton(onClick = { viewModel.handleIntent(ConnectionIntent.ClearError) }) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                    }
                }

                // 已保存的配置列表
                if (state.savedConfigs.isNotEmpty()) {
                    Text(
                        text = "已保存的连接",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.savedConfigs) { config ->
                            SavedConfigItem(
                                config = config,
                                onConnect = {
                                    viewModel.handleIntent(ConnectionIntent.Connect(config))
                                },
                                onEdit = {
                                    onNavigateToEdit(config)
                                },
                                onDelete = {
                                    configToDelete = config
                                }
                            )
                        }
                    }
                }
            }

            // 空状态提示 - 使用独立的Box覆盖在内容上方，确保居中
            if (state.savedConfigs.isEmpty() && state.error == null && state.testResult == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暂无保存的连接",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "点击右下角按钮新建连接",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // 删除确认对话框
        configToDelete?.let { config ->
            AlertDialog(
                onDismissRequest = { configToDelete = null },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("确认删除") },
                text = {
                    Text("确定要删除连接配置 \"${config.name.ifEmpty { "未命名配置" }}\" 吗？")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.handleIntent(ConnectionIntent.DeleteConnection(config.id))
                            configToDelete = null
                        }
                    ) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { configToDelete = null }
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            )
        }
    }
}

@Composable
private fun SavedConfigItem(
    config: SMBConfig,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name.ifEmpty { "未命名配置" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${config.serverAddress}:${config.port}/${config.shareName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (config.isAnonymous) {
                    Text(
                        text = "匿名登录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}


