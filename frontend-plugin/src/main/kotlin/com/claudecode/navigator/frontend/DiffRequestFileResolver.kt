package com.claudecode.navigator.frontend

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile

internal object DiffRequestFileResolver {
    private val wrapperPathMarkers = listOf(
        "TabPreviewDiffVirtualFile",
        "CombinedDiffVirtualFile",
        "DiffVirtualFile",
        "/DiffPreview",
        "/Diff",
    )

    data class Inspection(
        val requestClass: String,
        val title: String?,
        val contentTitles: List<String>,
        val candidates: List<String>,
        val resolvedPath: String?,
    )

    fun resolve(request: Any?, projectBasePath: String?): String? {
        return inspect(request, projectBasePath).resolvedPath
    }

    fun inspectTitles(
        title: String?,
        contentTitles: List<String>,
        projectBasePath: String?,
        sourceClass: String,
    ): Inspection {
        val candidates = collectTitleCandidates(title, contentTitles, projectBasePath)
        return Inspection(
            requestClass = sourceClass,
            title = title,
            contentTitles = contentTitles,
            candidates = candidates,
            resolvedPath = chooseBestPath(candidates),
        )
    }

    fun inspect(request: Any?, projectBasePath: String?): Inspection {
        if (request == null) {
            return Inspection(
                requestClass = "null",
                title = null,
                contentTitles = emptyList(),
                candidates = emptyList(),
                resolvedPath = null,
            )
        }

        val title = ReflectiveMemberLookup.invokeZeroArgMember(request, "getTitle") as? String
        val contentTitles = ReflectiveMemberLookup.readListMember(request, "getContentTitles")
            .mapNotNull { it as? String }
        val titleInspection = inspectTitles(
            title = title,
            contentTitles = contentTitles,
            projectBasePath = projectBasePath,
            sourceClass = request.javaClass.name,
        )
        val candidates = buildList {
            addAll(titleInspection.candidates)
            pathFromMember(ReflectiveMemberLookup.invokeZeroArgMember(request, "getFile"))?.let(::add)
            ReflectiveMemberLookup.readListMember(request, "getFilesToRefresh")
                .mapNotNull(::pathFromMember)
                .forEach(::add)
        }

        return Inspection(
            requestClass = titleInspection.requestClass,
            title = title,
            contentTitles = contentTitles,
            candidates = candidates,
            resolvedPath = chooseBestPath(candidates),
        )
    }

    fun looksLikeWrapperPath(path: String): Boolean {
        return wrapperPathMarkers.any(path::contains)
    }

    fun buildDiagnosticMessage(
        inspection: Inspection,
        wrapperPath: String?,
        projectBasePath: String?,
        editorClass: String?,
    ): String {
        val wrapper = wrapperPath ?: "null"
        val basePath = projectBasePath ?: "null"
        val editor = editorClass ?: "null"
        return "unresolved diff file (request=${inspection.requestClass}, title=${inspection.title ?: "null"}, contentTitles=${inspection.contentTitles}, candidates=${inspection.candidates}, wrapper=$wrapper, projectBase=$basePath, editor=$editor)"
    }

    private fun collectTitleCandidates(title: String?, contentTitles: List<String>, projectBasePath: String?): List<String> {
        val values = buildList {
            title?.let(::add)
            addAll(contentTitles)
        }

        return values.flatMap { toPathCandidates(it, projectBasePath) }
    }

