package com.prc.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Tiny persistence layer for user-local state that must survive app restarts:
 * saved jobs, recently viewed, dismissed jobs, and home filter toggles.
 * SharedPreferences is enough at this scale (a few hundred ints/booleans);
 * swap for DataStore if this grows.
 */
object PersistedState {
    private const val FILE = "prc_user_state"
    private const val KEY_SAVED = "saved_jobs"
    private const val KEY_VIEWED = "viewed_jobs"
    private const val KEY_DISMISSED = "dismissed_jobs"
    private const val KEY_FILTERS = "home_filters"
    private const val KEY_ALERTS = "job_alerts"

    private lateinit var prefs: SharedPreferences

    /** Call once from MainActivity before any repository is hydrated. */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        hydrate()
    }

    private fun hydrate() {
        // Saved jobs (id -> savedAtMillis, oldest wins order preserved)
        val savedPairs = prefs.getString(KEY_SAVED, null)
            ?.split(",")
            ?.mapNotNull {
                val p = it.split(":")
                val id = p.getOrNull(0)?.toLongOrNull()?.toInt() ?: return@mapNotNull null
                val at = p.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
                id to at
            }
            ?.associate { it.first to it.second }
            ?: emptyMap()
        if (savedPairs.isNotEmpty()) SavedRepository.restoreAll(savedPairs)

        // Recently viewed (most-recent-first order preserved)
        val viewed = prefs.getString(KEY_VIEWED, null)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty()
        if (viewed.isNotEmpty()) RecentlyViewedRepository.restoreAll(viewed)

        // Dismissed jobs
        val dismissed = prefs.getString(KEY_DISMISSED, null)
            ?.split(",")?.mapNotNull { it.toIntOrNull() }.orEmpty().toSet()
        if (dismissed.isNotEmpty()) DismissedJobsRepository.restoreAll(dismissed)

        // Filter toggles: bit0=remote, bit1=verified, bit2=closingSoon
        val bits = prefs.getInt(KEY_FILTERS, 0)
        if (bits != 0) {
            FilterState.remote = bits and 1 != 0
            FilterState.verified = bits and 2 != 0
            FilterState.closingSoon = bits and 4 != 0
        }

        // Job alerts — "keyword|category" pairs
        val alerts = prefs.getString(KEY_ALERTS, null)
            ?.split(";")
            ?.mapNotNull {
                val p = it.split("|")
                val kw = p.getOrNull(0) ?: return@mapNotNull null
                val cat = p.getOrNull(1) ?: "All"
                if (kw.isBlank()) null else JobAlert(kw, cat)
            }
            .orEmpty()
        if (alerts.isNotEmpty()) JobAlertRepository.restoreAll(alerts)
    }

    fun persistSaved(map: Map<Int, Long>) {
        prefs.edit().putString(
            KEY_SAVED, map.entries.joinToString(",") { "${it.key}:${it.value}" }
        ).apply()
    }

    fun persistViewed(ids: List<Int>) {
        prefs.edit().putString(KEY_VIEWED, ids.joinToString(",")).apply()
    }

    fun persistDismissed(ids: Set<Int>) {
        prefs.edit().putString(KEY_DISMISSED, ids.joinToString(",")).apply()
    }

    fun persistFilters(remote: Boolean, verified: Boolean, closingSoon: Boolean) {
        val bits = (if (remote) 1 else 0) or (if (verified) 2 else 0) or (if (closingSoon) 4 else 0)
        prefs.edit().putInt(KEY_FILTERS, bits).apply()
    }

    fun persistAlerts(alerts: List<JobAlert>) {
        prefs.edit().putString(KEY_ALERTS, alerts.joinToString(";") { "${it.keyword}|${it.category}" }).apply()
    }
}

/** Observable holder for the filter toggles so they can persist across restarts. */
object FilterState {
    var remote: Boolean = false
    var verified: Boolean = false
    var closingSoon: Boolean = false
}
