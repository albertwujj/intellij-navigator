package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class DiffAnchorTextCollectorTest {

    @Test
    fun prefersCurrentThenFollowingThenPreviousLines() {
        val result = DiffAnchorTextCollector.collect(
            lines = listOf("before", "removed", "after", "later"),
            caretLineIndex = 1,
            radius = 2,
        )

        assertEquals(listOf("removed", "after", "later", "before"), result)
    }

    @Test
    fun skipsBlankAndDuplicateLines() {
        val result = DiffAnchorTextCollector.collect(
            lines = listOf("same", "  ", "same", "other"),
            caretLineIndex = 0,
            radius = 3,
        )

        assertEquals(listOf("same", "other"), result)
    }

    @Test
    fun returnsEmptyWhenCaretIsOutOfRange() {
        val result = DiffAnchorTextCollector.collect(
            lines = listOf("one"),
            caretLineIndex = 5,
        )

        assertEquals(emptyList(), result)
    }
}
