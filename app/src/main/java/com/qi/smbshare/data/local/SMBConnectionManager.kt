package com.qi.smbshare.data.local

import android.util.Log
import com.qi.smbshare.BuildConfig
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.model.SMBConfig
import java.io.IOException

private const val TAG = "SMBConnectionManager"

class SMBConnectionManager {
    private var connection: Connection? = null
    private var session: Session? = null
    private var diskShare: DiskShare? = null
    private val client = SMBClient()
    private val connectionLock = Any()

    /**
     * 连接到SMB服务器
     */
    @Throws(IOException::class)
    fun connect(config: SMBConfig): DiskShare {
        synchronized(connectionLock) {
            try {
                // 断开现有连接；连接三元组必须在同一把锁下更新，避免并发读取到半初始化状态。
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

                return diskShare!!
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
            }
        }
    }

    /**
     * 获取当前活动的共享连接
     */
    fun getDiskShare(): DiskShare? {
        return synchronized(connectionLock) { diskShare }
    }

    /**
     * 检查连接是否有效
     */
    fun isConnected(): Boolean {
        return synchronized(connectionLock) {
            try {
                diskShare?.isConnected == true
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 断开连接
     */
    fun disconnect() {
        synchronized(connectionLock) {
            disconnectLocked()
        }
    }

    private fun disconnectLocked() {
        try {
            diskShare?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        diskShare = null

        try {
            session?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        session = null

        try {
            connection?.close()
        } catch (e: Exception) {
            // 忽略关闭错误
        }
        connection = null
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
}
