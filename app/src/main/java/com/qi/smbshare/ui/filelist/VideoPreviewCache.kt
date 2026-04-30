package com.qi.smbshare.ui.filelist

import java.io.File

private const val VideoPreviewCachePrefix = "video_preview_"
private const val MaxSafeVideoFileNameLength = 60

/**
 * 视频缓存文件名来自远端 SMB 文件名，创建本地文件前需要去掉路径分隔符等不安全字符。
 */
internal fun createVideoPreviewCacheFile(
    cacheDir: File,
    fileName: String,
    nowMillis: Long = System.currentTimeMillis()
): File {
    val safeName = fileName
        .take(MaxSafeVideoFileNameLength)
        .map { char ->
            if (char.isLetterOrDigit() || char == '.' || char == '_' || char == '-') char else '_'
        }
        .joinToString("")
        .trim('_')
        .ifBlank { "video" }

    return File(cacheDir, "${VideoPreviewCachePrefix}${nowMillis}_$safeName")
}

internal fun deleteReadyVideoCache(previewState: PreviewState): Boolean {
    val cacheFile = (previewState as? PreviewState.VideoReady)?.cacheFile ?: return false
    return !cacheFile.exists() || cacheFile.delete()
}
