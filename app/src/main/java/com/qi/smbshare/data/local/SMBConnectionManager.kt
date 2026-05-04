package com.qi.smbshare.data.local

import android.util.Log
import androidx.annotation.GuardedBy
import com.qi.smbshare.BuildConfig
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.model.SMBConfig
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "SMBConnectionManager"

class SMBConnectionManager {
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null
    private val client = SMBClient()

    // 串行化连接生命周期变更；Mutex 在挂起时不占用线程。
    private val lifecycleMutex = Mutex()

    // stateLock 保护 activeDiskShareLeases 和 isDiskShareLifecycleChanging 的原子性，
    // 仅用于极短的 check-then-act 操作，不在其中 suspend。
    private val stateLock = Any()

    @GuardedBy("stateLock") private var activeDiskShareLeases = 0
    @GuardedBy("stateLock") private var isDiskShareLifecycleChanging = false

    // 用于 suspend 等待所有租约归还；StateFlow 线程安全且支持 first { } 收集。
    private val leaseCountFlow = MutableStateFlow(0)

    /**
     * 连接到SMB服务器（suspend：等待租约归还时挂起协程而非阻塞线程）
     */
    @Throws(IOException::class)
    suspend fun connect(config: SMBConfig): DiskShare = lifecycleMutex.withLock {
        try {
            // 标记生命周期变更：之后的 acquireDiskShare 将快速失败
            synchronized(stateLock) { isDiskShareLifecycleChanging = true }
            // 等待现有租约全部归还，挂起而非阻塞
            leaseCountFlow.first { it == 0 }
            disconnectLocked()

            // 创建连接
            connection = client.connect(
                config.serverAddress,
                config.port
            )

            // 创建认证上下文
            val authContext = if (config.isAnonymous) {
                // 匿名登录：使用 Guest 用户和空密码
                // 大多数 SMB 服务器使用 Guest 用户来实现匿名访问
                Log.d(TAG, "使用匿名登录（Guest 用户，空密码）")
                AuthenticationContext("Guest", "".toCharArray(), null)
            } else {
                // 用户名密码登录，仅在调试构建中打印用户名，避免生产日志泄露凭据
                if (BuildConfig.DEBUG) Log.d(TAG, "使用用户名密码登录: ${config.username}")
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null // 域名，null表示使用默认
                )
            }

            // 建立会话
            session = connection!!.authenticate(authContext)

            // 打开共享文件夹
            diskShare = session!!.connectShare(config.shareName) as DiskShare

            diskShare!!
        } catch (e: Exception) {
            Log.e(TAG, "========== 连接SMB服务器失败 ==========")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            Log.e(TAG, "服务器地址: ${config.serverAddress}:${config.port}")
            Log.e(TAG, "共享名称: ${config.shareName}")
            Log.e(TAG, "匿名登录: ${config.isAnonymous}")
            Log.e(TAG, "错误堆栈:", e)
            disconnectLocked()
            throw IOException("连接SMB服务器失败: ${e.message}", e)
        } finally {
            synchronized(stateLock) { isDiskShareLifecycleChanging = false }
        }
    }

    /**
     * 获取当前活动的共享连接
     */
    fun getDiskShare(): DiskShare? {
        return synchronized(stateLock) { diskShare }
    }

    @Throws(IOException::class)
    fun acquireDiskShare(): DiskShareLease {
        return synchronized(stateLock) {
            val currentShare = diskShare
            if (currentShare == null || isDiskShareLifecycleChanging) {
                Log.e(TAG, "获取共享连接失败: 未连接到SMB服务器")
                throw IOException("未连接到SMB服务器")
            }
            activeDiskShareLeases++
            leaseCountFlow.value = activeDiskShareLeases
            DiskShareLease(currentShare, this)
        }
    }

    @Throws(IOException::class)
    inline fun <T> withDiskShare(action: (DiskShare) -> T): T {
        acquireDiskShare().use { lease ->
            return action(lease.diskShare)
        }
    }

    /**
     * 检查连接是否有效
     */
    fun isConnected(): Boolean {
        val currentShare = synchronized(stateLock) { diskShare }
        return try {
            currentShare?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 断开连接。等待活跃租约归还后再关闭底层共享，避免中断正在进行的 SMB 文件操作。
     */
    fun disconnect() {
        runBlocking {
            lifecycleMutex.withLock {
                try {
                    synchronized(stateLock) { isDiskShareLifecycleChanging = true }
                    leaseCountFlow.first { it == 0 }
                    disconnectLocked()
                } finally {
                    synchronized(stateLock) { isDiskShareLifecycleChanging = false }
                }
            }
        }
    }

    private fun releaseDiskShareLease() {
        synchronized(stateLock) {
            activeDiskShareLeases--
            leaseCountFlow.value = activeDiskShareLeases
        }
    }

    private fun disconnectLocked() {
        // 原子地取出并清空三元组，防止并发调用时重复关闭同一对象
        val shareToClose: DiskShare?
        val sessionToClose: Session?
        val connectionToClose: Connection?
        synchronized(stateLock) {
            shareToClose = diskShare; diskShare = null
            sessionToClose = session; session = null
            connectionToClose = connection; connection = null
        }
        try { shareToClose?.close() } catch (e: Exception) {}
        try { sessionToClose?.close() } catch (e: Exception) {}
        try { connectionToClose?.close() } catch (e: Exception) {}
    }

    /**
     * 测试连接
     */
    @Throws(IOException::class)
    fun testConnection(config: SMBConfig): Boolean {
        Log.d(TAG, "========== 开始测试SMB连接 ==========")
        Log.d(TAG, "服务器地址: ${config.serverAddress}")
        Log.d(TAG, "端口: ${config.port}")
        Log.d(TAG, "共享名称: ${config.shareName}")
        Log.d(TAG, "匿名登录: ${config.isAnonymous}")
        // 用户名/密码仅在调试构建中打印
        if (BuildConfig.DEBUG && !config.isAnonymous) {
            Log.d(TAG, "用户名: ${config.username}")
        }
        
        var testShare: DiskShare? = null
        var testSession: Session? = null
        var testConnection: Connection? = null
        
        try {
            // 步骤1: 创建连接
            Log.d(TAG, "[步骤1] 正在连接到服务器...")
            testConnection = client.connect(config.serverAddress, config.port)
            Log.d(TAG, "[步骤1] ✓ 连接创建成功")
            
            // 步骤2: 创建认证上下文
            Log.d(TAG, "[步骤2] 正在创建认证上下文...")
            val authContext = if (config.isAnonymous) {
                Log.d(TAG, "使用匿名认证（Guest 用户，空密码）")
                AuthenticationContext("Guest", "".toCharArray(), null)
            } else {
                // 仅在调试构建中打印用户名，避免生产日志泄露凭据
                if (BuildConfig.DEBUG) Log.d(TAG, "使用用户名密码认证: ${config.username}")
                AuthenticationContext(
                    config.username,
                    config.password.toCharArray(),
                    null
                )
            }
            Log.d(TAG, "[步骤2] ✓ 认证上下文创建成功")
            
            // 步骤3: 建立会话（不打印会话ID，避免敏感信息泄露）
            Log.d(TAG, "[步骤3] 正在建立会话...")
            testSession = testConnection.authenticate(authContext)
            Log.d(TAG, "[步骤3] ✓ 会话建立成功")
            
            // 步骤4: 连接共享文件夹
            Log.d(TAG, "[步骤4] 正在连接共享文件夹...")
            testShare = testSession.connectShare(config.shareName) as DiskShare
            Log.d(TAG, "[步骤4] ✓ 共享文件夹连接成功")
            
            // 步骤5: 检查连接状态
            val isConnected = testShare.isConnected
            Log.d(TAG, "[步骤5] ✓ 连接状态: $isConnected")
            
            if (isConnected) {
                Log.d(TAG, "========== 连接测试成功 ==========")
            } else {
                Log.w(TAG, "========== 连接测试失败: 共享未连接 ==========")
            }
            
            return isConnected
        } catch (e: Exception) {
            Log.e(TAG, "========== 连接测试失败 ==========")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            Log.e(TAG, "错误堆栈:", e)
            throw IOException("连接测试失败: ${e.message}", e)
        } finally {
            Log.d(TAG, "正在清理测试连接资源...")
            try {
                testShare?.close()
                Log.d(TAG, "✓ 共享连接已关闭")
            } catch (e: Exception) {
                Log.w(TAG, "关闭共享连接时出错: ${e.message}")
            }
            
            try {
                testSession?.close()
                Log.d(TAG, "✓ 会话已关闭")
            } catch (e: Exception) {
                Log.w(TAG, "关闭会话时出错: ${e.message}")
            }
            
            try {
                testConnection?.close()
                Log.d(TAG, "✓ 连接已关闭")
            } catch (e: Exception) {
                Log.w(TAG, "关闭连接时出错: ${e.message}")
            }
            Log.d(TAG, "========== 测试连接结束 ==========")
        }
    }

    class DiskShareLease internal constructor(
        val diskShare: DiskShare,
        private val manager: SMBConnectionManager
    ) : AutoCloseable {
        private var closed = false

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            manager.releaseDiskShareLease()
        }
    }
}
