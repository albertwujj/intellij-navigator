package com.claudecode.navigator

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class ReadOnlyEditorNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile,
    ): Function<in FileEditor, out JComponent?>? {
        val service = ReadOnlyEditorService.getInstance(project)
        if (!service.shouldShowNotification(file)) return null

        return Function {
            ReadOnlyEditorNotificationPanelFactory.create(project, service) {
                service.dismissNotification()
            }
        }
    }
}
