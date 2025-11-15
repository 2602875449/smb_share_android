package com.qi.smb_share_android.data.model

data class SMBConfig(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "", // 配置名称，用于标识
    val serverAddress: String, // 服务器地址（IP或域名）
    val port: Int = 445, // 端口，默认445
    val shareName: String, // 共享文件夹名称
    val username: String = "", // 用户名
    val password: String = "", // 密码
    val isAnonymous: Boolean = false // 是否匿名登录
)

