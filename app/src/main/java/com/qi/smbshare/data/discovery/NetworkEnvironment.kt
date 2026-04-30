package com.qi.smbshare.data.discovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.nio.ByteBuffer

internal interface LocalNetworkProvider {
    fun getIpv4Subnet(): Ipv4Subnet?
}

internal data class Ipv4Subnet(
    val localAddress: Inet4Address,
    val prefixLength: Int
) {
    fun hosts(): List<InetAddress> {
        val localAddressInt = ByteBuffer.wrap(localAddress.address).int
        val scanPrefixLength = 24
        val mask = -1 shl (32 - scanPrefixLength)
        val network = localAddressInt and mask
        val localHostAddress = localAddress.hostAddress

        return (1..254)
            .map { hostIndex ->
                InetAddress.getByAddress(
                    ByteBuffer.allocate(4)
                        .putInt(network or hostIndex)
                        .array()
                )
            }
            .filter { it.hostAddress != localHostAddress }
    }
}

internal class AndroidLocalNetworkProvider(
    private val context: Context
) : LocalNetworkProvider {
    override fun getIpv4Subnet(): Ipv4Subnet? {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        val activeNetwork = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return null
        if (
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return null
        }
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork) ?: return null
        val linkAddress = linkProperties.linkAddresses.firstOrNull {
            it.address is Inet4Address && !it.address.isLoopbackAddress
        } ?: return null

        return Ipv4Subnet(
            localAddress = linkAddress.address as Inet4Address,
            prefixLength = linkAddress.prefixLength
        )
    }
}
