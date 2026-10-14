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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.remindme.utils.LanguageManager
import com.example.remindme.utils.ReminderStorage
import com.example.remindme.utils.ShareUtils
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    val soundUri: String? = null
)

data class DayForecast(val date: String, val max: Int, val min: Int, val code: Int)
data class WeatherState(val currentTemp: Int, val currentCode: Int, val daily: List<DayForecast>)

// ───────────────────────────── Helpers ─────────────────────────────

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

private fun ReminderItem.isExpired(): Boolean {
    if (isDaily || repeatType != "NONE") return false
    val dateTime = parseDateTime(date, time) ?: return false
    return dateTime.before(Date())
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
    soundUri: String?
) = ReminderItem(
    id = (existing.maxOfOrNull { it.id } ?: 0) + 1,
    title = title,
    time = time,
    date = date,
    isDaily = isRepeating && repeatType == "DAILY",
    repeatType = if (isRepeating) repeatType else "NONE",
    soundUri = soundUri
)

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

private fun youtubeSearchUrl(query: String) =
    "https://www.youtube.com/results?search_query=${Uri.encode(query)}"

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

private suspend fun fetchWeather(): WeatherState? = withContext(Dispatchers.IO) {
    try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$WEATHER_LAT&longitude=$WEATHER_LON" +
                "&current=temperature_2m,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=7"
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
            daily = days
        )
    } catch (_: Exception) {
        null
    }
}

private suspend fun fetchSongSuggestions(query: String): List<Pair<String, String>> =
    withContext(Dispatchers.IO) {
        val fallback = listOf(query to youtubeSearchUrl(query))
        try {
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=${Uri.encode(query)}"
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            val response = try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
            val jsonArray = JSONArray(response.substring(response.indexOf("["), response.lastIndexOf("]") + 1))
            val items = jsonArray.getJSONArray(1)
            val result = mutableListOf<Pair<String, String>>()
            for (i in 0 until minOf(items.length(), 5)) {
                val term = items.getJSONArray(i).getString(0)
                result.add(term to youtubeSearchUrl(term))
            }
            if (result.isEmpty()) fallback else result
        } catch (_: Exception) {
            fallback
        }
    }

