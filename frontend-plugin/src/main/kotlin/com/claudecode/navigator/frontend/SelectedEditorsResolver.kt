package com.claudecode.navigator.frontend

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project

internal data class SelectedEditors(
    val selectedEditor: Any?,
    val selectedTextEditor: Editor?,
    val compositeEditors: List<Any> = emptyList(),
)

internal object SelectedEditorsResolver {
    fun read(project: Project): SelectedEditors {
        selectedClientEditors(project)?.let { return it }

        val fileEditorManager = FileEditorManager.getInstance(project)
        return SelectedEditors(
            selectedEditor = fileEditorManager.selectedEditor,
            selectedTextEditor = fileEditorManager.selectedTextEditor,
            compositeEditors = listOfNotNull(fileEditorManager.selectedEditor),
        )
    }

    private fun selectedClientEditors(project: Project): SelectedEditors? {
        return runCatching {
            val managerClass = Class.forName("com.intellij.openapi.fileEditor.ClientFileEditorManager")
            val getCurrentInstance = managerClass.getMethod("getCurrentInstance", Project::class.java)
            val manager = getCurrentInstance.invoke(null, project) ?: return null

            val selectedEditor = managerClass.getMethod("getSelectedEditor").invoke(manager)
            val selectedTextEditor = managerClass.getMethod("getSelectedTextEditor").invoke(manager) as? Editor
            val selectedFile = managerClass.getMethod("getSelectedFile").invoke(manager)
            val compositeEditors = if (selectedFile != null) {
                val composite = managerClass.getMethod(
                    "getComposite",
                    Class.forName("com.intellij.openapi.vfs.VirtualFile"),
                ).invoke(manager, selectedFile)
                ReflectiveMemberLookup.readListMember(
                    target = composite,
                    memberName = "getAllEditors",
                )
            } else {
                emptyList()
            }

            SelectedEditors(
                selectedEditor = selectedEditor,
                selectedTextEditor = selectedTextEditor,
                compositeEditors = compositeEditors,
            )
        }.getOrNull()
    }
}
