package com.claudecode.navigator.frontend

internal object TransportPathResolver {
    fun normalize(path: String, projectBasePath: String?): String {
        val normalizedPath = path.replace('\\', '/')
        val normalizedBase = projectBasePath
            ?.replace('\\', '/')
            ?.trimEnd('/')
            ?: return normalizedPath

        if (!normalizedBase.endsWith("/thinProject")) {
            return normalizedPath
        }

        val prefix = "$normalizedBase/"
        if (!normalizedPath.startsWith(prefix)) {
            return normalizedPath
        }

        return normalizedPath.removePrefix(prefix)
    }
}
