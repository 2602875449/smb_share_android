package com.qi.smbshare.data.discovery

import java.net.InetAddress
import java.nio.ByteBuffer

data class SmbDiscoveryTarget(
    val input: String,
    val addresses: List<InetAddress>
)

object SmbDiscoveryTargetParser {
    private const val MIN_CIDR_PREFIX = 24
    private const val MAX_CIDR_PREFIX = 32

    fun parse(input: String): Result<SmbDiscoveryTarget> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("发现目标不能为空"))
        }

        return runCatching {
            val addresses = when {
                "/" in trimmed -> parseCidr(trimmed)
                else -> listOf(parseIpv4(trimmed))
            }
            SmbDiscoveryTarget(input = trimmed, addresses = addresses.distinctBy { it.hostAddress })
        }
    }

    private fun parseCidr(input: String): List<InetAddress> {
        val parts = input.split('/')
        require(parts.size == 2) { "CIDR 格式无效" }
        val baseAddress = parseIpv4(parts[0])
        val prefixLength = parts[1].toIntOrNull()
            ?: throw IllegalArgumentException("CIDR 前缀无效")
        require(prefixLength in MIN_CIDR_PREFIX..MAX_CIDR_PREFIX) {
            "仅支持 /$MIN_CIDR_PREFIX 到 /$MAX_CIDR_PREFIX 的 IPv4 网段"
        }
        val hostCount = 1 shl (32 - prefixLength)
        val mask = -1 shl (32 - prefixLength)
        val network = baseAddress.toIpv4Int() and mask
        val hostRange = when (prefixLength) {
            MAX_CIDR_PREFIX -> listOf(baseAddress.toIpv4Int())
            31 -> (0 until hostCount).map { hostIndex -> network or hostIndex }
            else -> (1 until (hostCount - 1)).map { hostIndex -> network or hostIndex }
        }
        return hostRange.map { addressInt ->
            inetAddressFromInt(addressInt)
        }
    }

    private fun parseIpv4(input: String): InetAddress {
        val bytes = input.split('.').map { part ->
            val value = part.toIntOrNull()
                ?: throw IllegalArgumentException("IPv4 地址格式无效")
            require(value in 0..255) { "IPv4 地址格式无效" }
            value.toByte()
        }
        require(bytes.size == 4) { "IPv4 地址格式无效" }
        return InetAddress.getByAddress(bytes.toByteArray())
    }

    private fun InetAddress.toIpv4Int(): Int {
        return ByteBuffer.wrap(address).int
    }

    private fun inetAddressFromInt(value: Int): InetAddress {
        return InetAddress.getByAddress(
            ByteBuffer.allocate(4)
                .putInt(value)
                .array()
        )
    }
}
