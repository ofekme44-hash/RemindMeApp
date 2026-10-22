package com.example.remindme.ui.theme

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.remindme.utils.AlarmService
import com.example.remindme.utils.CloudSync
import com.example.remindme.utils.HistoryEntry
import com.example.remindme.utils.LanguageManager
import com.example.remindme.utils.NotificationHelper
import com.example.remindme.utils.ReminderStorage
import com.example.remindme.utils.ShareUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

// ───────────────────────────── Colors ─────────────────────────────

val DarkBackground = Color(0xFF0A0A0C)
val LightBackground = Color(0xFFF4F4F6)
val SurfaceCardDark = Color(0xFF161619)
val SurfaceCardLight = Color(0xFFFFFFFF)
val GoldAccent = Color(0xFFE6C687)
val TextPrimaryDark = Color(0xFFF8F8FA)
val TextPrimaryLight = Color(0xFF111115)
val TextSecondaryDark = Color(0xFF9E9EA7)
val TextSecondaryLight = Color(0xFF55555D)

private data class AppColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

private fun appColors(isDark: Boolean): AppColors =
    if (isDark) AppColors(DarkBackground, SurfaceCardDark, TextPrimaryDark, TextSecondaryDark)
    else AppColors(LightBackground, SurfaceCardLight, TextPrimaryLight, TextSecondaryLight)

// ───────────────────────────── Premium look helpers ─────────────────────────────

val GoldBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(
    listOf(Color(0xFFF3DDA5), GoldAccent, Color(0xFFC9A24F))
)

/** Soft vertical gradient used behind every screen. */
fun Modifier.premiumBackground(isDark: Boolean): Modifier = this.background(
    androidx.compose.ui.graphics.Brush.verticalGradient(
        if (isDark) listOf(Color(0xFF18181F), Color(0xFF0D0D11), Color(0xFF0A0A0C))
        else listOf(Color(0xFFFFFFFF), Color(0xFFF4F4F6), Color(0xFFECECF1))
    )
)

@Composable
private fun PremiumChoiceChip(
    label: String,
    selected: Boolean,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val (_, _, textPrimary, _) = appColors(isDarkMode)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .then(if (selected) Modifier.background(GoldBrush) else Modifier.background(GoldAccent.copy(alpha = 0.08f)))
            .border(1.dp, GoldAccent.copy(alpha = if (selected) 0f else 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = if (selected) Color(0xFF1B1608) else textPrimary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PremiumSection(icon: String, title: String, isDarkMode: Boolean, content: @Composable ColumnScope.() -> Unit) {
    val sectionBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(sectionBg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("$icon  $title", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        content()
    }
}

// ───────────────────────────── Weather location (change here) ─────────────────────────────

private const val WEATHER_LAT = 32.3215
private const val WEATHER_LON = 34.8532

// ───────────────────────────── Model ─────────────────────────────

data class ReminderItem(
    val id: Int,
    val title: String,
    val time: String,
    val date: String = "",
    val isEnabled: Boolean = true,
    val isDaily: Boolean = false,
    val repeatType: String = "NONE",
    val soundUri: String? = null,
    val advanceMinutes: Int = 0,   // 0 = no early alert
    val ringSeconds: Int = 0,      // 0 = use the general setting
    val category: String = "",     // "" = none
    val colorIdx: Int = -1,        // -1 = the category's default color
    val notes: String = ""
)

data class ReminderExtras(val category: String, val colorIdx: Int, val notes: String)

data class DayForecast(val date: String, val max: Int, val min: Int, val code: Int)
data class WeatherState(val currentTemp: Int, val currentCode: Int, val daily: List<DayForecast>, val currentTime: String = "", val cityName: String = "", val timezoneId: String = "")
private fun tr(lang: String, he: String, en: String, ru: String = en, fr: String = en): String =
    when (lang) {
        "he" -> he
        "ru" -> ru
        "fr" -> fr
        else -> en
    }

private fun commonCancel(lang: String) = tr(lang, "ביטול", "Cancel", "Отмена", "Annuler")
private fun commonOk(lang: String) = tr(lang, "אישור", "OK", "ОК", "OK")
private fun commonClose(lang: String) = tr(lang, "סגור", "Close", "Закрыть", "Fermer")

private fun formatDate(date: Date): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)

private fun parseDateTime(date: String, time: String): Date? = try {
    if (date.isBlank() || time.isBlank()) null
    else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$date $time")
} catch (_: Exception) {
    null
}

fun ReminderItem.isPassed(): Boolean = isExpired()

private fun ReminderItem.isExpired(): Boolean {
    if (isDaily || repeatType != "NONE") return false
    val dateTime = parseDateTime(date, time) ?: return false
    return dateTime.before(Date())
}

/** One-time reminder whose time is before [cutoffMillis]. Repeating reminders never expire. */
private fun ReminderItem.isOlderThan(cutoffMillis: Long): Boolean {
    if (isDaily || repeatType != "NONE") return false
    val dateTime = parseDateTime(date, time) ?: return false
    return dateTime.time < cutoffMillis
}

private fun List<ReminderItem>.sortedByTime(): List<ReminderItem> =
    sortedWith(compareBy({ it.date.ifBlank { "9999-99-99" } }, { it.time }))

private fun createReminder(
    existing: List<ReminderItem>,
    title: String,
    time: String,
    date: String,
    isRepeating: Boolean,
    repeatType: String,
    soundUri: String?,
    advanceMinutes: Int,
    ringSeconds: Int,
    extras: ReminderExtras = ReminderExtras("", -1, "")
) = ReminderItem(
    id = (existing.maxOfOrNull { it.id } ?: 0) + 1,
    title = title,
    time = time,
    date = date,
    isDaily = isRepeating && repeatType == "DAILY",
    repeatType = if (isRepeating) repeatType else "NONE",
    soundUri = soundUri,
    advanceMinutes = advanceMinutes,
    ringSeconds = ringSeconds,
    category = extras.category,
    colorIdx = extras.colorIdx,
    notes = extras.notes
)

/** Applies the edited values to an existing reminder, keeping its id and enabled state. */
private fun editedReminder(
    old: ReminderItem,
    title: String,
    time: String,
    date: String,
    isRepeating: Boolean,
    repeatType: String,
    soundUri: String?,
    advanceMinutes: Int,
    ringSeconds: Int,
    extras: ReminderExtras = ReminderExtras(old.category, old.colorIdx, old.notes)
) = old.copy(
    title = title,
    time = time,
    date = date,
    isDaily = isRepeating && repeatType == "DAILY",
    repeatType = if (isRepeating) repeatType else "NONE",
    soundUri = soundUri,
    advanceMinutes = advanceMinutes,
    ringSeconds = ringSeconds,
    category = extras.category,
    colorIdx = extras.colorIdx,
    notes = extras.notes
)

// ───────────────────────────── Categories ─────────────────────────────

private data class ReminderCategory(
    val key: String,
    val icon: String,
    val he: String,
    val en: String,
    val ru: String,
    val fr: String,
    val defaultColor: Int,
    val keywords: List<String>
)

private val categoryColors = listOf(
    Color(0xFFE6C687), Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFF66BB6A),
    Color(0xFF26C6DA), Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFFEC407A)
)

private val reminderCategories = listOf(
    ReminderCategory("shopping", "🛒", "קניות", "Shopping", "Покупки", "Courses", 3,
        listOf("סופר", "קניות", "שוק", "מכולת", "חלב", "לקנות", "קניון", "shop", "grocer", "supermarket", "market", "buy", "mall", "магазин", "покупк", "купить", "супермаркет", "рынок", "course", "achat", "acheter", "supermarch", "marché")),
    ReminderCategory("health", "💊", "בריאות", "Health", "Здоровье", "Santé", 1,
        listOf("תרופ", "כדור", "רופא", "מרפאה", "קופת חולים", "בדיקת דם", "חיסון", "רפוא", "שיניים", "doctor", "pill", "medic", "pharmac", "clinic", "dentist", "vaccin", "врач", "лекарств", "таблетк", "аптек", "поликлиник", "médecin", "médicament", "pharmacie", "dentiste", "docteur")),
    ReminderCategory("work", "💼", "עבודה", "Work", "Работа", "Travail", 5,
        listOf("עבודה", "פגישה", "ישיבה", "דוח", "בוס", "לקוח", "מייל", "meeting", "work", "boss", "client", "report", "office", "работа", "встреча", "совещани", "клиент", "офис", "travail", "réunion", "bureau", "rendez-vous pro")),
    ReminderCategory("study", "📚", "לימודים", "Study", "Учёба", "Études", 6,
        listOf("לימוד", "מבחן", "שיעורי בית", "עבודה להגשה", "קורס", "הרצאה", "בחינה", "exam", "study", "homework", "course work", "lecture", "school", "учеб", "экзамен", "домашн", "лекци", "школ", "étude", "examen", "devoir", "cours", "école")),
    ReminderCategory("home", "🏠", "בית", "Home", "Дом", "Maison", 2,
        listOf("בית", "ניקיון", "כביסה", "לנקות", "כלים", "אשפה", "תיקון", "clean", "laundry", "trash", "dishes", "home", "house", "repair", "дом", "уборк", "стирк", "мусор", "ремонт", "maison", "ménage", "lessive", "poubelle", "vaisselle")),
    ReminderCategory("call", "📞", "שיחה", "Call", "Звонок", "Appel", 5,
        listOf("להתקשר", "טלפון", "שיחה", "חייג", "call", "phone", "ring ", "позвон", "звонок", "телефон", "appeler", "téléphone", "appel")),
    ReminderCategory("birthday", "🎂", "אירוע", "Event", "Событие", "Événement", 7,
        listOf("יום הולדת", "מתנה", "חתונה", "אירוע", "מסיבה", "חגיגה", "birthday", "gift", "wedding", "party", "present", "день рождения", "подарок", "свадьб", "вечеринк", "праздник", "anniversaire", "cadeau", "mariage", "fête")),
    ReminderCategory("sport", "🏋️", "ספורט", "Sport", "Спорт", "Sport", 3,
        listOf("אימון", "ספורט", "ריצה", "חדר כושר", "כושר", "יוגה", "שחייה", "כדורגל", "gym", "run", "workout", "yoga", "swim", "football", "soccer", "training", "тренировк", "спорт", "бег", "йога", "плаван", "футбол", "entraîn", "course à pied", "natation", "musculation")),
    ReminderCategory("car", "🚗", "רכב", "Car", "Авто", "Voiture", 4,
        listOf("רכב", "טסט", "דלק", "מוסך", "ביטוח רכב", "חניה", "רישיון", "car ", "fuel", "garage", "parking", "petrol", "mot ", "машин", "авто", "бензин", "парковк", "voiture", "carburant", "essence", "garage", "stationnement")),
    ReminderCategory("money", "💰", "כסף", "Money", "Деньги", "Argent", 0,
        listOf("חשבון", "תשלום", "שכירות", "בנק", "משכנתא", "ארנונה", "חשמל", "מים", "מס ", "bill", "pay", "rent", "bank", "tax", "loan", "mortgage", "оплат", "счёт", "счет", "аренд", "банк", "налог", "кредит", "facture", "payer", "loyer", "banque", "impôt")),
    ReminderCategory("food", "🍽️", "אוכל", "Food", "Еда", "Repas", 2,
        listOf("ארוחה", "אוכל", "מסעדה", "בישול", "ארוחת", "לבשל", "קפה", "dinner", "lunch", "breakfast", "restaurant", "cook", "meal", "coffee", "ужин", "обед", "завтрак", "ресторан", "готовить", "кофе", "dîner", "déjeuner", "petit-déjeuner", "restaurant", "cuisiner", "café")),
    ReminderCategory("travel", "✈️", "נסיעה", "Travel", "Поездка", "Voyage", 4,
        listOf("טיסה", "חופשה", "נסיעה", "מלון", "נמל תעופה", "דרכון", "רכבת", "flight", "trip", "vacation", "holiday", "hotel", "airport", "passport", "train", "travel", "рейс", "поездк", "отпуск", "отель", "аэропорт", "паспорт", "поезд", "vol ", "voyage", "vacances", "hôtel", "aéroport", "passeport"))
)

private fun categoryByKey(key: String): ReminderCategory? = reminderCategories.firstOrNull { it.key == key }

private fun categoryLabel(lang: String, c: ReminderCategory): String = tr(lang, c.he, c.en, c.ru, c.fr)

/** Guesses the category from the words of the title ("" when nothing matches). */
private fun detectCategory(title: String): String {
    val lower = title.lowercase(Locale.getDefault()).trim()
    if (lower.length < 2) return ""
    val words = lower.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
    for (category in reminderCategories) {
        for (rawKeyword in category.keywords) {
            val keyword = rawKeyword.lowercase(Locale.getDefault())
            val trimmed = keyword.trim()
            val hit = when {
                trimmed.contains(' ') -> lower.contains(trimmed)
                trimmed.first().code < 128 -> words.any { it.startsWith(trimmed) }
                else -> words.any { it.contains(trimmed) }
            }
            if (hit) return category.key
        }
    }
    return ""
}

private fun reminderAccent(r: ReminderItem): Color =
    categoryColors.getOrNull(r.colorIdx)
        ?: categoryByKey(r.category)?.let { categoryColors[it.defaultColor] }
        ?: GoldAccent

/** The category icon of the reminder ("" when it has none), for the alarm screen. */
fun reminderIcon(r: ReminderItem): String = categoryByKey(r.category)?.icon ?: ""

/** The reminder's chosen color as ARGB, for the alarm screen. */
fun reminderAccentArgb(r: ReminderItem): Int = reminderAccent(r).toArgb()

// ───────────────────────────── Countdown ─────────────────────────────

fun countdownText(lang: String, r: ReminderItem, now: Long): String? {
    if (!r.isEnabled || r.time.isBlank() || r.date.isBlank()) return null
    val repeat = if (r.repeatType != "NONE") r.repeatType else if (r.isDaily) "DAILY" else "NONE"
    val next = NotificationHelper.nextTriggerMillis(r.date, r.time, repeat, now) ?: return null
    val totalMinutes = ((next - now) / 60_000L).coerceAtLeast(0)
    val days = totalMinutes / 1440
    val hours = (totalMinutes % 1440) / 60
    val minutes = totalMinutes % 60
    val dUnit = tr(lang, "י'", "d", "д", "j")
    val hUnit = tr(lang, "שע'", "h", "ч", "h")
    val mUnit = tr(lang, "דק'", "min", "мин", "min")
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("$days $dUnit")
    if (hours > 0) parts.add("$hours $hUnit")
    if (minutes > 0 && days == 0L) parts.add("$minutes $mUnit")
    val prefix = tr(lang, "בעוד", "in", "через", "dans")
    return if (parts.isEmpty()) {
        tr(lang, "בעוד פחות מדקה", "in less than a minute", "меньше чем через минуту", "dans moins d'une minute")
    } else {
        prefix + " " + parts.take(2).joinToString(" ")
    }
}

// ───────────────────────────── Voice input ─────────────────────────────

/** Returns a function that opens the phone's speech recognizer and passes the spoken text to [onResult]. */
@Composable
private fun rememberVoiceInput(lang: String, onResult: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) latest.value(text)
        }
    }
    val prompt = tr(lang, "אמור את התזכורת…", "Say the reminder…", "Скажите напоминание…", "Dites le rappel…")
    val unavailable = tr(lang, "הקלדה בקול לא זמינה בטלפון הזה", "Voice input isn't available on this phone", "Голосовой ввод недоступен", "La saisie vocale n'est pas disponible")
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                when (lang) { "he" -> "he-IL"; "ru" -> "ru-RU"; "fr" -> "fr-FR"; else -> "en-US" }
            )
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            launcher.launch(intent)
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, unavailable, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

