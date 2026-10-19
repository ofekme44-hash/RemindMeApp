package com.example.remindme.utils

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.core.content.edit
import com.example.remindme.receivers.RemindMeWidgetProvider
import com.example.remindme.ui.theme.ReminderItem
import org.json.JSONArray
import org.json.JSONObject

object ReminderStorage {
    private const val PREFS_NAME = "remindme_prefs"
    private const val KEY_REMINDERS = "saved_reminders"
    private const val KEY_INITIALIZED = "is_initialized"
    private const val KEY_ALARM_WINDOW_MODE = "alarm_window_mode"
    private const val KEY_RING_DURATION_SEC = "ring_duration_sec"
    private const val KEY_SNOOZE_COUNT = "snooze_count"
    private const val KEY_DARK_MODE = "is_dark_mode"

    fun saveReminders(context: Context, reminders: List<ReminderItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // What was saved before, so alarms of deleted reminders can be cancelled
        val previousIds = loadReminders(context).map { it.id }.toSet()

        val jsonArray = JSONArray()
        for (item in reminders) {
            val jsonObject = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("time", item.time)
                put("date", item.date)
                put("isEnabled", item.isEnabled)
                put("isDaily", item.isDaily)
                put("repeatType", item.repeatType)
                put("soundUri", item.soundUri ?: JSONObject.NULL)
                put("advanceMinutes", item.advanceMinutes)
                put("ringSeconds", item.ringSeconds)
            }
            jsonArray.put(jsonObject)
        }

        prefs.edit {
            putString(KEY_REMINDERS, jsonArray.toString())
            putBoolean(KEY_INITIALIZED, true)
        }

        // Keep the system alarms in sync with the saved list
        val currentIds = reminders.map { it.id }.toSet()
        (previousIds - currentIds).forEach { NotificationHelper.cancelReminder(context, it) }
        reminders.forEach { syncAlarm(context, it) }

        // רענון אוטומטי של הווידג'ט מיד עם שמירת התזכורת
        refreshWidgets(context)

        // גיבוי לחשבון Google (אם מחוברים)
        CloudSync.push(context)
    }

    /** The saved reminders exactly as stored, for the cloud backup. */
    fun getRemindersJson(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_REMINDERS, null) ?: "[]"

    /** Replaces everything on the phone with the data from the cloud and re-arms the alarms. */
    fun replaceAllFromCloud(context: Context, json: String) {
        val valid = try {
            JSONArray(json)
            true
        } catch (_: Exception) {
            false
        }
        if (!valid) return

        loadReminders(context).forEach { NotificationHelper.cancelReminder(context, it.id) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_REMINDERS, json)
            putBoolean(KEY_INITIALIZED, true)
        }
        loadReminders(context).forEach { syncAlarm(context, it) }
        refreshWidgets(context)
    }

    /** Used on sign-out: removes this account's reminders and alarms from the phone. */
    fun clearAllLocal(context: Context) {
        loadReminders(context).forEach { NotificationHelper.cancelReminder(context, it.id) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_REMINDERS, "[]")
            putBoolean(KEY_INITIALIZED, true)
        }
        AlarmService.stop(context)
        refreshWidgets(context)
    }

    /** Enabled reminders get (re)armed, disabled ones are cancelled. Safe to call repeatedly. */
    private fun syncAlarm(context: Context, item: ReminderItem) {
        if (item.isEnabled && item.time.isNotBlank() && item.date.isNotBlank()) {
            val repeatType = when {
                item.repeatType != "NONE" -> item.repeatType
                item.isDaily -> "DAILY"
                else -> "NONE"
            }
            NotificationHelper.schedule(
                context = context,
                id = item.id,
                title = item.title,
                dateStr = item.date,
                timeStr = item.time,
                soundUri = item.soundUri,
                repeatType = repeatType,
                advanceMinutes = item.advanceMinutes,
                ringSeconds = item.ringSeconds
            )
        } else {
            NotificationHelper.cancelReminder(context, item.id)
        }
    }

    /** Called after a reboot or app update, when the system has dropped all alarms. */
    fun rescheduleAll(context: Context) {
        loadReminders(context).forEach { syncAlarm(context, it) }
    }

    private fun refreshWidgets(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, RemindMeWidgetProvider::class.java)
        )

        if (widgetIds.isNotEmpty()) {
            val intent = android.content.Intent(context, RemindMeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            context.sendBroadcast(intent)

            // רענון מיידי וישיר של הווידג'ט
            val provider = RemindMeWidgetProvider()
            provider.onUpdate(context, appWidgetManager, widgetIds)
        }
    }

    fun loadReminders(context: Context): List<ReminderItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val jsonString = prefs.getString(KEY_REMINDERS, null)

        if (isInitialized) {
            if (jsonString.isNullOrEmpty()) return emptyList()
            val list = mutableListOf<ReminderItem>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        ReminderItem(
                            id = obj.getInt("id"),
                            title = obj.getString("title"),
                            time = obj.getString("time"),
                            date = obj.optString("date", ""),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            isDaily = obj.optBoolean("isDaily", false),
                            repeatType = obj.optString("repeatType", "NONE"),
                            soundUri = if (obj.has("soundUri") && !obj.isNull("soundUri")) obj.getString("soundUri") else null,
                            advanceMinutes = obj.optInt("advanceMinutes", 0),
                            ringSeconds = obj.optInt("ringSeconds", 0)
                        )
                    )
                }
            } catch (_: Exception) {
            }
            return list
        }

        return emptyList()
    }

    fun saveAlarmWindowMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putString(KEY_ALARM_WINDOW_MODE, mode) }
        CloudSync.push(context)
    }

    fun getAlarmWindowMode(context: Context): String {
        // "FULL" = full screen (default), "QUARTER" = top quarter of the screen
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ALARM_WINDOW_MODE, "FULL")
        return if (saved == "QUARTER") "QUARTER" else "FULL"
    }

    fun saveRingSettings(context: Context, durationSec: Int, snoozeCount: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt(KEY_RING_DURATION_SEC, durationSec)
            putInt(KEY_SNOOZE_COUNT, snoozeCount)
        }
        CloudSync.push(context)
    }

    fun getRingDurationSec(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_RING_DURATION_SEC, 30)
    }

    fun getSnoozeCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY_SNOOZE_COUNT, 3)
    }

    fun saveDarkMode(context: Context, isDark: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DARK_MODE, isDark)
        }
    }

    fun isDarkMode(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_DARK_MODE, true)
    }
}