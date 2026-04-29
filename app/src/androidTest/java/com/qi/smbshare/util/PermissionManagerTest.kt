package com.qi.smbshare.util

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qi.smbshare.testutil.PermissionTestActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证权限管理器在不同授权结果下的回调路径。
 */
@RunWith(AndroidJUnit4::class)
class PermissionManagerTest {

    @Test
    fun requestStoragePermission_shouldLaunchAndCacheHistory() {
        ActivityScenario.launch(PermissionTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val launcher = RecordingPermissionLauncher()
                val manager = PermissionManager(activity, launcher)

                var grantedCalled = false
                manager.requestDownloadPermission(
                    onGranted = { grantedCalled = true },
                    onDenied = {},
                    onPermanentlyDenied = {}
                )

                if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.P) {
                    // API 29+ 无需存储权限，应直接走 onGranted 回调而非弹窗
                    assertTrue("API 29+ 应直接授权", grantedCalled)
                    assertFalse("API 29+ 不应启动权限弹窗", launcher.launched)
                } else {
                    // API 28 需要 WRITE_EXTERNAL_STORAGE，应触发权限请求
                    assertTrue("API 28 应触发权限请求流程", launcher.launched)
                    val requestedPermissions = launcher.lastPermissions
                    require(!requestedPermissions.isNullOrEmpty())
                    val prefs = activity.getSharedPreferences("permission_prefs", Context.MODE_PRIVATE)
                    requestedPermissions.forEach { permission ->
                        assertTrue(
                            "被请求的权限需持久化记录，避免重复弹窗提示",
                            prefs.getBoolean("requested_$permission", false)
                        )
                    }
                }
            }
        }
    }

    /**
     * 验证 handlePermissionResult 的三路回调分发逻辑。
     * API 33+ 使用通知权限作为测试载体（该权限在此 API 级别需要请求）；
     * API < 33 通知权限无需请求，仅校验空结果不崩溃。
     */
    @Test
    fun handlePermissionResult_shouldDispatchCorrectCallbacks() {
        ActivityScenario.launch(PermissionTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val launcher = RecordingPermissionLauncher()
                val manager = PermissionManager(activity, launcher)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permission = Manifest.permission.POST_NOTIFICATIONS

                    // ---- 授权成功路径 ----
                    var grantedCalled = false
                    manager.requestNotificationPermission(
                        onGranted = { grantedCalled = true },
                        onDenied = {},
                        onPermanentlyDenied = {}
                    )
                    manager.handlePermissionResult(mapOf(permission to true))
                    assertTrue("授权成功后应走 granted 回调", grantedCalled)

                    // ---- 普通拒绝路径（shouldShowRationale = true）----
                    var deniedCalled = false
                    manager.requestNotificationPermission(
                        onGranted = {},
                        onDenied = { deniedCalled = true },
                        onPermanentlyDenied = {}
                    )
                    activity.setRationale(permission, true)
                    manager.handlePermissionResult(mapOf(permission to false))
                    assertTrue("普通拒绝应走 denied 回调", deniedCalled)

                    // ---- 永久拒绝路径（shouldShowRationale = false）----
                    var permanentlyDeniedCalled = false
                    manager.requestNotificationPermission(
                        onGranted = {},
                        onDenied = {},
                        onPermanentlyDenied = { permanentlyDeniedCalled = true }
                    )
                    activity.resetRationale()
                    manager.handlePermissionResult(mapOf(permission to false))
                    assertTrue("永久拒绝需触发 permanentlyDenied 回调", permanentlyDeniedCalled)
                } else {
                    // API < 33：无弹窗权限场景，仅验证空结果调用不崩溃
                    manager.handlePermissionResult(emptyMap())
                }
            }
        }
    }

    private class RecordingPermissionLauncher :
        ActivityResultLauncher<Array<String>>() {

        var launched: Boolean = false
        var lastPermissions: Array<String>? = null
        override val contract = ActivityResultContracts.RequestMultiplePermissions()

        override fun launch(input: Array<String>, options: ActivityOptionsCompat?) {
            launched = true
            lastPermissions = input
        }

        override fun unregister() = Unit
    }
}
