package com.example.remindme.ui.theme

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remindme.utils.AlarmService
import com.example.remindme.utils.LanguageManager
import com.example.remindme.utils.NotificationHelper
import com.example.remindme.utils.ReminderStorage

class AlarmAlertActivity : ComponentActivity() {

    private var alertTitle by mutableStateOf("")
    private var alertNotes by mutableStateOf("")
    private var alertAccent by mutableStateOf(0)
    private var alertIcon by mutableStateOf("")
    private var alertSound: String? = null
    private var alertPhone: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        readAlert(intent)

        val lang = LanguageManager.getSelectedLanguage(this)
        val mode = ReminderStorage.getAlarmWindowMode(this)
        val headerText = LanguageManager.getString(this, "alert_title")
        val dismissText = LanguageManager.getString(this, "alert_dismiss")
        val snoozeText = LanguageManager.getString(this, "alert_snooze")

        setContent {
            val direction = if (lang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlarmScreen(
                    title = alertTitle,
                    notes = alertNotes,
                    icon = alertIcon,
                    accent = if (alertAccent != 0) Color(alertAccent) else GoldAccent,
                    headerText = headerText,
                    dismissText = dismissText,
                    snoozeText = snoozeText,
                    onSnooze = {
                        NotificationHelper.snooze(
                            this@AlarmAlertActivity, alertTitle, alertSound, alertPhone,
                            alertNotes, alertAccent, alertIcon, 5
                        )
                        finish()
                    },
                    isQuarter = mode == "QUARTER",
                    onDismiss = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readAlert(intent)
    }

    private fun readAlert(source: Intent) {
        alertTitle = source.getStringExtra("title")
            ?: LanguageManager.getString(this, "alert_default_title")
        alertNotes = source.getStringExtra("notes").orEmpty()
        alertAccent = source.getIntExtra("accent", 0)
        alertIcon = source.getStringExtra("icon").orEmpty()
        alertSound = source.getStringExtra("soundUri")
        alertPhone = source.getStringExtra("phone")
    }

    override fun onDestroy() {
        if (isFinishing) {
            // Closing the alert also stops the ringing
            AlarmService.stop(this)
        }
        super.onDestroy()
    }

}

@Composable
fun AlarmScreen(
    title: String,
    notes: String = "",
    accent: Color = GoldAccent,
    icon: String = "",
    headerText: String,
    dismissText: String,
    snoozeText: String = "",
    onSnooze: () -> Unit = {},
    isQuarter: Boolean,
    onDismiss: () -> Unit
) {
    if (isQuarter) {
        val screenHeight = LocalConfiguration.current.screenHeightDp
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(DarkBackground)
                    .border(1.5.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .heightIn(min = (screenHeight * 0.25f).dp)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (icon.isNotBlank()) {
                        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                        Box(
                            modifier = Modifier
                                .align(if (isRtl) Alignment.CenterEnd else Alignment.CenterStart)
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.2f))
                                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Text(icon, fontSize = 24.sp) }
                    }
                    Text(
                        text = headerText,
                        color = accent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Text(
                    text = title,
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                if (notes.isNotBlank()) {
                    Text(
                        text = notes,
                        color = TextPrimaryDark.copy(alpha = 0.75f),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (snoozeText.isNotBlank()) {
                        OutlinedButton(
                            onClick = onSnooze,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, accent),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(snoozeText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = DarkBackground),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(dismissText, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.22f), DarkBackground, DarkBackground)
                    )
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (icon.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f))
                            .border(2.dp, accent.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Text(icon, fontSize = 46.sp) }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = headerText,
                    color = accent,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = title,
                    color = TextPrimaryDark,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = notes,
                        color = TextPrimaryDark.copy(alpha = 0.75f),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                if (snoozeText.isNotBlank()) {
                    OutlinedButton(
                        onClick = onSnooze,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, accent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(snoozeText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = DarkBackground),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(dismissText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}