# 引入 Hilt 依赖注入

## Goal

在 Android 应用中引入 Hilt 作为依赖注入框架，将 `DataStoreManager`、`TransferRepository`、Room 数据库/DAO 等可共享依赖统一为应用级单例，并让 ViewModel、Activity、Service 通过注入获得依赖，方便单元测试时替换或 mock 依赖，减少多处重复创建对象和手写 ViewModel 工厂。

## What I already know

* 用户希望引入 Hilt，核心动机是方便单元测试 mock 依赖、统一管理 `DataStoreManager`、`TransferRepository` 等单例、消除 `FileListViewModelFactory` 这类手写工厂。
* 当前工程是单模块 Android/Kotlin/Compose 应用，使用 Gradle Kotlin DSL、KSP、Room、DataStore、Navigation Compose。
* 当前没有 Hilt 依赖或注解。
* 当前 `DataStoreManager` 在 `MainActivity`、`AppNavGraph`、`FileListViewModel`、`SettingsViewModel`、`ConnectionRepository` 等位置重复创建。
* 当前 `TransferRepository` 在 `FileListViewModel`、`TransferManagerViewModel`、`TransferService`、测试中重复创建。
* `FileListViewModelFactory` 仅用于把 `Application`、`SMBConfig`、`initialPath` 传给 `FileListViewModel`。
* `ConnectionViewModel`、`SettingsViewModel`、`TransferManagerViewModel` 目前继承 `AndroidViewModel`，依赖 `Application` 和内部手动构造的 repository/use case。
* `TransferService` 是前台服务，需要接入 Hilt service 注入时继续保持现有启动和并发传输行为。
* `TransferDatabase` 当前已有手写单例，Hilt 接入后可以由 module 提供数据库和 DAO。

## Assumptions (temporary)

* 本任务采用 Hilt 官方 Android 集成，不引入其他 DI 框架。
* 优先改造生产代码中的共享依赖和 ViewModel 创建方式，不重写现有业务逻辑。
* 保留 `FileListViewModel` 的运行时参数语义：`SMBConfig` 与 `initialPath` 仍由导航状态决定。
* `FileListViewModel` 采用 Hilt Assisted Injection 接收运行时参数，避免把 `SMBConfig` 序列化进导航路由。
* 对于需要 Android `Context` 的依赖，优先注入 `@ApplicationContext Context`，减少对 `AndroidViewModel` 的依赖。
* 单元测试中已经使用 MockK/Robolectric，本任务以“依赖可注入、构造更易 mock”为目标，不强制把全部现有测试迁移到 Hilt instrumentation 测试。

## Open Questions

* 无。

## Requirements (evolving)

* 在版本目录和 Gradle 配置中加入 Hilt 插件、运行时依赖和 KSP 编译器依赖，保持与现有 Kotlin/KSP/AGP 配置兼容。
* 新增 `Application` 类并在 Manifest 中注册，作为 Hilt 根入口。
* `MainActivity` 使用 Hilt Android 入口点，并通过注入或 Hilt ViewModel 获取现有依赖。
* `TransferService` 接入 Hilt 注入，复用应用级 `TransferRepository` 等依赖。
* 新增 Hilt module 提供：
  * `DataStoreManager` 应用级单例。
  * `TransferDatabase` 应用级单例。
  * `TransferTaskDao`。
  * `TransferRepository` 应用级单例。
  * `ConnectionRepository` 及其依赖。
* 将可直接注入的 ViewModel 改为 Hilt 管理，减少内部 `new` repository/data manager/use case 的代码。
* 使用 Hilt Assisted Injection 创建 `FileListViewModel`，移除或废弃 `FileListViewModelFactory` 的手写工厂路径。
* 保持 SMB 连接、文件列表、上传/下载、传输管理、设置主题读取、最后访问路径恢复等现有行为不变。
* 新增或调整测试，使关键 repository/ViewModel 构造在 mock 依赖下可测试。

## Acceptance Criteria (evolving)

* [x] 工程可以成功编译，Hilt/KSP 生成代码无冲突。
* [x] `DataStoreManager`、`TransferRepository`、Room database/DAO 由 Hilt 统一提供，不再在多个生产调用点重复创建。
* [x] `ConnectionViewModel`、`TransferManagerViewModel`、`SettingsViewModel` 通过 Hilt 获取依赖。
* [x] 文件列表页面通过 Hilt Assisted Injection 创建 `FileListViewModel`，不再依赖 `FileListViewModelFactory` 手写工厂。
* [x] `TransferService` 通过注入使用仓库依赖，传输服务行为保持不变。
* [x] 现有单元测试通过，必要时补充 mock 依赖验证。
* [x] `./gradlew testDebugUnitTest` 和 `./gradlew lintDebug` 至少运行到可判定结果。

## Definition of Done (team quality bar)

* Tests added/updated where constructor injection or behavior changed.
* Lint / typecheck / unit tests green, or known unrelated failures clearly记录.
* Docs/spec notes updated if a new project-level DI convention is introduced.
* Rollback considered: Hilt changes are isolated to Gradle config, Application/Manifest, DI modules, and constructor injection paths.

## Out of Scope (explicit)

* 不重构 SMB 协议实现、文件传输执行算法或 UI 视觉结构。
* 不引入除 Hilt 之外的新第三方 UI/架构库。
* 不强制把所有 Robolectric 单元测试迁移成 Hilt instrumentation 测试。
* 不改变用户可见文案或主题颜色体系。

## Technical Notes

* 已检查：
  * `app/build.gradle.kts`
  * `gradle/libs.versions.toml`
  * `app/src/main/AndroidManifest.xml`
  * `app/src/main/java/com/qi/smbshare/MainActivity.kt`
  * `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModelFactory.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/ui/settings/SettingsViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/data/repository/ConnectionRepository.kt`
  * `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
  * `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
* 相关规范索引：`.trellis/spec/backend/index.md`。
* 官方 Hilt 配置和测试建议由 research 子任务记录到 `research/hilt-official-setup.md`。
