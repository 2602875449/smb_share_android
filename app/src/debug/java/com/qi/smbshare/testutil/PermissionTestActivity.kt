package com.qi.smbshare.testutil

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 测试专用 Activity，支持控制 shouldShowRequestPermissionRationale 的返回值。
 */
class PermissionTestActivity : ComponentActivity() {

    private val rationaleOverrides = mutableMapOf<String, Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 无 UI，测试中直接驱动权限逻辑
    }

    fun setRationale(permission: String, shouldShow: Boolean) {
        rationaleOverrides[permission] = shouldShow
    }

    fun resetRationale() {
        rationaleOverrides.clear()
    }

    override fun shouldShowRequestPermissionRationale(permission: String): Boolean {
        return rationaleOverrides[permission] ?: false
    }
}
