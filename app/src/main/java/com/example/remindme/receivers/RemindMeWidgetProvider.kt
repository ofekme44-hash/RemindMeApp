package com.example.remindme.receivers

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.app.PendingIntent
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.example.remindme.R
import com.example.remindme.ui.theme.countdownText
import com.example.remindme.ui.theme.reminderIcon
import com.example.remindme.utils.LanguageManager
import com.example.remindme.utils.NotificationHelper
import com.example.remindme.utils.ReminderStorage

class RemindMeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_remindme)

            val currentLang = LanguageManager.getSelectedLanguage(context)
            val now = System.currentTimeMillis()

            // The reminder that will ring next (not just the first one in the list)
            val next = ReminderStorage.loadReminders(context)
                .filter { it.isEnabled && it.time.isNotBlank() && it.date.isNotBlank() }
                .mapNotNull { r ->
                    val repeat = if (r.repeatType != "NONE") r.repeatType else if (r.isDaily) "DAILY" else "NONE"
                    NotificationHelper.nextTriggerMillis(r.date, r.time, repeat, now)?.let { at -> r to at }
                }
                .minByOrNull { it.second }
                ?.first

            val upcoming = ReminderStorage.loadReminders(context)
                .filter { it.isEnabled && it.time.isNotBlank() && it.date.isNotBlank() }
                .mapNotNull { r ->
                    val repeat = if (r.repeatType != "NONE") r.repeatType else if (r.isDaily) "DAILY" else "NONE"
                    NotificationHelper.nextTriggerMillis(r.date, r.time, repeat, now)?.let { at -> r to at }
                }
                .sortedBy { it.second }
                .map { it.first }

            val noRemindersText = when (currentLang) {
                "he" -> "אין תזכורות קרובות"
                "fr" -> "Pas de rappels"
                "ru" -> "Нет напоминаний"
                else -> "No upcoming reminders"
            }
            val summaryText = when (currentLang) {
                "he" -> "${upcoming.size} תזכורות פעילות"
                "fr" -> "${upcoming.size} rappels actifs"
                "ru" -> "Активных: ${upcoming.size}"
                else -> "${upcoming.size} active reminders"
            }
            views.setTextViewText(R.id.widget_summary, summaryText)

            val rowIds = listOf(
                Triple(R.id.row1_icon, R.id.row1_title, R.id.row1_sub) to R.id.row1,
                Triple(R.id.row2_icon, R.id.row2_title, R.id.row2_sub) to R.id.row2,
                Triple(R.id.row3_icon, R.id.row3_title, R.id.row3_sub) to R.id.row3
            )
            rowIds.forEachIndexed { index, (ids, rowId) ->
                val r = upcoming.getOrNull(index)
                if (r != null) {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(ids.first, reminderIcon(r).ifBlank { "⏰" })
                    views.setTextViewText(ids.second, r.title)
                    val countdown = countdownText(currentLang, r, now).orEmpty()
                    views.setTextViewText(ids.third, "⏰ ${r.time}  ·  $countdown".trimEnd(' ', '·'))
                } else if (index == 0) {
                    views.setViewVisibility(rowId, View.VISIBLE)
                    views.setTextViewText(ids.first, "✨")
                    views.setTextViewText(ids.second, noRemindersText)
                    views.setTextViewText(ids.third, "")
                } else {
                    views.setViewVisibility(rowId, View.GONE)
                }
            }

            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { launch ->
                val pi = PendingIntent.getActivity(
                    context, 0, launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pi)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}