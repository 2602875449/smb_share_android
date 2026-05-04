package com.qi.smbshare.data.local

import com.hierynomus.smbj.share.DiskShare
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证 SMBConnectionManager 对共享连接状态的并发访问安全。
 *
 * 设计说明：disconnect() 必须等待活跃租约归还，避免远端文件操作期间关闭共享。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SMBConnectionManagerTest {

    @Test
    fun `disconnect under concurrent access closes current share only once`() {
        val manager = SMBConnectionManager()
        val diskShare = mockk<DiskShare>()
        every { diskShare.isConnected } returns true
        every { diskShare.close() } just runs
        manager.setPrivateField("diskShare", diskShare)

        val workers = 16
        val ready = CountDownLatch(workers)
        val start = CountDownLatch(1)
        val done = CountDownLatch(workers)
        val executor = Executors.newFixedThreadPool(workers)

        repeat(workers) { index ->
            executor.execute {
                ready.countDown()
                start.await()
                if (index % 2 == 0) {
                    manager.disconnect()
                } else {
                    manager.isConnected()
                }
                done.countDown()
            }
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS))
        start.countDown()
        assertTrue(done.await(2, TimeUnit.SECONDS))
        executor.shutdownNow()

        verify(exactly = 1) { diskShare.close() }
    }

    @Test
    fun `disconnect waits for active disk share lease before closing share`() {
        val manager = SMBConnectionManager()
        val diskShare = mockk<DiskShare>()
        every { diskShare.close() } just runs
        manager.setPrivateField("diskShare", diskShare)

        val lease = manager.acquireDiskShare()
        val disconnectDone = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            manager.disconnect()
            disconnectDone.countDown()
        }

        assertFalse(disconnectDone.await(100, TimeUnit.MILLISECONDS))
        lease.close()
        assertTrue(disconnectDone.await(2, TimeUnit.SECONDS))
        executor.shutdownNow()

        verify(exactly = 1) { diskShare.close() }
    }

    @Test
    fun `new disk share lease is rejected while disconnect waits for active lease`() {
        val manager = SMBConnectionManager()
        val diskShare = mockk<DiskShare>()
        every { diskShare.close() } just runs
        manager.setPrivateField("diskShare", diskShare)

        val activeLease = manager.acquireDiskShare()
        val disconnectDone = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            manager.disconnect()
            disconnectDone.countDown()
        }

        assertFalse(disconnectDone.await(100, TimeUnit.MILLISECONDS))
        assertTrue(waitUntilAcquireRejected(manager))

        activeLease.close()
        assertTrue(disconnectDone.await(2, TimeUnit.SECONDS))
        executor.shutdownNow()

        verify(exactly = 1) { diskShare.close() }
    }

    private fun waitUntilAcquireRejected(manager: SMBConnectionManager): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (System.nanoTime() < deadline) {
            try {
                manager.acquireDiskShare().close()
            } catch (e: java.io.IOException) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }

    private fun SMBConnectionManager.setPrivateField(name: String, value: Any?) {
        val field = SMBConnectionManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }
}
