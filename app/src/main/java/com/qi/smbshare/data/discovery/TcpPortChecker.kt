package com.qi.smbshare.data.discovery

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface TcpPortChecker {
    suspend fun canConnect(address: InetAddress, port: Int, timeoutMillis: Int): Boolean
}

internal class SocketTcpPortChecker(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : TcpPortChecker {
    override suspend fun canConnect(address: InetAddress, port: Int, timeoutMillis: Int): Boolean {
        return withContext(ioDispatcher) {
            runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMillis)
                }
            }.isSuccess
        }
    }
}
