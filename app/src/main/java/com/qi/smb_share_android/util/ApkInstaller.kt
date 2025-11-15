package com.qi.smb_share_android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

private const val TAG = "ApkInstaller"

class ApkInstaller(private val context: Context) {
    companion object {
        private const val AUTHORITY_SUFFIX = ".fileprovider"
    }

    /**
     * 安装APK文件
     */
    fun installApk(apkFile: File) {
        Log.d(TAG, "开始安装APK: ${apkFile.absolutePath}")
        if (!apkFile.exists()) {
            Log.e(TAG, "APK文件不存在: ${apkFile.absolutePath}")
            throw IllegalArgumentException("APK文件不存在: ${apkFile.absolutePath}")
        }

        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 需要使用FileProvider
                val authority = "${context.packageName}$AUTHORITY_SUFFIX"
                Log.d(TAG, "使用FileProvider，authority: $authority")
                FileProvider.getUriForFile(context, authority, apkFile)
            } else {
                // Android 7.0以下直接使用file://
                Log.d(TAG, "使用file:// URI")
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            Log.d(TAG, "启动APK安装器")
            context.startActivity(intent)
            Log.d(TAG, "APK安装器启动成功")
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败", e)
            Log.e(TAG, "APK路径: ${apkFile.absolutePath}")
            Log.e(TAG, "错误类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "错误消息: ${e.message}")
            throw e
        }
    }
}

