# Quality Guidelines

> Current code quality, UI, and test expectations for this Android/Kotlin app.

---

## General Kotlin Style

Use Kotlin official style with 4-space indentation, camelCase names, and immutable values where practical.

Keep business logic out of Composables. Existing feature logic lives in ViewModels, repositories, use cases, services, and utilities.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
- `app/src/main/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCase.kt`
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt`

Add short Chinese comments only when they explain why a non-obvious choice exists.

Examples:

- `app/src/main/java/com/qi/smbshare/util/ErrorHandler.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`

---

## Compose And State Quality

Current UI state pattern:

- ViewModels expose public `StateFlow` from private `MutableStateFlow`.
- Screens use `collectAsStateWithLifecycle()`.
- User actions flow through feature-specific sealed intents.
- One-off UI effects are stored in state, consumed in `LaunchedEffect`, and cleared through intents.
- Screens preserve empty, loading, and error states when feature behavior changes.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerViewModel.kt`
- `app/src/main/java/com/qi/smbshare/ui/transfer/TransferManagerScreen.kt`

Do not run blocking SMB, network, or disk work directly from Composables. Existing code uses `viewModelScope`, services, repositories, and `Dispatchers.IO` for that work.

---

## Theme And UI Colors

The app uses Jetpack Compose Material3.

Theme colors are centralized in:

- `app/src/main/java/com/qi/smbshare/ui/theme/Color.kt`
- `app/src/main/java/com/qi/smbshare/ui/theme/Theme.kt`

Component UI should mainly reference:

- `MaterialTheme.colorScheme`
- `MaterialTheme.typography`

Examples:

- `app/src/main/java/com/qi/smbshare/MainActivity.kt`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`

Do not add component-level hard-coded `Color(0xFF...)` values. Direct color definitions belong in the theme layer. Existing component use of framework constants such as `Color.Transparent` should be treated as local precedent, not as permission to introduce arbitrary hex colors.

Dangerous actions use `MaterialTheme.colorScheme.error` or error containers.

Examples:

- `app/src/main/java/com/qi/smbshare/ui/components/ErrorSnackbar.kt`
- delete-related UI in `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- delete-related UI in `app/src/main/java/com/qi/smbshare/ui/filelist/FileListScreen.kt`

Spacing in existing screens mostly uses nearby local values such as `8.dp`, `16.dp`, and other established Material sizes. Follow the local screen style instead of introducing unrelated dimensions.

---

## Strings And Localization

User-visible text should use string resources when adding or changing UI text.

Examples:

- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionScreen.kt`
- `app/src/main/java/com/qi/smbshare/MainActivity.kt`

New user-facing Chinese text should be Simplified Chinese. Logs and explanatory code comments in existing Kotlin files are also Simplified Chinese.

---

## Testing

Unit tests live under:

- `app/src/test/java/com/qi/smbshare`

Instrumentation tests live under:

- `app/src/androidTest/java/com/qi/smbshare`

Current test stack includes JUnit4, coroutine `runTest`, MockK, Robolectric, AndroidX test core, and in-memory Room.

Examples:

- `app/src/test/java/com/qi/smbshare/util/ErrorHandlerTest.kt`
- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`
- `app/src/test/java/com/qi/smbshare/util/FileTypeHelperTest.kt`
- `app/src/test/java/com/qi/smbshare/domain/usecase/ConnectSMBUseCaseTest.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`
- `app/src/test/java/com/qi/smbshare/ui/filelist/FileListStateTest.kt`
- `app/src/androidTest/java/com/qi/smbshare/util/PermissionManagerTest.kt`

For Room persistence changes, follow the in-memory database pattern in `TransferRepositoryTest` and close the database in teardown.

For repository/service intent behavior, `TransferRepositoryTest.TestApplication` records started foreground service intents.

---

## Supported Verification Commands

The project currently supports:

```bash
./gradlew test
./gradlew assembleDebug
```

For documentation-only changes, read-only checks such as `rg` are often enough. Do not change app source, Gradle files, or generated files for documentation-only tasks.

---

## Boundaries

- Do not introduce new third-party UI libraries unless a task explicitly asks for them.
- Do not introduce a dependency injection framework as part of routine changes; current code manually wires dependencies and ViewModel factories.
- Do not do broad architectural cleanup while implementing narrow feature or documentation tasks.
- Do not replace existing Compose Material3 patterns with another UI system.
- Do not add unsupported server/backend quality rules; this repository is an Android app.
