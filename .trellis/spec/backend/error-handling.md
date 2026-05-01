# 错误处理

> Android 应用的数据、领域、服务和 UI 层当前使用的错误处理约定。

---

## 中央错误映射

`ErrorHandler` 是将 exception 映射为用户友好的应用错误分类和本地化消息的中央 mapper。

示例：

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`

当前分类：

- `NetworkError`
- `AuthenticationError`
- `PermissionError`
- `FileOperationError`
- `UnknownError`

`ErrorHandler.handleException()` 对已知 exception 类型和选定 message 关键词进行分类，并通过 `AppError.type` 暴露稳定领域错误类型。`getErrorMessage(context, error)` 返回本地化 string resource。`getErrorMessageFromException(context, exception, fallbackMessageResId)` 对未知错误或通用文件操作失败使用操作级兜底消息。

测试：

- `app/src/test/java/com/qi/smbshare/util/ErrorHandlerTest.kt`

---

## 用例模式

用例（use case）通常向 ViewModel 返回 `Result<T>`。

当前风格：

- 记录操作开始、成功和失败日志。
- 捕获 `IOException` 并作为 `Result.failure` 返回。
- 捕获非预期 exception，并将文件/网络失败包装为带中文消息的 `IOException`。

示例：

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/CreateFolderUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/DeleteFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/RenameFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/UploadFileUseCase.kt`

---

## Repository 与 SMB 错误模式

SMB repository 执行具体 SMB 操作，并在工作失败时抛出带中文操作消息的 `IOException`。

示例：

- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`

`SMBFileRepository` 中的当前模式：

- 校验 `DiskShare` 连接存在。
- 操作前将 SMB 路径规范化为反斜杠。
- 记录失败操作、路径、exception 类型和 message。
- 将非预期失败包装为 `IOException("<operation>失败: ${e.message}", e)`。
- 打开 SMBJ `File` 后必须确保外层 `File` 句柄在成功、失败和取消路径都会关闭；只关闭 `file.inputStream` / `file.outputStream` 不应被视为足够。
- 只读预览或下载类操作如果可能紧接删除/重命名，应在 `openFile` 的 share access 中包含 `FILE_SHARE_DELETE`，避免短暂读取窗口导致服务端返回 `STATUS_SHARING_VIOLATION`。

保留现有 SMB 路径行为。常规功能工作中不要引入新的路径抽象。

### 场景：SMB 预览流句柄释放

#### 1. Scope / Trigger

- Trigger: SMBJ 文件读取属于远端文件句柄集成；预览、下载后可能立即删除或重命名同一路径。

#### 2. Signatures

- `SMBFileRepository.getFileInputStream(filePath: String): InputStream`
- 返回的 `InputStream.close()` 必须同时关闭底层输入流和 SMBJ `File` 句柄。

#### 3. Contracts

- 路径进入 `openFile` 前继续使用 `normalizePath()`。
- 只读打开参数必须包含 `AccessMask.GENERIC_READ`。
- share access 至少包含 `FILE_SHARE_READ`、`FILE_SHARE_WRITE`、`FILE_SHARE_DELETE`。

#### 4. Validation & Error Matrix

- `DiskShare` 不存在 -> 抛出 `IOException("未连接到SMB服务器")`。
- `openFile` 失败 -> 包装为 `IOException("打开文件失败: ...", cause)`。
- `file.inputStream` 创建失败 -> 关闭 SMBJ `File` 后再包装为 `IOException`。
- 返回流关闭失败 -> 尽量关闭 SMBJ `File`，并保留原始关闭异常。

#### 5. Good/Base/Bad Cases

- Good: 预览图片 -> 返回 -> 删除同一文件，不应出现 `STATUS_SHARING_VIOLATION`。
- Base: 读取普通文本/图片后关闭流，远端 `File.close()` 调用一次。
- Bad: 只返回 `file.inputStream`，调用方 `use {}` 后远端句柄仍可能占用。

#### 6. Tests Required

- 单测断言返回流 `close()` 会调用 SMBJ `File.close()`。
- 单测断言重复关闭不会重复关闭外层句柄。
- 单测断言 `inputStream` 创建失败时仍关闭外层句柄。
- 单测断言 `openFile` share access 包含 `FILE_SHARE_DELETE`。

#### 7. Wrong vs Correct

Wrong：

```kotlin
val file = diskShare.openFile(path, setOf(AccessMask.GENERIC_READ), null, shareAccess, null, null)
return file.inputStream
```

Correct：

```kotlin
val file = diskShare.openFile(
    path,
    setOf(AccessMask.GENERIC_READ),
    null,
    setOf(
        SMB2ShareAccess.FILE_SHARE_READ,
        SMB2ShareAccess.FILE_SHARE_WRITE,
        SMB2ShareAccess.FILE_SHARE_DELETE
    ),
    null,
    null
)

