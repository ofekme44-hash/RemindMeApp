package com.example.remindme.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remindme.utils.SupportRepo
import com.example.remindme.utils.SupportTicket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private fun trs(lang: String, he: String, en: String, ru: String = en, fr: String = en): String =
    when (lang) {
        "he" -> he
        "ru" -> ru
        "fr" -> fr
        else -> en
    }

private data class SupportCategory(
    val key: String,
    val icon: String,
    val he: String,
    val en: String,
    val ru: String,
    val fr: String
)

private val supportCategories = listOf(
    SupportCategory("BUG", "🐞", "תקלה", "Problem", "Проблема", "Problème"),
    SupportCategory("ALARM", "⏰", "צלצול והתראות", "Alarms", "Сигналы", "Alarmes"),
    SupportCategory("ACCOUNT", "🔐", "חשבון והתחברות", "Account", "Аккаунт", "Compte"),
    SupportCategory("IDEA", "💡", "הצעה לשיפור", "Idea", "Идея", "Idée"),
    SupportCategory("QUESTION", "❓", "שאלה", "Question", "Вопрос", "Question"),
    SupportCategory("OTHER", "💬", "אחר", "Other", "Другое", "Autre")
)

private fun categoryOf(key: String): SupportCategory? = supportCategories.firstOrNull { it.key == key }

private fun SupportCategory.label(lang: String) = trs(lang, he, en, ru, fr)

private val dateFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)

private fun formatTicketDate(millis: Long): String =
    if (millis <= 0L) "" else dateFormatter.format(Date(millis))

@Composable
private fun SupportChip(label: String, selected: Boolean, textPrimary: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(GoldBrush) else Modifier.background(GoldAccent.copy(alpha = 0.10f)))
            .border(1.dp, GoldAccent.copy(alpha = if (selected) 0f else 0.35f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF1B1608) else textPrimary
        )
    }
}

