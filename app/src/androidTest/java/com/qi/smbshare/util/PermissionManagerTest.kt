package com.qi.smbshare.util

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.qi.smbshare.testutil.PermissionTestActivity
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

                manager.requestStoragePermission(
                    onGranted = {},
                    onDenied = {},
                    onPermanentlyDenied = {}
                )

                assertTrue("应触发权限请求流程", launcher.launched)
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

    @Test
    fun handlePermissionResult_shouldDispatchCorrectCallbacks() {
        ActivityScenario.launch(PermissionTestActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val launcher = RecordingPermissionLauncher()
                val manager = PermissionManager(activity, launcher)
                var grantedCalled = false
                val grantedPermission = requestWithCallbacks(
                    manager,
                    launcher,
                    onGranted = { grantedCalled = true }
                )
                manager.handlePermissionResult(mapOf(grantedPermission to true))
                assertTrue("授权成功后应走 granted 回调", grantedCalled)

                var deniedCalled = false
                activity.resetRationale()
                val deniedPermission = requestWithCallbacks(
                    manager,
                    launcher,
                    onDenied = { deniedCalled = true }
                )
                activity.setRationale(deniedPermission, true)
                manager.handlePermissionResult(mapOf(deniedPermission to false))
                assertTrue("普通拒绝应走 denied 回调", deniedCalled)

                var permanentlyDeniedCalled = false
                activity.resetRationale()
                val permanentlyDeniedPermission = requestWithCallbacks(
                    manager,
                    launcher,
                    onPermanentlyDenied = { permanentlyDeniedCalled = true }
                )
                activity.setRationale(permanentlyDeniedPermission, false)
                manager.handlePermissionResult(mapOf(permanentlyDeniedPermission to false))
                assertTrue("永久拒绝需触发 permanentlyDenied 回调", permanentlyDeniedCalled)
            }
        }
    }

    /**
     * 为每段断言准备一次回调，确保 PermissionManager 内部回调被覆盖。
     */
    private fun requestWithCallbacks(
        manager: PermissionManager,
        launcher: RecordingPermissionLauncher,
        onGranted: () -> Unit = {},
        onDenied: () -> Unit = {},
        onPermanentlyDenied: () -> Unit = {}
    ): String {
        manager.requestStoragePermission(
            onGranted = onGranted,
            onDenied = onDenied,
            onPermanentlyDenied = onPermanentlyDenied
        )
        return launcher.lastPermissions?.firstOrNull()
            ?: throw AssertionError("未捕获到请求的权限")
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