    private fun toPathCandidates(value: String, projectBasePath: String?): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == "Commit") return emptyList()

        val titleWithoutSuffix = trimmed.substringBefore(" (").trim()
        val preferredVariants = linkedSetOf<String>().apply {
            stripLeadingLabelPrefix(trimmed)?.let(::add)
            stripLeadingLabelPrefix(titleWithoutSuffix)?.let(::add)
            stripLeadingHexPrefix(trimmed)?.let(::add)
            stripLeadingHexPrefix(titleWithoutSuffix)?.let(::add)
            stripLeadingLabelPrefix(trimmed)?.let(::stripLeadingHexPrefix)?.let(::add)
            stripLeadingLabelPrefix(titleWithoutSuffix)?.let(::stripLeadingHexPrefix)?.let(::add)
        }
        val rawVariants = linkedSetOf(trimmed, titleWithoutSuffix)
        val orderedVariants = preferredVariants + rawVariants
        return orderedVariants.mapNotNull { normalizeCandidate(it, projectBasePath) }
    }

    private fun stripLeadingHexPrefix(value: String): String? {
        val match = Regex("""(?i)^[0-9a-f]{4,40}\s+(.+)$""").matchEntire(value) ?: return null
        return match.groupValues[1].trim()
    }

    private fun stripLeadingLabelPrefix(value: String): String? {
        val match = Regex("""^(Commit|Changes|Diff)\s*:\s+(.+)$""").matchEntire(value) ?: return null
        return match.groupValues[2].trim()
    }

    private fun normalizeCandidate(candidate: String, projectBasePath: String?): String? {
        if (candidate.isBlank() || looksLikeWrapperPath(candidate)) return null

        val normalized = candidate.replace('\\', '/')
        return when {
            normalized.startsWith("~/") -> normalized
            normalized.startsWith("/") -> normalized
            isWindowsAbsolutePath(normalized) -> normalized
            looksLikeRelativePath(normalized) -> projectBasePath?.let { joinProjectPath(it, normalized) }
            else -> null
        }
    }

    private fun looksLikeRelativePath(candidate: String): Boolean {
        return candidate.contains('/') || candidate.matches(Regex("""[^/\s]+\.[^/\s]+"""))
    }

    private fun joinProjectPath(projectBasePath: String, relativePath: String): String {
        val normalizedBase = projectBasePath.replace('\\', '/').trimEnd('/')
        val normalizedRelative = relativePath.trimStart('/').replace('\\', '/')
        val baseName = normalizedBase.substringAfterLast('/', "")
        return if (baseName.isNotEmpty() && normalizedRelative == baseName) {
            normalizedBase
        } else if (baseName.isNotEmpty() && normalizedRelative.startsWith("$baseName/")) {
            "$normalizedBase/${normalizedRelative.removePrefix("$baseName/")}"
        } else {
            "$normalizedBase/$normalizedRelative"
        }
    }

    private fun chooseBestPath(candidates: List<String>): String? {
        if (candidates.isEmpty()) return null
        val usableCandidates = candidates.filterNot(::looksLikeWrapperPath)
        if (usableCandidates.isEmpty()) return candidates.first()
        return usableCandidates.maxWithOrNull(
            compareBy<String>(
                ::candidateScore,
                { it.count { ch -> ch == '/' || ch == '\\' } },
                String::length,
            ),
        )
            ?: usableCandidates.first()
    }

    private fun isWindowsAbsolutePath(candidate: String): Boolean {
        return Regex("""^[A-Za-z]:/.*""").matches(candidate)
    }

    private fun candidateScore(candidate: String): Int {
        var score = 0
        if (candidate.startsWith("~/") || candidate.startsWith("/") || isWindowsAbsolutePath(candidate)) {
            score += 40
        }
        score += candidate.count { it == '/' || it == '\\' } * 2
        if (!candidate.contains(" (") && !candidate.contains('[') && !candidate.contains(']')) {
            score += 20
        }
        if (candidate.contains(" (")) score -= 40
        if (candidate.contains('[') || candidate.contains(']')) score -= 20
        if (candidate.contains("Changes") || candidate.contains("Commit")) score -= 30
        if (candidate.contains(' ')) score -= 10
        return score
    }

    private fun pathFromMember(candidate: Any?): String? {
        return when (candidate) {
            null -> null
            is String -> candidate
            is VirtualFile -> candidate.path
            is OpenFileDescriptor -> candidate.file.path
            else -> ReflectiveMemberLookup.invokeZeroArgMember(candidate, "getPath") as? String
        }
    }
}
