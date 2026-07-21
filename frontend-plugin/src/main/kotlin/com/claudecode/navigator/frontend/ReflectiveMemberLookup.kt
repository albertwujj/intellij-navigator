package com.claudecode.navigator.frontend

internal object ReflectiveMemberLookup {
    fun invokeMember(target: Any?, memberName: String, vararg args: Any?): Any? {
        if (target == null) return null

        return runCatching {
            target.javaClass.methods
                .firstOrNull { method ->
                    method.name == memberName && method.parameterCount == args.size
                }
                ?.invoke(target, *args)
        }.getOrNull()
    }

    fun invokeZeroArgMember(target: Any?, memberName: String): Any? {
        return invokeMember(target, memberName)
    }

    fun readNamedMember(target: Any?, memberName: String): Any? {
        if (target == null) return null

        readNamedMethod(target, memberName)?.let { return it }
        return readNamedField(target, memberName)
    }

    fun readListMember(target: Any?, memberName: String): List<Any> {
        if (target == null) return emptyList()

        val value = invokeZeroArgMember(target, memberName) ?: return emptyList()

        return when (value) {
            is List<*> -> value.filterNotNull()
            is Array<*> -> value.filterNotNull()
            is Iterable<*> -> value.filterNotNull().toList()
            else -> emptyList()
        }
    }

    fun <T : Any> readAssignableMember(target: Any, expectedType: Class<T>): T? {
        readFromMethods(target, expectedType)?.let { return it }
        return readFromFields(target, expectedType)
    }

    private fun <T : Any> readFromMethods(target: Any, expectedType: Class<T>): T? {
        for (method in target.javaClass.methods) {
            if (method.parameterCount != 0) continue
            if (!expectedType.isAssignableFrom(method.returnType)) continue
            if (method.name == "getClass") continue

            val value = runCatching { method.invoke(target) }.getOrNull() ?: continue
            return expectedType.cast(value)
        }
        return null
    }

    private fun <T : Any> readFromFields(target: Any, expectedType: Class<T>): T? {
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            for (field in currentClass.declaredFields) {
                if (!expectedType.isAssignableFrom(field.type)) continue

                val value = runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull() ?: continue
                return expectedType.cast(value)
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun readNamedMethod(target: Any, memberName: String): Any? {
        val getterName = "get" + memberName.replaceFirstChar { it.uppercase() }
        return target.javaClass.methods
            .firstOrNull { method ->
                method.parameterCount == 0 && (method.name == memberName || method.name == getterName)
            }
            ?.let { method -> runCatching { method.invoke(target) }.getOrNull() }
    }

    private fun readNamedField(target: Any, memberName: String): Any? {
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            val field = currentClass.declaredFields.firstOrNull { it.name == memberName }
            if (field != null) {
                return runCatching {
                    field.isAccessible = true
                    field.get(target)
                }.getOrNull()
            }
            currentClass = currentClass.superclass
        }
        return null
    }
}