@Composable
private fun TicketCard(
    ticket: SupportTicket,
    lang: String,
    isDarkMode: Boolean,
    showEmail: Boolean,
    onClick: (() -> Unit)? = null
) {
    val surface = if (isDarkMode) SurfaceCardDark else SurfaceCardLight
    val textPrimary = if (isDarkMode) TextPrimaryDark else TextPrimaryLight
    val textSecondary = if (isDarkMode) TextSecondaryDark else TextSecondaryLight
    val category = categoryOf(ticket.category)
    val replied = ticket.status == "REPLIED"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(surface)
            .border(1.dp, GoldAccent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(GoldAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Text(category?.icon ?: "💬", fontSize = 18.sp) }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ticket.subject,
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "#" + ticket.id.take(6).uppercase() + "  ·  " + (category?.label(lang) ?: "") + "  ·  " + formatTicketDate(ticket.createdAt),
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (replied) Color(0xFF66BB6A).copy(alpha = 0.2f) else GoldAccent.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    if (replied) "✅ " + trs(lang, "נענתה", "Answered", "Отвечено", "Répondu")
                    else "⏳ " + trs(lang, "בטיפול", "Waiting", "Ожидает", "En attente"),
                    color = textPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (showEmail && ticket.email.isNotBlank()) {
            Text("✉️ " + ticket.email, color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(ticket.details, color = textPrimary, fontSize = 14.sp)
        if (replied && ticket.reply.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(GoldAccent.copy(alpha = 0.12f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "🛡️ " + trs(lang, "תשובת צוות התמיכה של RemindMe", "RemindMe Support Team reply", "Ответ службы поддержки RemindMe", "Réponse de l'équipe d'assistance RemindMe") +
                            "  " + formatTicketDate(ticket.repliedAt),
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(ticket.reply, color = textPrimary, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun fieldColors(textPrimary: Color, textSecondary: Color) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GoldAccent,
    unfocusedBorderColor = GoldAccent.copy(alpha = 0.35f),
    focusedLabelColor = GoldAccent,
    unfocusedLabelColor = textSecondary,
    focusedTextColor = textPrimary,
    unfocusedTextColor = textPrimary,
    cursorColor = GoldAccent
)

/** Write to support, and read the answers. */
@Composable
fun SupportScreen(isDarkMode: Boolean, currentLang: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textPrimary = if (isDarkMode) TextPrimaryDark else TextPrimaryLight
    val textSecondary = if (isDarkMode) TextSecondaryDark else TextSecondaryLight
    val surface = if (isDarkMode) SurfaceCardDark else SurfaceCardLight

    BackHandler { onBack() }

    var tab by remember { mutableStateOf(0) }
    var category by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var tickets by remember { mutableStateOf<List<SupportTicket>?>(null) }
    var loadingTickets by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var lastSentAt by remember { mutableStateOf(0L) }

    LaunchedEffect(tab, reloadKey) {
        if (tab == 1) {
            loadingTickets = true
            tickets = SupportRepo.myTickets()
            loadingTickets = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(isDarkMode)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = GoldAccent),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("➡️ " + trs(currentLang, "חזרה", "Back", "Назад", "Retour"), fontWeight = FontWeight.Bold, maxLines = 1)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(GoldAccent.copy(alpha = 0.10f))
                .border(1.dp, GoldAccent.copy(alpha = 0.40f), RoundedCornerShape(22.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(GoldBrush),
                contentAlignment = Alignment.Center
            ) { Text("🛡️", fontSize = 24.sp) }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    trs(currentLang, "מרכז התמיכה", "Support Center", "Центр поддержки", "Centre d'assistance"),
                    color = textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    trs(
                        currentLang,
                        "צוות RemindMe ישמח לסייע. פנייתך תטופל ותקבל מענה כאן באפליקציה.",
                        "The RemindMe team is glad to help. Your request will be handled and answered here in the app.",
                        "Команда RemindMe поможет вам. Ответ на обращение вы получите здесь, в приложении.",
                        "L'équipe RemindMe est à votre service. Vous recevrez une réponse ici, dans l'application."
                    ),
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportChip("✏️ " + trs(currentLang, "פנייה חדשה", "New request", "Новое обращение", "Nouvelle demande"), tab == 0, textPrimary) { tab = 0 }
            SupportChip("📨 " + trs(currentLang, "הפניות שלי", "My requests", "Мои обращения", "Mes demandes"), tab == 1, textPrimary) { tab = 1 }
        }
        Spacer(modifier = Modifier.height(14.dp))

        if (tab == 0) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "🏷️ " + trs(currentLang, "סוג הפנייה", "Support category", "Категория", "Catégorie"),
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                ) {
                    supportCategories.forEach { c ->
                        SupportChip("${c.icon} ${c.label(currentLang)}", category == c.key, textPrimary) { category = c.key }
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { if (it.length <= 120) subject = it },
                    label = { Text(trs(currentLang, "נושא הפנייה", "Subject", "С чем нужна помощь?", "Sur quoi avez-vous besoin d'aide ?"), color = textSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors(textPrimary, textSecondary),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = details,
                    onValueChange = { if (it.length <= 2000) details = it },
                    label = { Text(trs(currentLang, "פירוט הפנייה", "Details", "Подробности", "Détails"), color = textSecondary) },
                    minLines = 5,
                    maxLines = 9,
                    shape = RoundedCornerShape(18.dp),
                    colors = fieldColors(textPrimary, textSecondary),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "ℹ️ " + trs(
                        currentLang,
                        "הפנייה נשלחת באופן מאובטח דרך האפליקציה בלבד, יחד עם כתובת האימייל של חשבונך, כדי שנוכל להשיב לך. פרטי הקשר של צוות התמיכה אינם מוצגים.",
                        "Your message is sent only through the app, with your account address, so we can answer you.",
                        "Сообщение отправляется только через приложение, с адресом вашего аккаунта, чтобы мы могли ответить.",
                        "Votre message est envoyé uniquement via l'application, avec l'adresse de votre compte, pour que nous puissions répondre."
                    ),
                    color = textSecondary,
                    fontSize = 12.sp
                )

                statusMessage?.let {
                    Text(
                        it,
                        color = if (statusIsError) MaterialTheme.colorScheme.error else Color(0xFF66BB6A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val needCategory = trs(currentLang, "נא לבחור סוג פנייה", "Choose a category", "Выберите категорию", "Choisissez une catégorie")
                val needSubject = trs(currentLang, "נא למלא את נושא הפנייה", "Write what you need help with", "Напишите, с чем нужна помощь", "Écrivez sur quoi vous avez besoin d'aide")
                val needDetails = trs(currentLang, "נא להוסיף פירוט (לפחות 10 תווים)", "Add some details (at least 10 characters)", "Добавьте подробности (минимум 10 символов)", "Ajoutez des détails (au moins 10 caractères)")
                val waitText = trs(currentLang, "אפשר לשלוח פנייה חדשה בעוד כמה שניות", "Please wait a few seconds before sending again", "Подождите несколько секунд", "Patientez quelques secondes")
                val okText = trs(currentLang, "✅ פנייתך התקבלה. המענה יופיע בלשונית \"הפניות שלי\"", "✅ Sent! The answer will appear in \"My requests\"", "✅ Отправлено! Ответ появится в «Мои обращения»", "✅ Envoyé ! La réponse apparaîtra dans « Mes demandes »")
                val failText = trs(currentLang, "השליחה נכשלה. נא לבדוק את החיבור לאינטרנט ולנסות שוב", "Sending failed. Check your internet connection and try again", "Не удалось отправить. Проверьте интернет и попробуйте снова", "Échec de l'envoi. Vérifiez votre connexion et réessayez")

                if (sending) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GoldAccent)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(GoldBrush)
                            .clickable {
                                val problem = when {
                                    category.isBlank() -> needCategory
                                    subject.isBlank() -> needSubject
                                    details.trim().length < 10 -> needDetails
                                    System.currentTimeMillis() - lastSentAt < 20_000L -> waitText
                                    else -> null
                                }
                                if (problem != null) {
                                    statusMessage = problem
                                    statusIsError = true
                                } else {
                                    statusMessage = null
                                    sending = true
                                    scope.launch {
                                        val label = categoryOf(category)?.label("en") ?: category
                                        val result = SupportRepo.send(category, label, subject.trim(), details.trim(), currentLang)
                                        sending = false
                                        if (result.isSuccess) {
                                            lastSentAt = System.currentTimeMillis()
                                            category = ""
                                            subject = ""
                                            details = ""
                                            statusMessage = okText
                                            statusIsError = false
                                            reloadKey++
                                        } else {
                                            statusMessage = failText
                                            statusIsError = true
                                        }
                                    }
                                }
                            }
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📨 " + trs(currentLang, "שליחת הפנייה", "Send to support", "Отправить в поддержку", "Envoyer au support"),
                            color = Color(0xFF1B1608),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            val list = tickets
            when {
                loadingTickets && list == null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GoldAccent)
                }
                list == null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        trs(currentLang, "לא הצלחנו לטעון. בדוק חיבור לאינטרנט", "Couldn't load. Check your internet connection", "Не удалось загрузить. Проверьте интернет", "Chargement impossible. Vérifiez votre connexion"),
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                list.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        trs(currentLang, "טרם נשלחו פניות מחשבך", "You haven't sent any requests yet", "Вы ещё не отправляли обращений", "Vous n'avez pas encore envoyé de demande"),
                        color = textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(list, key = { it.id }) { t ->
                        TicketCard(t, currentLang, isDarkMode, showEmail = false)
                    }
                }
            }
        }
    }
}

/** The owner's inbox: every request, with the sender's e-mail, and a way to answer. */
@Composable
fun SupportAdminScreen(isDarkMode: Boolean, currentLang: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val textPrimary = if (isDarkMode) TextPrimaryDark else TextPrimaryLight
    val textSecondary = if (isDarkMode) TextSecondaryDark else TextSecondaryLight
    val surface = if (isDarkMode) SurfaceCardDark else SurfaceCardLight

    BackHandler { onBack() }

    var tickets by remember { mutableStateOf<List<SupportTicket>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<SupportTicket?>(null) }
    var filter by remember { mutableStateOf("ALL") }

    LaunchedEffect(reloadKey) {
        loading = true
        tickets = SupportRepo.allTickets()
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .premiumBackground(isDarkMode)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = GoldAccent),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("➡️ " + trs(currentLang, "חזרה", "Back", "Назад", "Retour"), fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                onClick = { reloadKey++ },
                colors = ButtonDefaults.buttonColors(containerColor = surface, contentColor = GoldAccent),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, GoldAccent.copy(alpha = 0.6f)),
                modifier = Modifier.weight(1f)
            ) {
                Text("🔄 " + trs(currentLang, "רענן", "Refresh", "Обновить", "Actualiser"), fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "📥 " + trs(currentLang, "פניות תמיכה", "Support requests", "Обращения", "Demandes d'assistance"),
            color = textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SupportChip(trs(currentLang, "הכל", "All", "Все", "Tout"), filter == "ALL", textPrimary) { filter = "ALL" }
            SupportChip("⏳ " + trs(currentLang, "ממתינות", "Waiting", "Ожидают", "En attente"), filter == "OPEN", textPrimary) { filter = "OPEN" }
            SupportChip("✅ " + trs(currentLang, "נענו", "Answered", "Отвечены", "Répondues"), filter == "REPLIED", textPrimary) { filter = "REPLIED" }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val list = tickets?.filter { filter == "ALL" || it.status == filter }
        when {
            loading && tickets == null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GoldAccent)
            }
            list == null -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    trs(currentLang, "לא הצלחנו לטעון (בדוק הרשאות מנהל וחיבור)", "Couldn't load (check admin setup and connection)", "Не удалось загрузить (проверьте права администратора)", "Chargement impossible (vérifiez les droits admin)"),
                    color = textSecondary,
                    textAlign = TextAlign.Center
                )
            }
            list.isEmpty() -> Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(trs(currentLang, "אין פניות", "No requests", "Нет обращений", "Aucune demande"), color = textSecondary)
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(list, key = { it.id }) { t ->
                    TicketCard(t, currentLang, isDarkMode, showEmail = true, onClick = { open = t })
                }
            }
        }
    }

    open?.let { ticket ->
        var replyText by remember(ticket.id) { mutableStateOf(ticket.reply) }
        var working by remember(ticket.id) { mutableStateOf(false) }
        var confirmDelete by remember(ticket.id) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!working) open = null },
            containerColor = surface,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text(ticket.subject, color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text("✉️ " + ticket.email, color = GoldAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(ticket.details, color = textPrimary, fontSize = 14.sp)
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { if (it.length <= 2000) replyText = it },
                        label = { Text(trs(currentLang, "תשובת צוות התמיכה", "Your reply", "Ваш ответ", "Votre réponse"), color = textSecondary) },
                        minLines = 3,
                        maxLines = 8,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors(textPrimary, textSecondary),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (confirmDelete) {
                        Text(
                            trs(currentLang, "למחוק את הפנייה לצמיתות? לחץ שוב על 🗑️", "Delete this request for good? Tap 🗑️ again", "Удалить обращение навсегда? Нажмите 🗑️ ещё раз", "Supprimer définitivement ? Touchez 🗑️ à nouveau"),
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(GoldBrush)
                        .clickable {
                            if (!working && replyText.isNotBlank()) {
                                working = true
                                scope.launch {
                                    val ok = SupportRepo.reply(ticket.id, replyText.trim())
                                    working = false
                                    if (ok) {
                                        open = null
                                        reloadKey++
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 22.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (working) "…" else "📤 " + trs(currentLang, "שליחת תשובה", "Send reply", "Отправить ответ", "Envoyer"),
                        color = Color(0xFF1B1608),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        if (!confirmDelete) {
                            confirmDelete = true
                        } else if (!working) {
                            working = true
                            scope.launch {
                                val ok = SupportRepo.delete(ticket.id)
                                working = false
                                if (ok) {
                                    open = null
                                    reloadKey++
                                }
                            }
                        }
                    }) { Text("🗑️", fontSize = 18.sp) }
                    TextButton(onClick = { if (!working) open = null }) {
                        Text(trs(currentLang, "סגור", "Close", "Закрыть", "Fermer"), color = textSecondary)
                    }
                }
            }
        )
    }
}