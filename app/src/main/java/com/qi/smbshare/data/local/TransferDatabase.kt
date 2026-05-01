package com.qi.smbshare.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 传输任务数据库
 * 生产实例统一由 Hilt 创建，避免 Room 数据库存在多个打开入口。
 */
@Database(
    entities = [TransferTaskEntity::class],
    version = 3,
    exportSchema = true
)
abstract class TransferDatabase : RoomDatabase() {
    
    /**
     * 获取传输任务 DAO
     */
    abstract fun transferTaskDao(): TransferTaskDao

    companion object {
        /*
         * 已提交的 git 历史里，TransferDatabase 在开启 schema 导出前已经是 version 3，
         * 且 transfer_tasks 表结构与当前 schema 等价；没有可审计的 v1/v2 schema 文件。
         * 因此 1->2 和 2->3 迁移只支持这类“旧版本号 + 当前表结构”的安装包，
         * 通过 Room 打开后的 schema 校验拦截任何真实结构不一致的旧库。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTransferTasksTableIfMissing(db)
                createTransferTaskIndexes(db)
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createTransferTasksTableIfMissing(db)
                createTransferTaskIndexes(db)
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        private fun createTransferTasksTableIfMissing(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transfer_tasks` (
                    `id` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `fileName` TEXT NOT NULL,
                    `fileSize` INTEGER NOT NULL,
                    `remotePath` TEXT NOT NULL,
                    `localPath` TEXT NOT NULL,
                    `configData` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `progress` INTEGER NOT NULL,
                    `transferredBytes` INTEGER NOT NULL,
                    `speed` INTEGER NOT NULL,
                    `estimatedTimeRemaining` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `retryCount` INTEGER NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `startedAt` INTEGER,
                    `completedAt` INTEGER,
                    `lastUpdatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }

        private fun createTransferTaskIndexes(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_tasks_status` ON `transfer_tasks` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_tasks_type` ON `transfer_tasks` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_tasks_created_at` ON `transfer_tasks` (`created_at`)")
        }
    }
}
