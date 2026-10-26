package de.yukigasai.obsidiantodowidget.todo

import android.content.Context
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.util.FsHelper
import de.yukigasai.obsidiantodowidget.util.NoteSnapshot
import java.time.LocalDateTime

data class WidgetSnapshot(
    val config: WidgetConfig,
    val source: WidgetConfig,
    val todos: List<TodoItem>?
)

object TodoRepo {
    fun getSnapshots(context: Context): Map<Int, WidgetSnapshot> {
        val cache = mutableMapOf<String, NoteSnapshot?>()
        val today = LocalDateTime.now()
        return WidgetConfig.getWidgetIds(context).associateWith { id ->
            val config = WidgetConfig.loadFromPrefs(context, id)
            val source = config.resolved(today)
            val todos = if (config.isConfigured()) {
                FsHelper.readNote(source, cache)?.let { TodoFactory(source).getTodos(it) }
            } else emptyList()
            WidgetSnapshot(config, source, todos)
        }
    }
}
