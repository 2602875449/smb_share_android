# 质量规范

> 当前 Android/Kotlin 应用的代码质量、UI 和测试期望。

---

## 通用 Kotlin 风格

使用 Kotlin 官方风格：4 空格缩进、camelCase 命名，并在可行时优先使用不可变值。

不要把业务逻辑放进 Composable。现有功能逻辑位于 ViewModel、repository、use case、service 和 utility 中。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

只有在需要解释某个不明显选择为什么存在时，才添加简短中文注释。

示例：

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`

---

## Compose 与 State 质量

当前 UI state 模式：

- ViewModel 从私有 `MutableStateFlow` 暴露公开 `StateFlow`。
- Screen 使用 `collectAsStateWithLifecycle()`。
- 用户动作通过 feature-specific sealed intent 流转。
- 一次性 UI effect 存储在 state 中，在 `LaunchedEffect` 中消费，并通过 intent 清除。
- 功能行为变化时，screen 保留空数据、加载中和错误状态。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

不要直接从 Composable 执行阻塞式 SMB、网络或磁盘工作。现有代码使用 `viewModelScope`、service、repository 和 `Dispatchers.IO` 执行这些工作。

---

## 主题与 UI 颜色

应用使用 Jetpack Compose Material3。

主题颜色集中在：

- `app/src/main/java/com/qi/smbshare/ui/theme/Color.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Theme.kt`

组件 UI 应主要引用：

- `MaterialTheme.colorScheme`
- `MaterialTheme.typography`

示例：

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`

不要添加组件级硬编码 `Color(0xFF...)` 值。直接颜色定义属于主题层。现有组件使用 `Color.Transparent` 等框架常量应视为本地先例，而不是引入任意 hex 颜色的许可。

危险操作使用 `MaterialTheme.colorScheme.error` 或 error container。

示例：

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt` 中的删除相关 UI
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt` 中的删除相关 UI

现有 screen 中的间距大多使用附近已有本地值，例如 `8.dp`、`16.dp` 和其他既有 Material 尺寸。沿用本地 screen 风格，不要引入无关尺寸。

---

## 字符串与本地化

新增或修改 UI 文案时，面向用户的文本应使用 string resource。

示例：

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/MainActivity.kt`

新增面向用户的中文文本应使用简体中文。现有 Kotlin 文件中的日志和解释性代码注释也使用简体中文。

---

## 测试

单元测试位于：

- `app/src/test/java/com/qi/smbshare`

Instrumentation 测试位于：

- `app/src/androidTest/java/com/qi/smbshare`

当前测试栈包括 JUnit4、coroutine `runTest`、MockK、Robolectric、AndroidX test core 和 in-memory Room。

示例：

- `app/src/test/java/com/qi/smbshare/util/ErrorHandlerTest.kt`
- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`
- `app/src/test/java/com/qi/smbshare/util/FileTypeHelperTest.kt`
- `app/src/test/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCaseTest.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`
- `app/src/test/java/com/qi/smbshare/ui/filelist/FileListStateTest.kt`
- `app/src/androidTest/java/com/qi/smbshare/util/PermissionManagerTest.kt`

对于 Room 持久化变更，遵循 `TransferRepositoryTest` 中的 in-memory database 模式，并在 teardown 中关闭数据库。

对于 repository/service intent 行为，`TransferRepositoryTest.TestApplication` 会记录已启动的 foreground service intent。

---

## 支持的验证命令

项目当前支持：

```bash
./gradlew test
./gradlew assembleDebug
```

对于 documentation-only 变更，`rg` 等只读检查通常足够。documentation-only 任务不要修改 app 源码、Gradle 文件或生成文件。

---

## 边界

- 除非任务明确要求，否则不要引入新的第三方 UI 库。
- 不要在常规变更中引入依赖注入框架；当前代码手动连接依赖和 ViewModel factory。
- 实现狭窄功能或 documentation 任务时，不要进行大范围架构清理。
- 不要用其他 UI system 替换现有 Compose Material3 模式。
- 不要添加不受支持的服务端/backend 质量规则；本仓库是 Android 应用。
