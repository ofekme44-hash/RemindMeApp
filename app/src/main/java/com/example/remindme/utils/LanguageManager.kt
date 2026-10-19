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
                "alert_title" -> "🔔 תזכורת!"
                "alert_default_title" -> "תזכורת"
                "alert_dismiss" -> "סגור התראה"
                "alert_display" -> "תצוגת ההתראה"
                "alert_mode_full" -> "מסך מלא"
                "alert_mode_quarter" -> "רבע מסך למעלה"
                "settings_title" -> "הגדרות"
                "language_label" -> "שפת הממשק"
                "login_title" -> "ברוך הבא ל-RemindMe"
                "login_subtitle" -> "התחבר עם חשבון Google כדי לשמור את התזכורות ולוח השנה שלך ולשחזר אותם בכל מכשיר."
                "login_button" -> "התחבר עם Google"
                "login_loading" -> "מתחבר וטוען את הנתונים..."
                "login_error" -> "ההתחברות נכשלה. בדוק חיבור לאינטרנט ונסה שוב."
                "login_cancelled" -> "ההתחברות בוטלה"
                "account_label" -> "חשבון"
                "logged_in_as" -> "מחובר כ-%s"
                "logout" -> "התנתק"
                "logout_title" -> "התנתקות מהחשבון"
                "logout_msg" -> "התזכורות נשמרות בחשבון Google שלך ויחזרו כשתתחבר שוב. להתנתק?"
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
                "alert_title" -> "🔔 Напоминание!"
                "alert_default_title" -> "Напоминание"
                "alert_dismiss" -> "Закрыть"
                "alert_display" -> "Отображение уведомления"
                "alert_mode_full" -> "Полный экран"
                "alert_mode_quarter" -> "Верхняя четверть экрана"
                "settings_title" -> "Настройки"
                "language_label" -> "Язык интерфейса"
                "login_title" -> "Добро пожаловать в RemindMe"
                "login_subtitle" -> "Войдите через аккаунт Google, чтобы сохранить напоминания и календарь и восстановить их на любом устройстве."
                "login_button" -> "Войти через Google"
                "login_loading" -> "Вход и загрузка данных..."
                "login_error" -> "Не удалось войти. Проверьте интернет и попробуйте снова."
                "login_cancelled" -> "Вход отменён"
                "account_label" -> "Аккаунт"
                "logged_in_as" -> "Вы вошли как %s"
                "logout" -> "Выйти"
                "logout_title" -> "Выход из аккаунта"
                "logout_msg" -> "Напоминания сохранены в вашем аккаунте Google и вернутся после повторного входа. Выйти?"
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
                "alert_title" -> "🔔 Rappel !"
                "alert_default_title" -> "Rappel"
                "alert_dismiss" -> "Fermer l'alerte"
                "alert_display" -> "Affichage de l'alerte"
                "alert_mode_full" -> "Plein écran"
                "alert_mode_quarter" -> "Quart supérieur de l'écran"
                "settings_title" -> "Paramètres"
                "language_label" -> "Langue de l'interface"
                "login_title" -> "Bienvenue sur RemindMe"
                "login_subtitle" -> "Connectez-vous avec Google pour sauvegarder vos rappels et votre calendrier et les retrouver sur tout appareil."
                "login_button" -> "Se connecter avec Google"
                "login_loading" -> "Connexion et chargement des données..."
                "login_error" -> "Échec de la connexion. Vérifiez votre connexion internet et réessayez."
                "login_cancelled" -> "Connexion annulée"
                "account_label" -> "Compte"
                "logged_in_as" -> "Connecté en tant que %s"
                "logout" -> "Se déconnecter"
                "logout_title" -> "Déconnexion du compte"
                "logout_msg" -> "Vos rappels sont enregistrés dans votre compte Google et reviendront à la prochaine connexion. Se déconnecter ?"
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
                "alert_title" -> "🔔 Reminder!"
                "alert_default_title" -> "Reminder"
                "alert_dismiss" -> "Dismiss"
                "alert_display" -> "Alert display"
                "alert_mode_full" -> "Full screen"
                "alert_mode_quarter" -> "Top quarter of the screen"
                "settings_title" -> "Settings"
                "language_label" -> "Interface language"
                "login_title" -> "Welcome to RemindMe"
                "login_subtitle" -> "Sign in with your Google account to back up your reminders and calendar and restore them on any device."
                "login_button" -> "Sign in with Google"
                "login_loading" -> "Signing in and loading your data..."
                "login_error" -> "Sign-in failed. Check your internet connection and try again."
                "login_cancelled" -> "Sign-in cancelled"
                "account_label" -> "Account"
                "logged_in_as" -> "Signed in as %s"
                "logout" -> "Sign out"
                "logout_title" -> "Sign out"
                "logout_msg" -> "Your reminders are saved in your Google account and will come back when you sign in again. Sign out?"
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