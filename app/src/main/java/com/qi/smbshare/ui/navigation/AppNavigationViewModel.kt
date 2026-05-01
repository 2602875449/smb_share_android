package com.qi.smbshare.ui.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AppNavigationState(
    val currentConfig: SMBConfig? = null,
    val editConfig: SMBConfig? = null,
    val initialPath: String = "",
    val isFilePreviewVisible: Boolean = false
)

@HiltViewModel
class AppNavigationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val connectionRepository: ConnectionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(
        AppNavigationState(
            currentConfig = savedConfig(KEY_CURRENT_CONFIG),
            editConfig = savedConfig(KEY_EDIT_CONFIG),
            initialPath = savedStateHandle[KEY_INITIAL_PATH] ?: "",
            isFilePreviewVisible = savedStateHandle[KEY_PREVIEW_VISIBLE] ?: false
        )
    )
    val state: StateFlow<AppNavigationState> = _state.asStateFlow()

    init {
        restoreFullConfigsFromRepository()
    }

    fun showFiles(config: SMBConfig, initialPath: String = "") {
        update(
            currentConfig = config,
            initialPath = initialPath,
            isFilePreviewVisible = false
        )
    }

    fun restoreFiles(config: SMBConfig, initialPath: String) {
        showFiles(config, initialPath)
    }

    fun clearCurrentConnection() {
        update(
            currentConfig = null,
            initialPath = "",
            isFilePreviewVisible = false
        )
    }

    fun startEditing(config: SMBConfig?) {
        update(editConfig = config, isFilePreviewVisible = false)
    }

    fun clearEditing() {
        update(editConfig = null)
    }

    fun setPreviewVisible(visible: Boolean) {
        update(isFilePreviewVisible = visible)
    }

    private fun savedConfig(key: String): SMBConfig? {
        return savedStateHandle.get<String>(key)?.toSafeNavigationConfigOrNull()
    }

    private fun restoreFullConfigsFromRepository() {
        val currentConfigId = _state.value.currentConfig?.id
        val editConfigId = _state.value.editConfig?.id
        if (currentConfigId == null && editConfigId == null) {
            return
        }

        viewModelScope.launch {
            val fullCurrentConfig = currentConfigId?.let { connectionRepository.getConfigById(it) }
            val fullEditConfig = editConfigId?.let { connectionRepository.getConfigById(it) }
            val currentState = _state.value

            update(
                currentConfig = fullCurrentConfig ?: currentState.currentConfig,
                editConfig = fullEditConfig ?: currentState.editConfig
            )
        }
    }

    private fun update(
        currentConfig: SMBConfig? = _state.value.currentConfig,
        editConfig: SMBConfig? = _state.value.editConfig,
        initialPath: String = _state.value.initialPath,
        isFilePreviewVisible: Boolean = _state.value.isFilePreviewVisible
    ) {
        saveConfigSnapshot(KEY_CURRENT_CONFIG, currentConfig)
        saveConfigSnapshot(KEY_EDIT_CONFIG, editConfig)
        savedStateHandle[KEY_INITIAL_PATH] = initialPath
        savedStateHandle[KEY_PREVIEW_VISIBLE] = isFilePreviewVisible
        _state.value = AppNavigationState(
            currentConfig = currentConfig,
            editConfig = editConfig,
            initialPath = initialPath,
            isFilePreviewVisible = isFilePreviewVisible
        )
    }

    private fun saveConfigSnapshot(key: String, config: SMBConfig?) {
        if (config == null) {
            savedStateHandle.remove<String>(key)
        } else {
            savedStateHandle[key] = config.toSafeNavigationJson()
        }
    }

    private companion object {
        private const val KEY_CURRENT_CONFIG = "current_config"
        private const val KEY_EDIT_CONFIG = "edit_config"
        private const val KEY_INITIAL_PATH = "initial_path"
        private const val KEY_PREVIEW_VISIBLE = "preview_visible"
    }
}

/**
 * 导航状态只保存非敏感快照；密码恢复必须通过 ConnectionRepository 按 ID 读取。
 */
private fun SMBConfig.toSafeNavigationJson(): String {
    return JSONObject().apply {
        put("id", id)
        put("name", name)
        put("serverAddress", serverAddress)
        put("port", port)
        put("shareName", shareName)
        put("username", username)
        put("isAnonymous", isAnonymous)
    }.toString()
}

private fun String.toSafeNavigationConfigOrNull(): SMBConfig? {
    return try {
        val json = JSONObject(this)
        SMBConfig(
            id = json.getString("id"),
            name = json.optString("name", ""),
            serverAddress = json.getString("serverAddress"),
            port = json.optInt("port", 445),
            shareName = json.getString("shareName"),
            username = json.optString("username", ""),
            password = "",
            isAnonymous = json.optBoolean("isAnonymous", false)
        )
    } catch (e: Exception) {
        null
    }
}
