package com.claudecode.navigator

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

class ToggleReadOnlyEditorsAction : ToggleAction() {
    override fun isSelected(event: AnActionEvent): Boolean {
        val project = event.project ?: return false
        return ReadOnlyEditorService.getInstance(project).isEnabled()
    }

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val project = event.project ?: return
        ReadOnlyEditorService.getInstance(project).setEnabled(state)
    }

    override fun update(event: AnActionEvent) {
        super.update(event)

        val project = event.project
        event.presentation.isEnabled = project != null
        event.presentation.description = if (project != null && isSelected(event)) {
            "Allow direct typing in project source editors"
        } else {
            "Prevent direct typing in project source editors"
        }
    }
}
