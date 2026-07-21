package com.claudecode.navigator.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
private data class FrontendActionRequest(
    val action: String,
)

@Serializable
private data class ScrollRequest(
    val action: String = "scroll",
    val file: String,
    val line: Int,
    val column: Int = 0
)

@Serializable
private data class CaretRequest(
    val action: String = "caret"
)

@Serializable
private data class CaretDiagnosticsRequest(
    val action: String = "caret_diagnostics"
)

// Debug-only local investigation actions. These are intentionally undocumented and
// are not used by AgentTerm's shipped navigation flow.
@Serializable
private data class ExploreObjectRequest(
    val action: String = "explore_object",
    val root: String = "selectedEditor",
    val memberPath: String? = null,
    val depth: Int = 2,
    val maxNodes: Int = 40,
    val maxMembers: Int = 25,
)

@Serializable
private data class DiffProbeRequest(
    val action: String = "diff_probe"
)

@Serializable
private data class ScrollResponse(
    val status: String,
    val message: String? = null,
)

@Serializable
private data class CaretResponse(
    val status: String,
    val message: String? = null,
    val file: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val matchText: String? = null,
    val matchTextCandidates: List<String>? = null,
)

@Serializable
private data class CaretDiagnosticsResponse(
    val status: String,
    val message: String,
)

@Serializable
private data class ExploreObjectResponse(
    val status: String,
    val message: String,
)

@Serializable
private data class DiffProbeResponse(
    val status: String,
    val message: String,
)

@Serializable
private data class ErrorResponse(
    val status: String = "error",
    val message: String,
)

