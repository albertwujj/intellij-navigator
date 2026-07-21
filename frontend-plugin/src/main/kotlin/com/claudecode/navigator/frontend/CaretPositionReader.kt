package com.claudecode.navigator.frontend

import com.intellij.ide.DataManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.contents.FileContent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.diff.editor.DiffEditorViewerFileEditor
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.util.Side
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Component
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JComponent

internal data class CaretPosition(
    val file: String,
    val line: Int?,
    val column: Int,
    val matchText: String? = null,
    val matchTextCandidates: List<String>? = null,
)

internal sealed interface CaretReadResult {
    data class Success(val position: CaretPosition) : CaretReadResult
    data class Error(val message: String) : CaretReadResult
    data object NoActiveEditor : CaretReadResult
}

internal class CaretPositionReader(private val project: Project) {

    fun read(): CaretReadResult {
        var result: CaretReadResult = CaretReadResult.NoActiveEditor
        ApplicationManager.getApplication().invokeAndWait {
            result = readOnEdt()
        }
        return result
    }

    fun diagnose(): String {
        var result = ""
        ApplicationManager.getApplication().invokeAndWait {
            result = diagnoseOnEdt()
        }
        return result
    }

    fun diffProbe(): String {
        var result = ""
        ApplicationManager.getApplication().invokeAndWait {
            result = diffProbeOnEdt()
        }
        return result
    }

    private fun readOnEdt(): CaretReadResult {
        val selectedEditors = selectedEditors()

        readFrontendDiffCaret(selectedEditors.selectedEditor)?.let { return it }
        for (candidate in selectedEditors.compositeEditors) {
            readFrontendDiffCaret(candidate)?.let { return it }
        }

        readUnifiedDiffCaret(selectedEditors.selectedEditor)?.let { return it }
        for (candidate in selectedEditors.compositeEditors) {
            readUnifiedDiffCaret(candidate)?.let { return it }
        }

        val regularEditor = selectedEditors.selectedTextEditor?.let { editor ->
            val file = FileDocumentManager.getInstance().getFile(editor.document)
            if (file != null) {
                ResolvedEditor(editor = editor, filePath = file.path)
            } else {
                null
            }
        } ?: resolveEditorFromComponents(selectedEditors)
            ?: return CaretReadResult.Error(buildNoActiveEditorMessage(selectedEditors))

        val editor = regularEditor.editor
        val caret = editor.caretModel.logicalPosition
        return CaretReadResult.Success(
            CaretPosition(
                file = TransportPathResolver.normalize(regularEditor.filePath, project.basePath),
                line = caret.line + 1,
                column = caret.column,
                matchText = trimmedLineText(editor.document, caret.line),
                matchTextCandidates = null,
            )
        )
    }

    private fun diagnoseOnEdt(): String {
        val selectedEditors = selectedEditors()
        val lines = mutableListOf<String>()
        lines += "projectBase=${project.basePath ?: "null"}"
        lines += "selectedEditor=${selectedEditors.selectedEditor?.javaClass?.name ?: "null"}"
        lines += "selectedTextEditor=${selectedEditors.selectedTextEditor?.javaClass?.name ?: "null"}"
        lines += "compositeEditors=${
            selectedEditors.compositeEditors.joinToString(prefix = "[", postfix = "]") { it.javaClass.name }
        }"

        describeUnifiedDiffCandidate("selectedEditor", selectedEditors.selectedEditor).forEach(lines::add)
        describeEditorComponentTree("selectedEditor", selectedEditors.selectedEditor).forEach(lines::add)
        selectedEditors.compositeEditors.forEachIndexed { index, candidate ->
            describeUnifiedDiffCandidate("composite[$index]", candidate).forEach(lines::add)
            describeEditorComponentTree("composite[$index]", candidate).forEach(lines::add)
        }

        val regularEditor = selectedEditors.selectedTextEditor?.let { editor ->
            val file = FileDocumentManager.getInstance().getFile(editor.document)
            if (file != null) {
                ResolvedEditor(editor = editor, filePath = file.path)
            } else {
                null
            }
        } ?: resolveEditorFromComponents(selectedEditors)

