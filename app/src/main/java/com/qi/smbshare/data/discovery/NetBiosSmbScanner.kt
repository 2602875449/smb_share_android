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

private const val NETBIOS_TAG = "NetBiosSmbScanner"

internal class NetBiosSmbScanner(
    private val localNetworkProvider: LocalNetworkProvider,
    private val tcpPortChecker: TcpPortChecker,
    private val netBiosNameServiceClient: NetBiosNameServiceClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val smbPort: Int = 445,
    private val connectTimeoutMillis: Int = 250,
    private val nameTimeoutMillis: Int = 350,
    private val concurrency: Int = 24
) : SmbHostDiscoverySource {

    override fun discover(): Flow<SmbDiscoveryHost> = flow {
        val subnet = localNetworkProvider.getIpv4Subnet()
        if (subnet == null) {
            Log.w(NETBIOS_TAG, "无法确定局域网 IPv4 网段，跳过 NetBIOS/445 扫描")
            return@flow
        }

        val semaphore = Semaphore(concurrency)
        val hosts = subnet.hosts()
        Log.d(NETBIOS_TAG, "开始扫描 SMB 445 端口，候选主机数=${hosts.size}")
        val discoveredHosts = coroutineScope {
            hosts.map { address ->
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
                            source = SmbDiscoverySource.NETBIOS
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }

        discoveredHosts.forEach { emit(it) }
    }
}
