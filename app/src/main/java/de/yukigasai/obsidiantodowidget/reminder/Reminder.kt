package de.yukigasai.obsidiantodowidget.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.TodoWidgetReceiver
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants

data class Reminder (val todo: TodoItem, val title: String, val date: Long, val dateText: String, var pendingIntent: PendingIntent? = null) {

    fun register(context: Context) {

        val intent = Intent(context, TodoWidgetReceiver::class.java)
        val gson = Gson()
        // Set the action and the data for the intent
        intent.action = ActionsConstants.START_NOTIFICATION
        intent.putExtra(ExtrasConstants.NOTIFICATION_TITLE, this.title)
        intent.putExtra(ExtrasConstants.NOTIFICATION_MESSAGE, this.dateText)
        intent.putExtra(ExtrasConstants.NOTIFICATION_TODO, gson.toJson(todo))

        // Create a pending intent that will be triggered when the alarm goes off
        val alarmIntent = PendingIntent.getBroadcast(
            context, todo.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent = alarmIntent
        // Get the alarm manager from the system
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Set alarm for task: will not set a new one if one with same todoId is already set
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarmIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarmIntent)
        }
    }

}
