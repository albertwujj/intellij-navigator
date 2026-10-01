package com.claudecode.navigator.transport

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.charset.CharacterCodingException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SocketTransportTest {
    private fun <T> connection(action: (Socket, Socket) -> T): T =
        SocketTransport.bind(0).use { listener ->
            Socket("127.0.0.1", listener.localPort).use { client ->
                listener.accept().use { accepted -> action(client, accepted) }
            }
        }

    @Test
    fun bindsOnlyToIpv4Loopback() {
        SocketTransport.bind(0).use {
            assertEquals("127.0.0.1", it.inetAddress.hostAddress)
            assertTrue(it.inetAddress.isLoopbackAddress)
            assertFalse(it.inetAddress.isAnyLocalAddress)
        }
    }

    @Test
    fun acceptsUtf8AndCrLfAndOnlyReadsFirstRequest(): Unit = connection { client, accepted ->
        client.getOutputStream().write("{\"text\":\"你好 🎉\"}\r\n{\"ignored\":true}\n".toByteArray())
        assertEquals("{\"text\":\"你好 🎉\"}", SocketTransport.readRequest(accepted))
    }

    @Test
    fun rejectsHttpBeforeReadingHeadersOrBody(): Unit = connection { client, accepted ->
        client.getOutputStream().write("POST / HTTP/1.1\r\n\r\n{\"type\":\"caret\"}\n".toByteArray())
        assertThrows(IOException::class.java) { SocketTransport.readRequest(accepted) }
    }

    @Test
    fun requiresNewlineBeforeDispatch(): Unit = connection { client, accepted ->
        client.getOutputStream().write("{\"type\":\"caret\"}".toByteArray())
        client.shutdownOutput()
        assertThrows(IOException::class.java) { SocketTransport.readRequest(accepted) }
    }

    @Test
    fun rejectsOversizedRequestsIncludingMultibyteText(): Unit = connection { client, accepted ->
        client.getOutputStream().write("{\"text\":\"你好你好\"}\n".toByteArray())
        assertThrows(IOException::class.java) { SocketTransport.readRequest(accepted, maxBytes = 20) }
    }

    @Test
    fun rejectsInvalidUtf8(): Unit = connection { client, accepted ->
        client.getOutputStream().write(byteArrayOf('{'.code.toByte(), 0xff.toByte(), '}'.code.toByte(), '\n'.code.toByte()))
        assertThrows(CharacterCodingException::class.java) { SocketTransport.readRequest(accepted) }
    }

    @Test
    fun timesOutIdleClients(): Unit = connection { _, accepted ->
        assertThrows(SocketTimeoutException::class.java) { SocketTransport.readRequest(accepted, timeoutMs = 100) }
    }

    @Test
    fun trickledBytesCannotExtendRequestDeadline(): Unit = connection { client, accepted ->
        client.getOutputStream().write('{'.code)
        val writer = Executors.newSingleThreadScheduledExecutor()
        try {
            writer.scheduleAtFixedRate({ runCatching { client.getOutputStream().write(' '.code) } }, 20, 20, TimeUnit.MILLISECONDS)
            val start = System.nanoTime()
            assertThrows(SocketTimeoutException::class.java) { SocketTransport.readRequest(accepted, timeoutMs = 200) }
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500)
        } finally {
            writer.shutdownNow()
        }
    }

    @Test
    fun capsConnectionsAndReleasesCapacity() {
        val clients = ClientConnections(limit = 1)
        val first = Socket()
        val second = Socket()
        try {
            assertTrue(clients.add(first))
            assertFalse(clients.add(second))
            assertTrue(second.isClosed)
            clients.remove(first)
            Socket().use { replacement -> assertTrue(clients.add(replacement)) }
        } finally {
            first.close()
            clients.close()
        }
    }

    @Test
    fun shutdownClosesActiveConnectionsAndRejectsLaterOnes(): Unit = connection { _, accepted ->
        val clients = ClientConnections()
        assertTrue(clients.add(accepted))
        clients.close()
        assertTrue(accepted.isClosed)
        Socket().use { next ->
            assertFalse(clients.add(next))
            assertTrue(next.isClosed)
        }
    }
}
