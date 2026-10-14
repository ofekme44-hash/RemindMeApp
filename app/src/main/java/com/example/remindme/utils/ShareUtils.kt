package com.example.remindme.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareUtils {
    fun shareCalendarViaWhatsApp(context: Context, senderName: String, permission: String) {
        val encodedName = Uri.encode(senderName)
        // שימוש בקישור נקי שוואטסאפ יהפוך לכחול ולחיץ
        val deepLinkUrl = "https://remindme.local/share?sender=$encodedName&permission=$permission"

        val message = when (context.resources.configuration.locales[0].language) {
            "he" -> "📅 *הזמנה לשיתוף יומן ב-RemindMe*\n\nהיי! $senderName הזמין אותך לשתף את היומן שלו עם הרשאת: *${if (permission == "edit") "קריאה ועריכה" else "קריאה בלבד"}*.\n\nלחיצה לאישור המהיר:\n$deepLinkUrl"
            else -> "📅 *RemindMe Calendar Share Invite*\n\nHi! $senderName invited you to share their calendar with permission: *$permission*.\n\nClick to accept:\n$deepLinkUrl"
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://api.whatsapp.com/send?text=${Uri.encode(message)}")
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Calendar"))
        }
    }
}