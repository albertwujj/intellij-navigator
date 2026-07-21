package com.claudecode.navigator

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ReadOnlyModificationException
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotifications
import java.util.Collections
import java.util.WeakHashMap

/**
 * Backend twin of the frontend ReadOnlyEditorService. In Remote Dev / Split Mode the
 * Client's listener fires only after PatchEngineDocumentSynchronizer has applied changes
 * synced from this backend, and IntelliJ's EventDispatcher swallows the throw — so the
 * block has to live where the action actually runs.
 */
@Service(Service.Level.PROJECT)
class ReadOnlyEditorService(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(ReadOnlyEditorService::class.java)
    private val lockedEditors = Collections.newSetFromMap(WeakHashMap<Editor, Boolean>())
    private val lockedDocuments = Collections.newSetFromMap(WeakHashMap<Document, Boolean>())

    @Volatile
    private var started = false

    @Volatile
    private var enabled = true

    @Volatile
    private var notificationDismissed = false

    private val editorFactoryListener = object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            val editor = event.editor
            runOnEdt { applyToEditor(editor) }
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            lockedEditors.remove(event.editor)
        }
    }

    private val documentChangeBlocker = object : DocumentListener {
        override fun beforeDocumentChange(event: DocumentEvent) {
            if (!enabled || project.isDisposed) return
            if (event.isWholeTextReplaced) return
            val document = event.document
            if (!lockedDocuments.contains(document)) return
            throw ReadOnlyModificationException(
                document,
                "Document is locked by Agent read-only mode",
            )
        }
    }

    fun start() {
        if (started) return
        started = true

        EditorFactory.getInstance().addEditorFactoryListener(editorFactoryListener, this)
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentChangeBlocker, this)
        applyToOpenEditors()
        logger.info("Backend read-only editors enabled for project: ${project.name}")
    }

    fun stop() {
        if (!started) return
        unlockManagedEditors()
        started = false
    }

    fun isEnabled(): Boolean = enabled

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            notificationDismissed = false
            applyToOpenEditors()
            logger.info("Backend read-only editors enabled for project: ${project.name}")
        } else {
            unlockManagedEditors()
            logger.info("Backend read-only editors disabled for project: ${project.name}")
        }
        updateEditorNotifications()
    }

    fun isGuardedFile(file: VirtualFile): Boolean {
        if (!enabled || project.isDisposed) return false
        return file.isValid && !file.isDirectory
    }

    fun shouldShowNotification(file: VirtualFile): Boolean {
        return !notificationDismissed && isGuardedFile(file)
    }

    fun dismissNotification() {
        if (notificationDismissed) return
        notificationDismissed = true
        updateEditorNotifications()
    }

    private fun updateEditorNotifications() {
        runOnEdt {
            EditorNotifications.getInstance(project).updateAllNotifications()
        }
    }

    fun applyToDiffEditors(editors: Iterable<Editor>) {
        if (!enabled || project.isDisposed) return
        runOnEdt {
            if (!enabled || project.isDisposed) return@runOnEdt
            editors.forEach { editor ->
                if (editor.isDisposed) return@forEach
                val editorEx = editor as? EditorEx ?: return@forEach
                lockEditor(editorEx)
            }
        }
    }

    private fun applyToOpenEditors() {
        runOnEdt {
            EditorFactory.getInstance().allEditors.forEach(::applyToEditor)
        }
    }

    private fun applyToEditor(editor: Editor) {
        if (!enabled || !shouldGuard(editor)) return
        val editorEx = editor as? EditorEx ?: return
        lockEditor(editorEx)
    }

    private fun lockEditor(editor: EditorEx) {
        if (!editor.isViewer) {
            editor.setViewer(true)
        }
        lockedEditors.add(editor)
        // Don't call document.setReadOnly here — UnifiedDiffViewer.setText and
        // other whole-text-replace flows would throw at assertWriteAccess before
        // our DocumentListener can decide. The listener handles the partial-edit
        // block via beforeDocumentChange, skipping wholeTextReplaced.
        lockedDocuments.add(editor.document)
    }

    private fun unlockManagedEditors() {
        runOnEdt {
            val editorIterator = lockedEditors.iterator()
            while (editorIterator.hasNext()) {
                val editor = editorIterator.next()
                editorIterator.remove()
                if (editor.isDisposed) continue
                val editorEx = editor as? EditorEx ?: continue
                if (editorEx.isViewer) editorEx.setViewer(false)
            }
            lockedDocuments.clear()
        }
    }

    private fun shouldGuard(editor: Editor): Boolean {
        if (project.isDisposed || editor.isDisposed) return false
        // Skip DIFF — IntelliJ defaults diff editors to viewer mode, and the diff
        // viewer needs write access during initial population.
        return editor.editorKind == EditorKind.MAIN_EDITOR
    }

    private fun runOnEdt(action: () -> Unit) {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) {
            action()
        } else {
            application.invokeLater {
                if (!project.isDisposed) action()
            }
        }
    }

    override fun dispose() {
        stop()
    }

    companion object {
        fun getInstance(project: Project): ReadOnlyEditorService {
            return project.getService(ReadOnlyEditorService::class.java)
        }
    }
}
