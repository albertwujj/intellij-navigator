package com.claudecode.navigator.navigation

import com.claudecode.navigator.model.NavigationTarget
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.WindowManager
import java.awt.Frame

data class NavigationResult(val filePath: String, val line: Int, val column: Int, val diagMessage: String)

class Navigator(private val project: Project) {
    private val logger = Logger.getInstance(Navigator::class.java)
    private var activePopup: JBPopup? = null

    fun navigate(targets: List<NavigationTarget>): NavigationResult? {
        return openTargets(targets, ::navigateToTarget)
    }

    fun activate(targets: List<NavigationTarget>): NavigationResult? {
        return openTargets(targets, ::activateTarget)
    }

    private fun openTargets(
        targets: List<NavigationTarget>,
        openTarget: (NavigationTarget) -> NavigationResult
    ): NavigationResult? {
        if (targets.isEmpty()) {
            logger.warn("No targets to navigate to")
            return null
        }

        cancelActivePopup()
        bringToFront()

        return if (targets.size == 1) {
            openTarget(targets.first())
        } else {
            showSelectorPopup(targets, openTarget)
            null
        }
    }

    private fun cancelActivePopup() {
        activePopup?.let { popup ->
            if (!popup.isDisposed) {
                popup.cancel()
            }
        }
        activePopup = null
    }

    fun navigateToTarget(target: NavigationTarget): NavigationResult {
        logger.info("Navigating to ${target.file.path}:${target.line + 1}")

        val descriptor = OpenFileDescriptor(project, target.file, target.line, target.column)

        // Open file and move caret to the target position. On remote dev (WSL),
        // the caret position propagates to the thin client via Rd protocol.
        // Scrolling does NOT propagate (IJPL-168107) — the companion frontend
        // plugin on the thin client handles scrollToCaret separately.
        val editor = FileEditorManager.getInstance(project)
            .openTextEditor(descriptor, true)

        return if (editor != null) {
            // openTextEditor doesn't reliably reposition caret on already-open files.
            // navigateIn() forces caret to descriptor's line/column.
            descriptor.navigateIn(editor)
            val caret = editor.caretModel.logicalPosition
            val info = "caret=${caret.line + 1}:${caret.column}"
            logger.info("NAV_DIAG: $info")
            NavigationResult(target.file.path, caret.line + 1, caret.column, info)
        } else {
            logger.warn("Cannot navigate to ${target.file.path}: no editor returned")
            NavigationResult(target.file.path, target.line + 1, target.column, "no-editor")
        }
    }

    fun activateTarget(target: NavigationTarget): NavigationResult {
        logger.info("Activating ${target.file.path} without forcing caret position")

        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(target.file, true)

        val editor = fileEditorManager.selectedTextEditor?.takeIf { selectedEditor ->
            FileDocumentManager.getInstance().getFile(selectedEditor.document) == target.file
        }

        return if (editor != null) {
            val caret = editor.caretModel.logicalPosition
            val info = "caret=${caret.line + 1}:${caret.column}"
            logger.info("ACTIVATE_DIAG: $info")
            NavigationResult(target.file.path, caret.line + 1, caret.column, info)
        } else {
            logger.warn("Activated ${target.file.path} but no text editor is selected")
            NavigationResult(target.file.path, target.line + 1, target.column, "no-editor")
        }
    }

    private fun showSelectorPopup(
        targets: List<NavigationTarget>,
        openTarget: (NavigationTarget) -> NavigationResult
    ) {
        logger.info("Showing selector popup for ${targets.size} targets")
        activePopup = SelectorPopup(project, targets) { selected ->
            activePopup = null
            openTarget(selected)
        }.show()
    }

    private fun bringToFront() {
        val frame = WindowManager.getInstance().getFrame(project)
        if (frame != null) {
            if (frame.state == Frame.ICONIFIED) {
                frame.state = Frame.NORMAL
            }
            frame.toFront()
            frame.requestFocus()
        }
    }
}
