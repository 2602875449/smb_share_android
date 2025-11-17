package com.qi.smb_share_android.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 传输任务数据库
 * 使用单例模式确保全局只有一个数据库实例
 */
@Database(
    entities = [TransferTaskEntity::class],
    version = 3,
    exportSchema = false
)
abstract class TransferDatabase : RoomDatabase() {
    
    /**
     * 获取传输任务 DAO
     */
    abstract fun transferTaskDao(): TransferTaskDao
    
    companion object {
        @Volatile
        private var INSTANCE: TransferDatabase? = null
        
        /**
         * 获取数据库实例
         * 使用双重检查锁定确保线程安全
         */
        fun getInstance(context: Context): TransferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransferDatabase::class.java,
                    "transfer_database"
                )
                    .fallbackToDestructiveMigration(true) // 开发阶段使用，生产环境需要提供迁移策略
                    .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 清除数据库实例（主要用于测试）
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
