# Error Handling

> Error handling conventions currently used by the Android app's data, domain, service, and UI layers.

---

## Central Error Mapping

`ErrorHandler` is the central mapper from exceptions to user-friendly app error categories and localized messages.

Example:

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`

Current categories:

- `NetworkError`
- `AuthenticationError`
- `PermissionError`
- `FileOperationError`
- `UnknownError`

`ErrorHandler.handleException()` classifies known exception types and selected message keywords. `getErrorMessage(context, error)` returns localized string resources. `getErrorMessageFromException(context, exception, fallbackMessageResId)` uses operation-level fallback messages for unknown or generic file-operation failures.

Tests:

- `app/src/test/java/com/qi/smbshare/util/ErrorHandlerTest.kt`

---

## Use Case Pattern

Use cases generally return `Result<T>` to ViewModels.

Current style:

- log operation start/success/failure.
- catch `IOException` and return it as `Result.failure`.
- catch unexpected exceptions and wrap file/network failures in `IOException` with a Chinese message.

Examples:

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/CreateFolderUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/DeleteFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/RenameFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/UploadFileUseCase.kt`

---

## Repository And SMB Error Pattern

SMB repositories perform concrete SMB operations and throw `IOException` with Chinese operational messages when work fails.

Examples:

- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`

Current pattern in `SMBFileRepository`:

- validate that a `DiskShare` connection exists.
- normalize SMB paths to backslashes before operations.
- log the failed operation, path, exception type, and message.
- wrap unexpected failures as `IOException("<operation>失败: ${e.message}", e)`.

Preserve the existing SMB path behavior. Do not introduce a new path abstraction during normal feature work.

---

## ViewModel Error Pattern

ViewModels store user-facing errors in state and clear them after the screen consumes the message.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`

Current pattern:

- set loading flags before async work.
- call use cases or repositories from `viewModelScope.launch`.
- run blocking SMB/file work on `Dispatchers.IO` at call sites.
- map exceptions through `ErrorHandler`.
- set `error`, `message`, `testResult`, or navigation state in the feature state.

Screens consume one-off state with `LaunchedEffect` and clear it through intents.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`

---

## UI Error Display

Errors are displayed with Snackbar-style UI and then cleared.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`

User-visible error strings should come from string resources where the current code supports localization.

Examples:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`

---

## Transfer Error Pattern

Background transfer execution has service-local error classification.

Examples:

- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

Current transfer types:

- `TransferErrorType`
- `TransferException`

The service classifies network and timeout errors for retry behavior, while file/auth/unknown errors are handled separately. Keep transfer retry logic near `TransferService` unless a task explicitly asks to extract it.

---

## Boundaries

- Do not expose raw exception text directly to users when an existing string resource fallback is available.
- Do not handle long-running or blocking work in Composables.
- Do not add HTTP/API error response rules; this app does not contain an HTTP API server.
- Do not replace the current `Result<T>` use case pattern with a different error abstraction unless a task explicitly asks for it.
