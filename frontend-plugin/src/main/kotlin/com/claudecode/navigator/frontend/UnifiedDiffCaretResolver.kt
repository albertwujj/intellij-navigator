package com.claudecode.navigator.frontend

internal sealed interface DiffCaretResolution {
    data class Found(val lineIndex: Int) : DiffCaretResolution
    data class Error(val message: String) : DiffCaretResolution
}

private sealed interface UniqueLineMatch {
    data class Found(val lineIndex: Int) : UniqueLineMatch
    data object Missing : UniqueLineMatch
    data object Ambiguous : UniqueLineMatch
}

internal object UnifiedDiffCaretResolver {
    fun resolve(
        unifiedLines: List<String>,
        rightLines: List<String>,
        unifiedLineIndex: Int,
    ): DiffCaretResolution {
        if (unifiedLineIndex !in unifiedLines.indices) {
            return DiffCaretResolution.Error("ambiguous: could not determine unique line")
        }

        when (val directMatch = findUniqueLine(rightLines, unifiedLines[unifiedLineIndex])) {
            is UniqueLineMatch.Found -> return DiffCaretResolution.Found(directMatch.lineIndex)
            UniqueLineMatch.Ambiguous -> {
                return DiffCaretResolution.Error("ambiguous: line appears multiple times in file")
            }
            UniqueLineMatch.Missing -> Unit
        }

        for (delta in 1 until unifiedLines.size) {
            for (candidateIndex in listOf(unifiedLineIndex + delta, unifiedLineIndex - delta)) {
                if (candidateIndex !in unifiedLines.indices) continue

                val candidateText = unifiedLines[candidateIndex].trim()
                if (candidateText.isEmpty()) continue

                when (val match = findUniqueLine(rightLines, candidateText)) {
                    is UniqueLineMatch.Found -> return DiffCaretResolution.Found(match.lineIndex)
                    UniqueLineMatch.Missing, UniqueLineMatch.Ambiguous -> Unit
                }
            }
        }

        return DiffCaretResolution.Error("ambiguous: could not determine unique line")
    }

    private fun findUniqueLine(lines: List<String>, text: String): UniqueLineMatch {
        val target = text.trim()
        var matchIndex: Int? = null

        for ((index, line) in lines.withIndex()) {
            if (line.trim() != target) continue
            if (matchIndex != null) return UniqueLineMatch.Ambiguous
            matchIndex = index
        }

        return if (matchIndex == null) {
            UniqueLineMatch.Missing
        } else {
            UniqueLineMatch.Found(matchIndex)
        }
    }
}
