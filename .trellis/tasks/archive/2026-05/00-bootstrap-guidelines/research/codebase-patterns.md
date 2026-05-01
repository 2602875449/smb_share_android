# Codebase Patterns For Bootstrap Guidelines

This repository is an existing Android/Kotlin single-module app. The spec should document only patterns already supported by current examples and should not propose a refactor.

## Existing Convention Sources

- `AGENTS.md`: Kotlin official style, 4-space indentation, immutable-first code, Chinese comments/logs/user-facing text, business logic in ViewModel/data layers instead of Composables, Material theme colors instead of hard-coded UI colors, lifecycle-aware state collection, no large refactors unless requested.
- `README.md`: app module code lives under `app/src/main/java/com/qi/smbshare`; main folders are `data/`, `domain/`, `ui/`, and `util/`.

## Application Structure Observed

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`: top-level app container, navigation, theme state, bottom navigation, and ViewModel factory wiring.
- `data/model/`: simple data classes and enums such as `SMBConfig`, `FileItem`, `AppSettings`, and `TransferTask`.
- `data/local/`: local persistence and SMB connection primitives, including `DataStoreManager`, `SMBConnectionManager`, Room `TransferDatabase`, `TransferTaskDao`, and `TransferTaskEntity`.
- `data/repository/`: repository classes wrapping persistence, SMB file operations, and service starts, including `ConnectionRepository`, `SMBFileRepository`, and `TransferRepository`.
- `domain/usecase/`: action-oriented use cases such as `ConnectSMBUseCase`, `ListFilesUseCase`, `CreateFolderUseCase`, `DeleteFileUseCase`, `RenameFileUseCase`, and `UploadFileUseCase`.
- `ui/<feature>/`: feature screens follow `XXXScreen`, `XXXViewModel`, `XXXState`, and `XXXIntent` naming for connection, file list, settings, and transfer manager areas.
- `ui/components/`: reusable Compose components such as `ErrorSnackbar` and `PermissionRationaleDialog`.
- `ui/theme/`: centralized theme colors, typography, and `SmbShareAndroidTheme`.
- `service/TransferService.kt`: long-running transfer execution, progress updates, retry/cancellation handling, and network callbacks.
- `util/`: cross-cutting helpers such as `ErrorHandler`, `StorageHelper`, `PermissionManager`, `ConfigSerializer`, `FileTypeHelper`, `ApkInstaller`, and `FToastUtil`.

## State And UI Patterns

- ViewModels expose private `MutableStateFlow` and public `StateFlow` via `asStateFlow()`, e.g. `ConnectionViewModel`, `FileListViewModel`, `SettingsViewModel`, and `TransferManagerViewModel`.
- Screens collect state with `collectAsStateWithLifecycle()`, e.g. `ConnectionScreen`, `EditConnectionScreen`, `FileListScreen`, `SettingsScreen`, `TransferManagerScreen`, and `MainActivity`.
- UI actions are modeled as feature-specific sealed intents, e.g. `ConnectionIntent`, `FileListIntent`, and `TransferManagerIntent`.
- One-off UI effects are represented in state (`error`, `message`, `testResult`, navigation targets) and consumed with `LaunchedEffect`, then cleared through intents.
- User-visible strings use string resources, with Chinese resources in `app/src/main/res/values-zh/strings.xml`.
- Empty/error/loading states exist in feature screens and should be preserved when adding UI behavior.

## Theme And Compose Patterns

- Reusable colors are centralized in `ui/theme/Color.kt` as `LightColors` and `DarkColors`, then mapped into Material3 color schemes in `ui/theme/Theme.kt`.
- UI components mainly reference `MaterialTheme.colorScheme` and `MaterialTheme.typography`.
- The only acceptable direct `Color(0x...)` examples are theme-layer color definitions. Component-level hard-coded colors should not be added.
- Dangerous/destructive actions use `MaterialTheme.colorScheme.error` or error containers, e.g. delete actions and `ErrorSnackbar`.
- Common spacing values in screens are 8dp multiples or existing nearby values such as 16dp. Follow the local screen style instead of introducing unrelated dimensions.

## Persistence Patterns

- Room is used only for transfer tasks:
  - `TransferDatabase` has `TransferTaskEntity` as its only entity, version `3`, and `exportSchema = false`.
  - `TransferTaskDao` returns `Flow<List<TransferTaskEntity>>` for observable lists and uses `suspend` functions for one-shot CRUD.
  - `TransferTaskEntity` uses table `transfer_tasks`, a string primary key `id`, status/type stored as enum names, and indexes on `status`, `type`, and `created_at`.
  - Entity/model conversion lives beside the entity as `TransferTask.toEntity()` and `TransferTaskEntity.toModel()`.
  - Current database migration behavior is `.fallbackToDestructiveMigration(true)` with an inline comment that this is a development-stage choice.
- DataStore is used for SMB configs, last access, app settings, theme mode, onboarding, and permission request flags:
  - `DataStoreManager` stores preferences files under `context.noBackupFilesDir/datastore` through `SecurePreferenceDataStoreProvider` so sensitive credentials are excluded from backup.
  - Configs are serialized as JSON arrays with `org.json`.
  - Invalid theme values fall back to `ThemeMode.SYSTEM`; invalid config JSON falls back to an empty list.

## Domain And Repository Patterns

- Use cases wrap repository/local operations and generally return `Result<T>` to ViewModels.
- Use cases log start/success/failure and convert unexpected exceptions into `IOException` for file/network operations.
- Repositories perform concrete persistence, SMB, and service interaction work.
- Blocking SMB or file I/O is kept out of Composables and run from ViewModels/services, typically using `Dispatchers.IO` at call sites.
- SMB paths are normalized to backslashes in repository/service helpers. Preserve current path behavior rather than introducing a new path abstraction during bootstrap.

## Error Handling Patterns

- `util/ErrorHandler.kt` is the central mapper from exceptions to user-friendly `AppError` categories and localized messages.
- Error categories currently include network, authentication, permission, file operation, and unknown errors.
- UI surfaces errors via Snackbar patterns (`ErrorSnackbar`, direct `SnackbarHostState.showSnackbar` in screens) and clears state afterward.
- SMB/repository methods throw `IOException` with Chinese messages for operational failures; use cases return those failures as `Result.failure`.
- Transfer execution uses `TransferException` and retry classification inside `TransferService` for network/time-out retry behavior.

## Logging Patterns

- Logging uses platform `android.util.Log`, usually with a file-level `private const val TAG = "<ClassName>"`.
- Logs are Chinese and commonly include operation start, success, failure, error type, and error message.
- Current code logs SMB server address, share name, usernames, file paths, task IDs, and config IDs; it masks passwords in connection tests.
- Do not introduce a new logging library in the bootstrap specs.
- Do not log raw passwords or full serialized SMB config JSON. When secrets are relevant, mask them like `SMBConnectionManager.testConnection`.

## Quality And Testing Patterns

- Unit tests are under `app/src/test/java/com/qi/smbshare`; instrumentation tests are under `app/src/androidTest/java/com/qi/smbshare`.
- Tests use JUnit4, coroutine `runTest`, MockK, Robolectric, AndroidX test core, and in-memory Room where Android framework behavior is needed.
- Existing tests cover utility helpers, error mapping, config serialization, file type helpers, state defaults, use cases, and transfer repository persistence/service-start behavior.
- For persistence changes, use in-memory Room and clean up database resources.
- For repository/service intent behavior, `TransferRepositoryTest.TestApplication` records started foreground service intents.
- Build verification commands currently supported by the project are `./gradlew test` and `./gradlew assembleDebug`.

## Important Boundaries For The Spec

- Do not document server API routes, HTTP response formats, or backend controllers; this repo does not contain them.
- Do not prescribe a migration framework beyond the current Room/DataStore setup.
- Do not prescribe dependency injection frameworks; the current code manually constructs dependencies and ViewModel factories.
- Do not require a UI library beyond Jetpack Compose Material3 and the dependencies already present.
- Do not require broad architectural cleanup; the user explicitly asked not to refactor.
