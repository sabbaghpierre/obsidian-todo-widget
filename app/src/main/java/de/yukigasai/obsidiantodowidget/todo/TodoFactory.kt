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

    // Public function to get the Todolist
    fun getTodosFromFile(): List<TodoItem>{
        val snapshot = FsHelper.readNote(config) ?: return emptyList()
        return getTodos(snapshot)
    }
}
