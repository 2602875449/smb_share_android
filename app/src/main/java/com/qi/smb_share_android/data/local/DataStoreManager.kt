package com.qi.smb_share_android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qi.smb_share_android.data.model.SMBConfig
import com.qi.smb_share_android.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "smb_configs")
private val Context.lastAccessDataStore: DataStore<Preferences> by preferencesDataStore(name = "last_access")
private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class DataStoreManager(private val context: Context) {
    private val configsKey = stringPreferencesKey("saved_configs")
    private val lastConfigIdKey = stringPreferencesKey("last_config_id")
    private val lastPathKey = stringPreferencesKey("last_path")
    
    // 应用设置相关的键
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val permissionRequestedPrefix = "permission_requested_"

    /**
     * 获取所有保存的连接配置
     */
    val savedConfigs: Flow<List<SMBConfig>> = context.dataStore.data.map { preferences ->
        val configsJson = preferences[configsKey] ?: "[]"
        try {
            parseConfigsFromJson(configsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 保存连接配置列表
     */
    suspend fun saveConfigs(configs: List<SMBConfig>) {
        context.dataStore.edit { preferences ->
            val jsonString = configsToJson(configs)
            preferences[configsKey] = jsonString
        }
    }

    /**
     * 添加或更新单个配置
     */
    suspend fun saveConfig(config: SMBConfig) {
        val currentConfigs = savedConfigs.first()
        val updatedConfigs = if (currentConfigs.any { it.id == config.id }) {
            // 更新现有配置
            currentConfigs.map { if (it.id == config.id) config else it }
        } else {
            // 添加新配置
            currentConfigs + config
        }
        saveConfigs(updatedConfigs)
    }

    /**
     * 删除配置
     */
    suspend fun deleteConfig(configId: String) {
        val currentConfigs = savedConfigs.first()
        val updatedConfigs = currentConfigs.filter { it.id != configId }
        saveConfigs(updatedConfigs)
    }

    private fun configsToJson(configs: List<SMBConfig>): String {
        val jsonArray = JSONArray()
        configs.forEach { config ->
            val jsonObject = JSONObject().apply {
                put("id", config.id)
                put("name", config.name)
                put("serverAddress", config.serverAddress)
                put("port", config.port)
                put("shareName", config.shareName)
                put("username", config.username)
                put("password", config.password)
                put("isAnonymous", config.isAnonymous)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun parseConfigsFromJson(jsonString: String): List<SMBConfig> {
        val jsonArray = JSONArray(jsonString)
        val configs = mutableListOf<SMBConfig>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            configs.add(
                SMBConfig(
                    id = jsonObject.getString("id"),
                    name = jsonObject.optString("name", ""),
                    serverAddress = jsonObject.getString("serverAddress"),
                    port = jsonObject.optInt("port", 445),
                    shareName = jsonObject.getString("shareName"),
                    username = jsonObject.optString("username", ""),
                    password = jsonObject.optString("password", ""),
                    isAnonymous = jsonObject.optBoolean("isAnonymous", false)
                )
            )
        }
        return configs
    }
    
    /**
     * 保存最后访问的服务器ID和路径
     */
    suspend fun saveLastAccess(configId: String, path: String) {
        context.lastAccessDataStore.edit { preferences ->
            preferences[lastConfigIdKey] = configId
            preferences[lastPathKey] = path
        }
    }
    
    /**
     * 获取最后访问的服务器ID和路径
     */
    suspend fun getLastAccess(): Pair<String?, String?> {
        val preferences = context.lastAccessDataStore.data.first()
        val configId = preferences[lastConfigIdKey]
        val path = preferences[lastPathKey]
        return Pair(configId, path)
    }
    
    /**
     * 清除最后访问记录
     */
    suspend fun clearLastAccess() {
        context.lastAccessDataStore.edit { preferences ->
            preferences.remove(lastConfigIdKey)
            preferences.remove(lastPathKey)
        }
    }
    
    // ==================== 应用设置相关方法 ====================
    
    /**
     * 设置引导完成状态
     */
    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[onboardingCompletedKey] = completed
        }
    }
    
    /**
     * 检查引导是否已完成
     */
    suspend fun isOnboardingCompleted(): Boolean {
        val preferences = context.appSettingsDataStore.data.first()
        return preferences[onboardingCompletedKey] ?: false
    }
    
    /**
     * 设置主题模式
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[themeModeKey] = mode.name
        }
    }
    
    /**
     * 获取主题模式（Flow）
     */
    fun getThemeMode(): Flow<ThemeMode> = context.appSettingsDataStore.data.map { preferences ->
        val modeName = preferences[themeModeKey] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeName)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * 设置权限请求标记
     */
    suspend fun setPermissionRequested(permission: String, requested: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            val key = booleanPreferencesKey("${permissionRequestedPrefix}${permission}")
            preferences[key] = requested
        }
    }
    
    /**
     * 检查权限是否已请求过
     */
    suspend fun isPermissionRequested(permission: String): Boolean {
        val preferences = context.appSettingsDataStore.data.first()
        val key = booleanPreferencesKey("${permissionRequestedPrefix}${permission}")
        return preferences[key] ?: false
    }
}

