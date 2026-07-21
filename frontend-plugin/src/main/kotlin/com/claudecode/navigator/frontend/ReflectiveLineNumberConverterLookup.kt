package com.claudecode.navigator.frontend

import com.intellij.openapi.editor.LineNumberConverter

internal object ReflectiveLineNumberConverterLookup {
    data class Candidate(
        val label: String,
        val converter: Any,
    )

    fun find(rootLabel: String, target: Any?): List<Candidate> {
        if (target == null) return emptyList()

        val results = mutableListOf<Candidate>()
        val seen = linkedSetOf<Int>()

        target.javaClass.methods
            .asSequence()
            .filter { method ->
                method.parameterCount == 0 &&
                    method.name != "getClass" &&
                    looksRelevant(method.name)
            }
            .forEach { method ->
                val value = runCatching { method.invoke(target) }.getOrNull()
                maybeAdd("$rootLabel.${method.name}", value, results, seen)
            }

        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            currentClass.declaredFields
                .asSequence()
                .filter { field -> looksRelevant(field.name) }
                .forEach { field ->
                    val value = runCatching {
                        field.isAccessible = true
                        field.get(target)
                    }.getOrNull()
                    maybeAdd("$rootLabel.${field.name}", value, results, seen)
                }
            currentClass = currentClass.superclass
        }

        return results
    }

    private fun maybeAdd(
        label: String,
        value: Any?,
        results: MutableList<Candidate>,
        seen: MutableSet<Int>,
    ) {
        val converter = value ?: return
        if (!isConverter(converter)) return

        val identity = System.identityHashCode(converter)
        if (!seen.add(identity)) return
        results += Candidate(label = label, converter = converter)
    }

    private fun looksRelevant(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.contains("line") || normalized.contains("converter") || normalized.contains("gutter")
    }

    private fun isConverter(value: Any): Boolean {
        return value is LineNumberConverter ||
            value.javaClass.name.contains("LineNumberConverter") ||
            value.javaClass.name.contains("LineNumberConvertor")
    }
}
