package com.prc.app.data

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Conversion analytics for the subscription model. Records a lightweight
 * analytics document per user:
 *
 *   plan_analytics/{uid}
 *     choice        — "free" | "pro"       (what they picked on the plan screen)
 *     convertedAt   — timestamp of first Pro purchase (null for free users)
 *     choiceAt      — when the choice was made
 *     source        — "registration" | "paywall" | "profile"
 *
 * Free users who later upgrade are UPDATED in place, giving a simple
 * free→paid conversion funnel: count(docs) vs count(convertedAt != null).
 */
object PlanAnalytics {

    /** User picked a plan on the first-run choice screen. */
    fun recordChoice(choice: String, source: String = "registration") {
        val uid = AuthRepository.currentUid() ?: return
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return }
        db.collection("plan_analytics").document(uid)
            .set(
                mapOf(
                    "choice" to choice,
                    "source" to source,
                    "choiceAt" to FieldValue.serverTimestamp(),
                    "email" to (AuthRepository.currentUser.value?.contact ?: "")
                )
            )
            .addOnFailureListener { Log.w("PRC-Analytics", "choice record failed: ${it.message}") }
    }

    /** A free user converted to Pro (from paywall, profile, or anywhere). */
    fun recordConversion(source: String) {
        val uid = AuthRepository.currentUid() ?: return
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return }
        db.collection("plan_analytics").document(uid)
            .set(
                mapOf(
                    "choice" to "pro",
                    "convertedAt" to FieldValue.serverTimestamp(),
                    "conversionSource" to source
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnFailureListener { Log.w("PRC-Analytics", "conversion record failed: ${it.message}") }
    }

    /**
     * An upgrade ATTEMPT: the user tapped an upgrade button on some surface
     * but we do not yet know whether checkout completed. Logged as a separate
     * sub-collection so A/B attribution works even when checkout is abandoned:
     *
     *   plan_analytics/{uid}/attempts/{autoId}
     *     source    — "daily_cap" | "alert_cap" | "profile" | "registration"
     *     variant   — "standard" | experiment label (see [variant]
     *                 param on recordChoice)
     *     at        — server timestamp
     *     converted — set to true if a conversion follows this attempt
     */
    fun recordUpgradeAttempt(source: String, variant: String = "standard") {
        val uid = AuthRepository.currentUid() ?: return
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return }
        db.collection("plan_analytics").document(uid)
            .collection("attempts").add(
                mapOf(
                    "source" to source,
                    "variant" to variant,
                    "at" to FieldValue.serverTimestamp()
                )
            )
            .addOnFailureListener { Log.w("PRC-Analytics", "attempt record failed: ${it.message}") }
    }

    /**
     * Mark checkout as completed following an attempt from [source]. Called
     * on verified purchase so attempts can be joined to outcomes per source.
     */
    fun recordCheckoutCompleted(source: String, variant: String = "standard") {
        val uid = AuthRepository.currentUid() ?: return
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return }
        db.collection("plan_analytics").document(uid)
            .set(
                mapOf(
                    "lastCheckoutSource" to source,
                    "lastCheckoutVariant" to variant
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnFailureListener { Log.w("PRC-Analytics", "checkout-source record failed: ${it.message}") }
    }
}
