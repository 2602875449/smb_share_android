# Logging Guidelines

> Current logging conventions for this Android/Kotlin app.

---

## Logging Library

The project uses platform `android.util.Log`.

Do not introduce a new logging library as part of normal feature work.

Examples:

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- `app/src/main/java/com/qi/smbshare/MainActivity.kt`

---

## Tags

Current code commonly uses a class-name tag.

Examples:

- file-level `private const val TAG = "ConnectSMBUseCase"` in `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- file-level `private const val TAG = "SMBFileRepository"` in `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- companion object `private const val TAG = "TransferService"` in `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- instance property `private val TAG = "ConnectionViewModel"` in `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`

Prefer the local style of the file being changed.

---

## Message Language

Existing logs are written in Simplified Chinese.

Examples:

- `ConnectSMBUseCase` logs connection start, success, and failures.
- `SMBFileRepository` logs file listing/opening/upload operations and failure details.
- `TransferService` logs service lifecycle and transfer intent actions.

New logs should follow the same Chinese message style when touching these areas.

---

## Levels

Current usage:

- `Log.d` for operation start, operation success, lifecycle events, parsed intent actions, paths, task IDs, and progress-related diagnostics.
- `Log.w` for recoverable fallback paths, such as trying a second SMB directory-open strategy.
- `Log.e` for operation failures, missing required parameters, parse failures, and caught exceptions.

Examples:

- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

---

## What To Log

Current code logs:

- SMB server address, port, share name, and anonymous flag when testing or connecting.
- SMB paths used by file operations.
- operation start/success/failure for connection, file, and transfer flows.
- exception class and exception message for failed SMB file operations.
- transfer task IDs and service actions.
- config IDs where needed for repository/data operations.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

---

## Sensitive Data

Do not log raw passwords or full serialized SMB config JSON.

`SMBConnectionManager.testConnection` is the reference for masking secrets when logging connection test input.

Example:

- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`

The current code does log some operational identifiers and paths. Keep that behavior consistent, but avoid adding new logs that expose credentials or complete config payloads.

---

## Boundaries

- Do not add structured logging requirements; the project currently uses plain Android log messages.
- Do not add a logging framework.
- Do not add analytics/event tracking rules here; no analytics layer is present in this codebase.
