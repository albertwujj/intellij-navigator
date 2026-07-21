package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiffRequestFileResolverTest {

    @Test
    fun resolvesProjectRelativeTitleAgainstProjectBase() {
        val request = Request(title = "src/main.py")

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/repo/project/src/main.py", result)
    }

    @Test
    fun resolvesRepoRootFileTitleAgainstProjectBase() {
        val request = Request(title = "README.md")

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/repo/project/README.md", result)
    }

    @Test
    fun avoidsDuplicatingProjectNameWhenTitleStartsWithProjectFolder() {
        val request = Request(title = "project/src/main.py")

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/repo/project/src/main.py", result)
    }

    @Test
    fun preservesHomeRelativeTitle() {
        val request = Request(title = "~/repo/project/src/main.py")

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("~/repo/project/src/main.py", result)
    }

    @Test
    fun stripsLeadingHexPrefixFromRelativeTitle() {
        val request = Request(title = "9af31d2 src/main.py")

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/repo/project/src/main.py", result)
    }

    @Test
    fun ignoresCommitTitleAndWrapperPaths() {
        val request = Request(
            title = "Commit",
            file = PathHolder("/TabPreviewDiffVirtualFile"),
        )

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/TabPreviewDiffVirtualFile", result)
    }

    @Test
    fun inspectionIncludesResolvedCandidates() {
        val request = Request(
            title = "src/main.py",
            contentTitles = listOf("Commit", "project/src/main.py"),
        )

        val result = DiffRequestFileResolver.inspect(request, projectBasePath = "/repo/project")

        assertEquals("src/main.py", result.title)
        assertEquals(listOf("Commit", "project/src/main.py"), result.contentTitles)
        assertEquals(listOf("/repo/project/src/main.py", "/repo/project/src/main.py"), result.candidates)
        assertEquals("/repo/project/src/main.py", result.resolvedPath)
    }

    @Test
    fun prefersCleanAbsolutePathOverDecoratedTitle() {
        val request = Request(
            title = "terminal-keyboard.js (/repo/project/src) [Changes]",
            file = PathHolder("/repo/project/src/terminal-keyboard.js"),
        )

        val result = DiffRequestFileResolver.resolve(request, projectBasePath = "/repo/project")

        assertEquals("/repo/project/src/terminal-keyboard.js", result)
    }

    @Test
    fun returnsNullWhenNoUsablePathExists() {
        val result = DiffRequestFileResolver.resolve(Request(title = "Commit"), projectBasePath = null)

        assertNull(result)
    }

    private data class Request(
        private val title: String,
        private val file: PathHolder? = null,
        private val contentTitles: List<String> = emptyList(),
    ) {
        fun getTitle(): String = title
        fun getFile(): PathHolder? = file
        fun getContentTitles(): List<String> = contentTitles
    }

    private data class PathHolder(private val path: String) {
        fun getPath(): String = path
    }
}
