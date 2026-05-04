package com.qi.smbshare.ui.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qi.smbshare.R
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.ui.components.PredictiveBackAnimatedContent
import com.qi.smbshare.ui.connection.ConnectionIntent
import com.qi.smbshare.ui.connection.ConnectionScreen
import com.qi.smbshare.ui.connection.ConnectionViewModel
import com.qi.smbshare.ui.connection.EditConnectionScreen
import com.qi.smbshare.ui.filelist.FileListScreen
import com.qi.smbshare.ui.filelist.FileListViewModel
import com.qi.smbshare.ui.settings.AboutScreen
import com.qi.smbshare.ui.settings.PrivacyPolicyScreen
import com.qi.smbshare.ui.settings.SettingsScreen
import com.qi.smbshare.ui.settings.SettingsViewModel
import com.qi.smbshare.ui.transfer.TransferManagerScreen
import com.qi.smbshare.ui.transfer.TransferManagerViewModel
import java.io.File

@Composable
fun AppNavGraph(
    onInstallApk: (File) -> Unit
) {
    val navController = rememberNavController()

    val appNavigationViewModel: AppNavigationViewModel = hiltViewModel()
    val connectionViewModel: ConnectionViewModel = hiltViewModel()
    val transferManagerViewModel: TransferManagerViewModel = hiltViewModel()
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val configuration = LocalConfiguration.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val selectedTab = AppDestination.selectedTabFor(currentRoute)

    val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
    val isChinese = currentLocale.language == "zh"

    val appNavigationState by appNavigationViewModel.state.collectAsStateWithLifecycle()
    val transferState by transferManagerViewModel.state.collectAsStateWithLifecycle()
    val connectionState by connectionViewModel.state.collectAsStateWithLifecycle()
    val currentConfig = appNavigationState.currentConfig
    val editConfig = appNavigationState.editConfig
    val initialPath = appNavigationState.initialPath
    val isFilePreviewVisible = appNavigationState.isFilePreviewVisible
    val activeTransferCount = transferState.activeTransferCount

    LaunchedEffect(connectionState.restoredLastAccess) {
        connectionState.restoredLastAccess?.let { restored ->
            appNavigationViewModel.restoreFiles(restored.config, restored.path)
            navController.navigateTopLevel(AppDestination.FileList.route)
            connectionViewModel.handleIntent(ConnectionIntent.ClearRestoredLastAccess)
        }
    }

    LaunchedEffect(currentRoute, currentConfig) {
        if (currentRoute != AppDestination.FileList.route || currentConfig == null) {
            appNavigationViewModel.setPreviewVisible(false)
        }
    }

    val useFileListImmersiveBars = currentRoute == AppDestination.FileList.route &&
        currentConfig != null

    Scaffold(
        contentWindowInsets = if (useFileListImmersiveBars) {
            WindowInsets(0.dp)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        bottomBar = {
            if (!isFilePreviewVisible) {
                AppBottomNavigationBar(
                    selectedTab = selectedTab,
                    activeTransferCount = activeTransferCount,
                    isFileEnabled = currentConfig != null,
                    isChinese = isChinese,
                    onSelectTab = { tab ->
                        appNavigationViewModel.setPreviewVisible(false)
                        when (tab) {
                            NavigationTab.CONNECTION -> {
                                navController.navigateTopLevel(AppDestination.Connection.route)
                            }
                            NavigationTab.FILE -> {
                                if (currentConfig != null) {
                                    navController.navigateTopLevel(AppDestination.FileList.route)
                                }
                            }
                            NavigationTab.TRANSFER_MANAGER -> {
                                navController.navigateTopLevel(AppDestination.TransferManager.route)
                            }
                            NavigationTab.SETTINGS -> {
                                navController.navigateTopLevel(AppDestination.Settings.route)
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Connection.route
            ) {
                composable(AppDestination.Connection.route) {
                    ConnectionScreen(
                        viewModel = connectionViewModel,
                        onNavigateToFileList = { config ->
                            appNavigationViewModel.showFiles(config)
                            navController.navigateTopLevel(AppDestination.FileList.route)
                        },
                        onNavigateToEdit = { config ->
                            appNavigationViewModel.startEditing(config)
                            navController.navigate(AppDestination.EditConnection.route) {
                                launchSingleTop = true
                            }
                        },
                        onExit = {
                            activity?.finish()
                        }
                    )
                }
                composable(AppDestination.EditConnection.route) {
                    EditConnectionScreen(
                        viewModel = connectionViewModel,
                        configToEdit = editConfig,
                        onBack = {
                            appNavigationViewModel.clearEditing()
                            navController.popBackStack()
                        },
                        onSaveSuccess = {
                            appNavigationViewModel.clearEditing()
                            navController.popBackStack()
                        },
                        onConnectSuccess = { config ->
                            appNavigationViewModel.clearEditing()
                            appNavigationViewModel.showFiles(config)
                            navController.navigateTopLevel(AppDestination.FileList.route)
                        }
                    )
                }
                composable(AppDestination.FileList.route) {
                    val config = currentConfig
                    if (config != null) {
                        val fileListViewModel: FileListViewModel =
                            hiltViewModel<FileListViewModel, FileListViewModel.Factory>(
                                key = "${config.id}_${initialPath}_${config.navigationCredentialStateKey()}"
                            ) { factory ->
                                factory.create(config, initialPath)
                            }
                        val state by fileListViewModel.state.collectAsStateWithLifecycle()

                        LaunchedEffect(state.connectionErrorType) {
                            if (state.connectionErrorType != null) {
                                appNavigationViewModel.clearCurrentConnection()
                                navController.navigateToConnectionAndClear()
                            }
                        }

                        FileListScreen(
                            viewModel = fileListViewModel,
                            onInstallApk = onInstallApk,
                            config = config,
                            onBack = {
                                appNavigationViewModel.clearCurrentConnection()
                                navController.navigateToConnectionAndClear()
                            },
                            onPreviewVisibilityChange = { visible ->
                                appNavigationViewModel.setPreviewVisible(visible)
                            }
                        )
                    } else {
                        MissingConnectionContent(
                            onGoToConnection = {
                                navController.navigateTopLevel(AppDestination.Connection.route)
                            }
                        )
                    }
                }
                composable(AppDestination.TransferManager.route) {
                    TransferManagerScreen(
                        viewModel = transferManagerViewModel,
                        onInstallApk = onInstallApk
                    )
                }
                composable(AppDestination.Settings.route) {
                    BackHandler {
                        appNavigationViewModel.setPreviewVisible(false)
                        navController.navigateTopLevel(AppDestination.Connection.route)
                    }
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToPrivacyPolicy = {
                            navController.navigate(AppDestination.PrivacyPolicy.route)
                        },
                        onNavigateToAbout = {
                            navController.navigate(AppDestination.About.route)
                        }
                    )
                }
                composable(AppDestination.PrivacyPolicy.route) {
                    PredictiveBackAnimatedContent(
                        onBack = { navController.popBackStack() }
                    ) { predictiveBackModifier ->
                        PrivacyPolicyScreen(
                            onBack = { navController.popBackStack() },
                            modifier = predictiveBackModifier
                        )
                    }
                }
                composable(AppDestination.About.route) {
                    PredictiveBackAnimatedContent(
                        onBack = { navController.popBackStack() }
                    ) { predictiveBackModifier ->
                        AboutScreen(
                            onBack = { navController.popBackStack() },
                            onNavigateToPrivacyPolicy = {
                                navController.navigate(AppDestination.PrivacyPolicy.route) {
                                    popUpTo(AppDestination.Settings.route)
                                }
                            },
                            modifier = predictiveBackModifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingConnectionContent(onGoToConnection: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
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
            Button(onClick = onGoToConnection) {
                Text(stringResource(R.string.action_go_to_connection))
            }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavHostController.navigateToConnectionAndClear() {
    navigate(AppDestination.Connection.route) {
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private fun SMBConfig.navigationCredentialStateKey(): String {
    return if (isAnonymous || password.isNotEmpty()) {
        "credentials-ready"
    } else {
        "credentials-empty"
    }
}
