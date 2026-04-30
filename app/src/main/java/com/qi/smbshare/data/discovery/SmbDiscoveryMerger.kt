package com.qi.smbshare.data.discovery

import com.qi.smbshare.data.model.SmbDiscoveryHost

class SmbDiscoveryMerger {
    private val hostsByAddress = linkedMapOf<String, SmbDiscoveryHost>()

    fun add(host: SmbDiscoveryHost): List<SmbDiscoveryHost> {
        val existing = hostsByAddress[host.address]
        hostsByAddress[host.address] = if (existing == null) {
            host
        } else {
            merge(existing, host)
        }
        return hostsByAddress.values.sortedWith(compareBy({ it.addressToSortKey() }, { it.port }))
    }

    fun snapshot(): List<SmbDiscoveryHost> {
        return hostsByAddress.values.sortedWith(compareBy({ it.addressToSortKey() }, { it.port }))
    }

    private fun merge(existing: SmbDiscoveryHost, incoming: SmbDiscoveryHost): SmbDiscoveryHost {
        val displayName = when {
            existing.displayName.isBlank() || existing.displayName == existing.address -> incoming.displayName
            incoming.displayName.isBlank() || incoming.displayName == incoming.address -> existing.displayName
            incoming.displayName.length < existing.displayName.length -> incoming.displayName
            else -> existing.displayName
        }
        return existing.copy(
            displayName = displayName,
            port = if (existing.port != 445 && incoming.port == 445) existing.port else incoming.port,
            sources = existing.sources + incoming.sources + incoming.source
        )
    }

    private fun SmbDiscoveryHost.addressToSortKey(): String {
        val ipv4Parts = address.split('.').map { it.toIntOrNull() }
        if (ipv4Parts.size != 4 || ipv4Parts.any { it == null }) {
            return address
        }
        return ipv4Parts.joinToString(".") { part ->
            part!!.coerceIn(0, 255).toString().padStart(3, '0')
        }
    }
}
