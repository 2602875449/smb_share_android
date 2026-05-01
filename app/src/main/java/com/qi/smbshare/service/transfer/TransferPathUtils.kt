package com.qi.smbshare.service.transfer

object TransferPathUtils {
    /**
     * 规范化 SMB 路径，统一使用反斜杠并移除多余前缀。
     */
    fun normalizeSmbPath(path: String): String {
        return path
            .replace("/", "\\")
            .trimStart('\\')
    }
}
