package com.claudecode.navigator.frontend

internal object DiffAnchorTextCollector {
    fun collect(lines: List<String>, caretLineIndex: Int, radius: Int = 8): List<String> {
        if (caretLineIndex !in lines.indices) return emptyList()

        val results = linkedSetOf<String>()

        fun addLine(lineIndex: Int) {
            if (lineIndex !in lines.indices) return
            val text = lines[lineIndex].trim()
            if (text.isNotEmpty()) {
                results += text
            }
        }

        addLine(caretLineIndex)
        for (delta in 1..radius) {
            addLine(caretLineIndex + delta)
        }
        for (delta in 1..radius) {
            addLine(caretLineIndex - delta)
        }

        return results.toList()
    }
}
