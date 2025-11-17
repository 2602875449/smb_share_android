package com.qi.smb_share_android.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smb_share_android.data.local.DataStoreManager
import com.qi.smb_share_android.data.model.ThemeMode
import com.qi.smb_share_android.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 设置页面的 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    
    private val dataStoreManager = DataStoreManager(application)
    
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        loadSettings()
        calculateCacheSize()
        loadDownloadDirectory()
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            dataStoreManager.getThemeMode().collect { themeMode ->
                _state.value = _state.value.copy(themeMode = themeMode)
            }
        }
    }
    
    /**
     * 设置主题模式
     */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStoreManager.setThemeMode(mode)
            _state.value = _state.value.copy(themeMode = mode)
        }
    }
    
    /**
     * 显示清除缓存对话框
     */
    fun showClearCacheDialog() {
        _state.value = _state.value.copy(showClearCacheDialog = true)
    }
    
    /**
     * 隐藏清除缓存对话框
     */
    fun hideClearCacheDialog() {
        _state.value = _state.value.copy(showClearCacheDialog = false)
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 清除应用缓存目录
                    val cacheDir = getApplication<Application>().cacheDir
                    deleteDirectory(cacheDir)
                    
                    // 重新计算缓存大小
                    calculateCacheSize()
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            _state.value = _state.value.copy(showClearCacheDialog = false)
        }
    }
    
    /**
     * 计算缓存大小
     */
    fun calculateCacheSize() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                try {
                    val cacheDir = getApplication<Application>().cacheDir
                    formatFileSize(getDirSize(cacheDir))
                } catch (e: Exception) {
                    "未知"
                }
            }
            _state.value = _state.value.copy(cacheSize = size)
        }
    }

    /**
     * 加载默认下载目录，明确展示文件实际保存位置
     */
    private fun loadDownloadDirectory() {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                try {
                    StorageHelper.getDisplayDownloadPath(getApplication())
                } catch (e: Exception) {
                    "未知"
                }
            }
            _state.value = _state.value.copy(downloadDirectory = path)
        }
    }
    
    /**
     * 递归删除目录
     */
    private fun deleteDirectory(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
        }
    }
    
    /**
     * 获取目录大小
     */
    private fun getDirSize(dir: File): Long {
        var size = 0L
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                size += if (file.isDirectory) {
                    getDirSize(file)
                } else {
                    file.length()
                }
            }
        }
        return size
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0))
        }
    }
}

/**
 * 设置页面的状态
 */
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showClearCacheDialog: Boolean = false,
    val cacheSize: String = "计算中...",
    val downloadDirectory: String = ""
)
