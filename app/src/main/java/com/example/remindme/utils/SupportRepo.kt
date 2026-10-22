package com.example.remindme.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class SupportTicket(
    val id: String,
    val userId: String,
    val email: String,
    val category: String,
    val subject: String,
    val details: String,
    val status: String,      // "OPEN" or "REPLIED"
    val reply: String,
    val createdAt: Long,
    val repliedAt: Long
)

/**
 * Support messages. Every message is saved in Firestore (collection "tickets"), so the owner can read and
 * answer it inside the app, and the user sees the answer in "My requests".
 * The owner's e-mail address is NOT in the app: the e-mail alert is sent through a form service
 * (Web3Forms) that is set up with the owner's address on its own website. Only the access key is here.
 */
object SupportRepo {
    /** Get a free key at https://web3forms.com (enter your e-mail there) and paste it here. */
    private const val WEB3FORMS_KEY = "4d57e9d7-dcd4-44b5-bebe-41edd59331b6"

    private val db get() = FirebaseFirestore.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    private fun DocumentSnapshot.toTicket() = SupportTicket(
        id = id,
        userId = getString("userId").orEmpty(),
        email = getString("email").orEmpty(),
        category = getString("category").orEmpty(),
        subject = getString("subject").orEmpty(),
        details = getString("details").orEmpty(),
        status = getString("status") ?: "OPEN",
        reply = getString("reply").orEmpty(),
        createdAt = getTimestamp("createdAt")?.toDate()?.time ?: 0L,
        repliedAt = getTimestamp("repliedAt")?.toDate()?.time ?: 0L
    )

    suspend fun send(
        category: String,
        categoryLabel: String,
        subject: String,
        details: String,
        lang: String
    ): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("not signed in"))
        return try {
            val data = hashMapOf<String, Any>(
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "category" to category,
                "subject" to subject.take(120),
                "details" to details.take(2000),
                "status" to "OPEN",
                "lang" to lang,
                "createdAt" to FieldValue.serverTimestamp()
            )
            val saved = withTimeoutOrNull(10_000) { db.collection("tickets").add(data).await() }
            if (saved == null) {
                Result.failure(IllegalStateException("timeout"))
            } else {
                notifyOwner(user.email ?: "", categoryLabel, subject.take(120), details.take(2000))
                Result.success(Unit)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Sends the owner an e-mail alert. A failure here never fails the ticket, it is already saved. */
    private suspend fun notifyOwner(userEmail: String, categoryLabel: String, subject: String, details: String) =
        withContext(Dispatchers.IO) {
            if (WEB3FORMS_KEY.startsWith("PUT_")) return@withContext
            try {
                val connection = URL("https://api.web3forms.com/submit").openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "application/json")
                val json = JSONObject().apply {
                    put("access_key", WEB3FORMS_KEY)
                    put("subject", "RemindMe support: [$categoryLabel] $subject")
                    put("from_name", "RemindMe App")
                    put("email", userEmail)
                    put(
                        "message",
                        "From: $userEmail\nCategory: $categoryLabel\nTopic: $subject\n\n$details\n\n" +
                                "-- Answer from inside the app (Support requests screen)."
                    )
                }
                connection.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                connection.responseCode
                connection.disconnect()
            } catch (_: Exception) {
            }
        }

    /** The signed-in user's own requests, newest first. Null when they could not be loaded. */
    suspend fun myTickets(): List<SupportTicket>? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            withTimeoutOrNull(10_000) {
                db.collection("tickets").whereEqualTo("userId", uid).get().await()
                    .documents.map { it.toTicket() }.sortedByDescending { it.createdAt }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** True when this account is listed in the "admins" collection (set up by the owner in the console). */
    suspend fun isAdmin(): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        return try {
            withTimeoutOrNull(8_000) { db.collection("admins").document(uid).get().await().exists() } == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** All requests (owner only), newest first. Null when they could not be loaded. */
    suspend fun allTickets(): List<SupportTicket>? {
        return try {
            withTimeoutOrNull(10_000) {
                db.collection("tickets").get().await().documents.map { it.toTicket() }.sortedByDescending { it.createdAt }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun reply(ticketId: String, text: String): Boolean {
        return try {
            withTimeoutOrNull(10_000) {
                db.collection("tickets").document(ticketId).update(
                    mapOf(
                        "reply" to text.take(2000),
                        "status" to "REPLIED",
                        "repliedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                true
            } == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun delete(ticketId: String): Boolean {
        return try {
            withTimeoutOrNull(10_000) {
                db.collection("tickets").document(ticketId).delete().await()
                true
            } == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
