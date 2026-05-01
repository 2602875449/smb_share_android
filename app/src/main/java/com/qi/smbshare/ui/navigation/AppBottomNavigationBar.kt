package com.qi.smbshare.ui.navigation

import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.qi.smbshare.R
import com.qi.smbshare.ui.components.BadgedIcon

@Composable
internal fun AppBottomNavigationBar(
    selectedTab: NavigationTab,
    activeTransferCount: Int,
    isFileEnabled: Boolean,
    isChinese: Boolean,
    onSelectTab: (NavigationTab) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .wrapContentHeight()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
    ) {
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = stringResource(R.string.nav_connection)
                )
            },
            label = navigationLabel(isChinese, stringResource(R.string.nav_connection)),
            selected = selectedTab == NavigationTab.CONNECTION,
            onClick = { onSelectTab(NavigationTab.CONNECTION) },
            colors = appNavigationBarItemColors()
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = stringResource(R.string.nav_files)
                )
            },
            label = navigationLabel(isChinese, stringResource(R.string.nav_files)),
            selected = selectedTab == NavigationTab.FILE,
            enabled = isFileEnabled,
            onClick = { onSelectTab(NavigationTab.FILE) },
            colors = appNavigationBarItemColors()
        )
        NavigationBarItem(
            icon = {
                BadgedIcon(
                    icon = Icons.Default.SwapVert,
                    badgeCount = activeTransferCount,
                    hasActiveTransfers = activeTransferCount > 0
                )
            },
            label = navigationLabel(isChinese, stringResource(R.string.nav_transfer_manager)),
            selected = selectedTab == NavigationTab.TRANSFER_MANAGER,
            onClick = { onSelectTab(NavigationTab.TRANSFER_MANAGER) },
            colors = appNavigationBarItemColors()
        )
        NavigationBarItem(
            icon = {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.nav_settings)
                )
            },
            label = navigationLabel(isChinese, stringResource(R.string.nav_settings)),
            selected = selectedTab == NavigationTab.SETTINGS,
            onClick = { onSelectTab(NavigationTab.SETTINGS) },
            colors = appNavigationBarItemColors()
        )
    }
}

@Composable
private fun navigationLabel(
    isChinese: Boolean,
    text: String
): @Composable (() -> Unit)? = if (isChinese) {
    { Text(text) }
} else {
    null
}

@Composable
private fun appNavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
)
