package de.yukigasai.obsidiantodowidget.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.todo.TodoRepo
import de.yukigasai.obsidiantodowidget.todo.WidgetSnapshot
import de.yukigasai.obsidiantodowidget.util.PreferencesConstants

class ReminderRepo private constructor() {
    companion object {

        @Volatile
        private var instance: ReminderRepo? = null

        fun getInstance() =
            instance ?: synchronized(this) {
                instance ?: ReminderRepo().also { instance = it }
            }
    }

    private data class ReminderState(val reminders: Map<String, Reminder>, val validated: Set<String>)

    private fun read(context: Context): Map<String, Reminder> {
        val preferences = context.getSharedPreferences(PreferencesConstants.WIDGET_REMINDERS, Context.MODE_PRIVATE)
        return Gson().fromJson(preferences.getString("reminders", null), Array<Reminder>::class.java)
            .orEmpty().associateBy { it.id }
    }

    private fun save(context: Context, reminders: Collection<Reminder>) {
        context.getSharedPreferences(PreferencesConstants.WIDGET_REMINDERS, Context.MODE_PRIVATE).edit(commit = true) {
            clear()
            putString("reminders", Gson().toJson(reminders))
        }
    }

    private fun cancelAlarm(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, Reminder.alarmIntent(context, id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun migrateLegacy(context: Context) {
        val preferences = context.getSharedPreferences(PreferencesConstants.WIDGET_REMINDERS, Context.MODE_PRIVATE)
        if (preferences.contains("reminders")) return
        preferences.all.keys.filter { it.toIntOrNull() != null }.forEach { widgetId ->
            preferences.getStringSet(widgetId, emptySet()).orEmpty().forEach { todoId ->
                cancelAlarm(context, "$widgetId/$todoId")
            }
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.filter { it.tag?.startsWith("widget:") == true }.forEach {
            manager.cancel(it.tag, it.id)
        }
    }

    private fun reconcile(
        context: Context,
        snapshots: Map<Int, WidgetSnapshot> = TodoRepo.getSnapshots(context)
    ): ReminderState {
        migrateLegacy(context)
        val previous = read(context)
        val reminders = linkedMapOf<String, Reminder>()
        val validated = mutableSetOf<String>()
        val factory = ReminderFactory()
        val now = System.currentTimeMillis()
        val activeIds = WidgetConfig.getWidgetIds(context).toSet()

        snapshots.toSortedMap().forEach widget@ { (widgetId, snapshot) ->
            if (widgetId !in activeIds) return@widget
            val config = snapshot.source
            if (!config.isConfigured()) return@widget
            val todos = snapshot.todos
            if (todos == null) {
                previous.forEach { (id, reminder) ->
                    if (reminder.config.copy(hideDoneTasks = config.hideDoneTasks) == config) {
                        reminders.putIfAbsent(id, reminder)
                    }
                }
                return@widget
            }
            todos.forEach task@ { todo ->
                val parsed = factory.createFromTodo(todo, config) ?: return@task
                val overdue = previous.values.firstOrNull {
                    it.date <= now && it.todo.sourcePath == todo.sourcePath &&
                        it.todo.stateOffset == todo.stateOffset && it.todo.name == todo.name
                }
                val reminder = if (overdue == null) parsed else parsed.copy(date = overdue.date)
                val id = reminder.id
                val old = previous[id]
                if (reminder.date <= now && old == null) return@task
                reminders[id] = reminder.copy(delivered = old?.delivered == true)
                validated.add(id)
            }
        }

        save(context, reminders.values)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        (previous.keys - reminders.keys).forEach { id ->
            cancelAlarm(context, id)
        }
        val activeNotifications = manager.activeNotifications
        activeNotifications.filter {
            val tag = it.tag
            tag != null && tag.startsWith("reminder:") && tag.removePrefix("reminder:") !in reminders
        }.forEach { manager.cancel(it.tag, it.id) }
        val activeTags = activeNotifications.mapNotNull { it.tag }.toSet()
        reminders.forEach { (id, reminder) ->
            if (reminder.delivered) {
                cancelAlarm(context, id)
                if (id in validated && "reminder:$id" in activeTags) {
                    ReminderStartReceiver.postNotification(context, reminder)
                }
            } else if (reminder.date > now || id in validated) {
                reminder.register(context)
            }
        }
        return ReminderState(reminders, validated)
    }

    @Synchronized
    fun refresh(context: Context): Map<Int, WidgetSnapshot> {
        val snapshots = TodoRepo.getSnapshots(context)
        reconcile(context, snapshots)
        return snapshots
    }

    @Synchronized
    fun deliver(context: Context, id: String) {
        val previous = read(context)[id] ?: return
        if (previous.delivered || previous.date > System.currentTimeMillis()) return
        val state = reconcile(context)
        val reminder = state.reminders[id] ?: return
        if (id !in state.validated) return
        val delivered = reminder.copy(delivered = true)
        save(context, (state.reminders + (id to delivered)).values)
        cancelAlarm(context, id)
        if (!ReminderStartReceiver.postNotification(context, delivered)) {
            save(context, state.reminders.values)
        }
    }

    @Synchronized
    fun complete(context: Context, id: String, todo: TodoItem): Boolean {
        val state = reconcile(context)
        val reminder = state.reminders[id] ?: return false
        if (id !in state.validated || !reminder.delivered ||
            todo.copy(id = reminder.todo.id) != reminder.todo
        ) return false
        if (todo.updateInFile(reminder.config, checked = true) == null) return false
        save(context, (state.reminders - id).values)
        cancelAlarm(context, id)
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel("reminder:$id", 0)
        return true
    }

}
