package com.example.remindme.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.remindme.utils.CloudSync
import com.example.remindme.utils.LanguageManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun LoginScreen(onSignedIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun text(key: String) = LanguageManager.getString(context, key)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(true)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(124.dp)
                    .clip(CircleShape)
                    .background(GoldAccent.copy(alpha = 0.12f))
                    .border(2.dp, GoldAccent.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🔔", fontSize = 58.sp)
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "RemindMe",
                style = androidx.compose.ui.text.TextStyle(
                    brush = GoldBrush,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = text("login_title"),
                color = GoldAccent,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text("login_subtitle"),
                color = TextPrimaryDark,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(text = "⏰    ☁️    🎵    🔔", fontSize = 22.sp, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(32.dp))

            if (isLoading) {
                CircularProgressIndicator(color = GoldAccent)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = text("login_loading"), color = TextPrimaryDark, fontSize = 14.sp)
            } else {
                Button(
                    onClick = {
                        val activity = context.findActivity() ?: return@Button
                        scope.launch {
                            isLoading = true
                            errorText = null
                            val signIn = CloudSync.signIn(activity)
                            if (signIn.isSuccess) {
                                // The account's data must be loaded before the app opens,
                                // otherwise an empty phone could overwrite the saved data.
                                val loaded = withTimeoutOrNull(10_000) { CloudSync.pull(context) } == true
                                if (loaded) {
                                    onSignedIn()
                                } else {
                                    if (CloudSync.lastError.isBlank()) CloudSync.lastError = "cloud: timeout"
                                    CloudSync.signOutOnly(context)
                                    isLoading = false
                                    errorText = text("login_error") + "\n\n" + CloudSync.lastError
                                }
                            } else {
                                isLoading = false
                                errorText = if (signIn.exceptionOrNull() is GetCredentialCancellationException) {
                                    text("login_cancelled")
                                } else {
                                    text("login_error") + "\n\n" + CloudSync.lastError
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color(0xFF1B1608)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .background(GoldBrush, RoundedCornerShape(18.dp))
                ) {
                    Text(text("login_button"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            errorText?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}