class ScrollServer(
    private val project: Project,
    private val port: Int = DEFAULT_PORT
) {
    private val logger = Logger.getInstance(ScrollServer::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val isRunning = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        if (isRunning.getAndSet(true)) {
            logger.warn("Scroll server already running on port $port")
            return
        }

        serverJob = scope.launch {
            try {
                serverSocket = ServerSocket(port)
                logger.info("Navigator frontend server started on port $port")

                while (isActive && isRunning.get()) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch { handleClient(clientSocket) }
                    } catch (e: SocketException) {
                        if (isRunning.get()) {
                            logger.warn("Socket exception while accepting connection", e)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to start scroll server on port $port", e)
                isRunning.set(false)
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.use { client ->
                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                val writer = PrintWriter(client.getOutputStream(), true)

                val line = reader.readLine()
                if (line != null) {
                    logger.debug("Received frontend request: $line")
                    writer.println(handleRequest(line))
                }
            }
        } catch (e: Exception) {
            logger.warn("Error handling frontend client", e)
        }
    }

    private suspend fun handleRequest(rawJson: String): String {
        return try {
            when (json.decodeFromString(FrontendActionRequest.serializer(), rawJson).action) {
                "scroll" -> json.encodeToString(ScrollResponse.serializer(), handleScrollRequest(rawJson))
                "caret" -> json.encodeToString(CaretResponse.serializer(), handleCaretRequest(rawJson))
                "caret_diagnostics" -> json.encodeToString(CaretDiagnosticsResponse.serializer(), handleCaretDiagnosticsRequest(rawJson))
                "explore_object" -> json.encodeToString(ExploreObjectResponse.serializer(), handleExploreObjectRequest(rawJson))
                "diff_probe" -> json.encodeToString(DiffProbeResponse.serializer(), handleDiffProbeRequest(rawJson))
                else -> json.encodeToString(ErrorResponse.serializer(), ErrorResponse(message = "Unknown action"))
            }
        } catch (e: Exception) {
            logger.error("Failed to handle frontend request: $rawJson", e)
            json.encodeToString(
                ErrorResponse.serializer(),
                ErrorResponse(message = e.message ?: "failed to handle request"),
            )
        }
    }

    private suspend fun handleScrollRequest(rawJson: String): ScrollResponse {
        val request = json.decodeFromString(ScrollRequest.serializer(), rawJson)
        return scrollToCaret(request.file, request.line, request.column)
    }

    private fun handleCaretRequest(rawJson: String): CaretResponse {
        json.decodeFromString(CaretRequest.serializer(), rawJson)
        return try {
            when (val result = CaretPositionReader(project).read()) {
                is CaretReadResult.Success -> CaretResponse(
                    status = "ok",
                    file = result.position.file,
                    line = result.position.line,
                    column = result.position.column,
                    matchText = result.position.matchText,
                    matchTextCandidates = result.position.matchTextCandidates,
                )
                is CaretReadResult.Error -> CaretResponse(status = "error", message = result.message)
                CaretReadResult.NoActiveEditor -> CaretResponse(status = "error", message = "no active editor")
            }
        } catch (t: Throwable) {
            logger.error("Failed to read caret position", t)
            val detail = t.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            CaretResponse(
                status = "error",
                message = "caret lookup unavailable (${t.javaClass.simpleName}$detail)",
            )
        }
    }

    private fun handleCaretDiagnosticsRequest(rawJson: String): CaretDiagnosticsResponse {
        json.decodeFromString(CaretDiagnosticsRequest.serializer(), rawJson)
        return try {
            CaretDiagnosticsResponse(
                status = "ok",
                message = CaretPositionReader(project).diagnose(),
            )
        } catch (t: Throwable) {
            logger.error("Failed to collect caret diagnostics", t)
            val detail = t.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            CaretDiagnosticsResponse(
                status = "error",
                message = "caret diagnostics unavailable (${t.javaClass.simpleName}$detail)",
            )
        }
    }

    private fun handleExploreObjectRequest(rawJson: String): ExploreObjectResponse {
        val request = json.decodeFromString(ExploreObjectRequest.serializer(), rawJson)
        return try {
            ExploreObjectResponse(
                status = "ok",
                message = FrontendObjectExplorer(project).inspect(
                    root = request.root,
                    memberPath = request.memberPath,
                    depth = request.depth,
                    maxNodes = request.maxNodes,
                    maxMembers = request.maxMembers,
                ),
            )
        } catch (t: Throwable) {
            logger.error("Failed to explore frontend object graph", t)
            val detail = t.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            ExploreObjectResponse(
                status = "error",
                message = "Object exploration unavailable (${t.javaClass.simpleName}$detail)",
            )
        }
    }

    private fun handleDiffProbeRequest(rawJson: String): DiffProbeResponse {
        json.decodeFromString(DiffProbeRequest.serializer(), rawJson)
        return try {
            DiffProbeResponse(
                status = "ok",
                message = CaretPositionReader(project).diffProbe(),
            )
        } catch (t: Throwable) {
            logger.error("Failed to collect diff probe details", t)
            val detail = t.message?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
            DiffProbeResponse(
                status = "error",
                message = "Diff probe unavailable (${t.javaClass.simpleName}$detail)",
            )
        }
    }

    /** Reads the current editor file path on the EDT, or null if no editor is open. */
    private data class EditorState(val filePath: String, val editor: Editor)

    private fun readEditorState(): EditorState? {
        var state: EditorState? = null
        ApplicationManager.getApplication().invokeAndWait {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor != null) {
                val vFile = FileDocumentManager.getInstance().getFile(editor.document)
                if (vFile != null) {
                    state = EditorState(vFile.path, editor)
                }
            }
        }
        return state
    }

    /**
     * Suffix-based path matching: checks if either path ends with the other
     * when compared segment by segment from the end.
     */
    private fun pathMatches(candidate: String, request: String): Boolean {
        val candidateSegments = candidate.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotEmpty() }
        val requestSegments = request.replace('\\', '/').trimEnd('/').split('/').filter { it.isNotEmpty() }
        if (candidateSegments.isEmpty() || requestSegments.isEmpty()) return false
        val minLen = minOf(candidateSegments.size, requestSegments.size)
        for (i in 1..minLen) {
            if (!candidateSegments[candidateSegments.size - i].equals(requestSegments[requestSegments.size - i], ignoreCase = true)) {
                return false
            }
        }
        return true
    }

    private suspend fun scrollToCaret(expectedFile: String, expectedLine: Int, expectedColumn: Int): ScrollResponse {
        val pollIntervalMs = 50L
        val maxWaitMs = 3000L
        var waited = 0L

        // Poll until the editor has the expected file open, then force-move
        // the caret.  We don't wait for the caret to propagate via Rd — it's
        // too slow / unreliable on WSL remote dev.
        while (waited < maxWaitMs) {
            val state = readEditorState()
            if (state != null && pathMatches(state.filePath, expectedFile)) {
                return forceScrollTo(state.editor, expectedLine, expectedColumn)
            }
            delay(pollIntervalMs)
            waited += pollIntervalMs
        }

        // Timeout — file never opened; scroll whatever is open
        logger.warn("SCROLL: timed out waiting for file=$expectedFile (waited ${waited}ms)")
        val state = readEditorState()
        return if (state != null) {
            forceScrollTo(state.editor, expectedLine, expectedColumn)
        } else {
            ScrollResponse("no_editor")
        }
    }

    private fun forceScrollTo(editor: Editor, line: Int, column: Int): ScrollResponse {
        var lineCount = 0
        ApplicationManager.getApplication().invokeAndWait {
            lineCount = editor.document.lineCount
        }
        if (line > lineCount) {
            logger.info("SCROLL: file_too_short — requested line $line but file has $lineCount lines")
            return ScrollResponse("file_too_short")
        }

        ApplicationManager.getApplication().invokeAndWait {
            val targetLine = (line - 1).coerceAtLeast(0)
            editor.caretModel.moveToLogicalPosition(LogicalPosition(targetLine, column))
            logger.info("SCROLL: forced caret to $line:$column")

            if (editor.scrollingModel.visibleArea.height > 0) {
                editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
            } else {
                logger.info("SCROLL: editor not laid out yet, waiting for resize")
                scheduleScrollOnResize(editor)
            }
        }
        return ScrollResponse("ok")
    }

    private fun scheduleScrollOnResize(editor: com.intellij.openapi.editor.Editor) {
        val component = editor.contentComponent
        component.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                if (editor.isDisposed) {
                    component.removeComponentListener(this)
                    return
                }
                if (editor.scrollingModel.visibleArea.height > 0) {
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    component.removeComponentListener(this)
                    logger.info("SCROLL: scrolled via componentResized")
                }
            }
        })
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        logger.info("Stopping scroll server on port $port")
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            logger.warn("Error closing scroll server socket", e)
        }

        serverJob?.cancel()
        scope.cancel()
        serverSocket = null
        serverJob = null
    }

    companion object {
        const val DEFAULT_PORT = 8766
    }
}
