package com.claudecode.navigator.server

import com.claudecode.navigator.transport.ClientConnections
import com.claudecode.navigator.transport.SocketTransport
import com.claudecode.navigator.model.NavigationResponse
import com.claudecode.navigator.model.NavigationResponse.Companion.toJson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

class TcpServer(
    private val project: Project,
    private val port: Int = DEFAULT_PORT
) {
    private val logger = Logger.getInstance(TcpServer::class.java)
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val clients = ClientConnections()
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestHandler = RequestHandler(project)

    @Synchronized
    fun start() {
        if (isRunning.getAndSet(true)) return
        try {
            // Bind before launching the accept job so stop() cannot race with
            // creation of a listener that would otherwise survive shutdown.
            val listener = SocketTransport.bind(port)
            serverSocket = listener
            logger.info("Navigator server listening on 127.0.0.1:${listener.localPort}")
            serverJob = scope.launch {
                try {
                    while (isActive && isRunning.get()) {
                        val client = listener.accept()
                        if (!clients.add(client)) continue
                        launch {
                            try {
                                handleClient(client)
                            } finally {
                                clients.remove(client)
                                runCatching { client.close() }
                            }
                        }
                    }
                } catch (e: SocketException) {
                    if (isRunning.get()) logger.warn("Navigator socket closed unexpectedly", e)
                } finally {
                    listener.close()
                    clients.close()
                    isRunning.set(false)
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to start navigator server on port $port", e)
            isRunning.set(false)
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            var writer: PrintWriter? = null
            try {
                writer = PrintWriter(client.getOutputStream(), true, Charsets.UTF_8)

                val line = SocketTransport.readRequest(client)
                if (line == null) {
                    logger.warn("Received empty request on port $port")
                    writer.println(NavigationResponse(status = "error", message = "empty request").toJson())
                    return
                }

                val response = requestHandler.handle(line)
                writer.println(response.toJson())
                logger.debug("Navigation response status: ${response.status}")
            } catch (e: Exception) {
                if (isRunning.get()) logger.debug("Rejected navigator connection (${e.javaClass.simpleName})")
                try {
                    writer?.println(NavigationResponse(status = "error", message = "invalid or failed request").toJson())
                } catch (writeError: Exception) {
                    logger.warn("Failed to send error response to client", writeError)
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        logger.info("Stopping Navigator TCP server on port $port")

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logger.warn("Error closing server socket", e)
        }

        clients.close()
        serverJob?.cancel()
        scope.cancel()

        serverSocket = null
        serverJob = null
    }

    fun isRunning(): Boolean = isRunning.get()

    companion object {
        const val DEFAULT_PORT = 8765
    }
}
