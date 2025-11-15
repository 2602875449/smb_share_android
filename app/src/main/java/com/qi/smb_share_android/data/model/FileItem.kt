package com.qi.smb_share_android.data.model

import java.util.Date

data class FileItem(
    val name: String,
    val path: String, // 完整路径
    val isDirectory: Boolean,
    val size: Long = 0, // 文件大小（字节），文件夹为0
    val lastModified: Date? = null, // 最后修改时间
    val isReadOnly: Boolean = false
)

