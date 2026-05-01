package com.qi.smbshare.service.transfer

import android.util.Log
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

private const val CONNECTION_POOL_TAG = "ServiceSmbConnectionPool"

interface ServiceSmbConnectionProvider {
    fun acquire(config: SMBConfig): DiskShare
    fun release(config: SMBConfig)
    fun closeIdleConnections()
    fun closeAll()
}

internal interface ServiceSmbConnection {
    fun connect(config: SMBConfig): DiskShare
    fun isConnected(): Boolean
    fun disconnect()
}

private class ManagerServiceSmbConnection(
    private val manager: SMBConnectionManager = SMBConnectionManager()
) : ServiceSmbConnection {
    override fun connect(config: SMBConfig): DiskShare = manager.connect(config)
    override fun isConnected(): Boolean = manager.isConnected()
    override fun disconnect() = manager.disconnect()
}

@ServiceScoped
class ServiceSmbConnectionPool @Inject constructor() : ServiceSmbConnectionProvider {
    internal var connectionFactory: () -> ServiceSmbConnection = { ManagerServiceSmbConnection() }
    internal var nowMillis: () -> Long = { System.currentTimeMillis() }
    internal var idleTimeoutMillis: Long = DEFAULT_IDLE_TIMEOUT_MS

    private val lock = Any()
    private val buckets = mutableMapOf<String, Bucket>()

    override fun acquire(config: SMBConfig): DiskShare {
        return synchronized(lock) {
            closeIdleConnectionsLocked()

            val bucket = buckets.getOrPut(config.id) {
                Bucket(connectionFactory())
            }
            bucket.lastUsedAt = nowMillis()

            val diskShare = if (!bucket.connection.isConnected()) {
                if (bucket.hasConnectionAttempt) {
                    bucket.close()
                }
                Log.d(CONNECTION_POOL_TAG, "为配置 ${config.id} 建立 Service 级 SMB 连接")
                bucket.hasConnectionAttempt = true
                bucket.connection.connect(config).also {
                    bucket.diskShare = it
                }
            } else {
                bucket.diskShare ?: bucket.connection.connect(config).also {
                    bucket.diskShare = it
                }
            }
            bucket.activeLeases += 1
            diskShare
        }
    }

    override fun release(config: SMBConfig) {
        synchronized(lock) {
            buckets[config.id]?.let { bucket ->
                bucket.activeLeases = (bucket.activeLeases - 1).coerceAtLeast(0)
                bucket.lastUsedAt = nowMillis()
            }
        }
    }

    override fun closeIdleConnections() {
        synchronized(lock) {
            closeIdleConnectionsLocked()
        }
    }

    override fun closeAll() {
        synchronized(lock) {
            buckets.values.forEach { it.close() }
            buckets.clear()
        }
    }

    private fun closeIdleConnectionsLocked() {
        val now = nowMillis()
        val expiredIds = buckets
            .filterValues { it.activeLeases == 0 && now - it.lastUsedAt >= idleTimeoutMillis }
            .keys
            .toList()

        expiredIds.forEach { id ->
            Log.d(CONNECTION_POOL_TAG, "回收空闲 SMB 连接: $id")
            buckets.remove(id)?.close()
        }
    }

    private data class Bucket(
        val connection: ServiceSmbConnection,
        var diskShare: DiskShare? = null,
        var lastUsedAt: Long = 0L,
        var activeLeases: Int = 0,
        var hasConnectionAttempt: Boolean = false
    ) {
        fun close() {
            try {
                connection.disconnect()
            } catch (e: Exception) {
                Log.w(CONNECTION_POOL_TAG, "关闭 SMB 连接时出错: ${e.message}")
            }
            diskShare = null
        }
    }

    private companion object {
        private const val DEFAULT_IDLE_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
