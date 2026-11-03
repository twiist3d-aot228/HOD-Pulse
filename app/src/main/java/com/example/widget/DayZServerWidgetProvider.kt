package com.example.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.DayZServerRepository
import com.example.data.DayZServerStatus
import com.example.data.DayZTimeHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DayZServerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        refreshAllWidgetsAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET,
            ACTION_AUTO_TICK -> {
                refreshAllWidgetsAsync(context)
                scheduleNextTick(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        scheduleNextTick(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelTick(context)
    }

    private fun refreshAllWidgetsAsync(context: Context) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val status = DayZServerRepository.instance.refresh(context)
                updateAllWidgets(context, status)
            } catch (_: Exception) {
                updateAllWidgets(context, DayZServerStatus.offline())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.example.ACTION_REFRESH_WIDGET"
        const val ACTION_AUTO_TICK = "com.example.ACTION_AUTO_TICK"
        private const val TICK_INTERVAL_MS = 60_000L

        fun scheduleNextTick(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DayZServerWidgetProvider::class.java).apply {
                    action = ACTION_AUTO_TICK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val triggerAt = SystemClock.elapsedRealtime() + TICK_INTERVAL_MS
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } catch (_: Exception) {}
        }

        fun cancelTick(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val intent = Intent(context, DayZServerWidgetProvider::class.java).apply {
                    action = ACTION_AUTO_TICK
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    9001,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                alarmManager.cancel(pendingIntent)
            } catch (_: Exception) {}
        }

        fun updateAllWidgets(context: Context, status: DayZServerStatus) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val thisWidget = ComponentName(context, DayZServerWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            if (appWidgetIds.isEmpty()) return

            for (widgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_dayz_server)
                bindViews(context, views, status)
                appWidgetManager.updateAppWidget(widgetId, views)
            }
        }

        private fun bindViews(context: Context, views: RemoteViews, status: DayZServerStatus) {
            // Server Name
            views.setTextViewText(R.id.widget_server_name, status.serverName)

            // Players
            views.setTextViewText(R.id.widget_players_count, status.displayPlayers)

            // Map
            views.setTextViewText(R.id.widget_map_name, status.map)

            // In-game time of day
            val timeDisplay = if (status.isOnline) {
                if (status.inGamePhase.isNotBlank()) "${status.inGameTime} (${status.inGamePhase})" else status.inGameTime
            } else {
                "--:--"
            }
            views.setTextViewText(R.id.widget_ingame_time, timeDisplay)

            // RESTART IN countdown
            val remainingMs = DayZTimeHelper.getRestartCountdownMs()
            val countdownStr = DayZTimeHelper.formatCountdown(remainingMs)
            views.setTextViewText(R.id.widget_restart_countdown, countdownStr)

            // Last Updated
            val lastUpdatedText = "LAST UPDATED: " + DayZTimeHelper.formatTimestamp(status.lastUpdatedMillis)
            views.setTextViewText(R.id.widget_last_updated, lastUpdatedText)

            // Online / Offline State
            if (status.isOnline) {
                views.setViewVisibility(R.id.widget_offline_banner, View.GONE)
                views.setTextViewText(R.id.widget_status_badge, "ONLINE")
                views.setTextColor(R.id.widget_status_badge, 0xFF00FF66.toInt())
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_online)
                views.setTextColor(R.id.widget_players_count, 0xFF00FF66.toInt())
            } else {
                views.setViewVisibility(R.id.widget_offline_banner, View.VISIBLE)
                views.setTextViewText(R.id.widget_offline_banner, status.errorMessage ?: context.getString(R.string.server_offline))
                views.setTextViewText(R.id.widget_status_badge, "OFFLINE")
                views.setTextColor(R.id.widget_status_badge, 0xFFFF4444.toInt())
                views.setInt(R.id.widget_status_badge, "setBackgroundResource", R.drawable.widget_badge_offline)
                views.setTextColor(R.id.widget_players_count, 0xFF7A8F80.toInt())
            }

            // PendingIntent: Refresh when tapping refresh button or entire widget
            val refreshIntent = Intent(context, DayZServerWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_WIDGET
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_refresh, refreshPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_root, refreshPendingIntent)

            // PendingIntent: Open MainActivity when tapping open button or title
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                1,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_open, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_server_name, openAppPendingIntent)
        }
    }
}
