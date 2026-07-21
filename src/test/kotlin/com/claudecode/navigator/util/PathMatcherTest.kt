package com.claudecode.navigator.util

import org.junit.Assert.*
import org.junit.Test

class PathMatcherTest {

    @Test
    fun `exact match returns true`() {
        assertTrue(PathMatcher.pathMatches("/foo/bar/test.py", "/foo/bar/test.py"))
    }

    @Test
    fun `request is suffix of candidate returns true`() {
        assertTrue(PathMatcher.pathMatches("/project/src/foo/bar.py", "foo/bar.py"))
        assertTrue(PathMatcher.pathMatches("/project/src/foo/bar.py", "bar.py"))
        assertTrue(PathMatcher.pathMatches("/project/src/foo/bar.py", "src/foo/bar.py"))
    }

    @Test
    fun `candidate ends with request returns true`() {
        assertTrue(PathMatcher.pathMatches("bar.py", "/project/src/foo/bar.py"))
        assertTrue(PathMatcher.pathMatches("foo/bar.py", "/very/long/path/foo/bar.py"))
    }

    @Test
    fun `partial segment mismatch returns false`() {
        assertFalse(PathMatcher.pathMatches("/foo/bar.py", "/foo/baz.py"))
        assertFalse(PathMatcher.pathMatches("/foo/test.py", "/bar/test.py"))
    }

    @Test
    fun `filename only matches any path with same filename`() {
        assertTrue(PathMatcher.pathMatches("/a/b/c/test.py", "test.py"))
        assertTrue(PathMatcher.pathMatches("/x/y/z/test.py", "test.py"))
    }

    @Test
    fun `handles backslash normalization`() {
        assertTrue(PathMatcher.pathMatches("C:\\Users\\foo\\bar.py", "foo/bar.py"))
        assertTrue(PathMatcher.pathMatches("/unix/path/file.py", "path\\file.py"))
    }

    @Test
    fun `handles trailing slashes`() {
        assertTrue(PathMatcher.pathMatches("/foo/bar/", "/foo/bar"))
        assertTrue(PathMatcher.pathMatches("/foo/bar", "/foo/bar/"))
    }

    @Test
    fun `empty paths return false`() {
        assertFalse(PathMatcher.pathMatches("", "/foo/bar.py"))
        assertFalse(PathMatcher.pathMatches("/foo/bar.py", ""))
        assertFalse(PathMatcher.pathMatches("", ""))
    }

    @Test
    fun `case insensitive matching`() {
        assertTrue(PathMatcher.pathMatches("/Foo/Bar.py", "foo/bar.py"))
        assertTrue(PathMatcher.pathMatches("/foo/bar.py", "Foo/Bar.py"))
    }

    @Test
    fun `getFileName extracts filename correctly`() {
        assertEquals("test.py", PathMatcher.getFileName("/foo/bar/test.py"))
        assertEquals("test.py", PathMatcher.getFileName("test.py"))
        assertEquals("test.py", PathMatcher.getFileName("C:\\Users\\foo\\test.py"))
        assertEquals("test.py", PathMatcher.getFileName("/test.py"))
    }

    @Test
    fun `tilde home path matches absolute path`() {
        assertTrue(PathMatcher.pathMatches(
            "/home/user/primary/modules/BPO/flow/epoch.py",
            "~/primary/modules/BPO/flow/epoch.py"
        ))
    }

    @Test
    fun `tilde path suffix matches`() {
        assertTrue(PathMatcher.pathMatches(
            "/Users/yunxin/project/src/utils.py",
            "~/project/src/utils.py"
        ))
    }

    @Test
    fun `getFileName handles tilde path`() {
        assertEquals("epoch.py", PathMatcher.getFileName("~/primary/modules/BPO/flow/epoch.py"))
    }

    @Test
    fun `different filenames do not match`() {
        assertFalse(PathMatcher.pathMatches("/foo/bar.py", "/foo/baz.py"))
        assertFalse(PathMatcher.pathMatches("/foo/test.py", "other.py"))
    }

    @Test
    fun `partial path narrows candidates`() {
        val candidates = listOf(
            "/project/src/module1/utils.py",
            "/project/src/module2/utils.py",
            "/project/lib/utils.py"
        )
        val pattern = "module1/utils.py"

        val filtered = candidates.filter { PathMatcher.pathMatches(it, pattern) }

        assertEquals(1, filtered.size)
        assertEquals("/project/src/module1/utils.py", filtered[0])
    }

    @Test
    fun `non-matching pattern returns empty`() {
        val candidates = listOf(
            "/project/src/module1/utils.py",
            "/project/src/module2/utils.py"
        )
        val pattern = "nonexistent/path.py"

        val filtered = candidates.filter { PathMatcher.pathMatches(it, pattern) }

        assertTrue(filtered.isEmpty())
    }

    @Test
    fun `subdirectory pattern filters correctly`() {
        val candidates = listOf(
            "/project/src/api/handlers.py",
            "/project/src/web/handlers.py",
            "/project/tests/handlers.py"
        )
        val pattern = "api/handlers.py"

        val filtered = candidates.filter { PathMatcher.pathMatches(it, pattern) }

        assertEquals(1, filtered.size)
        assertTrue(filtered[0].contains("api"))
    }
}
