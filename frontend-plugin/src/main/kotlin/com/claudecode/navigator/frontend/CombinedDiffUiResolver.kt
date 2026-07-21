package com.claudecode.navigator.frontend

import com.intellij.openapi.actionSystem.DataContext

internal data class CombinedDiffUiInspection(
    val mainUiClass: String?,
    val viewerClass: String?,
    val requestInspection: DiffRequestFileResolver.Inspection,
    val titleInspection: DiffRequestFileResolver.Inspection,
) {
    val resolvedPath: String?
        get() = titleInspection.resolvedPath ?: requestInspection.resolvedPath
}

internal object CombinedDiffUiResolver {
    fun inspect(dataContext: DataContext, projectBasePath: String?): CombinedDiffUiInspection? {
        val viewer = readCombinedDiffViewer(dataContext)
        val mainUi = readCombinedDiffMainUi(dataContext)
            ?: viewer?.let { ReflectiveMemberLookup.invokeZeroArgMember(it, "getMainUI") }
            ?: return null

        val currentRequest = ReflectiveMemberLookup.invokeZeroArgMember(mainUi, "getCurrentRequest")
            ?: viewer?.let { ReflectiveMemberLookup.invokeZeroArgMember(it, "getCurrentRequest") }
        val requestInspection = DiffRequestFileResolver.inspect(currentRequest, projectBasePath)

        val uiState = ReflectiveMemberLookup.invokeZeroArgMember(mainUi, "getUiState")
        val diffInfoFlow = ReflectiveMemberLookup.invokeZeroArgMember(uiState, "getDiffInfoStateFlow")
        val diffInfoState = ReflectiveMemberLookup.invokeZeroArgMember(diffInfoFlow, "getValue")
        val titleInspection = DiffRequestFileResolver.inspectTitles(
            title = ReflectiveMemberLookup.invokeZeroArgMember(diffInfoState, "getTitle") as? String,
            contentTitles = listOfNotNull(
                ReflectiveMemberLookup.invokeZeroArgMember(diffInfoState, "getLeftTitle") as? String,
                ReflectiveMemberLookup.invokeZeroArgMember(diffInfoState, "getRightTitle") as? String,
            ),
            projectBasePath = projectBasePath,
            sourceClass = diffInfoState?.javaClass?.name ?: "null",
        )

        return CombinedDiffUiInspection(
            mainUiClass = mainUi.javaClass.name,
            viewerClass = viewer?.javaClass?.name,
            requestInspection = requestInspection,
            titleInspection = titleInspection,
        )
    }

    private fun readCombinedDiffMainUi(dataContext: DataContext): Any? {
        return readDataKeyValue(
            dataContext = dataContext,
            ownerClassName = "com.intellij.diff.tools.combined.CombinedDiffKeysKt",
            accessorName = "getCOMBINED_DIFF_MAIN_UI",
        )
    }

    private fun readCombinedDiffViewer(dataContext: DataContext): Any? {
        return readDataKeyValue(
            dataContext = dataContext,
            ownerClassName = "com.intellij.diff.tools.combined.CombinedDiffKeysKt",
            accessorName = "getCOMBINED_DIFF_VIEWER",
        )
    }

    private fun readDataKeyValue(dataContext: DataContext, ownerClassName: String, accessorName: String): Any? {
        return runCatching {
            val ownerClass = Class.forName(ownerClassName)
            val dataKey = ownerClass.getMethod(accessorName).invoke(null) ?: return null
            ReflectiveMemberLookup.invokeMember(dataKey, "getData", dataContext)
        }.getOrNull()
    }
}
