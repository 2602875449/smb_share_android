package com.qi.smb_share_android.ui.connection

import com.qi.smb_share_android.data.model.SMBConfig

sealed class ConnectionIntent {
    object LoadConnections : ConnectionIntent()
    data class SaveConnection(val config: SMBConfig) : ConnectionIntent()
    data class DeleteConnection(val configId: String) : ConnectionIntent()
    data class Connect(val config: SMBConfig) : ConnectionIntent()
    data class TestConnection(val config: SMBConfig) : ConnectionIntent()
    data class UpdateFormField(
        val field: FormField,
        val value: String
    ) : ConnectionIntent()
    object ToggleAnonymous : ConnectionIntent()
    object ClearError : ConnectionIntent()
    object ClearForm : ConnectionIntent()
    data class EditConfig(val config: SMBConfig) : ConnectionIntent()
    object NavigateToNewConnection : ConnectionIntent() // 导航到新建连接页面
    object ClearNavigation : ConnectionIntent() // 清除导航状态
}

enum class FormField {
    NAME,
    SERVER_ADDRESS,
    PORT,
    SHARE_NAME,
    USERNAME,
    PASSWORD
}

