package com.qi.smbshare.data.discovery

import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SmbDiscoveryTargetParserTest {

    @Test
    fun `parse 支持单个 IPv4 地址`() {
        val target = SmbDiscoveryTargetParser.parse("192.168.1.55").getOrThrow()

        assertEquals("192.168.1.55", target.input)
        assertEquals(listOf("192.168.1.55"), target.addresses.map { it.hostAddress })
    }

    @Test
    fun `parse 支持 IPv4 24 网段并排除网络和广播地址`() {
        val target = SmbDiscoveryTargetParser.parse("192.168.1.0/24").getOrThrow()

        assertEquals(254, target.addresses.size)
        assertEquals("192.168.1.1", target.addresses.first().hostAddress)
        assertEquals("192.168.1.254", target.addresses.last().hostAddress)
    }

    @Test
    fun `parse 支持 IPv4 31 网段并保留两个点对点地址`() {
        val target = SmbDiscoveryTargetParser.parse("192.168.1.55/31").getOrThrow()

        assertEquals(listOf("192.168.1.54", "192.168.1.55"), target.addresses.map { it.hostAddress })
    }

    @Test
    fun `parse 支持 IPv4 32 网段作为单地址探测`() {
        val target = SmbDiscoveryTargetParser.parse("192.168.1.55/32").getOrThrow()

        assertEquals(listOf("192.168.1.55"), target.addresses.map { it.hostAddress })
    }

    @Test
    fun `parse 拒绝过大的网段避免误扫大量地址`() {
        val result = SmbDiscoveryTargetParser.parse("192.168.0.0/16")

        assertTrue(result.isFailure)
    }

    @Test
    fun `ManualSmbTargetScanner 只返回 445 可达的手动目标`() = kotlinx.coroutines.test.runTest {
        val reachable = InetAddress.getByName("192.168.1.55")
        val unreachable = InetAddress.getByName("192.168.1.56")
        val scanner = ManualSmbTargetScanner(
            target = SmbDiscoveryTarget(
                input = "192.168.1.55/31",
                addresses = listOf(reachable, unreachable)
            ),
            tcpPortChecker = FakeTcpPortChecker(setOf("192.168.1.55")),
            netBiosNameServiceClient = NetBiosNameServiceClient(FakeUdpDatagramClient())
        )

        val hosts = mutableListOf<SmbDiscoveryHost>()
        scanner.discover().collect { hosts.add(it) }

        assertEquals(1, hosts.size)
        assertEquals("192.168.1.55", hosts.first().address)
        assertEquals(SmbDiscoverySource.MANUAL, hosts.first().source)
    }

    private class FakeTcpPortChecker(
        private val reachableHosts: Set<String>
    ) : TcpPortChecker {
        override suspend fun canConnect(
            address: InetAddress,
            port: Int,
            timeoutMillis: Int
        ): Boolean {
            return address.hostAddress.orEmpty() in reachableHosts
        }
    }

    private class FakeUdpDatagramClient : UdpDatagramClient {
        override suspend fun query(
            address: InetAddress,
            port: Int,
            payload: ByteArray,
            timeoutMillis: Int
        ): ByteArray? = null
    }
}
