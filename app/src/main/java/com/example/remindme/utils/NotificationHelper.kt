package com.example.remindme.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.example.remindme.ui.theme.reminderAccentArgb
import com.example.remindme.ui.theme.reminderIcon
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.remindme.ui.theme.AlarmAlertActivity
import java.text.SimpleDateFormat
import java.util.*

private fun pick(lang: String, he: String, en: String, ru: String = en, fr: String = en): String =
    when (lang) {
        "he" -> he
        "ru" -> ru
        "fr" -> fr
        else -> en
    }

private fun cleanPhone(phone: String?): String =
    phone.orEmpty().filter { it.isDigit() || it == '+' || it == '*' || it == '#' }

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isEarly = intent.getBooleanExtra("isEarly", false)
        val advanceMinutes = intent.getIntExtra("advanceMinutes", 0)
        val ringSeconds = intent.getIntExtra("ringSeconds", 0)
        val baseTitle = intent.getStringExtra("title") ?: getLocalizedDefaultTitle(context)
        val title = if (isEarly) NotificationHelper.earlyTitle(context, advanceMinutes, baseTitle) else baseTitle
        val soundUri = intent.getStringExtra("soundUri")
        val phone = intent.getStringExtra("phone")

        val reminderId = intent.getIntExtra("id", -1)
        val savedReminder = try {
            ReminderStorage.loadReminders(context).firstOrNull { it.id == reminderId }
        } catch (_: Exception) {
            null
        }
        // A snoozed alarm carries its own look, because the reminder may no longer be in the list
        val notes = savedReminder?.notes ?: intent.getStringExtra("notes")
        val accent = savedReminder?.let { reminderAccentArgb(it) } ?: intent.getIntExtra("accent", 0)
        val icon = savedReminder?.let { reminderIcon(it) } ?: intent.getStringExtra("icon").orEmpty()
        val fullScreenIntent = NotificationHelper.alarmActivityIntent(context, title, soundUri, phone, notes, accent, icon)

        // Turn the screen on, so the alert is visible even when the phone is off/locked
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "RemindMe:alarmWake"
            ).acquire(15_000L)
        } catch (_: Exception) {
        }

        val serviceStarted = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).apply {
                    putExtra("title", title)
                    putExtra("soundUri", soundUri)
                    putExtra("phone", phone)
                    putExtra("ringSeconds", ringSeconds)
                    putExtra("notes", notes)
                    putExtra("accent", accent)
                    putExtra("icon", icon)
                }
            )
            true
        } catch (_: Exception) {
            false
        }

        if (!serviceStarted) {
            NotificationHelper.showNotification(context, title, fullScreenIntent, phone, soundUri)
        }

        try {
            context.startActivity(fullScreenIntent)
        } catch (_: Exception) {
        }

        val repeatType = intent.getStringExtra("repeatType") ?: "NONE"
        val id = intent.getIntExtra("id", -1)
        val date = intent.getStringExtra("date")
        val time = intent.getStringExtra("time")
        if (repeatType != "NONE" && id != -1 && date != null && time != null) {
            val next = System.currentTimeMillis() + 1000
            if (isEarly) {
                // Only re-arm the early alarm for the next cycle; the main alarm is already set
                NotificationHelper.scheduleEarly(
                    context = context, id = id, title = baseTitle, dateStr = date, timeStr = time,
                    soundUri = soundUri, repeatType = repeatType, phone = phone.orEmpty(),
                    advanceMinutes = advanceMinutes, ringSeconds = ringSeconds, afterMillis = next
                )
            } else {
                NotificationHelper.schedule(
                    context = context,
                    id = id,
                    title = baseTitle,
                    dateStr = date,
                    timeStr = time,
                    soundUri = soundUri,
                    repeatType = repeatType,
                    phone = phone.orEmpty(),
                    advanceMinutes = advanceMinutes,
                    ringSeconds = ringSeconds,
                    afterMillis = next
                )
            }
        }
    }

    private fun getLocalizedDefaultTitle(context: Context): String {
        return when (LanguageManager.getSelectedLanguage(context)) {
            "he" -> "תזכורת חדשה"
            "ru" -> "Новое напоминание"
            "fr" -> "Nouveau rappel"
            else -> "New reminder"
        }
    }
}

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            ReminderStorage.rescheduleAll(context)
        }
    }
}