private fun repeatUnitLabel(lang: String, type: String): String = when (type) {
    "DAILY" -> tr(lang, "יום", "Day", "День", "Jour")
    "WEEKLY" -> tr(lang, "שבוע", "Week", "Неделя", "Semaine")
    "MONTHLY" -> tr(lang, "חודש", "Month", "Месяц", "Mois")
    "YEARLY" -> tr(lang, "שנה", "Year", "Год", "An")
    else -> ""
}

private fun repeatBadge(lang: String, type: String): String? = when (type) {
    "DAILY" -> tr(lang, "כל יום", "Every day", "Каждый день", "Tous les jours")
    "WEEKLY" -> tr(lang, "כל שבוע", "Every week", "Каждую неделю", "Chaque semaine")
    "MONTHLY" -> tr(lang, "כל חודש", "Every month", "Каждый месяц", "Chaque mois")
    "YEARLY" -> tr(lang, "כל שנה", "Every year", "Каждый год", "Chaque année")
    else -> null
}

private val advanceOptions = listOf(0, 5, 15, 30, 60, 120, 1440)

private val ringOptions = listOf(15, 30, 60, 120, 300, 600, 900, 1800)

private fun ringLabel(lang: String, seconds: Int): String = when (seconds) {
    15 -> tr(lang, "15 שנ'", "15 sec", "15 сек", "15 s")
    30 -> tr(lang, "30 שנ'", "30 sec", "30 сек", "30 s")
    60 -> tr(lang, "דקה", "1 min", "1 мин", "1 min")
    120 -> tr(lang, "2 דק'", "2 min", "2 мин", "2 min")
    300 -> tr(lang, "5 דק'", "5 min", "5 мин", "5 min")
    600 -> tr(lang, "10 דק'", "10 min", "10 мин", "10 min")
    900 -> tr(lang, "15 דק'", "15 min", "15 мин", "15 min")
    1800 -> tr(lang, "30 דק'", "30 min", "30 мин", "30 min")
    else -> "$seconds s"
}

private fun advanceLabel(lang: String, minutes: Int): String = when (minutes) {
    0 -> tr(lang, "ללא", "None", "Нет", "Aucun")
    5 -> tr(lang, "5 דק'", "5 min", "5 мин", "5 min")
    15 -> tr(lang, "15 דק'", "15 min", "15 мин", "15 min")
    30 -> tr(lang, "30 דק'", "30 min", "30 мин", "30 min")
    60 -> tr(lang, "שעה", "1 hour", "1 час", "1 heure")
    120 -> tr(lang, "שעתיים", "2 hours", "2 часа", "2 heures")
    1440 -> tr(lang, "יום", "1 day", "1 день", "1 jour")
    else -> "$minutes"
}

private fun advanceBadge(lang: String, minutes: Int): String? =
    if (minutes <= 0) null
    else tr(
        lang,
        "התראה ${advanceLabel(lang, minutes)} לפני",
        "Alert ${advanceLabel(lang, minutes)} before",
        "Уведомление за ${advanceLabel(lang, minutes)}",
        "Alerte ${advanceLabel(lang, minutes)} avant"
    )

private fun uriDisplayName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
} catch (_: Exception) {
    null
}

@Suppress("DEPRECATION")
private fun Intent.pickedRingtoneUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    else
        getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

private fun utcDateFormatter() =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

private fun dateStringToUtcMillis(date: String): Long? = try {
    utcDateFormatter().parse(date)?.time
} catch (_: Exception) {
    null
}

private fun shortDayLabel(date: String, lang: String): String = try {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
    if (parsed == null) date else SimpleDateFormat("EEE d/M", Locale.forLanguageTag(lang)).format(parsed)
} catch (_: Exception) {
    date
}

private fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "❄️"
    in 80..82 -> "🌧️"
    85, 86 -> "❄️"
    in 95..99 -> "⛈️"
    else -> "🌡️"
}

// ───────────────────────────── Network ─────────────────────────────

private class IpLocation(
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String,
    val city: String,
    val country: String
)

private fun readUrlText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.connectTimeout = 6000
    connection.readTimeout = 6000
    return try {
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

/** Where the phone is, by its IP address. Tries several free services, so one outage doesn't break the weather. */
private fun lookupIpLocation(): IpLocation? {
    try {
        val j = JSONObject(readUrlText("https://ipwho.is/"))
        if (j.optBoolean("success", true) && j.has("latitude") && j.has("longitude")) {
            return IpLocation(
                j.getDouble("latitude"), j.getDouble("longitude"),
                j.optJSONObject("timezone")?.optString("id", "") ?: "",
                j.optString("city", ""), j.optString("country", "")
            )
        }
    } catch (_: Exception) {
    }
    try {
        val j = JSONObject(readUrlText("https://ipapi.co/json/"))
        if (j.has("latitude") && j.has("longitude") && !j.isNull("latitude")) {
            return IpLocation(
                j.getDouble("latitude"), j.getDouble("longitude"),
                j.optString("timezone", ""),
                j.optString("city", ""), j.optString("country_name", "")
            )
        }
    } catch (_: Exception) {
    }
    try {
        val j = JSONObject(readUrlText("https://ipinfo.io/json"))
        val loc = j.optString("loc", "").split(",")
        if (loc.size == 2) {
            return IpLocation(
                loc[0].trim().toDouble(), loc[1].trim().toDouble(),
                j.optString("timezone", ""),
                j.optString("city", ""), j.optString("country", "")
            )
        }
    } catch (_: Exception) {
    }
    return null
}

private suspend fun fetchWeather(currentLang: String): WeatherState? = withContext(Dispatchers.IO) {
    try {
        val where = lookupIpLocation() ?: return@withContext null
        val latitude = where.latitude
        val longitude = where.longitude
        val timezoneId = where.timezoneId.ifBlank { TimeZone.getDefault().id }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone(timezoneId)
        }
        val exactLocalTime = timeFormat.format(Date())

        val city = where.city
        val country = where.country

        val formattedLocation = when {
            city.isNotBlank() && country.isNotBlank() ->
                if (currentLang == "he") "$country, $city" else "$city, $country"
            city.isNotBlank() -> city
            country.isNotBlank() -> country
            else -> String.format(Locale.US, "%.2f, %.2f", latitude, longitude)
        }

        val url = "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=$timezoneId&forecast_days=7"

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }

        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxes = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")

        val days = (0 until dates.length()).map { i ->
            DayForecast(
                date = dates.getString(i),
                max = maxes.getDouble(i).roundToInt(),
                min = mins.getDouble(i).roundToInt(),
                code = codes.getInt(i)
            )
        }

        WeatherState(
            currentTemp = current.getDouble("temperature_2m").roundToInt(),
            currentCode = current.getInt("weather_code"),
            daily = days,
            currentTime = exactLocalTime,
            cityName = formattedLocation,
            timezoneId = timezoneId
        )
    } catch (_: Exception) {
        null
    }
}
data class SongResult(val title: String, val artist: String, val previewUrl: String)

