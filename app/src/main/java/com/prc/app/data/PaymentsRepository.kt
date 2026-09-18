package com.prc.app.data

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Products the app sells. Keep ids/amounts in sync with functions/index.js PRODUCTS. */
sealed class BillingProduct(
    val id: String,
    val label: String,
    val description: String,
    val amountKes: Int
) {
    class Boost3d : BillingProduct("boost_3d", "Featured — 3 days", "Pin your job at the top of the home feed with a Featured badge.", 500)
    class Boost7d : BillingProduct("boost_7d", "Featured — 7 days", "Maximum visibility for a full week.", 1000)
    class AppUnlock : BillingProduct("app_unlock", "Application unlock", "Apply to one premium (lock-icon) job.", 200)
    class ProMonthly : BillingProduct("pro_monthly", "Pro — monthly", "Unlimited premium applications + priority profile.", 500)
    class ProYearly : BillingProduct("pro_yearly", "Pro — yearly", "Everything in Pro, two months free.", 5000)
}

data class CheckoutState(
    val phase: Phase = Phase.Idle,
    val accessCode: String = "",
    val reference: String = "",
    val message: String = ""
) {
    enum class Phase { Idle, Initializing, AwaitingSheet, Verifying, Success, Failed }
}

/**
 * Talks to the two Cloud Functions (initializePayment / verifyPayment).
 * The Paystack secret key never touches the app — only the public key does,
 * which the CheckoutScreen passes into Paystack.builder().
 */
object PaymentsRepository {

    private val _checkout = MutableStateFlow(CheckoutState())
    val checkout: StateFlow<CheckoutState> = _checkout.asStateFlow()

    private val functions: FirebaseFunctions? = try {
        FirebaseFunctions.getInstance()
    } catch (_: Exception) {
        null.also { Log.w("PRC-Pay", "Functions unavailable") }
    }

    private fun state(mutate: (CheckoutState) -> CheckoutState) {
        _checkout.value = mutate(_checkout.value)
    }

    /**
     * Step 1 — ask our backend to initialize a Paystack transaction.
     * Returns the access_code to feed into PaymentSheet.launch().
     */
    suspend fun initialize(product: BillingProduct, jobId: Int? = null, email: String): Boolean {
        val fns = functions ?: run {
            state { it.copy(phase = CheckoutState.Phase.Failed, message = "Payments backend unavailable") }
            return false
        }
        state { it.copy(phase = CheckoutState.Phase.Initializing, message = "") }
        return try {
            val result = fns.getHttpsCallable("initializePayment").call(
                mapOf(
                    "productId" to product.id,
                    "jobId" to jobId,
                    "email" to email
                )
            ).await()
            val data = result.data as? Map<*, *> ?: error("Unexpected response")
            val access = data["accessCode"] as? String ?: error("Missing accessCode")
            val ref = data["reference"] as? String ?: ""
            state { it.copy(phase = CheckoutState.Phase.AwaitingSheet, accessCode = access, reference = ref) }
            true
        } catch (e: Exception) {
            Log.w("PRC-Pay", "initialize failed: ${e.message}")
            state { it.copy(phase = CheckoutState.Phase.Failed, message = e.message ?: "Could not start payment") }
            false
        }
    }

    /** Step 3 — verify the reference server-side after PaymentSheet completes. */
    suspend fun verify(): Boolean {
        val fns = functions ?: return false
        val ref = _checkout.value.reference
        if (ref.isBlank()) return false
        state { it.copy(phase = CheckoutState.Phase.Verifying) }
        return try {
            val result = fns.getHttpsCallable("verifyPayment").call(mapOf("reference" to ref)).await()
            val data = result.data as? Map<*, *>
            val ok = data?.get("status") == "success"
            state {
                it.copy(
                    phase = if (ok) CheckoutState.Phase.Success else CheckoutState.Phase.Failed,
                    message = if (ok) "Payment confirmed" else "Verification failed"
                )
            }
            if (ok) {
                when (data?.get("kind")) {
                    "boost" -> notifyBoostSuccess(ref)
                    "subscription" -> {
                        notifyProSuccess()
                        PlanAnalytics.recordConversion(source = "checkout")
                    }
                    else -> {}
                }
            }
            ok
        } catch (e: Exception) {
            Log.w("PRC-Pay", "verify failed: ${e.message}")
            state { it.copy(phase = CheckoutState.Phase.Failed, message = e.message ?: "Verification failed") }
            false
        }
    }

    fun reset() {
        _checkout.value = CheckoutState()
    }

    /**
     * Bell notification for the job owner after a verified boost. Persisted to
     * the Firestore `notifications` collection so it survives restarts and is
     * mirrored into the in-memory bell for instant display.
     */
    private suspend fun notifyBoostSuccess(reference: String) {
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val pay = db.collection("payments").document(reference).get().await()
            val jobId = (pay.getLong("jobId") ?: 0L).toInt()
            val job = JobRepository.jobs.value.firstOrNull { it.id == jobId } ?: return
            val daysLeft = boostTimeLeftText(job.featuredUntil)
            NotificationRepository.onBoostVerified(job.posterContact, job.title, daysLeft)

            val uid = AuthRepository.currentUid() ?: return
            db.collection("notifications").add(
                mapOf(
                    "recipientContact" to job.posterContact,
                    "title" to "Job featured ⭐",
                    "body" to "Payment confirmed — \"${job.title}\" is now featured for $daysLeft.",
                    "type" to "boost",
                    "jobId" to jobId,
                    "uid" to uid,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            )
        } catch (e: Exception) {
            Log.w("PRC-Pay", "boost notification failed: ${e.message}")
        }
    }

    private suspend fun notifyProSuccess() {
        try {
            val uid = AuthRepository.currentUid() ?: return
            val me = AuthRepository.currentUser.value
            NotificationRepository.pushProVerified(me?.contact ?: "")
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("notifications").add(
                    mapOf(
                        "recipientContact" to (me?.contact ?: ""),
                        "title" to "Pro activated ✨",
                        "body" to "Your Pro subscription is active. Enjoy unlimited premium applications!",
                        "type" to "subscription",
                        "uid" to uid,
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
        } catch (e: Exception) {
            Log.w("PRC-Pay", "pro notification failed: ${e.message}")
        }
    }

    private fun boostTimeLeftText(featuredUntil: Long): String {
        val ms = featuredUntil - System.currentTimeMillis()
        if (ms <= 0) return "the remaining period"
        val hours = ms / 3600000
        return when {
            hours < 24 -> "${hours}h"
            else -> "${ms / 86400000}d"
        }
    }

    // ==== Entitlement checks (read straight from the user's Firestore doc) ====

    /** Pro subscription expiry (ms). 0 = not subscribed. */
    suspend fun proUntil(uid: String): Long {
        return try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            snap.getTimestamp("proUntil")?.toDate()?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    /** Remaining premium-application unlocks. */
    suspend fun unlocksRemaining(uid: String): Int {
        return try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(uid).get().await()
            (snap.getLong("applicationUnlocks") ?: 0L).toInt()
        } catch (_: Exception) { 0 }
    }
}
