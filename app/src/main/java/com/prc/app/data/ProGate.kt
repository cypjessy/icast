package com.prc.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Free-tier enforcement: caps for non-Pro users, evaluated against live
 * Firestore data. Pro subscribers (proUntil in the future) bypass all caps.
 */
object ProGate {

    const val FREE_DAILY_APPLICATIONS = 5
    const val FREE_MAX_ALERTS = 1

    /** True when the signed-in user has an active Pro subscription. */
    suspend fun isPro(uid: String?): Boolean {
        if (uid == null) return false
        return try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            val until = snap.getTimestamp("proUntil")?.toDate()?.time ?: 0L
            until > System.currentTimeMillis()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * How many applications the user has already sent today (midnight-based).
     * Counts all applications with the given contact; simple and honest.
     */
    suspend fun applicationsToday(contact: String): Int {
        if (contact.isBlank()) return 0
        return try {
            val dayStart = System.currentTimeMillis() - (System.currentTimeMillis() % 86_400_000L)
            val snap = FirebaseFirestore.getInstance()
                .collection("applications")
                .whereEqualTo("applicantContact", contact)
                .get()
                .await()
            snap.documents.count { doc ->
                val at = doc.getLong("appliedAt")
                    ?: doc.getTimestamp("appliedAt")?.toDate()?.time ?: 0L
                at >= dayStart
            }
        } catch (_: Exception) {
            0   // fail open: never block a genuine application on a network error
        }
    }
}
