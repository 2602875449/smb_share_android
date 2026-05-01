package com.qi.smbshare.ui.filelist

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import com.qi.smbshare.data.model.SMBConfig

/**
 * FileListViewModel 工厂类
 * 用于携带 SMBConfig 和初始路径创建 FileListViewModel
 */
class FileListViewModelFactory(
    private val application: android.app.Application,
    private val config: SMBConfig,
    private val initialPath: String = ""
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FileListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FileListViewModel(application, config, initialPath) as T
        }
        Log.e("FileListViewModelFactory", "创建ViewModel失败: 未知的ViewModel类 ${modelClass.simpleName}")
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.simpleName}")
    }
}
