package de.yukigasai.obsidiantodowidget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.reminder.ReminderRepo
import de.yukigasai.obsidiantodowidget.todo.TaskUndo
import de.yukigasai.obsidiantodowidget.todo.TodoRepo
import de.yukigasai.obsidiantodowidget.util.OnScreenReceiver
import de.yukigasai.obsidiantodowidget.util.RefreshTimings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object WidgetRefresher {
    val configKey = stringPreferencesKey("widgetConfig")
    val sourceKey = stringPreferencesKey("widgetSource")
    val todosKey = stringPreferencesKey("widgetTodos")
    val undoKey = stringPreferencesKey("widgetUndo")
    val refreshStartedKey = longPreferencesKey("refreshStarted")
    private val mutex = Mutex()

    suspend fun refresh(context: Context, reason: String = "background") {
        val started = SystemClock.elapsedRealtimeNanos()
        RefreshTimings.mark(started, "$reason/requested")
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            RefreshTimings.mark(started, "dispatched")
            mutex.withLock {
                RefreshTimings.mark(started, "refresh_lock_acquired")
                OnScreenReceiver.updateRegistration(appContext)
                val snapshots = TodoRepo.getSnapshots(appContext)
                RefreshTimings.mark(started, "notes_read_and_parsed")
                val manager = GlanceAppWidgetManager(appContext)
                val appWidgetManager = AppWidgetManager.getInstance(appContext)
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
                            state[refreshStartedKey] = started
                            state[configKey] = config
                            state[sourceKey] = source
                            state[todosKey] = gson.toJson(snapshot.todos.orEmpty())
                            snapshot.todos.orEmpty().forEach { todo ->
                                state[booleanPreferencesKey(todo.id.toString())] = todo.isChecked
                            }
                        }
                        RefreshTimings.mark(started, "widget=$id/state_updated")
                        val widget = TodoWidget()
                        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S) {
                            val views = widget.compose(
                                appContext, glanceId,
                                options = appWidgetManager.getAppWidgetOptions(id),
                                state = widget.getAppWidgetState<Preferences>(appContext, glanceId)
                            )
                            if (id in WidgetConfig.getWidgetIds(appContext) &&
                                WidgetConfig.loadFromPrefs(appContext, id) == snapshot.config
                            ) appWidgetManager.updateAppWidget(id, views)
                        } else {
                            widget.update(appContext, glanceId)
                        }
                        RefreshTimings.mark(started, "widget=$id/render_requested")
                    } catch (error: IllegalArgumentException) {
                        if (id in WidgetConfig.getWidgetIds(appContext)) throw error
                    }
                }
            }
            ReminderRepo.getInstance().refresh(appContext, started)
        }
        RefreshTimings.mark(started, "request_finished")
    }
}
