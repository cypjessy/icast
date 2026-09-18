package com.prc.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bookmarks. Persisted locally (SharedPreferences) AND synced to Firestore
 * under users/{uid}/saved/{jobId} so a user's saved list follows them
 * across devices. UI only sees this surface.
 */
object SavedRepository {
    private val _saved = MutableStateFlow<Map<Int, Long>>(emptyMap())
    val saved: StateFlow<Map<Int, Long>> = _saved.asStateFlow()

    private var syncingFromFirestore = false

    fun isSaved(jobId: Int): Boolean = jobId in _saved.value

    fun toggle(jobId: Int) {
        if (jobId in _saved.value) remove(jobId) else add(jobId)
    }

    fun add(jobId: Int) {
        if (jobId in _saved.value) return
        _saved.value = _saved.value + (jobId to System.currentTimeMillis())
        PersistedState.persistSaved(_saved.value)
        persistToFirestore(jobId, _saved.value[jobId] ?: System.currentTimeMillis())
    }

    fun remove(jobId: Int) {
        _saved.value = _saved.value - jobId
        PersistedState.persistSaved(_saved.value)
        deleteFromFirestore(jobId)
    }

    fun clearAll() {
        val ids = _saved.value.keys.toList()
        _saved.value = emptyMap()
        PersistedState.persistSaved(_saved.value)
        ids.forEach { deleteFromFirestore(it) }
    }

    private fun firestore() = try {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
    } catch (_: Exception) { null }

    private fun currentUid(): String? =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    private fun persistToFirestore(jobId: Int, atMillis: Long) {
        if (syncingFromFirestore) return
        try {
            val uid = currentUid() ?: return
            val coll = firestore()?.collection("users")?.document(uid)?.collection("saved")
            coll?.document(jobId.toString())
                ?.set(mapOf("savedAt" to atMillis))
                ?.addOnFailureListener { e ->
                    android.util.Log.w("PRC-Saved", "firestore save FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Saved", "Firestore unavailable: ${e.message}")
        }
    }

    private fun deleteFromFirestore(jobId: Int) {
        if (syncingFromFirestore) return
        try {
            val uid = currentUid() ?: return
            val coll = firestore()?.collection("users")?.document(uid)?.collection("saved")
            coll?.document(jobId.toString())
                ?.delete()
                ?.addOnFailureListener { e ->
                    android.util.Log.w("PRC-Saved", "firestore delete FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Saved", "Firestore unavailable: ${e.message}")
        }
    }

    /**
     * Start listening to the signed-in user's saved jobs in Firestore and
     * merge them into the local map (remote wins on conflicts). Call at app
     * start and whenever the signed-in user changes.
     */
    private var listener: com.google.firebase.firestore.ListenerRegistration? = null

    fun startFirestoreSync() {
        stopFirestoreSync()
        val db = firestore() ?: return
        val uid = currentUid() ?: return
        listener = db.collection("users").document(uid)
            .collection("saved")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("PRC-Saved", "saved sync error: ${err.message}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                syncingFromFirestore = true
                val remote = snap.documents.mapNotNull { doc ->
                    doc.id.toIntOrNull()?.let { id ->
                        id to (doc.getLong("savedAt") ?: System.currentTimeMillis())
                    }
                }.toMap()
                // Merge: keep local entries not yet on the server (pending upload),
                // otherwise remote is authoritative.
                val merged = _saved.value + remote
                _saved.value = merged
                PersistedState.persistSaved(merged)
                syncingFromFirestore = false
            }
    }

    fun stopFirestoreSync() {
        listener?.remove()
        listener = null
    }

    /** Restore persisted state at startup. */
    fun restoreAll(map: Map<Int, Long>) {
        _saved.value = map
    }

    /** Humanized "saved X ago" label; falls back to "just now". */
    fun savedAgo(jobId: Int): String {
        val t = _saved.value[jobId] ?: return "just now"
        val mins = (System.currentTimeMillis() - t) / 60000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "Saved ${mins}m ago"
            mins < 60 * 24 -> "Saved ${mins / 60}h ago"
            mins < 60 * 24 * 7 -> "Saved ${mins / (60 * 24)}d ago"
            else -> "Saved ${(mins / (60 * 24 * 7)).toInt()}w ago"
        }
    }
}
