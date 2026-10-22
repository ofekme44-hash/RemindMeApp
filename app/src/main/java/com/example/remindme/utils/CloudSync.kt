package com.example.remindme.utils

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.remindme.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Google sign-in plus cloud backup of the reminders (and alarm settings) in the user's account.
 * Everything is stored in Firestore under users/{uid}. The local copy stays on the phone, so alarms
 * keep ringing without internet and are synced whenever a connection is available.
 */
object CloudSync {
    // While cloud data is being applied locally we must not push it straight back
    @Volatile
    private var applying = false

    /** The technical reason of the last failure, shown on the sign-in screen to help find problems. */
    @Volatile
    var lastError: String = ""

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()

    fun isSignedIn(): Boolean = auth.currentUser != null

    fun email(): String? = auth.currentUser?.email

    /** Shows the Google account chooser and signs in to Firebase with the chosen account. */
    suspend fun signIn(activity: Activity): Result<Unit> {
        return try {
            val option = GetSignInWithGoogleOption.Builder(
                activity.getString(R.string.default_web_client_id)
            ).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
            val result = CredentialManager.create(activity).getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "signIn: " + e.javaClass.simpleName + " - " + (e.message ?: "")
            Result.failure(e)
        }
    }

    private fun snapshot(context: Context): Map<String, Any> = mapOf(
        "reminders" to ReminderStorage.getRemindersJson(context),
        "history" to ReminderStorage.getHistoryJson(context),
        "alarmWindowMode" to ReminderStorage.getAlarmWindowMode(context),
        "ringDurationSec" to ReminderStorage.getRingDurationSec(context),
        "snoozeCount" to ReminderStorage.getSnoozeCount(context),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    /** Saves the current local data to the cloud in the background. Safe to call at any time. */
    fun push(context: Context) {
        if (applying) return
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid).set(snapshot(context.applicationContext))
        } catch (_: Exception) {
        }
    }

    private suspend fun pushAndWait(context: Context) {
        val uid = auth.currentUser?.uid ?: return
        try {
            withTimeoutOrNull(4000) {
                db.collection("users").document(uid).set(snapshot(context.applicationContext)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    /**
     * Loads the account's data into the phone. If the account has no data yet, the data that is
     * already on the phone is uploaded instead. Returns false when the cloud could not be reached.
     */
    suspend fun pull(context: Context): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val appContext = context.applicationContext
        return try {
            val snap = db.collection("users").document(uid).get().await()
            if (snap.exists()) {
                var historyMissing = false
                applying = true
                try {
                    ReminderStorage.replaceAllFromCloud(appContext, snap.getString("reminders") ?: "[]")
                    val cloudHistory = snap.getString("history")
                    if (cloudHistory != null) {
                        ReminderStorage.replaceHistoryFromCloud(appContext, cloudHistory)
                    } else {
                        historyMissing = true
                    }
                    snap.getString("alarmWindowMode")?.let { ReminderStorage.saveAlarmWindowMode(appContext, it) }
                    val duration = snap.getLong("ringDurationSec")
                    val count = snap.getLong("snoozeCount")
                    if (duration != null && count != null) {
                        ReminderStorage.saveRingSettings(appContext, duration.toInt(), count.toInt())
                    }
                } finally {
                    applying = false
                }
                // An account saved before the history existed: upload what is on this phone
                if (historyMissing) push(appContext)
            } else {
                pushAndWait(appContext)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            lastError = "cloud: " + e.javaClass.simpleName + " - " + (e.message ?: "")
            false
        }
    }

    /** Backs up first, then signs out and removes this account's data from the phone. */
    suspend fun signOutAndClear(context: Context) {
        val appContext = context.applicationContext
        pushAndWait(appContext)
        auth.signOut()
        ReminderStorage.clearAllLocal(appContext)
        clearGoogleCredential(appContext)
    }

    /** Signs out only (used when a sign-in could not finish loading the data). */
    suspend fun signOutOnly(context: Context) {
        auth.signOut()
        clearGoogleCredential(context.applicationContext)
    }

    private suspend fun clearGoogleCredential(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}