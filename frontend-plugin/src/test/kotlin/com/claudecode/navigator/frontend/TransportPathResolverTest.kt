package com.claudecode.navigator.frontend

import kotlin.test.Test
import kotlin.test.assertEquals

class TransportPathResolverTest {

    @Test
    fun keepsRegularAbsolutePathOutsideThinProject() {
        val result = TransportPathResolver.normalize(
            path = "/repo/project/src/App.kt",
            projectBasePath = "/repo/project",
        )

        assertEquals("/repo/project/src/App.kt", result)
    }

    @Test
    fun stripsThinProjectPrefixFromAbsolutePath() {
        val result = TransportPathResolver.normalize(
            path = "/Users/test/Library/Caches/JetBrains/JetBrainsClient241/tmp/per_process_config_0/thinProject/src/App.kt",
            projectBasePath = "/Users/test/Library/Caches/JetBrains/JetBrainsClient241/tmp/per_process_config_0/thinProject",
        )

        assertEquals("src/App.kt", result)
    }

    @Test
    fun stripsWindowsThinProjectPrefixFromAbsolutePath() {
        val result = TransportPathResolver.normalize(
            path = "C:/Users/test/AppData/Local/JetBrains/JetBrainsClient241/tmp/per_process_config_0/thinProject/gradle.properties",
            projectBasePath = "C:/Users/test/AppData/Local/JetBrains/JetBrainsClient241/tmp/per_process_config_0/thinProject",
        )

        assertEquals("gradle.properties", result)
    }

    @Test
    fun keepsThinProjectExternalPathWhenNotUnderProjectBase() {
        val result = TransportPathResolver.normalize(
            path = "/tmp/other/gradle.properties",
            projectBasePath = "/Users/test/Library/Caches/JetBrains/JetBrainsClient241/tmp/per_process_config_0/thinProject",
        )

        assertEquals("/tmp/other/gradle.properties", result)
    }
}
