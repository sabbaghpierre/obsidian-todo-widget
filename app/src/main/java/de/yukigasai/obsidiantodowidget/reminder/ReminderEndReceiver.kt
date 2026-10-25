package de.yukigasai.obsidiantodowidget.reminder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.WidgetLogger

object ReminderEndReceiver : SimpleReceiver {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionsConstants.END_NOTIFICATION_CHECK) {
            WidgetLogger.warn("ReminderEndReceiver wrong action received ${intent.action}")
            return
        }

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId !in WidgetConfig.getWidgetIds(context)) return
        val configJson = intent.getStringExtra(ExtrasConstants.NOTIFICATION_CONFIG) ?: return
        val gson = Gson()
        val config = gson.fromJson(configJson, WidgetConfig::class.java)

        val todo = gson.fromJson(
            intent.getStringExtra(ExtrasConstants.NOTIFICATION_TODO),
            TodoItem::class.java
        )
        todo.updateInFile(config)
        Toast.makeText(context, "Marked ${todo.name} as done.", Toast.LENGTH_SHORT).show()

        NotificationManagerCompat.from(context).cancel("widget:$appWidgetId", todo.id)

    }
}
