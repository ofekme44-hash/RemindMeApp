package com.example.remindme.utils

import android.content.Context
import java.util.Locale

object LanguageManager {
    private const val PREFS_NAME = "remindme_lang_prefs"
    private const val KEY_LANGUAGE = "selected_language"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"

    fun isFirstLaunch(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }

    fun setFirstLaunchCompleted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }

    fun getSelectedLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "en") ?: "en"
    }

    fun saveLanguage(context: Context, langCode: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    fun getString(context: Context, key: String): String {
        val lang = getSelectedLanguage(context)
        return when (lang) {
            "he" -> when (key) {
                "app_title" -> "RemindMe"
                "my_reminders" -> "התזכורות שלי"
                "no_reminders" -> "אין תזכורות פעילות.\nלחץ על + כדי להוסיף תזכורת חדשה."
                "delete_title" -> "מחיקת תזכורת"
                "delete_msg" -> "האם אתה בטוח שברצונך למחוק את \"%s\"?"
                "delete_btn" -> "מחק"
                "cancel" -> "ביטול"
                "settings" -> "הגדרות התראה"
                "save" -> "שמור"
                "select_lang" -> "בחר שפה"
                "calendar_title" -> "לוח שנה"
                "back" -> "חזרה"
                "share_calendar" -> "שיתוף לוח"
                "reminders_for" -> "תזכורות לתאריך %s"
                "no_reminders_date" -> "אין תזכורות ליום זה.\nלחץ על + כדי להוסיף תזכורת."
                "add_calendar_reminder" -> "הוספת תזכורת בלוח השנה"
                "success_msg" -> "יום נעים ומלא הספק!"
                "add_title" -> "הוספת תזכורת חדשה"
                "reminder_title_label" -> "כותרת התזכורת"
                "reminder_time_label" -> "שעה (חובה)"
                "reminder_date_label" -> "תאריך"
                "sound_label" -> "🔔 צליל התראה"
                "save_and_schedule" -> "שמור ותזמן"
                else -> key
            }
            "ru" -> when (key) {
                "app_title" -> "RemindMe"
                "my_reminders" -> "Мои напоминания"
                "no_reminders" -> "Нет напоминаний.\nНажмите +, чтобы добавить."
                "delete_title" -> "Удалить напоминание"
                "delete_msg" -> "Вы уверены, что хотите удалить \"%s\"?"
                "delete_btn" -> "Удалить"
                "cancel" -> "Отмена"
                "settings" -> "Настройки"
                "save" -> "Сохранить"
                "select_lang" -> "Выберите язык"
                "calendar_title" -> "Календарь"
                "back" -> "Назад"
                "share_calendar" -> "Поделиться календарем"
                "reminders_for" -> "Напоминания на %s"
                "no_reminders_date" -> "На этот день нет напоминаний.\nНажмите +, чтобы добавить."
                "add_calendar_reminder" -> "Добавить напоминание"
                "success_msg" -> "Хорошего и продуктивного дня!"
                "add_title" -> "Новое напоминание"
                "reminder_title_label" -> "Название напоминания"
                "reminder_time_label" -> "Время (Обязательно)"
                "reminder_date_label" -> "Дата"
                "sound_label" -> "🔔 Звук уведомления"
                "save_and_schedule" -> "Сохранить"
                else -> key
            }
            "fr" -> when (key) {
                "app_title" -> "RemindMe"
                "my_reminders" -> "Mes Rappels"
                "no_reminders" -> "Aucun rappel.\nAppuyez sur + pour ajouter."
                "delete_title" -> "Supprimer le rappel"
                "delete_msg" -> "Voulez-vous supprimer \"%s\" ?"
                "delete_btn" -> "Supprimer"
                "cancel" -> "Annuler"
                "settings" -> "Paramètres"
                "save" -> "Enregistrer"
                "select_lang" -> "Choisir la langue"
                "calendar_title" -> "Calendrier"
                "back" -> "Retour"
                "share_calendar" -> "Partager le calendrier"
                "reminders_for" -> "Rappels pour le %s"
                "no_reminders_date" -> "Aucun rappel pour ce jour.\nAppuyez sur + pour ajouter."
                "add_calendar_reminder" -> "Ajouter un rappel"
                "success_msg" -> "Passez une excellente journée !"
                "add_title" -> "Nouveau rappel"
                "reminder_title_label" -> "Titre du rappel"
                "reminder_time_label" -> "Heure (Obligatoire)"
                "reminder_date_label" -> "Date"
                "sound_label" -> "🔔 Son de notification"
                "save_and_schedule" -> "Enregistrer"
                else -> key
            }
            else -> when (key) { // English (Default)
                "app_title" -> "RemindMe"
                "my_reminders" -> "My Reminders"
                "no_reminders" -> "No active reminders.\nTap + to add a new reminder."
                "delete_title" -> "Delete Reminder"
                "delete_msg" -> "Are you sure you want to delete \"%s\"?"
                "delete_btn" -> "Delete"
                "cancel" -> "Cancel"
                "settings" -> "Alert Settings"
                "save" -> "Save"
                "select_lang" -> "Select Language"
                "calendar_title" -> "Calendar"
                "back" -> "Back"
                "share_calendar" -> "Share Calendar"
                "reminders_for" -> "Reminders for date %s"
                "no_reminders_date" -> "No reminders for this day.\nTap + to add a reminder."
                "add_calendar_reminder" -> "Add Calendar Reminder"
                "success_msg" -> "Have a productive and pleasant day!"
                "add_title" -> "Add New Reminder"
                "reminder_title_label" -> "Reminder Title"
                "reminder_time_label" -> "Time (Required)"
                "reminder_date_label" -> "Date"
                "sound_label" -> "🔔 Notification Sound"
                "save_and_schedule" -> "Save & Schedule"
                else -> key
            }
        }
    }

    fun getLocale(context: Context): Locale {
        val lang = getSelectedLanguage(context)
        return when (lang) {
            "he" -> Locale("he", "IL")
            "ru" -> Locale("ru", "RU")
            "fr" -> Locale("fr", "FR")
            else -> Locale.ENGLISH
        }
    }
}