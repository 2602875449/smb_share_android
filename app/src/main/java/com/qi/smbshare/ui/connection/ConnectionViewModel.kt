package com.qi.smbshare.ui.connection

import android.app.Application
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.R
import com.qi.smbshare.data.discovery.AndroidSmbHostDiscovery
import com.qi.smbshare.data.discovery.SmbHostDiscovery
import com.qi.smbshare.data.discovery.SmbDiscoveryTarget
import com.qi.smbshare.data.discovery.SmbDiscoveryTargetParser
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.repository.ConnectionRepository
import com.qi.smbshare.domain.usecase.ConnectSMBUseCase
import com.qi.smbshare.domain.usecase.DeleteConnectionUseCase
import com.qi.smbshare.domain.usecase.SaveConnectionUseCase
import com.qi.smbshare.util.ErrorHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConnectionViewModel internal constructor(
    application: Application,
    private val smbHostDiscovery: SmbHostDiscovery,
    private val ioDispatcher: CoroutineDispatcher
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        smbHostDiscovery = AndroidSmbHostDiscovery(application),
        ioDispatcher = Dispatchers.IO
    )

    private val TAG = "ConnectionViewModel"
    private val connectionManager = SMBConnectionManager()
    private val connectionRepository = ConnectionRepository(application)
    private val connectUseCase = ConnectSMBUseCase(connectionManager)
    private val saveConnectionUseCase = SaveConnectionUseCase(connectionRepository)
    private val deleteConnectionUseCase = DeleteConnectionUseCase(connectionRepository)
    private var loadConnectionsJob: Job? = null
    private var discoveryJob: Job? = null

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
            is ConnectionIntent.StartDiscovery -> {
                startDiscovery(target = null)
            }
            is ConnectionIntent.ProbeDiscoveryTarget -> {
                startManualDiscoveryTarget()
            }
            is ConnectionIntent.UpdateDiscoveryTarget -> {
                _state.value = _state.value.copy(manualDiscoveryTarget = intent.target)
            }
            is ConnectionIntent.StopDiscovery -> {
                stopDiscovery()
            }
            is ConnectionIntent.SelectDiscoveredHost -> {
                selectDiscoveredHost(intent.host)
            }
            is ConnectionIntent.ClearDiscoveryError -> {
                _state.value = _state.value.copy(discoveryError = null)
            }
            is ConnectionIntent.ClearError -> {
                _state.value = _state.value.copy(error = null, testResult = null, discoveryError = null)
            }
            is ConnectionIntent.ClearForm -> {
                stopDiscovery()
                _state.value = _state.value.copy(
                    currentConfig = null,
                    isAnonymous = false,
                    hasDiscoveryStarted = false,
                    discoveredHosts = emptyList(),
                    discoveryError = null
                )
            }
            is ConnectionIntent.EditConfig -> {
                stopDiscovery()
                _state.value = _state.value.copy(
                    currentConfig = intent.config,
                    isAnonymous = intent.config.isAnonymous,
                    hasDiscoveryStarted = false,
                    discoveredHosts = emptyList(),
                    discoveryError = null
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
        if (loadConnectionsJob != null) {
            return
        }
        loadConnectionsJob = viewModelScope.launch {
            connectionRepository.getSavedConfigs()
                .catch { e ->
                    val errorMessage = formatError(e, R.string.error_load_connections_failed)
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
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_save_connection_failed)
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
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_delete_connection_failed)
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
                    // 然后执行连接
                    withContext(ioDispatcher) {
                        connectUseCase.execute(config)
                    }
                        .onSuccess {
                            _state.value = _state.value.copy(
                                isConnecting = false,
                                navigateToFileList = config
                            )
                        }
                        .onFailure { e ->
                            val errorMessage = formatError(e, R.string.error_connect_failed)
                            _state.value = _state.value.copy(
                                isConnecting = false,
                                error = errorMessage
                            )
                        }
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_save_connection_failed)
                    _state.value = _state.value.copy(
                        isConnecting = false,
                        error = errorMessage
                    )
                }
        }
    }

    private fun testConnection(config: SMBConfig) {
        Log.d(TAG, "收到测试连接请求")
        
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isTesting = true,
                error = null,
                testResult = null
            )
            Log.d(TAG, "开始执行连接测试...")
            
            // 在IO线程执行网络操作
            withContext(ioDispatcher) {
                connectUseCase.testConnection(config)
            }
                .onSuccess {
                    Log.d(TAG, "连接测试成功")
                    _state.value = _state.value.copy(
                        isTesting = false,
                        testResult = text(R.string.msg_test_success)
                    )
                }
                .onFailure { e ->
                    Log.e(TAG, "连接测试失败", e)
                    val errorMessage = formatError(e, R.string.error_connection_test_failed)
                    _state.value = _state.value.copy(
                        isTesting = false,
                        testResult = null,
                        error = errorMessage
                    )
                }
        }
    }

    private fun startDiscovery(target: SmbDiscoveryTarget?) {
        if (discoveryJob?.isActive == true) {
            return
        }
        discoveryJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isDiscovering = true,
                hasDiscoveryStarted = true,
                discoveredHosts = emptyList(),
                discoveryError = null
            )

            val discoveryFlow = if (target == null) {
                smbHostDiscovery.discover()
            } else {
                smbHostDiscovery.discover(target)
            }

            discoveryFlow
                .catch { e ->
                    Log.e(TAG, "局域网 SMB 主机扫描失败", e)
                    _state.value = _state.value.copy(
                        isDiscovering = false,
                        discoveryError = text(R.string.error_discovery_failed)
                    )
                }
                .onCompletion { cause ->
                    if (cause == null) {
                        _state.value = _state.value.copy(isDiscovering = false)
                    }
                }
                .collect { hosts ->
                    _state.value = _state.value.copy(discoveredHosts = hosts)
                }
        }
    }

    private fun startManualDiscoveryTarget() {
        val targetText = _state.value.manualDiscoveryTarget
        val target = SmbDiscoveryTargetParser.parse(targetText)
            .getOrElse {
                _state.value = _state.value.copy(discoveryError = text(R.string.error_discovery_target_invalid))
                return
            }
        startDiscovery(target)
    }

    private fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
        _state.value = _state.value.copy(isDiscovering = false)
    }

    private fun selectDiscoveredHost(host: SmbDiscoveryHost) {
        val current = _state.value.currentConfig
        val nextName = current?.name?.takeIf { it.isNotBlank() } ?: host.displayName
        val newConfig = current?.copy(
            name = nextName,
            serverAddress = host.address,
            port = host.port
        ) ?: SMBConfig(
            name = nextName,
            serverAddress = host.address,
            port = host.port,
            shareName = ""
        )
        _state.value = _state.value.copy(currentConfig = newConfig)
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

    private fun text(@StringRes resId: Int): String {
        return getApplication<Application>().getString(resId)
    }

    private fun formatError(error: Throwable, @StringRes fallbackResId: Int): String {
        return if (error is Exception) {
            ErrorHandler.getErrorMessageFromException(
                context = getApplication(),
                exception = error,
                fallbackMessageResId = fallbackResId
            )
        } else {
            text(fallbackResId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        discoveryJob?.cancel()
        connectUseCase.disconnect()
    }
}
