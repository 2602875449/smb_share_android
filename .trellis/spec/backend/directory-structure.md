# 目录结构

> 本仓库是 Android/Kotlin 单模块应用，不是服务端后端。在本规范中，"backend" 指应用的数据、领域、服务、持久化和非 UI 业务逻辑层。

---

## 范围

不要为本项目记录或创建服务端 API 路由、controller、HTTP 响应格式或服务端模块。当前代码库在 `app/src/main/java/com/qi/smbshare` 下包含一个 Android 应用。

添加行为时，保持现有应用架构：

- `MainActivity.kt` 负责顶层 Compose 容器、导航、主题状态连接和 Hilt 入口点设置。
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

### 依赖注入

Hilt 是当前生产代码的依赖注入入口。新增可共享依赖时，优先通过构造函数注入或 `di/` 下的 Hilt module 提供，不要在 Activity、Service、ViewModel 或 Composable 中重复创建 repository、DataStore、Room DAO 等应用级依赖。

示例：

- `app/src/main/java/com/qi/smbshare/SmbShareApplication.kt`
- `app/src/main/java/com/qi/smbshare/di/AppModule.kt`
- `app/src/main/java/com/qi/smbshare/di/SmbViewModelModule.kt`
- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

#### 1. Scope / Trigger

- Trigger：新增或修改跨页面/跨服务共享的 repository、manager、Room database/DAO、Android `Context` 依赖、ViewModel 构造依赖，或迁移 framework-created Android class（Activity、Service）依赖接线。
- 目标：共享依赖在应用级或 ViewModel 级有明确生命周期，测试可以直接传入 fake/mock，不把业务依赖创建散落在 UI 或 service 生命周期方法里。

#### 2. Signatures

- Application：`class SmbShareApplication : Application()`，使用 `@HiltAndroidApp` 并在 Manifest 的 `<application android:name=".SmbShareApplication">` 注册。
- Android 入口：Activity/Service 使用 `@AndroidEntryPoint`；Service 字段注入使用 `@Inject lateinit var dependency`，不要尝试构造函数注入 framework-created class。
- 普通依赖：`class SomeRepository @Inject constructor(...)`。
- Module：`@Module @InstallIn(SingletonComponent::class)` 提供应用级单例，`@InstallIn(ViewModelComponent::class)` 提供 ViewModel 生命周期内共享的 SMB 连接相关对象。
- ViewModel：可直接注入的使用 `@HiltViewModel` + `@Inject constructor(...)`；需要运行时对象的使用 `@HiltViewModel(assistedFactory = ...)` + `@AssistedInject`。

#### 3. Contracts

- `DataStoreManager`、`TransferDatabase`、`TransferTaskDao`、`TransferRepository` 是应用级共享依赖，生产路径由 Hilt 提供。
- `SMBConnectionManager` 属于单个 ViewModel 的连接状态，不要做成应用级单例；同一个 ViewModel 内的 `ConnectSMBUseCase` 与 `SMBFileRepository` 应共享同一个 manager。
- `FileListViewModel` 的 `SMBConfig` 和 `initialPath` 保持导航运行时状态，不序列化进 route；通过 Hilt Assisted Injection 创建。
- `TransferService` 继续暴露原有 Intent action/extras，调用方仍通过 `TransferRepository` 启动或控制服务。

#### 4. Validation & Error Matrix

- Hilt/KSP 配置不一致 -> 编译期失败；根工程应同时声明 Hilt 与 KSP 插件 `apply false`，app 模块再应用插件。
- framework class 使用构造函数注入 -> Hilt 不能创建；改用 `@AndroidEntryPoint` 和字段注入。
- ViewModel 运行时参数放入普通 `@Inject constructor` -> Hilt 无法解析；改用 Assisted Injection 或 `SavedStateHandle`。
- SMB 连接 manager 提升为全局单例 -> 不同页面/配置可能共享连接状态；保持 ViewModel 作用域。

#### 5. Good/Base/Bad Cases

- Good：`TransferRepository @Inject constructor(@ApplicationContext context, dao)`，DAO 由 `AppModule` 从 `TransferDatabase` 提供。
- Good：`FileListViewModel` 用 assisted factory 接收 `SMBConfig` 和 `initialPath`，导航层只调用 factory。
- Base：纯工具对象没有状态且不需要替换时继续作为 `object` 或普通类使用。
- Bad：在 `Composable`、`ViewModel` 或 `Service.onCreate()` 中直接 `DataStoreManager(context)`、`TransferRepository(context)`。
- Bad：把 `SMBConfig` 转成导航 route 字符串只是为了满足 ViewModel 注入。

#### 6. Tests Required

- 构造函数注入变更后，受影响的 JVM 单测直接构造类并传入 fake/mock 依赖。
- 修改 Hilt module、Application、Activity、Service 或 ViewModel 注入路径后，至少运行 `./gradlew :app:compileDebugKotlin`。
- 修改 repository/DAO/DataStore 接线后，运行 `./gradlew :app:testDebugUnitTest`。
- 修改 Manifest 或 Android 入口点后，运行 `./gradlew :app:lintDebug`。

