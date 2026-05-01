# 架构与性能改进

## Goal

基于现有架构审查结果，分阶段改善依赖创建、导航组织、传输服务体量、SMB 连接线程安全、传输性能、数据库进度更新和预览内存占用，提升可维护性、可测试性与局域网传输体验，同时保持现有 SMB 连接、文件列表、传输管理、主题和多语言行为不变。

## What I Already Know

* `FileListViewModel` 当前在构造函数内手动创建 `SMBConnectionManager`、`SMBFileRepository`、`TransferRepository`、`DataStoreManager` 和多个 UseCase，确实不利于 mock 与复用。
* `gradle/libs.versions.toml` 和 `app/build.gradle.kts` 已引入 Navigation Compose，但 `MainActivity.kt` 仍用本地状态和 `when` 承担主要导航。
* `MainActivity.kt` 当前约 541 行，包含导航状态、Tab、返回处理、启动恢复最后访问路径、`BadgedIcon` 等 UI 组件。
* `TransferService.kt` 当前约 963 行，包含前台服务、通知、网络监听、传输调度、上传/下载执行、重试和错误转换。
* `SMBConnectionManager` 的 `connection`、`session`、`diskShare` 字段未同步保护。
* `TransferService` 缓冲区为 8192 字节，视频预览已有 64KB 缓冲思路。
* `TransferRepository.updateProgress()` 当前会 `getTaskById -> toModel -> copy -> toEntity -> updateTask`，进度更新存在不必要的全量读写。
* `FileListViewModel.previewFile()` 对图片预览使用 `input.readBytes()`，大图片存在内存风险。
* 当前单元测试覆盖了部分 repository/usecase/viewmodel/util，但用户指出 `FileListViewModel`、`TransferManagerViewModel`、`SettingsViewModel` 和 `TransferService` 覆盖不足。

## Assumptions

* 这次改进应优先保持用户可见行为不变，不改变现有页面结构、文案、主题配色和传输任务状态语义。
* Hilt 属于新增框架，风险和改动面较大，需要作为独立阶段处理，避免与服务拆分、导航迁移和测试补充全部混在同一个提交中。
* `TransferService` 完整拆分为上传/下载 Executor 会触及核心传输流程，应优先拆出纯 Kotlin 可测逻辑，再用测试保护关键路径。

## Requirements (Evolving)

* 提升 SMB 连接线程安全：对 `connect`、`disconnect`、`getDiskShare`、`isConnected` 等共享状态访问加同步保护，或使用协程互斥方案。
* 提升传输缓冲区到 64KB 至 256KB 区间，优先选择与现有视频预览一致的 64KB 或更适合文件传输的 256KB，并保留清晰注释。
* 为 `TransferTaskDao` 增加局部进度更新 SQL，`TransferRepository.updateProgress()` 不再每秒读写全量 Entity。
* 图片预览改为流式写入缓存文件，再交给 Coil/现有预览 UI 消费，避免大图全量读入内存。
* 将 `MainActivity` 中可独立的导航和通用 UI 组件逐步拆出，例如 `AppNavGraph.kt`、底部导航组件、`BadgedIcon`。
* 评估并引入 Hilt，使 `DataStoreManager`、`TransferRepository`、连接相关仓库/UseCase 由 DI 管理，并逐步移除手写 ViewModelFactory。
* 拆分 `TransferService` 的上传/下载执行逻辑，提取可复用的进度更新、异常转换和重试辅助逻辑。
* 补充核心单元测试，优先覆盖低层可测变更：DAO 局部更新、Repository 进度更新、SMBConnectionManager 同步行为、预览缓存逻辑；后续再补 TransferService 与 ViewModel。

## Acceptance Criteria

* [ ] 改动后现有主要用户流程不变：连接服务器、浏览文件、上传、下载、暂停/恢复/取消、查看传输管理、设置页导航。
* [ ] 新增 UI 代码不硬编码颜色，继续使用 `MaterialTheme.colorScheme` 与现有主题。
* [ ] SMB 连接管理器共享状态访问具备明确线程安全保护。
* [ ] 传输缓冲区不再是 8KB，并有中文注释解释选择原因。
* [ ] 进度更新使用 DAO 局部 SQL 更新，避免每次全量 Entity 读写。
* [ ] 图片预览不再对图片执行 `readBytes()` 全量读取。
* [ ] 若引入 Hilt，Gradle、Application、Activity、ViewModel、Service 相关注解和测试依赖配置完整。
* [ ] 若拆分 `TransferService`，服务仍只负责生命周期/调度/通知，上传下载执行逻辑可独立测试。
* [ ] `fvm` 不适用；Android 验证至少运行 `./gradlew testDebugUnitTest`，如改动编译配置还需运行 `./gradlew :app:compileDebugKotlin`。

## Suggested Implementation Slices

### Slice A: 低风险性能与稳定性

* `SMBConnectionManager` 同步保护。
* `TransferService` 缓冲区提升。
* `TransferTaskDao` 增加局部进度更新，`TransferRepository.updateProgress()` 改用该 DAO 方法。
* 图片预览流式缓存，避免图片 `readBytes()`。
* 补对应单元测试。

### Slice B: MainActivity 导航拆分

* 把 `AppContent` 及相关导航状态提取到 `ui/navigation/AppNavGraph.kt` 或等价结构。
* 将 `BadgedIcon` 移到 `ui/components/`。
* 如直接引入 `NavHost`，确保文件页所需 `SMBConfig`/初始路径能稳定传递，避免复杂对象路由序列化破坏现有流程。

### Slice C: Hilt 接入

* 新增 Hilt Gradle 配置与 `@HiltAndroidApp` Application。
* 为 DataStore、Room、Repository、UseCase、ViewModel 提供注入。
* 逐步移除手写 factory，优先处理无运行时参数的 ViewModel；`FileListViewModel` 的 `SMBConfig`/`initialPath` 需要明确 assisted injection 或导航参数策略。

### Slice D: TransferService 拆分

* 提取 `DownloadExecutor`、`UploadExecutor` 和公共传输辅助逻辑。
* 保持 Service 的 Intent action、通知、并发控制和任务状态语义不变。
* 增加核心执行器单元测试或可替代的集成测试。

## Out of Scope (Initial Proposal)

* 不一次性重写全部架构。
* 不替换 SMBJ、Room、DataStore、Coil、Media3 等现有核心依赖。
* 不改变视觉设计、页面层级和用户可见文案，除非拆分导航时必须补充最小状态提示。
* 不引入新的第三方 UI 库。

## Technical Notes

* 相关文件：
  * `app/src/main/java/com/qi/smbshare/MainActivity.kt`
  * `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
  * `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`
  * `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
  * `gradle/libs.versions.toml`
  * `app/build.gradle.kts`
* 项目规范索引：`.trellis/spec/backend/index.md`。
* 规范提醒：新增注释、日志文案和用户可见文案使用简体中文；UI 颜色必须走主题；业务逻辑不要塞进 Composable。

## Open Question

* 首次实现范围需要确认：建议先做 Slice A 作为低风险起步，还是直接推进包含 Hilt/导航/服务拆分的较大范围？

## Definition of Done

* 需求范围已确认并写入本 PRD。
* `implement.jsonl` / `check.jsonl` 包含相关规范上下文。
* 代码由 Trellis implement/check 流程完成并通过验证。
* 如产生新的架构约定或踩坑，更新 `.trellis/spec/`。
