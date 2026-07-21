package com.claudecode.navigator.frontend

import java.lang.reflect.Field
import java.lang.reflect.Method

internal object ReflectiveGraphExplorer {
    data class Inspection(
        val targetPath: String,
        val targetClass: String?,
        val lines: List<String>,
    )

    fun inspect(
        rootName: String,
        rootValue: Any?,
        memberPath: String?,
        depth: Int,
        maxNodes: Int,
        maxMembers: Int,
    ): Inspection {
        if (rootValue == null) {
            return Inspection(
                targetPath = rootName,
                targetClass = null,
                lines = listOf("$rootName=null"),
            )
        }

        val resolved = resolveTarget(rootName, rootValue, memberPath)
        val target = resolved.value
        if (target == null) {
            return Inspection(
                targetPath = resolved.path,
                targetClass = null,
                lines = buildList {
                    add("targetPath=${resolved.path}")
                    add("targetClass=null")
                    resolved.error?.let(::add)
                },
            )
        }

        val lines = mutableListOf<String>()
        lines += "targetPath=${resolved.path}"
        lines += "targetClass=${target.javaClass.name}"
        resolved.error?.let(lines::add)

        val visited = linkedSetOf<Int>()
        val queue = ArrayDeque<QueuedValue>()
        queue += QueuedValue(resolved.path, target, 0)

        while (queue.isNotEmpty() && visited.size < maxNodes) {
            val current = queue.removeFirst()
            if (!visited.add(System.identityHashCode(current.value))) continue

            lines += "${current.path} class=${current.value.javaClass.name}"
            val members = readableMembers(current.value).take(maxMembers)
            if (members.isEmpty()) {
                lines += "${current.path} members=[]"
                continue
            }

            for (member in members) {
                val rawValue = member.read(current.value)
                val description = describeValue(rawValue)
                lines += "${current.path}.${member.name} -> $description"

                if (current.depth >= depth) continue
                traversalChildren(member.name, rawValue).forEach { child ->
                    queue += QueuedValue("${current.path}.${child.pathSuffix}", child.value, current.depth + 1)
                }
            }
        }

        if (queue.isNotEmpty()) {
            lines += "truncated=true"
        }

        return Inspection(
            targetPath = resolved.path,
            targetClass = target.javaClass.name,
            lines = lines,
        )
    }

    private data class QueuedValue(
        val path: String,
        val value: Any,
        val depth: Int,
    )

    private data class PathSegment(
        val name: String,
        val index: Int? = null,
    )

    private data class ResolvedTarget(
        val path: String,
        val value: Any?,
        val error: String? = null,
    )

    private data class ReadableMember(
        val name: String,
        val read: (Any) -> Any?,
    )

    private data class TraversalChild(
        val pathSuffix: String,
        val value: Any,
    )

    private fun resolveTarget(rootName: String, rootValue: Any, memberPath: String?): ResolvedTarget {
        val segments = parsePath(memberPath)
        var current: Any? = rootValue
        var currentPath = rootName

        for (segment in segments) {
            val target = current ?: return ResolvedTarget(
                path = currentPath,
                value = null,
                error = "pathResolutionFailed=null at $currentPath while resolving ${segment.name}",
            )
            val rawMember = ReflectiveMemberLookup.readNamedMember(target, segment.name)
                ?: return ResolvedTarget(
                    path = currentPath,
                    value = null,
                    error = "pathResolutionFailed=missing member ${segment.name} on ${target.javaClass.name}; available=${readableMembers(target).map(ReadableMember::name)}",
                )
            val baseValue = unwrapTraversalValue(rawMember) ?: rawMember
            val indexedValue = segment.index?.let { resolveIndexedValue(baseValue, it) } ?: baseValue
            currentPath += "." + segment.name + (segment.index?.let { "[$it]" } ?: "")
            current = indexedValue
        }

        return ResolvedTarget(
            path = currentPath,
            value = current,
        )
    }

    private fun parsePath(memberPath: String?): List<PathSegment> {
        if (memberPath.isNullOrBlank()) return emptyList()

        return memberPath.split('.')
            .filter { it.isNotBlank() }
            .map { rawSegment ->
                val match = Regex("""^([A-Za-z_][A-Za-z0-9_]*)(?:\[(\d+)])?$""").matchEntire(rawSegment)
                if (match != null) {
                    PathSegment(
                        name = match.groupValues[1],
                        index = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt(),
                    )
                } else {
                    PathSegment(name = rawSegment)
                }
            }
    }

    private fun resolveIndexedValue(value: Any?, index: Int): Any? {
        return when (value) {
            null -> null
            is Array<*> -> value.getOrNull(index)
            is List<*> -> value.getOrNull(index)
            is Iterable<*> -> value.drop(index).firstOrNull()
            else -> null
        }
    }