/** Searches songs by name (iTunes catalog). Each result has a 30-second preview. Returns null when offline. */
private suspend fun searchSongs(query: String): List<SongResult>? = withContext(Dispatchers.IO) {
    try {
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=10&term=${Uri.encode(query)}"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val body = try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
        val results = JSONObject(body).getJSONArray("results")
        val list = mutableListOf<SongResult>()
        for (i in 0 until results.length()) {
            val item = results.getJSONObject(i)
            val preview = item.optString("previewUrl", "")
            if (preview.isNotBlank()) {
                list.add(SongResult(item.optString("trackName"), item.optString("artistName"), preview))
            }
        }
        list
    } catch (_: Exception) {
        null
    }
}

/** Saves the preview inside the app so the alarm can play it later without internet. Returns the file uri. */
private suspend fun downloadPreview(context: Context, url: String): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "songs").apply { mkdirs() }
        val file = File(dir, "song_${url.hashCode()}.m4a")
        if (!file.exists() || file.length() == 0L) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 15000
            try {
                connection.inputStream.use { input -> file.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
        }
        if (file.length() > 0L) Uri.fromFile(file).toString() else null
    } catch (_: Exception) {
        null
    }
}

// ───────────────────────────── Activity ─────────────────────────────

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestFullScreenPermission()

        val data: Uri? = intent?.data
        val isSharedInvite = data != null && data.scheme == "https" && data.host == "remindme.local"
        val senderName = data?.getQueryParameter("sender")
            ?: tr(LanguageManager.getSelectedLanguage(this), "משתמש", "User", "Пользователь", "Utilisateur")
        val permission = data?.getQueryParameter("permission") ?: "read"
        setContent {
            var isDarkMode by remember { mutableStateOf(ReminderStorage.isDarkMode(this@MainActivity)) }
            var showShareDialog by remember { mutableStateOf(isSharedInvite) }
            var isFirstLaunch by remember { mutableStateOf(LanguageManager.isFirstLaunch(this@MainActivity)) }
            var currentLang by remember { mutableStateOf(LanguageManager.getSelectedLanguage(this@MainActivity)) }
            var showCalendarScreen by remember { mutableStateOf(false) }
            var isSignedIn by remember { mutableStateOf(CloudSync.isSignedIn()) }
            // Already signed in from before: refresh from the account first (a few seconds at most)
            var isSyncReady by remember { mutableStateOf(!CloudSync.isSignedIn()) }
            val activityScope = rememberCoroutineScope()

            LaunchedEffect(isSignedIn) {
                if (isSignedIn && !isSyncReady) {
                    withTimeoutOrNull(5000) { CloudSync.pull(this@MainActivity) }
                    isSyncReady = true
                }
            }

            RemindMeTheme(darkTheme = isDarkMode) {
                val direction = if (currentLang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr

                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    if (isFirstLaunch) {
                        WelcomeLanguageScreen(
                            isDarkMode = isDarkMode,
                            onLanguageSelected = { selectedCode ->
                                LanguageManager.saveLanguage(this@MainActivity, selectedCode)
                                LanguageManager.setFirstLaunchCompleted(this@MainActivity)
                                currentLang = selectedCode
                                isFirstLaunch = false
                            }
                        )
                    } else if (!isSignedIn) {
                        LoginScreen(
                            onSignedIn = {
                                isSyncReady = true
                                isSignedIn = true
                            }
                        )
                    } else if (!isSyncReady) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(DarkBackground),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    } else {
                        MainScreenContent(
                            onLogout = {
                                activityScope.launch {
                                    CloudSync.signOutAndClear(this@MainActivity)
                                    isSyncReady = false
                                    isSignedIn = false
                                }
                            },
                            isDarkMode = isDarkMode,
                            currentLang = currentLang,
                            onToggleDarkMode = {
                                val newValue = !isDarkMode
                                isDarkMode = newValue
                                ReminderStorage.saveDarkMode(this@MainActivity, newValue)
                            },
                            onOpenCalendar = {
                                // אם יש לך מסך קלנדר, ודא שהקריאה תואמת, או השאר את מה שהיה אצלך
                            },
                            onLanguageChanged = { newLang: String ->
                                LanguageManager.saveLanguage(this@MainActivity, newLang)
                                currentLang = newLang
                            }
                        )
                    }

                    if (showShareDialog) {
                        val permissionText = if (permission == "edit") {
                            tr(currentLang, "קריאה ועריכה", "Read & Edit", "Чтение и редактирование", "Lecture et modification")
                        } else {
                            tr(currentLang, "קריאה בלבד", "Read Only", "Только чтение", "Lecture seule")
                        }

                        AlertDialog(
                            onDismissRequest = { showShareDialog = false },
                            containerColor = SurfaceCardDark,
                            title = {
                                Text(
                                    text = tr(currentLang, "📅 בקשת שיתוף יומן", "📅 Calendar Share Request", "📅 Запрос календаря", "📅 Demande de partage"),
                                    color = TextPrimaryDark,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = tr(currentLang, "$senderName מבקש לשתף איתך את היומן שלו.", "$senderName wants to share their calendar with you.", "$senderName хочет поделиться с вами своим календарём.", "$senderName souhaite partager son calendrier avec vous."),
                                        color = TextPrimaryDark,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = tr(currentLang, "סוג הרשאה מבוקש: $permissionText", "Requested permission: $permissionText", "Запрошенное разрешение: $permissionText", "Autorisation demandée : $permissionText"),
                                        color = GoldAccent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { showShareDialog = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
                                ) {
                                    Text(tr(currentLang, "אישור", "Accept", "Принять", "Accepter"), fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showShareDialog = false }) {
                                    Text(tr(currentLang, "דחייה", "Decline", "Отклонить", "Refuser"), color = TextSecondaryDark)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun checkAndRequestFullScreenPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:$packageName")
                )
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                }
            }
        }
    }
}

// ───────────────────────────── Shared dialogs ─────────────────────────────

@Composable
fun SimpleConfirmDialog(
    isDarkMode: Boolean,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp) },
        text = { Text(message, color = textSecondary, fontSize = 15.sp) },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (destructive) Modifier.background(MaterialTheme.colorScheme.error)
                        else Modifier.background(GoldBrush)
                    )
                    .clickable { onConfirm() }
                    .padding(horizontal = 22.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    confirmText,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (destructive) Color.White else Color(0xFF1B1608)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = textSecondary)
            }
        }
    )
}

// ───────────────────────────── Screens ─────────────────────────────

