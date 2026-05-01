# TransferService 拆分

## Goal

将 `TransferService` 中上传、下载和公共传输执行逻辑拆分到独立、可测试的执行器/辅助类中，降低前台 Service 体量和职责复杂度，同时保持现有 Intent action、通知、并发控制、暂停/恢复/取消、重试和任务状态语义不变。

## What I Already Know

* `TransferService.kt` 当前约 963 行，集中包含前台服务生命周期、通知、网络监听、任务调度、重试、上传、下载、输入流打开、路径规范化、异常转换和进度更新。
* `executeDownload()` 和 `executeUpload()` 都包含流复制循环、`waitWhilePaused()`、`ensureTaskNotCancelled()`、`coroutineContext.ensureActive()`、进度/速度计算、`repository.updateProgress()` 和通知更新。
* 下载逻辑额外负责通过 `StorageHelper.createDownloadFileOutputStream()` 创建本地下载输出流，以及 Android 10+ `finishDownloadFile()` 收尾和任务路径更新。
* 上传逻辑额外负责通过文件路径或 `content://` URI 打开本地输入流。
* 现有 `TransferErrorType` / `TransferException` 已承担错误分类和用户错误文案映射基础。
* 上一个 Slice A 已将缓冲区提升到合理大小，并将进度更新优化为 DAO 局部更新；这次拆分应保留这些优化。
* 当前测试覆盖中已有 `TransferRepositoryTest`、`SMBConnectionManagerTest` 等，但 `TransferService` 执行逻辑仍缺少独立单元测试。

## Assumptions

* 本任务优先做结构拆分和可测试性提升，不改变现有传输功能的用户可见行为。
* 不引入 Hilt；执行器依赖先通过构造函数手动注入，后续 Hilt 任务再接入 DI。
* 不在本任务迁移 Navigation Compose。
* 不要求真实 SMB 服务器集成测试；可通过纯 Kotlin 辅助逻辑测试和可 mock 的执行器边界测试覆盖关键行为。

## Requirements

* 新增传输执行相关类，建议放在 `app/src/main/java/com/qi/smbshare/service/transfer/` 或符合现有结构规范的包中。
* 提取下载逻辑为 `DownloadExecutor` 或等价类，负责单个下载任务的 SMB 文件读取、本地文件写入、最终路径更新和异常转换。
* 提取上传逻辑为 `UploadExecutor` 或等价类，负责单个上传任务的本地输入流打开、SMB 文件写入和异常转换。
* 提取上传/下载共用的流复制、进度计算、速度计算、暂停/取消检查回调和通知进度回调，减少重复代码。
* `TransferService` 保留前台服务生命周期、Intent action 分发、通知创建/更新、并发信号量、活动 Job 映射、暂停/取消集合、网络监听和任务状态调度。
* 保持以下行为不变：
  * `ACTION_START_TRANSFER` / `ACTION_PAUSE_TRANSFER` / `ACTION_RESUME_TRANSFER` / `ACTION_CANCEL_TRANSFER`
  * 最大并发 3 个任务
  * 每秒更新一次进度
  * 网络错误/超时错误重试策略
  * 下载完成后 Android 10+ pending 文件收尾
  * 上传支持普通路径和 `content://` URI
  * pause/resume/cancel 对传输循环和重试等待均生效
* 新增或更新测试，优先覆盖：
  * 共用进度计算/流复制逻辑
  * 异常到 `TransferException` 的分类
  * 上传输入流打开的文件不存在/无权限/content URI 空流场景（可行时）
  * 下载/上传执行器对 `repository.updateProgress()` 的调用语义（可通过 fake 依赖或辅助类单测）

## Acceptance Criteria

* [ ] `TransferService.kt` 行数和职责明显下降，上传/下载具体执行逻辑不再主要堆在 Service 内。
* [ ] `TransferService` 的 public/Intent 行为保持兼容，不需要调用方改动。
* [ ] 上传和下载不再各自维护一份重复的进度/速度更新循环。
* [ ] 保留 256KB 缓冲区和 DAO 局部进度更新路径。
* [ ] 核心拆分点具备单元测试覆盖。
* [ ] 验证通过：`./gradlew testDebugUnitTest`、`./gradlew :app:compileDebugKotlin`、`git diff --check`，可行时运行 `./gradlew lintDebug`。

## Out of Scope

* 不引入 Hilt 或其他 DI 框架。
* 不迁移 Navigation Compose。
* 不重写传输任务数据库模型。
* 不改变通知样式和用户可见文案。
* 不改变 SMBJ 依赖或 SMB 协议行为。

## Technical Notes

* 主要文件：
  * `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
  * 新增 `app/src/main/java/com/qi/smbshare/service/transfer/*`
  * 相关测试位于 `app/src/test/java/com/qi/smbshare/`
* 规范上下文：
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/backend/error-handling.md`
  * `.trellis/spec/backend/logging-guidelines.md`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/backend/database-guidelines.md`
* 设计倾向：先通过构造函数注入 `Context`、`TransferRepository`、状态检查回调和通知回调，避免执行器直接承担 Service 生命周期。

## Definition of Done

* Trellis implement/check 均完成。
* 所有新增注释、日志和文案使用简体中文。
* 测试、编译和 lint/typecheck 通过。
* 如形成新的服务拆分约定，更新 `.trellis/spec/`。
