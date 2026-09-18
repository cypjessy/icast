package com.prc.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

private val Context.authDataStore by preferencesDataStore(name = "prc_auth")

/**
 * Firebase-backed auth + profile store.
 *
 * - Accounts: Firebase Auth (email + password).
 * - Profiles: Firestore `users/{uid}` document mirroring [User] fields.
 * - Onboarding flag stays local (DataStore).
 *
 * When Firebase isn't configured yet (placeholder google-services.json),
 * init() detects the failure and [firebaseReady] stays false; the UI keeps
 * working against the previous local mock so nothing crashes.
 */
object AuthRepository {

    private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_seen")

    private var appContext: Context? = null
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /** Firebase Auth uid of the signed-in user (Firestore users/{uid} doc id), or null. */
    fun currentUid(): String? = auth?.currentUser?.uid

    private val _onboardingSeen = MutableStateFlow(false)
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen.asStateFlow()

    /** Bumped on every login/logout so stale profile fetches can be discarded. */
    private var sessionGeneration = 0L

    /** True once Firebase initialized successfully. */
    var firebaseReady: Boolean = false
        private set

    /** Called once from MainActivity before any screen reads state. */
    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        // onboarding flag (local)
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.authDataStore.data.first()
            _onboardingSeen.value = prefs[ONBOARDING_KEY] ?: false
        }

        auth = try {
            FirebaseAuth.getInstance().also {
                db = FirebaseFirestore.getInstance()
                firebaseReady = true
            }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Auth", "Firebase not available yet: ${e.message}")
            null
        }

        // restore session when Firebase is live
        auth?.addAuthStateListener { fa ->
            val fu = fa.currentUser
            if (fu == null) {
                _currentUser.value = null
            } else {
                loadProfile(fu)
            }
        }
    }

    fun markOnboardingSeen() {
        _onboardingSeen.value = true
        val ctx = appContext ?: return
        CoroutineScope(Dispatchers.IO).launch {
            ctx.authDataStore.edit { it[ONBOARDING_KEY] = true }
        }
    }

    // ==================== auth (email + password) ====================

    fun signUp(email: String, password: String, fullName: String, phone: String = "", onResult: (Result<Unit>) -> Unit) {
        val a = auth
        if (a == null) {
            onResult(Result.failure(Exception("Firebase not configured yet")))
            return
        }
        a.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { cred ->
                val fu = cred.user
                if (fu != null) {
                    val profile = User(
                        fullName = fullName.trim(),
                        contact = email.trim(),
                        isPhone = false,
                        phone = phone.trim(),
                        memberSince = System.currentTimeMillis(),
                        password = ""   // never stored; auth handled by Firebase
                    )
                    saveProfile(fu.uid, profile)
                    _currentUser.value = profile
                }
                onResult(Result.success(Unit))
            }
            .addOnFailureListener { e -> onResult(Result.failure(mapError(e))) }
    }

    fun login(email: String, password: String, onResult: (Result<User>) -> Unit) {
        val a = auth
        if (a == null) {
            onResult(Result.failure(Exception("Firebase not configured yet")))
            return
        }
        a.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { cred ->
                val fu = cred.user
                val fallback = User(
                    fullName = fu?.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() } ?: "Member",
                    contact = fu?.email ?: email.trim(),
                    isPhone = false,
                    password = ""
                )
                _currentUser.value = fallback
                fu?.let { loadProfile(it) }   // overlay Firestore profile when it lands
                onResult(Result.success(fallback))
            }
            .addOnFailureListener { e -> onResult(Result.failure(mapError(e))) }
    }

    fun sendPasswordReset(email: String, onResult: (Result<Unit>) -> Unit) {
        val a = auth
        if (a == null) {
            onResult(Result.failure(Exception("Firebase not configured yet")))
            return
        }
        a.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener { onResult(Result.success(Unit)) }
            .addOnFailureListener { e -> onResult(Result.failure(mapError(e))) }
    }

    fun logout() {
        sessionGeneration++
        auth?.signOut()
        _currentUser.value = null
    }

    // ==================== profile (Firestore) ====================

    fun updateProfile(
        location: String,
        skills: List<String>,
        bio: String? = null,
        experience: List<ExperienceEntry>? = null,
        education: List<EducationEntry>? = null,
        certifications: List<String>? = null,
        languages: List<LanguageEntry>? = null,
        cvName: String? = null,
        phone: String? = null,
        notifyAlerts: Boolean? = null,
        notifyDigest: Boolean? = null
    ) {
        val current = _currentUser.value ?: return
        val updated = current.copy(
            location = location.trim(),
            skills = skills,
            bio = bio ?: current.bio,
            experience = experience ?: current.experience,
            education = education ?: current.education,
            certifications = certifications ?: current.certifications,
            languages = languages ?: current.languages,
            cvName = cvName ?: current.cvName,
            phone = phone?.trim() ?: current.phone,
            notifyAlerts = notifyAlerts ?: current.notifyAlerts,
            notifyDigest = notifyDigest ?: current.notifyDigest
        )
        _currentUser.value = updated
        auth?.currentUser?.let { saveProfile(it.uid, updated) }
    }

    private fun loadProfile(fu: FirebaseUser) {
        val d = db ?: return
        val generation = sessionGeneration
        d.collection("users").document(fu.uid).get()
            .addOnSuccessListener { snap ->
                // Discard results that raced with a logout / different login:
                // only apply if this fetch still belongs to the live session.
                if (generation != sessionGeneration) return@addOnSuccessListener
                if (auth?.currentUser?.uid != fu.uid) return@addOnSuccessListener
                if (snap != null && snap.exists()) {
                    val u = snap.toUser(fu)
                    if (u != null) _currentUser.value = u
                }
            }
            .addOnFailureListener { e ->
                android.util.Log.w("PRC-Auth", "profile load failed: ${e.message}")
            }
    }

    private fun saveProfile(uid: String, user: User) {
        val d = db ?: return
        d.collection("users").document(uid)
            .set(user.toMap(), SetOptions.merge())
            .addOnFailureListener { e ->
                android.util.Log.w("PRC-Auth", "profile save failed: ${e.message}")
            }
    }

    // ==== Firestore read-only lookup used by the Applicants screen ====
    fun publicProfileOf(contact: String): User? =
        _currentUser.value?.takeIf { it.contact.equals(contact.trim(), ignoreCase = true) }

    // ==================== helpers ====================

    private fun User.toMap(): Map<String, Any> = buildMap {
        put("fullName", fullName)
        put("contact", contact)
        put("isPhone", isPhone)
        put("phone", phone)
        put("location", location)
        put("skills", skills)
        put("bio", bio)
        put("certifications", certifications)
        put("cvName", cvName)
        put("memberSince", memberSince)
        put("notifyAlerts", notifyAlerts)
        put("notifyDigest", notifyDigest)
        put("experience", experience.map { mapOf("role" to it.role, "org" to it.org, "dates" to it.dates) })
        put("education", education.map { mapOf("qualification" to it.qualification, "institution" to it.institution, "dates" to it.dates) })
        put("languages", languages.map { mapOf("language" to it.language, "proficiency" to it.proficiency) })
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(fu: FirebaseUser): User? {
        if (!exists()) return null
        @Suppress("UNCHECKED_CAST")
        fun strList(key: String): List<String> = (get(key) as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        fun mapList(key: String): List<Map<String, Any>> = (get(key) as? List<Map<String, Any>>) ?: emptyList()
        return User(
            fullName = getString("fullName") ?: fu.email?.substringBefore("@") ?: "Member",
            contact = getString("contact") ?: fu.email ?: "",
            isPhone = getBoolean("isPhone") ?: false,
            phone = getString("phone") ?: "",
            password = "",
            memberSince = (get("memberSince") as? Long) ?: 0L,
            notifyAlerts = getBoolean("notifyAlerts") ?: true,
            notifyDigest = getBoolean("notifyDigest") ?: true,
            location = getString("location") ?: "",
            skills = strList("skills"),
            bio = getString("bio") ?: "",
            experience = mapList("experience").map { m ->
                ExperienceEntry(m["role"] as? String ?: "", m["org"] as? String ?: "", m["dates"] as? String ?: "")
            },
            education = mapList("education").map { m ->
                EducationEntry(m["qualification"] as? String ?: "", m["institution"] as? String ?: "", m["dates"] as? String ?: "")
            },
            certifications = strList("certifications"),
            languages = mapList("languages").map { m ->
                LanguageEntry(m["language"] as? String ?: "", m["proficiency"] as? String ?: "")
            },
            cvName = getString("cvName") ?: ""
        )
    }

    private fun mapError(e: Exception): Exception = Exception(
        when {
            e.message?.contains("already in use", ignoreCase = true) == true ->
                "An account with this email already exists"
            e.message?.contains("credential is invalid", ignoreCase = true) == true ||
                    e.message?.contains("wrong password", ignoreCase = true) == true ->
                "Incorrect email or password"
            e.message?.contains("no user record", ignoreCase = true) == true ->
                "No account found — sign up first"
            e.message?.contains("network", ignoreCase = true) == true ->
                "Network error — check your connection"
            else -> e.message ?: "Authentication failed"
        }
    )

    // ==== legacy no-op OTP surface kept so call sites still compile ====
    fun isPhone(contact: String) = contact.any { c -> c.isDigit() } &&
            contact.filter { it.isDigit() }.length >= 7

    fun requestOtp(contact: String, isPhone: Boolean): String = "000000"
    fun verifyOtp(contact: String, isPhone: Boolean, code: String): Boolean = true
}
