package com.prc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks which jobs the user has opened, most-recent-first, capped at 20.
 * In-memory for now (same tradeoff as SavedRepository); persisted later.
 */
object RecentlyViewedRepository {
    private const val MAX_ENTRIES = 20
    private val _ids = MutableStateFlow<List<Int>>(emptyList())
    val ids: StateFlow<List<Int>> = _ids.asStateFlow()

    /** Record a view; moves the job to the front and de-dupes. */
    fun record(jobId: Int) {
        if (jobId <= 0) return
        _ids.value = (listOf(jobId) + _ids.value.filter { it != jobId }).take(MAX_ENTRIES)
        PersistedState.persistViewed(_ids.value)
    }

    fun clear() {
        _ids.value = emptyList()
        PersistedState.persistViewed(_ids.value)
    }

    /** Restore persisted state at startup (order already most-recent-first). */
    fun restoreAll(ids: List<Int>) {
        _ids.value = ids
    }
}
