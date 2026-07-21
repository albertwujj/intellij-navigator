package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class ReactiveControlStringCollectorTest {

    @Test
    fun collectsNestedStringsFromBindableChildren() {
        val root = Node(
            controlId = Property("header"),
            bindableChildren = listOf(
                Node(text = Property("9af31d2 src/main/App.kt")),
                Pair("ignored", Node(tooltip = Property("~/repo/src/main/App.kt"))),
            ),
        )

        val inspection = ReactiveControlStringCollector.inspect(root)

        assertEquals(
            listOf("header", "9af31d2 src/main/App.kt", "~/repo/src/main/App.kt"),
            inspection.candidates,
        )
    }

    private class Node(
        private val tooltip: Property<String?> = Property(null),
        private val controlId: Property<String?> = Property(null),
        private val text: Property<String?> = Property(null),
        private val bindableChildren: List<Any> = emptyList(),
    ) {
        fun getTooltip(): Property<String?> = tooltip
        fun getControlId(): Property<String?> = controlId
        fun getText(): Property<String?> = text
        fun getBindableChildren(): List<Any> = bindableChildren
    }

    private class Property<T>(private val value: T) {
        fun getValue(): T = value
    }
}
