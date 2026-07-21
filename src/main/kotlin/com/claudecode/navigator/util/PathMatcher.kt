package com.claudecode.navigator.util

object PathMatcher {
    /**
     * Checks if the candidate path matches the request path using suffix matching.
     *
     * The match is successful if either path ends with the other when compared
     * segment by segment from the end. This allows partial path matching where:
     * - "foo/bar.py" matches "project/src/foo/bar.py" (request is suffix of candidate)
     * - "project/src/foo/bar.py" matches "bar.py" (candidate ends with request)
     *
     * @param candidate The full path to check (typically from the file system)
     * @param request The path pattern to match against (from the navigation request)
     * @return true if the paths match, false otherwise
     */
    fun pathMatches(candidate: String, request: String): Boolean {
        val candidateSegments = normalize(candidate)
        val requestSegments = normalize(request)

        if (candidateSegments.isEmpty() || requestSegments.isEmpty()) {
            return false
        }

        val minLen = minOf(candidateSegments.size, requestSegments.size)

        for (i in 1..minLen) {
            val candidateSegment = candidateSegments[candidateSegments.size - i]
            val requestSegment = requestSegments[requestSegments.size - i]
            if (!candidateSegment.equals(requestSegment, ignoreCase = true)) {
                return false
            }
        }

        return true
    }

    /**
     * Normalizes a path by:
     * - Converting backslashes to forward slashes
     * - Removing trailing slashes
     * - Splitting into segments
     */
    private fun normalize(path: String): List<String> {
        return path
            .replace('\\', '/')
            .trimEnd('/')
            .let { if (it.startsWith("~/")) it.substring(2) else it }
            .split('/')
            .filter { it.isNotEmpty() }
    }

    /**
     * Extracts the filename from a path.
     */
    fun getFileName(path: String): String {
        val normalized = path.replace('\\', '/').trimEnd('/')
        val lastSlash = normalized.lastIndexOf('/')
        return if (lastSlash >= 0) {
            normalized.substring(lastSlash + 1)
        } else {
            normalized
        }
    }
}
