package de.yukigasai.obsidiantodowidget.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import de.yukigasai.obsidiantodowidget.WidgetConfig
import de.yukigasai.obsidiantodowidget.WidgetRefreshWorker

class OnScreenReceiver: BroadcastReceiver() {
    companion object {
        private var receiver: OnScreenReceiver? = null

        @Synchronized
        fun updateRegistration(context: Context) {
            val appContext = context.applicationContext
            if (WidgetConfig.getWidgetIds(appContext).isEmpty()) {
                receiver?.let { appContext.unregisterReceiver(it) }
                receiver = null
            } else if (receiver == null) {
                val newReceiver = OnScreenReceiver()
                ContextCompat.registerReceiver(
                    appContext, newReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED
                )
                receiver = newReceiver
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_USER_PRESENT) WidgetRefreshWorker.enqueue(context)
    }

}
