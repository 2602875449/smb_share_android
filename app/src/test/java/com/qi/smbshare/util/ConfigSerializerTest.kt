package com.qi.smbshare.util

import com.qi.smbshare.data.model.SMBConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 验证 SMBConfig 的 JSON 序列化与反序列化的完整性和容错能力。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ConfigSerializerTest {

    private val sampleConfig = SMBConfig(
        id = "test-id-123",
        name = "测试服务器",
        serverAddress = "192.168.1.100",
        port = 445,
        shareName = "share",
        username = "testuser",
        password = "testpass",
        isAnonymous = false
    )

    @Test
    fun `toJsonString 包含所有字段值`() {
        val json = sampleConfig.toJsonString()
        assertTrue(json.contains("test-id-123"))
        assertTrue(json.contains("192.168.1.100"))
        assertTrue(json.contains("share"))
        assertTrue(json.contains("testuser"))
    }

    @Test
    fun `toSMBConfigOrNull 完整还原所有字段`() {
        val json = sampleConfig.toJsonString()
        val restored = json.toSMBConfigOrNull()

        assertNotNull(restored)
        assertEquals(sampleConfig.id, restored!!.id)
        assertEquals(sampleConfig.name, restored.name)
        assertEquals(sampleConfig.serverAddress, restored.serverAddress)
        assertEquals(sampleConfig.port, restored.port)
        assertEquals(sampleConfig.shareName, restored.shareName)
        assertEquals(sampleConfig.username, restored.username)
        assertEquals(sampleConfig.password, restored.password)
        assertEquals(sampleConfig.isAnonymous, restored.isAnonymous)
    }

    @Test
    fun `toSMBConfigOrNull 对非法 JSON 字符串返回 null`() {
        assertNull("invalid json".toSMBConfigOrNull())
        assertNull("{broken".toSMBConfigOrNull())
        assertNull("".toSMBConfigOrNull())
    }

    @Test
    fun `匿名配置序列化后 isAnonymous 为 true`() {
        val anonymousConfig = SMBConfig(
            serverAddress = "192.168.1.200",
            shareName = "public",
            isAnonymous = true
        )
        val restored = anonymousConfig.toJsonString().toSMBConfigOrNull()

        assertNotNull(restored)
        assertTrue(restored!!.isAnonymous)
    }

    @Test
    fun `自定义端口序列化后正确还原`() {
        val customPort = sampleConfig.copy(port = 139)
        val restored = customPort.toJsonString().toSMBConfigOrNull()

        assertNotNull(restored)
        assertEquals(139, restored!!.port)
    }

    @Test
    fun `toSMBConfigOrNull 缺少非必填字段时使用默认值`() {
        val minimalJson = """{"serverAddress":"10.0.0.1","shareName":"data"}"""
        val config = minimalJson.toSMBConfigOrNull()

        assertNotNull(config)
        assertEquals("10.0.0.1", config!!.serverAddress)
        assertEquals("data", config.shareName)
        assertEquals(445, config.port)
        assertEquals("", config.username)
        assertEquals("", config.password)
        assertEquals(false, config.isAnonymous)
    }
}
