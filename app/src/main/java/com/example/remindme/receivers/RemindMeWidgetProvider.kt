package com.example.remindme.receivers

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.example.remindme.R
import com.example.remindme.utils.LanguageManager
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
            val reminders = ReminderStorage.loadReminders(context)
            val activeReminders = reminders.filter { it.isEnabled }

            val noRemindersText = when (currentLang) {
                "he" -> "אין תזכורות קרובות"
                "es" -> "No hay recordatorios"
                "fr" -> "Pas de rappels"
                "ru" -> "Нет напоминаний"
                "de" -> "Keine Erinnerungen"
                "pt" -> "Sem lembretes"
                "ja" -> "リマインダーはありません"
                "zh" -> "没有提醒"
                else -> "No upcoming reminders"
            }

            val nextReminderText = if (activeReminders.isNotEmpty()) {
                val next = activeReminders.first()
                val timeStr = if (next.time.isNotBlank()) "⏰ ${next.time}" else ""
                "$timeStr ${next.title}"
            } else {
                noRemindersText
            }

            views.setTextViewText(R.id.widget_reminder_text, nextReminderText)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}