// ───────────────────────────── Activity ─────────────────────────────

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkAndRequestFullScreenPermission()

        val data: Uri? = intent?.data
        val isSharedInvite = data != null && data.scheme == "https" && data.host == "remindme.local"
        val senderName = data?.getQueryParameter("sender") ?: "משתמש"
        val permission = data?.getQueryParameter("permission") ?: "read"
        setContent {
            var isDarkMode by remember { mutableStateOf(ReminderStorage.isDarkMode(this@MainActivity)) }
            var showShareDialog by remember { mutableStateOf(isSharedInvite) }
            var isFirstLaunch by remember { mutableStateOf(LanguageManager.isFirstLaunch(this@MainActivity)) }
            var currentLang by remember { mutableStateOf(LanguageManager.getSelectedLanguage(this@MainActivity)) }

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
                    } else {
                        MainAppScreen(
                            isDarkMode = isDarkMode,
                            currentLang = currentLang,
                            onToggleDarkMode = {
                                val newValue = !isDarkMode
                                isDarkMode = newValue
                                ReminderStorage.saveDarkMode(this@MainActivity, newValue)
                            },
                            onLanguageChanged = { newLang ->
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
                                        text = "$senderName מבקש לשתף איתך את היומן שלו.",
                                        color = TextPrimaryDark,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "סוג הרשאה מבוקש: $permissionText",
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
        title = { Text(title, color = textPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = textSecondary) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (destructive) MaterialTheme.colorScheme.error else GoldAccent,
                    contentColor = if (destructive) Color.White else DarkBackground
                )
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
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
            .background(backgroundColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "RemindMe",
                color = GoldAccent,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
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
fun MainAppScreen(
    isDarkMode: Boolean,
    currentLang: String,
    onToggleDarkMode: () -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf("MAIN") }

    BackHandler(enabled = currentScreen == "CALENDAR") { currentScreen = "MAIN" }

    PermissionCheckDialog(isDarkMode = isDarkMode, currentLang = currentLang)

    if (currentScreen == "CALENDAR") {
        RemindMeCalendarScreen(
            currentLang = currentLang,
            onNavigateBack = { currentScreen = "MAIN" },
            isDarkMode = isDarkMode
        )
    } else {
        MainScreenContent(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            onToggleDarkMode = onToggleDarkMode,
            onOpenCalendar = { currentScreen = "CALENDAR" },
            onLanguageChanged = onLanguageChanged
        )
    }
}

@Composable
fun MainScreenContent(
    isDarkMode: Boolean,
    currentLang: String,
    onToggleDarkMode: () -> Unit,
    onOpenCalendar: () -> Unit,
    onLanguageChanged: (String) -> Unit
) {
    val context = LocalContext.current
    val (backgroundColor, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    var reminders by remember {
        mutableStateOf(ReminderStorage.loadReminders(context).filterNot { it.isExpired() })
    }

    LaunchedEffect(reminders) {
        ReminderStorage.saveReminders(context, reminders)
    }

    var weather by remember { mutableStateOf<WeatherState?>(null) }
    LaunchedEffect(Unit) { weather = fetchWeather() }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWeatherWeeklyDialog by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<ReminderItem?>(null) }

    val appTitle = remember(currentLang) { LanguageManager.getString(context, "app_title") }
    val myRemindersText = remember(currentLang) { LanguageManager.getString(context, "my_reminders") }
    val sortedReminders = remember(reminders) { reminders.sortedByTime() }

    Scaffold(
        containerColor = backgroundColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GoldAccent,
                contentColor = DarkBackground,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text("+", fontSize = 32.sp, fontWeight = FontWeight.Bold)
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

            Text(
                text = appTitle,
                color = GoldAccent,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleDarkMode,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceColor)
                        .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                ) {
                    Text(if (isDarkMode) "☀️" else "🌙", fontSize = 20.sp)
                }

                IconButton(
                    onClick = onOpenCalendar,
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

            Box(modifier = Modifier.clickable { showWeatherWeeklyDialog = true }) {
                WeatherAndDateWidget(isDarkMode = isDarkMode, currentLang = currentLang, weather = weather)
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = myRemindersText,
                color = textPrimary,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (sortedReminders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = LanguageManager.getString(context, "no_reminders"),
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        )
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
                                onDeleteClick = { reminderToDelete = reminder }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = currentLang,
            initialDate = formatDate(Date()),
            onDismiss = { showAddDialog = false },
            onAdd = { title, time, date, isRepeating, repeatType, soundUri ->
                reminders = reminders + createReminder(reminders, title, time, date, isRepeating, repeatType, soundUri)
                showAddDialog = false
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
            onLanguageChanged = onLanguageChanged
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
        containerColor = DarkBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GoldAccent,
                contentColor = DarkBackground,
                shape = CircleShape,
                modifier = Modifier.size(60.dp)
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
                        text = if (isRtl) "חזרה ➡️" else "⬅️ Back",
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
                colors = CardDefaults.cardColors(containerColor = SurfaceCardDark),
                shape = RoundedCornerShape(24.dp),
                border = goldGlowBorder,
                modifier = Modifier.fillMaxWidth()
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

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) GoldAccent else Color.Transparent)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) GoldAccent else GoldAccent.copy(alpha = 0.35f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedDate = (selectedDate.clone() as Calendar).apply {
                                                        set(Calendar.DAY_OF_MONTH, dayNumber)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                color = if (isSelected) DarkBackground else TextPrimaryDark,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 15.sp
                                            )
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
                text = "תזכורות לתאריך $selectedDateStr",
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
            title = { Text("שיתוף יומן עם חבר", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = senderNameInput,
                        onValueChange = { senderNameInput = it },
                        label = { Text("השם שלך", color = TextSecondaryDark) },
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
                        Text("התר הרשאת עריכה?", color = TextPrimaryDark, fontSize = 14.sp)
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
                    Text("שלח קישור בוואטסאפ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showShareSetupDialog = false }) {
                    Text("ביטול", color = TextSecondaryDark)
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
            onAdd = { title, time, date, isRepeating, repeatType, soundUri ->
                reminders = reminders + createReminder(reminders, title, time, date, isRepeating, repeatType, soundUri)
                ReminderStorage.saveReminders(context, reminders)
                showAddDialog = false
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
                reminders = reminders.filter { it.id != reminder.id }
                ReminderStorage.saveReminders(context, reminders)
                reminderToDelete = null
            },
            onDismiss = { reminderToDelete = null }
        )
    }
}

// ───────────────────────────── Add reminder ─────────────────────────────

@Composable
fun AddReminderDialog(
    isDarkMode: Boolean,
    currentLang: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onAdd: (title: String, time: String, date: String, isRepeating: Boolean, repeatType: String, soundUri: String?) -> Unit
) {
    val context = LocalContext.current
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    val fieldBackground = if (isDarkMode) DarkBackground else LightBackground

    val defaultSoundTitle = tr(currentLang, "ברירת מחדל (התראה)", "Default (Alarm)", "По умолчанию (будильник)", "Par défaut (alarme)")
    val noSoundTitle = tr(currentLang, "ללא צליל", "No sound", "Без звука", "Silencieux")
    val selectedSoundFallback = tr(currentLang, "צליל שנבחר", "Selected sound", "Выбранный звук", "Son sélectionné")

    var title by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var isRepeating by remember { mutableStateOf(false) }
    var repeatType by remember { mutableStateOf("DAILY") }
    var selectedSoundUri by remember { mutableStateOf<String?>(null) }
    var soundTitle by remember { mutableStateOf(defaultSoundTitle) }
    var attemptedSave by remember { mutableStateOf(false) }

    var showTimePickerState by remember { mutableStateOf(false) }
    var showDatePickerState by remember { mutableStateOf(false) }
    var showSoundOptionsDialog by remember { mutableStateOf(false) }
    var showUrlInputDialog by remember { mutableStateOf(false) }
    var tempUrlInput by remember { mutableStateOf("") }

    val addTitleText = LanguageManager.getString(context, "add_title")
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
    val isInPast = !isRepeating && parseDateTime(date, time)?.before(Date()) == true
    val errorText: String? = if (isInPast) {
        tr(currentLang, "הזמן שבחרת כבר עבר", "The selected time has already passed", "Выбранное время уже прошло", "L'heure choisie est déjà passée")
    } else null

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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = { Text(addTitleText, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(titleLabelText, color = if (titleError) MaterialTheme.colorScheme.error else textSecondary) },
                    singleLine = true,
                    isError = titleError,
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

                Box(modifier = Modifier.fillMaxWidth().clickable { showTimePickerState = true }) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(timeLabelText, color = if (timeError) MaterialTheme.colorScheme.error else textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = if (timeError) MaterialTheme.colorScheme.error else GoldAccent,
                            disabledLabelColor = if (timeError) MaterialTheme.colorScheme.error else textSecondary,
                            disabledTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().clickable { showDatePickerState = true }) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(dateLabelText, color = textSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = GoldAccent,
                            disabledLabelColor = textSecondary,
                            disabledTextColor = textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (errorText != null) {
                    Text(text = errorText, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = repeatToggleText, color = textPrimary, fontSize = 14.sp)
                    Switch(
                        checked = isRepeating,
                        onCheckedChange = { isRepeating = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = if (isDarkMode) DarkBackground else SurfaceCardLight, checkedTrackColor = GoldAccent)
                    )
                }

                if (isRepeating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                        repeatOptions.forEach { typeCode ->
                            val isSelected = repeatType == typeCode
                            Button(
                                onClick = { repeatType = typeCode },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) GoldAccent else surfaceColor,
                                    contentColor = if (isSelected) DarkBackground else textPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp)
                            ) {
                                Text(text = repeatUnitLabel(currentLang, typeCode), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(fieldBackground)
                        .clickable { showSoundOptionsDialog = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = soundLabelText, color = textSecondary, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = soundTitle, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    Text(text = "🔔", fontSize = 20.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    attemptedSave = true
                    if (title.isNotBlank() && time.isNotBlank() && !isInPast) {
                        onAdd(title.trim(), time, date, isRepeating, repeatType, selectedSoundUri)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
            ) {
                Text(saveScheduleText, fontWeight = FontWeight.Bold)
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
                            tempUrlInput = ""
                            showUrlInputDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(tr(currentLang, "🎵 חיפוש שיר או קישור", "🎵 Search Song or Link", "🎵 Поиск песни или ссылка", "🎵 Chanson ou lien"))
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

    if (showUrlInputDialog) {
        var searchQuery by remember { mutableStateOf(tempUrlInput) }
        var searchResults by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        val dialogTitle = tr(currentLang, "חיפוש שיר או הדבקת קישור", "Search Song or Paste Link", "Поиск песни или ссылка", "Rechercher une chanson ou un lien")
        val placeholderText = tr(currentLang, "חפש שיר...", "Search song title...", "Поиск песни...", "Rechercher une chanson...")

        LaunchedEffect(searchQuery) {
            if (searchQuery.isNotBlank() && !searchQuery.startsWith("http")) {
                delay(400)
                isSearching = true
                searchResults = fetchSongSuggestions(searchQuery)
                isSearching = false
            } else {
                searchResults = emptyList()
                isSearching = false
            }
        }

        AlertDialog(
            onDismissRequest = { showUrlInputDialog = false },
            containerColor = surfaceColor,
            title = { Text(dialogTitle, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(placeholderText, color = textSecondary) },
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

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = GoldAccent)
                        }
                    }

                    if (searchResults.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.heightIn(max = 160.dp)
                        ) {
                            items(searchResults) { (titleText, url) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(fieldBackground)
                                        .clickable {
                                            selectedSoundUri = url
                                            soundTitle = titleText
                                            showUrlInputDialog = false
                                            showSoundOptionsDialog = false
                                        }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "🎵 $titleText", color = textPrimary, fontSize = 14.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            selectedSoundUri = if (searchQuery.startsWith("http")) searchQuery else youtubeSearchUrl(searchQuery)
                            soundTitle = searchQuery
                            showUrlInputDialog = false
                            showSoundOptionsDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground)
                ) {
                    Text(okText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlInputDialog = false }) {
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
    val dateFormat = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag(currentLang))
    val currentDateStr = dateFormat.format(Date())

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = currentDateStr, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr(currentLang, "ישראל, נתניה", "Israel, Netanya", "Израиль, Нетания", "Israël, Netanya"),
                    color = textSecondary,
                    fontSize = 13.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (weather != null) {
                    Text(text = weatherEmoji(weather.currentCode), fontSize = 24.sp)
                    Text(text = "${weather.currentTemp}°", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
    onLanguageChanged: (String) -> Unit
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)

    val languages = listOf(
        "he" to "🇮🇱 עברית",
        "en" to "🇬🇧 English",
        "ru" to "🇷🇺 Русский",
        "fr" to "🇫🇷 Français"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surfaceColor,
        title = {
            Text(
                text = tr(currentLang, "הגדרות שפה", "Language Settings", "Настройки языка", "Paramètres de langue"),
                color = textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = tr(currentLang, "בחר שפת ממשק:", "Choose interface language:", "Выберите язык интерфейса:", "Choisissez la langue :"), color = textSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                languages.forEach { (code, label) ->
                    Button(
                        onClick = {
                            onLanguageChanged(code)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentLang == code) GoldAccent else surfaceColor,
                            contentColor = if (currentLang == code) DarkBackground else textPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = label, fontWeight = FontWeight.Bold)
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
fun LuxuryReminderRow(
    reminder: ReminderItem,
    isDarkMode: Boolean,
    currentLang: String,
    onStatusChange: (ReminderItem) -> Unit,
    onDeleteClick: () -> Unit
) {
    val (_, surfaceColor, textPrimary, textSecondary) = appColors(isDarkMode)
    val repeatKey = if (reminder.repeatType != "NONE") reminder.repeatType else if (reminder.isDaily) "DAILY" else "NONE"
    val badge = repeatBadge(currentLang, repeatKey)

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth().alpha(if (reminder.isEnabled) 1f else 0.55f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = reminder.title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "⏰ ${reminder.time}", color = GoldAccent, fontSize = 13.sp)
                    if (reminder.date.isNotBlank()) {
                        Text(text = "📅 ${reminder.date}", color = textSecondary, fontSize = 13.sp)
                    }
                }
                if (badge != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = "🔁 $badge", color = textSecondary, fontSize = 12.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { onStatusChange(reminder.copy(isEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = if (isDarkMode) DarkBackground else SurfaceCardLight, checkedTrackColor = GoldAccent)
                )
                IconButton(onClick = onDeleteClick) {
                    Text("🗑️", fontSize = 18.sp)
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
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
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