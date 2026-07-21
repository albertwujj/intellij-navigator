package com.claudecode.navigator.frontend

internal object BeControlPathResolver {
    data class Inspection(
        val rootClass: String,
        val candidates: List<String>,
        val resolvedPath: String?,
        val details: List<String>,
    )

    fun inspect(editor: Any?, projectBasePath: String?): Inspection? {
        if (editor == null) return null

        val editorModel = ReflectiveMemberLookup.readNamedMember(editor, "editorModel") ?: return null
        val content = ReflectiveMemberLookup.invokeZeroArgMember(editorModel, "getContent") ?: return null
        val collected = ReactiveControlStringCollector.inspect(content)

        val inspection = DiffRequestFileResolver.inspectTitles(
            title = collected.candidates.firstOrNull(),
            contentTitles = collected.candidates.drop(1),
            projectBasePath = projectBasePath,
            sourceClass = content.javaClass.name,
        )

        return Inspection(
            rootClass = content.javaClass.name,
            candidates = collected.candidates,
            resolvedPath = inspection.resolvedPath,
            details = collected.details,
        )
    }
}
