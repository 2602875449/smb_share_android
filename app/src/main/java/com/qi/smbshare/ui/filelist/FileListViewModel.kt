package com.qi.smbshare.ui.filelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.repository.SMBFileRepository
import com.qi.smbshare.data.repository.TransferRepository
import com.qi.smbshare.domain.usecase.ConnectSMBUseCase
import com.qi.smbshare.domain.usecase.CreateFolderUseCase
import com.qi.smbshare.domain.usecase.DeleteFileUseCase
import com.qi.smbshare.domain.usecase.ListFilesUseCase
import com.qi.smbshare.domain.usecase.RenameFileUseCase
import com.qi.smbshare.domain.usecase.UploadFileUseCase
import com.qi.smbshare.util.ErrorHandler
import com.qi.smbshare.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FileListViewModel(
    application: Application,
    private val config: SMBConfig,
    initialPath: String = ""
) : AndroidViewModel(application) {
    private val connectionManager = SMBConnectionManager()
    private val fileRepository = SMBFileRepository(connectionManager)
    private val transferRepository = TransferRepository(application)
    private val dataStoreManager = DataStoreManager(application)
    private val connectUseCase = ConnectSMBUseCase(connectionManager)
    private val listFilesUseCase = ListFilesUseCase(fileRepository)
    private val uploadFileUseCase = UploadFileUseCase(fileRepository)
    private val createFolderUseCase = CreateFolderUseCase(fileRepository)
    private val deleteFileUseCase = DeleteFileUseCase(fileRepository)
    private val renameFileUseCase = RenameFileUseCase(fileRepository)

    private val _state = MutableStateFlow(FileListState(currentPath = initialPath))
    val state: StateFlow<FileListState> = _state.asStateFlow()

    init {
        // 连接并加载文件
        connectAndLoadFiles()
    }

    fun handleIntent(intent: FileListIntent) {
        when (intent) {
            is FileListIntent.LoadFiles -> {
                loadFiles()
            }
            is FileListIntent.EnterDirectory -> {
                enterDirectory(intent.path)
            }
            is FileListIntent.GoBack -> {
                goBack()
            }
            is FileListIntent.DownloadFile -> {
                downloadFile(intent.filePath, intent.fileName)
            }
            is FileListIntent.ClearError -> {
                _state.value = _state.value.copy(error = null)
            }
            is FileListIntent.ClearMessage -> {
                _state.value = _state.value.copy(message = null)
            }
            is FileListIntent.UpdateSearchQuery -> {
                _state.value = _state.value.copy(searchQuery = intent.query)
            }
            is FileListIntent.ToggleSearch -> {
                _state.value = _state.value.copy(
                    isSearchActive = !_state.value.isSearchActive,
                    searchQuery = if (!_state.value.isSearchActive) "" else _state.value.searchQuery
                )
            }
            is FileListIntent.UploadFile -> {
                uploadFile(intent.file)
            }
            is FileListIntent.CreateFolder -> {
                createFolder(intent.folderName)
            }
            is FileListIntent.DeleteFile -> {
                deleteFile(intent.filePath)
            }
            is FileListIntent.RenameFile -> {
                renameFile(intent.filePath, intent.newName)
            }
            is FileListIntent.ShowCreateFolderDialog -> {
                _state.value = _state.value.copy(showCreateFolderDialog = true)
            }
            is FileListIntent.HideCreateFolderDialog -> {
                _state.value = _state.value.copy(showCreateFolderDialog = false)
            }
            is FileListIntent.ShowRenameDialog -> {
                _state.value = _state.value.copy(
                    showRenameDialog = true,
                    renameFilePath = intent.filePath,
                    renameCurrentName = intent.currentName
                )
            }
            is FileListIntent.HideRenameDialog -> {
                _state.value = _state.value.copy(
                    showRenameDialog = false,
                    renameFilePath = "",
                    renameCurrentName = ""
                )
            }
            is FileListIntent.ShowFileMenu -> {
                _state.value = _state.value.copy(fileMenuPath = intent.filePath)
            }
            is FileListIntent.HideFileMenu -> {
                _state.value = _state.value.copy(fileMenuPath = null)
            }
        }
    }

    private fun connectAndLoadFiles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            // 在IO线程执行网络操作
            withContext(Dispatchers.IO) {
                connectUseCase.execute(config)
            }
                .onSuccess {
                    loadFiles()
                    // 连接成功后保存最后访问的服务器和路径
                    dataStoreManager.saveLastAccess(config.id, _state.value.currentPath)
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "连接失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }
    
    /**
     * 确保连接有效，如果断开则重新连接
     */
    private suspend fun ensureConnected(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (!connectionManager.isConnected()) {
                connectUseCase.execute(config)
            } else {
                Result.success(Unit)
            }
        }
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            // 在IO线程执行网络操作
            withContext(Dispatchers.IO) {
                listFilesUseCase.execute(_state.value.currentPath)
            }
                .onSuccess { files ->
                    _state.value = _state.value.copy(
                        files = files,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "加载文件列表失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }

    private fun enterDirectory(path: String) {
        val currentPath = _state.value.currentPath
        val newPath = if (path == "..") {
            // 返回上级目录
            val history = _state.value.pathHistory
            if (history.isNotEmpty()) {
                history.last()
            } else {
                ""
            }
        } else {
            // 进入子目录
            if (currentPath.isEmpty()) {
                path
            } else {
                "$currentPath\\$path"
            }
        }

        val newHistory = if (path == "..") {
            _state.value.pathHistory.dropLast(1)
        } else {
            _state.value.pathHistory + currentPath
        }

        _state.value = _state.value.copy(
            currentPath = newPath,
            pathHistory = newHistory
        )
        // 保存最后访问的路径
        viewModelScope.launch {
            dataStoreManager.saveLastAccess(config.id, newPath)
        }
        loadFiles()
    }

    private fun goBack() {
        val history = _state.value.pathHistory
        if (history.isNotEmpty()) {
            val previousPath = history.last()
            val newHistory = history.dropLast(1)
            _state.value = _state.value.copy(
                currentPath = previousPath,
                pathHistory = newHistory
            )
            // 保存最后访问的路径
            viewModelScope.launch {
                dataStoreManager.saveLastAccess(config.id, previousPath)
            }
            loadFiles()
        }
    }

    private fun downloadFile(filePath: String, fileName: String) {
        viewModelScope.launch {
            try {
                // 确保连接有效
                ensureConnected()
                    .onFailure { e ->
                        val errorMessage = if (e is Exception) {
                            ErrorHandler.getErrorMessageFromException(e)
                        } else {
                            "重新连接失败: ${e.message}"
                        }
                        _state.value = _state.value.copy(error = errorMessage)
                        return@launch
                    }
                
                // 获取文件大小
                val fileSize = withContext(Dispatchers.IO) {
                    fileRepository.getFileSize(filePath)
                }
                
                // 使用 StorageHelper 获取下载目录并构建本地保存路径
                // 注意：实际文件创建会在 TransferService 中使用 StorageHelper 处理 Android 10+ 兼容性
                val downloadDir = StorageHelper.getDownloadDirectory(getApplication())
                val localPath = File(downloadDir, fileName).absolutePath
                
                // 调用 TransferRepository 开始下载
                withContext(Dispatchers.IO) {
                    transferRepository.startDownload(
                        fileName = fileName,
                        remotePath = filePath,
                        localPath = localPath,
                        fileSize = fileSize,
                        config = config
                    )
                }
                
                // 下载任务已创建，显示统一提示
                _state.value = _state.value.copy(
                    error = null,
                    message = "下载已开始"
                )
            } catch (e: Exception) {
                val errorMessage = ErrorHandler.getErrorMessageFromException(e)
                _state.value = _state.value.copy(error = errorMessage)
            }
        }
    }

    private fun uploadFile(localFile: File) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isUploading = true, error = null)
                
                // 确保连接有效
                ensureConnected()
                    .onFailure { e ->
                        val errorMessage = if (e is Exception) {
                            ErrorHandler.getErrorMessageFromException(e)
                        } else {
                            "重新连接失败: ${e.message}"
                        }
                        _state.value = _state.value.copy(
                            isUploading = false,
                            error = errorMessage
                        )
                        return@launch
                    }
                
                // 获取文件大小
                val fileSize = localFile.length()
                
                // 构建远程路径
                val remotePath = if (_state.value.currentPath.isEmpty()) {
                    localFile.name
                } else {
                    "${_state.value.currentPath}\\${localFile.name}"
                }
                
                // 调用 TransferRepository 开始上传
                withContext(Dispatchers.IO) {
                    transferRepository.startUpload(
                        fileName = localFile.name,
                        localPath = localFile.absolutePath,
                        remotePath = remotePath,
                        fileSize = fileSize,
                        config = config
                    )
                }
                
                // 上传任务已创建，显示统一提示
                _state.value = _state.value.copy(
                    isUploading = false,
                    error = null,
                    message = "上传已开始"
                )
                
                // 刷新文件列表（稍后会显示上传的文件）
                loadFiles()
            } catch (e: Exception) {
                val errorMessage = ErrorHandler.getErrorMessageFromException(e)
                _state.value = _state.value.copy(
                    isUploading = false,
                    error = errorMessage
                )
            }
        }
    }
    
    private fun createFolder(folderName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, showCreateFolderDialog = false)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "重新连接失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                    return@launch
                }
            
            withContext(Dispatchers.IO) {
                createFolderUseCase.execute(
                    folderName = folderName,
                    parentPath = _state.value.currentPath
                )
            }
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "创建文件夹失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }
    
    private fun deleteFile(filePath: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, fileMenuPath = null)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "重新连接失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                    return@launch
                }
            
            withContext(Dispatchers.IO) {
                deleteFileUseCase.execute(filePath)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "删除失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }
    
    private fun renameFile(filePath: String, newName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, showRenameDialog = false)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "重新连接失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                    return@launch
                }
            
            withContext(Dispatchers.IO) {
                renameFileUseCase.execute(filePath, newName)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = if (e is Exception) {
                        ErrorHandler.getErrorMessageFromException(e)
                    } else {
                        "重命名失败: ${e.message}"
                    }
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectUseCase.disconnect()
    }
}

