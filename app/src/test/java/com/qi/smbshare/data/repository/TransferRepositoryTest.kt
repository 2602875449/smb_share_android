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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
}
