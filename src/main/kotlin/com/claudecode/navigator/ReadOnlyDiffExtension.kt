package com.claudecode.navigator

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor

/**
 * Locks diff editors AFTER the viewer has populated them. The initial setText path
 * needs the document writable; we attach a DiffViewerListener so onAfterRediff /
 * onInit lock the editors once the diff content is loaded.
 */
class ReadOnlyDiffExtension : DiffExtension() {
    override fun onViewerCreated(
        viewer: FrameDiffTool.DiffViewer,
        context: DiffContext,
        request: DiffRequest,
    ) {
        val project = context.project ?: return
        val service = ReadOnlyEditorService.getInstance(project)
        if (!service.isEnabled()) return

        val listener = object : DiffViewerListener() {
            override fun onInit() {
                lockDiffEditors(service, viewer)
            }

            override fun onAfterRediff() {
                lockDiffEditors(service, viewer)
            }
        }
        (viewer as? DiffViewerBase)?.addListener(listener)

        ApplicationManager.getApplication().invokeLater {
            lockDiffEditors(service, viewer)
        }
    }

    private fun lockDiffEditors(
        service: ReadOnlyEditorService,
        viewer: FrameDiffTool.DiffViewer,
    ) {
        val editors = (viewer as? EditorDiffViewer)?.editors ?: return
        service.applyToDiffEditors(editors.filterIsInstance<Editor>())
    }
}
