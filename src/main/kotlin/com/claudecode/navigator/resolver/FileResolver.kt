package com.claudecode.navigator.resolver

import com.claudecode.navigator.model.NavigationTarget
import com.claudecode.navigator.util.PathMatcher
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

class FileResolver(private val project: Project) {
    private val logger = Logger.getInstance(FileResolver::class.java)

    /**
     * Resolves a file path to navigation targets.
     *
     * @param requestPath The requested file path (can be partial)
     * @return List of matching navigation targets
     */
    fun resolve(requestPath: String): List<NavigationTarget> {
        return ReadAction.compute<List<NavigationTarget>, Exception> {
            val fileName = PathMatcher.getFileName(requestPath)
            logger.debug("Resolving file: $requestPath (filename: $fileName)")

            val scope = GlobalSearchScope.projectScope(project)
            val files = FilenameIndex.getVirtualFilesByName(fileName, scope)

            logger.debug("Found ${files.size} files with name '$fileName'")

            val matches = files.filter { file ->
                PathMatcher.pathMatches(file.path, requestPath)
            }

            logger.debug("After path filtering: ${matches.size} matches")

            matches.map { file ->
                NavigationTarget(
                    file = file,
                    line = 0,
                    description = file.path
                )
            }
        }
    }
}
