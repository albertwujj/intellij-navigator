package com.claudecode.navigator.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RequestHandlerTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var requestHandler: RequestHandler

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("project", "")
        requestHandler = RequestHandler(project)
    }

    fun `test file request without line activates file and omits coordinates`() {
        val response = requestHandler.handle("""{"type":"file","path":"utils.py"}""")

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/utils.py"))
        assertNull(response.line)
        assertNull(response.column)
        assertTrue(selectedFilePath().endsWith("/utils.py"))
    }

    fun `test file request without line preserves caret for already open file`() {
        val file = myFixture.findFileInTempDir("models/user.py")!!
        myFixture.openFileInEditor(file)

        val editor = selectedEditor()
        ApplicationManager.getApplication().invokeAndWait {
            editor.caretModel.moveToLogicalPosition(LogicalPosition(7, 4))
        }

        val response = requestHandler.handle("""{"type":"file","path":"models/user.py"}""")
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertNull(response.line)
        assertNull(response.column)
        assertEquals(7, caret.line)
        assertEquals(4, caret.column)
    }

    fun `test file request with line still navigates and returns coordinates`() {
        val response = requestHandler.handle("""{"type":"file","path":"models/user.py","line":8}""")
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals(8, response.line)
        assertEquals(0, response.column)
        assertEquals(7, caret.line)
        assertEquals(0, caret.column)
    }

    fun `test file request with matchText only still navigates and returns coordinates`() {
        val response = requestHandler.handle(
            """{"type":"file","path":"models/user.py","matchText":"def delete(self):"}"""
        )
        val caret = selectedEditor().caretModel.logicalPosition

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals(11, response.line)
        assertEquals(0, response.column)
        assertEquals(10, caret.line)
        assertEquals(0, caret.column)
    }

    fun `test resolve file request returns unique match without opening editor`() {
        val response = requestHandler.handle("""{"type":"resolve_file","path":"models/user.py"}""")

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals("models/user.py", response.relativePath)
        assertNull(response.line)
        assertNull(response.column)
    }

    fun `test resolve file request returns project relative path for absolute file`() {
        val file = myFixture.findFileInTempDir("models/user.py")!!

        val response = requestHandler.handle("""{"type":"resolve_file","path":"${file.path.replace("\\", "\\\\")}"}""")

        assertEquals("ok", response.status)
        assertEquals(file.path, response.file)
        assertEquals("models/user.py", response.relativePath)
    }

    fun `test resolve file request reports missing file`() {
        val response = requestHandler.handle("""{"type":"resolve_file","path":"missing.py"}""")

        assertEquals("not_found", response.status)
        assertEquals("file not found", response.message)
    }

    fun `test resolve file request reports ambiguity as error`() {
        myFixture.addFileToProject("handlers/user.py", "print('duplicate filename')\n")

        val response = requestHandler.handle("""{"type":"resolve_file","path":"user.py"}""")

        assertEquals("error", response.status)
        assertEquals("multiple files match: user.py", response.message)
        assertEquals(2, response.count)
    }

    fun `test resolve file request uses matchText to disambiguate duplicate basename`() {
        myFixture.addFileToProject("handlers/user.py", "print('duplicate filename')\n")

        val response = requestHandler.handle(
            """{"type":"resolve_file","path":"user.py","matchText":"def delete(self):"}"""
        )

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
    }

    fun `test resolve file request reports ambiguity when multiple duplicates contain text`() {
        myFixture.addFileToProject(
            "handlers/user.py",
            """
            class Handler:
                def delete(self):
                    pass
            """.trimIndent() + "\n"
        )

        val response = requestHandler.handle(
            """{"type":"resolve_file","path":"user.py","matchText":"def delete(self):"}"""
        )

        assertEquals("error", response.status)
        assertEquals("multiple files match: user.py", response.message)
        assertEquals(2, response.count)
    }

    fun `test resolve file request returns unique line from matchText candidates`() {
        val response = requestHandler.handle(
            """
            {
              "type":"resolve_file",
              "path":"models/user.py",
              "matchText":"missing line",
              "matchTextCandidates":["missing line","def delete(self):","class User:"]
            }
            """.trimIndent()
        )

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals("models/user.py", response.relativePath)
        assertEquals(11, response.line)
        assertEquals(0, response.column)
    }

    fun `test resolve file request uses candidates to disambiguate duplicate basename and resolve line`() {
        myFixture.addFileToProject("handlers/user.py", "print(\"handler duplicate\")\n")

        val response = requestHandler.handle(
            """
            {
              "type":"resolve_file",
              "path":"user.py",
              "matchText":"missing line",
              "matchTextCandidates":["missing line","def delete(self):"]
            }
            """.trimIndent()
        )

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/models/user.py"))
        assertEquals(11, response.line)
    }

    fun `test resolve file request prefers file matching most diff candidates`() {
        myFixture.addFileToProject(
            "gradle.properties",
            """
            pluginGroup = com.claudecode.navigator
            pluginName = IntelliJ Navigator
            pluginVersion = 1.0.1

            platformType = PY
            platformVersion = 2024.1

            org.gradle.jvmargs = -Xmx2048m
            kotlin.stdlib.default.dependency = false
            """.trimIndent() + "\n"
        )
        myFixture.addFileToProject(
            "frontend-plugin/gradle.properties",
            """
            pluginGroup = com.claudecode.navigator.frontend
            pluginName = IntelliJ Navigator Frontend
            pluginVersion = 1.0.5

            platformType = IC
            platformVersion = 2024.1

            org.gradle.jvmargs = -Xmx2048m
            kotlin.stdlib.default.dependency = false
            """.trimIndent() + "\n"
        )

        val response = requestHandler.handle(
            """
            {
              "type":"resolve_file",
              "path":"gradle.properties",
              "matchText":"pluginVersion = 1.0.1",
              "matchTextCandidates":[
                "pluginVersion = 1.0.1",
                "pluginVersion = 1.0.5",
                "platformType = IC",
                "platformVersion = 2024.1",
                "org.gradle.jvmargs = -Xmx2048m",
                "kotlin.stdlib.default.dependency = false",
                "pluginName = IntelliJ Navigator Frontend",
                "pluginGroup = com.claudecode.navigator.frontend"
              ]
            }
            """.trimIndent()
        )

        assertEquals("ok", response.status)
        assertTrue(response.file!!.endsWith("/frontend-plugin/gradle.properties"))
        assertEquals("frontend-plugin/gradle.properties", response.relativePath)
        assertEquals(3, response.line)
        assertEquals(0, response.column)
    }

    private fun selectedEditor() =
        FileEditorManager.getInstance(project).selectedTextEditor
            ?: error("Expected a selected text editor")

    private fun selectedFilePath(): String =
        FileDocumentManager.getInstance().getFile(selectedEditor().document)?.path
            ?: error("Expected selected editor to have a backing file")
}
