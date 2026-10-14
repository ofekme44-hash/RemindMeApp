package com.example.remindme.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.remindme.ui.theme.AlarmAlertActivity

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "תזכורת"
        val soundUri = intent.getStringExtra("soundUri")

        // פתיחת מסך ההתראה המלא מעל מסך הבית והנעילה
        val alarmIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("title", title)
            putExtra("soundUri", soundUri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(alarmIntent)
    }
}