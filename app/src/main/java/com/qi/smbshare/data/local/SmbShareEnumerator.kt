package com.qi.smbshare.data.local

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ImpersonationLevel
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.NamedPipe
import com.hierynomus.smbj.share.PipeShare
import com.hierynomus.smbj.share.Share
import com.qi.smbshare.data.model.SMBConfig
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.EnumSet

private const val TAG = "SmbShareEnumerator"

/**
 * 通过 DCE/RPC SRVSVC 协议枚举服务器磁盘共享。
 * 流程：连接 IPC$ -> 打开 srvsvc 命名管道 -> Bind -> NetrShareEnum。
 */
internal class SmbShareEnumerator(private val client: SMBClient) {

    @Throws(IOException::class)
    fun listShares(config: SMBConfig): List<String> {
        var conn: Connection? = null
        var sess: Session? = null
        var ipcShare: Share? = null
        var pipe: NamedPipe? = null
        return try {
            conn = client.connect(config.serverAddress, config.port)
            val authContext = if (config.isAnonymous) {
                AuthenticationContext("Guest", "".toCharArray(), null)
            } else {
                AuthenticationContext(config.username, config.password.toCharArray(), null)
            }
            sess = conn.authenticate(authContext)
            ipcShare = sess.connectShare("IPC\$")
            if (ipcShare !is PipeShare) throw IOException("IPC\$ 返回了非管道类型的共享")
            pipe = ipcShare.open(
                "srvsvc",
                SMB2ImpersonationLevel.Impersonation,
                EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE),
                EnumSet.noneOf(FileAttributes::class.java),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.noneOf(SMB2CreateOptions::class.java)
            )
            val bindAck = pipe.transact(BIND_REQUEST)
            if (!isBindAccepted(bindAck)) throw IOException("SRVSVC Bind 请求被服务器拒绝")
            val response = pipe.transact(NET_SHARE_ENUM_REQUEST)
            parseShareEnumResponse(response)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException("枚举共享失败: ${e.message}", e)
        } finally {
            try { pipe?.close() } catch (_: Exception) {}
            try { ipcShare?.close() } catch (_: Exception) {}
            try { sess?.close() } catch (_: Exception) {}
            try { conn?.close() } catch (_: Exception) {}
        }
    }

    private fun isBindAccepted(ack: ByteArray): Boolean {
        if (ack.size < 20) return false
        val ptype = ack[2].toInt() and 0xFF
        Log.d(TAG, "Bind 响应 PTYPE=$ptype 长度=${ack.size}")
        return ptype == 0x0C
    }

    private fun parseShareEnumResponse(data: ByteArray): List<String> {
        if (data.size < 28) { Log.w(TAG, "响应长度不足: ${data.size}"); return emptyList() }
        return try {
            val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(24) // 跳过 DCE/RPC 响应头（16 标准头 + 8 响应专用头）
            val level = buf.int
            buf.int // union discriminant
            val containerPtr = buf.int
            if (level != 1 || containerPtr == 0) return emptyList()
            val entriesRead = buf.int
            val bufferPtr = buf.int
            Log.d(TAG, "服务器共享条目数: $entriesRead")
            if (entriesRead == 0 || bufferPtr == 0) return emptyList()
            buf.int // 数组 max_count
            data class RawEntry(val namePtr: Int, val type: Int, val remarkPtr: Int)
            val rawEntries = Array(entriesRead) { RawEntry(buf.int, buf.int, buf.int) }
            val result = mutableListOf<String>()
            for (entry in rawEntries) {
                val name = if (entry.namePtr != 0) readNdrString(buf) else ""
                if (entry.remarkPtr != 0) readNdrString(buf)
                // type == 0 表示 STYPE_DISKTREE（普通磁盘共享）
                if (entry.type == 0 && name.isNotEmpty() && !name.endsWith("$")) {
                    result.add(name)
                }
            }
            Log.d(TAG, "可用磁盘共享: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "解析 NetrShareEnum 响应失败", e)
            emptyList()
        }
    }

    /**
     * 读取 NDR 一致可变字符串：max_count(4) + offset(4) + actual_count(4) + data(n*2) + 对齐填充
     */
    private fun readNdrString(buf: ByteBuffer): String {
        val maxCount = buf.int
        buf.int // offset
        val actualCount = buf.int
        if (actualCount <= 0 || actualCount > 2048) {
            val skip = (actualCount.coerceIn(0, 2048)) * 2
            if (skip > 0 && buf.remaining() >= skip) buf.position(buf.position() + skip)
            return ""
        }
        val bytes = ByteArray(actualCount * 2)
        buf.get(bytes)
        val totalBytes = 12 + actualCount * 2
        val padding = (4 - (totalBytes % 4)) % 4
        if (padding > 0 && buf.remaining() >= padding) buf.position(buf.position() + padding)
        return String(bytes, Charsets.UTF_16LE).trimEnd('\u0000')
    }

    companion object {
        // DCE/RPC Bind 请求（72 字节）：声明使用 SRVSVC 接口 + NDR 传输语法
        private val BIND_REQUEST: ByteArray = byteArrayOf(
            0x05, 0x00, 0x0B, 0x03, 0x10.toByte(), 0x00, 0x00, 0x00,
            0x48, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00,
            0xB8.toByte(), 0x10, 0xB8.toByte(), 0x10, 0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00,
            // SRVSVC {4b324fc8-1670-01d3-1278-5a47bf6ee188} v3.0
            0xC8.toByte(), 0x4F, 0x32, 0x4B, 0x70, 0x16, 0xD3.toByte(), 0x01,
            0x12, 0x78, 0x5A, 0x47, 0xBF.toByte(), 0x6E, 0xE1.toByte(), 0x88.toByte(),
            0x03, 0x00, 0x00, 0x00,
            // NDR {8a885d04-1ceb-11c9-9fe8-08002b104860} v2.0
            0x04, 0x5D, 0x88.toByte(), 0x8A.toByte(), 0xEB.toByte(), 0x1C, 0xC9.toByte(), 0x11,
            0x9F.toByte(), 0xE8.toByte(), 0x08, 0x00, 0x2B, 0x10, 0x48, 0x60,
            0x02, 0x00, 0x00, 0x00
        )

        // NetrShareEnum 请求（56 字节）：Level=1, ServerName=NULL, PreferredMaxLen=-1
        private val NET_SHARE_ENUM_REQUEST: ByteArray = byteArrayOf(
            0x05, 0x00, 0x00, 0x03, 0x10.toByte(), 0x00, 0x00, 0x00,
            0x38, 0x00, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00,
            0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x0F, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x00,
            0x04, 0x00, 0x02, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00,
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0x00, 0x00, 0x00, 0x00
        )
    }
}
