package com.qi.smbshare.service.transfer

import com.hierynomus.smbj.share.DiskShare
import com.qi.smbshare.data.model.SMBConfig
import io.mockk.mockk
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServiceSmbConnectionPoolTest {

    @Test
    fun `acquire reuses connection bucket for the same config id`() {
        val fakeConnection = FakeServiceSmbConnection()
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
        }
        val config = sampleConfig("config-1")

        val first = pool.acquire(config)
        pool.release(config)
        val second = pool.acquire(config)

        assertSame(first, second)
        assertEquals(1, fakeConnection.connectCount)
    }

    @Test
    fun `closeIdleConnections disconnects expired buckets`() {
        var now = 0L
        val fakeConnection = FakeServiceSmbConnection()
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
            nowMillis = { now }
            idleTimeoutMillis = 100L
        }
        val config = sampleConfig("config-expired")

        pool.acquire(config)
        pool.release(config)
        now = 101L
        pool.closeIdleConnections()

        assertTrue(fakeConnection.disconnected)
    }

    @Test
    fun `closeIdleConnections keeps active leased bucket`() {
        var now = 0L
        val fakeConnection = FakeServiceSmbConnection()
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
            nowMillis = { now }
            idleTimeoutMillis = 100L
        }

        pool.acquire(sampleConfig("config-active"))
        now = 1_000L
        pool.closeIdleConnections()

        assertTrue(!fakeConnection.disconnected)
    }

    @Test
    fun `failed acquire does not leak active lease and idle cleanup disconnects bucket`() {
        var now = 0L
        val fakeConnection = FakeServiceSmbConnection(failuresBeforeSuccess = 1)
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
            nowMillis = { now }
            idleTimeoutMillis = 100L
        }

        assertThrows(IOException::class.java) {
            pool.acquire(sampleConfig("config-failure"))
        }

        now = 101L
        pool.closeIdleConnections()

        assertTrue(fakeConnection.disconnected)
    }

    @Test
    fun `successful retry after failed acquire releases single lease and becomes idle`() {
        var now = 0L
        val fakeConnection = FakeServiceSmbConnection(failuresBeforeSuccess = 1)
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
            nowMillis = { now }
            idleTimeoutMillis = 100L
        }
        val config = sampleConfig("config-retry")

        assertThrows(IOException::class.java) {
            pool.acquire(config)
        }
        pool.acquire(config)
        pool.release(config)
        now = 101L
        pool.closeIdleConnections()

        assertEquals(2, fakeConnection.connectCount)
        assertTrue(fakeConnection.disconnected)
    }

    @Test
    fun `reconnect closes stale disconnected bucket before opening a new share`() {
        val fakeConnection = FakeServiceSmbConnection()
        val pool = ServiceSmbConnectionPool().apply {
            connectionFactory = { fakeConnection }
        }
        val config = sampleConfig("config-reconnect")

        pool.acquire(config)
        pool.release(config)
        fakeConnection.forceDisconnected()

        pool.acquire(config)

        assertEquals(2, fakeConnection.connectCount)
        assertEquals(1, fakeConnection.disconnectCount)
    }

    private fun sampleConfig(id: String): SMBConfig {
        return SMBConfig(
            id = id,
            serverAddress = "192.168.0.10",
            shareName = "share"
        )
    }

    private class FakeServiceSmbConnection(
        private var failuresBeforeSuccess: Int = 0
    ) : ServiceSmbConnection {
        private val share = mockk<DiskShare>(relaxed = true)
        var connectCount = 0
        var disconnectCount = 0
        var disconnected = false
        private var connected = false

        override fun connect(config: SMBConfig): DiskShare {
            connectCount++
            if (failuresBeforeSuccess > 0) {
                failuresBeforeSuccess--
                throw IOException("连接失败")
            }
            connected = true
            disconnected = false
            return share
        }

        override fun isConnected(): Boolean = connected

        override fun disconnect() {
            disconnectCount++
            disconnected = true
            connected = false
        }

        fun forceDisconnected() {
            connected = false
        }
    }
}
