package de.yukigasai.obsidiantodowidget.todo

import android.content.Context
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.util.FsHelper
import de.yukigasai.obsidiantodowidget.util.PreferencesConstants
import de.yukigasai.obsidiantodowidget.util.WidgetLogger
import java.util.UUID

data class CompletionUndo(
    val widgetId: Int,
    val token: String,
    val config: WidgetConfig,
    val source: WidgetConfig,
    val item: TodoItem,
    val previousState: String
)

object TaskUndo {
    private fun preferences(context: Context) =
        context.getSharedPreferences(PreferencesConstants.WIDGET_UNDO, Context.MODE_PRIVATE)

    private fun read(context: Context): List<CompletionUndo> =
        Gson().fromJson(preferences(context).getString("completions", null), Array<CompletionUndo>::class.java)
            .orEmpty().toList()

    private fun save(context: Context, records: List<CompletionUndo>) {
        if (!preferences(context).edit().putString("completions", Gson().toJson(records)).commit()) {
            WidgetLogger.error("Could not persist task Undo state")
        }
    }

    @Synchronized
    fun update(
        context: Context,
        widgetId: Int,
        config: WidgetConfig,
        source: WidgetConfig,
        item: TodoItem,
        checked: Boolean?
    ): TodoItem? {
        if (widgetId !in WidgetConfig.getWidgetIds(context) ||
            WidgetConfig.loadFromPrefs(context, widgetId) != config
        ) return null
        val updated = item.updateInFile(source, checked) ?: return null
        if (updated.state == item.state) return updated
        val records = read(context).filterNot {
            it.item.sourcePath == item.sourcePath || it.widgetId == widgetId
        }
        val undo = if (!item.isChecked && updated.isChecked) {
            CompletionUndo(widgetId, UUID.randomUUID().toString(), config, source, updated, item.state)
        } else null
        save(context, if (undo == null) records else records + undo)
        return updated
    }

    @Synchronized
    fun completeReminder(context: Context, source: WidgetConfig, item: TodoItem): Boolean {
        val updated = item.updateInFile(source, checked = true) ?: return false
        if (updated.state != item.state) {
            save(context, read(context).filterNot { it.item.sourcePath == item.sourcePath })
        }
        return true
    }

    @Synchronized
    fun append(
        context: Context,
        widgetId: Int,
        config: WidgetConfig,
        source: WidgetConfig,
        task: String
    ): Boolean {
        if (widgetId !in WidgetConfig.getWidgetIds(context) ||
            WidgetConfig.loadFromPrefs(context, widgetId) != config
        ) return false
        val snapshot = FsHelper.appendTask(source, task) ?: return false
        save(context, read(context).filterNot { it.item.sourcePath == snapshot.path || it.widgetId == widgetId })
        return true
    }

    @Synchronized
    fun visible(context: Context, widgetId: Int, snapshot: WidgetSnapshot): CompletionUndo? {
        val undo = read(context).firstOrNull { it.widgetId == widgetId } ?: return null
        if (undo.config != snapshot.config || undo.source != snapshot.source) return null
        return undo.takeIf {
            snapshot.todos.orEmpty().any { todo ->
                todo.sourcePath == undo.item.sourcePath && todo.sourceRevision == undo.item.sourceRevision &&
                    todo.stateOffset == undo.item.stateOffset && todo.state == undo.item.state
            }
        }
    }

    @Synchronized
    fun undo(context: Context, widgetId: Int, token: String): Boolean {
        val records = read(context)
        val undo = records.firstOrNull { it.widgetId == widgetId && it.token == token } ?: return false
        if (widgetId !in WidgetConfig.getWidgetIds(context) ||
            WidgetConfig.loadFromPrefs(context, widgetId) != undo.config ||
            undo.config.resolved() != undo.source
        ) return false
        if (FsHelper.updateTask(undo.source, undo.item, undo.previousState) == null) return false
        save(context, records.filterNot { it.item.sourcePath == undo.item.sourcePath })
        return true
    }

    @Synchronized
    fun clear(context: Context, widgetIds: IntArray) {
        save(context, read(context).filterNot { it.widgetId in widgetIds })
    }
}
