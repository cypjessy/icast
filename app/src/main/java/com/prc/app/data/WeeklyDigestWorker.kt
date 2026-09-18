package com.prc.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.prc.app.MainActivity
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Weekly digest: fetches jobs posted in the last 7 days, matches them against
 * the signed-in user's skills, and notifies the user with a summary.
 * Delivers through both the Android notification shade and the in-app bell.
 */
class WeeklyDigestWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val contact = AuthRepository.currentUser.value?.contact ?: return Result.success()
        val skills = AuthRepository.currentUser.value?.skills.orEmpty()
            .map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (skills.isEmpty()) {
            Log.d("PRC-Digest", "no profile skills — digest skipped")
            return Result.success()
        }

        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val newJobs = try {
            FirebaseFirestore.getInstance().collection("jobs")
                .whereGreaterThan("postedAtMillis", weekAgo)
                .get().await()
                .documents.mapNotNull { JobRepository.parseRemoteJob(it) }
                .filter { !it.draft && !it.closed }
        } catch (e: Exception) {
            Log.w("PRC-Digest", "fetch failed: ${e.message}")
            return Result.retry()
        }

        // skill overlap: keep jobs sharing at least one skill token
        val matches = newJobs.filter { job ->
            val targets = (job.skills + job.requirements).map { it.trim().lowercase() }
            targets.any { target -> skills.any { target.contains(it) || it.contains(target) } }
        }

        if (matches.isEmpty()) {
            Log.d("PRC-Digest", "${newJobs.size} new jobs, none match skills")
            return Result.success()
        }

        val titles = matches.take(3).map { "\"${it.title}\"" }
        val more = matches.size - titles.size
        val body = buildString {
            append("${matches.size} new job${if (matches.size == 1) "" else "s"} match your skills: ")
            append(titles.joinToString(", "))
            if (more > 0) append(" +$more more")
        }

        // in-app bell
        NotificationRepository.pushDigest(contact, body)

        // saved-search alerts: notify separately for alert matches
        val alertMatches = JobAlertRepository.matchingNewJobs(newJobs)
        if (alertMatches.isNotEmpty()) {
            NotificationRepository.pushDigest(contact, "Job alert: ${alertMatches.size} new job${if (alertMatches.size == 1) "" else "s"} match your saved search — ${alertMatches.take(2).joinToString { "\"${it.title}\"" }}")
        }

        // system shade
        showSystemNotification(context, body)
        return Result.success()
    }

    private fun showSystemNotification(context: Context, body: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("PRC-Digest", "POST_NOTIFICATIONS not granted — system notification skipped")
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Weekly job digest", NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "New jobs matching your skills" }
            )
        }
        val intent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Your weekly job digest")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "weekly_digest"
        private const val NOTIFICATION_ID = 2001
        private const val WORK_NAME = "weekly_job_digest"

        /** Schedule every 7 days; keep existing schedule if already enrolled. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.MINUTES)   // first digest shortly after install
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
