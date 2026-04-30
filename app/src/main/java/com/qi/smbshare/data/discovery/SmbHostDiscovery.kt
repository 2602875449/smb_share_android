package com.qi.smbshare.data.discovery

import android.content.Context
import android.util.Log
import com.qi.smbshare.data.model.SmbDiscoveryHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AndroidSmbDiscovery"

interface SmbHostDiscovery {
    fun discover(): Flow<List<SmbDiscoveryHost>>
    fun discover(target: SmbDiscoveryTarget): Flow<List<SmbDiscoveryHost>>
}

internal interface SmbHostDiscoverySource {
    fun discover(): Flow<SmbDiscoveryHost>
}

class AndroidSmbHostDiscovery internal constructor(
    private val scanDurationMillis: Long,
    private val sources: List<SmbHostDiscoverySource>
) : SmbHostDiscovery {
    constructor(
        context: Context,
        scanDurationMillis: Long = 8_000L
    ) : this(
        scanDurationMillis = scanDurationMillis,
        sources = buildDefaultSources(context.applicationContext)
    )

    override fun discover(): Flow<List<SmbDiscoveryHost>> = channelFlow {
        collectSources(sources)
    }.onStart {
        emit(emptyList())
    }

    override fun discover(target: SmbDiscoveryTarget): Flow<List<SmbDiscoveryHost>> = channelFlow {
        collectSources(
            listOf(
                ManualSmbTargetScanner(
                    target = target,
                    tcpPortChecker = SocketTcpPortChecker(),
                    netBiosNameServiceClient = NetBiosNameServiceClient()
                )
            )
        )
    }.onStart {
        emit(emptyList())
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<List<SmbDiscoveryHost>>.collectSources(
        discoverySources: List<SmbHostDiscoverySource>
    ) {
        val merger = SmbDiscoveryMerger()
        val mutex = Mutex()
        val sourceJobs = discoverySources.map { source ->
            launch {
                try {
                    source.discover().collect { host ->
                        val mergedHosts = mutex.withLock { merger.add(host) }
                        trySend(mergedHosts)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "SMB 主机发现来源失败: ${source.javaClass.simpleName}", e)
                }
            }
        }
        val timeoutJob = launch {
            delay(scanDurationMillis)
            sourceJobs.forEach { it.cancel() }
            close()
        }
        val completionJob = launch {
            sourceJobs.joinAll()
            close()
        }

        awaitClose {
            timeoutJob.cancel()
            completionJob.cancel()
            sourceJobs.forEach { it.cancel() }
        }
    }

    private companion object {
        fun buildDefaultSources(context: Context): List<SmbHostDiscoverySource> {
            return listOf(
                AndroidMdnsSmbDiscoverySource(context),
                NetBiosSmbScanner(
                    localNetworkProvider = AndroidLocalNetworkProvider(context),
                    tcpPortChecker = SocketTcpPortChecker(),
                    netBiosNameServiceClient = NetBiosNameServiceClient()
                )
            )
        }
    }
}
