package com.example.remindme.utils

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
        val title = intent.getStringExtra("title") ?: getLocalizedDefaultTitle(context)
        val soundUri = intent.getStringExtra("soundUri")
        val phone = intent.getStringExtra("phone")

        val fullScreenIntent = NotificationHelper.alarmActivityIntent(context, title, soundUri, phone)

        try {
            context.startActivity(fullScreenIntent)
        } catch (_: Exception) {
        }

        val serviceStarted = try {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AlarmService::class.java).apply {
                    putExtra("title", title)
                    putExtra("soundUri", soundUri)
                    putExtra("phone", phone)
                }
            )
            true
        } catch (_: Exception) {
            false
        }

        if (!serviceStarted) {
            NotificationHelper.showNotification(context, title, fullScreenIntent, phone, soundUri)
        }

        val repeatType = intent.getStringExtra("repeatType") ?: "NONE"
        val id = intent.getIntExtra("id", -1)
        val date = intent.getStringExtra("date")
        val time = intent.getStringExtra("time")
        if (repeatType != "NONE" && id != -1 && date != null && time != null) {
            NotificationHelper.schedule(
                context = context,
                id = id,
                title = title,
                dateStr = date,
                timeStr = time,
                soundUri = soundUri,
                repeatType = repeatType,
                phone = phone.orEmpty(),
                afterMillis = System.currentTimeMillis() + 1000
            )
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

        handler.removeCallbacksAndMessages(null)
        releasePlayer()

        NotificationHelper.ensureAlarmChannel(this)
        val fullScreenIntent = NotificationHelper.alarmActivityIntent(this, title, soundUri, phone)
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

        handler.postDelayed({
            if (isAppInForeground()) {
                finishRinging()
            } else {
                startSound(soundUri)
                val seconds = ReminderStorage.getRingDurationSec(this).coerceAtLeast(10)
                handler.postDelayed({ finishRinging() }, seconds * 1000L)
            }
        }, 1500L)

        return START_NOT_STICKY
    }

    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun finishRinging() {
        releasePlayer()
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun defaultAlarmUri(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private fun startSound(soundUri: String?) {
        val custom: Uri? = if (!soundUri.isNullOrBlank() && !soundUri.startsWith("http")) soundUri.toUri() else null
        if (custom == null || !playUri(custom)) {
            playUri(defaultAlarmUri())
        }
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
            mp.setDataSource(this, uri)
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (_: Exception) {
            releasePlayer()
            false
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
                description = "התראות מיוחדות עבור תזכורות בזמן אמת"
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

    fun alarmActivityIntent(context: Context, title: String, soundUri: String?, phone: String?): Intent =
        Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("title", title)
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
            else -> return if (base.time > afterMillis) base.time else null
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

    fun schedule(
        context: Context,
        id: Int,
        title: String,
        dateStr: String,
        timeStr: String,
        soundUri: String? = null,
        repeatType: String = "NONE",
        phone: String = "",
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
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cancelReminder(context: Context, id: Int) {
        try {
            val existing = PendingIntent.getBroadcast(
                context,
                id,
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