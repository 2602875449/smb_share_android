# 数据库规范

> 当前 Android 应用的持久化模式。只记录已有 Room 和 DataStore 用法；不要虚构服务端数据库层。

---

## 持久化技术

项目当前使用：

- Room 用于传输任务持久化。
- Android DataStore Preferences 用于 SMB 配置、最后访问状态、应用设置、主题模式、onboarding 和权限请求标记。
- `org.json` 用于 DataStore 中的 SMB 配置序列化，以及传输任务配置载荷。

真实示例：

- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`
- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`
- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`
- `app/src/main/java/com/qi/smbshare/util/ConfigSerializer.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`
- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`

---

## Room 用法

Room 当前只用于传输任务。

`TransferDatabase`:

- 声明 `TransferTaskEntity` 作为唯一 entity。
- 使用数据库版本 `3`。
- 设置 `exportSchema = true`，schema 输出到 `app/schemas/`。
- 暴露 `transferTaskDao()`。
- 生产路径由 Hilt `AppModule` 以 `@Singleton` 提供数据库实例，再从数据库提供 `TransferTaskDao`。
- 不保留 `getInstance(context)` / `clearInstance()` 自建单例入口；生产代码只通过 Hilt 注入数据库或 DAO。
- 生产构建不使用 `.fallbackToDestructiveMigration(true)`；历史版本到当前版本通过 `TransferDatabase.MIGRATIONS` 显式迁移。
- 当前仓库可审计历史中，Room schema 导出前的 `TransferDatabase` 已经声明为 version 3，且没有提交过 v1/v2 schema 文件；现有 1->2、2->3 迁移只支持“旧版本号但 `transfer_tasks` 表结构等价于当前 schema”的历史安装，任何真实结构差异应由 Room schema 校验失败暴露，不能用空迁移或 destructive migration 静默吞掉。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`

不要添加单独的 migration 框架。如果任务修改传输任务 schema，更新 Room 版本、补充 `Migration`、重新生成 `app/schemas/`，并为迁移保留历史数据添加单测。

### 场景：TransferDatabase 迁移与 schema 导出

#### 1. Scope / Trigger

- Trigger：修改 `TransferTaskEntity`、Room 版本、DAO 依赖注入或数据库构建配置。

#### 2. Signatures

- `@Database(entities = [TransferTaskEntity::class], version = <n>, exportSchema = true)`
- `TransferDatabase.MIGRATIONS: Array<Migration>`
- `Room.databaseBuilder(...).addMigrations(*TransferDatabase.MIGRATIONS)`
- Gradle KSP 参数：`room.schemaLocation = "$projectDir/schemas"`

#### 3. Contracts

- `TransferDatabase` 只暴露 DAO，不提供自建全局单例。
- Hilt `AppModule` 是生产数据库实例唯一创建入口。
- schema JSON 必须提交到 `app/schemas/com.qi.smbshare.data.local.TransferDatabase/`。

#### 4. Validation & Error Matrix

- 缺少迁移路径 -> Room 打开旧库失败，禁止用 destructive migration 兜底。
- schema 文件未更新 -> PR 中无法审查表结构变化。

#### 5. Good/Base/Bad Cases

- Good：新增列时增加版本、Migration、schema JSON 和迁移测试。
- Base：只改 DAO 查询且不改 schema 时不升级版本。
- Bad：为了通过本地测试添加 `fallbackToDestructiveMigration(true)`。

#### 6. Tests Required

- Room 迁移单测至少断言旧版本任务升级后仍可按 ID 读取。
- 修改 Hilt/DAO 接线后运行 `./gradlew :app:compileDebugKotlin`。
- 修改持久化行为后运行 `./gradlew :app:testDebugUnitTest`。

#### 7. Wrong vs Correct

Wrong：

```kotlin
Room.databaseBuilder(context, TransferDatabase::class.java, "transfer_database")
    .fallbackToDestructiveMigration(true)
    .build()
```

Correct：

```kotlin
Room.databaseBuilder(context, TransferDatabase::class.java, "transfer_database")
    .addMigrations(*TransferDatabase.MIGRATIONS)
    .build()
