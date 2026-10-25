package de.yukigasai.obsidiantodowidget.reminder

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.R
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
        val displayedConfig = gson.fromJson(configJson, WidgetConfig::class.java)
        val config = WidgetConfig.loadFromPrefs(context, appWidgetId)

        val todo = gson.fromJson(
            intent.getStringExtra(ExtrasConstants.NOTIFICATION_TODO),
            TodoItem::class.java
        )
        val updated = if (displayedConfig == config) todo.updateInFile(config, checked = true) else null
        Handler(context.mainLooper).post {
            val message = if (updated != null) "Marked ${todo.name} as done."
                else context.getString(R.string.task_edit_failed)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
        if (updated != null) {
            NotificationManagerCompat.from(context).cancel("widget:$appWidgetId", todo.id)
        }

    }
}
