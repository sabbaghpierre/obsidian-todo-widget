package de.yukigasai.obsidiantodowidget.todo

import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.util.FsHelper
import de.yukigasai.obsidiantodowidget.util.NoteSnapshot

data class TodoFactory(val config: WidgetConfig) {

    // Optionally reduce the content of the file to the selected heading
    private fun sectionRange(fileContent: String): IntRange {
        if (config.header.isEmpty()) return fileContent.indices

        val findHeader = Regex(
            "^(?:\\uFEFF)?(#+)[\\t ]+${Regex.escape(config.header)}[\\t ]*\\r?\$", RegexOption.MULTILINE
        )
        val match1 = findHeader.find(fileContent) ?: return IntRange.EMPTY
        val headerLevel = match1.groupValues[1].length
        val start = match1.range.last + 1

        val findNextHeader = if (config.includeSubHeader) {
            Regex("^#{1,${headerLevel}}[\\t ]+[^\\r\\n]*\\r?\$", RegexOption.MULTILINE)
        } else {
            Regex("^#+[\\t ]+[^\\r\\n]*\\r?\$", RegexOption.MULTILINE)
        }

        val end = findNextHeader.find(fileContent, start)?.range?.first ?: fileContent.length
        return start until end
    }

    // Create list of todos from the content in the file
    fun getTodos(snapshot: NoteSnapshot): List<TodoItem> {
        val range = sectionRange(snapshot.text)
        if (range.isEmpty()) return emptyList()
        val filteredFileContent = snapshot.text.substring(range)
        val repeatPattern = Regex("#(\\d+)")
        return Regex(
            "^(?:\\uFEFF)?([^\\S\\r\\n]*)- \\[([0-9]+|[^\\r\\n])] ([^\\r\\n]*)\$",
            RegexOption.MULTILINE
        )
            .findAll(filteredFileContent)
            .mapIndexed { index, todo ->
                TodoItem(
                    id = index,
                    name = todo.groupValues[3],
                    state = todo.groupValues[2],
                    isChecked = todo.groupValues[2] == "x" || todo.groupValues[2] == "X",
                    offSet = todo.groupValues[1],
                    count = todo.groupValues[2].toIntOrNull() ?: 0,
                    repeats = repeatPattern.find(todo.groupValues[3])?.groupValues?.get(1)?.toIntOrNull(),
                    sourcePath = snapshot.path,
                    sourceRevision = snapshot.revision,
                    stateOffset = range.first + requireNotNull(todo.groups[2]).range.first,
                )
            }.toList()
    }

    fun appendTask(text: String, task: String): String? {
        val name = task.trim()
        if (name.isEmpty() || '\r' in name || '\n' in name) return null

        val selectedHeading = Regex("^#+[\\t ]+${Regex.escape(config.header)}[\\t ]*$")
        val heading = Regex("^#+[\\t ]+")
        val taskLine = Regex("^([\\t ]*)- \\[(?:[0-9]+|[^\\r\\n])] [^\\r\\n]*$")
        val fence = Regex("^[\\t ]*(`{3,}|~{3,})(.*)$")
        val wholeNote = config.header.isEmpty()
        var foundHeading = wholeNote
        var sectionEnd = text.length
        var lastTaskEnd: Int? = null
        var taskIndent: Int? = null
        var fenceMarker: Char? = null
        var fenceLength = 0

        for (line in Regex("[^\\n]*(?:\\n|$)").findAll(text)) {
            if (line.value.isEmpty()) continue
            val content = line.value.trimEnd('\r', '\n').removePrefix("\uFEFF")
            val fenceMatch = fence.matchEntire(content)
            val inCode = fenceMarker != null || fenceMatch != null
            if (!wholeNote && !inCode) {
                if (!foundHeading) {
                    if (selectedHeading.matches(content)) foundHeading = true
                } else if (heading.containsMatchIn(content)) {
                    sectionEnd = line.range.first
                    break
                } else {
                    val taskMatch = taskLine.matchEntire(content)
                    if (taskMatch != null) {
                        val indent = taskMatch.groupValues[1].length
                        taskIndent = minOf(taskIndent ?: indent, indent)
                        lastTaskEnd = line.range.last + 1
                    }
                }
            }
            if (!wholeNote && foundHeading && taskIndent != null && content.isNotBlank()) {
                val indent = content.takeWhile { it == ' ' || it == '\t' }.length
                if (indent > taskIndent) {
                    lastTaskEnd = line.range.last + 1
                } else if (inCode || !taskLine.matches(content)) {
                    taskIndent = null
                }
            }
            if (fenceMatch != null) {
                val marker = fenceMatch.groupValues[1]
                if (fenceMarker == null) {
                    fenceMarker = marker.first()
                    fenceLength = marker.length
                } else if (marker.first() == fenceMarker && marker.length >= fenceLength &&
                    fenceMatch.groupValues[2].isBlank()
                ) {
                    fenceMarker = null
                }
            }
        }
        if (!foundHeading) return null
        val offset = if (wholeNote) text.length else lastTaskEnd ?: sectionEnd
        if (offset == text.length && fenceMarker != null) return null
        val newline = if ("\r\n" in text) "\r\n" else "\n"
        val before = text.substring(0, offset)
        val after = text.substring(offset)
        val separator = if (before.isNotEmpty() && !before.endsWith('\n')) newline else ""
        val trailing = if (after.isNotEmpty() && !after.startsWith('\r') && !after.startsWith('\n')) {
            newline
        } else ""
        return "$before$separator- [ ] $name$newline$trailing$after"
    }

    // Public function to get the Todolist
    fun getTodosFromFile(): List<TodoItem>{
        val snapshot = FsHelper.readNote(config) ?: return emptyList()
        return getTodos(snapshot)
    }
}
