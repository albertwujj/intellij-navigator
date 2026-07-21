package com.claudecode.navigator.resolver

import com.claudecode.navigator.model.NavigationTarget
import com.claudecode.navigator.util.PathMatcher
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.NameUtil
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.PyFunction

class SymbolResolver(private val project: Project) {
    private val logger = Logger.getInstance(SymbolResolver::class.java)

    /**
     * Resolves a qualified symbol name to navigation targets.
     *
     * Uses contributor-based search to find all symbol types (classes, functions,
     * variables, constants). Falls back to partial matching when exact match fails.
     * Qualifiers are applied softly — they narrow results only when they produce matches.
     *
     * @param qualifiedName The qualified symbol name to find
     * @param fileHint Optional file path hint for filtering results
     * @return List of matching navigation targets
     */
    fun resolve(qualifiedName: String, fileHint: String? = null): List<NavigationTarget> {
        return ReadAction.compute<List<NavigationTarget>, Exception> {
            val parts = qualifiedName.split(".").let { rawParts ->
                // Strip leading "self" or "cls" — these are Python instance/class
                // references, not real symbol qualifiers.
                if (rawParts.size > 1 && rawParts.first() in listOf("self", "cls")) {
                    rawParts.drop(1)
                } else {
                    rawParts
                }
            }
            val shortName = parts.last()
            val qualifiers = parts.dropLast(1)

            logger.debug("Resolving symbol: '$qualifiedName' (shortName: $shortName, qualifiers: $qualifiers, fileHint: $fileHint)")

            // Step 1: Exact lookup via contributors (covers all symbol types)
            var items = findExact(shortName)

            // Step 2: Partial match fallback
            if (items.isEmpty()) {
                items = findByPartialMatch(shortName)
            }

            if (items.isEmpty()) return@compute emptyList()

            // Step 3: Soft qualifier filtering
            val qualified = applySoftQualifierFilter(items, qualifiers)

            // Step 4: Convert to NavigationTargets
            val targets = qualified.mapNotNull { it.toNavigationTarget() }

            logger.debug("Found ${targets.size} targets for '$qualifiedName'")

            // Step 5: Soft file hint filter
            applyFileHintFilter(targets, fileHint)
        }
    }

    /**
     * Finds symbols by exact name using all registered symbol contributors.
     * Covers classes, functions, variables, constants — everything IntelliJ's
     * "Go to Symbol" UI can find.
     */
    private fun findExact(shortName: String): List<NavigationItem> {
        val results = mutableListOf<NavigationItem>()
        for (contributor in ChooseByNameContributor.SYMBOL_EP_NAME.extensionList) {
            results.addAll(contributor.getItemsByName(shortName, shortName, project, false))
        }
        return results
    }

    /**
     * Finds symbols by partial/fuzzy match when exact lookup fails.
     * Uses MinusculeMatcher for case-insensitive substring and camelCase matching
     * (same algorithm as IntelliJ's "Go to Symbol" UI).
     */
    private fun findByPartialMatch(shortName: String): List<NavigationItem> {
        val matcher = NameUtil.buildMatcher("*$shortName", NameUtil.MatchingCaseSensitivity.NONE)
        val results = mutableListOf<NavigationItem>()

        for (contributor in ChooseByNameContributor.SYMBOL_EP_NAME.extensionList) {
            val names = contributor.getNames(project, false)
            for (name in names) {
                if (matcher.matches(name)) {
                    results.addAll(contributor.getItemsByName(name, name, project, false))
                }
            }
        }
        return results
    }

    /**
     * Applies qualifier filtering progressively. Each qualifier narrows results
     * only if it produces matches (soft behavior). Applied right-to-left so the
     * closest qualifier (e.g. class name) is applied first.
     */
    private fun applySoftQualifierFilter(
        items: List<NavigationItem>, qualifiers: List<String>
    ): List<NavigationItem> {
        if (qualifiers.isEmpty() || items.isEmpty()) return items

        var current = items
        for (qualifier in qualifiers.reversed()) {
            val filtered = current.filter { elementMatchesQualifier(it, qualifier) }
            if (filtered.isNotEmpty()) current = filtered
            // If filtered is empty, skip this qualifier (soft behavior)
        }
        return current
    }

    /**
     * Checks if a navigation item matches a single qualifier by checking
     * containing class name, file name, and directory names.
     */
    private fun elementMatchesQualifier(item: NavigationItem, qualifier: String): Boolean {
        val element = item as? PsiElement ?: return false
        when (element) {
            is PyFunction -> if (element.containingClass?.name == qualifier) return true
            is PyClass -> if ((element.parent as? PyClass)?.name == qualifier) return true
        }
        val file = element.containingFile?.virtualFile
        if (file != null) {
            if (file.nameWithoutExtension == qualifier) return true
            var dir = file.parent
            while (dir != null) {
                if (dir.name == qualifier) return true
                dir = dir.parent
            }
        }
        return false
    }

    /**
     * Applies file hint filtering to targets.
     * If hint matches some targets, returns only those.
     * If hint matches nothing (soft matching), returns all targets unchanged.
     */
    private fun applyFileHintFilter(
        targets: List<NavigationTarget>,
        fileHint: String?
    ): List<NavigationTarget> {
        if (fileHint.isNullOrBlank() || targets.isEmpty()) {
            return targets
        }

        val filtered = targets.filter {
            PathMatcher.pathMatches(it.file.path, fileHint)
        }

        logger.debug("FileHint filter: ${targets.size} -> ${filtered.size} (hint: $fileHint)")

        // Soft matching: if hint matches nothing, ignore it
        return if (filtered.isNotEmpty()) filtered else targets
    }

    private fun NavigationItem.toNavigationTarget(): NavigationTarget? {
        val element = this as? PsiElement ?: return null
        val file = element.containingFile?.virtualFile ?: return null
        val line = getLineNumber(element)
        val desc = when (element) {
            is PyClass -> "class ${element.name}"
            is PyFunction -> if (element.containingClass != null) {
                "${element.containingClass?.name}.${element.name}()"
            } else {
                "${element.name}()"
            }
            else -> {
                val fileName = file.name
                "${element.name ?: element.text} ($fileName)"
            }
        }
        return NavigationTarget(file = file, line = line, description = desc)
    }

    private val PsiElement.name: String?
        get() = when (this) {
            is com.intellij.psi.PsiNamedElement -> name
            else -> null
        }

    private fun getLineNumber(element: PsiElement): Int {
        val document = element.containingFile?.viewProvider?.document ?: return 0
        return document.getLineNumber(element.textOffset)
    }
}
