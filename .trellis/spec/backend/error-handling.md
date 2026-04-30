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

`ErrorHandler.handleException()` 对已知 exception 类型和选定 message 关键词进行分类。`getErrorMessage(context, error)` 返回本地化 string resource。`getErrorMessageFromException(context, exception, fallbackMessageResId)` 对未知错误或通用文件操作失败使用操作级兜底消息。

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

保留现有 SMB 路径行为。常规功能工作中不要引入新的路径抽象。

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

后台传输执行具有 service-local 错误分类。

示例：

- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

当前传输类型：

- `TransferErrorType`
- `TransferException`

service 会为了重试行为分类网络和超时错误，而文件/auth/unknown 错误单独处理。除非任务明确要求抽取，否则将传输重试逻辑保留在 `TransferService` 附近。

---

## 边界

- 当已有 string resource 兜底消息可用时，不要直接向用户暴露原始 exception 文本。
- 不要在 Composable 中处理长时间运行或阻塞式工作。
- 不要添加 HTTP/API 错误响应规则；本应用不包含 HTTP API server。
- 除非任务明确要求，否则不要用其他错误抽象替换当前 `Result<T>` use case 模式。
