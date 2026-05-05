package com.qi.smbshare.ui.connection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.discovery.SmbHostDiscovery
import com.qi.smbshare.data.discovery.SmbDiscoveryTarget
import com.qi.smbshare.data.local.SMBConnectionManager
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import com.qi.smbshare.data.repository.ConnectionRepository
import com.qi.smbshare.domain.usecase.ConnectSMBUseCase
import com.qi.smbshare.domain.usecase.DeleteConnectionUseCase
import com.qi.smbshare.domain.usecase.SaveConnectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConnectionViewModelDiscoveryTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
        application = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `StartDiscovery 成功后更新发现列表`() = runTest(dispatcher) {
        val host = SmbDiscoveryHost(
            displayName = "NAS",
            address = "192.168.1.10",
            port = 445,
            source = SmbDiscoverySource.NETBIOS
        )
        val viewModel = buildViewModel(FakeDiscovery(flowOf(listOf(host))))

        viewModel.handleIntent(ConnectionIntent.StartDiscovery)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDiscovering)
        assertTrue(viewModel.state.value.hasDiscoveryStarted)
        assertEquals(listOf(host), viewModel.state.value.discoveredHosts)
    }

    @Test
    fun `SelectDiscoveredHost 填充地址端口和空名称且不覆盖共享与凭据`() = runTest(dispatcher) {
        val viewModel = buildViewModel(FakeDiscovery(flowOf(emptyList())))
        viewModel.handleIntent(
            ConnectionIntent.EditConfig(
                SMBConfig(
                    name = "",
                    serverAddress = "old.local",
                    port = 1445,
                    shareName = "docs",
                    username = "user",
                    password = "secret"
                )
            )
        )

        viewModel.handleIntent(
            ConnectionIntent.SelectDiscoveredHost(
                SmbDiscoveryHost(
                    displayName = "Office NAS",
                    address = "192.168.1.30",
                    port = 445,
                    source = SmbDiscoverySource.MDNS
                )
            )
        )

        val config = viewModel.state.value.currentConfig!!
        assertEquals("Office NAS", config.name)
        assertEquals("192.168.1.30", config.serverAddress)
        assertEquals(445, config.port)
        assertEquals("docs", config.shareName)
        assertEquals("user", config.username)
        assertEquals("secret", config.password)
    }

    @Test
    fun `StartDiscovery 失败时记录扫描错误并停止加载`() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            FakeDiscovery(
                flow {
                    throw IOException("scan failed")
                }
            )
        )

        viewModel.handleIntent(ConnectionIntent.StartDiscovery)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDiscovering)
        assertTrue(viewModel.state.value.discoveryError!!.isNotBlank())
    }

    @Test
    fun `StopDiscovery 停止扫描并取消后台发现流`() = runTest(dispatcher) {
        var wasCancelled = false
        val viewModel = buildViewModel(
            FakeDiscovery(
                flow<List<SmbDiscoveryHost>> {
                    emit(emptyList())
                    awaitCancellation()
                }.onCompletion { wasCancelled = true }
            )
        )

        viewModel.handleIntent(ConnectionIntent.StartDiscovery)
        runCurrent()
        assertTrue(viewModel.state.value.isDiscovering)

        viewModel.handleIntent(ConnectionIntent.StopDiscovery)
        runCurrent()

        assertFalse(viewModel.state.value.isDiscovering)
        assertTrue(wasCancelled)
    }

    @Test
    fun `ProbeDiscoveryTarget 使用手动目标发现跨网段 SMB 主机`() = runTest(dispatcher) {
        val host = SmbDiscoveryHost(
            displayName = "WIN10",
            address = "192.168.1.55",
            port = 445,
            source = SmbDiscoverySource.MANUAL
        )
        val discovery = FakeDiscovery(
            defaultFlow = flowOf(emptyList()),
            targetFlow = flowOf(listOf(host))
        )
        val viewModel = buildViewModel(discovery)

        viewModel.handleIntent(ConnectionIntent.UpdateDiscoveryTarget("192.168.1.55"))
        viewModel.handleIntent(ConnectionIntent.ProbeDiscoveryTarget)
        advanceUntilIdle()

        assertEquals("192.168.1.55", discovery.lastTarget!!.input)
        assertFalse(viewModel.state.value.isDiscovering)
        assertEquals(listOf(host), viewModel.state.value.discoveredHosts)
    }

    @Test
    fun `ProbeDiscoveryTarget 目标格式无效时提示错误且不启动扫描`() = runTest(dispatcher) {
        val discovery = FakeDiscovery(flowOf(emptyList()))
        val viewModel = buildViewModel(discovery)

        viewModel.handleIntent(ConnectionIntent.UpdateDiscoveryTarget("192.168.1.0/16"))
        viewModel.handleIntent(ConnectionIntent.ProbeDiscoveryTarget)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.discoveryError!!.isNotBlank())
        assertEquals(0, discovery.targetDiscoverCount)
    }

    @Test
    fun `LoadConnections 恢复最后访问状态清除后不会重复恢复`() = runTest(dispatcher) {
        val config = SMBConfig(
            id = "restore-once",
            name = "Office NAS",
            serverAddress = "192.168.1.30",
            shareName = "docs"
        )
        val savedConfigs = MutableStateFlow(listOf(config))
        val connectionRepository = buildConnectionRepository(
            savedConfigs = savedConfigs,
            lastAccess = config.id to "projects"
        )

        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectionRepository = connectionRepository
        )
        advanceUntilIdle()

        assertEquals(
            RestoredLastAccess(config = config, path = "projects"),
            viewModel.state.value.restoredLastAccess
        )

        viewModel.handleIntent(ConnectionIntent.ClearRestoredLastAccess)
        savedConfigs.value = listOf(config.copy(name = "Office NAS Updated"))
        advanceUntilIdle()

        assertNull(viewModel.state.value.restoredLastAccess)
        coVerify(exactly = 1) { connectionRepository.getLastAccess() }
    }

    @Test
    fun `FetchShares 成功后展示可选共享并允许选用`() = runTest(dispatcher) {
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true)
        coEvery { connectUseCase.listShares(any()) } returns Result.success(listOf("docs", "media"))
        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectUseCase = connectUseCase
        )
        viewModel.handleIntent(ConnectionIntent.EditConfig(baseConfig()))

        viewModel.handleIntent(ConnectionIntent.FetchShares)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFetchingShares)
        assertEquals(listOf("docs", "media"), viewModel.state.value.availableShares)
        assertTrue(viewModel.state.value.hasFetchedShares)
        assertNull(viewModel.state.value.shareFetchError)
        assertNull(viewModel.state.value.error)

        viewModel.handleIntent(ConnectionIntent.SelectShare("media"))

        assertEquals("media", viewModel.state.value.formShareName)
    }

    @Test
    fun `FetchShares 空列表只展示共享区域空状态且不触发全局错误`() = runTest(dispatcher) {
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true)
        coEvery { connectUseCase.listShares(any()) } returns Result.success(emptyList())
        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectUseCase = connectUseCase
        )
        viewModel.handleIntent(ConnectionIntent.EditConfig(baseConfig(shareName = "manual")))

        viewModel.handleIntent(ConnectionIntent.FetchShares)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFetchingShares)
        assertTrue(viewModel.state.value.hasFetchedShares)
        assertTrue(viewModel.state.value.availableShares.isEmpty())
        assertNull(viewModel.state.value.shareFetchError)
        assertNull(viewModel.state.value.error)
        assertEquals("manual", viewModel.state.value.formShareName)
    }

    @Test
    fun `FetchShares 失败时停止加载保留表单并隔离为共享区域错误`() = runTest(dispatcher) {
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true)
        coEvery { connectUseCase.listShares(any()) } returns Result.failure(IOException("EOF while reading packet"))
        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectUseCase = connectUseCase
        )
        viewModel.handleIntent(
            ConnectionIntent.EditConfig(
                baseConfig(
                    serverAddress = "nas.local",
                    port = 1445,
                    shareName = "manual",
                    username = "alice",
                    password = "secret"
                )
            )
        )

        viewModel.handleIntent(ConnectionIntent.FetchShares)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isFetchingShares)
        assertTrue(viewModel.state.value.hasFetchedShares)
        assertTrue(viewModel.state.value.availableShares.isEmpty())
        assertTrue(viewModel.state.value.shareFetchError!!.isNotBlank())
        assertNull(viewModel.state.value.error)
        assertEquals("nas.local", viewModel.state.value.formServerAddress)
        assertEquals("1445", viewModel.state.value.formPort)
        assertEquals("manual", viewModel.state.value.formShareName)
        assertEquals("alice", viewModel.state.value.formUsername)
        assertEquals("secret", viewModel.state.value.formPassword)
    }

    @Test
    fun `UpdateFormField 修改连接参数时清空旧共享列表状态`() = runTest(dispatcher) {
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true)
        coEvery { connectUseCase.listShares(any()) } returns Result.success(listOf("docs"))
        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectUseCase = connectUseCase
        )
        viewModel.handleIntent(ConnectionIntent.EditConfig(baseConfig(shareName = "manual")))
        viewModel.handleIntent(ConnectionIntent.FetchShares)
        advanceUntilIdle()

        viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.SERVER_ADDRESS, "nas-new.local"))

        assertFalse(viewModel.state.value.isFetchingShares)
        assertFalse(viewModel.state.value.hasFetchedShares)
        assertTrue(viewModel.state.value.availableShares.isEmpty())
        assertNull(viewModel.state.value.shareFetchError)
        assertEquals("manual", viewModel.state.value.formShareName)
    }

    @Test
    fun `FetchShares 表单变更后的过期结果不会写入状态`() = runTest(dispatcher) {
        val pendingResult = CompletableDeferred<Result<List<String>>>()
        val connectUseCase = mockk<ConnectSMBUseCase>(relaxed = true)
        coEvery { connectUseCase.listShares(any()) } coAnswers { pendingResult.await() }
        val viewModel = buildViewModel(
            discovery = FakeDiscovery(flowOf(emptyList())),
            connectUseCase = connectUseCase
        )
        viewModel.handleIntent(ConnectionIntent.EditConfig(baseConfig()))

        viewModel.handleIntent(ConnectionIntent.FetchShares)
        runCurrent()
        assertTrue(viewModel.state.value.isFetchingShares)

        viewModel.handleIntent(ConnectionIntent.UpdateFormField(FormField.SERVER_ADDRESS, "nas-new.local"))
        runCurrent()
        assertFalse(viewModel.state.value.isFetchingShares)

        pendingResult.complete(Result.success(listOf("old-share")))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasFetchedShares)
        assertTrue(viewModel.state.value.availableShares.isEmpty())
        assertNull(viewModel.state.value.shareFetchError)
        assertEquals("nas-new.local", viewModel.state.value.formServerAddress)
    }

    private fun buildViewModel(
        discovery: SmbHostDiscovery,
        connectionRepository: ConnectionRepository = buildConnectionRepository(),
        connectUseCase: ConnectSMBUseCase = ConnectSMBUseCase(SMBConnectionManager())
    ): ConnectionViewModel {
        return ConnectionViewModel(
            application = application,
            smbHostDiscovery = discovery,
            ioDispatcher = dispatcher,
            connectionRepository = connectionRepository,
            connectUseCase = connectUseCase,
            saveConnectionUseCase = SaveConnectionUseCase(connectionRepository),
            deleteConnectionUseCase = DeleteConnectionUseCase(connectionRepository)
        )
    }

    private fun baseConfig(
        serverAddress: String = "192.168.1.20",
        port: Int = 445,
        shareName: String = "",
        username: String = "user",
        password: String = "pass"
    ): SMBConfig {
        return SMBConfig(
            serverAddress = serverAddress,
            port = port,
            shareName = shareName,
            username = username,
            password = password
        )
    }

    private fun buildConnectionRepository(
        savedConfigs: Flow<List<SMBConfig>> = flowOf(emptyList()),
        lastAccess: Pair<String?, String?> = null to null
    ): ConnectionRepository {
        val repository = mockk<ConnectionRepository>(relaxed = true)
        every { repository.getSavedConfigs() } returns savedConfigs
        coEvery { repository.getLastAccess() } returns lastAccess
        return repository
    }

    private class FakeDiscovery(
        private val defaultFlow: Flow<List<SmbDiscoveryHost>>,
        private val targetFlow: Flow<List<SmbDiscoveryHost>> = defaultFlow
    ) : SmbHostDiscovery {
        var lastTarget: SmbDiscoveryTarget? = null
        var targetDiscoverCount: Int = 0

        override fun discover(): Flow<List<SmbDiscoveryHost>> = defaultFlow

        override fun discover(target: SmbDiscoveryTarget): Flow<List<SmbDiscoveryHost>> {
            lastTarget = target
            targetDiscoverCount += 1
            return targetFlow
        }
    }
}
