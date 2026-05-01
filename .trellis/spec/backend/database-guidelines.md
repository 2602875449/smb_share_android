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
- 设置 `exportSchema = false`。
- 暴露 `transferTaskDao()`。
- 使用带 double-checked locking 的 singleton `getInstance(context)`。
- 当前使用 `.fallbackToDestructiveMigration(true)`，并通过行内注释说明这是开发阶段选择。

示例：

- `app/src/main/java/com/qi/smbshare/data/local/TransferDatabase.kt`

不要记录或添加单独的 migration 框架。如果任务修改传输任务 schema，更新 Room 版本，并在 `TransferDatabase.kt` 中明确保留 migration 行为。

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

`TransferRepository` 接受 `TransferTaskDao` 构造参数，并默认使用来自 `TransferDatabase` 的 DAO。测试使用这一点注入 in-memory Room DAO。

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
