package com.qi.smbshare.ui.connection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.R
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.util.FToastUtil

@Composable
fun ConnectionScreen(
    viewModel: ConnectionViewModel,
    onNavigateToFileList: (SMBConfig) -> Unit,
    onNavigateToEdit: (SMBConfig?) -> Unit,
    onExit: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var configToDelete by remember { mutableStateOf<SMBConfig?>(null) }
    var backPressTime by remember { mutableLongStateOf(0L) }
    val pressAgainToExitMsg = stringResource(R.string.press_again_to_exit)

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressTime > 2000) {
            backPressTime = currentTime
            FToastUtil.show(context, pressAgainToExitMsg)
        } else {
            onExit()
        }
    }

    LaunchedEffect(state.navigateToFileList) {
        state.navigateToFileList?.let { config ->
            onNavigateToFileList(config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
        }
    }

    LaunchedEffect(state.navigateToEdit) {
        state.navigateToEdit?.let { config ->
            onNavigateToEdit(if (config.serverAddress.isEmpty() && config.shareName.isEmpty()) null else config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
        }
    }

    LaunchedEffect(state.testResult) {
        state.testResult?.let { result ->
            snackbarHostState.showSnackbar(result)
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 工具栏：标题 + 新建按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.title_connection_config),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = { onNavigateToEdit(null) }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_new_connection),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                thickness = 0.5.dp
            )

            if (state.savedConfigs.isEmpty()) {
                // 空状态
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Text(
                            text = stringResource(R.string.empty_state_title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.empty_state_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        TextButton(onClick = { onNavigateToEdit(null) }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.action_new_connection),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(state.savedConfigs) { index, config ->
                        SavedConfigRow(
                            config = config,
                            onConnect = { viewModel.handleIntent(ConnectionIntent.Connect(config)) },
                            onEdit = { onNavigateToEdit(config) },
                            onDelete = { configToDelete = config }
                        )
                        if (index < state.savedConfigs.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }

        // 删除确认对话框
        configToDelete?.let { config ->
            AlertDialog(
                onDismissRequest = { configToDelete = null },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(stringResource(R.string.dialog_title_delete)) },
                text = {
                    Text(
                        stringResource(
                            R.string.dialog_message_delete,
                            config.name.ifEmpty { stringResource(R.string.unnamed_config) }
                        )
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.handleIntent(ConnectionIntent.DeleteConnection(config.id))
                            configToDelete = null
                        }
                    ) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { configToDelete = null }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SavedConfigRow(
    config: SMBConfig,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 服务器图标
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        // 配置名称 + 地址信息
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = config.name.ifEmpty { stringResource(R.string.unnamed_config) },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${config.serverAddress}:${config.port}/${config.shareName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (config.isAnonymous) {
                    Text(
                        text = stringResource(R.string.label_anonymous),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 操作按钮组
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.action_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
