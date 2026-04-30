package com.qi.smbshare.data.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val MDNS_TAG = "MdnsSmbDiscovery"
private const val SMB_SERVICE_TYPE = "_smb._tcp."

internal class AndroidMdnsSmbDiscoverySource(
    private val context: Context
) : SmbHostDiscoverySource {

    override fun discover(): Flow<SmbDiscoveryHost> = callbackFlow {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: error("系统不支持 mDNS 服务发现")
        val multicastLock = acquireMulticastLock()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(MDNS_TAG, "开始 mDNS SMB 发现: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.equals(SMB_SERVICE_TYPE, ignoreCase = true)) {
                    return
                }
                resolveService(nsdManager, serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(MDNS_TAG, "mDNS SMB 服务离线: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(MDNS_TAG, "mDNS SMB 发现已停止: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(MDNS_TAG, "启动 mDNS SMB 发现失败: $errorCode")
                close(IllegalStateException("启动 mDNS SMB 发现失败"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(MDNS_TAG, "停止 mDNS SMB 发现失败: $errorCode")
            }

            private fun resolveService(manager: NsdManager, serviceInfo: NsdServiceInfo) {
                val resolveListener = object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(MDNS_TAG, "解析 mDNS SMB 服务失败: ${serviceInfo.serviceName}, error=$errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val address = serviceInfo.host?.hostAddress ?: return
                        val port = if (serviceInfo.port > 0) serviceInfo.port else 445
                        val displayName = serviceInfo.serviceName.ifBlank { address }
                        trySend(
                            SmbDiscoveryHost(
                                displayName = displayName,
                                address = address,
                                port = port,
                                source = SmbDiscoverySource.MDNS
                            )
                        )
                    }
                }

                try {
                    manager.resolveService(serviceInfo, resolveListener)
                } catch (e: IllegalArgumentException) {
                    Log.w(MDNS_TAG, "mDNS SMB 服务解析请求被拒绝: ${serviceInfo.serviceName}", e)
                }
            }
        }

        nsdManager.discoverServices(
            SMB_SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener
        )

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: IllegalArgumentException) {
                Log.w(MDNS_TAG, "mDNS SMB 发现监听已释放", e)
            }
            if (multicastLock?.isHeld == true) {
                multicastLock.release()
            }
        }
    }

    private fun acquireMulticastLock(): WifiManager.MulticastLock? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        return wifiManager.createMulticastLock("smb-share-mdns-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
    }
}
