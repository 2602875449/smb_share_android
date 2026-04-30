package com.qi.smbshare.data.discovery

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface UdpDatagramClient {
    suspend fun query(
        address: InetAddress,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int
    ): ByteArray?
}

internal class DatagramUdpClient(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : UdpDatagramClient {
    override suspend fun query(
        address: InetAddress,
        port: Int,
        payload: ByteArray,
        timeoutMillis: Int
    ): ByteArray? = withContext(ioDispatcher) {
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMillis
                val request = DatagramPacket(payload, payload.size, address, port)
                socket.send(request)

                val buffer = ByteArray(1024)
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                buffer.copyOf(response.length)
            }
        }.getOrNull()
    }
}

internal class NetBiosNameServiceClient(
    private val udpDatagramClient: UdpDatagramClient = DatagramUdpClient(),
    private val transactionIdProvider: () -> Int = { Random.nextInt(0, 0xFFFF) }
) {
    suspend fun queryHostName(address: InetAddress, timeoutMillis: Int = 350): String? {
        val transactionId = transactionIdProvider()
        val request = NetBiosPacketCodec.createNodeStatusRequest(transactionId)
        val response = udpDatagramClient.query(
            address = address,
            port = 137,
            payload = request,
            timeoutMillis = timeoutMillis
        ) ?: return null

        return NetBiosPacketCodec.parseNodeStatusResponse(response, transactionId)
    }
}
