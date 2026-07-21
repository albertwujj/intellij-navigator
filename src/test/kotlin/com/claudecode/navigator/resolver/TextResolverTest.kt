package com.claudecode.navigator.resolver

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TextResolverTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private lateinit var resolver: TextResolver

    override fun setUp() {
        super.setUp()
        // Copy test project files into the test fixture
        myFixture.copyDirectoryToProject("project", "")
        resolver = TextResolver(project)
    }

    fun `test resolve text finds matching lines`() {
        val targets = resolver.resolve("class UserModel")

        // Should find UserModel class definition in both files
        assertEquals(2, targets.size)
    }

    fun `test resolve text with fileHint filters results`() {
        val targets = resolver.resolve("class UserModel", "user.py")

        // Should only find in user.py
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve text with partial path fileHint`() {
        val targets = resolver.resolve("class UserModel", "models/user.py")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("models/user.py"))
    }

    fun `test resolve text with non-matching fileHint returns all results`() {
        // Soft matching: if hint matches nothing, return all results
        val targets = resolver.resolve("class UserModel", "nonexistent.py")

        // Should return both since hint matches nothing
        assertEquals(2, targets.size)
    }

    fun `test resolve unique text without fileHint`() {
        val targets = resolver.resolve("class ProductModel")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("product.py"))
    }

    fun `test resolve function definition`() {
        val targets = resolver.resolve("def helper():")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("utils.py"))
    }

    fun `test resolve method in class`() {
        val targets = resolver.resolve("def save(self):")

        // save() appears in multiple files
        assertTrue(targets.size >= 2)
    }

    fun `test resolve method with fileHint`() {
        val targets = resolver.resolve("def save(self):", "user.py")

        // Should only find save in user.py
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve with empty text returns empty`() {
        val targets = resolver.resolve("")

        assertTrue(targets.isEmpty())
    }

    fun `test resolve with whitespace only returns empty`() {
        val targets = resolver.resolve("   ")

        assertTrue(targets.isEmpty())
    }

    fun `test resolve nonexistent text returns empty`() {
        val targets = resolver.resolve("this text does not exist anywhere")

        assertTrue(targets.isEmpty())
    }

    fun `test resolve with empty fileHint returns all results`() {
        val targets = resolver.resolve("class UserModel", "")

        // Empty hint should be ignored
        assertEquals(2, targets.size)
    }

    fun `test resolve partial line match`() {
        val targets = resolver.resolve("Save the user")

        // Should find the docstring in user.py
        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }

    fun `test resolve returns correct line numbers`() {
        val targets = resolver.resolve("class ProductModel")

        assertEquals(1, targets.size)
        // ProductModel is on line 1 (0-indexed: 0)
        assertEquals(0, targets[0].line)
    }

    fun `test resolve comment text`() {
        val targets = resolver.resolve("User model for the application")

        assertEquals(1, targets.size)
        assertTrue(targets[0].file.path.contains("user.py"))
    }
}
