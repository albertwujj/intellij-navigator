package com.claudecode.navigator.model

import org.junit.Assert.*
import org.junit.Test

class NavigationRequestTest {

    @Test
    fun `parse FileRequest with path and line`() {
        val json = """{"type":"file","path":"foo/bar.py","line":42}"""
        val request = NavigationRequest.parse(json) as FileRequest

        assertEquals("file", request.type)
        assertEquals("foo/bar.py", request.path)
        assertEquals(42, request.line)
    }

    @Test
    fun `parse FileRequest without line`() {
        val json = """{"type":"file","path":"test.py"}"""
        val request = NavigationRequest.parse(json) as FileRequest

        assertEquals("file", request.type)
        assertEquals("test.py", request.path)
        assertNull(request.line)
    }

    @Test
    fun `parse FileRequest ignores legacy mode field`() {
        val json = """{"type":"file","path":"test.py","mode":"activate"}"""
        val request = NavigationRequest.parse(json) as FileRequest

        assertEquals("file", request.type)
        assertEquals("test.py", request.path)
        assertNull(request.line)
        assertNull(request.matchText)
    }

    @Test
    fun `parse SymbolRequest without fileHint`() {
        val json = """{"type":"symbol","name":"MyClass.method"}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("symbol", request.type)
        assertEquals("MyClass.method", request.name)
        assertNull(request.fileHint)
    }

    @Test
    fun `parse SymbolRequest with fileHint`() {
        val json = """{"type":"symbol","name":"MyClass","fileHint":"models.py"}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("symbol", request.type)
        assertEquals("MyClass", request.name)
        assertEquals("models.py", request.fileHint)
    }

    @Test
    fun `parse SymbolRequest with partial path fileHint`() {
        val json = """{"type":"symbol","name":"UserModel","fileHint":"src/models/user.py"}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("symbol", request.type)
        assertEquals("UserModel", request.name)
        assertEquals("src/models/user.py", request.fileHint)
    }

    @Test
    fun `parse TextRequest without fileHint`() {
        val json = """{"type":"text","text":"def main():"}"""
        val request = NavigationRequest.parse(json) as TextRequest

        assertEquals("text", request.type)
        assertEquals("def main():", request.text)
        assertNull(request.fileHint)
    }

    @Test
    fun `parse TextRequest with fileHint`() {
        val json = """{"type":"text","text":"def process():","fileHint":"app.py"}"""
        val request = NavigationRequest.parse(json) as TextRequest

        assertEquals("text", request.type)
        assertEquals("def process():", request.text)
        assertEquals("app.py", request.fileHint)
    }

    @Test
    fun `parse TextRequest with partial path fileHint`() {
        val json = """{"type":"text","text":"class Handler:","fileHint":"api/handlers.py"}"""
        val request = NavigationRequest.parse(json) as TextRequest

        assertEquals("text", request.type)
        assertEquals("class Handler:", request.text)
        assertEquals("api/handlers.py", request.fileHint)
    }

    @Test
    fun `parse ignores unknown fields`() {
        val json = """{"type":"symbol","name":"Test","unknownField":"value","fileHint":"test.py"}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("Test", request.name)
        assertEquals("test.py", request.fileHint)
    }

    @Test
    fun `parse handles null fileHint explicitly`() {
        val json = """{"type":"symbol","name":"MyClass","fileHint":null}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("MyClass", request.name)
        assertNull(request.fileHint)
    }

    @Test
    fun `parse handles empty string fileHint`() {
        val json = """{"type":"symbol","name":"MyClass","fileHint":""}"""
        val request = NavigationRequest.parse(json) as SymbolRequest

        assertEquals("MyClass", request.name)
        assertEquals("", request.fileHint)
    }

    @Test
    fun `parse TextRequest with empty fileHint`() {
        val json = """{"type":"text","text":"test","fileHint":""}"""
        val request = NavigationRequest.parse(json) as TextRequest

        assertEquals("test", request.text)
        assertEquals("", request.fileHint)
    }

    @Test
    fun `parse ResolveFileRequest`() {
        val json = """{"type":"resolve_file","path":"src/main.py","matchText":"def main():","matchTextCandidates":["def main():","print('ok')"]}"""
        val request = NavigationRequest.parse(json) as ResolveFileRequest

        assertEquals("resolve_file", request.type)
        assertEquals("src/main.py", request.path)
        assertEquals("def main():", request.matchText)
        assertEquals(listOf("def main():", "print('ok')"), request.matchTextCandidates)
    }
}
