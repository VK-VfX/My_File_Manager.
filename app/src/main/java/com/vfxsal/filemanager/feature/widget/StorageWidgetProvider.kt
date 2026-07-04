package com.vfxsal.filemanager.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vfxsal.filemanager.MainActivity
import com.vfxsal.filemanager.R
import com.vfxsal.filemanager.util.FormatUtils
import com.vfxsal.filemanager.util.StorageStatsUtils

class StorageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { id -> updateWidget(context, appWidgetManager, id) }
    }

    companion object {
        /** Called after Clean/storage-affecting operations so the widget doesn't wait for
         *  the next [android.appwidget.AppWidgetProviderInfo.updatePeriodMillis] tick. */
        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, StorageWidgetProvider::class.java))
            ids.forEach { id -> updateWidget(context, manager, id) }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val stats = StorageStatsUtils.primaryStorageStats()
            val usedPercent = (stats.usedFraction * 100).toInt().coerceIn(0, 100)
            val views = RemoteViews(context.packageName, R.layout.widget_storage).apply {
                setProgressBar(R.id.widget_progress, 100, usedPercent, false)
                setTextViewText(
                    R.id.widget_stats,
                    "${FormatUtils.formatFileSize(stats.freeBytes)} free of ${FormatUtils.formatFileSize(stats.totalBytes)}",
                )
                val launchIntent = Intent(context, MainActivity::class.java)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
