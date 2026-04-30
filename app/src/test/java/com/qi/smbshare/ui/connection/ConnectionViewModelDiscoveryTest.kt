package com.qi.smbshare.ui.connection

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qi.smbshare.data.discovery.SmbHostDiscovery
import com.qi.smbshare.data.discovery.SmbDiscoveryTarget
import com.qi.smbshare.data.model.SMBConfig
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

    private fun buildViewModel(discovery: SmbHostDiscovery): ConnectionViewModel {
        return ConnectionViewModel(
            application = application,
            smbHostDiscovery = discovery,
            ioDispatcher = dispatcher
        )
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
