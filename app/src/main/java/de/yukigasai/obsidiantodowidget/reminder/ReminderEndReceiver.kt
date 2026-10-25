package de.yukigasai.obsidiantodowidget.reminder

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.widget.Toast
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants
import de.yukigasai.obsidiantodowidget.R
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.WidgetLogger

object ReminderEndReceiver : SimpleReceiver {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionsConstants.END_NOTIFICATION_CHECK) {
            WidgetLogger.warn("ReminderEndReceiver wrong action received ${intent.action}")
            return
        }

        val id = intent.getStringExtra(ExtrasConstants.REMINDER_ID) ?: return
        val gson = Gson()

        val todo = gson.fromJson(
            intent.getStringExtra(ExtrasConstants.NOTIFICATION_TODO),
            TodoItem::class.java
        )
        val updated = ReminderRepo.getInstance().complete(context, id, todo)
        Handler(context.mainLooper).post {
            val message = if (updated) "Marked ${todo.name} as done."
                else context.getString(R.string.task_edit_failed)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }

    }
}
