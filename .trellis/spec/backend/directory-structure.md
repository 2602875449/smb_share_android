# 目录结构

> 本仓库是 Android/Kotlin 单模块应用，不是服务端后端。在本规范中，"backend" 指应用的数据、领域、服务、持久化和非 UI 业务逻辑层。

---

## 范围

不要为本项目记录或创建服务端 API 路由、controller、HTTP 响应格式或服务端模块。当前代码库在 `app/src/main/java/com/qi/smbshare` 下包含一个 Android 应用。

添加行为时，保持现有应用架构：

- `MainActivity.kt` 负责顶层 Compose 容器、导航、主题状态连接和 ViewModel factory 设置。
- `data/` 负责 model、本地持久化/SMB 基础能力和 repository。
- `domain/usecase/` 负责面向动作的 use case，用来包装 repository 或本地操作。
- `ui/<feature>/` 负责功能 screen、ViewModel、state 和 intent。
- `service/` 负责后台传输等长时间运行的 Android service。
- `util/` 负责横切辅助工具。

真实示例：

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`

---

## 分层布局

### 数据模型

将简单 data class 和 enum 放在 `data/model/` 下。

示例：

- `app/src/main/java/com/qi/smbshare/data/model/SMBConfig.kt`
- `app/src/main/java/com/qi/smbshare/data/model/FileItem.kt`
- `app/src/main/java/com/qi/smbshare/data/model/TransferTask.kt`
- `app/src/main/java/com/qi/smbshare/data/model/AppSettings.kt`

### 本地持久化与 SMB 基础能力

将本地 Android 存储、Room database 对象、DAO、entity 和 SMB 连接基础能力放在 `data/local/` 下。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`

### Repository 层

将具体持久化、SMB 文件操作和 service 启动协调工作放在 `data/repository/` 下。

示例：

- `app/src/main/java/com/qi/smbshare/data/repository/ConnectionRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`

### 局域网发现与平台网络能力

将局域网发现、mDNS、NetBIOS/NBT、端口探测和本机网络环境探测放在 `data/discovery/` 下。这里属于 Android 平台网络能力和纯协议解析的边界层，不要放进 Composable。

#### 1. Scope / Trigger

- 触发：新增或修改局域网设备发现、本机网络信息读取、mDNS/NBT/端口扫描等平台网络集成。
- 目标：UI 只发起 intent 和展示 state；发现流程由 ViewModel 调用可测试抽象完成。

#### 2. Signatures

- 发现入口使用接口抽象，例如 `SmbHostDiscovery.discover(): Flow<List<SmbDiscoveryHost>>`；需要跨子网覆盖时增加显式目标入口，例如 `SmbHostDiscovery.discover(target: SmbDiscoveryTarget)`。
- Android 实现可包装 `NsdManager`、`WifiManager.MulticastLock`、UDP socket 和 TCP socket。
- 协议编解码、手动目标解析、结果合并、IP 排序等逻辑应保持纯 Kotlin 函数或小类。

#### 3. Contracts

- 发现结果至少包含展示名、地址、端口和来源。
- 选择发现结果时只填充服务器地址、端口和空配置名时的默认名称；不要覆盖用户已经输入的共享名、用户名或密码。
- 端口探测只能表示端口可达，不代表共享名、认证或权限可用；完整连接验证仍走现有测试连接流程。
- mDNS、NetBIOS 广播和局域网广播通常不能跨子网/路由传播；手机与 SMB 主机不在同一网段但路由可达时，通过用户显式输入 IPv4 或 CIDR 目标进行 TCP 445 探测，不要让默认扫描自动扩大到任意路由网段。

#### 4. Validation & Error Matrix

- 无可扫描网络：返回空结果或用户友好的发现错误，不影响手动填写。
- 手动目标格式无效：在 ViewModel 中给出用户友好错误，不启动 socket 扫描。
- mDNS/NBT 单一路径失败：记录诊断日志并允许其他发现来源继续返回。
- 协议包格式异常：忽略该响应，不让单个坏包终止整个扫描。
- 扫描取消：停止 discovery、释放 multicast lock、取消 socket 扫描任务，并清除 loading 状态。

