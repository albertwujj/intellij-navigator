package com.claudecode.navigator.resolver

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class FileResolverTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var resolver: FileResolver

    override fun setUp() {
        super.setUp()
        // Copy test project files into the test fixture
        myFixture.copyDirectoryToProject("project", "")
        resolver = FileResolver(project)
    }

    fun `test resolve file by name`() {
        val targets = resolver.resolve("user.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve file by partial path`() {
        val targets = resolver.resolve("models/user.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("models/user.py"))
    }

    fun `test resolve file by full relative path`() {
        val targets = resolver.resolve("handlers/api.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("handlers/api.py"))
    }

    fun `test resolve nonexistent file returns empty`() {
        val targets = resolver.resolve("nonexistent.py")

        assertTrue(targets.isEmpty())
    }

    fun `test resolve with wrong directory returns empty`() {
        val targets = resolver.resolve("wrong/user.py")

        // user.py is in models/, not wrong/
        assertTrue(targets.isEmpty())
    }

    fun `test resolve utils file`() {
        val targets = resolver.resolve("utils.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("utils.py"))
    }

    fun `test resolve all py files in models`() {
        val userTargets = resolver.resolve("models/user.py")
        val productTargets = resolver.resolve("models/product.py")

        assertEquals(1, userTargets.size)
        assertEquals(1, productTargets.size)
    }
}