#### 7. Wrong vs Correct

Wrong：

```kotlin
class TransferManagerViewModel(application: Application) : AndroidViewModel(application) {
    private val transferRepository = TransferRepository(application)
}
```

Correct：

```kotlin
@HiltViewModel
class TransferManagerViewModel @Inject constructor(
    application: Application,
    private val transferRepository: TransferRepository
) : AndroidViewModel(application)
```

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

### 顶层导航

将应用级 Navigation Compose 路由、底部导航和顶层页面组装放在 `ui/navigation/` 下，避免继续在 `MainActivity.kt` 中堆积页面切换状态。

约定：

- `MainActivity.kt` 只负责 Activity 生命周期、语言 context、主题读取和调用 `AppNavGraph`。
- `AppNavGraph` 负责 `NavHost`、顶层 ViewModel 获取、运行时导航状态（例如当前 SMB 配置、文件页初始路径、编辑中的配置）以及顶层返回语义。
- 底部导航相关组件放在 `ui/navigation/`，可跨入口复用的纯组件（例如带徽章图标）放在 `ui/components/`。
- 不要把 SMB、传输、持久化或页面内部业务状态搬进导航层；导航层只接线 screen callback 和 route 切换。

示例：

- `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt`
- `app/src/main/java/com/qi/smbshare/ui/navigation/AppBottomNavigationBar.kt`
- `app/src/main/java/com/qi/smbshare/ui/navigation/AppDestination.kt`

### 后台传输服务

长时间运行的前台服务仍放在 `service/` 下，但单个上传/下载任务的具体执行逻辑应拆到 `service/transfer/` 辅助类中，避免 Service 同时承担生命周期、Intent 分发和流复制职责。

#### 1. Scope / Trigger

- Trigger：修改后台上传、下载、进度计算、暂停/取消检查或传输异常分类。
- 目标：`TransferService` 保留前台服务生命周期、Intent action、通知、并发信号量、活动 Job、暂停/取消集合、网络监听和重试调度；执行器只负责单个任务的文件读写。

#### 2. Signatures

- `DownloadExecutor.execute(task: TransferTask, config: SMBConfig)`
- `UploadExecutor.execute(task: TransferTask, config: SMBConfig)`
- `TransferStreamCopier.copy(inputStream, outputStream, task, direction, finalProgress)`
- `TransferControl.waitWhilePaused(taskId)` / `ensureTaskNotCancelled(taskId)`
- `TransferTaskUpdater.updateProgress(...)` / `updateTaskLocalPath(...)`

#### 3. Contracts

- Intent action 和 extras 必须继续由 `TransferService` 暴露，调用方不应直接依赖执行器。
- 上传/下载共用 `TransferStreamCopier` 的 256KB 缓冲区、每秒进度更新、速度计算、暂停/取消检查和协程 active 检查。
- 下载执行器负责 `StorageHelper.createDownloadFileOutputStream()`、Android 10+ `finishDownloadFile()` 和本地路径更新。
- 上传执行器负责普通路径与 `content://` URI 输入流打开，不先复制到缓存目录。
- 执行器依赖通过构造函数传入；不要为了传输拆分迁移导航或改变 Hilt 接线范围。

#### 4. Validation & Error Matrix

- 暂停任务 -> 复制循环阻塞等待，恢复后继续；取消任务 -> 抛出 `CancellationException`，避免误写失败状态。
- 网络/超时读写异常 -> 转为 `TransferException` 并交由 `TransferService` 原有重试策略处理。
- 本地上传文件不存在、无权限或 content URI 无流 -> `FILE_ERROR`。
- 下载实际路径或 pending 收尾路径变化 -> 通过 `updateTaskLocalPath()` 更新任务。

#### 5. Good/Base/Bad Cases

- Good：新增传输行为时扩展执行器或小型 helper，Service 只接线回调与调度。
- Base：只改通知或网络暂停策略时，变更留在 `TransferService`。
- Bad：把 SMB `openFile`、本地输入流打开、流复制循环和进度计算重新写回 `TransferService`。

#### 6. Tests Required

- 单测覆盖共享复制器的进度、速度、最终进度和 256KB 缓冲区契约。
- 单测覆盖上传输入流普通路径、文件不存在和 `content://` 空流。
- 单测覆盖传输读写异常到 `TransferException` 类型的映射。
- 涉及真实 SMB 或 Android 存储的执行器路径优先通过依赖注入 fake 边界测试，不要求 JVM 单测连接真实 SMB。

#### 7. Wrong vs Correct

Wrong：

```kotlin
private suspend fun executeUpload(task: TransferTask, config: SMBConfig) {
    val buffer = ByteArray(256 * 1024)
    // 在 Service 中直接循环读写并计算进度
}
```

Correct：

```kotlin
uploadExecutor.execute(task, config)
```

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
- 不要在常规功能工作中更换依赖注入框架；当前生产代码使用 Hilt，新增共享依赖应沿用现有 module 和构造函数注入模式。
- 不要引入 Android 应用中不存在的服务端/backend 概念。
- 除非任务明确要求，否则不要进行大范围 package 重构。
