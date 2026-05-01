# Research: Hilt official setup for Kotlin Android Compose app

- Query: official Android/Dagger Hilt setup for Kotlin Android Compose with Gradle Kotlin DSL, KSP, AGP 8.13.1, Kotlin 2.2.21, and existing Room KSP
- Scope: mixed
- Date: 2026-05-01

## Findings

### Files Found

- `gradle/libs.versions.toml` - central version catalog; AGP is `8.13.1`, Kotlin is `2.2.21`, Room is `2.8.3`, KSP plugin is already declared, and no Hilt entries exist yet.
- `build.gradle.kts` - root plugins currently declare Android application, Kotlin Android, and Compose plugin aliases only.
- `app/build.gradle.kts` - app module already applies `com.google.devtools.ksp` and uses `ksp(libs.room.compiler)` for Room.
- `app/src/main/AndroidManifest.xml` - `<application>` has no `android:name`, so Hilt currently has no app-level `Application` entry point.
- `app/src/main/java/com/qi/smbshare/MainActivity.kt` - `ComponentActivity` Compose host; currently manually creates `DataStoreManager` and owns an `ApkInstaller`.
- `app/src/main/java/com/qi/smbshare/service/TransferService.kt` - Android `Service` currently constructs repository and transfer executors in `onCreate`.
- `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt` - Navigation Compose graph currently obtains ViewModels with `viewModel()` and uses a custom `FileListViewModelFactory` for runtime `SMBConfig` and `initialPath`.
- `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModelFactory.kt` - manual `ViewModelProvider.Factory` exists only to pass runtime navigation state into `FileListViewModel`.

### Code Patterns

- Version catalog currently has AGP/Kotlin/KSP/Room versions but no Hilt versions or aliases: `gradle/libs.versions.toml:2`, `gradle/libs.versions.toml:3`, `gradle/libs.versions.toml:16`, `gradle/libs.versions.toml:18`, `gradle/libs.versions.toml:79`.
- Root Gradle plugin aliases need a Hilt plugin entry beside the existing Android/Kotlin aliases: `build.gradle.kts:2`.
- App module already applies KSP and Room compiler through KSP, so Hilt should add more `ksp*` dependencies rather than introduce kapt: `app/build.gradle.kts:1`, `app/build.gradle.kts:5`, `app/build.gradle.kts:125`.
- Manifest must register a new Hilt `Application` class because the current `<application>` element lacks `android:name`: `app/src/main/AndroidManifest.xml:21`.
- `MainActivity` extends `ComponentActivity`, which is a supported base for `@AndroidEntryPoint`; it currently has no annotation: `app/src/main/java/com/qi/smbshare/MainActivity.kt:22`.
- `TransferService` is a supported Hilt entry point if dependencies are injected into fields; current fields are initialized manually in `onCreate`: `app/src/main/java/com/qi/smbshare/service/TransferService.kt:52`, `app/src/main/java/com/qi/smbshare/service/TransferService.kt:78`, `app/src/main/java/com/qi/smbshare/service/TransferService.kt:102`.
- Top-level ViewModels currently use Compose `viewModel()` without Hilt-specific retrieval: `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt:69`.
- File list runtime arguments are not route strings; they are Compose navigation state objects passed through a custom factory: `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt:63`, `app/src/main/java/com/qi/smbshare/ui/navigation/AppNavGraph.kt:208`, `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModelFactory.kt:11`.
- Several ViewModels still create repositories/use cases internally; Hilt migration should move these to constructor injection instead of leaving dependency creation inside ViewModels: `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:32`, `app/src/main/java/com/qi/smbshare/ui/connection/ConnectionViewModel.kt:43`, `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt:33`, `app/src/main/java/com/qi/smbshare/ui/filelist/FileListViewModel.kt:38`.
- `TransferRepository` already supports constructor-injected DAO override for tests; Hilt module can provide `TransferDatabase`/`TransferTaskDao` while preserving this test seam: `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt:22`.

