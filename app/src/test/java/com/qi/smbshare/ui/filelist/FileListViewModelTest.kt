package com.qi.smbshare.ui.filelist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.local.DataStoreManager
import com.qi.smbshare.data.model.FileItem
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.repository.SMBFileRepository
import com.qi.smbshare.data.repository.TransferRepository
import com.qi.smbshare.domain.usecase.ConnectSMBUseCase
import com.qi.smbshare.domain.usecase.CreateFolderUseCase
import com.qi.smbshare.domain.usecase.DeleteFileUseCase
import com.qi.smbshare.domain.usecase.ListFilesUseCase
import com.qi.smbshare.domain.usecase.RenameFileUseCase
import com.qi.smbshare.domain.usecase.UploadFileUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FileListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `older loadFiles result cannot overwrite latest directory state`() = runTest(dispatcher) {
        val oldLoadStarted = CompletableDeferred<Unit>()
        val oldLoadMayFinish = CompletableDeferred<Unit>()
        val oldFile = FileItem(name = "old.txt", path = "old.txt", isDirectory = false)
        val newFile = FileItem(name = "new.txt", path = "docs\\new.txt", isDirectory = false)
        val listFilesUseCase = mockk<ListFilesUseCase>()

        coEvery { listFilesUseCase.execute("") } coAnswers {
            oldLoadStarted.complete(Unit)
            // 模拟不可立即取消的 SMB 阻塞调用返回旧结果。
            withContext(NonCancellable) {
                oldLoadMayFinish.await()
            }
            Result.success(listOf(oldFile))
        }
        coEvery { listFilesUseCase.execute("docs") } returns Result.success(listOf(newFile))

        val viewModel = buildViewModel(listFilesUseCase)
        runCurrent()
        oldLoadStarted.await()

        viewModel.handleIntent(FileListIntent.JumpToPath("docs"))
        runCurrent()

        assertEquals("docs", viewModel.state.value.currentPath)
        assertEquals(listOf(newFile), viewModel.state.value.files)

        oldLoadMayFinish.complete(Unit)
        runCurrent()

        assertEquals("docs", viewModel.state.value.currentPath)
        assertEquals(listOf(newFile), viewModel.state.value.files)
    }

    private fun buildViewModel(listFilesUseCase: ListFilesUseCase): FileListViewModel {
        val config = SMBConfig(
            id = "config-1",
            serverAddress = "192.168.0.10",
            shareName = "share"
        )
        val dataStoreManager = mockk<DataStoreManager>(relaxed = true) {
            coEvery { saveLastAccess(any(), any()) } returns Unit
        }
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true) {
            coEvery { execute(config) } returns Result.success(Unit)
            every { isConnected() } returns true
        }

        return FileListViewModel(
            application = application,
            transferRepository = mockk<TransferRepository>(relaxed = true),
            dataStoreManager = dataStoreManager,
            connectUseCase = connectUseCase,
            fileRepository = mockk<SMBFileRepository>(relaxed = true),
            listFilesUseCase = listFilesUseCase,
            uploadFileUseCase = mockk<UploadFileUseCase>(relaxed = true),
            createFolderUseCase = mockk<CreateFolderUseCase>(relaxed = true),
            deleteFileUseCase = mockk<DeleteFileUseCase>(relaxed = true),
            renameFileUseCase = mockk<RenameFileUseCase>(relaxed = true),
            config = config,
            initialPath = ""
        )
    }
}
