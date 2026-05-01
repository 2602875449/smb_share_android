package com.qi.smbshare.data.repository

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.local.TransferDatabase
import com.qi.smbshare.data.local.TransferTaskDao
import com.qi.smbshare.data.local.toEntity
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.service.TransferServiceControl
import com.qi.smbshare.service.TransferServiceController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
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
 * 覆盖传输仓库的关键路径，确保任务入库与进度更新逻辑稳定。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = TransferRepositoryTest.TestApplication::class,
    sdk = [33]
)
class TransferRepositoryTest {

    private lateinit var context: TestApplication
    private lateinit var dao: TransferTaskDao
    private lateinit var database: TransferDatabase
    private lateinit var repository: TransferRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            TransferDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = database.transferTaskDao()
        repository = TransferRepository(context, dao)
        context.clearStartedServices()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `startDownload should persist task and start foreground service`() = runTest {
        val taskId = repository.startDownload(
            fileName = "movie.mkv",
            remotePath = "\\\\nas\\movies\\movie.mkv",
            localPath = "/storage/emulated/0/Movies/movie.mkv",
            fileSize = 1024L * 1024,
            config = sampleConfig()
        )

        val storedTask = dao.getTaskById(taskId)
        assertNotNull("任务应写入数据库", storedTask)
        assertEquals(TransferType.DOWNLOAD.name, storedTask!!.type)
        assertEquals(TransferStatus.PENDING.name, storedTask.status)
        assertEquals(1, context.startedIntents.size)
        val intent = context.startedIntents.first()
        assertEquals(
            com.qi.smbshare.service.TransferService.ACTION_START_TRANSFER,
            intent.action
        )
        assertEquals(taskId, intent.getStringExtra(com.qi.smbshare.service.TransferService.EXTRA_TASK_ID))
        assertTrue("SMB 配置应序列化后随 Intent 传递", intent.hasExtra(com.qi.smbshare.service.TransferService.EXTRA_CONFIG))
    }

    @Test
    fun `updateProgress should update transferred values and ETA`() = runTest {
        val task = TransferTask(
            id = "task-1",
            type = TransferType.UPLOAD,
            fileName = "log.zip",
            fileSize = 1024L * 1024,
            remotePath = "\\\\nas\\logs\\log.zip",
            localPath = "/tmp/log.zip",
            config = sampleConfig(),
            status = TransferStatus.ACTIVE,
            progress = 0,
            transferredBytes = 0,
            speed = 0,
            estimatedTimeRemaining = 0
        )
        dao.insertTask(task.toEntity())

        repository.updateProgress(
            taskId = task.id,
            progress = 40,
            transferredBytes = 500_000,
            speed = 250_000
        )

        val updated = dao.getTaskById(task.id)
        assertNotNull(updated)
        assertEquals(40, updated!!.progress)
        assertEquals(500_000, updated.transferredBytes)
        assertEquals(250_000, updated.speed)
        assertTrue("应给出预计剩余时间", updated.estimatedTimeRemaining > 0)
        assertEquals("局部更新不应改变任务状态", TransferStatus.ACTIVE.name, updated.status)
        assertEquals("局部更新不应改变配置载荷", task.toEntity().configData, updated.configData)
        assertTrue("应刷新最后更新时间", updated.lastUpdatedAt >= task.lastUpdatedAt)
    }

    @Test
    fun `updateProgress should clamp progress and reset ETA when speed is zero`() = runTest {
        val task = TransferTask(
            id = "task-2",
            type = TransferType.DOWNLOAD,
            fileName = "large.iso",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\large.iso",
            localPath = "/tmp/large.iso",
            config = sampleConfig(),
            status = TransferStatus.ACTIVE,
            progress = 0,
            transferredBytes = 0,
            speed = 1,
            estimatedTimeRemaining = 1_000
        )
        dao.insertTask(task.toEntity())

        repository.updateProgress(
            taskId = task.id,
            progress = 150,
            transferredBytes = 900,
            speed = 0
        )

        val updated = dao.getTaskById(task.id)
        assertNotNull(updated)
        assertEquals(100, updated!!.progress)
        assertEquals(900, updated.transferredBytes)
        assertEquals(0, updated.speed)
        assertEquals(0, updated.estimatedTimeRemaining)
        assertEquals(TransferStatus.ACTIVE.name, updated.status)
    }

    @Test
    fun `taskStatuses should remain unchanged when only progress changes`() = runTest {
        val task = TransferTask(
            id = "status-only",
            type = TransferType.DOWNLOAD,
            fileName = "status-only.bin",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\status-only.bin",
            localPath = "/tmp/status-only.bin",
            config = sampleConfig(),
            status = TransferStatus.ACTIVE
        )
        dao.insertTask(task.toEntity())

        val before = repository.taskStatuses.first()
        repository.updateProgress(
            taskId = task.id,
            progress = 20,
            transferredBytes = 200,
            speed = 100
        )
        val after = repository.taskStatuses.first()

        assertEquals(before, after)
    }

