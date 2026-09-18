package com.prc.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A user's saved search alert: keyword + optional category. */
data class JobAlert(
    val keyword: String,
    val category: String   // "All" or a specific category
)

/**
 * Saved search alerts. Alerts are created from the search screen (the
 * current query + category are captured). The repository watches the live
 * job list: whenever a new job matches a saved alert, the hit is recorded
 * in [triggered] (surfaced as a popup card on the home page) and a
 * notification lands in the bell. Alerts persist via PersistedState.
 */
object JobAlertRepository {
    private val _alerts = MutableStateFlow<List<JobAlert>>(emptyList())
    val alerts: StateFlow<List<JobAlert>> = _alerts.asStateFlow()

    /** Alerts matched by newly-arrived jobs (most recent first). */
    private val _triggered = MutableStateFlow<List<Pair<JobAlert, Job>>>(emptyList())
    val triggered: StateFlow<List<Pair<JobAlert, Job>>> = _triggered.asStateFlow()

    private var observing = false

    /**
     * Watch the live job list; whenever a genuinely new job arrives that
     * matches a saved alert, record the hit and ring the bell. Call once
     * at app start after repositories are hydrated.
     */
    fun startObserving() {
        if (observing) return
        observing = true
        CoroutineScope(Dispatchers.Default).launch {
            var seen = JobRepository.jobs.value.map { it.id }.toSet()
            JobRepository.jobs.collect { jobs ->
                val fresh = jobs.filter { it.id !in seen && !it.draft && !it.closed }
                seen = jobs.map { it.id }.toSet()
                if (fresh.isEmpty() || _alerts.value.isEmpty()) return@collect
                val matches = fresh.mapNotNull { job ->
                    _alerts.value.firstOrNull { alert -> matchesAlert(alert, job) }
                        ?.let { it to job }
                }
                if (matches.isNotEmpty()) {
                    _triggered.value = (matches + _triggered.value)
                        .distinctBy { it.second.id }
                        .take(10)
                    val contact = AuthRepository.currentUser.value?.contact ?: return@collect
                    matches.forEach { (alert, job) ->
                        NotificationRepository.pushAlertMatch(contact, alert, job)
                    }
                }
            }
        }
    }

    /** Dismiss one (or all) triggered alert hits shown on the home page. */
    fun clearTriggered(jobId: Int? = null) {
        _triggered.value = if (jobId == null) emptyList()
        else _triggered.value.filter { it.second.id != jobId }
    }

    /** How many alerts the user currently holds (for free-tier caps). */
    fun alertCount(): Int = _alerts.value.size

    fun hasAlert(keyword: String, category: String): Boolean =
        _alerts.value.any {
            it.keyword.equals(keyword.trim(), ignoreCase = true) && it.category == category
        }

    fun add(keyword: String, category: String) {
        val alert = JobAlert(keyword.trim(), category)
        if (hasAlert(alert.keyword, alert.category)) return
        _alerts.value = listOf(alert) + _alerts.value
        PersistedState.persistAlerts(_alerts.value)
    }

    fun remove(keyword: String, category: String) {
        _alerts.value = _alerts.value.filter {
            !(it.keyword.equals(keyword.trim(), ignoreCase = true) && it.category == category)
        }
        PersistedState.persistAlerts(_alerts.value)
    }

    /** Jobs among [candidates] that match any alert (keyword in title/skills/company, or category). */
    fun matchingNewJobs(candidates: List<Job>): List<Job> =
        candidates.filter { job -> _alerts.value.any { matchesAlert(it, job) } }

    private fun matchesAlert(alert: JobAlert, job: Job): Boolean {
        val catOk = alert.category == "All" || job.category == alert.category
        val kw = alert.keyword.lowercase()
        val kwOk = kw.isBlank() ||
                job.title.lowercase().contains(kw) ||
                job.postedBy.lowercase().contains(kw) ||
                job.category.lowercase().contains(kw) ||
                job.location.lowercase().contains(kw) ||
                job.skills.any { it.lowercase().contains(kw) }
        return catOk && kwOk
    }

    fun restoreAll(list: List<JobAlert>) {
        _alerts.value = list
    }
}
