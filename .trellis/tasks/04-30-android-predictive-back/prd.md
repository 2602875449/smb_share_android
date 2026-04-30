# Android 预见式返回体验

## Goal

让应用的返回体验更贴近 Android 现代系统手势：在支持的系统版本上接入预见式返回（Predictive Back），减少当前返回动作“生硬”的感觉，同时保持旧系统与现有导航行为稳定。

## What I already know

* 用户反馈当前返回体验比较生硬，希望了解 Android 的“预见式返回”能力是否能加入软件。
* 该能力需要先确认 Android 官方接入要求、项目当前导航/返回处理方式，以及兼容旧版本的实现路径。
* 项目当前 `compileSdk = 36`、`targetSdk = 36`、`minSdk = 28`，并使用 `activity-compose 1.11.0`、`navigation-compose 2.9.6`，基础版本满足接入条件。
* 主界面当前主要由 `MainActivity.kt` 内的 `NavigationTab`、`showEditScreen`、`settingsDestination` 状态机驱动，未使用 `NavHost` 统一承接应用内返回。
* 现有返回处理分布在 `MainActivity.kt`、`ConnectionScreen.kt`、`EditConnectionScreen.kt`、`FileListScreen.kt`、`TransferManagerScreen.kt`。

## Assumptions (temporary)

* MVP 优先接入系统级/导航级返回动画与回调，不额外重做页面转场设计。
* 旧系统版本应保持原行为，不因为新特性引入崩溃或行为变化。

## Open Questions

* 已收敛：MVP 只做系统返回手势体验优化，并保证顶部栏返回按钮与系统返回走同一业务路径。

## Requirements (evolving)

* 在支持预见式返回的 Android 版本上启用相关能力。
* 保持现有导航栈、文件列表、预览、传输等核心功能的返回行为不被破坏。
* 对不支持该特性的系统版本保持兼容。
* 优先改善文件预览、编辑连接页、设置二级页、文件列表返回连接页等用户能明显感知的返回场景。
* 保留双击返回退出、多选模式退出、文件夹上级目录返回等现有业务语义。

## Acceptance Criteria (evolving)

* [x] 官方接入要求和项目最低/目标 SDK 约束已确认。
* [x] 找到项目当前返回处理与导航入口。
* [x] 给出可执行的接入方案，并在用户确认后进入实现。
* [x] 实现后通过构建/测试检查。

## Definition of Done (team quality bar)

* Tests added/updated where appropriate.
* Lint / typecheck / CI green.
* Docs/notes updated if behavior changes.
* Rollout/rollback considered if risky.

## Out of Scope (explicit)

* 不在 MVP 中重做整套页面导航架构。
* 不引入新的第三方 UI 库。

## Technical Notes

* 研究文件：`.trellis/tasks/04-30-android-predictive-back/research/android-predictive-back-official.md`
* 官方资料：
  * https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture
  * https://developer.android.com/develop/ui/compose/system/predictive-back-setup
* 项目入口：
  * `app/src/main/AndroidManifest.xml`
  * `app/src/main/java/com/qi/smbshare/MainActivity.kt`
  * `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/connection/EditConnectionScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/settings/PrivacyPolicyScreen.kt`
  * `app/src/main/java/com/qi/smbshare/ui/settings/AboutScreen.kt`
* 实现记录：
  * 启用 `android:enableOnBackInvokedCallback="true"`。
  * 新增 `PredictiveBackAnimatedContent` 复用手势进度动画。
  * 文件预览、编辑连接页、设置二级页、文件列表根返回连接页已接入。
  * 文件夹上级返回、多选退出、双击退出等原业务返回语义保留。
* 检查记录：
  * `./gradlew lint` 通过。
  * `./gradlew test assembleDebug` 通过。
  * `git diff --check` 通过。
