# Directory Structure

> This repository is an Android/Kotlin single-module app, not a server backend. In this spec, "backend" means the app's data, domain, service, persistence, and non-UI business logic layers.

---

## Scope

Do not document or create server API routes, controllers, HTTP response formats, or server-side modules for this project. The current codebase contains an Android app under `app/src/main/java/com/qi/smbshare`.

When adding behavior, keep the existing app architecture:

- `MainActivity.kt` owns the top-level Compose container, navigation, theme state wiring, and ViewModel factory setup.
- `data/` owns models, local persistence/SMB primitives, and repositories.
- `domain/usecase/` owns action-oriented use cases that wrap repository or local operations.
- `ui/<feature>/` owns feature screens, ViewModels, state, and intents.
- `service/` owns long-running Android services such as background transfers.
- `util/` owns cross-cutting helpers.

Real examples:

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`
- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`

---

## Layer Layout

### Data models

Put simple data classes and enums under `data/model/`.

Examples:

- `app/src/main/java/com/qi/smbshare/data/model/SMBConfig.kt`
- `app/src/main/java/com/qi/smbshare/data/model/FileItem.kt`
- `app/src/main/java/com/qi/smbshare/data/model/TransferTask.kt`
- `app/src/main/java/com/qi/smbshare/data/model/AppSettings.kt`

### Local persistence and SMB primitives

Put local Android storage, Room database objects, DAOs, entities, and SMB connection primitives under `data/local/`.

Examples:

- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`
- `app/src/main/java/com/qi/smbshare/data/local/SMBConnectionManager.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`

### Repositories

Put concrete persistence, SMB file operation, and service-start coordination work under `data/repository/`.

Examples:

- `app/src/main/java/com/qi/smbshare/data/repository/ConnectionRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/SMBFileRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`

### Domain use cases

Put single-purpose, action-oriented operations under `domain/usecase/`. Existing use cases use names like `<Verb><Thing>UseCase`.

Examples:

- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ListFilesUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/CreateFolderUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/DeleteFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/RenameFileUseCase.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/UploadFileUseCase.kt`

### Feature UI

Put Compose feature code under `ui/<feature>/`. Feature areas use the `XXXScreen`, `XXXViewModel`, `XXXState`, and `XXXIntent` naming pattern.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionState.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

### Shared UI and theme

Put reusable Compose pieces under `ui/components/`, and shared color/typography/theme definitions under `ui/theme/`.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- `app/src/main/java/com/qi/smbshare/ui/components/PermissionRationaleDialog.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Color.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Theme.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Type.kt`

### Cross-cutting utilities

Put shared helpers under `util/`. Search this package before creating a new helper.

Examples:

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`
- `app/src/main/java/com/qi/smbshare/util/StorageHelper.kt`
- `app/src/main/java/com/qi/smbshare/util/PermissionManager.kt`
- `app/src/main/java/com/qi/smbshare/util/ConfigSerializer.kt`
- `app/src/main/java/com/qi/smbshare/util/FileTypeHelper.kt`
- `app/src/main/java/com/qi/smbshare/util/ApkInstaller.kt`
- `app/src/main/java/com/qi/smbshare/util/FToastUtil.kt`

---

## State And Intent Pattern

Feature ViewModels expose a private `MutableStateFlow` and a public read-only `StateFlow` via `asStateFlow()`.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`

Screens collect state with `collectAsStateWithLifecycle()`.

Examples:

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/settings/SettingsScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

Feature actions are represented as sealed intents.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListIntent.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerIntent.kt`

One-off UI effects such as errors, messages, test results, and navigation targets are currently represented in state, consumed in `LaunchedEffect`, and then cleared through intents.

Examples:

- `ConnectionScreen` consumes `state.error`, `state.testResult`, `state.navigateToFileList`, and `state.navigateToEdit`.
- `ConnectionViewModel` handles `ClearError` and `ClearNavigation`.

---

## Boundaries

- Do not put blocking SMB, disk, or network work directly in Composables.
- Do not move existing manual dependency construction to a dependency injection framework as part of normal feature work; this project currently constructs dependencies manually.
- Do not introduce server/backend concepts that are not present in the Android app.
- Do not perform broad package refactors unless a task explicitly asks for them.
