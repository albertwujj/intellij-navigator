package com.claudecode.navigator.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

internal class FrontendObjectExplorer(private val project: Project) {
    fun inspect(
        root: String,
        memberPath: String?,
        depth: Int,
        maxNodes: Int,
        maxMembers: Int,
    ): String {
        var result = ""
        ApplicationManager.getApplication().invokeAndWait {
            result = inspectOnEdt(root, memberPath, depth, maxNodes, maxMembers)
        }
        return result
    }

    private fun inspectOnEdt(
        root: String,
        memberPath: String?,
        depth: Int,
        maxNodes: Int,
        maxMembers: Int,
    ): String {
        val selectedEditors = SelectedEditorsResolver.read(project)
        val rootValue = resolveRoot(root, selectedEditors)
        val inspection = ReflectiveGraphExplorer.inspect(
            rootName = root,
            rootValue = rootValue,
            memberPath = memberPath,
            depth = depth.coerceIn(0, 6),
            maxNodes = maxNodes.coerceIn(1, 200),
            maxMembers = maxMembers.coerceIn(1, 80),
        )

        return buildList {
            add("projectBase=${project.basePath ?: "null"}")
            add("root=$root")
            add("memberPath=${memberPath ?: ""}")
            add("selectedEditor=${selectedEditors.selectedEditor?.javaClass?.name ?: "null"}")
            add("selectedTextEditor=${selectedEditors.selectedTextEditor?.javaClass?.name ?: "null"}")
            add("compositeEditors=${
                selectedEditors.compositeEditors.joinToString(prefix = "[", postfix = "]") { it.javaClass.name }
            }")
            addAll(inspection.lines)
        }.joinToString("\n")
    }

    private fun resolveRoot(root: String, selectedEditors: SelectedEditors): Any? {
        return when {
            root == "selectedEditor" -> selectedEditors.selectedEditor
            root == "selectedTextEditor" -> selectedEditors.selectedTextEditor
            root == "firstComposite" -> selectedEditors.compositeEditors.firstOrNull()
            Regex("""^composite\[(\d+)]$""").matchEntire(root) != null -> {
                val index = Regex("""^composite\[(\d+)]$""").matchEntire(root)!!.groupValues[1].toInt()
                selectedEditors.compositeEditors.getOrNull(index)
            }
            else -> null
        }
    }
}
