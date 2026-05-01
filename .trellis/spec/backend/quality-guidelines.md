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

### 手动返回与预测式返回

当页面由本地状态机控制，而不是由 `NavHost` 管理返回栈时，系统返回处理应继续保持原有业务语义，并优先复用共享 Compose helper 承接预测式返回进度。

当前模式：

- Manifest 通过 `android:enableOnBackInvokedCallback="true"` 启用系统返回回调。
- 页面级 Composable 使用 `ui/components/PredictiveBackAnimatedContent` 包裹全屏内容，并把同一个 `onBack` 传入 helper 和顶部栏返回按钮。
- 自定义进度动画保持轻量缩放/淡出，不做大幅横向位移，避免和系统边缘返回指示叠加后造成页面与底部导航错位。
- 文件预览这类覆盖层可见时应隐藏主底部导航，避免返回手势过程中露出下层导航栏。
- 如果返回存在多层业务语义，例如文件列表中“有上级目录则返回上级目录，否则返回连接页”，只让根页面返回连接页参与预测式返回动画；文件夹层级返回继续使用明确的 `BackHandler`。
- 不要为了接入预测式返回重构 SMB、传输或持久化层；返回动画只属于 UI/导航层。

检查点：

- 系统返回、顶部栏返回按钮和旧系统普通返回键触发同一业务路径。
- 手势取消时不触发业务返回。
- 变更后至少运行 `./gradlew test assembleDebug`。

### 场景：图片与文本在线预览

#### 1. Scope / Trigger

- Trigger: 从 SMB 远端文件流读取图片或文本内容，并在文件列表页内覆盖显示预览。
- 目标：网络读取和大小限制由 ViewModel 管理，Composable 只根据 `PreviewState` 展示内容，避免 UI 层直接执行 SMB IO。

#### 2. Signatures

- `FileListIntent.PreviewFile(filePath: String, fileName: String)`
- `FileListIntent.ClosePreview`
- `PreviewState.ImageReady(cacheFile: File)`
- `PreviewState.TextReady(content: String, isTruncated: Boolean = false)`

#### 3. Contracts

- 预览入口只对 `FileTypeHelper.isPreviewable(file.name)` 返回 true 的文件显示。
- 图片文件由 ViewModel 通过 `SMBFileRepository.getFileInputStream(filePath)` 流式写入本地缓存文件，UI 使用图片加载组件从 `ImageReady.cacheFile` 解码。
- 切换到另一个预览文件、关闭预览或 `ViewModel.onCleared()` 时，必须删除已经进入 `ImageReady` 的本地缓存文件；读取中取消或失败时由预览协程删除未移交的临时文件。
- 文本文件最多读取 1 MB；超过限制时 `isTruncated = true`，UI 显示截断提示。
- 预览覆盖层可见时由 `previewFileName != null` 表示，关闭预览必须取消当前预览协程并把状态恢复为 `PreviewState.Idle`。

#### 4. Validation & Error Matrix

- SMB 流打开或读取失败 -> `PreviewState.Error`，显示本地化错误文案。
- 重新打开另一个预览文件 -> 取消旧预览协程，只允许当前文件更新 state。
- 图片缓存文件创建或写入失败 -> 删除未移交临时文件，并显示预览失败错误。
- 文本超过 1 MB -> 显示前 1 MB，并展示截断提示。
- 系统返回 / 顶部返回 -> 关闭预览，不退出文件列表页。

#### 5. Good/Base/Bad Cases

- Good：图片流式写入 cacheDir 后进入 `ImageReady(cacheFile)`，文本最多读取 1 MB；两者都在 `Dispatchers.IO` 中执行，Composable 只消费 state。
- Good：预览页复用 `PredictiveBackAnimatedContent`，并隐藏下层底部导航。
- Base：不支持的文件类型不显示预览入口。
- Bad：在 `FilePreviewScreen` 中直接调用 repository 或打开 SMB 流。
- Bad：图片预览使用 `readBytes()` 一次性读取完整远端文件。
- Bad：文本预览无大小上限，导致大文件一次性读入内存。

