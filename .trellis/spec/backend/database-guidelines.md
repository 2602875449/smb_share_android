# Database Guidelines

> Current persistence patterns for this Android app. Document only the existing Room and DataStore usage; do not invent a server database layer.

---

## Persistence Technologies

The project currently uses:

- Room for transfer task persistence.
- Android DataStore Preferences for SMB configs, last access state, app settings, theme mode, onboarding, and permission request flags.
- `org.json` for SMB config serialization in DataStore and transfer task config payloads.

Real examples:

- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`
- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`
- `app/src/main/java/com/qi/smbshare/util/ConfigSerializer.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`
- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`

---

## Room Usage

Room is currently used only for transfer tasks.

`TransferDatabase`:

- declares `TransferTaskEntity` as its only entity.
- uses database version `3`.
- sets `exportSchema = false`.
- exposes `transferTaskDao()`.
- uses a singleton `getInstance(context)` with double-checked locking.
- currently uses `.fallbackToDestructiveMigration(true)` with an inline comment that this is a development-stage choice.

Example:

- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`

Do not document or add a separate migration framework. If a task changes the transfer task schema, update the Room version and keep migration behavior explicit in `TransferDatabase.kt`.

---

## DAO Patterns

DAOs are interfaces under `data/local/`, annotated with `@Dao`.

Current query style:

- observable lists and counts return `Flow`.
- one-shot reads and writes are `suspend` functions.
- SQL is written in `@Query` annotations.
- inserts use `@Insert(onConflict = OnConflictStrategy.REPLACE)` where current behavior expects replacement.
- batch deletes use `WHERE id IN (:taskIds)`.

Example:

- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`

Current DAO methods include:

- `getAllTasks(): Flow<List<TransferTaskEntity>>`
- `getActiveTasks(): Flow<List<TransferTaskEntity>>`
- `getActiveTransferCount(): Flow<Int>`
- `getTaskById(taskId: String): TransferTaskEntity?`
- `insertTask(task: TransferTaskEntity): Long`
- `updateTask(task: TransferTaskEntity)`
- `deleteTasksByIds(taskIds: List<String>)`

---

## Entity And Model Conversion

The transfer table is named `transfer_tasks`. The primary key is the task `id` string.

`TransferTaskEntity` stores transfer type and status as enum names:

- `type = type.name`
- `status = status.name`

Entity/model conversion lives beside the entity:

- `TransferTask.toEntity()`
- `TransferTaskEntity.toModel()`

Example:

- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`

Indexes currently exist on:

- `status`
- `type`
- `created_at`

Column naming is mixed in current code. `created_at` is snake_case because it is indexed and used in SQL ordering; fields such as `completedAt` and `lastUpdatedAt` remain camelCase. Preserve existing names unless a task is explicitly about schema cleanup.

---

## DataStore Usage

`DataStoreManager` owns DataStore preference access.

Current behavior:

- DataStore files are created under `context.noBackupFilesDir/datastore` through `SecurePreferenceDataStoreProvider`.
- Sensitive SMB credentials are therefore excluded from system backup.
- DataStores are cached by name in the provider.
- SMB configs are stored as JSON arrays.
- Invalid config JSON falls back to an empty list.
- Invalid theme values fall back to `ThemeMode.SYSTEM`.
- One-shot reads use `data.first()`.
- Observable settings such as theme mode return `Flow`.

Example:

- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`

Current preference areas:

- `smb_configs`
- `last_access`
- `app_settings`

---

## Repository Boundary

Repositories wrap persistence and service interaction. ViewModels should call use cases or repositories rather than accessing DAOs directly.

Examples:

- `app/src/main/java/com/qi/smbshare/data/repository/ConnectionRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`

`TransferRepository` accepts a `TransferTaskDao` constructor parameter with a default DAO from `TransferDatabase`. Tests use this to inject an in-memory Room DAO.

Example:

- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`

---

## Testing Persistence

For Room-backed behavior, tests use in-memory Room and close the database in teardown.

Example:

- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`

For serialization behavior, tests cover helper functions directly.

Example:

- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`

---

## Boundaries

- Do not add server database conventions; no server database exists in this repo.
- Do not require a migration system that the codebase does not currently use.
- Do not store new sensitive config values in default backed-up SharedPreferences.
- Do not bypass existing model/entity conversion helpers when working with transfer tasks.
