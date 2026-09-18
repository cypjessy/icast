package com.prc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AppNotification(
    val id: Int,
    val recipientContact: String,   // whose bell this rings
    val title: String,
    val body: String,
    val timeAgo: String = "Just now",
    val type: String                // applicant | accepted | rejected
)

/**
 * In-memory notification center. Events are pushed by JobRepository actions
 * (apply, decide) and read per signed-in user. Swap for push notifications
 * + a backend later; screens only see this surface.
 */
object NotificationRepository {

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _readBy = MutableStateFlow<Set<String>>(emptySet())  // "contact#id" keys
    val readBy: StateFlow<Set<String>> = _readBy.asStateFlow()

    private var nextId = 1

    // Single source of truth for read-tracking keys (contact + notification id).
    fun readKey(contact: String?, id: Int): String = "${contact ?: ""}#$id"

    fun forUser(contact: String?): List<AppNotification> =
        _notifications.value.filter { it.recipientContact == contact }

    fun unreadCount(contact: String?): Int =
        if (contact == null) 0
        else _notifications.value.count {
            it.recipientContact == contact && readKey(contact, it.id) !in _readBy.value
        }

    fun markAllRead(contact: String?) {
        if (contact == null) return
        val mine = _notifications.value.filter { it.recipientContact == contact }
        _readBy.value = _readBy.value + mine.map { readKey(contact, it.id) }
    }

    /** Internal hook for workers (e.g. deadline expiry reminders). */
    fun pushSystem(recipient: String, title: String, body: String, type: String) {
        push(recipient, title, body, type)
    }

    private fun push(recipient: String, title: String, body: String, type: String) {
        _notifications.value = listOf(
            AppNotification(nextId++, recipient, title, body, "Just now", type)
        ) + _notifications.value
    }

    /** Weekly digest notification — lands in the bell as a digest item. */
    fun pushDigest(recipient: String, body: String) {
        push(
            recipient = recipient,
            title = "Weekly job digest",
            body = body,
            type = "digest"
        )
    }

    /** A saved search alert was matched by a newly-posted job. */
    fun pushAlertMatch(recipient: String, alert: JobAlert, job: Job) {
        push(
            recipient = recipient,
            title = "Job alert: ${alert.keyword.ifBlank { alert.category }}",
            body = "A new job matching your alert was posted: \"${job.title}\" at ${job.postedBy} · ${job.location}",
            type = "alert"
        )
    }

    // ---- events fired by JobRepository ----

    fun onNewApplication(job: Job, applicantName: String) {
        if (job.posterContact.isBlank()) return   // sample jobs have no live poster
        push(
            recipient = job.posterContact,
            title = "New applicant",
            body = "$applicantName applied for \"${job.title}\"",
            type = "applicant"
        )
    }

    /** The user's Pro subscription payment was verified. */
    fun pushProVerified(recipientContact: String) {
        if (recipientContact.isBlank()) return
        push(
            recipient = recipientContact,
            title = "Pro activated ✨",
            body = "Your Pro subscription is active. Enjoy unlimited premium applications!",
            type = "subscription"
        )
    }

    /** The owner's boost payment was verified and their job is now featured. */
    fun onBoostVerified(recipientContact: String, jobTitle: String, daysLeft: String) {
        if (recipientContact.isBlank()) return
        push(
            recipient = recipientContact,
            title = "Job featured ⭐",
            body = "Payment confirmed — \"$jobTitle\" is now featured for $daysLeft. It will stay at the top of the home feed.",
            type = "boost"
        )
    }

    // ==== boost expiry reminders ====

    // Dedupe keys "contact#jobId#dayBucket" so a reminder fires at most once
    // per day per job, and resets if the boost is extended/renewed.
    private val expiryNotified = mutableSetOf<String>()

    /**
     * Watch the live job list and notify the owner of any featured job whose
     * boost expires within 24 hours. Call once at app start; runs for the
     * lifetime of the process and re-evaluates on every job snapshot.
     */
    fun startBoostExpiryWatcher(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            JobRepository.jobs.collect { jobs ->
                val contact = AuthRepository.currentUser.value?.contact ?: return@collect
                val now = System.currentTimeMillis()
                val dayBucket = now / 86_400_000L
                jobs.filter { it.isFeatured && it.posterContact == contact }
                    .forEach { job ->
                        val msLeft = job.featuredUntil - now
                        if (msLeft in 1..86_400_000L) {
                            val key = "$contact#${job.id}#$dayBucket"
                            if (key !in expiryNotified) {
                                expiryNotified.add(key)
                                val hoursLeft = (msLeft / 3_600_000L).coerceAtLeast(1)
                                push(
                                    recipient = contact,
                                    title = "Boost expiring soon ⏰",
                                    body = "\"${job.title}\" stays featured for only ${hoursLeft}h more. Extend the boost to keep it at the top of the feed.",
                                    type = "boost"
                                )
                            }
                        }
                    }
            }
        }
    }

    // ==== Pro subscription expiry reminders ====

    // Dedupe key "contact#dayBucket" — one reminder per day, and it re-arms
    // on renewal (new expiry => still in window only again after renewal).
    private var proExpiryNotifiedDay = 0L

    /**
     * Watch the signed-in user's Pro expiry (`users/{uid}.proUntil`) and ring
     * the bell once per day while it's within 3 days of expiring. Call once
     * at app start next to [startBoostExpiryWatcher].
     */
    fun startProExpiryWatcher(scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch(kotlinx.coroutines.Dispatchers.Default) {
            // Poll daily instead of streaming: proUntil changes rarely and
            // Firestore listeners on the user doc are unnecessary load.
            while (true) {
                try {
                    val uid = AuthRepository.currentUid()
                    if (uid != null) {
                        val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(uid).get().await()
                        val proUntil = snap.getTimestamp("proUntil")?.toDate()?.time ?: 0L
                        val msLeft = proUntil - System.currentTimeMillis()
                        if (msLeft in 1..(3 * 86_400_000L)) {
                            val dayBucket = System.currentTimeMillis() / 86_400_000L
                            if (proExpiryNotifiedDay != dayBucket) {
                                proExpiryNotifiedDay = dayBucket
                                val daysLeft = ((msLeft + 86_399_999L) / 86_400_000L).toInt()
                                val contact = AuthRepository.currentUser.value?.contact ?: ""
                                if (contact.isNotBlank()) {
                                    push(
                                        recipient = contact,
                                        title = "Pro expiring soon ⏳",
                                        body = "Your Pro subscription ends in $daysLeft day${if (daysLeft == 1) "" else "s"}. Renew now to keep unlimited premium applications.",
                                        type = "subscription"
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Offline / Firestore unavailable — retry on the next tick.
                }
                kotlinx.coroutines.delay(6 * 3_600_000L)   // re-check every 6h
            }
        }
    }

    fun onDecision(job: Job, applicantContact: String, applicantName: String, accepted: Boolean) {
        push(
            recipient = applicantContact,
            title = if (accepted) "Application accepted 🎉" else "Application rejected",
            body = if (accepted)
                "${job.postedBy} accepted your application for \"${job.title}\". Contact them through the job page."
            else
                "${job.postedBy} moved on with other applicants for \"${job.title}\".",
            type = if (accepted) "accepted" else "rejected"
        )
    }
}
