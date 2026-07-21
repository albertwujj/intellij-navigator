package com.claudecode.navigator.model

import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
sealed class NavigationRequest {
    abstract val type: String

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parse(jsonString: String): NavigationRequest {
            return json.decodeFromString<NavigationRequest>(jsonString)
        }
    }
}

@Serializable
@SerialName("file")
data class FileRequest(
    override val type: String = "file",
    val path: String,
    val line: Int? = null,
    val matchText: String? = null
) : NavigationRequest()

@Serializable
@SerialName("symbol")
data class SymbolRequest(
    override val type: String = "symbol",
    val name: String,
    val fileHint: String? = null
) : NavigationRequest()

@Serializable
@SerialName("text")
data class TextRequest(
    override val type: String = "text",
    val text: String,
    val fileHint: String? = null
) : NavigationRequest()

@Serializable
@SerialName("caret")
data class CaretRequest(
    override val type: String = "caret"
) : NavigationRequest()

@Serializable
@SerialName("resolve_file")
data class ResolveFileRequest(
    override val type: String = "resolve_file",
    val path: String,
    val matchText: String? = null,
    val matchTextCandidates: List<String>? = null,
) : NavigationRequest()

data class NavigationTarget(
    val file: VirtualFile,
    val line: Int = 0,
    val column: Int = 0,
    val description: String = ""
) {
    val displayPath: String
        get() = file.path

    val displayName: String
        get() = if (description.isNotEmpty()) {
            "$description (${file.name}:${line + 1})"
        } else {
            "${file.name}:${line + 1}"
        }
}

@Serializable
data class NavigationResponse(
    val status: String,
    val message: String? = null,
    val count: Int? = null,
    val file: String? = null,
    val relativePath: String? = null,
    val line: Int? = null,
    val column: Int? = null
) {
    companion object {
        private val json = Json { encodeDefaults = false }

        fun ok() = NavigationResponse(status = "ok")
        fun multiple(count: Int) = NavigationResponse(status = "multiple", count = count)

        fun NavigationResponse.toJson(): String = json.encodeToString(serializer(), this)
    }
}
