package de.yukigasai.obsidiantodowidget.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Base64
import de.yukigasai.obsidiantodowidget.TodoWidgetReceiver
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants
import java.security.MessageDigest

data class Reminder(
    val todo: TodoItem,
    val title: String,
    val date: Long,
    val dateText: String,
    val config: WidgetConfig,
    val delivered: Boolean = false
) {

    val id: String
        get() {
            val identity = "${todo.sourcePath}\n${todo.stateOffset}\n${todo.name}\n$date"
            return Base64.encodeToString(
                MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

    companion object {
        fun alarmIntent(context: Context, id: String): Intent =
            Intent(context, TodoWidgetReceiver::class.java)
                .setAction(ActionsConstants.START_NOTIFICATION)
                .setData(Uri.parse("obsidian-todo-widget://reminder/$id"))
    }

    fun register(context: Context) {
        val intent = alarmIntent(context, id).putExtra(ExtrasConstants.REMINDER_ID, id)

        // Create a pending intent that will be triggered when the alarm goes off
        val alarmIntent = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // Get the alarm manager from the system
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarmIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, date, alarmIntent)
        }
    }

}
