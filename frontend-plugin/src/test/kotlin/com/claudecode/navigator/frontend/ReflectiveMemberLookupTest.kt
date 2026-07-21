package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReflectiveMemberLookupTest {

    @Test
    fun readsAssignableValueFromGetter() {
        val expected = SampleContractImpl()

        val result = ReflectiveMemberLookup.readAssignableMember(
            GetterHolder(expected),
            SampleContract::class.java,
        )

        assertSame(expected, result)
    }

    @Test
    fun readsAssignableValueFromField() {
        val expected = SampleContractImpl()

        val result = ReflectiveMemberLookup.readAssignableMember(
            FieldHolder(expected),
            SampleContract::class.java,
        )

        assertSame(expected, result)
    }

    @Test
    fun returnsNullWhenNoAssignableMemberExists() {
        val result = ReflectiveMemberLookup.readAssignableMember(
            NoMatchHolder(),
            SampleContract::class.java,
        )

        assertNull(result)
    }

    @Test
    fun prefersCallableAssignableMember() {
        val expected = SampleContractImpl()
        val result = ReflectiveMemberLookup.readAssignableMember(
            GetterAndFieldHolder(expected),
            SampleContract::class.java,
        )

        assertNotNull(result)
    }

    @Test
    fun readsListMemberFromMethod() {
        val result = ReflectiveMemberLookup.readListMember(
            ListHolder(),
            "getAllEditors",
        )

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun invokesZeroArgMemberByName() {
        val result = ReflectiveMemberLookup.invokeZeroArgMember(
            GetterHolder(SampleContractImpl()),
            "getProcessor",
        )

        assertNotNull(result)
    }

    @Test
    fun invokesMemberWithArguments() {
        val result = ReflectiveMemberLookup.invokeMember(
            ProducerHolder(),
            "collect",
            false,
        )

        assertEquals("all", result)
    }

    @Test
    fun readsNamedMemberFromGetterOrField() {
        assertEquals(
            "getter",
            ReflectiveMemberLookup.readNamedMember(
                NamedGetterHolder(),
                "editorModel",
            ),
        )
        assertEquals(
            "field",
            ReflectiveMemberLookup.readNamedMember(
                NamedFieldHolder(),
                "editorModel",
            ),
        )
    }

    private interface SampleContract

    private class SampleContractImpl : SampleContract

    private class GetterHolder(private val value: SampleContract) {
        fun getProcessor(): SampleContract = value
    }

    private class FieldHolder(value: SampleContract) {
        @Suppress("unused")
        private val processor: SampleContract = value
    }

    private class GetterAndFieldHolder(value: SampleContract) {
        private val storedValue = value
        @Suppress("unused")
        private val processor: SampleContract = storedValue
        fun getProcessor(): SampleContract = storedValue
    }

    private class NoMatchHolder {
        fun getValue(): String = "nope"
    }

    private class ListHolder {
        fun getAllEditors(): List<String> = listOf("a", "b")
    }

    private class ProducerHolder {
        fun collect(selectedOnly: Boolean): String = if (selectedOnly) "selected" else "all"
    }

    private class NamedGetterHolder {
        fun getEditorModel(): String = "getter"
    }

    private class NamedFieldHolder {
        @Suppress("unused")
        private val editorModel: String = "field"
    }
}
