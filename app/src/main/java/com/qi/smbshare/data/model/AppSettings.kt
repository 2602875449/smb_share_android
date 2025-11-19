package com.qi.smbshare.data.model

/**
 * 应用设置数据模型
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val onboardingCompleted: Boolean = false,
    val permissionsRequested: Map<String, Boolean> = emptyMap()
)

/**
 * 主题模式枚举
 */
enum class ThemeMode {
    SYSTEM,  // 跟随系统
    LIGHT,   // 浅色主题
    DARK     // 深色主题
}