```

---

## DAO 模式

DAO 是 `data/local/` 下带 `@Dao` 注解的 interface。

当前查询风格：

- 可观察列表和计数返回 `Flow`。
- 一次性读写是 `suspend` 函数。
- SQL 写在 `@Query` 注解中。
- 当前行为期望替换时，insert 使用 `@Insert(onConflict = OnConflictStrategy.REPLACE)`。
- 批量删除使用 `WHERE id IN (:taskIds)`。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskDao.kt`

当前 DAO 方法包括：

- `getAllTasks(): Flow<List<TransferTaskEntity>>`
- `getActiveTasks(): Flow<List<TransferTaskEntity>>`
- `getActiveTransferCount(): Flow<Int>`
- `getTaskById(taskId: String): TransferTaskEntity?`
- `insertTask(task: TransferTaskEntity): Long`
- `updateTask(task: TransferTaskEntity)`
- `updateProgress(taskId, progress, transferredBytes, speed, lastUpdatedAt): Int` 使用局部 `UPDATE` 只写进度相关列，预计剩余时间在 SQL 中基于 `fileSize` 计算，避免高频进度更新全量读取和回写 entity。
- `deleteTasksByIds(taskIds: List<String>)`

---

## Entity 与 Model 转换

传输表名为 `transfer_tasks`。主键是任务 `id` 字符串。

`TransferTaskEntity` 将传输类型和状态保存为 enum name：

- `type = type.name`
- `status = status.name`

Entity/model 转换与 entity 放在一起：

- `TransferTask.toEntity()`
- `TransferTaskEntity.toModel()`

示例：

- `app/src/main/java/com/qi/smbshare/data/local/TransferTaskEntity.kt`

当前索引包括：

- `status`
- `type`
- `created_at`

当前代码中的列命名存在混合风格。`created_at` 是 snake_case，因为它被索引并用于 SQL 排序；`completedAt` 和 `lastUpdatedAt` 等字段仍为 camelCase。除非任务明确涉及 schema 清理，否则保留现有名称。

---

## DataStore 用法

`DataStoreManager` 负责 DataStore preference 访问。

当前行为：

- DataStore 文件通过 `SecurePreferenceDataStoreProvider` 创建在 `context.noBackupFilesDir/datastore` 下。
- 因此，敏感 SMB 凭据会被排除在系统备份之外。
- DataStore 在 provider 中按名称缓存。
- SMB 配置保存为 JSON array。
- 无效配置 JSON 回退为空列表。
- 无效主题值回退为 `ThemeMode.SYSTEM`。
- 一次性读取使用 `data.first()`。
- 主题模式等可观察设置返回 `Flow`。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/DataStoreManager.kt`

当前 preference 区域：

- `smb_configs`
- `last_access`
- `app_settings`

---

## Repository 边界

Repository 包装持久化和 service 交互。ViewModel 应调用 use case 或 repository，而不是直接访问 DAO。

示例：

- `app/src/main/java/com/qi/smbshare/data/repository/ConnectionRepository.kt`
- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`

`TransferRepository` 接受 `@ApplicationContext Context` 和 `TransferTaskDao` 构造参数。生产路径由 Hilt 注入，测试使用这一点注入 in-memory Room DAO。

示例：

- `app/src/main/java/com/qi/smbshare/data/repository/TransferRepository.kt`
- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`

---

## 持久化测试

对于 Room 支持的行为，测试使用 in-memory Room，并在 teardown 中关闭数据库。

示例：

- `app/src/test/java/com/qi/smbshare/data/repository/TransferRepositoryTest.kt`

对于序列化行为，测试直接覆盖辅助函数。

示例：

- `app/src/test/java/com/qi/smbshare/util/ConfigSerializerTest.kt`

---

## 边界

- 不要添加服务端数据库约定；本仓库不存在服务端数据库。
- 不要要求代码库当前没有使用的 migration 体系。
- 不要把新的敏感配置值存储到默认会备份的 SharedPreferences 中。
- 处理传输任务时不要绕过现有 model/entity 转换辅助函数。
