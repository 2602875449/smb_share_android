package com.qi.smbshare.ui.navigation

internal enum class NavigationTab {
    CONNECTION,
    FILE,
    TRANSFER_MANAGER,
    SETTINGS
}

internal sealed class AppDestination(val route: String) {
    data object Connection : AppDestination("connection")
    data object EditConnection : AppDestination("connection/edit")
    data object FileList : AppDestination("files")
    data object TransferManager : AppDestination("transfer-manager")
    data object Settings : AppDestination("settings")
    data object PrivacyPolicy : AppDestination("settings/privacy-policy")
    data object About : AppDestination("settings/about")

    companion object {
        fun selectedTabFor(route: String?): NavigationTab = when (route) {
            FileList.route -> NavigationTab.FILE
            TransferManager.route -> NavigationTab.TRANSFER_MANAGER
            Settings.route,
            PrivacyPolicy.route,
            About.route -> NavigationTab.SETTINGS
            else -> NavigationTab.CONNECTION
        }
    }
}
