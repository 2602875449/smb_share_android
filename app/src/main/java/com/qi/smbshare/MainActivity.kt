package com.qi.smbshare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.ThemeMode
import com.qi.smbshare.ui.navigation.AppNavGraph
import com.qi.smbshare.ui.theme.SmbShareAndroidTheme
import com.qi.smbshare.util.ApkInstaller
import com.qi.smbshare.util.LanguageHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val apkInstaller by lazy { ApkInstaller(this) }
    @Inject lateinit var dataStoreManager: DataStoreManager

    override fun attachBaseContext(newBase: Context) {
        // 在 Activity 附着 Context 时同步应用选择的语言，避免重启后依旧是系统默认语言
        super.attachBaseContext(LanguageHelper.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by dataStoreManager.getThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SmbShareAndroidTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavGraph(
                        onInstallApk = { file ->
                            apkInstaller.installApk(file)
                        }
                    )
                }
            }
        }
    }
}
