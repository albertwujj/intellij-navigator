package com.claudecode.navigator.transport

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/** Shared by both independently packaged plugins. The protocol trusts local clients. */
internal object SocketTransport {
    const val MAX_REQUEST_BYTES = 64 * 1024
    const val REQUEST_TIMEOUT_MS = 5000

    fun bind(port: Int): ServerSocket =
        ServerSocket(port, 16, InetAddress.getByName("127.0.0.1"))

    // A deadline, not just an idle timeout: a trickle of bytes cannot keep a
    // connection occupied forever. Count bytes before UTF-8 decoding.
    fun readRequest(
        socket: Socket,
        maxBytes: Int = MAX_REQUEST_BYTES,
        timeoutMs: Int = REQUEST_TIMEOUT_MS,
    ): String? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        val input = socket.getInputStream().buffered()
        val bytes = ByteArrayOutputStream()
        var objectStarted = false
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw SocketTimeoutException("request deadline exceeded")
            socket.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceAtLeast(1).toInt()
            val next = input.read()
            if (next < 0) {
                if (bytes.size() == 0) return null
                throw EOFException("request must end with a newline")
            }
            if (next == '\n'.code) break
            if (bytes.size() >= maxBytes) throw IOException("request too large")
            if (!objectStarted && next !in listOf(' '.code, '\t'.code, '\r'.code)) {
                // Reject HTTP/WebSocket handshakes before they reach the IDE.
                if (next != '{'.code) throw IOException("expected a JSON object")
                objectStarted = true
            }
            bytes.write(next)
        }
        return Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString().trimEnd('\r')
    }
}

/** Bounds active handlers, and closes their sockets on plugin/project shutdown. */
internal class ClientConnections(private val limit: Int = 16) {
    private val sockets = mutableSetOf<Socket>()
    private var closed = false

    @Synchronized
    fun add(socket: Socket): Boolean {
        if (closed || sockets.size >= limit) {
            socket.close()
            return false
        }
        sockets.add(socket)
        return true
    }

    @Synchronized
    fun remove(socket: Socket) {
        sockets.remove(socket)
    }

    @Synchronized
    fun close() {
        closed = true
        sockets.forEach { runCatching { it.close() } }
        sockets.clear()
    }
}
