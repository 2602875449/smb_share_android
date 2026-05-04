package com.qi.smbshare.ui.transfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import com.qi.smbshare.data.model.TransferTask
import java.io.File

/**
 * 打开文件
 * 根据文件类型使用合适的方式打开
 */
internal fun openFile(
    context: Context,
    task: TransferTask,
    onInstallApk: (File) -> Unit
) {
    val isContentUri = task.localPath.startsWith("content://")

    // 如果是 APK 文件，使用安装回调
    if (task.fileName.endsWith(".apk", ignoreCase = true)) {
        if (isContentUri) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(task.localPath), "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("TransferFileHelper", "安装 APK 失败: ${e.message}", e)
            }
        } else {
            val file = File(task.localPath)
            if (!file.exists()) {
                return
            }
            onInstallApk(file)
        }
        return
    }

    // 其他文件类型，使用系统默认应用打开
    try {
        val uri = if (isContentUri) {
            Uri.parse(task.localPath)
        } else {
            val file = File(task.localPath)
            if (!file.exists()) {
                return
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, getMimeType(task.fileName))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("TransferFileHelper", "打开文件失败: ${e.message}", e)
    }
}

/**
 * 打开文件所在的文件夹
 * 使用系统文件管理器打开文件所在目录
 * 统一指向 Download/SMBShare，确保用户打开后就是我们的默认目录
 */
internal fun openFolder(
    context: Context,
    task: TransferTask,
    savedTreeUri: Uri?,
    onRequestTreePermission: (Uri?) -> Unit
) {
    try {
        val defaultFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "SMBShare"
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }

        val storageAuthority = "com.android.externalstorage.documents"
        val downloadDocId = "primary:${Environment.DIRECTORY_DOWNLOADS}"
        val docIdCandidates = listOf(
            "$downloadDocId/SMBShare",
            "primary:Downloads/SMBShare"
        )

        // 使用 LinkedHashSet 保证尝试顺序，同时去重
        val candidateUris = linkedSetOf<Uri>()
        var permissionRequired = false

        val persistedTreeUri = savedTreeUri?.takeIf {
            hasPersistedDownloadTreePermission(context, it)
        }
        persistedTreeUri?.let { treeUri ->
            // 使用用户授权的目录，优先确保权限可用
            val currentDocId = DocumentsContract.getTreeDocumentId(treeUri)
            runCatching {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
            }.getOrNull()?.let(candidateUris::add)
            docIdCandidates.forEach { docId ->
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                }.getOrNull()?.let(candidateUris::add)
            }
        }

        // 方法1：构建树形 URI，再生成目录 Document URI，提高 ACTION_VIEW 打开的成功率
        val downloadTreeUri = runCatching {
            DocumentsContract.buildTreeDocumentUri(storageAuthority, downloadDocId)
        }.getOrNull()
        if (downloadTreeUri != null) {
            docIdCandidates.forEach { docId ->
                runCatching {
                    DocumentsContract.buildDocumentUriUsingTree(downloadTreeUri, docId)
                }.getOrNull()?.let(candidateUris::add)
            }
        }

        // 方法2：直接构建 Document URI（部分 ROM 只支持此形式）
        docIdCandidates.forEach { docId ->
            runCatching {
                DocumentsContract.buildDocumentUri(storageAuthority, docId)
            }.getOrNull()?.let(candidateUris::add)
        }

        // 方法3：兜底使用 FileProvider 暴露目录
        runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                defaultFolder
            )
        }.getOrNull()?.let(candidateUris::add)

        // 逐个尝试使用 ACTION_VIEW 打开，避免直接进入"选择文件"模式
        candidateUris.forEach { uri ->
            when (tryOpenFolderWithViewIntent(context, uri)) {
                FolderOpenResult.SUCCESS -> {
                    Log.d("TransferFileHelper", "成功通过 ACTION_VIEW 打开目录: $uri")
                    return
                }
                FolderOpenResult.PERMISSION_REQUIRED -> {
                    permissionRequired = true
                }
                FolderOpenResult.FAILED -> {}
            }
        }

        if (permissionRequired && persistedTreeUri == null) {
            Log.w("TransferFileHelper", "缺少目录访问权限，准备请求用户授权 Download/SMBShare")
            onRequestTreePermission(buildDownloadInitialUri(context))
            return
        }

        // 方法4：仍无法直接定位时，退回到 ACTION_OPEN_DOCUMENT（会进入选择界面，但至少定位到目录）
        val initialUri = runCatching {
            DocumentsContract.buildDocumentUri(storageAuthority, "$downloadDocId/SMBShare")
        }.getOrNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && initialUri != null) {
            val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
            context.startActivity(pickerIntent)
            Log.d("TransferFileHelper", "回退到 ACTION_OPEN_DOCUMENT，已定位到 SMBShare 目录")
            return
        }

        Log.e("TransferFileHelper", "所有方式均失败，无法打开 SMBShare 目录")
    } catch (e: Exception) {
        Log.e("TransferFileHelper", "打开文件夹时发生异常: ${e.message}", e)
    }
}

/**
 * 尝试通过 ACTION_VIEW 打开指定 URI 对应的目录
 * 某些 ROM 需要显式声明可写/可读和前缀权限，否则会抛出 ActivityNotFoundException
 */
private fun tryOpenFolderWithViewIntent(
    context: Context,
    uri: Uri
): FolderOpenResult {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = uri
        // 使用目录 MIME，提示系统这是一个文件夹
        setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK
        )
    }
    return try {
        context.startActivity(intent)
        FolderOpenResult.SUCCESS
    } catch (e: ActivityNotFoundException) {
        Log.w("TransferFileHelper", "当前 URI 无可处理的应用: ${e.message}")
        FolderOpenResult.FAILED
    } catch (e: SecurityException) {
        Log.w("TransferFileHelper", "缺少访问目录的权限: ${e.message}")
        FolderOpenResult.PERMISSION_REQUIRED
    } catch (e: Exception) {
        Log.w("TransferFileHelper", "ACTION_VIEW 打开目录失败: ${e.message}")
        FolderOpenResult.FAILED
    }
}

private enum class FolderOpenResult {
    SUCCESS,
    PERMISSION_REQUIRED,
    FAILED
}

private fun buildDownloadInitialUri(context: Context): Uri? {
    val storageAuthority = "com.android.externalstorage.documents"
    val downloadDocId = "primary:${Environment.DIRECTORY_DOWNLOADS}"
    return runCatching {
        DocumentsContract.buildDocumentUri(storageAuthority, downloadDocId)
    }.getOrNull()
}

/**
 * 根据文件名获取 MIME 类型
 */
private val textExtensions = setOf(
    "txt", "md", "json", "xml", "html", "csv", "log", "ini", "cfg"
)

// pdf/doc/xls 等文档类型属于 application/*，而非 text/*
private val documentExtensions = setOf(
    "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
)

private val imageExtensions = setOf(
    "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
)

private val videoExtensions = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv", "mpeg", "3gp"
)

private val audioExtensions = setOf(
    "mp3", "aac", "wav", "flac", "ogg", "m4a", "amr"
)

/**
 * 根据文件后缀映射到大类 MIME，优先减少系统弹窗中过多的候选应用
 */
private fun getMimeType(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when {
        extension in textExtensions -> "text/*"
        extension in documentExtensions -> "application/*"
        extension in imageExtensions -> "image/*"
        extension in videoExtensions -> "video/*"
        extension in audioExtensions -> "audio/*"
        else -> "*/*"
    }
}
