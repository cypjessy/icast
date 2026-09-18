package com.prc.app.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks which user contacts hold an active Pro subscription so employers
 * can give Pro applicants priority placement ("Priority placement in
 * employer searches" Pro benefit). Kept as a small live snapshot rather
 * than per-applicant queries so the list sorts instantly offline too.
 */
object ProDirectory {

    private val _proContacts = MutableStateFlow<Set<String>>(emptySet())
    val proContacts: StateFlow<Set<String>> = _proContacts

    private var started = false

    /** One live Firestore listener for the whole app; safe to call repeatedly. */
    fun start() {
        if (started) return
        started = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .whereGreaterThan("proUntil", com.google.firebase.Timestamp.now())
                    .addSnapshotListener { snap, _ ->
                        if (snap == null) return@addSnapshotListener
                        _proContacts.value = snap.documents.mapNotNull { it.getString("contact") }
                            .map { it.trim().lowercase() }
                            .toSet()
                    }
            } catch (_: Exception) {
                // Pro ranking simply doesn't apply if Firestore is unreachable.
            }
        }
    }

    fun isProContact(contact: String): Boolean =
        contact.isNotBlank() && contact.trim().lowercase() in _proContacts.value
}