### Official Setup Implications

- Add Hilt Gradle plugin through the version catalog with a version compatible with the existing AGP. Review verification found `com.google.dagger.hilt.android` `2.59.2` rejects AGP `8.13.1` with "only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher"; keep AGP at `8.13.1` for this task and use Hilt `2.57.2`.
- In the root `build.gradle.kts`, add `alias(libs.plugins.hilt.android) apply false`. In `app/build.gradle.kts`, apply `alias(libs.plugins.hilt.android)` alongside the existing Android/Kotlin/KSP plugins.
- Add runtime/compiler dependencies through KSP, not kapt:
  - `implementation(libs.hilt.android)`
  - `ksp(libs.hilt.compiler)`
  - `testImplementation(libs.hilt.android.testing)`
  - `kspTest(libs.hilt.compiler)`
  - `androidTestImplementation(libs.hilt.android.testing)`
  - `kspAndroidTest(libs.hilt.compiler)`
- Use one Hilt compiler artifact consistently. Dagger's KSP setup uses `com.google.dagger:hilt-compiler`; Android Developers Kotlin snippet still shows `com.google.dagger:hilt-android-compiler`. The implementation should use `hilt-compiler` consistently for runtime, unit-test, and android-test KSP.
- For Compose ViewModel retrieval, add AndroidX Hilt `1.3.0`. The stable AndroidX Hilt release is `1.3.0`; release notes say Compose `hiltViewModel()` moved to `androidx.hilt:hilt-lifecycle-viewmodel-compose` and package `androidx.hilt.lifecycle.viewmodel.compose`. Android's Compose guide still lists `androidx.hilt:hilt-navigation-compose:1.3.0` for Navigation Compose. Prefer the non-deprecated `hilt-lifecycle-viewmodel-compose` import, and add `hilt-navigation-compose` only if the implementation intentionally uses the legacy navigation-compose helper package.
- Create an app class, for example `SmbShareApplication : Application`, annotate it with `@HiltAndroidApp`, and set `android:name=".SmbShareApplication"` on `<application>`.
- Annotate `MainActivity` with `@AndroidEntryPoint`. Hilt supports `ComponentActivity`, so this activity is compatible without changing its base type.
- Annotate `TransferService` with `@AndroidEntryPoint` if it receives injected fields. Hilt does not constructor-inject framework-created services; use field injection such as `@Inject lateinit var repository: TransferRepository`. Injected fields cannot be private.
- Convert ViewModels to `@HiltViewModel` with `@Inject constructor(...)`. If `Application` is still required, Hilt can provide `Application`; otherwise prefer normal `ViewModel` plus `@ApplicationContext Context` for narrower coupling.
- Prefer constructor injection for app-owned repositories, use cases, managers, executor helpers, and pure collaborators. Use `@Module` + `@InstallIn(SingletonComponent::class)` for things Hilt cannot construct directly, such as Room database/DAO, interfaces, Android service system objects, or factory functions.
- For Room, provide `TransferDatabase` as `@Singleton` and provide `TransferTaskDao` from it. This replaces the manual singleton path while preserving the current Room KSP compiler.
- For runtime navigation values:
  - If an argument is primitive/String/Parcelable and belongs in a route, use Navigation arguments and inject `SavedStateHandle` into a `@HiltViewModel`.
  - For current `SMBConfig` object state and `initialPath` passed outside routes, Hilt assisted ViewModel injection is the official fit if the value must remain runtime-only. Use `@HiltViewModel(assistedFactory = ...)`, `@AssistedInject`, `@Assisted`, and a `@AssistedFactory`, then create it from Compose with the `hiltViewModel` overload that accepts a creation callback.
  - Assisted parameters are not persisted after process death; use `SavedStateHandle` or another persisted source if restoration matters.

### Testing / Mocking Implications

