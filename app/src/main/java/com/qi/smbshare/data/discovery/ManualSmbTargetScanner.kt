package com.qi.smbshare.data.discovery

import android.util.Log
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MANUAL_SCAN_TAG = "ManualSmbScanner"

internal class ManualSmbTargetScanner(
    private val target: SmbDiscoveryTarget,
    private val tcpPortChecker: TcpPortChecker,
    private val netBiosNameServiceClient: NetBiosNameServiceClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val smbPort: Int = 445,
    private val connectTimeoutMillis: Int = 450,
    private val nameTimeoutMillis: Int = 350,
    private val concurrency: Int = 24
) : SmbHostDiscoverySource {

    override fun discover(): Flow<SmbDiscoveryHost> = flow {
        val semaphore = Semaphore(concurrency)
        Log.d(
            MANUAL_SCAN_TAG,
            "开始手动 SMB 目标探测，输入=${target.input}, 候选主机数=${target.addresses.size}"
        )

        // 手动目标用于跨子网路由可达场景，不依赖 mDNS/NetBIOS 广播。
        val discoveredHosts = coroutineScope {
            target.addresses.map { address ->
                async(ioDispatcher) {
                    semaphore.withPermit {
                        if (!currentCoroutineContext().isActive) return@withPermit null
                        if (!tcpPortChecker.canConnect(address, smbPort, connectTimeoutMillis)) {
                            return@withPermit null
                        }
                        val hostName = netBiosNameServiceClient.queryHostName(address, nameTimeoutMillis)
                        val hostAddress = address.hostAddress ?: return@withPermit null
                        SmbDiscoveryHost(
                            displayName = hostName ?: hostAddress,
                            address = hostAddress,
                            port = smbPort,
                            source = SmbDiscoverySource.MANUAL
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

        discoveredHosts.forEach { emit(it) }
    }
}
