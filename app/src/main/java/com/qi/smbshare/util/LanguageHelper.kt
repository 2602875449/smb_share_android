package com.qi.smbshare.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Locale

/**
 * 语言类型枚举
 */
enum class AppLanguage(val code: String, val displayName: String) {
    SYSTEM("system", "Follow System"),
    ENGLISH("en", "English"),
    CHINESE("zh", "简体中文")
}

/**
 * 语言管理工具类
 */
object LanguageHelper {
    
    private const val PREFS_NAME = "app_language"
    private const val KEY_LANGUAGE = "selected_language"
    
    /**
     * 保存选择的语言
     */
    fun saveLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }
    
    /**
     * 获取保存的语言
     */
    fun getSavedLanguage(context: Context): AppLanguage {
        val code = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        return AppLanguage.values().find { it.code == code } ?: AppLanguage.ENGLISH
    }
    
    /**
     * 应用语言设置到 Context
     */
    fun applyLanguage(context: Context, language: AppLanguage = getSavedLanguage(context)): Context {
        val locale = when (language) {
            AppLanguage.SYSTEM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    context.resources.configuration.locale
                }
            }
            AppLanguage.ENGLISH -> Locale.ENGLISH
            AppLanguage.CHINESE -> Locale.SIMPLIFIED_CHINESE
        }
        
        val configuration = context.resources.configuration
        configuration.setLocale(locale)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
    
    /**
     * 重启应用以应用语言更改
     */
    fun restartApp(activity: Activity) {
        val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        activity.finish()
        activity.startActivity(intent)
    }
}
