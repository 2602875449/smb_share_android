package com.qi.smb_share_android.ui.filelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smb_share_android.data.local.DataStoreManager
import com.qi.smb_share_android.data.local.SMBConnectionManager
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.data.repository.DownloadRepository
import com.qi.smb_share_android.data.repository.SMBFileRepository
import com.qi.smb_share_android.domain.usecase.ConnectSMBUseCase
import com.qi.smb_share_android.domain.usecase.CreateFolderUseCase
import com.qi.smb_share_android.domain.usecase.DeleteFileUseCase
import com.qi.smb_share_android.domain.usecase.DownloadFileUseCase
import com.qi.smb_share_android.domain.usecase.ListFilesUseCase
import com.qi.smb_share_android.domain.usecase.RenameFileUseCase
import com.qi.smb_share_android.domain.usecase.UploadFileUseCase
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
    private val downloadRepository = DownloadRepository(application)
    private val dataStoreManager = DataStoreManager(application)
    private val connectUseCase = ConnectSMBUseCase(connectionManager)
    private val listFilesUseCase = ListFilesUseCase(fileRepository)
    private val downloadFileUseCase = DownloadFileUseCase(fileRepository, downloadRepository)
    private val uploadFileUseCase = UploadFileUseCase(fileRepository)
    private val createFolderUseCase = CreateFolderUseCase(fileRepository)
    private val deleteFileUseCase = DeleteFileUseCase(fileRepository)
    private val renameFileUseCase = RenameFileUseCase(fileRepository)

    private val _state = MutableStateFlow(FileListState(currentPath = initialPath))
    val state: StateFlow<FileListState> = _state.asStateFlow()

    // 监听下载状态
    init {
        viewModelScope.launch {
            downloadRepository.currentDownload.collect { downloadItem ->
                _state.value = _state.value.copy(
                    downloadItem = downloadItem,
                    isDownloading = downloadItem?.status == com.qi.smb_share_android.data.model.DownloadStatus.DOWNLOADING
                )
                if (downloadItem?.status == com.qi.smb_share_android.data.model.DownloadStatus.COMPLETED) {
                    downloadItem.localPath?.let { path ->
                        _state.value = _state.value.copy(
                            downloadedFile = File(path)
                        )
                    }
                }
            }
        }
    }

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
            is FileListIntent.ClearDownload -> {
                downloadRepository.clearDownload()
                _state.value = _state.value.copy(
                    downloadItem = null,
                    downloadedFile = null
                )
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
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "连接失败: ${e.message}"
                    )
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
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "加载文件列表失败: ${e.message}"
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
            _state.value = _state.value.copy(isDownloading = true, error = null)
            // 在IO线程执行网络操作
            withContext(Dispatchers.IO) {
                downloadFileUseCase.execute(
                    filePath = filePath,
                    fileName = fileName
                ) { progress, downloaded, total ->
                    // 进度更新由Flow自动处理
                }
            }
                .onSuccess { file ->
                    _state.value = _state.value.copy(
                        isDownloading = false,
                        downloadedFile = file
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isDownloading = false,
                        error = "下载失败: ${e.message}"
                    )
                }
        }
    }

    private fun uploadFile(localFile: File) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isUploading = true, error = null)
            withContext(Dispatchers.IO) {
                uploadFileUseCase.execute(
                    localFile = localFile,
                    remotePath = _state.value.currentPath
                ) { uploaded, total ->
                    // 进度更新可以在这里处理
                }
            }
                .onSuccess {
                    _state.value = _state.value.copy(isUploading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isUploading = false,
                        error = "上传失败: ${e.message}"
                    )
                }
        }
    }
    
    private fun createFolder(folderName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, showCreateFolderDialog = false)
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
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "创建文件夹失败: ${e.message}"
                    )
                }
        }
    }
    
    private fun deleteFile(filePath: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, fileMenuPath = null)
            withContext(Dispatchers.IO) {
                deleteFileUseCase.execute(filePath)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "删除失败: ${e.message}"
                    )
                }
        }
    }
    
    private fun renameFile(filePath: String, newName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, showRenameDialog = false)
            withContext(Dispatchers.IO) {
                renameFileUseCase.execute(filePath, newName)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isLoading = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "重命名失败: ${e.message}"
                    )
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectUseCase.disconnect()
    }
}

