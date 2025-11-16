package com.qi.smb_share_android.ui.connection

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smb_share_android.data.local.SMBConnectionManager
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.data.repository.ConnectionRepository
import com.qi.smb_share_android.domain.usecase.ConnectSMBUseCase
import com.qi.smb_share_android.domain.usecase.DeleteConnectionUseCase
import com.qi.smb_share_android.domain.usecase.SaveConnectionUseCase
import com.qi.smb_share_android.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ConnectionViewModel"
    private val connectionManager = SMBConnectionManager()
    private val connectionRepository = ConnectionRepository(application)
    private val connectUseCase = ConnectSMBUseCase(connectionManager)
    private val saveConnectionUseCase = SaveConnectionUseCase(connectionRepository)
    private val deleteConnectionUseCase = DeleteConnectionUseCase(connectionRepository)

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    init {
        handleIntent(ConnectionIntent.LoadConnections)
    }

    fun handleIntent(intent: ConnectionIntent) {
        when (intent) {
            is ConnectionIntent.LoadConnections -> {
                loadConnections()
            }
            is ConnectionIntent.SaveConnection -> {
                saveConnection(intent.config)
            }
            is ConnectionIntent.DeleteConnection -> {
                deleteConnection(intent.configId)
            }
            is ConnectionIntent.Connect -> {
                connect(intent.config)
            }
            is ConnectionIntent.TestConnection -> {
                testConnection(intent.config)
            }
            is ConnectionIntent.UpdateFormField -> {
                updateFormField(intent.field, intent.value)
            }
            is ConnectionIntent.ToggleAnonymous -> {
                toggleAnonymous()
            }
            is ConnectionIntent.ClearError -> {
                _state.value = _state.value.copy(error = null, testResult = null)
            }
            is ConnectionIntent.ClearForm -> {
                _state.value = _state.value.copy(
                    currentConfig = null,
                    isAnonymous = false
                )
            }
            is ConnectionIntent.EditConfig -> {
                _state.value = _state.value.copy(
                    currentConfig = intent.config,
                    isAnonymous = intent.config.isAnonymous
                )
            }
            is ConnectionIntent.NavigateToNewConnection -> {
                _state.value = _state.value.copy(navigateToEdit = SMBConfig(serverAddress = "", shareName = ""))
            }
            is ConnectionIntent.ClearNavigation -> {
                _state.value = _state.value.copy(
                    navigateToFileList = null,
                    navigateToEdit = null
                )
            }
        }
    }

    private fun loadConnections() {
        viewModelScope.launch {
            connectionRepository.getSavedConfigs()
                .catch { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "加载配置失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(error = errorMessage)
                }
                .collect { configs ->
                    _state.value = _state.value.copy(savedConfigs = configs)
                }
        }
    }

    private fun saveConnection(config: SMBConfig) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            saveConnectionUseCase.execute(config)
                .onSuccess {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        currentConfig = null,
                        isAnonymous = false,
                        navigateToEdit = null // 保存成功后清除导航状态
                    )
                    loadConnections()
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "保存配置失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }

    private fun deleteConnection(configId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            deleteConnectionUseCase.execute(configId)
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadConnections()
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "删除配置失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }

    private fun connect(config: SMBConfig) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isConnecting = true,
                error = null,
                navigateToFileList = null
            )
            
            // 先保存配置
            saveConnectionUseCase.execute(config)
                .onSuccess {
                    // 保存成功后，重新加载配置列表
                    loadConnections()
                    
                    // 然后执行连接
                    withContext(Dispatchers.IO) {
                        connectUseCase.execute(config)
                    }
                        .onSuccess {
                            _state.value = _state.value.copy(
                                isConnecting = false,
                                navigateToFileList = config
                            )
                        }
                        .onFailure { e ->
                            val errorMessage = if (e is Exception) {
                                ErrorHandler.getErrorMessageFromException(e)
                            } else {
                                "连接失败: ${e.message}"
                            }
                            _state.value = _state.value.copy(
                                isConnecting = false,
                                error = errorMessage
                            )
                        }
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "保存配置失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        error = errorMessage
                    )
                }
        }
    }

    private fun testConnection(config: SMBConfig) {
        Log.d(TAG, "收到测试连接请求")
        Log.d(TAG, "配置信息: 服务器=${config.serverAddress}, 端口=${config.port}, 共享=${config.shareName}, 匿名=${config.isAnonymous}")
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isTesting = true,
                error = null,
                testResult = null
            )
            Log.d(TAG, "开始执行连接测试...")
            
            // 在IO线程执行网络操作
            withContext(Dispatchers.IO) {
                connectUseCase.testConnection(config)
            }
                .onSuccess {
                    Log.d(TAG, "连接测试成功")
                    _state.value = _state.value.copy(
                        isTesting = false,
                        testResult = "连接测试成功"
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "连接测试失败", e)
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "连接测试失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isTesting = false,
                        testResult = null,
                        error = errorMessage
                    )
                }
        }
    }

    private fun updateFormField(field: FormField, value: String) {
        val current = _state.value.currentConfig
        val newConfig = when (field) {
            FormField.NAME -> current?.copy(name = value)
                ?: SMBConfig(name = value, serverAddress = "", shareName = "")
            FormField.SERVER_ADDRESS -> current?.copy(serverAddress = value)
                ?: SMBConfig(serverAddress = value, shareName = "")
            FormField.PORT -> {
                val port = value.toIntOrNull() ?: 445
                current?.copy(port = port)
                    ?: SMBConfig(port = port, serverAddress = "", shareName = "")
            }
            FormField.SHARE_NAME -> current?.copy(shareName = value)
                ?: SMBConfig(shareName = value, serverAddress = "")
            FormField.USERNAME -> current?.copy(username = value)
                ?: SMBConfig(username = value, serverAddress = "", shareName = "")
            FormField.PASSWORD -> current?.copy(password = value)
                ?: SMBConfig(password = value, serverAddress = "", shareName = "")
        }
        _state.value = _state.value.copy(currentConfig = newConfig)
    }

    private fun toggleAnonymous() {
        val newAnonymous = !_state.value.isAnonymous
        val current = _state.value.currentConfig
        val newConfig = if (newAnonymous) {
            current?.copy(isAnonymous = true, username = "", password = "")
                ?: SMBConfig(isAnonymous = true, serverAddress = "", shareName = "")
        } else {
            current?.copy(isAnonymous = false)
                ?: SMBConfig(isAnonymous = false, serverAddress = "", shareName = "")
        }
        _state.value = _state.value.copy(
            isAnonymous = newAnonymous,
            currentConfig = newConfig
        )
    }

    override fun onCleared() {
        super.onCleared()
        connectUseCase.disconnect()
    }
}

