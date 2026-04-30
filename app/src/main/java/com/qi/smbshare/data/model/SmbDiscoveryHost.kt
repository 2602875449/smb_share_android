package com.qi.smbshare.data.model

data class SmbDiscoveryHost(
    val displayName: String,
    val address: String,
    val port: Int = 445,
    val source: SmbDiscoverySource,
    val sources: Set<SmbDiscoverySource> = setOf(source)
)

enum class SmbDiscoverySource {
    MDNS,
    NETBIOS,
    MANUAL
}