    private fun readableMembers(target: Any): List<ReadableMember> {
        val methodsByName = linkedMapOf<String, Method>()
        for (method in target.javaClass.methods.sortedBy(Method::getName)) {
            if (!isReadableMethod(method)) continue
            val memberName = methodMemberName(method) ?: continue
            methodsByName.putIfAbsent(memberName, method)
        }

        val fieldsByName = linkedMapOf<String, Field>()
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            for (field in currentClass.declaredFields.sortedBy(Field::getName)) {
                if (field.isSynthetic || field.name.startsWith("$")) continue
                fieldsByName.putIfAbsent(field.name, field)
            }
            currentClass = currentClass.superclass
        }

        val memberNames = linkedSetOf<String>().apply {
            addAll(methodsByName.keys)
            addAll(fieldsByName.keys)
        }

        return memberNames.map { memberName ->
            val method = methodsByName[memberName]
            val field = fieldsByName[memberName]
            ReadableMember(memberName) { instance ->
                when {
                    method != null -> runCatching { method.invoke(instance) }.getOrElse { it }
                    field != null -> runCatching {
                        field.isAccessible = true
                        field.get(instance)
                    }.getOrElse { it }
                    else -> null
                }
            }
        }
    }

    private fun isReadableMethod(method: Method): Boolean {
        if (method.parameterCount != 0) return false
        if (method.returnType == Void.TYPE) return false
        if (method.isSynthetic || method.isBridge) return false
        if (method.name in setOf("getClass", "hashCode", "toString", "clone", "finalize")) return false
        return true
    }

    private fun methodMemberName(method: Method): String? {
        return when {
            method.name.startsWith("get") && method.name.length > 3 ->
                method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
            method.name.startsWith("is") && method.name.length > 2 ->
                method.name.removePrefix("is").replaceFirstChar { it.lowercase() }
            else -> method.name
        }
    }

    private fun describeValue(value: Any?): String {
        if (value == null) return "null"
        if (value is Throwable) {
            return "error=${value.javaClass.name}:${value.message ?: ""}"
        }

        val unwrapped = unwrapTraversalValue(value)
        val baseDescription = when {
            isSimpleValue(value) -> "${value.javaClass.name}(${value.toString().take(120)})"
            value is Array<*> -> "${value.javaClass.name}(size=${value.size})"
            value is Collection<*> -> "${value.javaClass.name}(size=${value.size})"
            value is Map<*, *> -> "${value.javaClass.name}(size=${value.size})"
            else -> value.javaClass.name
        }

        if (unwrapped == null || unwrapped === value) return baseDescription

        val unwrappedDescription = when {
            isSimpleValue(unwrapped) -> "${unwrapped.javaClass.name}(${unwrapped.toString().take(120)})"
            unwrapped is Array<*> -> "${unwrapped.javaClass.name}(size=${unwrapped.size})"
            unwrapped is Collection<*> -> "${unwrapped.javaClass.name}(size=${unwrapped.size})"
            else -> unwrapped.javaClass.name
        }
        return "$baseDescription => $unwrappedDescription"
    }

    private fun traversalChildren(memberName: String, rawValue: Any?): List<TraversalChild> {
        if (rawValue == null) return emptyList()

        val unwrapped = unwrapTraversalValue(rawValue)
        if (unwrapped != null && unwrapped !== rawValue && isTraversable(unwrapped)) {
            return listOf(TraversalChild(memberName, unwrapped))
        }

        return when (rawValue) {
            is Array<*> -> rawValue.mapIndexedNotNull { index, item ->
                item?.takeIf(::isTraversable)?.let { TraversalChild("$memberName[$index]", it) }
            }.take(5)
            is Iterable<*> -> rawValue.mapIndexedNotNull { index, item ->
                item?.takeIf(::isTraversable)?.let { TraversalChild("$memberName[$index]", it) }
            }.take(5)
            else -> listOfNotNull(
                rawValue.takeIf(::isTraversable)?.let { TraversalChild(memberName, it) },
            )
        }
    }

    private fun unwrapTraversalValue(value: Any?): Any? {
        if (value == null) return null
        if (value is Throwable) return value

        listOf("getValue", "getValueOrNull", "getOrNull").forEach { memberName ->
            ReflectiveMemberLookup.invokeZeroArgMember(value, memberName)?.let { return it }
        }

        return runCatching {
            val interfacesKt = Class.forName("com.jetbrains.rd.util.reactive.InterfacesKt")
            interfacesKt.methods
                .firstOrNull { method ->
                    method.name == "getValueOrThrow" && method.parameterCount == 1
                }
                ?.invoke(null, value)
        }.getOrNull()
    }

    private fun isTraversable(value: Any): Boolean {
        if (value is Throwable) return false
        if (value.javaClass.name.startsWith("java.lang.")) return false
        if (value is Enum<*>) return false
        return true
    }

    private fun isSimpleValue(value: Any): Boolean {
        return value is String ||
            value is Number ||
            value is Boolean ||
            value is Char ||
            value is Enum<*> ||
            value.javaClass.name.startsWith("java.lang.")
    }
}