        if (regularEditor != null) {
            val caret = regularEditor.editor.caretModel.logicalPosition
            lines += "regularEditor.file=${regularEditor.filePath}"
            lines += "regularEditor.line=${caret.line + 1}"
            lines += "regularEditor.column=${caret.column}"
            lines += "regularEditor.matchText=${trimmedLineText(regularEditor.editor.document, caret.line) ?: "null"}"
        } else {
            lines += "regularEditor.file=null"
        }

        return lines.joinToString("\n")
    }

    private fun diffProbeOnEdt(): String {
        val selectedEditors = selectedEditors()
        val lines = mutableListOf<String>()
        lines += "projectBase=${project.basePath ?: "null"}"

        val candidates = buildList {
            selectedEditors.selectedEditor?.let(::add)
            addAll(selectedEditors.compositeEditors)
        }.distinctBy(System::identityHashCode)

        if (candidates.isEmpty()) {
            return "no candidate editors"
        }

        var foundDiffCandidate = false
        for ((index, candidate) in candidates.withIndex()) {
            val label = "candidate[$index]"
            val frontendInspection = FrontendEditorModelResolver.inspect(candidate, project.basePath)
            val isDiff = isDiffCandidate(candidate, frontendInspection)
            lines += "$label.class=${candidate.javaClass.name}"
            lines += "$label.isDiff=$isDiff"
            lines += "$label.frontendModelClass=${frontendInspection?.modelClass ?: "null"}"
            lines += "$label.frontendProviderId=${frontendInspection?.providerId ?: "null"}"
            lines += "$label.frontendTitle=${frontendInspection?.title ?: "null"}"
            lines += "$label.frontendTabTitle=${frontendInspection?.tabTitle ?: "null"}"
            lines += "$label.frontendResolvedPath=${frontendInspection?.resolvedPath ?: "null"}"

            if (!isDiff) continue
            foundDiffCandidate = true

            BeControlPathResolver.inspect(candidate, project.basePath)?.let { inspection ->
                lines += "$label.beControlResolvedPath=${inspection.resolvedPath ?: "null"}"
                lines += "$label.beControlCandidates=${inspection.candidates}"
            }

            val editor = resolveEditorForCandidate(candidate)
            lines += "$label.editorClass=${editor?.javaClass?.name ?: "null"}"
            if (editor == null) continue

            val caret = editor.caretModel.logicalPosition
            lines += "$label.caret.line=${caret.line + 1}"
            lines += "$label.caret.column=${caret.column}"
            lines += "$label.documentFile=${FileDocumentManager.getInstance().getFile(editor.document)?.path ?: "null"}"

            aroundCaretLines(editor, caret.line).forEachIndexed { nearbyIndex, detail ->
                lines += "$label.visible[$nearbyIndex]=$detail"
            }

            val converterProbes = buildList {
                addAll(ReflectiveLineNumberConverterLookup.find("editor", editor))
                addAll(
                    ReflectiveLineNumberConverterLookup.find(
                        "editor.gutterComponentEx",
                        ReflectiveMemberLookup.readNamedMember(editor, "gutterComponentEx"),
                    )
                )
                addAll(
                    ReflectiveLineNumberConverterLookup.find(
                        "editor.gutter",
                        ReflectiveMemberLookup.readNamedMember(editor, "gutter"),
                    )
                )
            }.distinctBy { probe ->
                probe.label to System.identityHashCode(probe.converter)
            }

            if (converterProbes.isEmpty()) {
                lines += "$label.converter=none"
            } else {
                converterProbes.forEachIndexed { converterIndex, probe ->
                    lines += "$label.converter[$converterIndex].label=${probe.label}"
                    lines += "$label.converter[$converterIndex].class=${probe.converter.javaClass.name}"
                    lines += "$label.converter[$converterIndex].current=${convertLineNumber(probe.converter, editor, caret.line + 1) ?: "null"}"
                    lines += "$label.converter[$converterIndex].prev=${
                        if (caret.line > 0) convertLineNumber(probe.converter, editor, caret.line) ?: "null" else "null"
                    }"
                    lines += "$label.converter[$converterIndex].next=${
                        if (caret.line + 2 <= editor.document.lineCount) {
                            convertLineNumber(probe.converter, editor, caret.line + 2) ?: "null"
                        } else {
                            "null"
                        }
                    }"
                }
            }
        }

        if (!foundDiffCandidate) {
            lines += "no diff candidate detected"
        }

        return lines.joinToString("\n")
    }

    private fun selectedEditors(): SelectedEditors {
        return SelectedEditorsResolver.read(project)
    }

    private data class ResolvedEditor(
        val editor: Editor,
        val filePath: String,
    )

    private fun resolveEditorFromComponents(selectedEditors: SelectedEditors): ResolvedEditor? {
        val candidates = buildList {
            selectedEditors.selectedEditor?.let { add(it) }
            addAll(selectedEditors.compositeEditors)
        }

        for (candidate in candidates) {
            val frontendInspection = FrontendEditorModelResolver.inspect(candidate, project.basePath)
            val beControlInspection = BeControlPathResolver.inspect(candidate, project.basePath)
            for (component in extractEditorComponents(candidate)) {
                val dataContext = DataManager.getInstance().getDataContext(component)
                val editor = resolveEditorFromDataContext(dataContext)
                    ?: continue
                val filePath = listOfNotNull(
                    resolveFileFromDataContext(dataContext)?.path
                        ?.takeUnless(DiffRequestFileResolver::looksLikeWrapperPath),
                    FileDocumentManager.getInstance().getFile(editor.document)?.path
                        ?.takeUnless(DiffRequestFileResolver::looksLikeWrapperPath),
                    beControlInspection?.resolvedPath,
                    frontendInspection?.resolvedPath,
                ).firstOrNull()
                    ?: continue
                return ResolvedEditor(editor = editor, filePath = filePath)
            }
        }

        return null
    }

    private fun resolveEditorForCandidate(candidate: Any?): Editor? {
        for (component in extractEditorComponents(candidate ?: return null)) {
            val dataContext = DataManager.getInstance().getDataContext(component)
            resolveEditorFromDataContext(dataContext)?.let { return it }
            (ReflectiveMemberLookup.readNamedMember(component, "editor") as? Editor)?.let { return it }
        }
        return null
    }

    private fun extractEditorComponent(candidate: Any): JComponent? {
        return extractEditorComponents(candidate).firstOrNull()
    }

    private fun extractEditorComponents(candidate: Any): List<JComponent> {
        val fileEditor = candidate as? FileEditor ?: return emptyList()
        return linkedSetOf<JComponent>().apply {
            (ReflectiveMemberLookup.invokeZeroArgMember(fileEditor, "getBeComponent") as? JComponent)?.let(::add)
            fileEditor.preferredFocusedComponent?.let(::add)
            add(fileEditor.component)
        }.toList()
    }

    private fun resolveFileFromDataContext(dataContext: DataContext): VirtualFile? {
        val navigatable = CommonDataKeys.NAVIGATABLE.getData(dataContext)
        extractNavigatableFile(navigatable)?.let { return it }

        val navigatables = CommonDataKeys.NAVIGATABLE_ARRAY.getData(dataContext)
        if (navigatables != null) {
            for (candidate in navigatables) {
                extractNavigatableFile(candidate)?.let { return it }
            }
        }

        return CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
    }

    private fun resolveEditorFromDataContext(dataContext: DataContext): Editor? {
        return CommonDataKeys.EDITOR.getData(dataContext)
            ?: CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext)
    }

    private fun extractNavigatableFile(navigatable: Any?): VirtualFile? {
        return (navigatable as? OpenFileDescriptor)?.file
    }

    private fun resolveDiffContentFile(content: DocumentContent): VirtualFile? {
        if (content is FileContent) {
            return content.file
        }

        content.highlightFile?.let { return it }

        val navigatable = content.getNavigatable(com.intellij.diff.util.LineCol(0, 0))
        extractNavigatableFile(navigatable)?.let { return it }

        return FileDocumentManager.getInstance().getFile(content.document)
    }

    private fun buildNoActiveEditorMessage(selectedEditors: SelectedEditors): String {
        val selectedEditorClass = selectedEditors.selectedEditor?.javaClass?.name ?: "null"
        val selectedTextEditorClass = selectedEditors.selectedTextEditor?.javaClass?.name ?: "null"
        val compositeClasses = selectedEditors.compositeEditors.joinToString(
            prefix = "[",
            postfix = "]",
        ) { it.javaClass.name }
        return "no active editor (selectedEditor=$selectedEditorClass, selectedTextEditor=$selectedTextEditorClass, compositeEditors=$compositeClasses)"
    }

    private fun describeEditorComponentTree(label: String, candidate: Any?): List<String> {
        val components = candidate?.let(::extractEditorComponents).orEmpty()
        val lines = mutableListOf<String>()

        FrontendEditorModelResolver.inspect(candidate, project.basePath)?.let { inspection ->
            lines += "$label.frontendModelClass=${inspection.modelClass ?: "null"}"
            lines += "$label.frontendProviderId=${inspection.providerId ?: "null"}"
            lines += "$label.frontendTitle=${inspection.title ?: "null"}"
            lines += "$label.frontendTabTitle=${inspection.tabTitle ?: "null"}"
            lines += "$label.frontendCandidates=${inspection.candidates}"
            lines += "$label.frontendResolvedPath=${inspection.resolvedPath ?: "null"}"
        }
        BeControlPathResolver.inspect(candidate, project.basePath)?.let { inspection ->
            lines += "$label.beControlRootClass=${inspection.rootClass}"
            lines += "$label.beControlCandidates=${inspection.candidates}"
            lines += "$label.beControlResolvedPath=${inspection.resolvedPath ?: "null"}"
            inspection.details.forEachIndexed { index, detail ->
                lines += "$label.beControl[$index]=$detail"
            }
        }

        if (components.isEmpty()) return lines

        components.forEachIndexed { componentIndex, component ->
            val prefix = "$label.component[$componentIndex]"
            lines += "$prefix.class=${component.javaClass.name}"

            val dataContext = DataManager.getInstance().getDataContext(component)
            val virtualFile = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)?.path ?: "null"
            val navigatable = CommonDataKeys.NAVIGATABLE.getData(dataContext)
            val navigatableFile = extractNavigatableFile(navigatable)?.path ?: "null"
            lines += "$prefix.data.virtualFile=$virtualFile"
            lines += "$prefix.data.navigatableFile=$navigatableFile"
            CombinedDiffUiResolver.inspect(dataContext, project.basePath)?.let { inspection ->
                lines += "$prefix.data.combinedMainUi=${inspection.mainUiClass ?: "null"}"
                lines += "$prefix.data.combinedViewer=${inspection.viewerClass ?: "null"}"
                lines += "$prefix.data.combinedTitle=${inspection.titleInspection.title ?: "null"}"
                lines += "$prefix.data.combinedContentTitles=${inspection.titleInspection.contentTitles}"
                lines += "$prefix.data.combinedCandidates=${inspection.titleInspection.candidates}"
                lines += "$prefix.data.combinedResolvedPath=${inspection.resolvedPath ?: "null"}"
            }
            inspectComponentPathHints(component)?.let { inspection ->
                lines += "$prefix.data.componentHintCandidates=${inspection.candidates}"
                lines += "$prefix.data.componentHintResolvedPath=${inspection.resolvedPath ?: "null"}"
            }

            val interestingComponents = collectInterestingComponentDetails(component)
            interestingComponents.forEachIndexed { index, detail ->
                lines += "$prefix.tree[$index]=$detail"
            }
        }
        return lines
    }

    private fun collectInterestingComponentDetails(root: JComponent): List<String> {
        val results = mutableListOf<String>()
        val queue = ArrayDeque<Component>()
        queue.add(root)

        while (queue.isNotEmpty() && results.size < 20) {
            val component = queue.removeFirst()
            describeInterestingComponent(component)?.let(results::add)

            if (component is Container) {
                component.components.forEach(queue::addLast)
            }
        }

        return results
    }

    private fun describeInterestingComponent(component: Component): String? {
        val text = when (component) {
            is JLabel -> component.text
            is AbstractButton -> component.text
            else -> null
        }?.trim().orEmpty()
        val tooltip = (component as? JComponent)?.toolTipText?.trim().orEmpty()
        val name = component.name?.trim().orEmpty()
        val accessibleName = component.accessibleContext?.accessibleName?.trim().orEmpty()

        if (text.isEmpty() && tooltip.isEmpty() && name.isEmpty() && accessibleName.isEmpty()) {
            return null
        }

        return buildString {
            append(component.javaClass.name)
            if (name.isNotEmpty()) append(" name=").append(name)
            if (text.isNotEmpty()) append(" text=").append(text)
            if (tooltip.isNotEmpty()) append(" tooltip=").append(tooltip)
            if (accessibleName.isNotEmpty()) append(" accessibleName=").append(accessibleName)
        }
    }

    private fun describeUnifiedDiffCandidate(label: String, selectedEditor: Any?): List<String> {
        val lines = mutableListOf<String>()
        lines += "$label.class=${selectedEditor?.javaClass?.name ?: "null"}"

        val processor = when (selectedEditor) {
            null -> null
            is DiffEditorViewerFileEditor -> selectedEditor.editorViewer as? DiffRequestProcessor
            else -> ReflectiveMemberLookup.readAssignableMember(selectedEditor, DiffRequestProcessor::class.java)
        }
        lines += "$label.processor=${processor?.javaClass?.name ?: "null"}"

        val activeViewer = processor?.activeViewer
        lines += "$label.activeViewer=${activeViewer?.javaClass?.name ?: "null"}"
        val viewer = activeViewer as? UnifiedDiffViewer ?: return lines

        val rightContent = viewer.getContent(Side.RIGHT)
        val inspection = DiffRequestFileResolver.inspect(
            ReflectiveMemberLookup.invokeZeroArgMember(processor, "getActiveRequest")
                ?: ReflectiveMemberLookup.invokeZeroArgMember(viewer, "getRequest")
                ?: ReflectiveMemberLookup.invokeZeroArgMember(processor, "getRequest"),
            project.basePath,
        )
        val mapping = when (
            val resolution = UnifiedDiffCaretResolver.resolve(
                unifiedLines = documentLines(viewer.editor.document),
                rightLines = documentLines(rightContent.document),
                unifiedLineIndex = viewer.editor.caretModel.logicalPosition.line,
            )
        ) {
            is DiffCaretResolution.Found -> {
                val mappedText = trimmedLineText(rightContent.document, resolution.lineIndex) ?: ""
                "line=${resolution.lineIndex + 1}, text=$mappedText"
            }
            is DiffCaretResolution.Error -> "error=${resolution.message}"
        }

        lines += "$label.requestClass=${inspection.requestClass}"
        lines += "$label.requestTitle=${inspection.title ?: "null"}"
        lines += "$label.contentTitles=${inspection.contentTitles}"
        lines += "$label.requestCandidates=${inspection.candidates}"
        lines += "$label.requestResolvedPath=${inspection.resolvedPath ?: "null"}"
        lines += "$label.rightDocumentFile=${FileDocumentManager.getInstance().getFile(rightContent.document)?.path ?: "null"}"
        lines += "$label.unifiedCaretLine=${viewer.editor.caretModel.logicalPosition.line + 1}"
        lines += "$label.mappedRight=$mapping"
        return lines
    }

    private fun readUnifiedDiffCaret(selectedEditor: Any?): CaretReadResult? {
        val processor = when (selectedEditor) {
            null -> return null
            is DiffEditorViewerFileEditor -> selectedEditor.editorViewer as? DiffRequestProcessor
            else -> ReflectiveMemberLookup.readAssignableMember(selectedEditor, DiffRequestProcessor::class.java)
        } ?: return null

        val viewer = processor.activeViewer as? UnifiedDiffViewer
            ?: return null

        val editor = viewer.editor
        val rightContent = viewer.getContent(Side.RIGHT)
        val rightDoc = rightContent.document
        val request = ReflectiveMemberLookup.invokeZeroArgMember(processor, "getActiveRequest")
            ?: ReflectiveMemberLookup.invokeZeroArgMember(viewer, "getRequest")
            ?: ReflectiveMemberLookup.invokeZeroArgMember(processor, "getRequest")
        val combinedUiPath = resolveDiffPathFromComponents(selectedEditor)
        val inspection = DiffRequestFileResolver.inspect(request, project.basePath)
        val filePath = combinedUiPath
            ?: inspection.resolvedPath
            ?: resolveDiffContentPath(rightContent)
            ?: return CaretReadResult.Error("active diff view is not backed by a file")

        if (DiffRequestFileResolver.looksLikeWrapperPath(filePath)) {
            return CaretReadResult.Error(
                DiffRequestFileResolver.buildDiagnosticMessage(
                    inspection = inspection,
                    wrapperPath = filePath,
                    projectBasePath = project.basePath,
                    editorClass = selectedEditor.javaClass.name,
                ),
            )
        }

        return when (
            val resolution = UnifiedDiffCaretResolver.resolve(
                unifiedLines = documentLines(editor.document),
                rightLines = documentLines(rightDoc),
                unifiedLineIndex = editor.caretModel.logicalPosition.line,
            )
        ) {
            is DiffCaretResolution.Found -> CaretReadResult.Success(
                CaretPosition(
                    file = TransportPathResolver.normalize(filePath, project.basePath),
                    line = resolution.lineIndex + 1,
                    column = 0,
                    matchText = trimmedLineText(rightDoc, resolution.lineIndex),
                    matchTextCandidates = null,
                )
            )
            is DiffCaretResolution.Error -> CaretReadResult.Error(resolution.message)
        }
    }

    private fun readFrontendDiffCaret(candidate: Any?): CaretReadResult? {
        val frontendInspection = FrontendEditorModelResolver.inspect(candidate, project.basePath)
        if (!isDiffCandidate(candidate, frontendInspection)) return null

        val filePath = listOfNotNull(
            resolveDiffPathFromComponents(candidate),
            BeControlPathResolver.inspect(candidate, project.basePath)?.resolvedPath,
            frontendInspection?.resolvedPath,
        ).firstOrNull()
            ?: return CaretReadResult.Error("active diff view is not backed by a file")

        if (DiffRequestFileResolver.looksLikeWrapperPath(filePath)) {
            return CaretReadResult.Error(
                "unresolved diff file (path=$filePath, model=${frontendInspection?.modelClass ?: "null"}, provider=${frontendInspection?.providerId ?: "null"})",
            )
        }

        val editor = resolveEditorForCandidate(candidate)
            ?: return CaretReadResult.Error("active diff view has no focused editor")
        val caret = editor.caretModel.logicalPosition
        // Split-mode diff tabs expose unified diff text, but not a stable right-side line mapping.
        // Return ordered anchor texts here and let the backend resolve the canonical file and line.
        val anchorTexts = DiffAnchorTextCollector.collect(
            lines = documentLines(editor.document),
            caretLineIndex = caret.line,
        )

        return CaretReadResult.Success(
            CaretPosition(
                file = TransportPathResolver.normalize(filePath, project.basePath),
                line = null,
                column = 0,
                matchText = anchorTexts.firstOrNull(),
                matchTextCandidates = anchorTexts,
            )
        )
    }

    private fun resolveDiffContentPath(content: DocumentContent): String? {
        return resolveDiffContentFile(content)?.path
    }

    private fun resolveDiffPathFromComponents(selectedEditor: Any?): String? {
        for (component in extractEditorComponents(selectedEditor ?: return null)) {
            val dataContext = DataManager.getInstance().getDataContext(component)
            CombinedDiffUiResolver.inspect(dataContext, project.basePath)?.resolvedPath?.let { return it }
            inspectComponentPathHints(component)?.resolvedPath?.let { return it }
        }
        return null
    }

    private fun inspectComponentPathHints(component: JComponent): DiffRequestFileResolver.Inspection? {
        val values = mutableListOf<String>()
        val queue = ArrayDeque<Component>()
        queue.add(component)

        while (queue.isNotEmpty() && values.size < 80) {
            val current = queue.removeFirst()
            val tooltip = (current as? JComponent)?.toolTipText?.trim().orEmpty()
            val text = when (current) {
                is JLabel -> current.text
                is AbstractButton -> current.text
                else -> null
            }?.trim().orEmpty()
            val name = current.name?.trim().orEmpty()
            val accessibleName = current.accessibleContext?.accessibleName?.trim().orEmpty()

            if (tooltip.isNotEmpty()) values += tooltip
            if (text.isNotEmpty()) values += text
            if (name.isNotEmpty()) values += name
            if (accessibleName.isNotEmpty()) values += accessibleName

            if (current is Container) {
                current.components.forEach(queue::addLast)
            }
        }

        if (values.isEmpty()) return null
        return DiffRequestFileResolver.inspectTitles(
            title = values.first(),
            contentTitles = values.drop(1),
            projectBasePath = project.basePath,
            sourceClass = component.javaClass.name,
        )
    }

    private fun isDiffCandidate(
        candidate: Any?,
        frontendInspection: FrontendEditorModelResolver.Inspection?,
    ): Boolean {
        if (candidate == null) return false
        if (candidate is DiffEditorViewerFileEditor) return true
        if (ReflectiveMemberLookup.readAssignableMember(candidate, DiffRequestProcessor::class.java) != null) return true

        val modelClass = frontendInspection?.modelClass.orEmpty()
        val providerId = frontendInspection?.providerId.orEmpty()
        return modelClass.contains("Diff", ignoreCase = true) ||
            providerId.contains("Diff", ignoreCase = true)
    }

    private fun aroundCaretLines(editor: Editor, caretLineIndex: Int): List<String> {
        val document = editor.document
        if (document.lineCount == 0) return listOf("document is empty")

        val start = maxOf(0, caretLineIndex - 2)
        val end = minOf(document.lineCount - 1, caretLineIndex + 2)
        return (start..end).map { lineIndex ->
            val marker = if (lineIndex == caretLineIndex) "*" else " "
            val text = trimmedLineText(document, lineIndex) ?: ""
            "$marker${lineIndex + 1}: ${text.take(200)}"
        }
    }

    private fun convertLineNumber(converter: Any, editor: Editor, oneBasedLineNumber: Int): Int? {
        return when (converter) {
            is com.intellij.openapi.editor.LineNumberConverter -> converter.convert(editor, oneBasedLineNumber)
            else -> ReflectiveMemberLookup.invokeMember(
                converter,
                "convert",
                editor,
                oneBasedLineNumber,
            ) as? Int
        }
    }

    private fun documentLines(document: com.intellij.openapi.editor.Document): List<String> {
        val lines = ArrayList<String>(document.lineCount)
        for (lineIndex in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(lineIndex)
            val lineEnd = document.getLineEndOffset(lineIndex)
            lines += document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
        }
        return lines
    }

    private fun trimmedLineText(document: com.intellij.openapi.editor.Document, lineIndex: Int): String? {
        if (lineIndex !in 0 until document.lineCount) return null
        val lineStart = document.getLineStartOffset(lineIndex)
        val lineEnd = document.getLineEndOffset(lineIndex)
        return document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
