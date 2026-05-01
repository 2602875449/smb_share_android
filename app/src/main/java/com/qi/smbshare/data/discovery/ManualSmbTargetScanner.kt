package com.qi.smbshare.data.discovery

import android.util.Log
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import java.net.InetAddress
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

private const val TAG = "ManualSmbTargetScanner"
private const val DEFAULT_SMB_PORT = 445
private const val CONNECT_TIMEOUT_MILLIS = 450
private const val NET_BIOS_TIMEOUT_MILLIS = 350

/**
 * 手动目标扫描器：对指定的 IP 或 CIDR 地址范围进行主动 SMB 探测。
 *
 * 与被动发现不同，此扫描器对 [SmbDiscoveryTarget.addresses] 中的
 * 每个地址依次进行 TCP 445 端口检测和 NetBIOS 名称查询。
 */
internal class ManualSmbTargetScanner(
    private val target: SmbDiscoveryTarget,
    private val tcpPortChecker: TcpPortChecker,
    private val netBiosNameServiceClient: NetBiosNameServiceClient
) : SmbHostDiscoverySource {

    override fun discover(): Flow<SmbDiscoveryHost> = channelFlow {
        if (target.addresses.size == 1) {
            val address = target.addresses.single()
            val reachable = tcpPortChecker.canConnect(
                address = address,
                port = DEFAULT_SMB_PORT,
                timeoutMillis = CONNECT_TIMEOUT_MILLIS
            )
            if (reachable) {
                val name = resolveNetBiosName(address)
                val hostAddress = address.hostAddress ?: address.toString()
                send(
                    SmbDiscoveryHost(
                        displayName = name ?: hostAddress,
                        address = hostAddress,
                        port = DEFAULT_SMB_PORT,
                        source = SmbDiscoverySource.MANUAL
                    )
                )
            } else {
                Log.d(TAG, "目标不可达: ${address.hostAddress}")
            }
        } else {
            val reachableAddresses = target.addresses.map { address ->
                async {
                    val reachable = tcpPortChecker.canConnect(
                        address = address,
                        port = DEFAULT_SMB_PORT,
                        timeoutMillis = CONNECT_TIMEOUT_MILLIS
                    )
                    if (reachable) address else null
                }
            }.awaitAll().filterNotNull()

            reachableAddresses.forEach { address ->
                val name = resolveNetBiosName(address)
                val hostAddress = address.hostAddress ?: address.toString()
                send(
                    SmbDiscoveryHost(
                        displayName = name ?: hostAddress,
                        address = hostAddress,
                        port = DEFAULT_SMB_PORT,
                        source = SmbDiscoverySource.MANUAL
                    )
                )
            }
        }
    }

    private suspend fun resolveNetBiosName(address: InetAddress): String? {
        return try {
            netBiosNameServiceClient.queryHostName(
                address = address,
                timeoutMillis = NET_BIOS_TIMEOUT_MILLIS
            )
        } catch (e: Exception) {
            Log.d(TAG, "NetBIOS 名称解析失败: ${address.hostAddress}", e)
            null
        }
    }
}
