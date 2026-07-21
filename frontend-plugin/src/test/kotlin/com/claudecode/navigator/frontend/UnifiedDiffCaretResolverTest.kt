package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class UnifiedDiffCaretResolverTest {

    @Test
    fun resolvesDirectUniqueMatch() {
        val result = UnifiedDiffCaretResolver.resolve(
            unifiedLines = listOf("class User:", "    pass"),
            rightLines = listOf("class User:", "    pass"),
            unifiedLineIndex = 1,
        )

        assertEquals(DiffCaretResolution.Found(1), result)
    }

    @Test
    fun reportsAmbiguousDirectMatch() {
        val result = UnifiedDiffCaretResolver.resolve(
            unifiedLines = listOf("value = 1"),
            rightLines = listOf("value = 1", "value = 1"),
            unifiedLineIndex = 0,
        )

        assertEquals(
            DiffCaretResolution.Error("ambiguous: line appears multiple times in file"),
            result,
        )
    }

    @Test
    fun fallsThroughDeletedLineToNearestRemainingNeighbor() {
        val result = UnifiedDiffCaretResolver.resolve(
            unifiedLines = listOf("before", "removed", "after"),
            rightLines = listOf("before", "after"),
            unifiedLineIndex = 1,
        )

        assertEquals(DiffCaretResolution.Found(1), result)
    }

    @Test
    fun reportsErrorWhenNoUniqueNeighborExists() {
        val result = UnifiedDiffCaretResolver.resolve(
            unifiedLines = listOf("removed"),
            rightLines = listOf("other"),
            unifiedLineIndex = 0,
        )

        assertEquals(
            DiffCaretResolution.Error("ambiguous: could not determine unique line"),
            result,
        )
    }
}
