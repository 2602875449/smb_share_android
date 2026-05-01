# 架构与依赖注入改进方案

## Goal

根据审查结果，形成一份可执行的架构与依赖注入改进方案，优先解决数据库单例入口冲突、SMB 连接生命周期混乱、Room 迁移风险、导航状态易丢失、错误类型依赖文案、传输服务并发安全和控制指令启动方式风险。

## What I already know

* `AppModule` 已通过 Hilt 在 `SingletonComponent` 中提供 `TransferDatabase` 单例。
* `TransferDatabase` 同时保留 `getInstance()` / `clearInstance()` 自建双重检查单例，并且同样使用 `fallbackToDestructiveMigration(true)`。
* `SMBConnectionManager` 在 `SmbViewModelModule` 中是 `@ViewModelScoped`，但 `DownloadExecutor` / `UploadExecutor` 默认工厂会直接创建新的 `SMBConnectionManager()`。
* `SMBConnectionManager.connect()` 会先断开已有连接，再重新创建 SMBClient connection / session / share。
* `AppNavGraph` 把 `currentConfig` / `editConfig` / `initialPath` / `isFilePreviewVisible` 放在顶层 `remember` 中。
* `AppNavGraph` 存在通过 `state.error.contains("连接失败")` 判断是否返回连接页的逻辑。
* `ErrorHandler` 存在大量基于异常 message/cause message 关键字的错误分类。
* `TransferService` 使用 `mutableMapOf` / `mutableSetOf` 存储活动任务、暂停任务、取消任务和配置，并在 Service、协程和网络回调间共享。
* `waitWhilePaused()` 通过每 100ms 轮询暂停集合实现等待。
* `TransferRepository` 使用 `startForegroundService()` 发送暂停、恢复、取消控制指令。

## Requirements

* 数据库实例必须统一由 Hilt 创建和注入，避免双入口打开同一 Room 数据库。
* Room schema 变更必须通过显式 Migration 管理，避免生产环境清空传输历史。
* SMB 连接必须由 Service 生命周期内的集中组件管理，按 `config.id` 分桶复用，并支持过期回收。
* ViewModel 使用的连接测试/浏览连接与后台传输连接边界需要清晰，避免互相抢占或重复创建。
* UI 导航关键状态必须支持配置变化和进程恢复，至少不能因为旋转丢失当前连接上下文。
* 错误处理需要从字符串判断迁移到领域错误类型，UI 根据类型处理导航和提示。
* 传输服务内共享状态必须线程安全。
* 暂停等待应改为信号驱动，避免固定轮询。
* 传输控制指令应从“每次 startForegroundService”改为更安全的服务内通道、绑定服务或 DB 状态订阅方案。

## Acceptance Criteria

* [ ] 删除或废弃 `TransferDatabase.getInstance()` / `clearInstance()`，现有生产代码不再直接构造数据库。
* [ ] `TransferDatabase` 开启 `exportSchema = true`，schema 输出到 `app/schemas/`。
* [ ] `AppModule` 不再使用 destructive migration，至少提供从当前历史版本到 version 3 的迁移路径。
* [ ] 引入 Service 级 SMB 连接池或等价组件，`DownloadExecutor` / `UploadExecutor` 不再默认自行 `SMBConnectionManager()`。
* [ ] `TransferService` 共享任务状态改为线程安全结构或集中状态机。
* [ ] 暂停/恢复等待由 `StateFlow` / `Channel` / `Mutex+Condition` 等信号机制驱动。
* [ ] `AppNavGraph` 的连接和预览状态移入 Hilt ViewModel + `SavedStateHandle`，或实现可靠 `rememberSaveable`。
* [ ] 错误判断改为领域错误类型，UI 不再通过用户文案 `contains()` 控制导航。
* [ ] 暂停/恢复/取消不再依赖后台重复 `startForegroundService()`。
* [ ] 相关单元测试覆盖数据库迁移、错误映射、传输状态控制和连接池复用/回收。

## Out of Scope

* 本任务不改变 SMB 文件浏览、上传、下载的核心用户流程。
* 本任务不引入新的第三方 UI 库。
* 本任务不重做整体导航架构，只修复状态归属和恢复能力。

## Technical Notes

* 相关文件：
  * `app/src/main/java/com/qi/smbshare/di/AppModule.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
  * `app/src/main/java/com/qi/smbshare/di/SmbViewModelModule.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
  * `app/src/main/java/com/qi/smbshare/service/transfer/DownloadExecutor.kt`
  * `app/src/main/java/com/qi/smbshare/service/transfer/UploadExecutor.kt`
  * `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
  * `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
  * `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt`
  * `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`
