# smb_share_android

## 项目简介
`smb_share_android` 是一个使用 Jetpack Compose 构建的 Android 应用，用于连接并浏览 SMB 文件共享服务。应用内置了连接管理、文件浏览与传输管理等能力，方便在移动端完成常见的内网文件操作。

## 功能概览
- **连接管理**：创建、编辑与保存多个 SMB 服务器配置，并自动恢复上次访问的服务器与路径。
- **文件浏览**：在移动端浏览远程文件列表，配合记忆的初始路径快速回到常用目录。
- **传输管理**：统一查看上传/下载任务进度，支持暂停、恢复、取消与 APK 安装等操作。

## 开发环境要求
- Android Studio Iguana（或更高版本）。
- Android Gradle Plugin 8.x 与兼容的 Gradle 版本（项目已配置在 `build.gradle.kts` 与 `settings.gradle.kts` 中）。
- 推荐使用 JDK 17。

## 快速开始
1. 使用 Android Studio 打开项目根目录。
2. 同步 Gradle 依赖并等待构建完成。
3. 连接 Android 设备或启动模拟器。
4. 运行 `app` 模块即可体验完整功能。

也可以在命令行执行：

```bash
./gradlew assembleDebug
```

## 目录结构
项目的核心代码位于 `app/src/main/java/com/qi/smb_share_android`，其中：
- `data/`：数据层与本地存储相关的管理类，例如 `DataStoreManager` 与 `SMBConfig`。
- `domain/`：封装业务逻辑的用例与模型。
- `ui/`：按功能模块划分的 Compose 界面（连接管理、文件列表、传输管理等）。
- `util/`：通用工具类，例如处理 APK 安装的 `ApkInstaller`。

## 运行测试
项目包含基础的单元测试，可通过以下命令在终端执行：

```bash
./gradlew test
```

## 许可证
仓库未显式声明许可证，如需对外发布请先确认授权策略。
