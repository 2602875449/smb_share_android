package com.qi.smb_share_android.ui.connection

import com.qi.smb_share_android.data.model.SMBConfig

data class ConnectionState(
    val savedConfigs: List<SMBConfig> = emptyList(),
    val currentConfig: SMBConfig? = null,
    val isAnonymous: Boolean = false,
    val isLoading: Boolean = false,
    val isConnecting: Boolean = false,
    val isTesting: Boolean = false,
    val error: String? = null,
    val testResult: String? = null,
    val navigateToFileList: SMBConfig? = null, // 导航到文件列表
    val navigateToEdit: SMBConfig? = null // 导航到编辑页面（null表示新建）
) {
    // 表单字段
    val formName: String get() = currentConfig?.name ?: ""
    val formServerAddress: String get() = currentConfig?.serverAddress ?: ""
    val formPort: String get() = currentConfig?.port?.toString() ?: "445"
    val formShareName: String get() = currentConfig?.shareName ?: ""
    val formUsername: String get() = currentConfig?.username ?: ""
    val formPassword: String get() = currentConfig?.password ?: ""
}

