package com.claudecode.navigator.server

import com.claudecode.navigator.model.*
import com.claudecode.navigator.navigation.NavigationResult
import com.claudecode.navigator.navigation.Navigator
import com.claudecode.navigator.resolver.FileResolver
import com.claudecode.navigator.resolver.SymbolResolver
import com.claudecode.navigator.resolver.TextResolver
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.ClientFileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class RequestHandler(private val project: Project) {
    private val logger = Logger.getInstance(RequestHandler::class.java)
    private val fileResolver = FileResolver(project)
    private val symbolResolver = SymbolResolver(project)
    private val textResolver = TextResolver(project)
    private val navigator = Navigator(project)

    private companion object

    private data class CandidateTargetLineMatch(
        val target: NavigationTarget,
        val line: Int,
    )

    private data class TargetCandidateEvaluation(
        val target: NavigationTarget,
        val uniqueLinesByCandidate: LinkedHashMap<String, Int>,
        val sawPerFileAmbiguity: Boolean,
    )

    private sealed interface ResolveByTextOutcome {
        data class Resolved(val match: CandidateTargetLineMatch) : ResolveByTextOutcome
        data class Ambiguous(val message: String) : ResolveByTextOutcome
        data object NotFound : ResolveByTextOutcome
    }

    fun handle(jsonRequest: String): NavigationResponse {
        return try {
            val request = NavigationRequest.parse(jsonRequest)
            logger.info("Parsed request: $request")

            when (request) {
                is FileRequest -> handleFileRequest(request)
                is SymbolRequest -> handleSymbolRequest(request)
                is TextRequest -> handleTextRequest(request)
                is CaretRequest -> handleCaretRequest()
                is ResolveFileRequest -> handleResolveFileRequest(request)
            }
        } catch (e: Exception) {
            logger.error("Failed to handle request: $jsonRequest", e)
            NavigationResponse(status = "error", message = e.message)
        }
    }

    private fun handleFileRequest(request: FileRequest): NavigationResponse {
        val targets = fileResolver.resolve(request.path)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "file not found")
        }

        if (request.line == null && request.matchText == null) {
            return activate(targets)
        }

        val targetsWithLine = if (request.line != null) {
            targets.map { it.copy(line = request.line - 1) } // Convert to 0-indexed
        } else {
            targets
        }

        if (request.matchText != null) {
            val target = targetsWithLine.first()
            val doc = FileDocumentManager.getInstance().getDocument(target.file)
            if (doc != null) {
                if (request.line != null) {
                    // Validate matchText against the given line; spiral if mismatch
                    val lineIndex = target.line
                    if (lineIndex in 0 until doc.lineCount) {
                        val lineStart = doc.getLineStartOffset(lineIndex)
                        val lineEnd = doc.getLineEndOffset(lineIndex)
                        val lineContent = doc.getText(TextRange(lineStart, lineEnd)).trim()
                        if (lineContent != request.matchText) {
                            val foundLine = findUniqueLineInDoc(doc, request.matchText)
                            if (foundLine != null && foundLine == -1) {
                                return NavigationResponse(status = "error", message = "ambiguous: matchText appears multiple times in file")
                            }
                            if (foundLine != null) {
                                val correctedTargets = targetsWithLine.map { it.copy(line = foundLine) }
                                val result = navigate(correctedTargets)
                                return result.copy(status = "text_moved")
                            }
                            return NavigationResponse(status = "not_found", message = "matchText not in file")
                        }
                    }
                } else {
                    // No line given — search entire file for matchText
                    val foundLine = findUniqueLineInDoc(doc, request.matchText)
                    if (foundLine != null && foundLine == -1) {
                        return NavigationResponse(status = "error", message = "ambiguous: matchText appears multiple times in file")
                    }
                    if (foundLine != null) {
                        val matchedTargets = targets.map { it.copy(line = foundLine) }
                        return navigate(matchedTargets)
                    }
                    return NavigationResponse(status = "not_found", message = "matchText not in file")
                }
            }
        }

        return navigate(targetsWithLine)
    }

    private fun handleSymbolRequest(request: SymbolRequest): NavigationResponse {
        val targets = symbolResolver.resolve(request.name, request.fileHint)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "symbol: ${request.name}")
        }

        return navigate(targets)
    }

    private fun handleTextRequest(request: TextRequest): NavigationResponse {
        val targets = textResolver.resolve(request.text)

        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "text: ${request.text.take(50)}")
        }

        return navigate(targets)
    }

    private fun handleCaretRequest(): NavigationResponse {
        var response: NavigationResponse? = null
        ApplicationManager.getApplication().invokeAndWait {
            response = readCaretPosition()
        }
        return response ?: NavigationResponse(status = "error", message = "no active editor")
    }

    private fun handleResolveFileRequest(request: ResolveFileRequest): NavigationResponse {
        val requestPath = normalizePath(request.path.trim())
        if (requestPath.isEmpty()) {
            return NavigationResponse(status = "error", message = "missing path")
        }

        val targets = fileResolver.resolve(requestPath)
        if (targets.isEmpty()) {
            return NavigationResponse(status = "not_found", message = "file not found")
        }

        return when (
            val resolution = resolveByCandidateTexts(
                targets = targets,
                requestPath = requestPath,
                matchText = request.matchText,
                matchTextCandidates = request.matchTextCandidates,
            )
        ) {
            is ResolveByTextOutcome.Resolved -> {
                val targetFile = resolution.match.target.file
                NavigationResponse(
                    status = "ok",
                    file = targetFile.path,
                    relativePath = projectRelativePath(targetFile, requestPath),
                    line = resolution.match.line + 1,
                    column = 0,
                )
            }
            is ResolveByTextOutcome.Ambiguous -> NavigationResponse(
                status = "error",
                message = resolution.message,
                count = targets.size.takeIf { it > 1 },
            )
            ResolveByTextOutcome.NotFound -> {
                val disambiguatedTargets = disambiguateResolveTargets(
                    targets = targets,
                    matchText = request.matchText,
                )
                when {
                    disambiguatedTargets.isEmpty() -> NavigationResponse(status = "not_found", message = "file not found")
                    disambiguatedTargets.size > 1 -> NavigationResponse(
                        status = "error",
                        message = "multiple files match: $requestPath",
                        count = disambiguatedTargets.size,
                    )
                    else -> {
                        val targetFile = disambiguatedTargets.single().file
                        NavigationResponse(
                            status = "ok",
                            file = targetFile.path,
                            relativePath = projectRelativePath(targetFile, requestPath),
                        )
                    }
                }
            }
        }
    }

    private fun disambiguateResolveTargets(
        targets: List<NavigationTarget>,
        matchText: String?,
    ): List<NavigationTarget> {
        if (targets.size <= 1) return targets
        return narrowTargetsByMatchText(targets, matchText)
    }

    private fun narrowTargetsByMatchText(
        targets: List<NavigationTarget>,
        matchText: String?,
    ): List<NavigationTarget> {
        val normalizedText = matchText?.trim()?.takeIf { it.isNotEmpty() } ?: return targets

        val textMatches = readAction {
            targets.filter { targetContainsText(it.file, normalizedText) }
        }
        return if (textMatches.isNotEmpty()) textMatches else targets
    }

    private fun resolveByCandidateTexts(
        targets: List<NavigationTarget>,
        requestPath: String,
        matchText: String?,
        matchTextCandidates: List<String>?,
    ): ResolveByTextOutcome {
        val candidates = buildList {
            matchText?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            matchTextCandidates
                .orEmpty()
                .map(String::trim)
                .filter { it.isNotEmpty() }
                .forEach(::add)
        }.distinct()

        if (candidates.isEmpty()) {
            return ResolveByTextOutcome.NotFound
        }

        return readAction {
            // Frontend diff tabs may only provide a basename-style file hint plus nearby
            // unified-diff anchor texts. Score files by how many anchors they match before
            // choosing the authoritative real line within the best-matching file.
            val evaluations = targets.map { target ->
                val doc = FileDocumentManager.getInstance().getDocument(target.file)
                val uniqueLinesByCandidate = linkedMapOf<String, Int>()
                var sawPerFileAmbiguity = false

                if (doc != null) {
                    for (candidate in candidates) {
                        when (val foundLine = findUniqueLineInDoc(doc, candidate)) {
                            null -> Unit
                            -1 -> sawPerFileAmbiguity = true
                            else -> uniqueLinesByCandidate[candidate] = foundLine
                        }
                    }
                }

                TargetCandidateEvaluation(
                    target = target,
                    uniqueLinesByCandidate = uniqueLinesByCandidate,
                    sawPerFileAmbiguity = sawPerFileAmbiguity,
                )
            }

            val bestScore = evaluations.maxOfOrNull { it.uniqueLinesByCandidate.size } ?: 0
            if (bestScore > 0) {
                val bestTargets = evaluations.filter { it.uniqueLinesByCandidate.size == bestScore }
                if (bestTargets.size > 1) {
                    return@readAction ResolveByTextOutcome.Ambiguous("multiple files match: $requestPath")
                }

                val bestTarget = bestTargets.single()
                val resolvedLine = candidates.firstNotNullOfOrNull { bestTarget.uniqueLinesByCandidate[it] }
                    ?: return@readAction ResolveByTextOutcome.NotFound

                return@readAction ResolveByTextOutcome.Resolved(
                    CandidateTargetLineMatch(target = bestTarget.target, line = resolvedLine)
                )
            }

            val sawPerFileAmbiguity = evaluations.any { it.sawPerFileAmbiguity }
            when {
                targets.size > 1 ->
                    ResolveByTextOutcome.Ambiguous("multiple files match: $requestPath")
                sawPerFileAmbiguity ->
                    ResolveByTextOutcome.Ambiguous("ambiguous: matchText appears multiple times in file")
                else ->
                    ResolveByTextOutcome.NotFound
            }
        }
    }

    private fun <T> readAction(action: () -> T): T {
        return ApplicationManager.getApplication().runReadAction<T>(action)
    }

    private fun targetContainsText(file: com.intellij.openapi.vfs.VirtualFile, text: String): Boolean {
        val doc = FileDocumentManager.getInstance().getDocument(file) ?: return false
        for (lineIndex in 0 until doc.lineCount) {
            if (trimmedLineText(doc, lineIndex) == text) {
                return true
            }
        }
        return false
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trim()
    }

    private fun projectRelativePath(file: VirtualFile, requestPath: String): String? {
        ProjectRootManager.getInstance(project).contentRoots.forEach { contentRoot ->
            VfsUtilCore.getRelativePath(file, contentRoot, '/')
                ?.takeIf(::isUsableRelativePath)
                ?.let { return it }
        }

        return requestPath.takeIf(::isUsableRelativePath)
    }

    private fun isUsableRelativePath(path: String): Boolean {
        val normalized = normalizePath(path)
        if (normalized.isEmpty()) return false
        if (normalized.startsWith("../") || normalized == "..") return false
        if (normalized.startsWith("~/")) return false
        if (normalized.startsWith("/") || Regex("""^[A-Za-z]:/.*""").matches(normalized)) return false
        return true
    }

    private fun readCaretPosition(): NavigationResponse? {
        // Check if the active editor is a unified diff viewer
        val diffResult = tryReadUnifiedDiffCaret(currentSelectedEditor())
        if (diffResult != null) return diffResult

        // Regular editor
        val editor = currentSelectedTextEditor() ?: return null
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val caret = editor.caretModel.logicalPosition
        return NavigationResponse(
            status = "ok",
            file = file.path,
            line = caret.line + 1
        )
    }

    /**
     * If the active editor is a unified diff viewer, read the caret position and
     * map it to the corresponding line in the current (right-side) version of the file.
     * If the caret is on a deleted line, finds the nearest line present in the current version.
     */
    private fun tryReadUnifiedDiffCaret(selectedEditor: FileEditor?): NavigationResponse? {
        if (selectedEditor !is DiffEditorViewerFileEditor) return null

        val processor = selectedEditor.editorViewer as? DiffRequestProcessor ?: return null
        val viewer = processor.activeViewer as? UnifiedDiffViewer ?: return null

        val editor = viewer.editor
        val unifiedLine = editor.caretModel.logicalPosition.line

        val rightDoc = viewer.getContent(Side.RIGHT).document
        val file = FileDocumentManager.getInstance().getFile(rightDoc)
        val path = file?.path ?: return null

        // Read the line text at the caret in the unified editor
        val unifiedDoc = editor.document
        if (unifiedLine >= unifiedDoc.lineCount) return null
        val lineStart = unifiedDoc.getLineStartOffset(unifiedLine)
        val lineEnd = unifiedDoc.getLineEndOffset(unifiedLine)
        val lineText = unifiedDoc.getText(TextRange(lineStart, lineEnd)).trim()

        // Find this line in the right-side (current) document
        val matchedLine = findUniqueLineInDoc(rightDoc, lineText)
        if (matchedLine != null) {
            return if (matchedLine >= 0) {
                NavigationResponse(status = "ok", file = path, line = matchedLine + 1)
            } else {
                NavigationResponse(status = "error", message = "ambiguous: line appears multiple times in file")
            }
        }

        // Line is deleted (only in old version) — search downward then upward in the
        // unified document for the first line whose text exists in the current file.
        for (delta in 1..unifiedDoc.lineCount) {
            for (candidate in listOf(unifiedLine + delta, unifiedLine - delta)) {
                if (candidate in 0 until unifiedDoc.lineCount) {
                    val s = unifiedDoc.getLineStartOffset(candidate)
                    val e = unifiedDoc.getLineEndOffset(candidate)
                    val candidateText = unifiedDoc.getText(TextRange(s, e)).trim()
                    if (candidateText.isNotEmpty()) {
                        val found = findUniqueLineInDoc(rightDoc, candidateText)
                        if (found != null && found >= 0) {
                            return NavigationResponse(status = "ok", file = path, line = found + 1)
                        }
                        // Skip ambiguous neighbor lines, keep searching
                    }
                }
            }
        }

        return NavigationResponse(status = "error", message = "ambiguous: could not determine unique line")
    }

    /**
     * Find a line in [doc] whose trimmed content matches [text].
     * Returns: 0-indexed line number if unique match, -1 if ambiguous (multiple matches),
     * null if not found.
     */
    private fun findUniqueLineInDoc(doc: Document, text: String): Int? {
        var count = 0
        var firstMatch: Int? = null
        for (i in 0 until doc.lineCount) {
            if (trimmedLineText(doc, i) == text) {
                count++
                if (firstMatch == null) firstMatch = i
                if (count > 1) return -1
            }
        }
        return firstMatch
    }

    private fun trimmedLineText(doc: Document, lineIndex: Int): String? {
        if (lineIndex !in 0 until doc.lineCount) return null
        val s = doc.getLineStartOffset(lineIndex)
        val e = doc.getLineEndOffset(lineIndex)
        return doc.getText(TextRange(s, e)).trim()
    }

    private fun currentSelectedEditor(): FileEditor? {
        return currentClientFileEditorManager()?.getSelectedEditor()
            ?: FileEditorManager.getInstance(project).selectedEditor
    }

    private fun currentSelectedTextEditor() =
        currentClientFileEditorManager()?.getSelectedTextEditor()
            ?: FileEditorManager.getInstance(project).selectedTextEditor

    private fun currentClientFileEditorManager(): ClientFileEditorManager? {
        return try {
            ClientFileEditorManager.getCurrentInstance(project)
        } catch (e: Exception) {
            logger.info("ClientFileEditorManager unavailable: ${e.message}")
            null
        }
    }



    private fun navigate(targets: List<NavigationTarget>): NavigationResponse {
        return runOnEdt(targets.size, includeCoordinates = true) { navigator.navigate(targets) }
    }

    private fun activate(targets: List<NavigationTarget>): NavigationResponse {
        return runOnEdt(targets.size, includeCoordinates = false) { navigator.activate(targets) }
    }

    private fun runOnEdt(
        targetCount: Int,
        includeCoordinates: Boolean,
        openTargets: () -> NavigationResult?
    ): NavigationResponse {
        var result: NavigationResult? = null

        // Run navigation synchronously on EDT so it completes before the TCP
        // response returns. This prevents agent-term from stealing focus before
        // navigation executes, and lets us return diagnostic info.
        ApplicationManager.getApplication().invokeAndWait {
            result = openTargets()
        }

        val navigationResult = result
        return if (navigationResult != null) {
            NavigationResponse(
                status = "ok",
                file = navigationResult.filePath,
                line = if (includeCoordinates) navigationResult.line else null,
                column = if (includeCoordinates) navigationResult.column else null
            )
        } else if (targetCount > 1) {
            NavigationResponse(status = "multiple", count = targetCount)
        } else {
            NavigationResponse(status = "ok")
        }
    }
}
