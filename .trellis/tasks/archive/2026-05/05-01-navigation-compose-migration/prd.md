# Navigation Compose 全量迁移

## Goal

将当前 `MainActivity.kt` 中基于 `selectedTab + when` 的顶层导航迁移到 Navigation Compose 的 `NavHost`，让 Activity 只保留初始化、主题和顶层入口职责，同时保持现有连接、文件列表、传输管理、设置子页、返回键和底部导航行为不变。

## What I already know

* `androidx.navigation:navigation-compose` 依赖已经存在于 `app/build.gradle.kts`，当前未使用。
* `MainActivity.kt` 目前约 542 行，包含 `AppContent`、底部导航、设置子页状态、编辑连接状态、文件页 ViewModel factory、最后访问恢复和 `BadgedIcon`。
* 文件列表页仍需要运行时参数：`SMBConfig` 与 `initialPath`，当前通过 `FileListViewModelFactory` 和 `viewModel(key = "${config.id}_$initialPath")` 创建。
* 传输管理 tab 的徽章数量来自 `TransferManagerViewModel.state.activeTransferCount`。
* 文件预览覆盖层可见时需要隐藏底部导航，文件列表页在预览状态下使用自己的返回逻辑。
* 设置隐私政策和关于页面已经使用 `PredictiveBackAnimatedContent` 承接返回动画。

## Assumptions

* 本任务不引入 Hilt，不改变现有 ViewModel 构造方式。
* 本任务不改动业务层、传输服务、SMB 连接逻辑或数据模型。
* 顶层导航可以使用不带复杂参数序列化的内部状态保存当前连接配置，因为 `SMBConfig` 当前已经由连接页状态持有并传入文件页。
* `MainActivity` 可以继续负责创建 `ApkInstaller` 和读取主题，导航图迁移到独立 Kotlin 文件。

## Requirements

* 新增独立导航图文件，使用 `NavHost` 管理连接页、文件页、传输管理页、设置主页、隐私政策、关于页、连接编辑页。
* 将底部导航抽成可复用 Compose 组件，继续支持中文环境显示 label，非中文环境只显示图标。
* 将 `BadgedIcon` 迁移到 `ui/components/`，不再留在 `MainActivity.kt`。
* 保留最后访问恢复：若存在上次访问的配置和路径，启动后进入文件页并使用该初始路径。
* 保留连接失败回退：文件页出现“连接失败”错误时回到连接管理页并清空当前连接。
* 保留设置页返回语义：设置主页系统返回到连接管理页；隐私政策/关于页返回设置主页。
* 保留文件页返回语义：文件页顶部返回回连接管理页；目录层级返回继续由 `FileListScreen` 自己处理。
* 文件预览可见时隐藏底部导航；离开文件页、打开编辑页或切换其他 tab 时清除预览可见状态。
* `MainActivity.kt` 变薄，只保留 Activity 生命周期、语言 context、主题读取和 `AppNavGraph` 入口。

## Acceptance Criteria

* [x] `MainActivity.kt` 不再包含 `AppContent`、底部导航实现或 `BadgedIcon`。
* [x] 顶层页面切换由 Navigation Compose `NavHost` 管理。
* [x] 连接、编辑连接、文件列表、传输管理、设置、隐私政策、关于页面均可达。
* [x] 文件页预览可见时底部导航隐藏，关闭/离开后恢复。
* [x] 传输管理 tab 仍显示活动任务徽章和动画。
* [x] 最后访问恢复、连接失败回退、设置子页返回行为保持一致。
* [x] `./gradlew testDebugUnitTest`、`./gradlew :app:compileDebugKotlin`、`./gradlew lintDebug` 通过。

## Definition of Done

* Tests added/updated when behavior moves into testable helpers.
* Lint / typecheck / unit tests green.
* Trellis spec updated if this task establishes a reusable navigation convention.
* Task archived after verification.

## Out of Scope

* 不引入 Hilt 或其他依赖注入框架。
* 不重构 ViewModel、Repository 或 SMB/Transfer 业务逻辑。
* 不新增深链、外部 Intent 路由或多 back stack 底部导航。
* 不调整现有页面视觉风格，除非迁移需要的最小结构变更。

## Technical Notes

* 相关文件：
  * `app/src/main/java/com/qi/smbshare/MainActivity.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/settings/SettingsScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/components/PredictiveBackAnimatedContent.kt`
* 规范：
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/guides/code-reuse-thinking-guide.md`
  * `.trellis/spec/guides/cross-layer-thinking-guide.md`

## Completion Notes

* 新增 `ui/navigation/AppNavGraph.kt`、`AppBottomNavigationBar.kt`、`AppDestination.kt`。
* 新增共享组件 `ui/components/BadgedIcon.kt`。
* `MainActivity.kt` 精简为主题读取和 `AppNavGraph` 入口。
* 已将 Navigation Compose 顶层导航约定写入 `.trellis/spec/backend/directory-structure.md` 和 `.trellis/spec/backend/quality-guidelines.md`。
* 验证通过：
  * `./gradlew testDebugUnitTest`
  * `./gradlew :app:compileDebugKotlin`
  * `./gradlew lintDebug`
  * `git diff --check`
