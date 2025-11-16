package com.qi.smb_share_android.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.edit

/**
 * 权限管理工具类
 * 负责处理运行时权限请求和状态管理
 */
class PermissionManager(
    private val activity: ComponentActivity,
    private val permissionLauncher: ActivityResultLauncher<Array<String>>
) {
    
    /**
     * 权限状态枚举
     */
    enum class PermissionStatus {
        GRANTED,           // 已授权
        DENIED,            // 拒绝
        PERMANENTLY_DENIED // 永久拒绝（用户选择了"不再询问"）
    }
    
    private var onGrantedCallback: (() -> Unit)? = null
    private var onDeniedCallback: (() -> Unit)? = null
    private var onPermanentlyDeniedCallback: (() -> Unit)? = null
    
    /**
     * 检查存储权限状态
     */
    fun checkStoragePermission(): PermissionStatus {
        val permissions = getStoragePermissions()
        
        // 检查所有需要的权限是否都已授予
        val allGranted = permissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
        
        if (allGranted) {
            return PermissionStatus.GRANTED
        }
        
        // 检查是否有权限被永久拒绝
        // 注意：只有在用户明确拒绝过权限后，才会被判定为永久拒绝
        val anyPermanentlyDenied = permissions.any { permission ->
            val isGranted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            val shouldShow = activity.shouldShowRequestPermissionRationale(permission)
            // 只有当权限未授予且不应该显示说明（用户选择了"不再询问"）时，才判定为永久拒绝
            // 但首次请求时，shouldShow 也是 false，所以需要额外判断
            !isGranted && !shouldShow && hasRequestedPermissionBefore(permission)
        }
        
        return if (anyPermanentlyDenied) {
            PermissionStatus.PERMANENTLY_DENIED
        } else {
            PermissionStatus.DENIED
        }
    }
    
    /**
     * 检查是否曾经请求过该权限
     * 通过 SharedPreferences 记录权限请求历史
     */
    private fun hasRequestedPermissionBefore(permission: String): Boolean {
        val prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("requested_$permission", false)
    }
    
    /**
     * 记录权限请求历史
     */
    private fun markPermissionAsRequested(permission: String) {
        val prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
        prefs.edit { putBoolean("requested_$permission", true) }
    }
    
    /**
     * 请求存储权限
     * @param onGranted 权限授予时的回调
     * @param onDenied 权限拒绝时的回调
     * @param onPermanentlyDenied 权限永久拒绝时的回调
     */
    fun requestStoragePermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: () -> Unit
    ) {
        this.onGrantedCallback = onGranted
        this.onDeniedCallback = onDenied
        this.onPermanentlyDeniedCallback = onPermanentlyDenied
        
        val permissions = getStoragePermissions()
        // 记录权限请求历史
        permissions.forEach { markPermissionAsRequested(it) }
        permissionLauncher.launch(permissions)
    }
    
    /**
     * 打开应用设置页面
     * 用于引导用户手动授予权限
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        activity.startActivity(intent)
    }
    
    /**
     * 获取需要请求的存储权限列表
     * 根据 Android 版本返回不同的权限
     */
    private fun getStoragePermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) 使用细粒度存储权限
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12 (API 30-32)
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            // Android 10 及以下 (API 29-)
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * 处理权限请求结果
     * 由外部调用（从 ActivityResultLauncher 的回调中）
     */
    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }
        
        if (allGranted) {
            // 所有权限都已授予
            onGrantedCallback?.invoke()
        } else {
            // 有权限被拒绝，检查是否是永久拒绝
            val anyPermanentlyDenied = permissions.keys.any { permission ->
                !permissions[permission]!! &&
                !activity.shouldShowRequestPermissionRationale(permission)
            }
            
            if (anyPermanentlyDenied) {
                onPermanentlyDeniedCallback?.invoke()
            } else {
                onDeniedCallback?.invoke()
            }
        }
        
        // 清除回调
        onGrantedCallback = null
        onDeniedCallback = null
        onPermanentlyDeniedCallback = null
    }
    
    companion object {
        /**
         * 检查是否需要请求存储权限
         * 在 Android 10+ (API 29+) 使用分区存储时，某些操作可能不需要权限
         */
        fun needsStoragePermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用分区存储，但读取媒体文件仍需要权限
                true
            } else {
                // Android 9 及以下需要存储权限
                true
            }
        }
    }
}
