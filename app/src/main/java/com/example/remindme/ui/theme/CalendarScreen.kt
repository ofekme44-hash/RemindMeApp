package com.example.remindme.ui.theme

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.remindme.utils.LanguageManager
import com.example.remindme.utils.NotificationHelper
import com.example.remindme.utils.ReminderStorage
import com.example.remindme.utils.ShareUtils
import java.text.SimpleDateFormat
import java.util.*

private val DarkBg = Color(0xFF0A0A0C)
private val LightBg = Color(0xFFF4F4F6)
private val SurfaceDark = Color(0xFF161619)
private val SurfaceLight = Color(0xFFFFFFFF)
private val GoldAccentColor = Color(0xFFE6C687)
private val TextPrimaryDarkColor = Color(0xFFF8F8FA)
private val TextPrimaryLightColor = Color(0xFF111115)
private val TextSecondaryDarkColor = Color(0xFF9E9EA7)
private val TextSecondaryLightColor = Color(0xFF55555D)

@Composable
fun CalendarScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var selectedDate by remember { mutableStateOf(sdf.format(Date())) }

    val isDarkMode = ReminderStorage.isDarkMode(context)

    val backgroundColor = if (isDarkMode) DarkBg else LightBg
    val surfaceColor = if (isDarkMode) SurfaceDark else SurfaceLight
    val textPrimary = if (isDarkMode) TextPrimaryDarkColor else TextPrimaryLightColor
    val textSecondary = if (isDarkMode) TextSecondaryDarkColor else TextSecondaryLightColor

    var reminders by remember { mutableStateOf(ReminderStorage.loadReminders(context)) }

    LaunchedEffect(reminders) {
        ReminderStorage.saveReminders(context, reminders)
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToDelete by remember { mutableStateOf<ReminderItem?>(null) }

    val filteredReminders = reminders.filter { it.date == selectedDate }

    Scaffold(
        containerColor = backgroundColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = GoldAccentColor,
                contentColor = backgroundColor,
                shape = RoundedCornerShape(20.dp)
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
            // הוספת מרווח עליון כדי שהכפתורים ירדו טיפה למטה ולא יצמדו למעלה
            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = surfaceColor,
                        contentColor = GoldAccentColor
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, GoldAccentColor.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("➡️ ${LanguageManager.getString(context, "back")}", fontWeight = FontWeight.Bold, maxLines = 1)
                }

                Button(
                    onClick = {
                        val inviteId = "xyz123"
                        val permissionType = "read"
                        ShareUtils.shareCalendarViaWhatsApp(context, inviteId, permissionType)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = surfaceColor,
                        contentColor = GoldAccentColor
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, GoldAccentColor.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔗 ${LanguageManager.getString(context, "share_calendar")}", fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "📅 ${LanguageManager.getString(context, "calendar_title")}",
                color = textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            CalendarViewComposable(
                selectedDate = selectedDate,
                isDarkMode = isDarkMode,
                onDateSelected = { selectedDate = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = String.format(LanguageManager.getString(context, "reminders_for"), selectedDate),
                color = GoldAccentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (filteredReminders.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = LanguageManager.getString(context, "no_reminders_date"),
                            color = textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredReminders, key = { it.id }) { reminder ->
                            CalendarReminderRow(
                                reminder = reminder,
                                isDarkMode = isDarkMode,
                                onStatusChange = { updated: ReminderItem ->
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

    reminderToDelete?.let { reminder ->
        AlertDialog(
            onDismissRequest = { reminderToDelete = null },
            containerColor = surfaceColor,
            title = { Text(LanguageManager.getString(context, "delete_title"), color = textPrimary, fontWeight = FontWeight.Bold) },
            text = { Text(String.format(LanguageManager.getString(context, "delete_msg"), reminder.title), color = textSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        reminders = reminders.filter { it.id != reminder.id }
                        reminderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(LanguageManager.getString(context, "delete_btn"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { reminderToDelete = null }) {
                    Text(LanguageManager.getString(context, "cancel"), color = textSecondary)
                }
            }
        )
    }

    if (showAddDialog) {
        AddReminderDialog(
            isDarkMode = isDarkMode,
            currentLang = LanguageManager.getSelectedLanguage(context),
            initialDate = selectedDate,
            onDismiss = { showAddDialog = false },
            onAdd = { title: String, time: String, date: String, isRepeating: Boolean, repeatType: String, soundUri: String? ->
                val newId = (reminders.maxOfOrNull { it.id } ?: 0) + 1
                val newReminder = ReminderItem(
                    id = newId,
                    title = title,
                    time = time,
                    date = date,
                    isDaily = isRepeating && repeatType == "DAILY",
                    repeatType = if (isRepeating) repeatType else "NONE"
                )
                reminders = reminders + newReminder
                NotificationHelper.scheduleNotification(context, title, date, time, soundUri)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CalendarViewComposable(
    selectedDate: String,
    isDarkMode: Boolean,
    onDateSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    var calendarDate by remember {
        mutableStateOf(try { sdf.parse(selectedDate) ?: Date() } catch (_: Exception) { Date() })
    }

    val cal = remember(calendarDate) {
        Calendar.getInstance().apply { time = calendarDate }
    }

    val currentYear = cal.get(Calendar.YEAR)
    val currentMonth = cal.get(Calendar.MONTH)

    val monthHeaderFormat = SimpleDateFormat("MMMM yyyy", LanguageManager.getLocale(context))
    val monthTitle = monthHeaderFormat.format(cal.time)

    val surfaceColor = if (isDarkMode) SurfaceDark else SurfaceLight
    val textPrimary = if (isDarkMode) TextPrimaryDarkColor else TextPrimaryLightColor
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val daysList = (1..daysInMonth).toList()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, GoldAccentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val prevCal = Calendar.getInstance().apply {
                        time = calendarDate
                        add(Calendar.MONTH, -1)
                    }
                    calendarDate = prevCal.time
                }) {
                    Text(
                        "<",
                        color = GoldAccentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = monthTitle,
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = {
                    val nextCal = Calendar.getInstance().apply {
                        time = calendarDate
                        add(Calendar.MONTH, 1)
                    }
                    calendarDate = nextCal.time
                }) {
                    Text(
                        ">",
                        color = GoldAccentColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
            ) {
                items(daysList) { day ->
                    val dayDateStr = String.format(
                        Locale.getDefault(),
                        "%04d-%02d-%02d",
                        currentYear,
                        currentMonth + 1,
                        day
                    )
                    val isSelected = dayDateStr == selectedDate

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(if (isSelected) GoldAccentColor else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) GoldAccentColor else GoldAccentColor.copy(
                                    alpha = 0.4f
                                ),
                                shape = CircleShape
                            )
                            .clickable { onDateSelected(dayDateStr) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$day",
                            color = if (isSelected) (if (isDarkMode) DarkBg else SurfaceLight) else textPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarReminderRow(
    reminder: ReminderItem,
    isDarkMode: Boolean,
    onStatusChange: (ReminderItem) -> Unit,
    onDeleteClick: () -> Unit
) {
    val surfaceColor = if (isDarkMode) SurfaceDark else SurfaceLight
    val textPrimary = if (isDarkMode) TextPrimaryDarkColor else TextPrimaryLightColor
    val textSecondary = if (isDarkMode) TextSecondaryDarkColor else TextSecondaryLightColor

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, GoldAccentColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    color = textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⏰ ${reminder.time}",
                    color = textSecondary,
                    fontSize = 14.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { isChecked ->
                        onStatusChange(reminder.copy(isEnabled = isChecked))
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = if (isDarkMode) DarkBg else SurfaceLight,
                        checkedTrackColor = GoldAccentColor
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDeleteClick) {
                    Text("🗑️", fontSize = 18.sp)
                }
            }
        }
    }
}