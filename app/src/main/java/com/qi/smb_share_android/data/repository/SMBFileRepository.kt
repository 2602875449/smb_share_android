package com.qi.smb_share_android.data.repository

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.File
import com.qi.smb_share_android.data.local.SMBConnectionManager
import com.qi.smb_share_android.data.model.FileItem
import java.io.IOException
import java.util.Date

private const val TAG = "SMBFileRepository"

class SMBFileRepository(private val connectionManager: SMBConnectionManager) {

    /**
     * 列出指定路径下的文件和文件夹
     */
    @Throws(IOException::class)
    fun listFiles(path: String = ""): List<FileItem> {
        Log.d(TAG, "开始列出文件，路径: $path")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "列出文件失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }

        try {
            val normalizedPath = normalizePath(path)
            // 对于根目录，使用空字符串 ""，不要使用 "."
            // SMB服务器不接受以 "." 结尾的路径（如 home\.）
            val directoryPath = when {
                normalizedPath.isEmpty() || normalizedPath == "\\" || normalizedPath == "." -> ""
                else -> normalizedPath
            }
            Log.d(TAG, "规范化后的路径: '$normalizedPath' -> 实际使用路径: '$directoryPath'")
            
            // 尝试打开目录
            val directory = try {
                Log.d(TAG, "尝试方式1: 使用完整参数打开目录")
                diskShare.openDirectory(
                    directoryPath,
                    setOf(AccessMask.GENERIC_READ),
                    setOf(FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                    setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    null,
                    null
                )
            } catch (e: Exception) {
                Log.w(TAG, "方式1失败: ${e.message}，尝试方式2: 不使用FileAttributes过滤")
                // 如果失败，尝试不使用 FileAttributes 过滤
                diskShare.openDirectory(
                    directoryPath,
                    setOf(AccessMask.GENERIC_READ),
                    null, // 不使用 FileAttributes 过滤
                    setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                    null,
                    null
                )
            }

            val fileItems = mutableListOf<FileItem>()

            // 如果不是根目录，添加返回上级目录的选项
            if (normalizedPath.isNotEmpty() && normalizedPath != "\\") {
                val parentPath = getParentPath(normalizedPath)
                fileItems.add(
                    FileItem(
                        name = "..",
                        path = parentPath,
                        isDirectory = true,
                        size = 0,
                        lastModified = null
                    )
                )
            }

            // 列出目录内容
            directory.list().forEach { fileInfo ->
                val fileName = fileInfo.fileName
                if (fileName != "." && fileName != "..") {
                    val filePath = if (normalizedPath.isEmpty() || normalizedPath == "\\") {
                        fileName
                    } else {
                        "$normalizedPath\\$fileName"
                    }

                    // fileAttributes 是 long 类型，使用位运算检查属性
                    // FILE_ATTRIBUTE_DIRECTORY = 0x10 (16)
                    val isDirectory = (fileInfo.fileAttributes and 0x10L) != 0L
                    val size = if (isDirectory) 0L else fileInfo.endOfFile
                    val lastModified = if (fileInfo.lastWriteTime != null) {
                        Date(fileInfo.lastWriteTime.toEpochMillis())
                    } else {
                        null
                    }

                    fileItems.add(
                        FileItem(
                            name = fileName,
                            path = filePath,
                            isDirectory = isDirectory,
                            size = size,
                            lastModified = lastModified,
                            // FILE_ATTRIBUTE_READONLY = 0x1 (1)
                            isReadOnly = (fileInfo.fileAttributes and 0x1L) != 0L
                        )
                    )
                }
            }

            directory.close()
            val sortedFiles = fileItems.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            Log.d(TAG, "列出文件成功，共 ${sortedFiles.size} 个文件/文件夹")
            return sortedFiles
        } catch (e: Exception) {
            Log.e(TAG, "列出文件失败", e)
            Log.e(TAG, "路径: $path")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("列出文件失败: ${e.message}", e)
        }
    }

    /**
     * 获取文件输入流用于下载
     */
    @Throws(IOException::class)
    fun getFileInputStream(filePath: String): java.io.InputStream {
        Log.d(TAG, "开始打开文件: $filePath")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "打开文件失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }

        try {
            val normalizedPath = normalizePath(filePath)
            Log.d(TAG, "规范化路径: $normalizedPath")
            val file: File = diskShare.openFile(
                normalizedPath,
                setOf(AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                null,
                null
            )

            Log.d(TAG, "文件打开成功: $filePath")
            return file.inputStream
        } catch (e: Exception) {
            Log.e(TAG, "打开文件失败", e)
            Log.e(TAG, "文件路径: $filePath")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("打开文件失败: ${e.message}", e)
        }
    }

    /**
     * 获取文件大小
     */
    @Throws(IOException::class)
    fun getFileSize(filePath: String): Long {
        Log.d(TAG, "开始获取文件大小: $filePath")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "获取文件大小失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }

        try {
            val normalizedPath = normalizePath(filePath)
            Log.d(TAG, "规范化路径: $normalizedPath")
            
            // 获取文件信息
            val fileInfo = diskShare.getFileInformation(normalizedPath)
            val fileSize = fileInfo.standardInformation.endOfFile
            
            Log.d(TAG, "文件大小获取成功: $fileSize 字节")
            return fileSize
        } catch (e: Exception) {
            Log.e(TAG, "获取文件大小失败", e)
            Log.e(TAG, "文件路径: $filePath")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("获取文件大小失败: ${e.message}", e)
        }
    }

    /**
     * 规范化路径（统一使用反斜杠）
     */
    private fun normalizePath(path: String): String {
        if (path.isEmpty()) return ""
        var p = path.trim()
        // 统一分隔符
        p = p.replace("/", "\\")
        // 去掉前后多余的反斜杠
        p = p.trim('\\')
        // 避免类似 "." 这种形式，根目录返回空字符串
        if (p == "." || p == ".\\" || p == "./") return ""
        return p
    }

    /**
     * 获取父目录路径
     */
    private fun getParentPath(path: String): String {
        val normalized = normalizePath(path)
        if (normalized.isEmpty() || normalized == "\\") return ""
        val parts = normalized.split("\\").filter { it.isNotEmpty() }
        return if (parts.size <= 1) "" else parts.dropLast(1).joinToString("\\")
    }
    
    /**
     * 上传文件到SMB服务器
     */
    @Throws(IOException::class)
    fun uploadFile(localFile: java.io.File, remotePath: String, onProgress: (Long, Long) -> Unit) {
        Log.d(TAG, "开始上传文件: ${localFile.name} 到 $remotePath")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "上传文件失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }
        
        try {
            val normalizedPath = normalizePath(remotePath)
            val filePath = if (normalizedPath.isEmpty()) {
                localFile.name
            } else {
                "$normalizedPath\\${localFile.name}"
            }
            Log.d(TAG, "规范化后的上传路径: $filePath")
            
            val file: File = diskShare.openFile(
                filePath,
                setOf(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                null,
                setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null
            )
            
            val totalBytes = localFile.length()
            var uploadedBytes = 0L
            
            file.outputStream.use { outputStream ->
                localFile.inputStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        uploadedBytes += bytesRead
                        onProgress(uploadedBytes, totalBytes)
                    }
                }
            }
            
            file.close()
            Log.d(TAG, "文件上传成功: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "上传文件失败", e)
            Log.e(TAG, "文件路径: $remotePath")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("上传文件失败: ${e.message}", e)
        }
    }
    
    /**
     * 创建文件夹
     */
    @Throws(IOException::class)
    fun createFolder(folderName: String, parentPath: String = "") {
        Log.d(TAG, "开始创建文件夹: $folderName 在 $parentPath")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "创建文件夹失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }
        
        val normalizedParentPath = normalizePath(parentPath)
        val folderPath = if (normalizedParentPath.isEmpty()) {
            folderName
        } else {
            "$normalizedParentPath\\$folderName"
        }
        
        try {
            Log.d(TAG, "规范化后的文件夹路径: $folderPath")
            diskShare.mkdir(folderPath)
            Log.d(TAG, "文件夹创建成功: $folderPath")
        } catch (e: Exception) {
            Log.e(TAG, "创建文件夹失败", e)
            Log.e(TAG, "文件夹路径: $folderPath")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("创建文件夹失败: ${e.message}", e)
        }
    }
    
    /**
     * 删除文件或文件夹
     */
    @Throws(IOException::class)
    fun deleteFileOrFolder(path: String) {
        Log.d(TAG, "开始删除: $path")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "删除失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }
        
        try {
            val normalizedPath = normalizePath(path)
            Log.d(TAG, "规范化后的路径: $normalizedPath")
            
            // 先尝试作为文件删除
            try {
                // 直接使用diskShare的rm方法删除文件
                diskShare.rm(normalizedPath)
                Log.d(TAG, "文件删除成功: $normalizedPath")
            } catch (e: Exception) {
                // 如果作为文件删除失败，尝试作为文件夹删除
                Log.d(TAG, "作为文件删除失败，尝试作为文件夹删除: ${e.message}")
                diskShare.rmdir(normalizedPath, true)
                Log.d(TAG, "文件夹删除成功: $normalizedPath")
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除失败", e)
            Log.e(TAG, "路径: $path")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("删除失败: ${e.message}", e)
        }
    }
    
    /**
     * 重命名文件或文件夹
     */
    @Throws(IOException::class)
    fun renameFileOrFolder(oldPath: String, newName: String) {
        Log.d(TAG, "开始重命名: $oldPath -> $newName")
        val diskShare = connectionManager.getDiskShare()
        if (diskShare == null) {
            Log.e(TAG, "重命名失败: 未连接到SMB服务器")
            throw IOException("未连接到SMB服务器")
        }
        
        try {
            val normalizedOldPath = normalizePath(oldPath)
            val parentPath = getParentPath(normalizedOldPath)
            val newPath = if (parentPath.isEmpty()) {
                newName
            } else {
                "$parentPath\\$newName"
            }
            Log.d(TAG, "规范化后的路径: $normalizedOldPath -> $newPath")
            
            // 打开文件或文件夹（需要 DELETE 权限才能重命名）
            val diskEntry = try {
                diskShare.openFile(
                    normalizedOldPath,
                    setOf(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                    null,
                    setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    null,
                    null
                )
            } catch (e: Exception) {
                // 如果作为文件打开失败，尝试作为文件夹打开
                diskShare.openDirectory(
                    normalizedOldPath,
                    setOf(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                    null,
                    setOf(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                    null,
                    null
                )
            }
            
            // 重命名
            diskEntry.rename(newPath)
            diskEntry.close()
            Log.d(TAG, "重命名成功: $normalizedOldPath -> $newPath")
        } catch (e: Exception) {
            Log.e(TAG, "重命名失败", e)
            Log.e(TAG, "旧路径: $oldPath")
            Log.e(TAG, "新名称: $newName")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw IOException("重命名失败: ${e.message}", e)
        }
    }
}

