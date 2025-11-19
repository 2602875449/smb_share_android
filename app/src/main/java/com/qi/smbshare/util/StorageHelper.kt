package com.qi.smbshare.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import androidx.core.net.toUri

/**
 * 存储辅助类
 * 处理 Android 10+ 的存储权限问题
 * 对于 Android 10+ 使用 MediaStore API，对于 Android 9 及以下使用传统方式
 */
object StorageHelper {
    private const val TAG = "StorageHelper"
    
    /**
     * 获取下载目录的文件路径
     * 优先返回公共下载目录，如果不可用则返回应用私有目录
     */
    fun getDownloadDirectory(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore，但这里返回路径用于显示
            // 实际写入需要使用 createDownloadFileOutputStream
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else {
            // Android 9 及以下，直接使用公共下载目录
            val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val appDownloadDir = File(publicDownloadDir, "SMBShare")
            if (!appDownloadDir.exists()) {
                appDownloadDir.mkdirs()
            }
            appDownloadDir
        }
    }

    /**
     * 获取展示用的下载目录
     * Android 10+ 实际写入在 Downloads/SMBShare，通过追加子目录保证用户看到的路径真实
     */
    fun getDisplayDownloadPath(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadDir, "SMBShare").absolutePath
        } else {
            getDownloadDirectory(context).absolutePath
        }
    }
    
    /**
     * 文件写入信息
     * 包含 OutputStream、文件路径和 URI（Android 10+）
     */
    data class FileWriteInfo(
        val outputStream: OutputStream,
        val filePath: String,
        val uri: android.net.Uri? = null  // Android 10+ 时不为 null
    )
    
    /**
     * 创建用于写入下载文件的 OutputStream
     * 根据 Android 版本自动选择合适的方式
     * 
     * @param context 上下文
     * @param fileName 文件名
     * @return Result<FileWriteInfo> 包含 OutputStream、文件路径和 URI 的信息
     */
    fun createDownloadFileOutputStream(
        context: Context,
        fileName: String
    ): Result<FileWriteInfo> {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                createDownloadFileOutputStreamQ(context, fileName)
            } else {
                // Android 9 及以下使用传统方式
                createDownloadFileOutputStreamLegacy(context, fileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建文件输出流失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Android 10+ 使用 MediaStore API 创建文件
     */
    @android.annotation.SuppressLint("InlinedApi")
    private fun createDownloadFileOutputStreamQ(
        context: Context,
        fileName: String
    ): Result<FileWriteInfo> {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SMBShare")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: return Result.failure(Exception("无法创建下载文件 URI"))
        
        try {
            val outputStream = resolver.openOutputStream(uri)
                ?: return Result.failure(Exception("无法打开文件输出流"))
            
            // 获取实际的文件路径（用于后续访问）
            val filePath = getFilePathFromUri(context, uri) ?: uri.toString()
            
            Log.d(TAG, "使用 MediaStore 创建文件: $fileName, URI: $uri, 路径: $filePath")
            return Result.success(FileWriteInfo(outputStream, filePath, uri))
        } catch (e: Exception) {
            // 如果创建失败，删除已创建的 URI
            resolver.delete(uri, null, null)
            return Result.failure(e)
        }
    }
    
    /**
     * Android 9 及以下使用传统方式创建文件
     */
    private fun createDownloadFileOutputStreamLegacy(
        context: Context,
        fileName: String
    ): Result<FileWriteInfo> {
        val downloadDir = getDownloadDirectory(context)
        val file = File(downloadDir, fileName)
        
        // 如果文件已存在，生成唯一文件名
        val uniqueFileName = generateUniqueFileName(downloadDir, fileName)
        val uniqueFile = File(downloadDir, uniqueFileName)
        
        try {
            // 确保父目录存在
            uniqueFile.parentFile?.mkdirs()
            
            val outputStream = FileOutputStream(uniqueFile)
            val filePath = uniqueFile.absolutePath
            
            Log.d(TAG, "使用传统方式创建文件: $uniqueFileName, 路径: $filePath")
            return Result.success(FileWriteInfo(outputStream, filePath, null))
        } catch (e: Exception) {
            Log.e(TAG, "创建文件失败: ${e.message}", e)
            return Result.failure(e)
        }
    }
    
    /**
     * 完成文件写入（Android 10+ 需要将 IS_PENDING 设置为 0）
     * 返回更新后的文件路径（去除 .pending- 前缀）
     * 
     * @param context 上下文
     * @param uri 文件的 URI（Android 10+ 时使用）
     * @return 更新后的文件路径，如果失败则返回 null
     */
    fun finishDownloadFile(context: Context, uri: android.net.Uri?): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri != null) {
            try {
                // 先尝试获取当前路径（可能包含 .pending- 前缀）
                val currentPath = getFilePathFromUri(context, uri)
                Log.d(TAG, "finishDownloadFile 前获取的路径: $currentPath")
                
                // 尝试更新 IS_PENDING 状态
                // 注意：某些设备可能会因为唯一约束冲突而失败，但文件已经成功写入
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }
                
                try {
                    val updated = context.contentResolver.update(uri, contentValues, null, null)
                    Log.d(TAG, "完成文件写入: $uri, 更新行数: $updated")
                } catch (e: android.database.sqlite.SQLiteConstraintException) {
                    // 唯一约束冲突：可能是因为文件路径已经存在
                    // 这通常发生在文件已经写入但 MediaStore 还没有更新时
                    Log.w(TAG, "更新 IS_PENDING 时出现唯一约束冲突（文件可能已存在）: ${e.message}")
                    // 继续尝试获取最终路径
                } catch (e: Exception) {
                    Log.w(TAG, "完成文件写入时出错: ${e.message}", e)
                    // 继续尝试获取最终路径
                }
                
                // 等待一小段时间，让 MediaStore 更新
                Thread.sleep(100)
                
                // 重新查询文件路径（此时应该已经去除 .pending- 前缀）
                val finalPath = getFilePathFromUri(context, uri)
                Log.d(TAG, "finishDownloadFile 后获取的最终路径: $finalPath")
                
                // 如果路径包含 .pending- 前缀，尝试手动去除
                if (finalPath != null && finalPath.contains(".pending-")) {
                    val cleanPath = finalPath.replace(Regex("\\.pending-\\d+-"), "")
                    if (File(cleanPath).exists()) {
                        Log.d(TAG, "手动去除 .pending- 前缀后的路径: $cleanPath")
                        return cleanPath
                    }
                }
                
                return finalPath
            } catch (e: Exception) {
                Log.e(TAG, "finishDownloadFile 异常: ${e.message}", e)
            }
        }
        return null
    }
    
    /**
     * 从 URI 获取文件路径（如果可能）
     * 优先使用 MediaStore.DATA 字段，如果不可用则尝试构建路径
     * 注意：如果路径包含 .pending- 前缀，说明文件还在 pending 状态
     */
    fun getFilePathFromUri(context: Context, uri: android.net.Uri): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(
                    MediaStore.Downloads.DATA,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.RELATIVE_PATH,
                    MediaStore.Downloads.IS_PENDING
                )
                val cursor = context.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        // 检查 IS_PENDING 状态
                        val pendingIndex = it.getColumnIndex(MediaStore.Downloads.IS_PENDING)
                        val isPending = if (pendingIndex >= 0) {
                            it.getInt(pendingIndex) == 1
                        } else {
                            false
                        }
                        
                        if (isPending) {
                            Log.w(TAG, "文件仍在 pending 状态，路径可能包含 .pending- 前缀")
                        }
                        
                        // 优先使用 DATA 字段（Android 10+ 可能为空，但某些设备仍可用）
                        val dataIndex = it.getColumnIndex(MediaStore.Downloads.DATA)
                        if (dataIndex >= 0) {
                            val dataPath = it.getString(dataIndex)
                            if (!dataPath.isNullOrEmpty()) {
                                // 如果路径包含 .pending- 前缀，说明文件还在 pending 状态
                                if (dataPath.contains(".pending-")) {
                                    Log.w(TAG, "从 DATA 字段获取的路径包含 .pending- 前缀: $dataPath")
                                    // 尝试去除 .pending- 前缀
                                    val cleanPath = dataPath.replace(Regex("\\.pending-\\d+-"), "")
                                    if (File(cleanPath).exists()) {
                                        Log.d(TAG, "去除 .pending- 前缀后的路径: $cleanPath")
                                        return cleanPath
                                    }
                                }
                                Log.d(TAG, "从 DATA 字段获取路径: $dataPath")
                                return dataPath
                            }
                        }
                        
                        // 如果 DATA 字段不可用，尝试构建路径
                        val displayNameIndex = it.getColumnIndex(MediaStore.Downloads.DISPLAY_NAME)
                        val relativePathIndex = it.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
                        
                        if (displayNameIndex >= 0) {
                            var displayName = it.getString(displayNameIndex)
                            val relativePath = if (relativePathIndex >= 0) {
                                it.getString(relativePathIndex)
                            } else {
                                Environment.DIRECTORY_DOWNLOADS + "/SMBShare"
                            }
                            
                            // 如果文件名包含 .pending- 前缀，去除它
                            if (displayName != null && displayName.contains(".pending-")) {
                                Log.w(TAG, "文件名包含 .pending- 前缀: $displayName")
                                displayName = displayName.replace(Regex("\\.pending-\\d+-"), "")
                                Log.d(TAG, "去除 .pending- 前缀后的文件名: $displayName")
                            }
                            
                            if (!displayName.isNullOrEmpty() && !relativePath.isNullOrEmpty()) {
                                // 构建完整路径
                                val publicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                // 移除 relativePath 开头的 "Download/" 前缀（如果存在）
                                val relativePathWithoutPrefix = relativePath.removePrefix(Environment.DIRECTORY_DOWNLOADS + "/")
                                val fullPath = File(publicDir, relativePathWithoutPrefix).absolutePath
                                val filePath = File(fullPath, displayName).absolutePath
                                
                                Log.d(TAG, "尝试构建路径 - relativePath: $relativePath, displayName: $displayName, filePath: $filePath")
                                
                                // 验证文件是否存在
                                val file = File(filePath)
                                if (file.exists()) {
                                    Log.d(TAG, "构建的文件路径有效: $filePath")
                                    return filePath
                                } else {
                                    // 如果构建的路径不存在，尝试直接使用 relativePath 和 displayName
                                    val alternativePath = File(publicDir,
                                        "$relativePathWithoutPrefix/$displayName"
                                    ).absolutePath
                                    val altFile = File(alternativePath)
                                    if (altFile.exists()) {
                                        Log.d(TAG, "使用替代路径: $alternativePath")
                                        return alternativePath
                                    } else {
                                        Log.w(TAG, "构建的路径文件不存在: $filePath, 替代路径也不存在: $alternativePath")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "无法从 URI 获取文件路径: ${e.message}", e)
            }
        }
        return null
    }
    
    /**
     * 检查文件是否存在
     * 支持普通文件路径和 URI 格式路径（Android 10+）
     * 
     * @param context 上下文
     * @param filePathOrUri 文件路径或 URI 字符串
     * @return 文件是否存在
     */
    fun fileExists(context: Context, filePathOrUri: String): Boolean {
        return try {
            // 如果是 URI 格式（以 content:// 开头）
            if (filePathOrUri.startsWith("content://")) {
                val uri = filePathOrUri.toUri()
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Downloads._ID),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    it.moveToFirst()
                } ?: false
            } else {
                // 普通文件路径
                File(filePathOrUri).exists()
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查文件是否存在时出错: ${e.message}", e)
            false
        }
    }
    
    /**
     * 删除文件
     * 支持普通文件路径和 URI 格式路径（Android 10+）
     * 
     * @param context 上下文
     * @param filePathOrUri 文件路径或 URI 字符串
     * @return 是否删除成功
     */
    fun deleteFile(context: Context, filePathOrUri: String): Boolean {
        return try {
            // 如果是 URI 格式（以 content:// 开头）
            if (filePathOrUri.startsWith("content://")) {
                val uri = filePathOrUri.toUri()
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted > 0) {
                    Log.d(TAG, "通过 MediaStore 删除文件成功: $filePathOrUri")
                    true
                } else {
                    Log.w(TAG, "通过 MediaStore 删除文件失败: $filePathOrUri")
                    false
                }
            } else {
                // 普通文件路径
                val file = File(filePathOrUri)
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "删除文件成功: $filePathOrUri")
                    } else {
                        Log.w(TAG, "删除文件失败: $filePathOrUri")
                    }
                    deleted
                } else {
                    Log.w(TAG, "文件不存在: $filePathOrUri")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "删除文件时出错: ${e.message}", e)
            false
        }
    }
    
    /**
     * 生成不重复的文件名
     */
    private fun generateUniqueFileName(directory: File, originalFileName: String): String {
        var targetFile = File(directory, originalFileName)
        
        if (!targetFile.exists()) {
            return originalFileName
        }
        
        val nameWithoutExtension = originalFileName.substringBeforeLast('.', originalFileName)
        val extension = if (originalFileName.contains('.')) {
            "." + originalFileName.substringAfterLast('.')
        } else {
            ""
        }
        
        var counter = 1
        while (targetFile.exists()) {
            val newFileName = "$nameWithoutExtension($counter)$extension"
            targetFile = File(directory, newFileName)
            counter++
            
            if (counter > 9999) {
                Log.w(TAG, "文件序号超过9999，使用时间戳")
                return "$nameWithoutExtension(${System.currentTimeMillis()})$extension"
            }
        }
        
        return targetFile.name
    }
}

