package com.qi.smbshare.data.discovery

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NetBiosPacketCodecTest {

    @Test
    fun `createNodeStatusRequest 生成 NBSTAT 查询`() {
        val request = NetBiosPacketCodec.createNodeStatusRequest(0x1234)

        assertEquals(0x12, request[0].toInt() and 0xFF)
        assertEquals(0x34, request[1].toInt() and 0xFF)
        assertEquals(32, request[12].toInt())
        val qTypeOffset = request.size - 4
        assertEquals(0x00, request[qTypeOffset].toInt() and 0xFF)
        assertEquals(0x21, request[qTypeOffset + 1].toInt() and 0xFF)
    }

    @Test
    fun `parseNodeStatusResponse 解析唯一 NetBIOS 主机名`() {
        val response = buildNodeStatusResponse(
            transactionId = 0x4567,
            names = listOf(
                NodeName("WORKGROUP", 0x00, isGroup = true),
                NodeName("WIN-PC", 0x00, isGroup = false),
                NodeName("WIN-PC", 0x20, isGroup = false)
            )
        )

        val hostName = NetBiosPacketCodec.parseNodeStatusResponse(response, 0x4567)

        assertEquals("WIN-PC", hostName)
    }

    @Test
    fun `parseNodeStatusResponse 交易 ID 不匹配时忽略响应`() {
        val response = buildNodeStatusResponse(
            transactionId = 0x4567,
            names = listOf(NodeName("WIN-PC", 0x00, isGroup = false))
        )

        assertNull(NetBiosPacketCodec.parseNodeStatusResponse(response, 0x1111))
    }

    @Test
    fun `parseNodeStatusResponse 畸形响应返回 null`() {
        assertNull(NetBiosPacketCodec.parseNodeStatusResponse(byteArrayOf(0x00, 0x01), 0x0001))
        assertNotNull(
            NetBiosPacketCodec.parseNodeStatusResponse(
                buildNodeStatusResponse(
                    transactionId = 0x0001,
                    names = listOf(NodeName("NAS", 0x20, isGroup = false))
                ),
                0x0001
            )
        )
    }

    private fun buildNodeStatusResponse(
        transactionId: Int,
        names: List<NodeName>
    ): ByteArray {
        val rData = ByteArrayOutputStream()
        rData.write(names.size)
        names.forEach { name ->
            val padded = name.name.padEnd(15, ' ').take(15).toByteArray(Charsets.US_ASCII)
            rData.write(padded)
            rData.write(name.suffix)
            rData.write(if (name.isGroup) 0x80 else 0x00)
            rData.write(0x00)
        }
        val rDataBytes = rData.toByteArray()

        return ByteArrayOutputStream().apply {
            writeShort(transactionId)
            writeShort(0x8500)
            writeShort(0x0000)
            writeShort(0x0001)
            writeShort(0x0000)
            writeShort(0x0000)
            write(0xC0)
            write(0x0C)
            writeShort(0x0021)
            writeShort(0x0001)
            writeInt(0)
            writeShort(rDataBytes.size)
            write(rDataBytes)
        }.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write((value shr 24) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private data class NodeName(
        val name: String,
        val suffix: Int,
        val isGroup: Boolean
    )
}
