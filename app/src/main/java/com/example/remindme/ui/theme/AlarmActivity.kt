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
import com.example.remindme.utils.ReminderStorage

class AlarmAlertActivity : ComponentActivity() {

    private var alertTitle by mutableStateOf("")

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

        setContent {
            val direction = if (lang == "he") LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                AlarmScreen(
                    title = alertTitle,
                    headerText = headerText,
                    dismissText = dismissText,
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
    headerText: String,
    dismissText: String,
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
                    .border(1.5.dp, GoldAccent.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .heightIn(min = (screenHeight * 0.25f).dp)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = headerText,
                    color = GoldAccent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = title,
                    color = TextPrimaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Text(dismissText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = headerText,
                    color = GoldAccent,
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

                Spacer(modifier = Modifier.height(40.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = DarkBackground),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text(dismissText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}