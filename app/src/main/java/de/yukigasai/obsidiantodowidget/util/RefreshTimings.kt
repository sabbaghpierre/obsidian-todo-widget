package de.yukigasai.obsidiantodowidget.util

import android.os.SystemClock
import android.util.Log

object RefreshTimings {
    fun mark(started: Long, stage: String) {
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
        Log.i("WidgetRefresh", "request=$started stage=$stage elapsedMs=$elapsedMs")
    }
}
