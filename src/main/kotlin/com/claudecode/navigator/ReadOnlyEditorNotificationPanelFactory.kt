package com.claudecode.navigator

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.EditorNotificationPanel

internal object ReadOnlyEditorNotificationPanelFactory {
    private const val TITLE = "Agent Read-Only Editors"
    private const val BANNER_TEXT =
        "Agent read-only editors are on. Direct typing is disabled; use Tools > Agent Read-Only Editors or Find Action (Ctrl/Cmd+Shift+A) to disable."
    private const val HOW_TO_DISABLE_TEXT =
        "To allow direct editing, disable Agent Read-Only Editors from Tools > Agent Read-Only Editors, or open Find Action with Ctrl+Shift+A on Windows/Linux or Cmd+Shift+A on macOS and search for Agent Read-Only Editors."

    fun create(
        project: Project,
        service: ReadOnlyEditorService,
        onDismiss: (() -> Unit)? = null,
    ): EditorNotificationPanel {
        return EditorNotificationPanel().apply {
            text = BANNER_TEXT
            createActionLabel("How to Disable") {
                Messages.showInfoMessage(project, HOW_TO_DISABLE_TEXT, TITLE)
            }
            createActionLabel("Disable Agent Read-Only Editors") {
                service.setEnabled(false)
            }
            if (onDismiss != null) {
                createActionLabel("Dismiss") {
                    onDismiss()
                }
            }
        }
    }
}
