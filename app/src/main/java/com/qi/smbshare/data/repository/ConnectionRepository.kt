package com.qi.smbshare.data.repository

import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.SMBConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ConnectionRepository @Inject constructor(
    private val dataStoreManager: DataStoreManager
) {
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

    /**
     * 获取最后访问的连接配置 ID 和路径
     */
    suspend fun getLastAccess(): Pair<String?, String?> {
        return dataStoreManager.getLastAccess()
    }
}
