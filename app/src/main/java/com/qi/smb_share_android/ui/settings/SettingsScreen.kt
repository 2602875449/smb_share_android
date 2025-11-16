package com.qi.smb_share_android.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smb_share_android.data.model.ThemeMode

/**
 * 设置页面
 * 
 * @param viewModel 设置页面的 ViewModel
 * @param onNavigateToPrivacyPolicy 导航到隐私政策页面
 * @param onNavigateToAbout 导航到关于页面
 * @param onNavigateToOnboarding 导航到引导页面（未来实现）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToOnboarding: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 通用设置区域
            item {
                SettingsSectionHeader(title = "通用")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Palette,
                    title = "主题",
                    subtitle = when (state.themeMode) {
                        ThemeMode.SYSTEM -> "跟随系统"
                        ThemeMode.LIGHT -> "浅色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { showThemeDialog = true }
                )
            }
            
            // 帮助与支持区域
            item {
                SettingsSectionHeader(title = "帮助与支持")
            }
            
            // 查看引导教程（如果提供了回调）
            if (onNavigateToOnboarding != null) {
                item {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Filled.Help,
                        title = "查看引导教程",
                        subtitle = "重新查看应用使用说明",
                        onClick = onNavigateToOnboarding
                    )
                }
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Policy,
                    title = "隐私政策",
                    subtitle = "了解我们如何保护您的隐私",
                    onClick = onNavigateToPrivacyPolicy
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.Info,
                    title = "关于应用",
                    subtitle = "版本信息和开发者信息",
                    onClick = onNavigateToAbout
                )
            }
            
            // 高级设置区域
            item {
                SettingsSectionHeader(title = "高级")
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.CleaningServices,
                    title = "清除缓存",
                    subtitle = "当前缓存: ${state.cacheSize}",
                    onClick = { viewModel.showClearCacheDialog() }
                )
            }
            
            item {
                SettingsItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "清除下载历史",
                    subtitle = "删除所有下载记录",
                    onClick = { viewModel.showClearHistoryDialog() }
                )
            }
        }
    }
    
    // 主题选择对话框
    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentTheme = state.themeMode,
            onThemeSelected = { theme ->
                viewModel.setThemeMode(theme)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }
    
    // 清除缓存确认对话框
    if (state.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearCacheDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("清除缓存") },
            text = { Text("确定要清除所有缓存数据吗？这不会影响您的连接配置和下载历史。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache() }) {
                    Text("清除", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearCacheDialog() }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
    
    // 清除下载历史确认对话框
    if (state.showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearHistoryDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("清除下载历史") },
            text = { Text("确定要删除所有下载记录吗？这不会删除已下载的文件。") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDownloadHistory() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearHistoryDialog() }) {
                    Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

/**
 * 设置区域标题
 */
@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * 设置项组件
 */
@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * 主题选择对话框
 */
@Composable
private fun ThemeSelectionDialog(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Palette,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("选择主题") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeOption(
                    title = "跟随系统",
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onThemeSelected(ThemeMode.SYSTEM) }
                )
                ThemeOption(
                    title = "浅色",
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeSelected(ThemeMode.LIGHT) }
                )
                ThemeOption(
                    title = "深色",
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeSelected(ThemeMode.DARK) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

/**
 * 主题选项组件
 */
@Composable
private fun ThemeOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
