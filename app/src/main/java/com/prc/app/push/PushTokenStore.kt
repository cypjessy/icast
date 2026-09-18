package com.prc.app.push

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.prc.app.data.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registers this device's FCM token in Firestore under the signed-in user
 * (`users/{uid}.fcmTokens` map) and maintains a `devices/{contact}` doc so
 * the backend can fan out pushes either by uid or by contact.
 */
object PushTokenStore {

    fun register(token: String) {
        val uid = AuthRepository.currentUid() ?: return
        val contact = AuthRepository.currentUser.value?.contact ?: return
        if (token.isBlank()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayUnion(token))
                    .addOnFailureListener {
                        // Field missing on first push — create it.
                        db.collection("users").document(uid)
                            .set(mapOf("fcmTokens" to listOf(token)))
                    }
                db.collection("devices").document(contact.trim().lowercase())
                    .set(mapOf("tokens" to FieldValue.arrayUnion(token)))
            } catch (_: Exception) {
                // Push registration is best-effort; bell still works.
            }
        }
    }
}
