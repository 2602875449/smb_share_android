package com.qi.smbshare.ui.connection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.R
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource


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
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.handleIntent(ConnectionIntent.StopDiscovery) }
    }

    LaunchedEffect(state.testResult) {
        state.testResult?.let { result ->
            snackbarHostState.showSnackbar(result)
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.handleIntent(ConnectionIntent.ClearError)
        }
    }

    LaunchedEffect(state.discoveryError) {
        state.discoveryError?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.handleIntent(ConnectionIntent.ClearDiscoveryError)
        }
    }

    LaunchedEffect(configToEdit) {
        if (configToEdit != null) {
            viewModel.handleIntent(ConnectionIntent.EditConfig(configToEdit))
        } else {
            viewModel.handleIntent(ConnectionIntent.ClearForm)
        }
    }

    var previousLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading, state.currentConfig) {
        if (previousLoading && !state.isLoading && state.currentConfig == null && state.error == null) {
            onSaveSuccess()
        }
        previousLoading = state.isLoading
    }

    LaunchedEffect(state.navigateToFileList) {
        state.navigateToFileList?.let { config ->
            onConnectSuccess(config)
            viewModel.handleIntent(ConnectionIntent.ClearNavigation)
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
                // 工具栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .height(52.dp)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        text = if (configToEdit != null) stringResource(R.string.title_edit_connection) else stringResource(R.string.title_new_connection),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )

                // 可滚动表单内容
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // 分区：基本信息
                    FormSectionHeader(title = stringResource(R.string.label_server_address))
                    FormSection {
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.formName,
                            onValueChange = { viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.NAME, it)) },
                            label = { Text(stringResource(R.string.label_config_name)) },
                            singleLine = true,
                            colors = flatTextFieldColors()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.formServerAddress,
                            onValueChange = { viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.SERVER_ADDRESS, it)) },
                            label = { Text(stringResource(R.string.label_server_address)) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            colors = flatTextFieldColors()
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                        TextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = state.formPort,
                            onValueChange = { viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.PORT, it)) },
                            label = { Text(stringResource(R.string.label_port)) },
                            singleLine = true,
                            colors = flatTextFieldColors()
                        )
                    }

                    // 分区：共享名称
                    FormSectionHeader(title = stringResource(R.string.section_share_name))
                    FormSection {
                        ShareNameSection(
                            state = state,
                            onFetchShares = { viewModel.handleIntent(ConnectionIntent.FetchShares) },
                            onSelectShare = { name -> viewModel.handleIntent(ConnectionIntent.SelectShare(name)) },
                            onShareNameChange = { name -> viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.SHARE_NAME, name)) }
                        )
                    }

                    // 分区：认证
                    FormSectionHeader(title = stringResource(R.string.label_username))
                    FormSection {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.handleIntent(ConnectionIntent.ToggleAnonymous) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.label_anonymous),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Checkbox(
                                checked = state.isAnonymous,
                                onCheckedChange = { viewModel.handleIntent(ConnectionIntent.ToggleAnonymous) }
                            )
                        }
                        if (!state.isAnonymous) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                            TextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = state.formUsername,
                                onValueChange = { viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.USERNAME, it)) },
                                label = { Text(stringResource(R.string.label_username)) },
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                colors = flatTextFieldColors()
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                            TextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = state.formPassword,
                                onValueChange = { viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.PASSWORD, it)) },
                                label = { Text(stringResource(R.string.label_password)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                colors = flatTextFieldColors()
                            )
                        }
                    }

                    // 分区：局域网发现（可折叠）
                    CollapsibleDiscoverySection(
                        state = state,
                        onStart = { viewModel.handleIntent(ConnectionIntent.StartDiscovery) },
                        onProbeTarget = { viewModel.handleIntent(ConnectionIntent.ProbeDiscoveryTarget) },
                        onTargetChange = { target -> viewModel.handleIntent(ConnectionIntent.UpdateDiscoveryTarget(target)) },
                        onStop = { viewModel.handleIntent(ConnectionIntent.StopDiscovery) },
                        onSelect = { host -> viewModel.handleIntent(ConnectionIntent.SelectDiscoveredHost(host)) }
                    )
                }

                // 固定底部操作栏
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    thickness = 0.5.dp
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val compactShape = RoundedCornerShape(8.dp)
                    val compactPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    // 测试按钮
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) viewModel.handleIntent(ConnectionIntent.TestConnection(config))
                        },
                        enabled = !state.isTesting && canConnect(state),
                        shape = compactShape,
                        contentPadding = compactPadding
                    ) {
                        if (state.isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.btn_test_connection), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // 保存按钮
                    OutlinedButton(
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) viewModel.handleIntent(ConnectionIntent.SaveConnection(config))
                        },
                        enabled = !state.isLoading && canSave(state),
                        shape = compactShape,
                        contentPadding = compactPadding
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.btn_save_config), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    // 连接按钮（主按钮）
                    Button(
                        modifier = Modifier.weight(1f).height(36.dp),
                        onClick = {
                            val config = buildConfigFromState(state)
                            if (config != null) viewModel.handleIntent(ConnectionIntent.Connect(config))
                        },
                        enabled = !state.isConnecting && canConnect(state),
                        shape = compactShape,
                        contentPadding = compactPadding
                    ) {
                        if (state.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(stringResource(R.string.btn_connect), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
}

@Composable
private fun FormSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp, end = 16.dp)
    )
}

