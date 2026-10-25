package de.yukigasai.obsidiantodowidget.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import de.yukigasai.obsidiantodowidget.util.ActionsConstants
import de.yukigasai.obsidiantodowidget.util.ChannelConstants
import de.yukigasai.obsidiantodowidget.util.ExtrasConstants
import de.yukigasai.obsidiantodowidget.R
import de.yukigasai.obsidiantodowidget.TodoWidgetReceiver
import de.yukigasai.obsidiantodowidget.util.WidgetLogger


object ReminderStartReceiver : SimpleReceiver {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionsConstants.START_NOTIFICATION) {
            WidgetLogger.warn("ReminderStartReceiver wrong action received ${intent.action}")
            return
        }
        if (!notificationsAllowed(context)) return
        val id = intent.getStringExtra(ExtrasConstants.REMINDER_ID) ?: return
        ReminderRepo.getInstance().deliver(context, id)
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postNotification(context: Context, reminder: Reminder): Boolean {
        if (!notificationsAllowed(context)) return false

        // CREATE CALLBACK FOR CHECK OPTION
        val checkIntent = Intent(context, TodoWidgetReceiver::class.java)
        checkIntent.action = ActionsConstants.END_NOTIFICATION_CHECK
        checkIntent.data = Uri.Builder()
            .scheme("obsidian-todo-widget")
            .authority("done")
            .appendPath(reminder.id)
            .appendQueryParameter("revision", reminder.todo.sourceRevision)
            .build()
        checkIntent.putExtra(ExtrasConstants.REMINDER_ID, reminder.id)
        checkIntent.putExtra(ExtrasConstants.NOTIFICATION_TODO, Gson().toJson(reminder.todo))

        val checkPendingIntent = PendingIntent.getBroadcast(context, 0, checkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // CREATE CALLBACK FOR OPEN OPTION
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(reminder.config.getFullObsidianURL())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create a notification channel needed for Android O and above
        val channelId = ChannelConstants.ID
        val channelName = ChannelConstants.NAME
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(channelId, channelName, importance)
        // Get the notification manager from the system
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Create the notification builder and set the properties
        val builder = NotificationCompat.Builder(context, channelId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setBadgeIconType(R.mipmap.ic_launcher)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(reminder.title)
            .setContentText(reminder.dateText)
            .setContentIntent(openPendingIntent)
            .setOnlyAlertOnce(true)
            .addAction( NotificationCompat.Action.Builder(R.drawable.file_open, "Open", openPendingIntent).build())
            .addAction(NotificationCompat.Action.Builder(R.drawable.check, "Done", checkPendingIntent).build())
            .setAutoCancel(true)
        // Register the channel with the notification manager
        return try {
            notificationManager.createNotificationChannel(notificationChannel)
            // Send the notification with a unique id
            notificationManager.notify("reminder:${reminder.id}", 0, builder.build())
            true
        } catch (error: SecurityException) {
            WidgetLogger.warn("Reminder notification could not be posted: ${error.message}")
            false
        }
    }
}
