package de.yukigasai.obsidiantodowidget.todo

import de.yukigasai.obsidiantodowidget.WidgetConfig

object TodoRepo {
    fun getTodos(config: WidgetConfig): List<TodoItem> =
        if (config.isConfigured()) TodoFactory(config).getTodosFromFile() else emptyList()
}
