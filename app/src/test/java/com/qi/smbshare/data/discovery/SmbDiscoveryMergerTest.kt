package com.qi.smbshare.data.discovery

import com.qi.smbshare.data.model.SmbDiscoveryHost
import com.qi.smbshare.data.model.SmbDiscoverySource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbDiscoveryMergerTest {

    @Test
    fun `add 按 IP 合并 mDNS 和 NetBIOS 结果`() {
        val merger = SmbDiscoveryMerger()

        merger.add(
            SmbDiscoveryHost(
                displayName = "192.168.1.20",
                address = "192.168.1.20",
                port = 445,
                source = SmbDiscoverySource.NETBIOS
            )
        )
        val merged = merger.add(
            SmbDiscoveryHost(
                displayName = "Mac Mini",
                address = "192.168.1.20",
                port = 445,
                source = SmbDiscoverySource.MDNS
            )
        )

        assertEquals(1, merged.size)
        assertEquals("Mac Mini", merged.first().displayName)
        assertTrue(merged.first().sources.contains(SmbDiscoverySource.MDNS))
        assertTrue(merged.first().sources.contains(SmbDiscoverySource.NETBIOS))
    }
}
