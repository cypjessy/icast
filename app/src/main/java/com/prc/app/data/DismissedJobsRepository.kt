package com.prc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Jobs the user dismissed from recommendations ("Not interested").
 * Dismissed jobs are hidden from the home "Recommended for you" section,
 * and the dismissed-job *titles/companies* are de-prioritized in future
 * recommendation logic. In-memory for now; persisted later.
 */
object DismissedJobsRepository {
    private const val MAX_ENTRIES = 100
    private val _ids = MutableStateFlow<Set<Int>>(emptySet())
    val ids: StateFlow<Set<Int>> = _ids.asStateFlow()

    fun isDismissed(jobId: Int): Boolean = jobId in _ids.value

    fun dismiss(jobId: Int) {
        _ids.value = (_ids.value + jobId).toList().takeLast(MAX_ENTRIES).toSet()
        PersistedState.persistDismissed(_ids.value)
    }

    fun restore(jobId: Int) {
        _ids.value = _ids.value - jobId
        PersistedState.persistDismissed(_ids.value)
    }

    /** Restore persisted state at startup. */
    fun restoreAll(ids: Set<Int>) {
        _ids.value = ids
    }
}