class AlarmService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.removeCallbacksAndMessages(null)
            releasePlayer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra("title").orEmpty()
        val soundUri = intent?.getStringExtra("soundUri")
        val phone = intent?.getStringExtra("phone")
        val ringSeconds = intent?.getIntExtra("ringSeconds", 0) ?: 0

        handler.removeCallbacksAndMessages(null)
        releasePlayer()

        NotificationHelper.ensureAlarmChannel(this)
        val notes = intent?.getStringExtra("notes")
        val accent = intent?.getIntExtra("accent", 0) ?: 0
        val icon = intent?.getStringExtra("icon").orEmpty()
        val fullScreenIntent = NotificationHelper.alarmActivityIntent(this, title, soundUri, phone, notes, accent, icon)
        val notification = NotificationHelper.buildAlarmNotification(
            context = this,
            channelId = NotificationHelper.ALARM_CHANNEL_ID,
            title = title,
            fullScreenIntent = fullScreenIntent,
            soundUri = soundUri,
            phone = phone,
            withStopAction = true
        )

        val started = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (_: Exception) {
            false
        }
        if (!started) {
            NotificationHelper.showNotification(this, title, fullScreenIntent, phone, soundUri)
            stopSelf()
            return START_NOT_STICKY
        }

        // A foreground service started by the alarm may also open the alert screen
        try {
            startActivity(fullScreenIntent)
        } catch (_: Exception) {
        }

        handler.postDelayed({
            val seconds = (if (ringSeconds > 0) ringSeconds else ReminderStorage.getRingDurationSec(this)).coerceAtLeast(5)
            // A chosen song plays once, from start to end. The default alarm sound
            // (short) repeats for the default ring duration instead.
            val songPlaying = startSound(soundUri)
            deadlineAt = if (songPlaying) 0L else System.currentTimeMillis() + seconds * 1000L
            if (player == null) {
                handler.postDelayed({ finishRinging() }, seconds * 1000L)
            } else {
                // Safety net only (very long tracks)
                handler.postDelayed({ finishRinging() }, 30 * 60_000L)
            }
        }, 300L)

        return START_NOT_STICKY
    }

    private var deadlineAt = 0L

    private fun finishRinging() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun startSound(soundUri: String?): Boolean {
        // Web links can't be played directly, so only phone sounds and songs are used here
        val isLink = !soundUri.isNullOrBlank() && soundUri.startsWith("http")
        val custom: Uri? = if (!soundUri.isNullOrBlank() && !isLink) soundUri.toUri() else null
        if (custom != null && playUri(custom)) return true
        playUri(defaultAlarmUri())
        return false
    }

    private fun playUri(uri: Uri?): Boolean {
        if (uri == null) return false
        releasePlayer()
        return try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(this, uri)
            mp.isLooping = false
            // Replay the whole track from the start until the chosen time is reached;
            // a track is never cut in the middle - the last one plays to its end.
            mp.setOnCompletionListener { m ->
                if (System.currentTimeMillis() < deadlineAt) {
                    try {
                        m.seekTo(0)
                        m.start()
                    } catch (_: Exception) {
                        finishRinging()
                    }
                } else {
                    finishRinging()
                }
            }
            mp.prepare()
            // Start quietly and get louder over a few seconds
            rampVolume = 0.05f
            mp.setVolume(rampVolume, rampVolume)
            mp.start()
            player = mp
            handler.removeCallbacks(rampRunnable)
            handler.postDelayed(rampRunnable, 400L)
            true
        } catch (_: Exception) {
            releasePlayer()
            false
        }
    }

    private var rampVolume = 1f
    private val rampRunnable = object : Runnable {
        override fun run() {
            val p = player ?: return
            rampVolume = (rampVolume + 0.06f).coerceAtMost(1f)
            try {
                p.setVolume(rampVolume, rampVolume)
            } catch (_: Exception) {
                return
            }
            if (rampVolume < 1f) handler.postDelayed(this, 400L)
        }
    }

    private fun releasePlayer() {
        player?.let {
            try {
                if (it.isPlaying) it.stop()
            } catch (_: Exception) {
            }
            try {
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.remindme.ACTION_STOP_ALARM"
        const val NOTIF_ID = 4242

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AlarmService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}

class AlarmActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val type = intent.getStringExtra(EXTRA_TYPE)
        val value = intent.getStringExtra(EXTRA_VALUE).orEmpty()

        AlarmService.stop(this)

        try {
            when (type) {
                TYPE_CALL -> {
                    val canCall = ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED
                    startActivity(
                        Intent(
                            if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL,
                            Uri.fromParts("tel", value, null)
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                TYPE_SONG -> startActivity(
                    Intent(Intent.ACTION_VIEW, value.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (_: Exception) {
        }
        finish()
    }

    companion object {
        const val TYPE_CALL = "call"
        const val TYPE_SONG = "song"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_VALUE = "value"

        fun createIntent(context: Context, type: String, value: String): Intent =
            Intent(context, AlarmActionActivity::class.java).apply {
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_VALUE, value)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
    }
}

object AlarmSettingsManager {
    private const val PREF_NAME = "alarm_settings_pref"
    private const val KEY_DURATION = "ring_duration"
    private const val KEY_COUNT = "snooze_count"

    fun saveSettings(context: Context, duration: Int, count: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_DURATION, duration)
            putInt(KEY_COUNT, count)
            apply()
        }
    }

    fun getDuration(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DURATION, 30)
    }

    fun getCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_COUNT, 3)
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "remind_me_channel_v2"
    const val ALARM_CHANNEL_ID = "remind_me_alarm_silent_v1"
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val EARLY_OFFSET = 1_000_000

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = when (LanguageManager.getSelectedLanguage(context)) {
                "he" -> "RemindMe התראות פרימיום"
                "ru" -> "RemindMe Премиум Уведомления"
                "fr" -> "RemindMe Alertes Premium"
                else -> "RemindMe Premium Notifications"
            }

            val channel = NotificationChannel(
                CHANNEL_ID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = pick(
                    LanguageManager.getSelectedLanguage(context),
                    "התראות מיוחדות עבור תזכורות בזמן אמת",
                    "Special alerts for real-time reminders",
                    "Специальные уведомления для напоминаний",
                    "Alertes spéciales pour les rappels en temps réel"
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        ensureAlarmChannel(context)
    }

    fun ensureAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = pick(
                LanguageManager.getSelectedLanguage(context),
                "RemindMe שעון מעורר",
                "RemindMe Alarm",
                "RemindMe Будильник",
                "RemindMe Alarme"
            )
            val channel = NotificationChannel(ALARM_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun alarmActivityIntent(context: Context, title: String, soundUri: String?, phone: String?, notes: String? = null, accent: Int = 0, icon: String = ""): Intent =
        Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("title", title)
            putExtra("notes", notes)
            putExtra("accent", accent)
            putExtra("icon", icon)
            putExtra("soundUri", soundUri)
            putExtra("phone", phone)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    @SuppressLint("FullScreenIntent")
    fun buildAlarmNotification(
        context: Context,
        channelId: String,
        title: String,
        fullScreenIntent: Intent,
        soundUri: String?,
        phone: String?,
        withStopAction: Boolean
    ): Notification {
        val lang = LanguageManager.getSelectedLanguage(context)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val openIntent = PendingIntent.getActivity(context, 7001, fullScreenIntent, flags)

        val headerText = pick(
            lang,
            "🔔 RemindMe • תזכורת בזמן",
            "🔔 RemindMe • Reminder",
            "🔔 RemindMe • Напоминание",
            "🔔 RemindMe • Rappel"
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(headerText)
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openIntent)
            .setFullScreenIntent(openIntent, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setAutoCancel(true)

        if (withStopAction) {
            val stopPending = PendingIntent.getService(
                context,
                7002,
                Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_STOP),
                flags
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                pick(lang, "עצור", "Stop", "Стоп", "Arrêter"),
                stopPending
            )
        }

        val number = cleanPhone(phone)
        if (number.isNotBlank()) {
            val callPending = PendingIntent.getActivity(
                context,
                7003,
                AlarmActionActivity.createIntent(context, AlarmActionActivity.TYPE_CALL, number),
                flags
            )
            builder.setSubText("📞 $number")
            builder.addAction(
                android.R.drawable.sym_action_call,
                pick(lang, "התקשר", "Call", "Позвонить", "Appeler"),
                callPending
            )
        }

        if (!soundUri.isNullOrBlank() && soundUri.startsWith("http")) {
            val songPending = PendingIntent.getActivity(
                context,
                7004,
                AlarmActionActivity.createIntent(context, AlarmActionActivity.TYPE_SONG, soundUri),
                flags
            )
            builder.addAction(
                android.R.drawable.ic_media_play,
                pick(lang, "פתח שיר", "Open song", "Открыть песню", "Ouvrir la chanson"),
                songPending
            )
        }

        return builder.build()
    }

    fun showNotification(
        context: Context,
        title: String,
        fullScreenIntent: Intent,
        phone: String? = null,
        soundUri: String? = null
    ) {
        createNotificationChannel(context)
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = buildAlarmNotification(
            context = context,
            channelId = CHANNEL_ID,
            title = title,
            fullScreenIntent = fullScreenIntent,
            soundUri = soundUri,
            phone = phone,
            withStopAction = false
        )

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    fun nextTriggerMillis(
        dateStr: String,
        timeStr: String,
        repeatType: String,
        afterMillis: Long = System.currentTimeMillis()
    ): Long? {
        val parsed: Date? = try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$dateStr $timeStr")
        } catch (_: Exception) {
            null
        }
        val base = parsed ?: return null

        val (field, step) = when (repeatType) {
            "DAILY" -> Calendar.DAY_OF_YEAR to 1
            "WEEKLY" -> Calendar.DAY_OF_YEAR to 7
            "MONTHLY" -> Calendar.MONTH to 1
            "YEARLY" -> Calendar.YEAR to 1
            else -> return when {
                base.time > afterMillis -> base.time
                // Chosen "right now" (same minute): ring in a couple of seconds
                afterMillis - base.time < 60_000L -> afterMillis + 2_000L
                else -> null
            }
        }

        var k = when (repeatType) {
            "DAILY" -> (afterMillis - base.time) / DAY_MS
            "WEEKLY" -> (afterMillis - base.time) / (7 * DAY_MS) - 1
            else -> 0L
        }.coerceAtLeast(0L).toInt()

        val limit = k + 20000
        while (k < limit) {
            val candidate = Calendar.getInstance().apply {
                time = base
                add(field, k * step)
            }.timeInMillis
            if (candidate > afterMillis) return candidate
            k++
        }
        return null
    }

    private fun setAlarm(context: Context, triggerAt: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            val showIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            if (showIntent != null) {
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    /** Rings the same alert again after [minutes]. */
    fun snooze(
        context: Context,
        title: String,
        soundUri: String?,
        phone: String?,
        notes: String?,
        accent: Int,
        icon: String,
        minutes: Int = 5
    ) {
        try {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", -1)
                putExtra("title", title)
                putExtra("soundUri", soundUri)
                putExtra("phone", phone)
                putExtra("notes", notes)
                putExtra("accent", accent)
                putExtra("icon", icon)
                putExtra("repeatType", "NONE")
            }
            val code = 3_000_000 + (kotlin.math.abs(title.hashCode()) % 100_000)
            val pendingIntent = PendingIntent.getBroadcast(
                context, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(context, System.currentTimeMillis() + minutes * 60_000L, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun schedule(
        context: Context,
        id: Int,
        title: String,
        dateStr: String,
        timeStr: String,
        soundUri: String? = null,
        repeatType: String = "NONE",
        phone: String = "",
        advanceMinutes: Int = 0,
        ringSeconds: Int = 0,
        afterMillis: Long = System.currentTimeMillis()
    ) {
        try {
            val triggerAt = nextTriggerMillis(dateStr, timeStr, repeatType, afterMillis)
            if (triggerAt == null) {
                cancelReminder(context, id)
                return
            }

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("soundUri", soundUri)
                putExtra("date", dateStr)
                putExtra("time", timeStr)
                putExtra("repeatType", repeatType)
                putExtra("phone", phone)
                putExtra("advanceMinutes", advanceMinutes)
                putExtra("ringSeconds", ringSeconds)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(context, triggerAt, pendingIntent)

            scheduleEarly(context, id, title, dateStr, timeStr, soundUri, repeatType, phone, advanceMinutes, ringSeconds, afterMillis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Arms the "X before" alarm for the first occurrence whose early time is still in the future. */
    fun scheduleEarly(
        context: Context,
        id: Int,
        title: String,
        dateStr: String,
        timeStr: String,
        soundUri: String?,
        repeatType: String,
        phone: String,
        advanceMinutes: Int,
        ringSeconds: Int = 0,
        afterMillis: Long = System.currentTimeMillis()
    ) {
        try {
            if (advanceMinutes <= 0) {
                cancelCode(context, id + EARLY_OFFSET)
                return
            }
            val advanceMs = advanceMinutes * 60_000L
            val occurrence = nextTriggerMillis(dateStr, timeStr, repeatType, afterMillis + advanceMs)
            val baseTime = try { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).parse("$dateStr $timeStr")?.time } catch (_: Exception) { null }
            val earlyAlreadyPassed = repeatType == "NONE" && baseTime != null && baseTime <= afterMillis + advanceMs
            if (occurrence == null || earlyAlreadyPassed) {
                cancelCode(context, id + EARLY_OFFSET)
                return
            }

            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", id)
                putExtra("title", title)
                putExtra("soundUri", soundUri)
                putExtra("date", dateStr)
                putExtra("time", timeStr)
                putExtra("repeatType", repeatType)
                putExtra("phone", phone)
                putExtra("advanceMinutes", advanceMinutes)
                putExtra("ringSeconds", ringSeconds)
                putExtra("isEarly", true)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, id + EARLY_OFFSET, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(context, occurrence - advanceMs, pendingIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, id: Int) {
        cancelCode(context, id)
        cancelCode(context, id + EARLY_OFFSET)
    }

    private fun cancelCode(context: Context, requestCode: Int) {
        try {
            val existing = PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, ReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existing != null) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(existing)
                existing.cancel()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun earlyTitle(context: Context, minutes: Int, title: String): String {
        val lang = LanguageManager.getSelectedLanguage(context)
        val amount = when (minutes) {
            5 -> pick(lang, "5 דקות", "5 minutes", "5 минут", "5 minutes")
            15 -> pick(lang, "15 דקות", "15 minutes", "15 минут", "15 minutes")
            30 -> pick(lang, "30 דקות", "30 minutes", "30 минут", "30 minutes")
            60 -> pick(lang, "שעה", "1 hour", "1 час", "1 heure")
            120 -> pick(lang, "שעתיים", "2 hours", "2 часа", "2 heures")
            1440 -> pick(lang, "יום", "1 day", "1 день", "1 jour")
            else -> pick(lang, "$minutes דקות", "$minutes minutes", "$minutes минут", "$minutes minutes")
        }
        return pick(lang, "בעוד $amount: $title", "In $amount: $title", "Через $amount: $title", "Dans $amount : $title")
    }

    @Deprecated("Use schedule() with the reminder id")
    fun scheduleNotification(
        context: Context,
        title: String,
        dateStr: String,
        timeStr: String,
        soundUri: String? = null
    ) {
        schedule(context, title.hashCode(), title, dateStr, timeStr, soundUri, "NONE")
    }
}