    @Test
    fun `taskStatuses should not emit a new value when only progress changes`() = runTest {
        val task = TransferTask(
            id = "status-emission",
            type = TransferType.DOWNLOAD,
            fileName = "status-emission.bin",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\status-emission.bin",
            localPath = "/tmp/status-emission.bin",
            config = sampleConfig(),
            status = TransferStatus.ACTIVE
        )
        dao.insertTask(task.toEntity())

        val nextStatusEmission = async {
            withTimeoutOrNull(300) {
                repository.taskStatuses.drop(1).first()
            }
        }

        repository.updateProgress(
            taskId = task.id,
            progress = 20,
            transferredBytes = 200,
            speed = 100
        )

        assertNull("进度更新不应触发服务控制状态重新分发", nextStatusEmission.await())
    }

    @Test
    fun `pause and cancel should update database without starting foreground service`() = runTest {
        val activeTask = TransferTask(
            id = "control-1",
            type = TransferType.DOWNLOAD,
            fileName = "control.bin",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\control.bin",
            localPath = "/tmp/control.bin",
            config = sampleConfig(),
            status = TransferStatus.ACTIVE
        )
        val service = RecordingTransferServiceControl()
        dao.insertTask(activeTask.toEntity())
        context.clearStartedServices()
        TransferServiceController.register(service)

        try {
            repository.pauseTransfer(activeTask.id)
            assertEquals(TransferStatus.PAUSED.name, dao.getTaskById(activeTask.id)!!.status)

            repository.cancelTransfer(activeTask.id)
            assertEquals(TransferStatus.CANCELLED.name, dao.getTaskById(activeTask.id)!!.status)
            assertEquals(listOf(activeTask.id), service.pausedTaskIds)
            assertEquals(listOf(activeTask.id), service.cancelledTaskIds)
            assertEquals("控制指令不应重复启动前台服务", 0, context.startedIntents.size)
        } finally {
            TransferServiceController.unregister(service)
        }
    }

    @Test
    fun `resume should use existing service when possible and otherwise start task execution`() = runTest {
        val pausedTask = TransferTask(
            id = "resume-1",
            type = TransferType.DOWNLOAD,
            fileName = "resume.bin",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\resume.bin",
            localPath = "/tmp/resume.bin",
            config = sampleConfig(),
            status = TransferStatus.PAUSED
        )
        dao.insertTask(pausedTask.toEntity())
        context.clearStartedServices()

        repository.resumeTransfer(pausedTask.id)

        assertEquals(TransferStatus.ACTIVE.name, dao.getTaskById(pausedTask.id)!!.status)
        assertEquals("服务不在时恢复应启动实际任务执行", 1, context.startedIntents.size)
        assertEquals(
            com.qi.smbshare.service.TransferService.ACTION_START_TRANSFER,
            context.startedIntents.first().action
        )
    }

    @Test
    fun `resume should notify existing service without starting foreground service`() = runTest {
        val pausedTask = TransferTask(
            id = "resume-existing",
            type = TransferType.DOWNLOAD,
            fileName = "resume-existing.bin",
            fileSize = 1_000L,
            remotePath = "\\\\nas\\resume-existing.bin",
            localPath = "/tmp/resume-existing.bin",
            config = sampleConfig(),
            status = TransferStatus.PAUSED
        )
        val service = RecordingTransferServiceControl()
        dao.insertTask(pausedTask.toEntity())
        context.clearStartedServices()
        TransferServiceController.register(service)

        try {
            repository.resumeTransfer(pausedTask.id)

            assertEquals(TransferStatus.ACTIVE.name, dao.getTaskById(pausedTask.id)!!.status)
            assertEquals(listOf(pausedTask.id), service.resumedTaskIds)
            assertEquals("已有服务时恢复不应重复启动前台服务", 0, context.startedIntents.size)
        } finally {
            TransferServiceController.unregister(service)
        }
    }

    private fun sampleConfig(): SMBConfig {
        return SMBConfig(
            serverAddress = "192.168.0.100",
            shareName = "downloads",
            username = "user",
            password = "pass"
        )
    }

    /**
     * 自定义 Application，记录前台服务启动意图用于断言。
     */
    class TestApplication : Application() {
        val startedIntents = mutableListOf<Intent>()

        override fun startForegroundService(service: Intent): ComponentName? {
            startedIntents.add(service)
            val component = service.component
            return ComponentName(this, component?.className ?: "")
        }

        fun clearStartedServices() {
            startedIntents.clear()
        }
    }

    private class RecordingTransferServiceControl : TransferServiceControl {
        val pausedTaskIds = mutableListOf<String>()
        val resumedTaskIds = mutableListOf<String>()
        val cancelledTaskIds = mutableListOf<String>()

        override fun pauseTransfer(taskId: String) {
            pausedTaskIds.add(taskId)
        }

        override fun resumeTransfer(taskId: String) {
            resumedTaskIds.add(taskId)
        }

        override fun cancelTransfer(taskId: String) {
            cancelledTaskIds.add(taskId)
        }
    }
}
