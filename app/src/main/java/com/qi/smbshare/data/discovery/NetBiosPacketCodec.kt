package com.qi.smbshare.data.discovery

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

internal object NetBiosPacketCodec {
    private const val TYPE_NBSTAT = 0x0021
    private const val CLASS_IN = 0x0001
    private const val HEADER_LENGTH = 12

    fun createNodeStatusRequest(transactionId: Int): ByteArray {
        val output = ByteArrayOutputStream()
        output.writeShort(transactionId)
        output.writeShort(0x0000)
        output.writeShort(0x0001)
        output.writeShort(0x0000)
        output.writeShort(0x0000)
        output.writeShort(0x0000)
        output.write(encodeNetBiosName("*", 0x00))
        output.writeShort(TYPE_NBSTAT)
        output.writeShort(CLASS_IN)
        return output.toByteArray()
    }

    fun parseNodeStatusResponse(data: ByteArray, expectedTransactionId: Int): String? {
        if (data.size < HEADER_LENGTH) return null
        val buffer = ByteBuffer.wrap(data)
        val transactionId = buffer.unsignedShort()
        if (transactionId != expectedTransactionId) return null

        val flags = buffer.unsignedShort()
        if ((flags and 0x8000) == 0) return null

        val questionCount = buffer.unsignedShort()
        val answerCount = buffer.unsignedShort()
        buffer.unsignedShort()
        buffer.unsignedShort()

        repeat(questionCount) {
            if (!skipDnsName(buffer, data.size) || buffer.remaining() < 4) return null
            buffer.position(buffer.position() + 4)
        }

        repeat(answerCount) {
            if (!skipDnsName(buffer, data.size) || buffer.remaining() < 10) return null
            val type = buffer.unsignedShort()
            buffer.unsignedShort()
            buffer.int
            val dataLength = buffer.unsignedShort()
            if (buffer.remaining() < dataLength) return null

            if (type == TYPE_NBSTAT) {
                val limit = buffer.position() + dataLength
                if (buffer.position() >= limit) return null
                val nameCount = buffer.get().toInt() and 0xFF
                return parseNodeNames(buffer, limit, nameCount)
            } else {
                buffer.position(buffer.position() + dataLength)
            }
        }

        return null
    }

    private fun parseNodeNames(buffer: ByteBuffer, limit: Int, nameCount: Int): String? {
        val names = mutableListOf<Pair<String, Int>>()
        repeat(nameCount) {
            if (buffer.position() + 18 > limit) return null
            val nameBytes = ByteArray(15)
            buffer.get(nameBytes)
            val suffix = buffer.get().toInt() and 0xFF
            val flags = buffer.unsignedShort()
            val isGroup = (flags and 0x8000) != 0
            val name = String(nameBytes, StandardCharsets.US_ASCII).trim()
            if (!isGroup && name.isNotBlank() && name != "*") {
                names += name to suffix
            }
        }
        return names.firstOrNull { it.second == 0x00 }?.first
            ?: names.firstOrNull { it.second == 0x20 }?.first
            ?: names.firstOrNull()?.first
    }

    private fun encodeNetBiosName(name: String, suffix: Int): ByteArray {
        val padded = ByteArray(16) { 0x20 }
        val source = name.take(15).toByteArray(StandardCharsets.US_ASCII)
        source.copyInto(padded, endIndex = source.size)
        padded[15] = suffix.toByte()

        val output = ByteArrayOutputStream()
        output.write(32)
        padded.forEach { byte ->
            val value = byte.toInt() and 0xFF
            output.write(((value shr 4) and 0x0F) + 'A'.code)
            output.write((value and 0x0F) + 'A'.code)
        }
        output.write(0)
        return output.toByteArray()
    }

    private fun skipDnsName(buffer: ByteBuffer, packetSize: Int): Boolean {
        while (buffer.hasRemaining()) {
            val length = buffer.get().toInt() and 0xFF
            when {
                length == 0 -> return true
                (length and 0xC0) == 0xC0 -> {
                    if (!buffer.hasRemaining()) return false
                    buffer.get()
                    return true
                }
                length > 63 || buffer.position() + length > packetSize -> return false
                else -> buffer.position(buffer.position() + length)
            }
        }
        return false
    }

    private fun ByteArrayOutputStream.writeShort(value: Int) {
        write((value shr 8) and 0xFF)
        write(value and 0xFF)
    }

    private fun ByteBuffer.unsignedShort(): Int {
        return short.toInt() and 0xFFFF
    }
}
