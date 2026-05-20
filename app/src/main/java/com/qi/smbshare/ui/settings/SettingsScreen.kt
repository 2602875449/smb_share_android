package com.qi.smbshare.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.R
import com.qi.smbshare.data.model.ThemeMode
import com.qi.smbshare.util.AppLanguage
import com.qi.smbshare.util.LanguageHelper

/**
 * 设置页面 - iOS 分组列表风格
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToOnboarding: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val downloadPathCopiedMessage = stringResource(R.string.settings_download_path_copied)
    val downloadPathText = if (state.downloadDirectory.isNotBlank()) {
        state.downloadDirectory
    } else {
        stringResource(R.string.settings_download_path_checking)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            snackbarMessage = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_settings),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), thickness = 0.5.dp)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // ── 通用 ──
                item { SettingsSectionHeader(stringResource(R.string.settings_section_general)) }
                item {
                    SettingsGroup {
                        // 主题内联选择（不用 Dialog）
                        SettingsGroupHeader(stringResource(R.string.settings_theme_title), Icons.Default.Palette)
                        listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_follow_system),
                            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                            ThemeMode.DARK to stringResource(R.string.settings_theme_dark)
                        ).forEachIndexed { i, (mode, label) ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(start = 40.dp))
                            ThemeRadioRow(
                                label = label,
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Default.Language,
                            title = stringResource(R.string.settings_language),
                            subtitle = when (state.currentLanguage) {
                                AppLanguage.SYSTEM -> stringResource(R.string.language_follow_system)
                                AppLanguage.ENGLISH -> stringResource(R.string.language_english)
                                AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
                            },
                            onClick = { viewModel.showLanguageDialog() }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(start = 40.dp))
                        SettingsItem(
                            icon = Icons.Default.Folder,
                            title = stringResource(R.string.settings_download_location_title),
                            subtitle = downloadPathText,
                            showChevron = false,
                            onClick = {
                                if (state.downloadDirectory.isNotBlank()) {
                                    clipboardManager.setText(AnnotatedString(state.downloadDirectory))
                                    snackbarMessage = downloadPathCopiedMessage
                                }
                            }
                        )
                    }
                }

                // ── 帮助与支持 ──
                item { SettingsSectionHeader(stringResource(R.string.settings_section_support)) }
                item {
                    SettingsGroup {
                        var firstItem = true
                        if (onNavigateToOnboarding != null) {
                            SettingsItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = stringResource(R.string.settings_onboarding_title),
                                subtitle = stringResource(R.string.settings_onboarding_subtitle),
                                onClick = onNavigateToOnboarding
                            )
                            firstItem = false
                        }
                        if (!firstItem) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(start = 40.dp))
                        SettingsItem(
                            icon = Icons.Default.Policy,
                            title = stringResource(R.string.settings_privacy_title),
                            subtitle = stringResource(R.string.settings_privacy_subtitle),
                            onClick = onNavigateToPrivacyPolicy
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), thickness = 0.5.dp, modifier = Modifier.padding(start = 40.dp))
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = stringResource(R.string.settings_about_title),
                            subtitle = stringResource(R.string.settings_about_subtitle),
                            onClick = onNavigateToAbout
                        )
                    }
                }

                // ── 高级 ──
                item { SettingsSectionHeader(stringResource(R.string.settings_section_advanced)) }
                item {
                    SettingsGroup {
                        SettingsItem(
                            icon = Icons.Default.CleaningServices,
                            title = stringResource(R.string.settings_clear_cache_title),
                            subtitle = stringResource(R.string.settings_clear_cache_subtitle, state.cacheSize),
                            onClick = { viewModel.showClearCacheDialog() }
                        )
                    }
                }
            }
        }
    }

    // 语言选择对话框（需要重启，保留）
    if (state.showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideLanguageDialog() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    listOf(
                        AppLanguage.SYSTEM to stringResource(R.string.language_follow_system),
                        AppLanguage.ENGLISH to stringResource(R.string.language_english),
                        AppLanguage.CHINESE to stringResource(R.string.language_chinese)
                    ).forEach { (lang, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(lang)
                                    viewModel.hideLanguageDialog()
                                    if (activity != null) LanguageHelper.restartApp(activity)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            if (state.currentLanguage == lang) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.hideLanguageDialog() }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // 清除缓存确认对话框
    if (state.showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideClearCacheDialog() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(stringResource(R.string.settings_clear_cache_dialog_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_dialog_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCache() }) {
                    Text(stringResource(R.string.settings_clear_cache_confirm), color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideClearCacheDialog() }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp, end = 16.dp)
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        content()
    }
}

@Composable
private fun SettingsGroupHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (showChevron) {
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ThemeRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
    }
}
