package com.qi.smb_share_android.util

object FileTypeHelper {
    /**
     * 判断是否为APK文件
     */
    fun isApkFile(fileName: String): Boolean {
        return fileName.lowercase().endsWith(".apk")
    }

    /**
     * 判断是否为图片文件
     */
    fun isImageFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    }

    /**
     * 判断是否为文档文件
     */
    fun isDocumentFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt")
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }
}

