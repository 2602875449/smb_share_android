package com.qi.smbshare.ui.download

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.local.TransferDatabase
import com.qi.smbshare.data.local.TransferTaskDao
import com.qi.smbshare.data.local.toEntity
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.data.repository.TransferRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证下载历史记录相关的仓库操作：已完成/失败/取消任务的查询、删除、清空与重试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = DownloadHistoryViewModelTest.TestApp::class,
    sdk = [33]
)
class DownloadHistoryViewModelTest {

    private lateinit var database: TransferDatabase
    private lateinit var dao: TransferTaskDao
    private lateinit var repository: TransferRepository
    private lateinit var context: TestApp

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            TransferDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.transferTaskDao()
        repository = TransferRepository(context, dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getCompletedTasks 仅返回终态任务`() = runTest {
        dao.insertTask(buildTask("t1", TransferStatus.COMPLETED).toEntity())
        dao.insertTask(buildTask("t2", TransferStatus.FAILED).toEntity())
        dao.insertTask(buildTask("t3", TransferStatus.CANCELLED).toEntity())
        dao.insertTask(buildTask("t4", TransferStatus.ACTIVE).toEntity())

        val completed = repository.getCompletedTasks().first()

        assertEquals(3, completed.size)
        assertTrue(completed.none { it.status == TransferStatus.ACTIVE })
    }

    @Test
    fun `deleteTransfer 从数据库移除指定任务`() = runTest {
        dao.insertTask(buildTask("del-1", TransferStatus.COMPLETED).toEntity())
        assertNotNull(dao.getTaskById("del-1"))

        repository.deleteTransfer("del-1")

        assertNull(dao.getTaskById("del-1"))
    }

    @Test
    fun `deleteTransfers 批量移除多个任务`() = runTest {
        dao.insertTask(buildTask("m1", TransferStatus.COMPLETED).toEntity())
        dao.insertTask(buildTask("m2", TransferStatus.FAILED).toEntity())
        dao.insertTask(buildTask("m3", TransferStatus.ACTIVE).toEntity())

        repository.deleteTransfers(listOf("m1", "m2"))

        assertNull(dao.getTaskById("m1"))
        assertNull(dao.getTaskById("m2"))
        assertNotNull(dao.getTaskById("m3"))
    }

    @Test
    fun `clearCompletedTasks 清空所有终态任务并保留活跃任务`() = runTest {
        dao.insertTask(buildTask("c1", TransferStatus.COMPLETED).toEntity())
        dao.insertTask(buildTask("c2", TransferStatus.FAILED).toEntity())
        dao.insertTask(buildTask("c3", TransferStatus.CANCELLED).toEntity())
        dao.insertTask(buildTask("c4", TransferStatus.ACTIVE).toEntity())

        repository.clearCompletedTasks()

        assertNull(dao.getTaskById("c1"))
        assertNull(dao.getTaskById("c2"))
        assertNull(dao.getTaskById("c3"))
        assertNotNull(dao.getTaskById("c4"))
    }

    @Test
    fun `retryTransfer 为失败任务创建新任务且 retryCount 递增`() = runTest {
        dao.insertTask(buildTask("retry-1", TransferStatus.FAILED, retryCount = 2).toEntity())

        val newTaskId = repository.retryTransfer("retry-1")

        assertNotNull(newTaskId)
        val newTask = dao.getTaskById(newTaskId!!)
        assertNotNull(newTask)
        assertEquals(TransferStatus.PENDING.name, newTask!!.status)
        assertEquals(3, newTask.retryCount)
    }

    @Test
    fun `retryTransfer 对非失败任务返回 null`() = runTest {
        dao.insertTask(buildTask("active-1", TransferStatus.ACTIVE).toEntity())

        val result = repository.retryTransfer("active-1")

        assertNull(result)
    }

    @Test
    fun `updateTaskStatus 设为 FAILED 时记录错误信息`() = runTest {
        dao.insertTask(buildTask("fail-1", TransferStatus.ACTIVE).toEntity())

        repository.updateTaskStatus("fail-1", TransferStatus.FAILED, "连接超时")

        val task = dao.getTaskById("fail-1")
        assertNotNull(task)
        assertEquals(TransferStatus.FAILED.name, task!!.status)
        assertEquals("连接超时", task.errorMessage)
    }

    @Test
    fun `updateTaskStatus 设为 COMPLETED 时记录完成时间`() = runTest {
        val before = System.currentTimeMillis()
        dao.insertTask(buildTask("done-1", TransferStatus.ACTIVE).toEntity())

        repository.updateTaskStatus("done-1", TransferStatus.COMPLETED)

        val task = dao.getTaskById("done-1")
        assertNotNull(task)
        assertEquals(TransferStatus.COMPLETED.name, task!!.status)
        assertTrue("completedAt 应在操作之后", task.completedAt != null && task.completedAt!! >= before)
    }

    @Test
    fun `cancelTransfer 仅取消可取消状态的任务`() = runTest {
        dao.insertTask(buildTask("cancel-1", TransferStatus.ACTIVE).toEntity())
        dao.insertTask(buildTask("cancel-2", TransferStatus.COMPLETED).toEntity())

        repository.cancelTransfer("cancel-1")
        repository.cancelTransfer("cancel-2")

        assertEquals(TransferStatus.CANCELLED.name, dao.getTaskById("cancel-1")!!.status)
        assertEquals(TransferStatus.COMPLETED.name, dao.getTaskById("cancel-2")!!.status)
    }

    private fun buildTask(
        id: String,
        status: TransferStatus,
        retryCount: Int = 0
    ) = TransferTask(
        id = id,
        type = TransferType.DOWNLOAD,
        fileName = "file.mkv",
        fileSize = 1024L * 1024,
        remotePath = "\\\\nas\\movies\\file.mkv",
        localPath = "/storage/emulated/0/Movies/file.mkv",
        config = SMBConfig(
            serverAddress = "192.168.0.1",
            shareName = "movies",
            username = "u",
            password = "p"
        ),
        status = status,
        retryCount = retryCount
    )

    /** 吞掉前台服务启动请求，避免 Robolectric 报错 */
    class TestApp : Application() {
        override fun startForegroundService(service: Intent): ComponentName? {
            val component = service.component
            return ComponentName(this, component?.className ?: "")
        }
    }
}
