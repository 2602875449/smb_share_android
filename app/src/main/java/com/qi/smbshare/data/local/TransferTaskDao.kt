package com.qi.smbshare.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
