package de.yukigasai.obsidiantodowidget.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
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
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.todo.TodoItem
import de.yukigasai.obsidiantodowidget.util.WidgetLogger


object ReminderStartReceiver : SimpleReceiver {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ActionsConstants.START_NOTIFICATION) {
            WidgetLogger.warn("ReminderStartReceiver wrong action received ${intent.action}")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val gson = Gson()

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId !in WidgetConfig.getWidgetIds(context)) return
        val configJson = intent.getStringExtra(ExtrasConstants.NOTIFICATION_CONFIG) ?: return
        val config = gson.fromJson(configJson, WidgetConfig::class.java)

        val title = intent.getStringExtra(ExtrasConstants.NOTIFICATION_TITLE)
        val desc = intent.getStringExtra(ExtrasConstants.NOTIFICATION_MESSAGE)
        val todo = gson.fromJson(intent.getStringExtra(ExtrasConstants.NOTIFICATION_TODO), TodoItem::class.java)
        WidgetLogger.info("ReminderStartReceiver send notification for ${todo.name}")

        // CREATE CALLBACK FOR CHECK OPTION
        val checkIntent = Intent(context, TodoWidgetReceiver::class.java)
        checkIntent.action = ActionsConstants.END_NOTIFICATION_CHECK
        checkIntent.data = intent.data
        checkIntent.putExtra(ExtrasConstants.NOTIFICATION_TODO, gson.toJson(todo))
        checkIntent.putExtra(ExtrasConstants.NOTIFICATION_CONFIG, configJson)
        checkIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)

        val checkPendingIntent = PendingIntent.getBroadcast(context, 0, checkIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // CREATE CALLBACK FOR OPEN OPTION
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse(config.getFullObsidianURL())).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val openPendingIntent = PendingIntent.getActivity(
            context, appWidgetId, openIntent,
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
            .setContentTitle(title)
            .setContentText(desc)
            .addAction( NotificationCompat.Action.Builder(R.drawable.file_open, "Open", openPendingIntent).build())
            .addAction(NotificationCompat.Action.Builder(R.drawable.check, "Done", checkPendingIntent).build())
            .setAutoCancel(true)
        // Register the channel with the notification manager
        notificationManager.createNotificationChannel(notificationChannel)
        // Send the notification with a unique id
        notificationManager.notify("widget:$appWidgetId", todo.id, builder.build())
    }
}
