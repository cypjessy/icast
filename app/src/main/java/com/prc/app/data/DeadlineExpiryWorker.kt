package com.prc.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Auto-expiry for provider jobs whose application deadline has passed.
 *
 * - If the AI/admin set a deadline (deadlineDays), the job auto-closes when
 *   postedAtMillis + deadlineDays elapses.
 * - If no deadline was written, a two-week default applies — and the admin
 *   is reminded once to delete the job manually if it should stay longer.
 *
 * Scheduled from MainActivity; runs roughly every 6 hours even when the
 * app is backgrounded.
 */
object DeadlineExpiryScheduler {
    private const val WORK_NAME = "prc_deadline_expiry"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<DeadlineExpiryWorker>(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

class DeadlineExpiryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            val db = FirebaseFirestore.getInstance()
            val now = System.currentTimeMillis()

            // 1) Auto-close: deadline reached on provider jobs still live.
            val jobsSnap = db.collection("jobs")
                .whereEqualTo("closed", false)
                .whereEqualTo("draft", false)
                .whereEqualTo("source", Job.SOURCE_PROVIDER)
                .get()
                .await()

            var closed = 0
            var reminded = 0
            jobsSnap.documents.forEach { doc ->
                val postedAt = doc.getLong("postedAtMillis") ?: return@forEach
                val deadline = doc.getLong("deadlineDays") ?: return@forEach
                if (deadline <= 0) return@forEach
                if (now >= postedAt + deadline * 86_400_000L) {
                    doc.reference.update("closed", true)
                    closed++
                }
            }

            // 2) Two-week default reminder: jobs with NO written deadline that
            //    have now passed 14 days. Remind the admin once to delete
            //    manually (we never auto-close an open-ended posting).
            val adminContact = "admin@prc.app"   // notification route for the bell
            jobsSnap.documents.forEach { doc ->
                val postedAt = doc.getLong("postedAtMillis") ?: return@forEach
                val deadline = doc.getLong("deadlineDays")   // null = not stated
                val alreadyReminded = doc.getBoolean("expiryReminderSent") == true
                if (deadline == null && !alreadyReminded &&
                    now >= postedAt + 14 * 86_400_000L
                ) {
                    doc.reference.update("expiryReminderSent", true)
                    reminded++
                    NotificationRepository.pushSystem(
                        recipient = adminContact,
                        title = "Job past 2 weeks — review",
                        body = "\"${doc.getString("title") ?: "A job"}\" had no deadline and has been live for 2+ weeks. Delete it from the portal if it should no longer appear.",
                        type = "admin"
                    )
                }
            }

            Log.d("PRC-Expiry", "deadline sweep: closed=$closed reminded=$reminded")
            return Result.success()
        } catch (e: Exception) {
            Log.w("PRC-Expiry", "deadline sweep failed: ${e.message}")
            return Result.retry()
        }
    }
}
