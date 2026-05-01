package com.qi.smbshare.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.TransferStatus
import com.qi.smbshare.data.model.TransferTask
import com.qi.smbshare.data.model.TransferType
import com.qi.smbshare.util.toJsonString
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TransferDatabaseMigrationTest {

    private lateinit var context: Application
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath(DB_NAME)
        databaseFile.parentFile?.mkdirs()
        databaseFile.delete()
    }

    @After
    fun tearDown() {
        databaseFile.delete()
    }

    @Test
    fun `migration from version 2 to 3 preserves transfer history`() = runTest {
        createLegacyDatabase(version = 2, taskId = "task-2")

        val migrated = Room.databaseBuilder(context, TransferDatabase::class.java, DB_NAME)
            .addMigrations(*TransferDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val task = migrated.transferTaskDao().getTaskById("task-2")
            assertNotNull(task)
            assertEquals(TransferStatus.PAUSED.name, task!!.status)
            assertEquals(512L, task.transferredBytes)
        } finally {
            migrated.close()
        }
    }

    @Test
    fun `migration from version 1 to 3 preserves transfer history for current legacy schema`() = runTest {
        createLegacyDatabase(version = 1, taskId = "task-1")

        val migrated = Room.databaseBuilder(context, TransferDatabase::class.java, DB_NAME)
            .addMigrations(*TransferDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()

        try {
            val task = migrated.transferTaskDao().getTaskById("task-1")
            assertNotNull(task)
            assertEquals(TransferStatus.PAUSED.name, task!!.status)
            assertEquals(512L, task.transferredBytes)
        } finally {
            migrated.close()
        }
    }

    private fun createLegacyDatabase(version: Int, taskId: String) {
        val config = SMBConfig(serverAddress = "192.168.0.2", shareName = "share")
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { db ->
            db.execSQL(CREATE_TRANSFER_TASKS_SQL)
            db.execSQL(
                """
                INSERT INTO transfer_tasks (
                    id, type, fileName, fileSize, remotePath, localPath, configData,
                    status, progress, transferredBytes, speed, estimatedTimeRemaining,
                    errorMessage, retryCount, created_at, startedAt, completedAt, lastUpdatedAt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    taskId,
                    TransferType.DOWNLOAD.name,
                    "movie.mkv",
                    1024L,
                    "remote/movie.mkv",
                    "/tmp/movie.mkv",
                    config.toJsonString(),
                    TransferStatus.PAUSED.name,
                    50,
                    512L,
                    100L,
                    5_000L,
                    null,
                    1,
                    10L,
                    11L,
                    null,
                    12L
                )
            )
            db.version = version
        }
    }

    private companion object {
        private const val DB_NAME = "migration-test.db"

        private val CREATE_TRANSFER_TASKS_SQL = """
            CREATE TABLE transfer_tasks (
                id TEXT NOT NULL,
                type TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileSize INTEGER NOT NULL,
                remotePath TEXT NOT NULL,
                localPath TEXT NOT NULL,
                configData TEXT NOT NULL,
                status TEXT NOT NULL,
                progress INTEGER NOT NULL,
                transferredBytes INTEGER NOT NULL,
                speed INTEGER NOT NULL,
                estimatedTimeRemaining INTEGER NOT NULL,
                errorMessage TEXT,
                retryCount INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                startedAt INTEGER,
                completedAt INTEGER,
                lastUpdatedAt INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
        """.trimIndent()
    }
}
