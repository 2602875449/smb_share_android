package com.qi.smb_share_android.ui.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.util.FToastUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditConnectionScreen(
    viewModel: ConnectionViewModel,
    configToEdit: SMBConfig? = null,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    onConnectSuccess: (SMBConfig) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // 处理系统返回键
    BackHandler(onBack = onBack)

    // 监听测试结果，使用Toast显示
    LaunchedEffect(state.testResult) {
        state.testResult?.let { result ->
            FToastUtil.show(context, result)
            // 显示后清除测试结果
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
    }

    // 如果是编辑模式，初始化表单
    LaunchedEffect(configToEdit) {
        if (configToEdit != null) {
            viewModel.handleIntent(ConnectionIntent.EditConfig(configToEdit))
        } else {
            viewModel.handleIntent(ConnectionIntent.ClearForm)
        }
    }

    // 监听保存成功 - 当 isLoading 从 true 变为 false，且 currentConfig 为 null 时，表示保存成功
    var previousLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.currentConfig) {
        if (previousLoading && !state.isLoading && state.currentConfig == null && state.error == null) {
            // 保存成功后，currentConfig 会被清空，此时可以返回
            onSaveSuccess()
        }
        previousLoading = state.isLoading
    }

    // 监听连接成功
    LaunchedEffect(state.navigateToFileList) {
        state.navigateToFileList?.let { config ->
            onConnectSuccess(config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    Text(
                        text = if (configToEdit != null) "编辑连接配置" else "新建连接配置",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
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

                // 配置名称
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.formName,
                    onValueChange = {
                        viewModel.handleIntent(
                            ConnectionIntent.UpdateFormField(FormField.NAME, it)
                        )
                    },
                    label = { Text("配置名称（可选）") },
                    singleLine = true
                )

                // 服务器地址
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.formServerAddress,
                    onValueChange = {
                        viewModel.handleIntent(
                            ConnectionIntent.UpdateFormField(FormField.SERVER_ADDRESS, it)
                        )
                    },
                    label = { Text("服务器地址 *") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) }
                )

                // 端口
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.formPort,
                    onValueChange = {
                        viewModel.handleIntent(
                            ConnectionIntent.UpdateFormField(FormField.PORT, it)
                        )
                    },
                    label = { Text("端口") },
                    singleLine = true
                )

                // 共享名称
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.formShareName,
                    onValueChange = {
                        viewModel.handleIntent(
                            ConnectionIntent.UpdateFormField(FormField.SHARE_NAME, it)
                        )
                    },
                    label = { Text("共享名称 *") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )

                // 匿名登录开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = state.isAnonymous,
                        onCheckedChange = {
                            viewModel.handleIntent(ConnectionIntent.ToggleAnonymous)
                        }
                    )
                    Text("匿名登录")
                }

                // 用户名和密码（匿名登录时隐藏）
                if (!state.isAnonymous) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.formUsername,
                        onValueChange = {
                            viewModel.handleIntent(
                                ConnectionIntent.UpdateFormField(FormField.USERNAME, it)
                            )
                        },
                        label = { Text("用户名") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.formPassword,
                        onValueChange = {
                            viewModel.handleIntent(
                                ConnectionIntent.UpdateFormField(FormField.PASSWORD, it)
                            )
                        },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                    )
                }

                // 按钮组 - 纵向排列，放在滚动区域内
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 测试连接按钮
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) {
                                viewModel.handleIntent(ConnectionIntent.TestConnection(config))
                            }
                        },
                        enabled = !state.isTesting && canConnect(state)
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("测试连接")
                        }
                    }

                    // 保存配置按钮
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) {
                                viewModel.handleIntent(ConnectionIntent.SaveConnection(config))
                            }
                        },
                        enabled = !state.isLoading && canSave(state)
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("保存配置")
                        }
                    }

                    // 连接按钮
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) {
                                viewModel.handleIntent(ConnectionIntent.Connect(config))
                            }
                        },
                        enabled = !state.isConnecting && canConnect(state)
                    ) {
                        if (state.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("连接")
                        }
                    }
                }
            }
        }
    }
}

private fun buildConfigFromState(state: ConnectionState): SMBConfig? {
    val current = state.currentConfig
    return current?.copy(
        name = state.formName,
        serverAddress = state.formServerAddress,
        port = state.formPort.toIntOrNull() ?: 445,
        shareName = state.formShareName,
        username = if (state.isAnonymous) "" else state.formUsername,
        password = if (state.isAnonymous) "" else state.formPassword,
        isAnonymous = state.isAnonymous
    )
        ?: if (state.formServerAddress.isNotEmpty() && state.formShareName.isNotEmpty()) {
            SMBConfig(
                name = state.formName,
                serverAddress = state.formServerAddress,
                port = state.formPort.toIntOrNull() ?: 445,
                shareName = state.formShareName,
                username = if (state.isAnonymous) "" else state.formUsername,
                password = if (state.isAnonymous) "" else state.formPassword,
                isAnonymous = state.isAnonymous
            )
        } else {
            null
        }
}

private fun canConnect(state: ConnectionState): Boolean {
    return state.formServerAddress.isNotEmpty() && state.formShareName.isNotEmpty()
}

private fun canSave(state: ConnectionState): Boolean {
    return canConnect(state)
}

