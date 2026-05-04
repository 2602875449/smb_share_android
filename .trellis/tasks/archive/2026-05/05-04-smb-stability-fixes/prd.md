# 修复 SMB 功能与稳定性问题

## Goal

基于用户提供的问题报告, 逐项核对当前代码, 形成可执行的修复计划。目标是先修复仍然存在且影响功能、安全或稳定性的真实缺口, 同时把报告中已经由当前代码满足或描述不匹配的项目标记为复验项, 避免重复实现。

## Scope Decision

用户已确认本轮聚焦“能够确认的 bug”。因此, 共享枚举/listShares 属于当前代码不存在且 SMBJ 0.14.0 API 未确认的新增能力, 本轮只记录技术核对结果, 不作为必须实现项。

## What I already know

* 当前任务由用户提供的 12 个问题和 5 个缺失功能触发。
* 当前仓库没有 `SmbTransportImpl.kt`, 也没有 `listShares()` 实现。现有 SMB 连接入口是 `SMBConnectionManager.connect(config)`, 直接按用户输入的 `shareName` 调用 `Session.connectShare(config.shareName)`。
* 当前仓库使用 SMBJ `0.14.0`。本地依赖源码包未发现报告中提到的 `ServerService` 类, 共享枚举能力需要单独确认 SMBJ 0.14.0 的可用 API 或补充 RPC 实现。
* `openFile()` 当前在 `SMBFileRepository`, `DownloadExecutor`, `UploadExecutor` 中使用的是 `SMB2CreateDisposition` 等真实枚举参数, 未发现报告中的 `.let {}` 伪代码写法。
* 顶层 `AppNavGraph` 已持有 `navController`, 已根据 `currentBackStackEntryAsState()` 计算底部导航选中项, 并把 `Scaffold` 的 `padding` 传给 `NavHost` 外层 `Box`。
* `Connection/EditConnectionScreen` 的密码输入已使用 `PasswordVisualTransformation()`。
* `release` 当前已启用 `isMinifyEnabled = true` 和 `isShrinkResources = true`。
* 文件下载、连接历史、设置页、错误分类、搜索/过滤在当前代码中已有实现痕迹。
* `FileListViewModel.loadFiles()` 每次调用都会新建协程, 当前没有统一取消前一次列表加载的 `Job`, 快速导航仍可能出现旧结果覆盖新状态。
* `SMBConnectionManager` 持有单个 `Connection/Session/DiskShare`, 使用锁保护连接三元组更新, 但文件操作会在锁外使用 `DiskShare`, 与重连/断开并发时仍需进一步收敛生命周期边界。
* 目前多数 SMBJ 调用由调用方包裹在 `withContext(Dispatchers.IO)` 或 service IO scope 中, 但 IO 调度边界分散在 UI/ViewModel/service 层, 数据层自身仍暴露阻塞方法。
* `FileTypeHelper.formatTimestamp()` 和 `formatDate()` 每次调用创建 `SimpleDateFormat`。

## Requirements

### P0: 阻断功能与报告复核

* 共享枚举/listShares 不纳入本轮实现, 仅保留 `research/smbj-share-enumeration.md` 作为后续依据。
* 保持现有 `openFile()` 调用为真实 SMBJ API 参数, 补充测试覆盖关键调用参数和资源关闭, 防止回退到伪实现。
* 复验 `AppNavGraph` 与各子页面 `Scaffold`/edge-to-edge padding, 保证状态栏、导航栏、底部导航不会遮挡内容。

### P1: 安全

* 保留密码输入遮蔽。
* 保留 release 混淆和资源压缩。
* 迁移 `app/build.gradle.kts` 中硬编码签名密码, 改为读取本地未入库的 Gradle properties 或环境变量, 保持本地没有配置时仍可使用 debug 默认签名构建。

### P2: 稳定性

* 将 SMB 阻塞 IO 的调度边界集中到 data/domain/service 层, 避免依赖每个调用方手动切换线程。
* 为文件列表加载引入可取消的 `loadFilesJob` 或请求序号机制, 保证快速进入目录、返回、刷新时只有最新请求能更新列表状态。
* 收敛 `SMBConnectionManager` 的连接生命周期:
  * 连接对象状态更新保持互斥。
  * 文件操作期间避免与 disconnect/reconnect 产生半关闭对象竞争。
  * 断线后重连重试逻辑应集中, 并可测试。

### P3: 体验

* 复验已有下载、连接历史、设置页、错误重试、搜索/过滤功能是否满足用户报告中的预期。
* 对确实存在的体验缺口, 优先补“错误重试入口”和“共享枚举辅助填入”, 不重复实现已存在能力。

### P4: 质量

* 将日期格式化改为 `java.time.DateTimeFormatter` 或缓存格式化器, 避免重复创建 `SimpleDateFormat`。
* 为 SMB 连接生命周期、列表加载取消、文件大小格式化、错误分类补充单元测试。
* 保持 Compose 主题颜色、本地化文案和现有架构模式一致。

## Acceptance Criteria

* [ ] 报告中的每个条目都有明确状态: 已存在且通过复验, 仍需修复, 或当前代码不适用。
* [ ] 仍需修复的 P0/P1/P2 项有对应代码实现和直接验证。
* [ ] 快速连续目录导航不会让旧的列表请求覆盖新的 `currentPath/files` 状态。
* [ ] SMB 连接/重连/断开不会让文件操作读取到半关闭的 `DiskShare`。
* [ ] 发布签名密码不再硬编码在 `app/build.gradle.kts`。
* [ ] 直接相关单元测试通过。
* [ ] `fvm dart analyze` 不适用于本 Android/Kotlin 项目时, 使用 Gradle 的 lint/unit test/compile 验证。
* [ ] `./gradlew :app:compileDebugKotlin`, `./gradlew :app:testDebugUnitTest`, `./gradlew :app:lintDebug` 通过。

## Out of Scope

* 不在本轮引入新的第三方 UI 库。
* 不做大规模 UI 重构或导航架构重写, 除非验证发现当前结构无法满足 padding/路由响应要求。
* 不把 SMB 凭据存储加密作为默认范围。
* 不实现完整文件管理器新功能集, 只补报告中被确认缺失或不稳定的能力。

## Technical Notes

* 关键现状文件:
  * `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
  * `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
  * `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt`
  * `app/src/main/java/com/qi/smbshare/util/FileTypeHelper.kt`
  * `app/build.gradle.kts`
* 相关规范:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/backend/error-handling.md`
  * `.trellis/spec/backend/logging-guidelines.md`
  * `.trellis/spec/backend/quality-guidelines.md`

## Open Questions

* 无。
