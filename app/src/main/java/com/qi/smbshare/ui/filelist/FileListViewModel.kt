package com.qi.smbshare.ui.filelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.R
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.FileItem
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@HiltViewModel(assistedFactory = FileListViewModel.Factory::class)
class FileListViewModel @AssistedInject constructor(
    application: Application,
    private val transferRepository: TransferRepository,
    private val dataStoreManager: DataStoreManager,
    private val connectUseCase: ConnectSMBUseCase,
    private val fileRepository: SMBFileRepository,
    private val listFilesUseCase: ListFilesUseCase,
    private val uploadFileUseCase: UploadFileUseCase,
    private val createFolderUseCase: CreateFolderUseCase,
    private val deleteFileUseCase: DeleteFileUseCase,
    private val renameFileUseCase: RenameFileUseCase,
    @Assisted private val config: SMBConfig,
    @Assisted initialPath: String
) : AndroidViewModel(application) {
    private var previewJob: Job? = null

    private val initialBrowserPath = initialPath.trim('\\', '/')
    private val _state = MutableStateFlow(
        FileListState(
            currentPath = initialBrowserPath,
            pathHistory = buildPathHistory(initialBrowserPath)
        )
    )
    val state: StateFlow<FileListState> = _state.asStateFlow()

    init {
        // 连接并加载文件
        connectAndLoadFiles()
    }

    @AssistedFactory
    interface Factory {
        fun create(config: SMBConfig, initialPath: String): FileListViewModel
    }

    fun handleIntent(intent: FileListIntent) {
        when (intent) {
            is FileListIntent.LoadFiles -> {
                loadFiles()
            }
            is FileListIntent.EnterDirectory -> {
                enterDirectory(intent.path)
            }
            is FileListIntent.JumpToPath -> {
                jumpToPath(intent.path)
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
                uploadFile(
                    fileUri = intent.uri,
                    displayName = intent.displayName,
                    size = intent.size
                )
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
            is FileListIntent.PreviewFile -> {
                previewFile(intent.filePath, intent.fileName)
            }
            is FileListIntent.ClosePreview -> {
                previewJob?.cancel()
                previewJob = null
                clearReadyPreviewCache()
                _state.value = _state.value.copy(
                    previewFileName = null,
                    previewState = PreviewState.Idle
                )
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
                    val errorMessage = formatError(e, R.string.error_connect_failed)
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
    private suspend fun ensureConnected(forceReconnect: Boolean = false): Result<Unit> {
        return withContext(Dispatchers.IO) {
            if (forceReconnect || !connectUseCase.isConnected()) {
                connectUseCase.execute(config)
            } else {
                Result.success(Unit)
            }
        }
    }

    private fun loadFiles() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            ensureConnected()
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_reconnect_failed)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                    return@launch
                }

            val result = loadFilesOnce(_state.value.currentPath)
            val finalResult = if (result.isFailure && shouldReconnectAndRetry(result.exceptionOrNull())) {
                // SMBJ 有时在共享已被服务端关闭后仍保留对象引用，重连后重试一次即可恢复刷新。
                ensureConnected(forceReconnect = true)
                    .fold(
                        onSuccess = { loadFilesOnce(_state.value.currentPath) },
                        onFailure = { Result.failure(it) }
                    )
            } else {
                result
            }

            finalResult
                .onSuccess { files ->
                    _state.value = _state.value.copy(
                        files = files,
                        isLoading = false
                    )
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_load_file_list_failed)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = errorMessage
                    )
                }
        }
    }

    private suspend fun loadFilesOnce(path: String): Result<List<FileItem>> {
        return withContext(Dispatchers.IO) {
            listFilesUseCase.execute(path)
        }
    }

    private fun shouldReconnectAndRetry(error: Throwable?): Boolean {
        val message = error?.message.orEmpty()
        val causeMessage = error?.cause?.message.orEmpty()
        return listOf(message, causeMessage).any { text ->
            text.contains("closed", ignoreCase = true) ||
                text.contains("未连接", ignoreCase = true) ||
                text.contains("connection", ignoreCase = true)
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

    private fun jumpToPath(path: String) {
        val targetPath = path.trim('\\', '/')
        if (targetPath == _state.value.currentPath) return

        val newHistory = buildPathHistory(targetPath)
        _state.value = _state.value.copy(
            currentPath = targetPath,
            pathHistory = newHistory
        )
        viewModelScope.launch {
            dataStoreManager.saveLastAccess(config.id, targetPath)
        }
        loadFiles()
    }

    private fun buildPathHistory(path: String): List<String> {
        if (path.isEmpty()) return emptyList()

        val segments = path
            .split('\\', '/')
            .filter { it.isNotBlank() }

        // 点击面包屑跳转时需要重建历史栈，保证系统返回键仍按目录层级返回。
        return buildList {
            add("")
            var current = ""
            segments.dropLast(1).forEach { segment ->
                current = if (current.isEmpty()) segment else "$current\\$segment"
                add(current)
            }
        }
    }

    private fun downloadFile(filePath: String, fileName: String) {
        viewModelScope.launch {
            try {
                // 确保连接有效
                ensureConnected()
                    .onFailure { e ->
                        val errorMessage = formatError(e, R.string.error_reconnect_failed)
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
                    message = text(R.string.msg_download_started)
                )
            } catch (e: Exception) {
                val errorMessage = formatError(e, R.string.error_download_start_failed)
                _state.value = _state.value.copy(error = errorMessage)
            }
        }
    }

    private fun uploadFile(fileUri: android.net.Uri, displayName: String, size: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isUploading = true, error = null)
                
                // 确保连接有效
                ensureConnected()
                    .onFailure { e ->
                        val errorMessage = formatError(e, R.string.error_reconnect_failed)
                        _state.value = _state.value.copy(
                            isUploading = false,
                            error = errorMessage
                        )
                        return@launch
                    }
                
                // 构建远程路径
                val remotePath = if (_state.value.currentPath.isEmpty()) {
                    displayName
                } else {
                    "${_state.value.currentPath}\\$displayName"
                }
                
                // 调用 TransferRepository 开始上传
                withContext(Dispatchers.IO) {
                    transferRepository.startUpload(
                        fileName = displayName,
                        localPath = fileUri.toString(),
                        remotePath = remotePath,
                        fileSize = size.coerceAtLeast(0L),
                        config = config
                    )
                }
                
                // 上传任务已创建，显示统一提示
                _state.value = _state.value.copy(
                    isUploading = false,
                    error = null,
                    message = text(R.string.msg_upload_started)
                )
                
            } catch (e: Exception) {
                val errorMessage = formatError(e, R.string.error_upload_start_failed)
                _state.value = _state.value.copy(
                    isUploading = false,
                    error = errorMessage
                )
            }
        }
    }
    
    private fun createFolder(folderName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isOperating = true, error = null, showCreateFolderDialog = false)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_reconnect_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
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
                    _state.value = _state.value.copy(isOperating = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_create_folder_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
                        error = errorMessage
                    )
                }
        }
    }
    
    private fun deleteFile(filePath: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isOperating = true, error = null, fileMenuPath = null)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_reconnect_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
                        error = errorMessage
                    )
                    return@launch
                }
            
            withContext(Dispatchers.IO) {
                deleteFileUseCase.execute(filePath)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isOperating = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_delete_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
                        error = errorMessage
                    )
                }
        }
    }
    
    private fun renameFile(filePath: String, newName: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isOperating = true, error = null, showRenameDialog = false)
            
            // 确保连接有效
            ensureConnected()
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_reconnect_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
                        error = errorMessage
                    )
                    return@launch
                }
            
            withContext(Dispatchers.IO) {
                renameFileUseCase.execute(filePath, newName)
            }
                .onSuccess {
                    _state.value = _state.value.copy(isOperating = false)
                    loadFiles() // 刷新文件列表
                }
                .onFailure { e ->
                    val errorMessage = formatError(e, R.string.error_rename_failed)
                    _state.value = _state.value.copy(
                        isOperating = false,
                        error = errorMessage
                    )
                }
        }
    }

    /**
     * 在线预览文件：根据文件类型分发到图片/文本/视频预览状态。
     * - 图片：流式写入缓存文件，交 Coil 从本地文件解码。
     * - 文本：最多读取 1 MB，超出截断，避免 OOM。
     * - 视频：流式写入缓存目录临时文件，带进度；完成后由 ExoPlayer 读取本地文件。
     */
    private fun previewFile(filePath: String, fileName: String) {
        previewJob?.cancel()
        clearReadyPreviewCache()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val activeJob = coroutineContext[Job]
            // 预览临时文件引用，被取消或出错时在 finally 中删除
            var tempVideoFile: File? = null
            var tempImageFile: File? = null
            try {
                _state.value = _state.value.copy(
                    previewFileName = fileName,
                    previewState = PreviewState.Loading,
                    fileMenuPath = null
                )

                ensureConnected()
                    .onFailure { e ->
                        if (previewJob !== activeJob) return@onFailure
                        val msg = formatError(e, R.string.error_reconnect_failed)
                        _state.value = _state.value.copy(previewState = PreviewState.Error(msg))
                        return@launch
                    }

                val isImage = com.qi.smbshare.util.FileTypeHelper.isImageFile(fileName)
                val isVideo = com.qi.smbshare.util.FileTypeHelper.isVideoFile(fileName)

                if (isVideo) {
                    // 视频：先获取文件大小以显示进度，再流式写入临时缓存文件
                    val fileSize = withContext(Dispatchers.IO) {
                        runCatching { fileRepository.getFileSize(filePath) }.getOrDefault(-1L)
                    }
                    if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@launch

                    val cacheFile = createVideoPreviewCacheFile(
                        cacheDir = getApplication<Application>().cacheDir,
                        fileName = fileName
                    )
                    tempVideoFile = cacheFile
                    val initialProgress = if (fileSize > 0) 0f else -1f
                    _state.value = _state.value.copy(
                        previewState = PreviewState.VideoDownloading(initialProgress)
                    )

                    val streamResult = withContext(Dispatchers.IO) {
                        runCatching {
                            fileRepository.getFileInputStream(filePath).use { input ->
                                cacheFile.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024)
                                    var totalRead = 0L
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        // 取消协程时即时中断写入
                                        ensureActive()
                                        output.write(buffer, 0, bytesRead)
                                        totalRead += bytesRead
                                        if (fileSize > 0) {
                                            val progress = (totalRead.toFloat() / fileSize).coerceIn(0f, 1f)
                                            // MutableStateFlow.value 线程安全，可直接在 IO 线程更新
                                            _state.value = _state.value.copy(
                                                previewState = PreviewState.VideoDownloading(progress)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    streamResult
                        .onSuccess {
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onSuccess
                            _state.value = _state.value.copy(
                                previewState = PreviewState.VideoReady(cacheFile)
                            )
                            // 所有权转让给 state，不在 finally 中删除
                            tempVideoFile = null
                        }
                        .onFailure { e ->
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onFailure
                            val msg = formatError(
                                e as? Exception ?: RuntimeException(e),
                                R.string.error_preview_failed
                            )
                            _state.value = _state.value.copy(previewState = PreviewState.Error(msg))
                        }
                } else if (isImage) {
                    val cacheFile = createImagePreviewCacheFile(
                        cacheDir = getApplication<Application>().cacheDir,
                        fileName = fileName
                    )
                    tempImageFile = cacheFile

                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            fileRepository.getFileInputStream(filePath).use { input ->
                                cacheFile.outputStream().use { output ->
                                    // 图片预览复用流式缓存路径，避免全量读入导致大图占用双份内存。
                                    val buffer = ByteArray(64 * 1024)
                                    var bytesRead: Int
                                    while (input.read(buffer).also { bytesRead = it } != -1) {
                                        ensureActive()
                                        output.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                        }
                    }

                    result
                        .onSuccess {
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onSuccess
                            _state.value = _state.value.copy(previewState = PreviewState.ImageReady(cacheFile))
                            // 所有权转让给 state，不在 finally 中删除
                            tempImageFile = null
                        }
                        .onFailure { e ->
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onFailure
                            val msg = formatError(
                                e as? Exception ?: RuntimeException(e),
                                R.string.error_preview_failed
                            )
                            _state.value = _state.value.copy(previewState = PreviewState.Error(msg))
                        }
                } else {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            fileRepository.getFileInputStream(filePath).use { input ->
                                // 文本：最多读取 1 MB，超出截断
                                val maxBytes = 1 * 1024 * 1024
                                val buffer = ByteArray(maxBytes + 1)
                                var totalRead = 0
                                var bytesRead: Int
                                while (totalRead <= maxBytes) {
                                    bytesRead = input.read(buffer, totalRead, buffer.size - totalRead)
                                    if (bytesRead == -1) break
                                    totalRead += bytesRead
                                }
                                buffer.copyOf(totalRead.coerceAtMost(maxBytes + 1))
                            }
                        }
                    }

                    result
                        .onSuccess { bytes ->
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onSuccess
                            val isTruncated = bytes.size > 1 * 1024 * 1024
                            val content = bytes.take(1 * 1024 * 1024).toByteArray()
                                .toString(Charsets.UTF_8)
                            _state.value = _state.value.copy(previewState = PreviewState.TextReady(content, isTruncated))
                        }
                        .onFailure { e ->
                            if (previewJob !== activeJob || _state.value.previewFileName != fileName) return@onFailure
                            val msg = formatError(
                                e as? Exception ?: RuntimeException(e),
                                R.string.error_preview_failed
                            )
                            _state.value = _state.value.copy(previewState = PreviewState.Error(msg))
                        }
                }
            } finally {
                // 被取消或出错时删除未移交的临时文件
                tempVideoFile?.delete()
                tempImageFile?.delete()
                if (previewJob === activeJob) {
                    previewJob = null
                }
            }
        }
        previewJob = job
        job.start()
    }

    private fun clearReadyPreviewCache() {
        // 只有已移交给 state 的缓存文件会走这里；下载/读取中的临时文件由协程 finally 清理。
        deleteReadyVideoCache(_state.value.previewState)
        deleteReadyImageCache(_state.value.previewState)
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
        previewJob?.cancel()
        previewJob = null
        clearReadyPreviewCache()
        connectUseCase.disconnect()
    }
}
