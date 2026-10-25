package de.yukigasai.obsidiantodowidget.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.content.edit
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
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

    private fun cancelAlarms(context: Context, appWidgetId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val preferences = context.getSharedPreferences(PreferencesConstants.WIDGET_REMINDERS, Context.MODE_PRIVATE)
        preferences.getStringSet(appWidgetId.toString(), emptySet()).orEmpty().forEach { todoId ->
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, Reminder.alarmIntent(context, appWidgetId, todoId.toInt()),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        preferences.edit { remove(appWidgetId.toString()) }
    }

    fun deleteWidget(context: Context, appWidgetId: Int) {
        cancelAlarms(context, appWidgetId)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.activeNotifications.filter { it.tag == "widget:$appWidgetId" }.forEach {
            manager.cancel(it.tag, it.id)
        }
    }

    fun update(context: Context, appWidgetId: Int, config: WidgetConfig, todoItems: List<TodoItem>) {
        cancelAlarms(context, appWidgetId)
        val reminderFactory = ReminderFactory()
        val reminders = todoItems.mapNotNull { reminderFactory.createFromTodo(it) }
        context.getSharedPreferences(PreferencesConstants.WIDGET_REMINDERS, Context.MODE_PRIVATE).edit {
            putStringSet(appWidgetId.toString(), reminders.map { it.todo.id.toString() }.toSet())
        }
        reminders.forEach { it.register(context, appWidgetId, config) }
    }

}
