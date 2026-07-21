package com.claudecode.navigator.frontend

internal object ReactiveControlStringCollector {
    data class Inspection(
        val candidates: List<String>,
        val details: List<String>,
    )

    fun inspect(root: Any?, maxNodes: Int = 80, maxDetails: Int = 40): Inspection {
        if (root == null) {
            return Inspection(
                candidates = emptyList(),
                details = emptyList(),
            )
        }

        val candidates = linkedSetOf<String>()
        val details = mutableListOf<String>()
        val visited = linkedSetOf<Int>()
        val queue = ArrayDeque<Pair<Any, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty() && visited.size < maxNodes) {
            val (node, depth) = queue.removeFirst()
            if (!visited.add(System.identityHashCode(node))) continue

            val strings = readCandidateStrings(node)
            candidates.addAll(strings)

            if (details.size < maxDetails) {
                details += buildString {
                    append("depth=").append(depth)
                    append(" class=").append(node.javaClass.name)
                    if (strings.isNotEmpty()) append(" strings=").append(strings)
                }
            }

            traverseChildren(node).forEach { queue.add(it to (depth + 1)) }
        }

        return Inspection(
            candidates = candidates.toList(),
            details = details,
        )
    }

    private fun readCandidateStrings(node: Any): List<String> {
        val values = listOfNotNull(
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getTooltip")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getControlId")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getDataId")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getUniqueId")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getText")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getTitle")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getName")),
            readReactiveString(ReflectiveMemberLookup.invokeZeroArgMember(node, "getLabel")),
            readReactiveString(ReflectiveMemberLookup.readNamedMember(node, "text")),
            readReactiveString(ReflectiveMemberLookup.readNamedMember(node, "title")),
            readReactiveString(ReflectiveMemberLookup.readNamedMember(node, "name")),
            readReactiveString(ReflectiveMemberLookup.readNamedMember(node, "label")),
        )

        return values
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun traverseChildren(node: Any): List<Any> {
        val nested = linkedSetOf<Any>()

        ReflectiveMemberLookup.readListMember(node, "getBindableChildren")
            .flatMap(::unwrapChild)
            .forEach(nested::add)

        ReflectiveMemberLookup.readListMember(node, "getChildren")
            .flatMap(::unwrapChild)
            .forEach(nested::add)

        listOf(
            "getBeControl",
            "getContent",
            "getControl",
            "getCurrentControl",
            "getCurrent",
            "getViewer",
            "getModel",
            "getValue",
            "getPresentation",
            "getHeader",
            "getLeft",
            "getRight",
        ).forEach { memberName ->
            ReflectiveMemberLookup.invokeZeroArgMember(node, memberName)
                ?.let(::readReactiveValue)
                ?.takeIf(::looksTraversable)
                ?.let(nested::add)
        }

        return nested.toList()
    }

    private fun unwrapChild(child: Any): List<Any> {
        return when (child) {
            is Pair<*, *> -> listOfNotNull(
                child.first?.let(::readReactiveValue)?.takeIf(::looksTraversable),
                child.second?.let(::readReactiveValue)?.takeIf(::looksTraversable),
            )
            else -> listOfNotNull(
                readReactiveValue(child)?.takeIf(::looksTraversable),
            )
        }
    }

    private fun looksTraversable(value: Any): Boolean {
        if (value is String) return false
        if (value is Number) return false
        if (value is Boolean) return false
        if (value is Enum<*>) return false
        return true
    }

    private fun readReactiveString(property: Any?): String? {
        return when (val value = readReactiveValue(property)) {
            is String -> value
            else -> null
        }
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
        }.getOrNull() ?: property
    }
}
