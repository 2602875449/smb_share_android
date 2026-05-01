package com.qi.smbshare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class TransferTaskStatusRow(
    val id: String,
    val status: String
)

/**
 * 传输任务数据访问对象
 * 提供数据库操作接口
 */
@Dao
interface TransferTaskDao {
    
    /**
     * 获取所有传输任务，按创建时间倒序排列
     */
    @Query("SELECT * FROM transfer_tasks ORDER BY created_at DESC")
    fun getAllTasks(): Flow<List<TransferTaskEntity>>

    /**
     * 只观察服务控制所需的状态列，避免进度高频更新触发完整任务重分发。
     */
    @Query("SELECT id, status FROM transfer_tasks")
    fun getTaskStatusRows(): Flow<List<TransferTaskStatusRow>>
    
    /**
     * 获取活动的传输任务（等待中、进行中、已暂停）
     */
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('PENDING', 'ACTIVE', 'PAUSED') ORDER BY created_at DESC")
    fun getActiveTasks(): Flow<List<TransferTaskEntity>>
    
    /**
     * 获取活动传输任务的数量
     */
    @Query("SELECT COUNT(*) FROM transfer_tasks WHERE status IN ('PENDING', 'ACTIVE')")
    fun getActiveTransferCount(): Flow<Int>
    
    /**
     * 根据类型获取活动任务（下载中或上传中）
     */
    @Query("SELECT * FROM transfer_tasks WHERE type = :type AND status IN ('PENDING', 'ACTIVE', 'PAUSED') ORDER BY created_at DESC")
    fun getActiveTasksByType(type: String): Flow<List<TransferTaskEntity>>
    
    /**
     * 获取已完成的任务（已完成、失败、已取消）
     */
    @Query("SELECT * FROM transfer_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<TransferTaskEntity>>
    
    /**
     * 根据 ID 获取单个任务
     */
    @Query("SELECT * FROM transfer_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TransferTaskEntity?
    
    /**
     * 插入新任务，如果冲突则替换
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TransferTaskEntity): Long
    
    /**
     * 更新任务
     */
    @Update
    suspend fun updateTask(task: TransferTaskEntity)

    /**
     * 高频进度更新只改动进度相关列，避免每秒读取并回写完整 Entity。
     */
    @Query(
        """
        UPDATE transfer_tasks
        SET progress = :progress,
            transferredBytes = :transferredBytes,
            speed = :speed,
            estimatedTimeRemaining = CASE
                WHEN :speed > 0 THEN
                    ((CASE
                        WHEN fileSize - :transferredBytes > 0 THEN fileSize - :transferredBytes
                        ELSE 0
                    END) * 1000) / :speed
                ELSE 0
            END,
            lastUpdatedAt = :lastUpdatedAt
        WHERE id = :taskId
        """
    )
    suspend fun updateProgress(
        taskId: String,
        progress: Int,
        transferredBytes: Long,
        speed: Long,
        lastUpdatedAt: Long
    ): Int
    
    /**
     * 删除任务
     */
    @Delete
    suspend fun deleteTask(task: TransferTaskEntity)
    
    /**
     * 根据 ID 删除任务
     */
    @Query("DELETE FROM transfer_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
    
    /**
     * 删除所有已完成的任务
     */
    @Query("DELETE FROM transfer_tasks WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun deleteAllCompletedTasks()
    
    /**
     * 批量删除任务
     */
    @Query("DELETE FROM transfer_tasks WHERE id IN (:taskIds)")
    suspend fun deleteTasksByIds(taskIds: List<String>)
}