@Composable
fun WelcomeLanguageScreen(
    isDarkMode: Boolean,
    onLanguageSelected: (String) -> Unit
) {
    val (backgroundColor, surfaceColor, textPrimary, _) = appColors(isDarkMode)

    val languages = listOf(
        "he" to "🇮🇱 Hebrew (עברית)",
        "en" to "🇬🇧 English",
        "ru" to "🇷🇺 Russian (Русский)",
        "fr" to "🇫🇷 French (Français)"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(isDarkMode)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.12f))
                    .border(2.dp, GoldAccent.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("🔔", fontSize = 46.sp) }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "RemindMe",
                style = androidx.compose.ui.text.TextStyle(
                    brush = GoldBrush,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose your preferred language",
                color = textPrimary,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(languages) { (code, label) ->
                    Button(
                        onClick = { onLanguageSelected(code) },
                        colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = textPrimary),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(text = label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreenContent(
    isDarkMode: Boolean,
    currentLang: String,
    onToggleDarkMode: () -> Unit,
    onOpenCalendar: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val (backgroundColor, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    var showCalendarScreen by remember { mutableStateOf(false) }

    var reminders by remember {
        mutableStateOf(ReminderStorage.loadReminders(context))
    }

    LaunchedEffect(reminders) {
        ReminderStorage.saveReminders(context, reminders)
    }

    // One-time reminders whose time has passed move to the history (repeating ones stay).
    // The calendar screen keeps its own list, so nothing is archived while it is open.
    LaunchedEffect(showCalendarScreen) {
        if (showCalendarScreen) return@LaunchedEffect
        reminders = ReminderStorage.loadReminders(context)
        while (true) {
            val cutoff = System.currentTimeMillis() - 60_000L
            val expired = reminders.filter { it.isOlderThan(cutoff) }
            if (expired.isNotEmpty()) {
                ReminderStorage.addToHistory(context, expired, "PASSED")
                reminders = reminders.filterNot { r -> expired.any { it.id == r.id } }
            }
            delay(10_000L)
        }
    }

    // שומרים את מזג האוויר ואת השעה החיה שמתעדכנת ברקע
    var weather by remember { mutableStateOf<WeatherState?>(null) }
    var liveLocalTime by remember { mutableStateOf("") }

// טעינת מזג האוויר והשעה הראשונית לפי השפה הנבחרת
    LaunchedEffect(key1 = currentLang) {
        while (true) {
            val fetchedWeather = fetchWeather(currentLang)
            if (fetchedWeather != null) {
                weather = fetchedWeather
            }
            // Refresh regularly, so the location follows the phone when you travel
            delay(if (fetchedWeather == null) 30_000L else 15 * 60_000L)
        }
    }

    // The clock always follows the phone's own time and ticks exactly on each new minute
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            val nowMs = System.currentTimeMillis()
            timeFormat.timeZone = TimeZone.getDefault()
            liveLocalTime = timeFormat.format(Date(nowMs))
            delay(60_000L - (nowMs % 60_000L) + 100L)
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showQuickDialog by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSupportScreen by remember { mutableStateOf(false) }
    var showAdminScreen by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isAdmin = com.example.remindme.utils.SupportRepo.isAdmin() }
    var restoreFrom by remember { mutableStateOf<HistoryEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var reminderToEdit by remember { mutableStateOf<ReminderItem?>(null) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWeatherWeeklyDialog by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<ReminderItem?>(null) }

    val myRemindersText = remember(currentLang) { LanguageManager.getString(context, "my_reminders") }
    val sortedReminders = remember(reminders, searchQuery, currentLang) {
        val q = searchQuery.trim()
        reminders.sortedByTime().filter { r ->
            q.isEmpty() ||
                    r.title.contains(q, ignoreCase = true) ||
                    r.notes.contains(q, ignoreCase = true) ||
                    (categoryByKey(r.category)?.let { categoryLabel(currentLang, it).contains(q, ignoreCase = true) } == true)
        }
    }

    if (showMenu) {
        val menuColors = appColors(isDarkMode)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showMenu = false },
            containerColor = menuColors.surface,
            shape = RoundedCornerShape(28.dp),
            title = { Text("☰ " + tr(currentLang, "תפריט", "Menu", "Меню", "Menu"), color = GoldAccent, fontWeight = FontWeight.ExtraBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val items = buildList {
                        add(Triple("🕘", tr(currentLang, "היסטוריה", "History", "История", "Historique")) { showHistoryScreen = true })
                        add(Triple("💬", tr(currentLang, "תמיכה", "Support", "Поддержка", "Assistance")) { showSupportScreen = true })
                        if (isAdmin) add(Triple("📥", tr(currentLang, "פניות תמיכה (מנהל)", "Support inbox (admin)", "Обращения (админ)", "Demandes (admin)")) { showAdminScreen = true })
                    }
                    items.forEach { (icon, label, action) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(GoldAccent.copy(alpha = 0.10f))
                                .border(1.dp, GoldAccent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                                .clickable { showMenu = false; action() }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(icon, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(label, color = menuColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text(commonCancel(currentLang), color = menuColors.textSecondary) } }
        )
    }

    if (showSupportScreen) {
        SupportScreen(isDarkMode = isDarkMode, currentLang = currentLang, onBack = { showSupportScreen = false })
    } else if (showAdminScreen) {
        SupportAdminScreen(isDarkMode = isDarkMode, currentLang = currentLang, onBack = { showAdminScreen = false })
    } else if (showHistoryScreen) {
        HistoryScreen(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            onBack = { showHistoryScreen = false },
            onRestore = { entry -> restoreFrom = entry }
        )
    } else if (showCalendarScreen) {
        RemindMeCalendarScreen(
            currentLang = currentLang,
            onNavigateBack = { showCalendarScreen = false },
            isDarkMode = isDarkMode
        )
    } else {
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier.premiumBackground(isDarkMode),
            floatingActionButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SmallFloatingActionButton(
                        onClick = { showQuickDialog = true },
                        containerColor = surfaceColor,
                        contentColor = GoldAccent,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("⚡", fontSize = 20.sp)
                    }
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF1B1608),
                        shape = RoundedCornerShape(22.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                        modifier = Modifier
                            .shadow(14.dp, RoundedCornerShape(22.dp), ambientColor = GoldAccent, spotColor = GoldAccent)
                            .background(GoldBrush, RoundedCornerShape(22.dp))
                    ) {
                        Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // ── חלק עליון: כותרת RemindMe בצהוב ומתחתיה השעה החיה שמתעדכנת לבד ──
                Column(
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "RemindMe",
                        style = androidx.compose.ui.text.TextStyle(
                            brush = GoldBrush,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.4.sp
                        )
                    )

                    if (liveLocalTime.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GoldAccent.copy(alpha = 0.12f))
                                .border(1.dp, GoldAccent.copy(alpha = 0.3f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🕐  \u2066$liveLocalTime\u2069",
                                color = GoldAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // שורת כפתורי העזר (מצב לילה, קלנדר, הגדרות)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceColor)
                            .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Text("☰", fontSize = 22.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = { onToggleDarkMode() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceColor)
                            .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp)
                    }

                    IconButton(
                        onClick = { showCalendarScreen = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceColor)
                            .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Text("📅", fontSize = 20.sp)
                    }

                    IconButton(
                        onClick = { showSettingsDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceColor)
                            .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        Text("⚙️", fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ווידג'ט מזג האוויר
                Box(modifier = Modifier.clickable { showWeatherWeeklyDialog = true }) {
                    WeatherAndDateWidget(isDarkMode = isDarkMode, currentLang = currentLang, weather = weather)
                }

                Spacer(modifier = Modifier.height(26.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(GoldBrush)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = myRemindersText,
                        color = textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (reminders.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GoldAccent.copy(alpha = 0.16f))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("${reminders.size}", color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (reminders.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(tr(currentLang, "חיפוש תזכורת…", "Search reminders…", "Поиск напоминаний…", "Rechercher un rappel…"), color = textSecondary) },
                        leadingIcon = { Text("🔍", fontSize = 16.sp) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Text("✕", color = textSecondary, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp).clickable { searchQuery = "" })
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = GoldAccent.copy(alpha = 0.35f),
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = GoldAccent,
                            focusedContainerColor = surfaceColor,
                            unfocusedContainerColor = surfaceColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // רשימת התזכורות
                Box(modifier = Modifier.weight(1f)) {
                    if (sortedReminders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent.copy(alpha = 0.10f))
                                        .border(1.5.dp, GoldAccent.copy(alpha = 0.35f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(if (reminders.isNotEmpty() && searchQuery.isNotBlank()) "🔍" else "🔔", fontSize = 38.sp)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (reminders.isNotEmpty() && searchQuery.isNotBlank())
                                        tr(currentLang, "לא נמצאו תזכורות", "No reminders found", "Напоминания не найдены", "Aucun rappel trouvé")
                                    else LanguageManager.getString(context, "no_reminders"),
                                    color = textSecondary,
                                    textAlign = TextAlign.Center,
                                    fontSize = 16.sp
                                )
                                if (reminders.isEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = tr(currentLang, "לחץ על + כדי להוסיף", "Tap + to add one", "Нажмите +, чтобы добавить", "Appuyez sur + pour en ajouter"),
                                        color = GoldAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(sortedReminders, key = { it.id }) { reminder ->
                                LuxuryReminderRow(
                                    reminder = reminder,
                                    isDarkMode = isDarkMode,
                                    currentLang = currentLang,
                                    onStatusChange = { updated ->
                                        reminders = reminders.map { if (it.id == updated.id) updated else it }
                                    },
                                    onDeleteClick = { reminderToDelete = reminder },
                                    onEditClick = { reminderToEdit = reminder }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // דיאלוגים
    if (showAddDialog) {
        AddReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            initialDate = formatDate(Date()),
            onDismiss = { showAddDialog = false },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras ->
                reminders = reminders + createReminder(reminders, title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras)
                showAddDialog = false
            }
        )
    }

    if (showQuickDialog) {
        QuickReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            onDismiss = { showQuickDialog = false },
            onCreate = { title, minutes ->
                val whenMillis = System.currentTimeMillis() + minutes * 60_000L
                val moment = Date(whenMillis)
                reminders = reminders + createReminder(
                    reminders, title,
                    SimpleDateFormat("HH:mm", Locale.US).format(moment),
                    formatDate(moment),
                    false, "NONE", null, 0, 30,
                    ReminderExtras(detectCategory(title), -1, "")
                )
                showQuickDialog = false
            }
        )
    }

    restoreFrom?.let { entry ->
        val old = entry.reminder
        val isOneTime = old.repeatType == "NONE" && !old.isDaily
        AddReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            initialDate = formatDate(Date()),
            initial = if (isOneTime) old.copy(date = formatDate(Date())) else old,
            onDismiss = { restoreFrom = null },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras ->
                reminders = reminders + createReminder(reminders, title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras)
                ReminderStorage.removeFromHistory(context, entry.at, old.id)
                restoreFrom = null
                showHistoryScreen = false
            }
        )
    }

    reminderToEdit?.let { editing ->
        AddReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            initialDate = editing.date,
            initial = editing,
            onDismiss = { reminderToEdit = null },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras ->
                reminders = reminders.map {
                    if (it.id == editing.id) editedReminder(it, title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras) else it
                }
                reminderToEdit = null
            }
        )
    }

    if (showWeatherWeeklyDialog) {
        WeeklyWeatherDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            onDismiss = { showWeatherWeeklyDialog = false },
            weather = weather
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            onDismiss = { showSettingsDialog = false },
            onLanguageChanged = onLanguageChanged,
            onLogout = {
                showSettingsDialog = false
                onLogout()
            }
        )
    }

    reminderToDelete?.let { reminder ->
        SimpleConfirmDialog(
            isDarkMode = isDarkMode,
            title = LanguageManager.getString(context, "delete_title"),
            message = String.format(LanguageManager.getString(context, "delete_msg"), reminder.title),
            confirmText = LanguageManager.getString(context, "delete_btn"),
            dismissText = LanguageManager.getString(context, "cancel"),
            destructive = true,
            onConfirm = {
                ReminderStorage.addToHistory(context, listOf(reminder), "DELETED")
                reminders = reminders.filter { it.id != reminder.id }
                reminderToDelete = null
            },
            onDismiss = { reminderToDelete = null }
        )
    }
}

// ───────────────────────────── RemindMeCalendarScreen (Premium #1) ─────────────────────────────

@Composable
fun RemindMeCalendarScreen(
    currentLang: String,
    onNavigateBack: () -> Unit,
    isDarkMode: Boolean = ReminderStorage.isDarkMode(LocalContext.current)
) {
    val context = LocalContext.current
    val (backgroundColor, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var reminders by remember { mutableStateOf(ReminderStorage.loadReminders(context)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<ReminderItem?>(null) }
    var reminderToDelete by remember { mutableStateOf<ReminderItem?>(null) }
    var showShareSetupDialog by remember { mutableStateOf(false) }
    var senderNameInput by remember { mutableStateOf("") }
    var selectedPermission by remember { mutableStateOf("read") }

    val selectedDateStr = formatDate(selectedDate.time)
    val dayReminders = remember(reminders, selectedDateStr) {
        reminders.filter { it.date == selectedDateStr }.sortedBy { it.time }
    }
    val isRtl = currentLang == "he"
    val goldGlowBorder = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.8f))

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.premiumBackground(true),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color.Transparent,
                contentColor = Color(0xFF1B1608),
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier
                    .size(60.dp)
                    .shadow(14.dp, CircleShape, ambientColor = GoldAccent, spotColor = GoldAccent)
                    .background(GoldBrush, CircleShape)
            ) {
                Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(20.dp),
                    border = goldGlowBorder,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCardDark),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text(
                        text = tr(currentLang, "חזרה ➡️", "⬅️ Back", "⬅️ Назад", "⬅️ Retour"),
                        color = GoldAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { showShareSetupDialog = true },
                    shape = RoundedCornerShape(20.dp),
                    border = goldGlowBorder,
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceCardDark),
                    modifier = Modifier.weight(1f).height(46.dp)
                ) {
                    Text(
                        text = tr(currentLang, "שיתוף לוח 🔗", "Share Calendar 🔗", "Поделиться 🔗", "Partager 🔗"),
                        color = GoldAccent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "📅 ${tr(currentLang, "לוח שנה", "Calendar", "Календарь", "Calendrier")}",
                color = TextPrimaryDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
            )

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(androidx.compose.ui.graphics.lerp(SurfaceCardDark, GoldAccent, 0.14f), SurfaceCardDark, SurfaceCardDark)
                        ),
                        RoundedCornerShape(26.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        }) {
                            Text("<", color = GoldAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }

                        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag(currentLang))
                        Text(
                            text = monthFormat.format(selectedDate.time),
                            color = TextPrimaryDark,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(onClick = {
                            selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                        }) {
                            Text(">", color = GoldAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val year = selectedDate.get(Calendar.YEAR)
                    val month = selectedDate.get(Calendar.MONTH)
                    val firstOfMonth = (selectedDate.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                    val firstDayOffset = firstOfMonth.get(Calendar.DAY_OF_WEEK) - 1
                    val maxDays = firstOfMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val rows = (firstDayOffset + maxDays + 6) / 7

                    val weekDays = when (currentLang) {
                        "he" -> listOf("א׳", "ב׳", "ג׳", "ד׳", "ה׳", "ו׳", "ש׳")
                        "ru" -> listOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
                        "fr" -> listOf("D", "L", "M", "M", "J", "V", "S")
                        else -> listOf("S", "M", "T", "W", "T", "F", "S")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        weekDays.forEach { label ->
                            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                                Text(label, color = GoldAccent.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    val todayCal = Calendar.getInstance()
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (row in 0 until rows) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                for (col in 0 until 7) {
                                    val dayIndex = row * 7 + col
                                    val dayNumber = dayIndex - firstDayOffset + 1

                                    if (dayIndex >= firstDayOffset && dayNumber <= maxDays) {
                                        val isSelected = selectedDate.get(Calendar.DAY_OF_MONTH) == dayNumber
                                        val isToday = todayCal.get(Calendar.YEAR) == year &&
                                                todayCal.get(Calendar.MONTH) == month &&
                                                todayCal.get(Calendar.DAY_OF_MONTH) == dayNumber
                                        val dayKey = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayNumber)
                                        val hasReminders = reminders.any { it.date == dayKey }

                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .then(if (isSelected) Modifier.background(GoldBrush) else Modifier)
                                                .border(
                                                    width = if (isToday && !isSelected) 2.dp else 1.dp,
                                                    color = if (isSelected) Color.Transparent
                                                    else if (isToday) GoldAccent
                                                    else GoldAccent.copy(alpha = 0.22f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedDate = (selectedDate.clone() as Calendar).apply {
                                                        set(Calendar.DAY_OF_MONTH, dayNumber)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    color = if (isSelected) Color(0xFF1B1608) else TextPrimaryDark,
                                                    fontWeight = if (isSelected || isToday) FontWeight.ExtraBold else FontWeight.Normal,
                                                    fontSize = 15.sp
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .padding(top = 2.dp)
                                                        .size(5.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            if (hasReminders) (if (isSelected) Color(0xFF1B1608) else GoldAccent)
                                                            else Color.Transparent
                                                        )
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(42.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = tr(currentLang, "תזכורות לתאריך $selectedDateStr", "Reminders for $selectedDateStr", "Напоминания на $selectedDateStr", "Rappels pour le $selectedDateStr"),
                color = GoldAccent,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = if (isRtl) TextAlign.Right else TextAlign.Left
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (dayReminders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = tr(currentLang, "אין תזכורות לתאריך זה", "No reminders for this date", "Нет напоминаний на эту дату", "Aucun rappel pour cette date"),
                            color = TextSecondaryDark,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(dayReminders, key = { it.id }) { reminder ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                                border = goldGlowBorder,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        IconButton(onClick = { reminderToEdit = reminder }) {
                                            Text("✏️", fontSize = 18.sp)
                                        }
                                        IconButton(onClick = { reminderToDelete = reminder }) {
                                            Text("🗑️", fontSize = 18.sp)
                                        }
                                        Switch(
                                            checked = reminder.isEnabled,
                                            onCheckedChange = { isChecked ->
                                                reminders = reminders.map { if (it.id == reminder.id) reminder.copy(isEnabled = isChecked) else it }
                                                ReminderStorage.saveReminders(context, reminders)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = DarkBackground,
                                                checkedTrackColor = GoldAccent
                                            )
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = reminder.title,
                                            color = TextPrimaryDark,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${reminder.time} ⏰",
                                            color = TextSecondaryDark,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showShareSetupDialog) {
        AlertDialog(
            onDismissRequest = { showShareSetupDialog = false },
            containerColor = SurfaceCardDark,
            title = { Text(tr(currentLang, "שיתוף יומן עם חבר", "Share calendar with a friend", "Поделиться календарём с другом", "Partager le calendrier avec un ami"), color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = senderNameInput,
                        onValueChange = { senderNameInput = it },
                        label = { Text(tr(currentLang, "השם שלך", "Your name", "Ваше имя", "Votre nom"), color = TextSecondaryDark) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = TextSecondaryDark,
                            focusedTextColor = TextPrimaryDark,
                            unfocusedTextColor = TextPrimaryDark
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr(currentLang, "התר הרשאת עריכה?", "Allow editing?", "Разрешить редактирование?", "Autoriser la modification ?"), color = TextPrimaryDark, fontSize = 14.sp)
                        Switch(
                            checked = selectedPermission == "edit",
                            onCheckedChange = { selectedPermission = if (it) "edit" else "read" },
                            colors = SwitchDefaults.colors(checkedThumbColor = DarkBackground, checkedTrackColor = GoldAccent)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (senderNameInput.isNotBlank()) {
                            showShareSetupDialog = false
                            ShareUtils.shareCalendarViaWhatsApp(context, senderNameInput.trim(), selectedPermission)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
                ) {
                    Text(tr(currentLang, "שלח קישור בוואטסאפ", "Send link via WhatsApp", "Отправить ссылку в WhatsApp", "Envoyer le lien par WhatsApp"), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareSetupDialog = false }) {
                    Text(commonCancel(currentLang), color = TextSecondaryDark)
                }
            }
        )
    }

    if (showAddDialog) {
        AddReminderDialog(
            isDarkMode = true,
            currentLang = currentLang,
            initialDate = selectedDateStr,
            onDismiss = { showAddDialog = false },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras ->
                reminders = reminders + createReminder(reminders, title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras)
                ReminderStorage.saveReminders(context, reminders)
                showAddDialog = false
            }
        )
    }

    reminderToEdit?.let { editing ->
        AddReminderDialog(
            isDarkMode = true,
            currentLang = currentLang,
            initialDate = editing.date,
            initial = editing,
            onDismiss = { reminderToEdit = null },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras ->
                reminders = reminders.map {
                    if (it.id == editing.id) editedReminder(it, title, time, date, isRepeating, repeatType, soundUri, advanceMinutes, ringSeconds, extras) else it
                }
                ReminderStorage.saveReminders(context, reminders)
                reminderToEdit = null
            }
        )
    }

    reminderToDelete?.let { reminder ->
        SimpleConfirmDialog(
            isDarkMode = true,
            title = LanguageManager.getString(context, "delete_title"),
            message = String.format(LanguageManager.getString(context, "delete_msg"), reminder.title),
            confirmText = LanguageManager.getString(context, "delete_btn"),
            dismissText = LanguageManager.getString(context, "cancel"),
            destructive = true,
            onConfirm = {
                ReminderStorage.addToHistory(context, listOf(reminder), "DELETED")
                reminders = reminders.filter { it.id != reminder.id }
                ReminderStorage.saveReminders(context, reminders)
                reminderToDelete = null
            },
            onDismiss = { reminderToDelete = null }
        )
    }
}

// ───────────────────────────── History ─────────────────────────────

@Composable
private fun HistoryScreen(
    isDarkMode: Boolean,
    currentLang: String,
    onBack: () -> Unit,
    onRestore: (HistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val (backgroundColor, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    var entries by remember { mutableStateOf(ReminderStorage.loadHistory(context)) }
    var filter by remember { mutableStateOf("ALL") }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    val shown = entries.filter { filter == "ALL" || it.reason == filter }
    val formatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(isDarkMode)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = GoldAccent),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("➡️ ${LanguageManager.getString(context, "back")}", fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (entries.isNotEmpty()) {
                Button(
                    onClick = { confirmClear = true },
                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🗑️ " + tr(currentLang, "נקה הכל", "Clear all", "Очистить всё", "Tout effacer"), fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🕘 " + tr(currentLang, "היסטוריית תזכורות", "Reminder history", "История напоминаний", "Historique des rappels"),
            color = textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = tr(
                currentLang,
                "תזכורות שהזמן שלהן עבר, או שנמחקו",
                "Reminders whose time passed, or that were deleted",
                "Напоминания, время которых прошло, или удалённые",
                "Rappels dont l'heure est passée ou qui ont été supprimés"
            ),
            color = textSecondary,
            fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        ) {
            listOf(
                "ALL" to tr(currentLang, "הכל", "All", "Все", "Tout"),
                "PASSED" to "⌛ " + tr(currentLang, "עבר זמנן", "Passed", "Прошли", "Passés"),
                "DELETED" to "🗑️ " + tr(currentLang, "נמחקו", "Deleted", "Удалённые", "Supprimés")
            ).forEach { (key, label) ->
                val selected = filter == key
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) GoldAccent else GoldAccent.copy(alpha = 0.10f))
                        .border(1.dp, GoldAccent.copy(alpha = if (selected) 0f else 0.35f), RoundedCornerShape(50))
                        .clickable { filter = key }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF1B1608) else textPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (shown.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = tr(currentLang, "אין עדיין היסטוריה", "No history yet", "Истории пока нет", "Pas encore d'historique"),
                    color = textSecondary,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(shown, key = { "${it.at}-${it.reminder.id}" }) { entry ->
                    val r = entry.reminder
                    val accent = reminderAccent(r)
                    val category = categoryByKey(r.category)
                    val isDeleted = entry.reason == "DELETED"
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surfaceColor),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Text(category?.icon ?: "🔔", fontSize = 20.sp) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    r.title,
                                    color = textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "🕐 \u2066${r.time}\u2069   📅 \u2066${r.date}\u2069",
                                    color = textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                ReminderChip(
                                    if (isDeleted) "🗑️ " + tr(currentLang, "נמחקה", "Deleted", "Удалено", "Supprimé")
                                    else "⌛ " + tr(currentLang, "עבר הזמן", "Time passed", "Время прошло", "Heure passée"),
                                    textSecondary
                                )
                                Text(
                                    "\u2066${formatter.format(Date(entry.at))}\u2069",
                                    color = textSecondary.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(GoldAccent.copy(alpha = 0.16f))
                                        .clickable { onRestore(entry) },
                                    contentAlignment = Alignment.Center
                                ) { Text("↩️", fontSize = 16.sp) }
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                                        .clickable {
                                            ReminderStorage.removeFromHistory(context, entry.at, r.id)
                                            entries = entries.filterNot { it.at == entry.at && it.reminder.id == r.id }
                                        },
                                    contentAlignment = Alignment.Center
                                ) { Text("✕", fontSize = 16.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        SimpleConfirmDialog(
            isDarkMode = isDarkMode,
            title = tr(currentLang, "לנקות את ההיסטוריה?", "Clear the history?", "Очистить историю?", "Effacer l'historique ?"),
            message = tr(
                currentLang,
                "כל התזכורות ההיסטוריות יימחקו לצמיתות.",
                "All history entries will be permanently deleted.",
                "Вся история будет удалена навсегда.",
                "Tout l'historique sera supprimé définitivement."
            ),
            confirmText = tr(currentLang, "נקה", "Clear", "Очистить", "Effacer"),
            dismissText = commonCancel(currentLang),
            destructive = true,
            onConfirm = {
                ReminderStorage.clearHistory(context)
                entries = emptyList()
                confirmClear = false
            },
            onDismiss = { confirmClear = false }
        )
    }
}

// ───────────────────────────── Add reminder ─────────────────────────────

private fun quickLabel(lang: String, minutes: Int): String = when {
    minutes < 60 -> tr(lang, "$minutes דק'", "$minutes min", "$minutes мин", "$minutes min")
    minutes == 60 -> tr(lang, "שעה", "1 h", "1 ч", "1 h")
    else -> tr(lang, "${minutes / 60} שע'", "${minutes / 60} h", "${minutes / 60} ч", "${minutes / 60} h")
}

@Composable
private fun QuickReminderDialog(
    isDarkMode: Boolean,
    currentLang: String,
    onDismiss: () -> Unit,
    onCreate: (title: String, minutes: Int) -> Unit
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(10) }
    var attempted by remember { mutableStateOf(false) }
    val startVoice = rememberVoiceInput(currentLang) { spoken -> title = spoken }
    val options = listOf(5, 10, 30, 60, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                "⚡ " + tr(currentLang, "תזכורת מהירה", "Quick reminder", "Быстрое напоминание", "Rappel rapide"),
                color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(LanguageManager.getString(LocalContext.current, "reminder_title_label"), color = textSecondary) },
                    isError = attempted && title.isBlank(),
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.18f))
                                .clickable { startVoice() },
                            contentAlignment = Alignment.Center
                        ) { Text("🎤", fontSize = 16.sp) }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = GoldAccent.copy(alpha = 0.35f),
                        focusedLabelColor = GoldAccent,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        cursorColor = GoldAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    tr(currentLang, "להזכיר לי בעוד:", "Remind me in:", "Напомнить через:", "Me rappeler dans :"),
                    color = textSecondary, fontSize = 12.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    options.forEach { m ->
                        val selected = minutes == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) GoldAccent else GoldAccent.copy(alpha = 0.10f))
                                .border(1.dp, GoldAccent.copy(alpha = if (selected) 0f else 0.35f), RoundedCornerShape(50))
                                .clickable { minutes = m }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                quickLabel(currentLang, m),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF1B1608) else textPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attempted = true
                    if (title.isNotBlank()) onCreate(title.trim(), minutes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
            ) {
                Text(tr(currentLang, "צור", "Create", "Создать", "Créer"), fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(commonCancel(currentLang), color = textSecondary) }
        }
    )
}

@Composable
fun AddReminderDialog(
    isDarkMode: Boolean,
    currentLang: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onAdd: (title: String, time: String, date: String, isRepeating: Boolean, repeatType: String, soundUri: String?, advanceMinutes: Int, ringSeconds: Int, extras: ReminderExtras) -> Unit,
    initial: ReminderItem? = null
) {
    val context = LocalContext.current
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    val fieldBackground = if (isDarkMode) DarkBackground else LightBackground

    val defaultSoundTitle = tr(currentLang, "ברירת מחדל (התראה)", "Default (Alarm)", "По умолчанию (будильник)", "Par défaut (alarme)")
    val noSoundTitle = tr(currentLang, "ללא צליל", "No sound", "Без звука", "Silencieux")
    val selectedSoundFallback = tr(currentLang, "צליל שנבחר", "Selected sound", "Выбранный звук", "Son sélectionné")

    var title by remember { mutableStateOf(initial?.title ?: "") }
    var time by remember { mutableStateOf(initial?.time ?: "") }
    var date by remember { mutableStateOf(initial?.date?.ifBlank { initialDate } ?: initialDate) }
    var isRepeating by remember { mutableStateOf(initial?.let { it.repeatType != "NONE" || it.isDaily } ?: false) }
    var repeatType by remember {
        mutableStateOf(initial?.let { if (it.repeatType != "NONE") it.repeatType else "DAILY" } ?: "DAILY")
    }
    var selectedSoundUri by remember { mutableStateOf<String?>(initial?.soundUri) }
    var soundTitle by remember {
        mutableStateOf(if (initial?.soundUri != null) selectedSoundFallback else defaultSoundTitle)
    }
    var ringSeconds by remember { mutableStateOf(initial?.ringSeconds?.takeIf { it > 0 } ?: 30) }
    var attemptedSave by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var colorIdx by remember { mutableStateOf(initial?.colorIdx ?: -1) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var categoryTouched by remember { mutableStateOf(initial?.category?.isNotBlank() == true) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showMore by remember {
        mutableStateOf(
            initial != null && (initial.repeatType != "NONE" || initial.isDaily || initial.advanceMinutes > 0 ||
                    initial.notes.isNotBlank())
        )
    }
    val startVoice = rememberVoiceInput(currentLang) { spoken -> title = spoken }
    LaunchedEffect(title) {
        if (!categoryTouched) category = detectCategory(title)
    }

    var showTimePickerState by remember { mutableStateOf(false) }
    var showDatePickerState by remember { mutableStateOf(false) }
    var showSoundOptionsDialog by remember { mutableStateOf(false) }
    var showSongSearchDialog by remember { mutableStateOf(false) }

    val addTitleText = if (initial != null) {
        tr(currentLang, "עריכת תזכורת", "Edit Reminder", "Изменить напоминание", "Modifier le rappel")
    } else {
        LanguageManager.getString(context, "add_title")
    }
    val titleLabelText = LanguageManager.getString(context, "reminder_title_label")
    val timeLabelText = LanguageManager.getString(context, "reminder_time_label")
    val dateLabelText = LanguageManager.getString(context, "reminder_date_label")
    val soundLabelText = LanguageManager.getString(context, "sound_label")
    val saveScheduleText = LanguageManager.getString(context, "save_and_schedule")
    val cancelText = LanguageManager.getString(context, "cancel")
    val okText = LanguageManager.getString(context, "save")

    val repeatToggleText = tr(currentLang, "תזכורת מחזורית (Repeat)", "Recurring Reminder", "Повторяющееся напоминание", "Rappel récurrent")
    val repeatOptions = listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY")

    val titleError = attemptedSave && title.isBlank()
    val timeError = attemptedSave && time.isBlank()
    var advanceMinutes by remember { mutableStateOf(initial?.advanceMinutes ?: 0) }

    val isInPast = !isRepeating && parseDateTime(date, time)?.before(Date()) == true
    val isAdvanceInPast = !isRepeating && advanceMinutes > 0 &&
            (parseDateTime(date, time)?.let { it.time - advanceMinutes * 60_000L < System.currentTimeMillis() } == true)
    val noteText: String? = when {
        isInPast -> tr(currentLang, "הזמן כבר עבר – התזכורת תישמר ביומן ולא תצלצל", "This time has passed – the reminder will be saved but won't ring", "Это время уже прошло — напоминание сохранится, но не прозвонит", "Cette heure est passée – le rappel sera enregistré sans sonner")
        isAdvanceInPast -> tr(currentLang, "ההתראה המוקדמת כבר עברה – תצלצל רק בזמן עצמו", "The early alert time has passed – it will ring only at the time itself", "Раннее уведомление уже прошло — сигнал только в назначенное время", "L'alerte anticipée est passée – sonnerie uniquement à l'heure")
        else -> null
    }

    val songPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Keep access to the file so the alarm can play it later, even when the app is closed
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            selectedSoundUri = uri.toString()
            soundTitle = uriDisplayName(context, uri) ?: selectedSoundFallback
        }
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.pickedRingtoneUri()
            if (uri != null) {
                selectedSoundUri = uri.toString()
                soundTitle = RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: selectedSoundFallback
            } else {
                selectedSoundUri = null
                soundTitle = noSoundTitle
            }
        }
    }

    fun openRingtonePicker() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, tr(currentLang, "בחר צליל התראה", "Select notification sound", "Выберите звук уведомления", "Choisir le son de notification"))
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedSoundUri?.toUri())
        }
        ringtonePickerLauncher.launch(intent)
    }

    val sectionBg = if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.04f)
    val goldBrush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFFF3DDA5), GoldAccent, Color(0xFFC9A24F)))
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GoldAccent,
        unfocusedBorderColor = GoldAccent.copy(alpha = 0.35f),
        focusedLabelColor = GoldAccent,
        unfocusedLabelColor = textSecondary,
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        cursorColor = GoldAccent,
        focusedContainerColor = sectionBg,
        unfocusedContainerColor = sectionBg
    )

    @Composable
    fun PremiumChip(label: String, selected: Boolean, onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) goldBrush else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(sectionBg, sectionBg)))
                .border(1.dp, GoldAccent.copy(alpha = if (selected) 0f else 0.35f), RoundedCornerShape(50))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color(0xFF1B1608) else textPrimary)
        }
    }

    @Composable
    fun SectionTitle(icon: String, text: String) {
        Text("$icon  $text", color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }

    @Composable
    fun PickTile(icon: String, label: String, value: String, isError: Boolean, modifier: Modifier, onClick: () -> Unit) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(sectionBg)
                .border(1.dp, if (isError) MaterialTheme.colorScheme.error else GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text("$icon  $label", color = textSecondary, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (value.isBlank()) "––:––" else "\u2066$value\u2069",
                color = if (value.isBlank()) textSecondary else textPrimary,
                fontSize = 17.sp,
                maxLines = 1,
                softWrap = false,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(goldBrush),
                    contentAlignment = Alignment.Center
                ) { Text(if (initial != null) "✏️" else "⏰", fontSize = 20.sp) }
                Spacer(Modifier.width(12.dp))
                Text(addTitleText, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(titleLabelText, color = if (titleError) MaterialTheme.colorScheme.error else textSecondary) },
                    leadingIcon = { Text("📝", fontSize = 18.sp) },
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(GoldAccent.copy(alpha = 0.18f))
                                .clickable { startVoice() },
                            contentAlignment = Alignment.Center
                        ) { Text("🎤", fontSize = 16.sp) }
                    },
                    singleLine = true,
                    isError = titleError,
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PickTile("🕐", timeLabelText, time, timeError, Modifier.weight(1f)) { showTimePickerState = true }
                    PickTile("📅", dateLabelText, date, false, Modifier.weight(1f)) { showDatePickerState = true }
                }

                if (noteText != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GoldAccent.copy(alpha = 0.12f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ℹ️", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(noteText, color = textPrimary, fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(sectionBg)
                        .border(1.dp, GoldAccent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .clickable { showSoundOptionsDialog = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = soundLabelText, color = textSecondary, fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = soundTitle, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        modifier = Modifier.size(38.dp).clip(CircleShape).background(GoldAccent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) { Text("🎵", fontSize = 18.sp) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sectionBg).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val detected = categoryByKey(category)
                    val needsAsk = title.isNotBlank() && !categoryTouched && detected == null
                    val pickerOpen = showCategoryPicker || needsAsk
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { showCategoryPicker = !showCategoryPicker },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "🏷️  " + tr(currentLang, "קטגוריה", "Category", "Категория", "Catégorie"),
                            color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = (if (detected != null) "${detected.icon} ${categoryLabel(currentLang, detected)}"
                            else tr(currentLang, "בחר", "Choose", "Выбрать", "Choisir")) + (if (pickerOpen) "  ▴" else "  ▾"),
                            color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (pickerOpen) {
                        if (needsAsk) {
                            Text(
                                "🤔 " + tr(
                                    currentLang,
                                    "לא הצלחתי לזהות – בחר קטגוריה",
                                    "Couldn't tell – please pick a category",
                                    "Не удалось определить — выберите категорию",
                                    "Je n'ai pas su deviner – choisissez une catégorie"
                                ),
                                color = textSecondary, fontSize = 12.sp
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            reminderCategories.forEach { c ->
                                PremiumChip("${c.icon} ${categoryLabel(currentLang, c)}", category == c.key) {
                                    category = if (category == c.key) "" else c.key
                                    categoryTouched = true
                                }
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            categoryColors.forEachIndexed { index, color ->
                                val selected = colorIdx == index
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.Black.copy(alpha = 0.2f), CircleShape)
                                        .clickable { colorIdx = if (selected) -1 else index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) Text("✓", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showMore = !showMore }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "⚙️ " + tr(currentLang, "אפשרויות נוספות", "More options", "Дополнительно", "Plus d'options") + (if (showMore) "  ▴" else "  ▾"),
                        color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }

                if (showMore) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sectionBg).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🔁  $repeatToggleText", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isRepeating,
                                onCheckedChange = { isRepeating = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = if (isDarkMode) DarkBackground else SurfaceCardLight, checkedTrackColor = GoldAccent)
                            )
                        }
                        if (isRepeating) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            ) {
                                repeatOptions.forEach { typeCode ->
                                    PremiumChip(repeatUnitLabel(currentLang, typeCode), repeatType == typeCode) { repeatType = typeCode }
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(sectionBg).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SectionTitle("🔔", tr(currentLang, "התראה מוקדמת", "Early alert", "Раннее уведомление", "Alerte anticipée"))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        ) {
                            advanceOptions.forEach { minutes ->
                                PremiumChip(advanceLabel(currentLang, minutes), advanceMinutes == minutes) { advanceMinutes = minutes }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr(currentLang, "הערות (יופיעו בהתראה)", "Notes (shown in the alert)", "Заметки (в уведомлении)", "Notes (affichées dans l'alerte)"), color = textSecondary) },
                        leadingIcon = { Text("🗒️", fontSize = 18.sp) },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )

                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(goldBrush)
                    .clickable {
                        attemptedSave = true
                        if (title.isNotBlank() && time.isNotBlank()) {
                            onAdd(title.trim(), time, date, isRepeating, repeatType, selectedSoundUri, advanceMinutes, ringSeconds, ReminderExtras(category, colorIdx, notes.trim()))
                        }
                    }
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(if (initial != null) okText else saveScheduleText, color = Color(0xFF1B1608), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(cancelText, color = textSecondary)
            }
        }
    )

    if (showSoundOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showSoundOptionsDialog = false },
            containerColor = surfaceColor,
            title = {
                Text(tr(currentLang, "בחר סוג צליל", "Choose Sound Type", "Выберите тип звука", "Choisir le type de son"), color = textPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showSoundOptionsDialog = false
                            openRingtonePicker()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(currentLang, "🔔 צליל התראה מובנה", "🔔 System Ringtone", "🔔 Системный рингтон", "🔔 Sonnerie système"))
                    }

                    Button(
                        onClick = {
                            showSoundOptionsDialog = false
                            showSongSearchDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(currentLang, "🔎 חיפוש שיר לפי שם", "🔎 Search song by name", "🔎 Поиск песни по названию", "🔎 Rechercher une chanson"))
                    }

                    Button(
                        onClick = {
                            showSoundOptionsDialog = false
                            songPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(currentLang, "🎵 שיר מהטלפון", "🎵 Song from phone", "🎵 Песня с телефона", "🎵 Chanson du téléphone"))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSoundOptionsDialog = false }) {
                    Text(cancelText, color = textSecondary)
                }
            }
        )
    }

    if (showSongSearchDialog) {
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SongResult>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }
        var isDownloading by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        val networkError = tr(currentLang, "אין חיבור לאינטרנט או שהחיפוש נכשל", "No internet connection or the search failed", "Нет интернета или поиск не удался", "Pas de connexion ou la recherche a échoué")
        val downloadError = tr(currentLang, "לא הצלחנו לשמור את השיר, נסה שיר אחר", "Couldn't save the song, try another one", "Не удалось сохранить песню, попробуйте другую", "Impossible d'enregistrer la chanson, essayez-en une autre")
        val noResults = tr(currentLang, "לא נמצאו שירים", "No songs found", "Песни не найдены", "Aucune chanson trouvée")

        LaunchedEffect(searchQuery) {
            errorMessage = null
            if (searchQuery.trim().length >= 2) {
                delay(500)
                isSearching = true
                val found = searchSongs(searchQuery.trim())
                searchResults = found ?: emptyList()
                errorMessage = if (found == null) networkError else if (found.isEmpty()) noResults else null
                isSearching = false
            } else {
                searchResults = emptyList()
                isSearching = false
            }
        }

        AlertDialog(
            onDismissRequest = { if (!isDownloading) showSongSearchDialog = false },
            containerColor = surfaceColor,
            title = {
                Text(tr(currentLang, "חיפוש שיר", "Search Song", "Поиск песни", "Rechercher une chanson"), color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(tr(currentLang, "שם שיר או אמן", "Song or artist name", "Название песни или исполнитель", "Nom de la chanson ou de l'artiste"), color = textSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldAccent,
                            unfocusedBorderColor = textSecondary.copy(alpha = 0.5f),
                            focusedLabelColor = GoldAccent,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            cursorColor = GoldAccent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = tr(
                            currentLang,
                            "מהחיפוש אפשר לקבל רק קטע של 30 שניות (מגבלת זכויות יוצרים). כדי שיתנגן שיר שלם – בחר \"שיר מהטלפון\"."
                            ,"Search gives only a 30-second clip (copyright limit). To play a full song, choose \"Song from phone\"."
                            ,"Поиск даёт только 30-секундный фрагмент (авторские права). Для полной песни выберите «Песня с телефона»."
                            ,"La recherche donne seulement un extrait de 30 s (droits d'auteur). Pour une chanson entière, choisissez « Chanson du téléphone »."
                        ),
                        color = textSecondary,
                        fontSize = 12.sp
                    )

                    if (isSearching || isDownloading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    }

                    errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 220.dp)
                        ) {
                            items(searchResults) { song ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(fieldBackground)
                                        .clickable(enabled = !isDownloading) {
                                            scope.launch {
                                                isDownloading = true
                                                errorMessage = null
                                                val savedUri = downloadPreview(context, song.previewUrl)
                                                isDownloading = false
                                                if (savedUri != null) {
                                                    selectedSoundUri = savedUri
                                                    soundTitle = "${song.artist} – ${song.title}"
                                                    showSongSearchDialog = false
                                                } else {
                                                    errorMessage = downloadError
                                                }
                                            }
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(text = "🎵 ${song.title}", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Text(text = song.artist, color = textSecondary, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { if (!isDownloading) showSongSearchDialog = false }) {
                    Text(cancelText, color = textSecondary)
                }
            }
        )
    }

    if (showTimePickerState) {
        TimePickerDialogWrapper(
            isDarkMode = isDarkMode,
            onDismiss = { showTimePickerState = false },
            onTimeSelected = { hour, minute ->
                time = String.format(Locale.US, "%02d:%02d", hour, minute)
                showTimePickerState = false
            },
            currentLang = currentLang
        )
    }

    if (showDatePickerState) {
        DatePickerDialogWrapper(
            isDarkMode = isDarkMode,
            initialDate = date,
            onDismiss = { showDatePickerState = false },
            onDateSelected = { selectedDate: String ->
                date = selectedDate
                showDatePickerState = false
            },
            currentLang = currentLang
        )
    }
}

// ───────────────────────────── Permissions ─────────────────────────────

@Composable
fun PermissionCheckDialog(isDarkMode: Boolean, currentLang: String = "en") {
    val context = LocalContext.current
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showExactAlarmDialog by remember { mutableStateOf(false) }
    var showOverlayDialog by remember { mutableStateOf(false) }

    fun checkOverlay() {
        if (!Settings.canDrawOverlays(context)) showOverlayDialog = true
    }

    fun checkExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmDialog = true
                return
            }
        }
        checkOverlay()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) checkExactAlarm() else showNotificationDialog = true
    }

    LaunchedEffect(Unit) {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when {
            needsRuntimePermission -> notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            !notificationManager.areNotificationsEnabled() -> showNotificationDialog = true
            else -> checkExactAlarm()
        }
    }

    if (showNotificationDialog) {
        SimpleConfirmDialog(
            isDarkMode = isDarkMode,
            title = tr(currentLang, "הפעלת התראות", "Enable Notifications", "Включите уведомления", "Activer les notifications"),
            message = tr(currentLang, "RemindMe צריכה הרשאת התראות כדי להזכיר לך בזמן.", "RemindMe needs notification permission.", "RemindMe нужно разрешение.", "RemindMe a besoin de l'autorisation."),
            confirmText = tr(currentLang, "פתח הגדרות", "Open Settings", "Открыть настройки", "Ouvrir les paramètres"),
            dismissText = commonCancel(currentLang),
            onConfirm = {
                showNotificationDialog = false
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            },
            onDismiss = { showNotificationDialog = false }
        )
    }

    if (showExactAlarmDialog) {
        SimpleConfirmDialog(
            isDarkMode = isDarkMode,
            title = tr(currentLang, "תזכורות בזמן מדויק", "Exact Alarms", "Точные будильники", "Alarmes précises"),
            message = tr(currentLang, "כדי שהתזכורות יצלצלו בדיוק בשעה שקבעת, יש לאשר בהגדרות.", "Allow exact alarms.", "Разрешите точные будильники.", "Autorisez les alarmes exactes."),
            confirmText = tr(currentLang, "פתח הגדרות", "Open Settings", "Открыть настройки", "Ouvrir les paramètres"),
            dismissText = commonCancel(currentLang),
            onConfirm = {
                showExactAlarmDialog = false
                checkOverlay()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            },
            onDismiss = {
                showExactAlarmDialog = false
                checkOverlay()
            }
        )
    }

    if (showOverlayDialog) {
        SimpleConfirmDialog(
            isDarkMode = isDarkMode,
            title = tr(currentLang, "הצגת ההתראה מעל אפליקציות", "Show Over Other Apps", "Поверх приложений", "Afficher par-dessus"),
            message = tr(currentLang, "כדי שמסך ההתראה ייפתח כשאתה משתמש בטלפון, צריך לאשר הצגה מעל אפליקציות אחרות.", "Allow display over other apps.", "Разрешите показ поверх других приложений.", "Autorisez l'affichage."),
            confirmText = tr(currentLang, "פתח הגדרות", "Open Settings", "Открыть настройки", "Ouvrir les paramètres"),
            dismissText = commonCancel(currentLang),
            onConfirm = {
                showOverlayDialog = false
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                try { context.startActivity(intent) } catch (_: Exception) {}
            },
            onDismiss = { showOverlayDialog = false }
        )
    }
}

