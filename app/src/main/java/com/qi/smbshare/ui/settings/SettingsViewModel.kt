package com.qi.smbshare.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qi.smbshare.R
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.ThemeMode
import com.qi.smbshare.util.LanguageHelper
import com.qi.smbshare.util.AppLanguage
import com.qi.smbshare.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        loadSettings()
        calculateCacheSize()
        loadDownloadDirectory()
        loadCurrentLanguage()
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
                    getApplication<Application>().getString(R.string.settings_value_unknown)
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
                    getApplication<Application>().getString(R.string.settings_value_unknown)
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
    
    /**
     * 加载当前语言设置
     */
    private fun loadCurrentLanguage() {
        val currentLanguage = LanguageHelper.getSavedLanguage(getApplication())
        _state.value = _state.value.copy(currentLanguage = currentLanguage)
    }
    
    /**
     * 显示语言选择对话框
     */
    fun showLanguageDialog() {
        _state.value = _state.value.copy(showLanguageDialog = true)
    }
    
    /**
     * 隐藏语言选择对话框
     */
    fun hideLanguageDialog() {
        _state.value = _state.value.copy(showLanguageDialog = false)
    }
    
    /**
     * 设置语言
     */
    fun setLanguage(language: AppLanguage) {
        LanguageHelper.saveLanguage(getApplication(), language)
        _state.value = _state.value.copy(currentLanguage = language)
    }
}

/**
 * 设置页面的状态
 */
data class SettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showClearCacheDialog: Boolean = false,
    val cacheSize: String = "计算中...",
    val downloadDirectory: String = "",
    val currentLanguage: AppLanguage = AppLanguage.SYSTEM,
    val showLanguageDialog: Boolean = false
)
