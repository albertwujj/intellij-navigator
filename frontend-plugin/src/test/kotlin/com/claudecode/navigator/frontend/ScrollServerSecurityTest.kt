package com.claudecode.navigator.frontend

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ScrollServerSecurityTest : BasePlatformTestCase() {
    fun testDiagnosticActionsAreDisabledBeforeTheyInspectIdeObjects() = runBlocking {
        val property = "intellij.navigator.diagnostics"
        val previous = System.getProperty(property)
        System.clearProperty(property)
        try {
            val server = ScrollServer(project)
            for (action in listOf("caret_diagnostics", "explore_object", "diff_probe")) {
                // Deliberately omit action-specific arguments. The access gate
                // must reject this before decoding or traversing IDE objects.
                val response = Json.parseToJsonElement(server.handleRequest("""{"action":"$action"}""")).jsonObject
                assertEquals("error", response["status"]?.jsonPrimitive?.content)
                assertEquals("diagnostics disabled", response["message"]?.jsonPrimitive?.content)
            }
        } finally {
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
        }
    }

    fun testMalformedRequestDoesNotEchoItsContents() = runBlocking {
        val response = ScrollServer(project).handleRequest("private code snippet")
        assertFalse(response.contains("private code snippet"))
        assertEquals("error", Json.parseToJsonElement(response).jsonObject["status"]?.jsonPrimitive?.content)
    }
}
