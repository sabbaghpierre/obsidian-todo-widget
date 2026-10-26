package de.yukigasai.obsidiantodowidget

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ObsidianReturnService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var armed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        armed = false
        cancelRefresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val paneChanges = AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_APPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_DISAPPEARED or
            AccessibilityEvent.CONTENT_CHANGE_TYPE_PANE_TITLE
        if (event.contentChangeTypes and paneChanges != 0) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        val className = event.className?.toString()
        if (packageName == "android" || packageName == "com.android.systemui" ||
            className == "android.inputmethodservice.SoftInputWindow" ||
            className?.startsWith("android.widget.PopupWindow") == true ||
            className == "android.app.Dialog" || className == "android.app.AlertDialog" ||
            className == "androidx.appcompat.app.AlertDialog"
        ) return

        if (packageName == "md.obsidian") {
            cancelRefresh()
            armed = true
            return
        }
        if (!armed && refreshJob?.isActive != true) return

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeActivity = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
        val homePackage = homeActivity?.takeIf { resolved ->
            packageManager.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY).any {
                it.activityInfo.packageName == resolved.packageName && it.activityInfo.name == resolved.name
            }
        }?.packageName
        if (packageName == homePackage) {
            if (armed) {
                armed = false
                refreshJob = scope.launch {
                    delay(150)
                    refreshOnce("obsidian_home")
                    delay(3_000)
                    refreshOnce("obsidian_follow_up")
                }
            }
        } else {
            armed = false
            cancelRefresh()
        }
    }

    private suspend fun refreshOnce(reason: String) {
        try {
            if (WidgetConfig.getWidgetIds(this).isEmpty()) return
            WidgetRefresher.refresh(applicationContext, reason)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e("ObsidianReturnService", "Widget refresh failed", error)
        }
    }

    private fun cancelRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        armed = false
        cancelRefresh()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
