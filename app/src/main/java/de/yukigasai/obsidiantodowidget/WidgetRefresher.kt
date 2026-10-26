package de.yukigasai.obsidiantodowidget

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.reminder.ReminderRepo
import de.yukigasai.obsidiantodowidget.todo.TaskUndo
import de.yukigasai.obsidiantodowidget.util.OnScreenReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetRefresher {
    val configKey = stringPreferencesKey("widgetConfig")
    val sourceKey = stringPreferencesKey("widgetSource")
    val todosKey = stringPreferencesKey("widgetTodos")
    val undoKey = stringPreferencesKey("widgetUndo")
    private val mutex = Mutex()

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val appContext = context.applicationContext
            OnScreenReceiver.updateRegistration(appContext)
            val snapshots = ReminderRepo.getInstance().refresh(appContext)
            val manager = GlanceAppWidgetManager(appContext)
            val gson = Gson()
            snapshots.forEach { (id, snapshot) ->
                if (id !in WidgetConfig.getWidgetIds(appContext) ||
                    WidgetConfig.loadFromPrefs(appContext, id) != snapshot.config
                ) return@forEach
                try {
                    val glanceId = manager.getGlanceIdBy(id)
                    val undo = TaskUndo.visible(appContext, id, snapshot)
                    updateAppWidgetState(appContext, glanceId) { state ->
                        if (undo == null) state.remove(undoKey) else state[undoKey] = gson.toJson(undo)
                        val config = gson.toJson(snapshot.config)
                        val source = gson.toJson(snapshot.source)
                        if (snapshot.todos == null && state[configKey] == config && state[sourceKey] == source) {
                            return@updateAppWidgetState
                        }
                        state[configKey] = config
                        state[sourceKey] = source
                        state[todosKey] = gson.toJson(snapshot.todos.orEmpty())
                        snapshot.todos.orEmpty().forEach { todo ->
                            state[booleanPreferencesKey(todo.id.toString())] = todo.isChecked
                        }
                    }
                    TodoWidget().update(appContext, glanceId)
                } catch (error: IllegalArgumentException) {
                    if (id in WidgetConfig.getWidgetIds(appContext)) throw error
                }
            }
        }
    }
}
