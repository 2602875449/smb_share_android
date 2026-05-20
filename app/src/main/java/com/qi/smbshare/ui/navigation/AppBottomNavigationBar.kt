package com.qi.smbshare.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                icon = Icons.Default.Cloud,
                label = stringResource(R.string.nav_connection),
                selected = selectedTab == NavigationTab.CONNECTION,
                enabled = true,
                showLabel = isChinese,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(NavigationTab.CONNECTION) }
            )
            NavItem(
                icon = Icons.Default.Folder,
                label = stringResource(R.string.nav_files),
                selected = selectedTab == NavigationTab.FILE,
                enabled = isFileEnabled,
                showLabel = isChinese,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(NavigationTab.FILE) }
            )
            NavTransferItem(
                label = stringResource(R.string.nav_transfer_manager),
                selected = selectedTab == NavigationTab.TRANSFER_MANAGER,
                activeCount = activeTransferCount,
                showLabel = isChinese,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(NavigationTab.TRANSFER_MANAGER) }
            )
            NavItem(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.nav_settings),
                selected = selectedTab == NavigationTab.SETTINGS,
                enabled = true,
                showLabel = isChinese,
                modifier = Modifier.weight(1f),
                onClick = { onSelectTab(NavigationTab.SETTINGS) }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .height(52.dp)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 26.dp)
                        .size(width = 20.dp, height = 2.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun NavTransferItem(
    label: String,
    selected: Boolean,
    activeCount: Int,
    showLabel: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .height(52.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box {
            BadgedIcon(
                icon = Icons.Default.SwapVert,
                badgeCount = activeCount,
                hasActiveTransfers = activeCount > 0,
                modifier = Modifier.size(22.dp),
                tint = tint
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(top = 26.dp)
                        .size(width = 20.dp, height = 2.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.extraSmall
                        )
                )
            }
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