#### 6. Tests Required

- `FileTypeHelper` 单测覆盖图片、文本和不可预览类型。
- 状态单测覆盖 `ImageReady(cacheFile)`、`TextReady(isTruncated = true)`、`Error` 和关闭预览后的 `Idle`。
- 缓存工具单测覆盖图片缓存文件名清理和 `deleteReadyImageCache(PreviewState.ImageReady(file))` 删除真实文件。
- ViewModel 行为变化时，补充 fake repository 测试读取成功、读取失败和取消旧预览的 state 转换。

#### 7. Wrong vs Correct

Wrong：

```kotlin
val bytes = repository.getFileInputStream(path).readBytes()
_state.value = _state.value.copy(previewState = PreviewState.ImageReady(bytes))
```

Correct：

```kotlin
repository.getFileInputStream(path).use { input ->
    cacheFile.outputStream().use { output ->
        input.copyTo(output, bufferSize = 64 * 1024)
    }
}
_state.value = _state.value.copy(previewState = PreviewState.ImageReady(cacheFile))
```

---

### 场景：在线视频预览缓存

#### 1. Scope / Trigger

- Trigger: 将 SMB 远端视频写入本地 `cacheDir` 后交给播放器读取。
- 目标：缓存文件生命周期由预览状态持有，避免路径注入、缓存泄漏或关闭预览后继续占用磁盘。

#### 2. Signatures

- `createVideoPreviewCacheFile(cacheDir: File, fileName: String, nowMillis: Long = System.currentTimeMillis()): File`
- `deleteReadyVideoCache(previewState: PreviewState): Boolean`
- `PreviewState.VideoDownloading(progress: Float)`，文件大小未知时 `progress < 0`。
- `PreviewState.VideoReady(cacheFile: File)`，只表示本地缓存文件已完整写入。

#### 3. Contracts

- 远端文件名进入本地缓存路径前必须清理 `/`、`\` 等路径分隔符和不安全字符。
- 下载中临时文件由预览协程 `finally` 删除；已进入 `VideoReady` 的文件由关闭预览或 `ViewModel.onCleared()` 删除。
- 切换到另一个预览文件前，必须先删除上一份 `VideoReady` 缓存。
- UI 只能读取 `VideoReady.cacheFile` 播放，不负责删除缓存文件。

#### 4. Validation & Error Matrix

- 文件大小获取失败 -> 继续下载，`progress = -1` 展示无确定进度。
- SMB 流读取失败 -> 删除未移交临时文件，并显示预览失败错误。
- 预览协程取消 -> 删除未移交临时文件，不触发业务返回或错误覆盖。
- 关闭预览 / ViewModel 清理 -> 删除 `VideoReady.cacheFile`，状态恢复 `Idle`。

#### 5. Good/Base/Bad Cases

- Good：`../movie.mp4` 这样的远端名称创建出的缓存文件仍位于 app cache 目录内。
- Good：打开视频 A 后切换视频 B，视频 A 的本地缓存被删除。
- Base：文件大小未知时显示加载 spinner，完成后进入播放器。
- Bad：直接把原始 SMB 文件名拼进 `File(cacheDir, fileName)`。
- Bad：在 `AndroidView` / `PlayerView` 的 `onDispose` 中删除缓存，导致缓存生命周期散落在 UI 层。

#### 6. Tests Required

- 单测断言缓存文件创建会清理路径分隔符，并且 parent 仍是传入的 cache 目录。
- 单测断言 `deleteReadyVideoCache(PreviewState.VideoReady(file))` 会删除真实文件。
- 单测断言非 `VideoReady` 状态不会误删文件。
- 文件类型单测只把 Media3 可合理处理的本地视频容器列为可预览。

#### 7. Wrong vs Correct

Wrong：

```kotlin
val cacheFile = File(cacheDir, "video_preview_$fileName")
```

Correct：

```kotlin
val cacheFile = createVideoPreviewCacheFile(cacheDir, fileName)
```

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
