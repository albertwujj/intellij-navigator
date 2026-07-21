package com.claudecode.navigator.resolver

import com.claudecode.navigator.model.NavigationTarget
import com.claudecode.navigator.util.PathMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class TextResolver(private val project: Project) {
    private val logger = Logger.getInstance(TextResolver::class.java)

    /**
     * Resolves a text snippet to navigation targets by searching file contents.
     * If fileHint is provided, results are further filtered by path matching.
     *
     * @param text The text to search for (trimmed before search)
     * @param fileHint Optional file path hint for filtering results
     * @return List of matching navigation targets
     */
    fun resolve(text: String, fileHint: String? = null): List<NavigationTarget> {
        return ReadAction.compute<List<NavigationTarget>, Exception> {
            val trimmedText = text.trim()
            if (trimmedText.isEmpty()) {
                return@compute emptyList()
            }

            logger.debug("Searching for text: '$trimmedText' (fileHint: $fileHint)")

            val scope = GlobalSearchScope.projectScope(project)
            val filesToSearch = FilenameIndex.getAllFilesByExt(project, "py", scope).toList()

            logger.debug("Searching in ${filesToSearch.size} files")

            val targets = mutableListOf<NavigationTarget>()

            for (file in filesToSearch) {
                val matches = searchInFile(file, trimmedText)
                targets.addAll(matches)
            }

            logger.debug("Found ${targets.size} matches for text '$trimmedText'")

            // Apply fileHint as secondary filter
            applyFileHintFilter(targets, fileHint)
        }
    }

    /**
     * Applies file hint filtering to targets.
     * If hint matches some targets, returns only those.
     * If hint matches nothing (soft matching), returns all targets unchanged.
     */
    private fun applyFileHintFilter(
        targets: List<NavigationTarget>,
        fileHint: String?
    ): List<NavigationTarget> {
        if (fileHint.isNullOrBlank() || targets.isEmpty()) {
            return targets
        }

        val filtered = targets.filter {
            PathMatcher.pathMatches(it.file.path, fileHint)
        }

        logger.debug("FileHint filter: ${targets.size} -> ${filtered.size} (hint: $fileHint)")

        // Soft matching: if hint matches nothing, ignore it
        return if (filtered.isNotEmpty()) filtered else targets
    }

    private fun searchInFile(file: VirtualFile, searchText: String): List<NavigationTarget> {
        val targets = mutableListOf<NavigationTarget>()

        try {
            val content = String(file.contentsToByteArray(), file.charset)
            val lines = content.lines()

            for ((index, line) in lines.withIndex()) {
                if (line.trim() == searchText || line.contains(searchText)) {
                    targets.add(
                        NavigationTarget(
                            file = file,
                            line = index,  // 0-indexed
                            description = line.trim().take(60) + if (line.length > 60) "..." else ""
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Error reading file ${file.path}", e)
        }

        return targets
    }
}
