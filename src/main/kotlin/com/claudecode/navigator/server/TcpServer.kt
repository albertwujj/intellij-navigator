package com.claudecode.navigator.server

import com.claudecode.navigator.model.NavigationResponse
import com.claudecode.navigator.model.NavigationResponse.Companion.toJson
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
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
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val requestHandler = RequestHandler(project)

    fun start() {
        if (isRunning.getAndSet(true)) {
            logger.warn("TCP server already running on port $port")
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                logger.info("Navigator TCP server started on port $port for project: ${project.name}")

                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleClient(clientSocket) }
                    } catch (e: SocketException) {
                        if (isRunning.get()) {
                            logger.warn("Socket exception while accepting connection", e)
                        }
                        // Expected when server is stopped
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to start TCP server on port $port", e)
                isRunning.set(false)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            var writer: PrintWriter? = null
            try {
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                writer = PrintWriter(client.getOutputStream(), true)

                val line = reader.readLine()
                if (line == null) {
                    logger.warn("Received empty request on port $port")
                    writer.println(NavigationResponse(status = "error", message = "empty request").toJson())
                    return
                }

                logger.info("Received request: $line")
                val response = requestHandler.handle(line)
                writer.println(response.toJson())
                logger.info("Sent response: ${response.toJson()}")
            } catch (e: Exception) {
                logger.warn("Error handling client connection", e)
                val message = e.message ?: e.javaClass.simpleName
                try {
                    writer?.println(NavigationResponse(status = "error", message = "internal server error: $message").toJson())
                } catch (writeError: Exception) {
                    logger.warn("Failed to send error response to client", writeError)
                }
            }
        }
    }

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
