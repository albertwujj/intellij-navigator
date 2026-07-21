package com.claudecode.navigator.frontend

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LineNumberConverter
import kotlin.test.Test
import kotlin.test.assertEquals

class ReflectiveLineNumberConverterLookupTest {

    @Test
    fun findsConverterFromGetter() {
        val converter = FakeConverter()

        val result = ReflectiveLineNumberConverterLookup.find(
            rootLabel = "editor",
            target = GetterHolder(converter),
        )

        assertEquals(listOf("editor.getLineNumberConverter"), result.map { it.label })
    }

    @Test
    fun findsConverterFromPrivateField() {
        val converter = FakeConverter()

        val result = ReflectiveLineNumberConverterLookup.find(
            rootLabel = "gutter",
            target = FieldHolder(converter),
        )

        assertEquals(listOf("gutter.myAdditionalLineNumberConverter"), result.map { it.label })
    }

    @Test
    fun deduplicatesSameConverterInstance() {
        val converter = FakeConverter()

        val result = ReflectiveLineNumberConverterLookup.find(
            rootLabel = "editor",
            target = GetterAndFieldHolder(converter),
        )

        assertEquals(1, result.size)
    }

    private class GetterHolder(private val converter: LineNumberConverter) {
        fun getLineNumberConverter(): LineNumberConverter = converter
    }

    private class FieldHolder(converter: LineNumberConverter) {
        @Suppress("unused")
        private val myAdditionalLineNumberConverter: LineNumberConverter = converter
    }

    private class GetterAndFieldHolder(private val converter: LineNumberConverter) {
        @Suppress("unused")
        private val myAdditionalLineNumberConverter: LineNumberConverter = converter

        fun getLineNumberConverter(): LineNumberConverter = converter
    }

    private class FakeConverter : LineNumberConverter {
        override fun convert(editor: Editor, lineNumber: Int): Int = lineNumber

        override fun getMaxLineNumber(editor: Editor): Int? = null
    }
}
