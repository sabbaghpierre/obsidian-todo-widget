package de.yukigasai.obsidiantodowidget.todo

object MarkdownLabel {
    private const val escapable = """!"#$%&'()*+,-./:;<=>?@[\]^_`{|}~"""

    fun format(text: String): String = buildString {
        var index = 0
        while (index < text.length) {
            val character = text[index]
            if (character == '\\' && index + 1 < text.length && text[index + 1] in escapable) {
                append(text[index + 1])
                index += 2
                continue
            }
            if (character == '`') {
                val openingEnd = runEnd(text, index)
                val closing = codeEnd(text, index)
                if (closing >= 0) {
                    append(text.substring(openingEnd, closing))
                    index = closing + (openingEnd - index)
                } else {
                    append(text.substring(index, openingEnd))
                    index = openingEnd
                }
                continue
            }
            if (text.startsWith("[[", index)) {
                val closing = text.indexOf("]]", index + 2)
                if (closing > index + 2) {
                    val link = text.substring(index + 2, closing)
                    if ('|' in link) {
                        val alias = link.substringAfter('|')
                        append(if (alias.isBlank()) link.substringBefore('|') else format(alias))
                    } else {
                        append(link)
                    }
                    index = closing + 2
                    continue
                }
            }
            if (character == '[') {
                val labelEnd = bracketEnd(text, index, '[', ']')
                if (labelEnd >= 0 && text.getOrNull(labelEnd + 1) == '(') {
                    val destinationEnd = bracketEnd(text, labelEnd + 1, '(', ')')
                    if (destinationEnd >= 0) {
                        append(format(text.substring(index + 1, labelEnd)))
                        index = destinationEnd + 1
                        continue
                    }
                }
            }
            if (character == '*' || character == '_') {
                val openingEnd = runEnd(text, index)
                if (openingEnd - index <= 3 && openingEnd < text.length &&
                    !text[openingEnd].isWhitespace() && text.getOrNull(index - 1)?.isLetterOrDigit() != true
                ) {
                    val closing = emphasisEnd(text, index)
                    if (closing >= 0) {
                        append(format(text.substring(openingEnd, closing)))
                        index = closing + (openingEnd - index)
                        continue
                    }
                }
                append(text.substring(index, openingEnd))
                index = openingEnd
                continue
            }
            append(character)
            index++
        }
    }

    private fun runEnd(text: String, start: Int): Int {
        var end = start + 1
        while (end < text.length && text[end] == text[start]) end++
        return end
    }

    private fun codeEnd(text: String, start: Int): Int {
        val length = runEnd(text, start) - start
        var index = start + length
        while (index < text.length) {
            if (text[index] == '`') {
                val end = runEnd(text, index)
                if (end - index == length) return index
                index = end
            } else {
                index++
            }
        }
        return -1
    }

    private fun bracketEnd(text: String, start: Int, opening: Char, closing: Char): Int {
        var depth = 1
        var index = start + 1
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index++
                '`' -> if (opening == '[') {
                    val closingCode = codeEnd(text, index)
                    if (closingCode >= 0) {
                        index = closingCode + (runEnd(text, index) - index)
                        continue
                    }
                }
                opening -> depth++
                closing -> if (--depth == 0) return index
            }
            index++
        }
        return -1
    }

    private fun emphasisEnd(text: String, start: Int): Int {
        val marker = text[start]
        val length = runEnd(text, start) - start
        var index = start + length
        while (index < text.length) {
            when (text[index]) {
                '\\' -> index += 2
                '`' -> {
                    val openingEnd = runEnd(text, index)
                    val closing = codeEnd(text, index)
                    index = if (closing < 0) openingEnd else closing + (openingEnd - index)
                }
                '[' -> {
                    val labelEnd = bracketEnd(text, index, '[', ']')
                    val destinationEnd = if (labelEnd >= 0 && text.getOrNull(labelEnd + 1) == '(') {
                        bracketEnd(text, labelEnd + 1, '(', ')')
                    } else -1
                    index = when {
                        destinationEnd >= 0 -> destinationEnd + 1
                        text.startsWith("[[", index) && labelEnd >= 0 -> labelEnd + 1
                        else -> index + 1
                    }
                }
                marker -> {
                    val end = runEnd(text, index)
                    if (end - index == length && !text[index - 1].isWhitespace() &&
                        text.getOrNull(end)?.isLetterOrDigit() != true
                    ) return index
                    index = end
                }
                else -> index++
            }
        }
        return -1
    }
}
