package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReflectiveGraphExplorerTest {

    @Test
    fun resolvesMemberPathAndExploresNestedNodes() {
        val inspection = ReflectiveGraphExplorer.inspect(
            rootName = "selectedEditor",
            rootValue = FakeEditor(),
            memberPath = "editorModel.content",
            depth = 1,
            maxNodes = 20,
            maxMembers = 20,
        )

        assertEquals("selectedEditor.editorModel.content", inspection.targetPath)
        assertEquals(FakeContent::class.java.name, inspection.targetClass)
        assertTrue(inspection.lines.any { it.contains("selectedEditor.editorModel.content.title ->") })
        assertTrue(inspection.lines.any { it.contains("selectedEditor.editorModel.content.children[0] class=") })
    }

    private class FakeEditor {
        @Suppress("unused")
        private val editorModel = FakeEditorModel()
    }

    private class FakeEditorModel {
        fun getContent(): FakeProperty<FakeContent> = FakeProperty(FakeContent())
    }

    private class FakeContent {
        fun getTitle(): String = "src/main/App.kt"
        fun getChildren(): List<FakeChild> = listOf(FakeChild("first"), FakeChild("second"))
    }

    private class FakeChild(private val name: String) {
        fun getName(): String = name
    }

    private class FakeProperty<T>(private val value: T) {
        fun getValue(): T = value
    }
}
