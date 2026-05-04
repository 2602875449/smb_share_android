package com.qi.smbshare.service.transfer

import android.util.Log
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val CONNECTION_POOL_TAG = "ServiceSmbConnectionPool"

interface ServiceSmbConnectionProvider {
    suspend fun acquire(config: SMBConfig): DiskShare
    fun release(config: SMBConfig)
    fun closeIdleConnections()
    fun closeAll()
}

internal interface ServiceSmbConnection {
    suspend fun connect(config: SMBConfig): DiskShare
    fun isConnected(): Boolean
    fun disconnect()
}

private class ManagerServiceSmbConnection(
    private val manager: SMBConnectionManager = SMBConnectionManager()
) : ServiceSmbConnection {
    override suspend fun connect(config: SMBConfig): DiskShare = manager.connect(config)
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

    override suspend fun acquire(config: SMBConfig): DiskShare {
        val bucket = synchronized(lock) {
            closeIdleConnectionsLocked()
            buckets.getOrPut(config.id) { Bucket(connectionFactory()) }.also {
                it.lastUsedAt = nowMillis()
                it.activeLeases += 1
            }
        }

        return try {
            // 同一配置的建连过程必须串行化，避免并发任务重复连接并互相关闭共享。
            bucket.connectMutex.withLock {
                if (!bucket.connection.isConnected()) {
                    synchronized(lock) {
                        if (bucket.hasConnectionAttempt) bucket.close()
                        bucket.hasConnectionAttempt = true
                    }
                    Log.d(CONNECTION_POOL_TAG, "为配置 ${config.id} 建立 Service 级 SMB 连接")
                    bucket.connection.connect(config).also { share ->
                        synchronized(lock) {
                            bucket.diskShare = share
                            bucket.lastUsedAt = nowMillis()
                        }
                    }
                } else {
                    synchronized(lock) { bucket.diskShare } ?: bucket.connection.connect(config).also { share ->
                        synchronized(lock) {
                            bucket.diskShare = share
                            bucket.lastUsedAt = nowMillis()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            release(config)
            throw e
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
        var hasConnectionAttempt: Boolean = false,
        val connectMutex: Mutex = Mutex()
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