@Composable
private fun FormSection(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        content()
    }
}

@Composable
private fun flatTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
    unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
    disabledIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
)

@Composable
private fun CollapsibleDiscoverySection(
    state: ConnectionState,
    onStart: () -> Unit,
    onProbeTarget: () -> Unit,
    onTargetChange: (String) -> Unit,
    onStop: () -> Unit,
    onSelect: (SmbDiscoveryHost) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    FormSectionHeader(title = stringResource(R.string.section_lan_discovery))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // 折叠标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (expanded) stringResource(R.string.btn_stop_scan) else stringResource(R.string.btn_scan_lan_smb_hosts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = if (state.isDiscovering) onStop else onStart
                    ) {
                        Icon(
                            imageVector = if (state.isDiscovering) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (state.isDiscovering) stringResource(R.string.btn_stop_scan) else stringResource(R.string.btn_scan_lan_smb_hosts),
                            modifier = Modifier.padding(start = 4.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                TextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.manualDiscoveryTarget,
                    onValueChange = onTargetChange,
                    label = { Text(stringResource(R.string.label_discovery_manual_target)) },
                    placeholder = { Text(stringResource(R.string.hint_discovery_manual_target)) },
                    supportingText = { Text(stringResource(R.string.help_discovery_manual_target)) },
                    singleLine = true,
                    enabled = !state.isDiscovering,
                    colors = flatTextFieldColors()
                )
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onProbeTarget,
                    enabled = !state.isDiscovering && state.manualDiscoveryTarget.isNotBlank()
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.btn_probe_discovery_target), modifier = Modifier.padding(start = 4.dp))
                }
                if (state.isDiscovering) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.discovery_scanning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (state.hasDiscoveryStarted && !state.isDiscovering && state.discoveredHosts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.discovery_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                state.discoveredHosts.forEach { host ->
                    DiscoveredHostRow(host = host, onSelect = { onSelect(host) })
                }
            }
        }
    }
}

@Composable
private fun DiscoveredHostRow(
    host: SmbDiscoveryHost,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = host.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${host.address}:${host.port} · ${host.discoverySourceLabel()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onSelect) {
            Text(stringResource(R.string.discovery_select_host), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SmbDiscoveryHost.discoverySourceLabel(): String {
    val mdnsLabel = stringResource(R.string.discovery_source_mdns)
    val netBiosLabel = stringResource(R.string.discovery_source_netbios)
    val manualLabel = stringResource(R.string.discovery_source_manual)
    return sources.joinToString(" / ") { source ->
        when (source) {
            SmbDiscoverySource.MDNS -> mdnsLabel
            SmbDiscoverySource.NETBIOS -> netBiosLabel
            SmbDiscoverySource.MANUAL -> manualLabel
        }
    }
}

@Composable
private fun ShareNameSection(
    state: ConnectionState,
    onFetchShares: () -> Unit,
    onSelectShare: (String) -> Unit,
    onShareNameChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                modifier = Modifier.weight(1f),
                value = state.formShareName,
                onValueChange = onShareNameChange,
                label = { Text(stringResource(R.string.label_share_name)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = flatTextFieldColors()
            )
            OutlinedButton(
                onClick = onFetchShares,
                enabled = state.formServerAddress.isNotEmpty() && !state.isFetchingShares
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        if (state.isFetchingShares) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (state.shareFetchError != null && !state.isFetchingShares) {
            Text(
                text = state.shareFetchError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (
            state.hasFetchedShares &&
            state.availableShares.isEmpty() &&
            state.shareFetchError == null &&
            !state.isFetchingShares
        ) {
            Text(
                text = stringResource(R.string.shares_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        state.availableShares.forEach { shareName ->
            val isSelected = shareName == state.formShareName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectShare(shareName) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = shareName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
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