- Pure JVM unit tests do not need Hilt. Official Android guidance says constructor-injected classes can be instantiated directly with fake or mock dependencies, which matches the repository and use case test style already in this project.
- Hilt Android/Robolectric/UI tests require `hilt-android-testing`, `@HiltAndroidTest`, `HiltAndroidRule`, and calling `hiltRule.inject()` before using injected fields.
- Instrumented tests that use Hilt need a Hilt test application. The current test runner is `androidx.test.runner.AndroidJUnitRunner`; add a custom runner that returns `HiltTestApplication`, then set `testInstrumentationRunner` to that class.
- Robolectric tests can use `robolectric.properties` with `application = dagger.hilt.android.testing.HiltTestApplication` or per-test `@Config(application = HiltTestApplication::class)`.
- Replace bindings across a source set with `@TestInstallIn`; replace per test with `@UninstallModules` plus a nested `@InstallIn` module or `@BindValue`. Prefer `@TestInstallIn` where possible because per-test custom components can slow builds.
- With KSP, keep all processors whose generated types Hilt/Dagger must inspect on KSP. The project already uses Room via KSP and does not currently use kapt, so adding Hilt KSP should not create a mixed KAPT/KSP generated-type visibility issue.

### External References

- Android Developers: Dependency injection with Hilt - Gradle plugin, `@HiltAndroidApp`, supported Android classes, components/scopes: https://developer.android.com/training/dependency-injection/hilt-android
- Dagger Hilt: Gradle Build Setup - current `2.59.2` dependencies, KSP configurations, plugin DSL, aggregating task notes: https://dagger.dev/hilt/gradle-setup
- Dagger Hilt: View Models - `@HiltViewModel`, `SavedStateHandle`, assisted ViewModel injection and persistence caveat: https://dagger.dev/hilt/view-model.html
- Android Developers: Compose and other libraries - Hilt with Compose and Navigation Compose: https://developer.android.com/develop/ui/compose/libraries
- AndroidX Hilt release notes - stable `1.3.0`, new `hilt-lifecycle-viewmodel-compose` artifact/package, assisted `hiltViewModel()` support history: https://developer.android.com/jetpack/androidx/releases/hilt
- Android Developers: Hilt testing guide - unit tests, `HiltAndroidRule`, `HiltTestApplication`, replacement APIs: https://developer.android.com/training/dependency-injection/hilt-testing
- Dagger KSP guide - Dagger KSP requirements and KSP/Javac generated-type visibility caveat: https://dagger.dev/dev-guide/ksp.html

### Related Specs

- `.trellis/spec/backend/index.md` - spec entry point and pre-development checklist.
- `.trellis/spec/backend/directory-structure.md` - preserve current Android module boundaries: `MainActivity`, `data/`, `domain/usecase/`, `ui/<feature>/`, `service/`, `util/`.
- `.trellis/spec/backend/database-guidelines.md` - Room is currently limited to transfer tasks; preserve `TransferDatabase`/DAO contracts and tests.
- `.trellis/spec/backend/quality-guidelines.md` - ViewModel/state ownership, Compose state collection, testing expectations, and thin `MainActivity` expectations.

## Caveats / Not Found

- Did not find an existing `Application` subclass in the app; Hilt setup must add one.
- Did not find existing Hilt/Dagger dependencies or annotations.
- The official Android Developers Hilt dependency snippets are slightly inconsistent: one Kotlin snippet uses `hilt-android-compiler`, while the Dagger Hilt Gradle setup uses `hilt-compiler`. The implementation should choose one compiler artifact consistently, with `hilt-compiler` matching Dagger KSP docs.
- Hilt `2.59.2` is not compatible with this repository's AGP `8.13.1`; do not upgrade `gradle/wrapper/gradle-wrapper.properties`, `gradle.properties`, or AGP just to consume that Hilt version in this task.
- Dagger documents KSP support as alpha even though it is the official documented path. Existing Room KSP makes KSP the practical choice here; do not introduce kapt unless a blocker appears.
- No implementation or spec files were modified during this research.
