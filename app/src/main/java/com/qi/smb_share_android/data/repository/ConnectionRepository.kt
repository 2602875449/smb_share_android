package com.qi.smb_share_android.data.repository

import android.content.Context
import com.qi.smb_share_android.data.local.DataStoreManager
import com.qi.smb_share_android.data.model.SMBConfig
import kotlinx.coroutines.flow.Flow

class ConnectionRepository(context: Context) {
    private val dataStoreManager = DataStoreManager(context)

    /**
     * 获取所有保存的连接配置
     */
    fun getSavedConfigs(): Flow<List<SMBConfig>> {
        return dataStoreManager.savedConfigs
    }

    /**
     * 保存连接配置
     */
    suspend fun saveConfig(config: SMBConfig) {
        dataStoreManager.saveConfig(config)
    }

    /**
     * 删除连接配置
     */
    suspend fun deleteConfig(configId: String) {
        dataStoreManager.deleteConfig(configId)
    }
}

