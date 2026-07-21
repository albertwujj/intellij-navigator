package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FrontendEditorModelResolverTest {

    @Test
    fun resolvesPathFromTitleAndTopBottomTooltip() {
        val inspection = FrontendEditorModelResolver.inspect(
            FakeEditor(
                FakeEditorModel(
                    name = FakeProperty("abc123 src/main/App.kt"),
                    tabTitle = FakeProperty(null),
                    provider = FakeProvider("diff-preview"),
                    topBottomComponentsUpdates = FakeProperty(
                        arrayOf(
                            FakeUpdate(
                                componentId = "header",
                                beControl = FakeBeControl(
                                    tooltip = FakeProperty("~/repo/src/main/App.kt"),
                                    controlId = FakeProperty("src/main/App.kt"),
                                ),
                            )
                        )
                    ),
                ),
            ),
            "/repo",
        )

        assertEquals("~/repo/src/main/App.kt", inspection?.resolvedPath)
        assertEquals("abc123 src/main/App.kt", inspection?.title)
        assertEquals("diff-preview", inspection?.providerId)
    }

    @Test
    fun resolvesPathFromCommitTabTitle() {
        val inspection = FrontendEditorModelResolver.inspect(
            FakeEditor(
                FakeEditorModel(
                    name = FakeProperty("Diff"),
                    tabTitle = FakeProperty("Commit: gradle.properties"),
                    provider = FakeProvider("BackendDiffFileEditorProvider"),
                    topBottomComponentsUpdates = FakeProperty(null),
                ),
            ),
            "/repo",
        )

        assertEquals("/repo/gradle.properties", inspection?.resolvedPath)
        assertEquals("Commit: gradle.properties", inspection?.tabTitle)
    }

    @Test
    fun resolvesPathFromNestedTopBottomControlText() {
        val inspection = FrontendEditorModelResolver.inspect(
            FakeEditor(
                FakeEditorModel(
                    name = FakeProperty("Diff"),
                    tabTitle = FakeProperty("Commit"),
                    provider = FakeProvider("BackendDiffFileEditorProvider"),
                    topBottomComponentsUpdates = FakeProperty(
                        arrayOf(
                            FakeUpdate(
                                componentId = "header",
                                beControl = FakeBeControl(
                                    bindableChildren = listOf(
                                        FakeBeControl(
                                            text = FakeProperty("9af31d2 src/main/App.kt"),
                                        )
                                    ),
                                ),
                            )
                        )
                    ),
                ),
            ),
            "/repo",
        )

        assertEquals("/repo/src/main/App.kt", inspection?.resolvedPath)
    }

    @Test
    fun returnsNullWhenEditorHasNoModel() {
        assertNull(FrontendEditorModelResolver.inspect(Any(), "/repo"))
    }

    private class FakeEditor(@Suppress("unused") private val editorModel: FakeEditorModel)

    private class FakeEditorModel(
        private val name: FakeProperty<String?>,
        private val tabTitle: FakeProperty<String?>,
        private val provider: FakeProvider,
        private val topBottomComponentsUpdates: FakeProperty<Array<FakeUpdate>?>,
    ) {
        fun getName(): FakeProperty<String?> = name
        fun getTabTitle(): FakeProperty<String?> = tabTitle
        fun getProvider(): FakeProvider = provider
        fun getTopBottomComponentsUpdates(): FakeProperty<Array<FakeUpdate>?> = topBottomComponentsUpdates
    }

    private class FakeProvider(private val providerId: String) {
        fun getProviderId(): String = providerId
    }

    private class FakeUpdate(
        private val componentId: String,
        private val beControl: FakeBeControl,
    ) {
        fun getComponentId(): String = componentId
        fun getBeControl(): FakeBeControl = beControl
    }

    private class FakeBeControl(
        private val tooltip: FakeProperty<String?> = FakeProperty(null),
        private val controlId: FakeProperty<String?> = FakeProperty(null),
        private val text: FakeProperty<String?> = FakeProperty(null),
        private val bindableChildren: List<Any> = emptyList(),
    ) {
        fun getTooltip(): FakeProperty<String?> = tooltip
        fun getControlId(): FakeProperty<String?> = controlId
        fun getText(): FakeProperty<String?> = text
        fun getBindableChildren(): List<Any> = bindableChildren
    }

    private class FakeProperty<T>(private val value: T) {
        fun getValue(): T = value
    }
}