// ───────────────────────────── Weather ─────────────────────────────

@Composable
fun WeeklyWeatherDialog(
    isDarkMode: Boolean,
    currentLang: String,
    onDismiss: () -> Unit,
    weather: WeatherState? = null
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = {
            Text(
                text = tr(currentLang, "תחזית מזג אוויר שבועית", "Weekly Weather Forecast", "Прогноз погоды на неделю", "Prévisions météo de la semaine"),
                color = textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (weather == null) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = tr(currentLang, "לא ניתן לטעון תחזית כרגע.", "Couldn't load forecast.", "Не удалось загрузить прогноз.", "Impossible de charger les prévisions."),
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    weather.daily.forEach { day ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = shortDayLabel(day.date, currentLang),
                                color = textPrimary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(text = weatherEmoji(day.code), fontSize = 20.sp)
                            Text(
                                text = "${day.max}° / ${day.min}°",
                                color = textSecondary,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
            ) {
                Text(commonClose(currentLang), fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun WeatherAndDateWidget(isDarkMode: Boolean, currentLang: String, weather: WeatherState? = null) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    val locale = when (currentLang) {
        "he" -> Locale("iw", "IL")
        "ru" -> Locale("ru", "RU")
        "fr" -> Locale("fr", "FR")
        else -> Locale.US
    }
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", locale)
    val currentDateStr = dateFormat.format(Date())

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(androidx.compose.ui.graphics.lerp(surfaceColor, GoldAccent, 0.16f), surfaceColor)
                ),
                RoundedCornerShape(22.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = currentDateStr, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (weather != null) "📍 " + weather.cityName
                    else tr(currentLang, "מאתר מיקום…", "Locating…", "Определение места…", "Localisation…"),
                    color = textSecondary,
                    fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weather != null) {
                    Text(text = "${weather.currentTemp}°", color = textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(GoldAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) { Text(text = weatherEmoji(weather.currentCode), fontSize = 26.sp) }
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GoldAccent, strokeWidth = 2.dp)
                }
            }
        }
    }
}

// ───────────────────────────── Settings ─────────────────────────────

@Composable
fun SettingsDialog(
    isDarkMode: Boolean,
    currentLang: String,
    onDismiss: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    var showLogoutConfirm by remember { mutableStateOf(false) }
    var windowMode by remember { mutableStateOf(ReminderStorage.getAlarmWindowMode(context)) }

    val languages = listOf(
        "he" to "🇮🇱 עברית",
        "en" to "🇬🇧 English",
        "ru" to "🇷🇺 Русский",
        "fr" to "🇫🇷 Français"
    )

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = surfaceColor,
            title = { Text(LanguageManager.getString(context, "logout_title"), color = textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(LanguageManager.getString(context, "logout_msg"), color = textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutConfirm = false
                    onLogout()
                }) {
                    Text(LanguageManager.getString(context, "logout"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(commonCancel(currentLang), color = textSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        shape = RoundedCornerShape(28.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(GoldBrush),
                    contentAlignment = Alignment.Center
                ) { Text("⚙️", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = LanguageManager.getString(context, "settings_title"),
                    color = textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                PremiumSection("🌐", tr(currentLang, "שפת ממשק", "Interface language", "Язык интерфейса", "Langue"), isDarkMode) {
                    languages.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { (code, label) ->
                                PremiumChoiceChip(
                                    label = label,
                                    selected = currentLang == code,
                                    isDarkMode = isDarkMode,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    onLanguageChanged(code)
                                    onDismiss()
                                }
                            }
                        }
                    }
                }

                PremiumSection("🔔", LanguageManager.getString(context, "alert_display"), isDarkMode) {
                    listOf("FULL" to "alert_mode_full", "QUARTER" to "alert_mode_quarter").forEach { (mode, labelKey) ->
                        PremiumChoiceChip(
                            label = LanguageManager.getString(context, labelKey),
                            selected = windowMode == mode,
                            isDarkMode = isDarkMode,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            windowMode = mode
                            ReminderStorage.saveAlarmWindowMode(context, mode)
                        }
                    }
                }

                PremiumSection("👤", LanguageManager.getString(context, "account_label"), isDarkMode) {
                    CloudSync.email()?.let { email ->
                        Text(
                            text = String.format(LanguageManager.getString(context, "logged_in_as"), email),
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { showLogoutConfirm = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = LanguageManager.getString(context, "logout"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(commonClose(currentLang), color = GoldAccent)
            }
        }
    )
}

// ───────────────────────────── Luxury Reminder Row ─────────────────────────────

@Composable
private fun ReminderChip(text: String, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(GoldAccent.copy(alpha = 0.12f))
            .border(1.dp, GoldAccent.copy(alpha = 0.25f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LuxuryReminderRow(
    reminder: ReminderItem,
    isDarkMode: Boolean,
    currentLang: String,
    onStatusChange: (ReminderItem) -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit = {}
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    val repeatKey = if (reminder.repeatType != "NONE") reminder.repeatType else if (reminder.isDaily) "DAILY" else "NONE"
    val badge = repeatBadge(currentLang, repeatKey)
    val advance = advanceBadge(currentLang, reminder.advanceMinutes)
    val accent = reminderAccent(reminder)
    val category = categoryByKey(reminder.category)
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }
    val countdown = countdownText(currentLang, reminder, now)
    val hasChips = reminder.date.isNotBlank() || badge != null || advance != null || reminder.isPassed() ||
            countdown != null || category != null

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (!reminder.isEnabled) 0.55f else if (reminder.isPassed()) 0.75f else 1f)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(surfaceColor, accent, 0.18f),
                        surfaceColor,
                        surfaceColor
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .clickable { onEditClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (category != null) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Text(category.icon, fontSize = 20.sp) }
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reminder.title,
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (reminder.notes.isNotBlank()) {
                            Text(
                                text = reminder.notes,
                                color = textSecondary,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // The time is always shown left-to-right so the digits never get mixed up
                Text(
                    text = "\u2066${reminder.time}\u2069",
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp
                )
            }

            if (hasChips) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (countdown != null) {
                        ReminderChip("⏳ $countdown", accent)
                    }
                    if (category != null) {
                        ReminderChip("${category.icon} ${categoryLabel(currentLang, category)}", accent)
                    }
                    if (reminder.date.isNotBlank()) {
                        ReminderChip("📅 \u2066${reminder.date}\u2069", textSecondary)
                    }
                    if (badge != null) {
                        ReminderChip("🔁 $badge", textSecondary)
                    }
                    if (advance != null) {
                        ReminderChip("🔔 $advance", textSecondary)
                    }
                    if (reminder.isPassed()) {
                        ReminderChip("⌛ " + tr(currentLang, "עבר", "Passed", "Прошло", "Passé"), textSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = GoldAccent.copy(alpha = 0.15f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onStatusChange(reminder.copy(isEnabled = it)) },
                    modifier = Modifier.scale(0.85f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (isDarkMode) DarkBackground else SurfaceCardLight,
                        checkedTrackColor = GoldAccent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GoldAccent.copy(alpha = 0.14f))
                            .clickable { onEditClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✏️", fontSize = 16.sp)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                            .clickable { onDeleteClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🗑️", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Pickers ─────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogWrapper(
    isDarkMode: Boolean,
    onDismiss: () -> Unit,
    onTimeSelected: (Int, Int) -> Unit,
    currentLang: String = "en"
) {
    val currentTime = Calendar.getInstance()
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true
    )
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = { Text(tr(currentLang, "בחר שעה", "Select Time", "Выберите время", "Choisir l'heure"), color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        selectorColor = GoldAccent,
                        clockDialSelectedContentColor = DarkBackground,
                        clockDialUnselectedContentColor = textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onTimeSelected(timePickerState.hour, timePickerState.minute) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
            ) {
                Text(commonOk(currentLang), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(commonCancel(currentLang), color = textSecondary)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogWrapper(
    isDarkMode: Boolean,
    initialDate: String,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit,
    currentLang: String = "en"
) {
    val (_, _, _, textSecondary) = appColors(isDarkMode)
    val surfaceColor = if (isDarkMode) SurfaceCardDark else SurfaceCardLight

    val todayLocal = Calendar.getInstance()
    val todayUtcMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(todayLocal.get(Calendar.YEAR), todayLocal.get(Calendar.MONTH), todayLocal.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateStringToUtcMillis(initialDate),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = true
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        onDateSelected(utcDateFormatter().format(Date(millis)))
                    } else {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
            ) {
                Text(commonOk(currentLang), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(commonCancel(currentLang), color = textSecondary)
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            colors = DatePickerDefaults.colors(
                containerColor = SurfaceCardDark,
                selectedDayContainerColor = GoldAccent,
                selectedDayContentColor = DarkBackground,
                todayDateBorderColor = GoldAccent
            )
        )
    }
}