#### 5. Good/Base/Bad Cases

- Good：mDNS 和 NetBIOS 发现到同一 IP 时合并为一个结果，并保留来源集合。
- Good：手机在 `192.168.2.4`、SMB 主机在 `192.168.1.55` 且手动连接正常时，用户输入 `192.168.1.55` 或 `192.168.1.0/24` 后按显式目标探测路径发现主机。
- Base：只发现到 TCP 445 可达主机时，也可以显示 IP 结果。
- Bad：在 Composable 中直接创建 socket、阻塞扫描网段或解析协议包。
- Bad：把默认扫描范围从本机网段静默扩大到跨路由网段，导致慢扫描、误扫或电量消耗。

#### 6. Tests Required

- 纯 Kotlin 单测覆盖 NBT 包编解码、异常包忽略、发现结果去重/排序。
- 纯 Kotlin 单测覆盖手动目标解析，至少包含单个 IPv4、`/24` CIDR 和过大网段拒绝。
- ViewModel 单测覆盖启动扫描、扫描失败、停止扫描、选择主机填表且不覆盖凭据字段。
- 不要在 JVM 单测中依赖真实局域网、真实 socket 或真实 Android mDNS 服务；通过接口 fake。

#### 7. Wrong vs Correct

Wrong：

```kotlin
Button(onClick = { Socket(host, 445).close() }) { ... }
```

Correct：

```kotlin
viewModel.handleIntent(ConnectionIntent.StartDiscovery)
```

### Domain 用例

将单一职责、面向动作的操作放在 `domain/usecase/` 下。现有 use case 使用 `<Verb><Thing>UseCase` 这样的命名。

示例：

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/CreateFolderUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/DeleteFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/RenameFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/UploadFileUseCase.kt`

### 功能 UI

将 Compose 功能代码放在 `ui/<feature>/` 下。功能区域使用 `XXXScreen`、`XXXViewModel`、`XXXState` 和 `XXXIntent` 命名模式。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionState.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

### 共享 UI 与主题

将可复用 Compose 组件放在 `ui/components/` 下，将共享颜色、排版和主题定义放在 `ui/theme/` 下。

示例：

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- `app/src/main/java/com/qi/smbshare/ui/components/PermissionRationaleDialog.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Color.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Theme.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Type.kt`

### 横切工具

将共享辅助工具放在 `util/` 下。创建新辅助工具前先搜索这个 package。

示例：

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`
- `app/src/main/java/com/qi/smbshare/util/StorageHelper.kt`
- `app/src/main/java/com/qi/smbshare/util/PermissionManager.kt`
- `app/src/main/java/com/qi/smbshare/util/ConfigSerializer.kt`
- `app/src/main/java/com/qi/smbshare/util/FileTypeHelper.kt`
- `app/src/main/java/com/qi/smbshare/util/ApkInstaller.kt`
- `app/src/main/java/com/qi/smbshare/util/FToastUtil.kt`

---

## State 与 Intent 模式

功能 ViewModel 通过 `asStateFlow()` 暴露私有 `MutableStateFlow` 对应的公开只读 `StateFlow`。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`

Screen 使用 `collectAsStateWithLifecycle()` 收集 state。

示例：

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

功能动作使用 sealed intent 表示。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerIntent.kt`

错误、消息、测试结果和导航目标等一次性 UI effect 当前以 state 表示，在 `LaunchedEffect` 中消费，然后通过 intent 清除。

示例：

- `ConnectionScreen` 消费 `state.error`、`state.testResult`、`state.navigateToFileList` 和 `state.navigateToEdit`。
- `ConnectionViewModel` 处理 `ClearError` 和 `ClearNavigation`。

---

## 边界

- 不要在 Composable 中直接执行阻塞式 SMB、磁盘或网络工作。
- 不要在常规功能工作中把现有手动依赖构造迁移到依赖注入框架；本项目当前手动构造依赖。
- 不要引入 Android 应用中不存在的服务端/backend 概念。
- 除非任务明确要求，否则不要进行大范围 package 重构。
