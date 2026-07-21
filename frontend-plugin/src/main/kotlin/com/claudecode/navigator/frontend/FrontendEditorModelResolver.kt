package com.claudecode.navigator.frontend

internal object FrontendEditorModelResolver {
    data class Inspection(
        val editorClass: String,
        val modelClass: String?,
        val providerId: String?,
        val title: String?,
        val tabTitle: String?,
        val candidates: List<String>,
        val resolvedPath: String?,
    )

    fun inspect(editor: Any?, projectBasePath: String?): Inspection? {
        if (editor == null) return null

        val model = ReflectiveMemberLookup.readNamedMember(editor, "editorModel") ?: return null
        val title = readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(model, "getName"))
        val tabTitle = readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(model, "getTabTitle"))
        val providerId = ReflectiveMemberLookup.invokeZeroArgMember(
            ReflectiveMemberLookup.invokeZeroArgMember(model, "getProvider"),
            "getProviderId",
        ) as? String

        val candidates = buildList {
            tabTitle?.let(::add)
            title?.let(::add)
            addAll(readTopBottomCandidates(model))
        }.distinct()

        val inspection = DiffRequestFileResolver.inspectTitles(
            title = tabTitle ?: title,
            contentTitles = buildList {
                if (tabTitle != null && title != null) add(title)
                addAll(candidates.drop(if (tabTitle != null) 1 else 0))
            },
            projectBasePath = projectBasePath,
            sourceClass = model.javaClass.name,
        )

        return Inspection(
            editorClass = editor.javaClass.name,
            modelClass = model.javaClass.name,
            providerId = providerId,
            title = title,
            tabTitle = tabTitle,
            candidates = candidates,
            resolvedPath = inspection.resolvedPath,
        )
    }

    private fun readTopBottomCandidates(model: Any): List<String> {
        val updatesProperty = ReflectiveMemberLookup.invokeZeroArgMember(model, "getTopBottomComponentsUpdates")
        val updates = readReactiveValue(updatesProperty)

        return when (updates) {
            is Array<*> -> updates.filterNotNull().flatMap(::readUpdateCandidates)
            is Iterable<*> -> updates.filterNotNull().flatMap(::readUpdateCandidates)
            else -> emptyList()
        }
            .map(String::trim)
            .filter { it.isNotEmpty() }
    }

    private fun readUpdateCandidates(update: Any): List<String> {
        val inspection = ReactiveControlStringCollector.inspect(update, maxNodes = 40, maxDetails = 0)
        return buildList {
            (ReflectiveMemberLookup.invokeZeroArgMember(update, "getComponentId") as? String)?.let(::add)
            addAll(inspection.candidates)
        }
    }

    private fun readReactiveString(property: Any?): String? {
        return readReactiveValue(property) as? String
    }

    private fun readReactiveValue(property: Any?): Any? {
        if (property == null) return null

        listOf("getValue", "getValueOrNull", "getOrNull").forEach { memberName ->
            ReflectiveMemberLookup.invokeZeroArgMember(property, memberName)?.let { return it }
        }

        return runCatching {
            val interfacesKt = Class.forName("com.jetbrains.rd.util.reactive.InterfacesKt")
            interfacesKt.methods
                .firstOrNull { method ->
                    method.name == "getValueOrThrow" && method.parameterCount == 1
                }
                ?.invoke(null, property)
        }.getOrNull()
    }
}