return object : FilterInputStream(file.inputStream) {
    override fun close() {
        try {
            super.close()
        } finally {
            file.close()
        }
    }
}
```

---

## ViewModel 错误模式

ViewModel 将面向用户的错误保存在 state 中，并在 screen 消费消息后清除。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`

当前模式：

- 在异步工作前设置 loading 标记。
- 从 `viewModelScope.launch` 中调用 use case 或 repository。
- 在调用点使用 `Dispatchers.IO` 执行阻塞式 SMB/file 工作。
- 通过 `ErrorHandler` 映射 exception。
- 在功能 state 中设置 `error`、`message`、`testResult` 或导航 state。

Screen 使用 `LaunchedEffect` 消费一次性 state，并通过 intent 清除。

示例：

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`

---

## UI 错误展示

错误通过 Snackbar 风格 UI 展示，然后清除。

示例：

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`

当前代码支持本地化的位置，面向用户的错误字符串应来自 string resource。

示例：

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`

---

## 传输错误模式

后台传输执行具有传输模块内的错误分类。

示例：

- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- `app/src/main/java/com/qi/smbshare/service/transfer/TransferErrors.kt`

当前传输类型：

- `TransferErrorType`
- `TransferException`

执行器负责把底层 IO/权限异常转换为 `TransferException`，`TransferService` 负责根据错误类型执行重试和最终任务状态更新。网络和超时错误可重试；文件、认证和未知错误不重试。

上传输入流边界必须保留这些分类：

- 本地文件不存在、不可读或 `content://` 无法打开输入流 -> `FILE_ERROR`
- `SecurityException`（例如系统文件选择器 URI 权限失效）-> `FILE_ERROR`
- 网络连接中断、UnknownHost 或连接类 IO 错误 -> `NETWORK_ERROR`
- Socket timeout -> `TIMEOUT_ERROR`

---

## 边界

- 当已有 string resource 兜底消息可用时，不要直接向用户暴露原始 exception 文本。
- 不要在 Composable 中处理长时间运行或阻塞式工作。
- UI 导航决策不得依赖用户可见错误文案或 `contains()`；需要跳转时由 ViewModel state 暴露领域错误类型或显式导航事件。
- 不要添加 HTTP/API 错误响应规则；本应用不包含 HTTP API server。
- 除非任务明确要求，否则不要用其他错误抽象替换当前 `Result<T>` use case 模式。

### 场景：错误类型驱动导航

#### 1. Scope / Trigger

- Trigger：UI 需要根据错误决定是否返回连接页、显示权限入口或阻止重试。

#### 2. Signatures

- `ErrorHandler.AppErrorType`
- `ErrorHandler.AppError.type`
- Feature state 中的领域字段，例如 `FileListState.connectionErrorType`

#### 3. Contracts

- 用户可见文案只用于展示，不作为业务判断输入。
- ViewModel 在捕获异常时同时写入本地化错误文案和领域错误类型。
- Screen 只消费领域字段执行导航，并在清除错误时一并清除领域字段。

#### 4. Validation & Error Matrix

- 文案翻译变化 -> 不应改变导航行为。
- 未知异常 -> 可展示兜底文案，但不要误判为连接配置失效。
- 连接/认证类异常 -> 可返回连接页或引导用户修正配置。

#### 5. Good/Base/Bad Cases

- Good：`LaunchedEffect(state.connectionErrorType)` 判断是否返回连接页。
- Base：普通文件操作失败只展示 Snackbar。
- Bad：`state.error?.contains("连接失败") == true` 后导航。

#### 6. Tests Required

- `ErrorHandlerTest` 覆盖异常到 `AppErrorType` 的映射。
- 修改 feature state 时补充 state 或 ViewModel 测试，断言清除错误会清除类型字段。

#### 7. Wrong vs Correct

Wrong：

```kotlin
if (state.error?.contains("连接失败") == true) {
    navController.navigate("connection")
}
```

Correct：

```kotlin
if (state.connectionErrorType != null) {
    navController.navigate("connection")
}
```
