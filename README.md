# NetFold: SMB Manager

## 项目简介

`NetFold: SMB Manager` 是一个使用 Jetpack Compose 构建的 Android SMB 文件管理器，支持连接局域网内的 SMB/CIFS 共享服务，提供连接管理、文件浏览、文件传输、图片/视频预览等功能，方便在移动端完成常见的内网文件操作。

## 功能概览

- **连接管理**：创建、编辑与保存多个 SMB 服务器配置，自动恢复上次访问的服务器与路径。
- **文件浏览**：浏览远程目录与文件，支持记忆初始路径快速回到常用目录。
- **文件传输**：上传/下载任务统一管理，支持后台传输、暂停、恢复、取消与 APK 安装。
- **媒体预览**：图片（含 GIF）在线查看，视频通过 ExoPlayer 流式预览。
- **局域网发现**：自动扫描局域网内可用的 SMB 主机。
- **深色模式**：完整适配 Material You 浅色 / 深色主题。

## 技术架构

### 整体架构

采用 **MVVM + Clean Architecture** 分层架构：

```
ui/          ← Compose 界面层（Screen + ViewModel）
domain/      ← 业务逻辑层（用例 / 领域模型）
data/        ← 数据层（Repository + 本地存储 + SMB 通信）
di/          ← Hilt 依赖注入模块
service/     ← 后台传输 Service
util/        ← 通用工具类
```

### 主要技术栈

| 分类 | 库 / 技术 | 版本 |
|------|----------|------|
| UI 框架 | Jetpack Compose + Material 3 | BOM 2025.11.00 |
| 语言 | Kotlin | 2.2.21 |
| 依赖注入 | Hilt (Dagger) | 2.57.2 |
| 导航 | Navigation Compose | 2.9.6 |
| 异步 | Kotlin Coroutines + Flow | 1.10.2 |
| 本地存储 | Room (SQLite) | 2.8.3 |
| 配置持久化 | DataStore Preferences | 1.1.7 |
| SMB 协议 | smbj | 0.14.0 |
| 图片加载 | Coil 3（含 GIF 支持） | 3.2.0 |
| 视频播放 | Media3 ExoPlayer | 1.5.1 |
| 构建工具 | AGP + KSP | 8.13.1 / 2.3.2 |

### UI 模块划分

```
ui/
├── connection/   # 服务器连接管理
├── filelist/     # 文件列表与浏览
├── transfer/     # 传输任务管理
├── settings/     # 应用设置
├── navigation/   # 路由与导航图
├── components/   # 公共可复用组件
└── theme/        # Material You 主题定义
```

## 开发环境要求

- **Android Studio** Iguana 或更高版本
- **JDK 17**
- **Android 9（API 28）** 或更高版本的设备/模拟器
- Gradle 版本已锁定在 `gradle/wrapper/gradle-wrapper.properties` 中，无需手动配置

## 快速开始

1. 使用 Android Studio 打开项目根目录
2. 同步 Gradle 依赖并等待构建完成
3. 连接 Android 设备或启动模拟器
4. 运行 `app` 模块即可体验完整功能

也可以在命令行执行：

```bash
./gradlew assembleDebug
```

## 目录结构

```
app/src/main/java/com/qi/smbshare/
├── MainActivity.kt          # 应用入口，承载 Compose NavHost
├── SmbShareApplication.kt   # Application，初始化 Hilt
├── data/
│   ├── discovery/           # 局域网主机发现
│   ├── local/               # Room 数据库（传输记录）
│   ├── model/               # 数据模型（SMBConfig 等）
│   └── repository/          # 数据访问层抽象
├── di/                      # Hilt Module
├── domain/                  # 业务用例与领域模型
├── service/                 # 后台传输 ForegroundService
├── ui/                      # 各功能页面（见上方模块划分）
└── util/                    # 工具类（ApkInstaller 等）
```

## 运行测试

```bash
# 单元测试（使用 Robolectric + MockK）
./gradlew test

# Instrumented 测试
./gradlew connectedAndroidTest
```

## 许可证

本项目基于 [MIT License](LICENSE) 开源，你可以自由使用、修改和分发，但须保留原始版权声明。
