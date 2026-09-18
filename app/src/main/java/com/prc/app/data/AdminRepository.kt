package com.prc.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One line in the admin activity log. */
data class AdminActivity(
    val id: Int,
    val icon: String,        // emoji glyph
    val message: String,
    val at: Long = System.currentTimeMillis(),
    val admin: String = ""   // which admin account performed the action
)

/** Snapshot of app-wide numbers shown on the admin dashboard. */
data class AdminStats(
    val users: Int,
    val jobs: Int,
    val providerJobs: Int,
    val applications: Int,
    val adminPosts: Int,
    val postedToday: Int
)

/** One bar in the weekly posting trend shown on the overview. */
data class DailyPosting(
    val label: String,   // Mon…Sun
    val community: Int,
    val provider: Int
)

/** A label + share pair for composition charts (category mix, job mix). */
data class NamedShare(val label: String, val count: Int)

/** Rollup of live/closed provider jobs for one provider. */
data class ProviderRow(
    val providerName: String,
    val live: Int,
    val closed: Int,
    val lastPostAt: Long
)

/**
 * Mock admin backend: one demo portal account, direct provider-job posting
 * (the admin pastes jobs exactly as providers hand them over — no editing),
 * dashboard stats and an activity log. In-memory for now — swap for an
 * API-backed repo later; screens only see this surface.
 *
 * Demo credentials (shown on the admin login screen): admin@prc.app / admin123
 */
object AdminRepository {

    private val fireAuth: FirebaseAuth? = try { FirebaseAuth.getInstance() } catch (_: Exception) { null }
    private val fireDb: FirebaseFirestore? = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _activity = MutableStateFlow(emptyList<AdminActivity>())
    val activity: StateFlow<List<AdminActivity>> = _activity.asStateFlow()

    private var activityListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** Connection state of the audit log listener. */
    private val _activitySyncState = MutableStateFlow(SyncState())
    val activitySyncState: StateFlow<SyncState> = _activitySyncState.asStateFlow()

    private val _jobs = JobRepository.jobs

    private var nextActivityId = 100

    // ==== portal auth (Firebase-backed) ====

    /** Signed-in admin profile (null when not signed in). */
    private val _adminUser = MutableStateFlow<AdminUser?>(null)
    val adminUser: StateFlow<AdminUser?> = _adminUser.asStateFlow()

    /**
     * Sign in against Firebase Auth, then verify the account is an admin by
     * reading `users/{uid}.role == "admin"` (or the `admins/{email}` pointer).
     * Calls [onResult] with null on success, else an error message.
     */
    fun login(email: String, password: String, onResult: (String?) -> Unit) {
        val key = email.trim().lowercase()
        val auth = fireAuth
        if (auth == null) {
            onResult("Firebase not configured")
            return
        }
        auth.signInWithEmailAndPassword(key, password)
            .addOnSuccessListener { cred ->
                val uid = cred.user?.uid
                if (uid == null) {
                    auth.signOut()
                    onResult("Sign-in failed — try again")
                    return@addOnSuccessListener
                }
                verifyAdminRole(uid, key) { errMsg ->
                    if (errMsg == null) {
                        _signedIn.value = true
                        _adminUser.value = AdminUser(uid = uid, email = key)
                        startApprovalsSync()
                        startUsersSync()
                        startActivitySync()
                        startPaymentsSync()
                        startConversionSync()
                        onResult(null)
                    } else {
                        auth.signOut()
                        onResult(errMsg)
                    }
                }
            }
            .addOnFailureListener { e ->
                onResult(
                    when {
                        e.message?.contains("no user record", true) == true -> "No admin account found"
                        e.message?.contains("invalid credential", true) == true ||
                                e.message?.contains("wrong password", true) == true -> "Incorrect email or password"
                        e.message?.contains("network", true) == true -> "Network error — check your connection"
                        else -> e.message ?: "Sign-in failed"
                    }
                )
            }
    }

    private fun verifyAdminRole(uid: String, email: String, onDone: (String?) -> Unit) {
        val db = fireDb ?: run { onDone(null); return }   // offline bootstrap: allow
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val role = snap.getString("role")
                if (role == "admin") onDone(null)
                else {
                    // fall back to the admins/{email} pointer
                    db.collection("admins").document(email).get()
                        .addOnSuccessListener { a ->
                            if (a.exists()) onDone(null) else onDone("This account is not an admin")
                        }
                        .addOnFailureListener { onDone("This account is not an admin") }
                }
            }
            .addOnFailureListener { onDone(null) }   // read failed: allow (rules may block; bootstrap mode)
    }

    /**
     * TEMPORARY bootstrap: register a new admin. Creates the Firebase Auth
     * account and writes users/{uid}.role="admin" plus admins/{email}.
     *
     * @return null on success, else an error message.
     */
    fun registerAdmin(fullName: String, email: String, password: String, onResult: (String?) -> Unit) {
        val key = email.trim().lowercase()
        when {
            fullName.trim().length < 2 -> { onResult("Enter the admin's full name"); return }
            !email.contains("@") || !email.contains(".") -> { onResult("Enter a valid email address"); return }
            password.length < 8 -> { onResult("Password must be at least 8 characters"); return }
        }
        val auth = fireAuth
        val db = fireDb
        if (auth == null || db == null) {
            onResult("Firebase not configured")
            return
        }
        auth.createUserWithEmailAndPassword(key, password)
            .addOnSuccessListener { cred ->
                val uid = cred.user?.uid
                if (uid == null) {
                    onResult("Registration failed — try again")
                    return@addOnSuccessListener
                }
                val doc = mapOf(
                    "fullName" to fullName.trim(),
                    "contact" to key,
                    "role" to "admin",
                    "isPhone" to false,
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("users").document(uid).set(doc, SetOptions.merge())
                db.collection("admins").document(key).set(
                    mapOf(
                        "uid" to uid,
                        "fullName" to fullName.trim(),
                        "createdAt" to System.currentTimeMillis()
                    )
                )
                android.util.Log.d("PRC-Admin", "admin persisted to Firestore: $key (uid=$uid)")
                _signedIn.value = true
                _adminUser.value = AdminUser(uid = uid, email = key)
                startApprovalsSync()
                startUsersSync()
                startActivitySync()
                startPaymentsSync()
                startConversionSync()
                onResult(null)
            }
            .addOnFailureListener { e ->
                onResult(
                    when {
                        e.message?.contains("already in use", true) == true ->
                            "An admin account with this email already exists"
                        e.message?.contains("network", true) == true ->
                            "Network error — check your connection"
                        else -> e.message ?: "Registration failed"
                    }
                )
            }
    }

    data class AdminUser(val uid: String, val email: String)

    fun logout() {
        _signedIn.value = false
        stopApprovalsSync()
        stopUsersSync()
        stopActivitySync()
        stopPaymentsSync()
        stopConversionSync()
    }

    // ==== direct provider-job posting ====

    /**
     * Publishes a provider job exactly as handed over — the admin pastes the
     * details but never edits them. The job appears in the public feed
     * immediately and applications route through admin approval.
     */
    fun adminPostJob(
        providerName: String,
        providerContact: String,
        title: String,
        category: String,
        location: String,
        pay: String,
        type: String,
        description: String,
        requirements: List<String>,
        applicationUrl: String,
        openings: Int = 1,
        remoteOk: Boolean = false,
        experience: String = "",
        skills: List<String> = emptyList(),
        deadlineDays: Int? = null,
        isCompany: Boolean = false
    ): Job {
        val job = Job(
            id = 0, // reassigned by JobRepository on publish
            title = title.trim(),
            category = category,
            location = location.trim(),
            pay = pay.trim(),
            type = type,
            postedAgo = "Just now",
            description = description.trim(),
            requirements = requirements,
            postedBy = providerName.trim(),
            posterContact = providerContact.trim(),
            deadlineDays = deadlineDays,
            openings = openings,
            remoteOk = remoteOk,
            experience = experience,
            skills = skills,
            source = Job.SOURCE_PROVIDER,
            applicationUrl = applicationUrl.trim(),
            isCompany = isCompany
        )
        val published = JobRepository.publishProviderJob(job)
            ?: error("publishProviderJob refused a provider job")
        log("🚀", "Posted \"$title\" from ${providerName.trim()}")
        return published
    }

    /** True when a live provider job with the same title from the same provider exists. */
    fun isDuplicatePost(providerName: String, title: String): Boolean =
        _jobs.value.any {
            it.isProviderJob && !it.closed && !it.draft &&
                    it.title.equals(title.trim(), ignoreCase = true) &&
                    it.postedBy.equals(providerName.trim(), ignoreCase = true)
        }

    /** Provider-sourced jobs on the public board (live + closed). */
    fun providerJobs(): List<Job> = _jobs.value.filter { it.isProviderJob }

    // ==== stats & analytics ====

    /** Weekly (last 7 days) posting trend, community vs provider, across all non-draft jobs. */
    fun postingTrend(): List<DailyPosting> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        val calendar = java.util.Calendar.getInstance()

        val live = _jobs.value.filter { !it.draft }
        val buckets = MutableList(7) { i ->
            // index 0 = 6 days ago … index 6 = today
            val bucketCal = (calendar.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.DAY_OF_YEAR, i - 6)
            }
            DailyPosting(days[bucketCal.get(java.util.Calendar.DAY_OF_WEEK) - 1], 0, 0)
        }
        live.forEach { job ->
            val ageDays = ((now - job.postedAtMillis) / dayMs).toInt()
            if (ageDays in 0..6) {
                // Newest first (index 0 = today); chart wants oldest first.
                val slot = buckets[6 - ageDays]
                val isProvider = job.isProviderJob
                buckets[6 - ageDays] = if (isProvider)
                    slot.copy(provider = slot.provider + 1) else slot.copy(community = slot.community + 1)
            }
        }
        return buckets
    }

    /** Live jobs grouped by category (top N others grouped together by the caller). */
    fun categoryMix(): List<NamedShare> =
        _jobs.value
            .filter { !it.draft && !it.closed }
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { NamedShare(it.key, it.value) }

    /** Job type mix across live jobs (Full-time / Part-time / Gig / Contract). */
    fun jobTypeMix(): List<NamedShare> =
        _jobs.value
            .filter { !it.draft && !it.closed }
            .groupingBy { it.type }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { NamedShare(it.key, it.value) }

    /** Per-provider rollup over provider jobs on the board, most recent first. */
    fun providerRollup(): List<ProviderRow> {
        return _jobs.value
            .filter { it.isProviderJob }
            .groupBy { it.postedBy }
            .map { (name, jobsOfName) ->
                ProviderRow(
                    providerName = name,
                    live = jobsOfName.count { !it.closed && !it.draft },
                    closed = jobsOfName.count { it.closed },
                    lastPostAt = jobsOfName.maxOf { it.postedAtMillis }
                )
            }
            .sortedByDescending { it.lastPostAt }
    }

    /** Share of application decisions made so far (approved vs rejected vs pending). */
    fun approvalRate(): NamedShare {
        var approved = 0
        var rejected = 0
        _pendingApprovals.value.forEach {
            when (it.status) {
                "Approved" -> approved++
                "Rejected" -> rejected++
            }
        }
        val pending = _pendingApprovals.value.count { it.status == "Pending" }
        val decided = approved + rejected
        return NamedShare(
            label = if (decided == 0) "—" else "${approved * 100 / decided}%",
            count = pending
        )
    }

    /** Live count of registered user accounts, maintained by a Firestore listener. */
    private val _userCount = MutableStateFlow(0)
    val userCount: StateFlow<Int> = _userCount.asStateFlow()

    private var usersListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Listen to the Firestore `users` collection so the Overview's users
     * metric is always the real number of registered accounts. Call once at
     * admin sign-in (after approvals sync).
     */
    fun startUsersSync() {
        stopUsersSync()
        val db = fireDb ?: return
        usersListener = db.collection("users")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("PRC-Admin", "users sync error: ${err.message}")
                    return@addSnapshotListener
                }
                _userCount.value = snap?.documents?.size ?: 0
            }
    }

    fun stopUsersSync() {
        usersListener?.remove()
        usersListener = null
    }

    // ==== payments (client payment features control) ====

    data class AdminPayment(
        val reference: String,
        val uid: String,
        val email: String,
        val kind: String,          // boost | unlock | subscription
        val productId: String,
        val amountKes: Int,
        val status: String,        // initialized | success | revoked
        val jobId: Int? = null,
        val createdAt: Long = 0,
        val verifiedAt: Long = 0
    )

    private val _payments = MutableStateFlow<List<AdminPayment>>(emptyList())
    val payments: StateFlow<List<AdminPayment>> = _payments.asStateFlow()

    private val _paymentsSyncState = MutableStateFlow(SyncState())
    val paymentsSyncState: StateFlow<SyncState> = _paymentsSyncState.asStateFlow()

    private var paymentsListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** Real-time feed of every payment attempt across all clients. */
    fun startPaymentsSync() {
        stopPaymentsSync()
        val db = fireDb ?: run {
            _paymentsSyncState.value = SyncState(loading = false, error = "Firestore unavailable on this device")
            return
        }
        _paymentsSyncState.value = SyncState(loading = true)
        paymentsListener = db.collection("payments")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    _paymentsSyncState.value = SyncState(
                        loading = false,
                        error = "Connection lost — showing last known payments (${err.message ?: "offline"})"
                    )
                    return@addSnapshotListener
                }
                _payments.value = snap?.documents?.mapNotNull { doc ->
                    AdminPayment(
                        reference = doc.id,
                        uid = doc.getString("uid") ?: "",
                        email = doc.getString("email") ?: "",
                        kind = doc.getString("kind") ?: "",
                        productId = doc.getString("productId") ?: "",
                        amountKes = ((doc.getLong("amount") ?: 0L) / 100).toInt(),
                        status = doc.getString("status") ?: "initialized",
                        jobId = (doc.getLong("jobId") ?: 0L).toInt().takeIf { it > 0 },
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        verifiedAt = doc.getTimestamp("verifiedAt")?.toDate()?.time ?: 0L
                    )
                }?.sortedByDescending { it.createdAt } ?: emptyList()
                _paymentsSyncState.value = SyncState(loading = false)
            }
    }

    fun stopPaymentsSync() {
        paymentsListener?.remove()
        paymentsListener = null
    }

    fun retryPaymentsSync() = startPaymentsSync()

    /** Aggregate revenue stats for the Payments page KPI strip. */
    fun paymentStats(): PaymentStats {
        val all = _payments.value
        val paid = all.filter { it.status == "success" }
        val now = System.currentTimeMillis()
        val todayStart = now - (now % 86_400_000L)
        return PaymentStats(
            totalRevenue = paid.sumOf { it.amountKes },
            todayRevenue = paid.filter { it.createdAt >= todayStart }.sumOf { it.amountKes },
            transactions = paid.size,
            pending = all.count { it.status == "initialized" },
            boosts = paid.count { it.kind == "boost" },
            unlocks = paid.count { it.kind == "unlock" },
            subscriptions = paid.count { it.kind == "subscription" }
        )
    }

    data class PaymentStats(
        val totalRevenue: Int,
        val todayRevenue: Int,
        val transactions: Int,
        val pending: Int,
        val boosts: Int,
        val unlocks: Int,
        val subscriptions: Int
    )

    // ==== plan conversion analytics (plan_analytics collection) ====

    data class ConversionStats(
        val signups: Int,          // total docs = users who saw the plan choice
        val choseProAtSignup: Int, // picked Pro on the registration screen
        val converted: Int,        // docs with convertedAt != null (ever upgraded)
        val ratePercent: Int       // converted * 100 / signups
    )

    private val _conversionStats = MutableStateFlow(ConversionStats(0, 0, 0, 0))
    val conversionStats: StateFlow<ConversionStats> = _conversionStats.asStateFlow()

    private var conversionListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** Live funnel from plan_analytics: signups → chose Pro → converted. */
    fun startConversionSync() {
        stopConversionSync()
        val db = fireDb ?: return
        conversionListener = db.collection("plan_analytics")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                val docs = snap.documents
                val converted = docs.count { it.getTimestamp("convertedAt") != null }
                val signups = docs.size
                _conversionStats.value = ConversionStats(
                    signups = signups,
                    choseProAtSignup = docs.count { it.getString("choice") == "pro" },
                    converted = converted,
                    ratePercent = if (signups == 0) 0 else converted * 100 / signups
                )
            }
    }

    fun stopConversionSync() {
        conversionListener?.remove()
        conversionListener = null
    }

    /**
     * Mark a payment as refunded/revoked. For boosts this also removes the
     * job's featured status; entitlements (unlocks/subscriptions) are noted
     * for manual handling. Every action is written to the audit history.
     */
    fun revokePayment(p: AdminPayment, adminEmail: String, onDone: (Boolean) -> Unit = {}) {
        val db = fireDb
        if (db == null) { onDone(false); return }
        db.collection("payments").document(p.reference)
            .update("status", "revoked")
            .addOnSuccessListener {
                if (p.kind == "boost" && p.jobId != null) {
                    db.collection("jobs").document("job_${p.jobId}")
                        .update("featuredUntil", 0L)
                }
                log("↩️", "Revoked ${p.kind} payment ${p.reference} (KSh ${p.amountKes}) by $adminEmail")
                onDone(true)
            }
            .addOnFailureListener { onDone(false) }
    }

    /**
     * Manually grant an entitlement without a payment (goodwill / support):
     * boost a job, add application unlocks, or extend Pro — all audit-logged.
     */
    fun grantEntitlement(
        kind: String,          // boost | unlock | subscription
        uid: String,
        userEmail: String,
        jobId: Int?,
        productId: String,
        adminEmail: String,
        onDone: (Boolean) -> Unit = {}
    ) {
        val db = fireDb
        if (db == null) { onDone(false); return }
        val now = System.currentTimeMillis()
        val days = when (productId) {
            "boost_7d", "pro_yearly" -> 365L
            "pro_monthly" -> 30L
            else -> 3L
        }
        when (kind) {
            "boost" -> {
                if (jobId == null) { onDone(false); return }
                db.collection("jobs").document("job_$jobId")
                    .update("featuredUntil", now + days * 86_400_000L, "featuredPlan", productId)
            }
            "unlock" -> db.collection("users").document(uid)
                .set(mapOf("applicationUnlocks" to com.google.firebase.firestore.FieldValue.increment(1)),
                    com.google.firebase.firestore.SetOptions.merge())
            else -> {
                val userRef = db.collection("users").document(uid)
                userRef.get().addOnSuccessListener { snap ->
                    val current = snap.getTimestamp("proUntil")?.toDate()?.time ?: 0L
                    userRef.set(
                        mapOf("proUntil" to current.coerceAtLeast(now) + days * 86_400_000L, "proPlan" to productId),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            }
        }
        log("🎁", "Granted $productId ($kind) to $userEmail by $adminEmail")
        onDone(true)
    }

    fun stats(): AdminStats {
        val jobs = JobRepository.jobs.value
        val apps = JobRepository.applications.value
        val providerPosts = jobs.filter { it.isProviderJob }
        return AdminStats(
            users = _userCount.value,   // real registered accounts via Firestore listener
            jobs = jobs.count { !it.draft && !it.closed },
            providerJobs = providerPosts.count { !it.closed },
            applications = apps.size,
            adminPosts = providerPosts.size,
            postedToday = providerPosts.count {
                System.currentTimeMillis() - it.postedAtMillis < 24 * 60 * 60 * 1000L
            }
        )
    }

    // ==== user applications to provider jobs ====

    /** Applications to provider jobs routed to admin for approval. */
    private val _pendingApprovals = MutableStateFlow(emptyList<Application>())
    val pendingApprovals: StateFlow<List<Application>> = _pendingApprovals.asStateFlow()

    /** Connection state of the approvals queue for the UI (loading / error / ready). */
    data class SyncState(val loading: Boolean = true, val error: String? = null)
    private val _approvalsSyncState = MutableStateFlow(SyncState())
    val approvalsSyncState: StateFlow<SyncState> = _approvalsSyncState.asStateFlow()

    private var approvalsListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Real-time Firestore loader for the approvals queue: applications to
     * provider jobs are read live from the `applications` collection, so the
     * queue survives restarts and reflects decisions from any device.
     * Call once at portal sign-in.
     */
    fun startApprovalsSync() {
        stopApprovalsSync()
        val db = fireDb ?: run {
            _approvalsSyncState.value = SyncState(loading = false, error = "Firestore unavailable on this device")
            return
        }
        _approvalsSyncState.value = SyncState(loading = true)
        approvalsListener = db.collection("applications")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("PRC-Admin", "approvals sync error: ${err.message}")
                    _approvalsSyncState.value = SyncState(
                        loading = false,
                        error = "Connection lost — showing last known queue (${err.message?.take(80) ?: "unknown"})"
                    )
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val providerTitles = _jobs.value
                    .filter { it.isProviderJob }
                    .associate { it.title to it }
                val loaded = snap.documents.mapNotNull { doc ->
                    val title = doc.getString("jobTitle") ?: return@mapNotNull null
                    val job = providerTitles[title] ?: return@mapNotNull null   // provider jobs only
                    Application(
                        job = job,
                        applicantName = doc.getString("applicantName") ?: "",
                        applicantContact = doc.getString("applicantContact") ?: "",
                        status = doc.getString("status") ?: "Pending",
                        appliedAt = doc.getLong("appliedAt") ?: doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                        coverNote = doc.getString("coverNote") ?: "",
                        privateNote = doc.getString("privateNote") ?: ""
                    )
                }.sortedByDescending { it.appliedAt }
                _pendingApprovals.value = loaded
                _approvalsSyncState.value = SyncState(loading = false, error = null)
                android.util.Log.d("PRC-Admin", "approvals synced: ${loaded.size} provider-job applications")
            }
    }

    /** Manual retry after a connection error. */
    fun retryApprovalsSync() = startApprovalsSync()

    fun stopApprovalsSync() {
        approvalsListener?.remove()
        approvalsListener = null
    }

    /** Called by JobRepository when a user applies to a provider job. */
    fun onApplicationSubmitted(job: Job, applicantName: String, applicantContact: String, note: String) {
        // The queue refreshes via the Firestore listener; just log locally.
        log("🙋", "$applicantName applied to \"${job.title}\" — awaiting approval")
    }

    /**
     * Mirror an application change to Firestore using the SAME stable doc id
     * as JobRepository.apply() so admin decisions update the applicant's own
     * "My Applications" view too.
     */
    private fun persistApproval(
        jobTitle: String,
        provider: String,
        applicantName: String,
        applicantContact: String,
        status: String,
        note: String = "",
        privateNote: String? = null,
        jobId: Int = -1
    ) {
        try {
            val db = fireDb ?: return
            val docId = "app_${applicantContact.hashCode()}_${if (jobId > 0) jobId else jobTitle.hashCode().toUInt().toInt()}"
            val doc = buildMap<String, Any> {
                put("jobTitle", jobTitle)
                put("provider", provider)
                put("applicantName", applicantName)
                put("applicantContact", applicantContact)
                put("status", status)
                put("coverNote", note)
                put("updatedAt", System.currentTimeMillis())
                if (jobId > 0) put("jobId", jobId)
                privateNote?.takeIf { it.isNotBlank() }?.let { put("privateNote", it) }
            }
            db.collection("applications").document(docId)
                .set(doc, SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("PRC-Admin", "application saved: $applicantContact -> $jobTitle ($status)")
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("PRC-Admin", "application save FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Admin", "Firestore unavailable: ${e.message}")
        }
    }

    /** Admin decision on a pending/shortlisted application — persisted + applicant notified. */
    fun decideApproval(applicantContact: String, approved: Boolean) {
        val app = _pendingApprovals.value.find {
            (it.status == "Pending" || it.status == "Shortlisted") &&
                    it.applicantContact == applicantContact
        } ?: return
        val newStatus = if (approved) "Accepted" else "Rejected"
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it == app) it.copy(status = newStatus) else it
        }
        val verdict = if (approved) "approved" else "rejected"
        log(if (approved) "✅" else "🚫", "$verdict ${app.applicantName}'s application to \"${app.job.title}\"")
        persistApproval(
            jobTitle = app.job.title,
            provider = app.job.postedBy,
            applicantName = app.applicantName,
            applicantContact = applicantContact,
            status = newStatus,
            note = app.coverNote ?: "",
            privateNote = app.privateNote,
            jobId = app.job.id
        )
        // Ring the applicant's bell directly (status "Accepted"/"Rejected"
        // matches what the user Applications screen displays).
        NotificationRepository.onDecision(app.job, applicantContact, app.applicantName, approved)
    }

    /** Soft-accept: keep a pending application in the queue but mark it promising. */
    fun shortlistApproval(applicantContact: String) {
        val app = _pendingApprovals.value.find {
            it.applicantContact == applicantContact && it.status == "Pending"
        } ?: return
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it == app) it.copy(status = "Shortlisted") else it
        }
        log("⭐", "Shortlisted ${app.applicantName} for \"${app.job.title}\"")
        persistApproval(
            jobTitle = app.job.title,
            provider = app.job.postedBy,
            applicantName = app.applicantName,
            applicantContact = applicantContact,
            status = "Shortlisted",
            note = app.coverNote ?: "",
            privateNote = app.privateNote,
            jobId = app.job.id
        )
    }

    /** Move a shortlisted/rejected application back into the pending queue (persisted). */
    fun reopenApproval(applicantContact: String) {
        val app = _pendingApprovals.value.find {
            it.applicantContact == applicantContact && it.status != "Pending"
        } ?: return
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it.applicantContact == applicantContact && it.status != "Pending")
                it.copy(status = "Pending") else it
        }
        persistApproval(
            jobTitle = app.job.title,
            provider = app.job.postedBy,
            applicantName = app.applicantName,
            applicantContact = applicantContact,
            status = "Pending",
            note = app.coverNote ?: "",
            privateNote = app.privateNote,
            jobId = app.job.id
        )
    }

    /** Decide every pending application at once (bulk action, persisted). */
    fun decideAllPending(approved: Boolean): Int {
        val targets = _pendingApprovals.value.filter { it.status == "Pending" }
        if (targets.isEmpty()) return 0
        val newStatus = if (approved) "Accepted" else "Rejected"
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it.status == "Pending") it.copy(status = newStatus) else it
        }
        targets.forEach { app ->
            persistApproval(
                jobTitle = app.job.title,
                provider = app.job.postedBy,
                applicantName = app.applicantName,
                applicantContact = app.applicantContact,
                status = newStatus,
                note = app.coverNote ?: "",
                privateNote = app.privateNote,
                jobId = app.job.id
            )
            NotificationRepository.onDecision(app.job, app.applicantContact, app.applicantName, approved)
        }
        log(
            if (approved) "✅" else "🚫",
            "Bulk-${if (approved) "approved" else "rejected"} ${targets.size} pending application${if (targets.size == 1) "" else "s"}"
        )
        return targets.size
    }

    /** Private admin note on any application (persisted, visible only in the portal). */
    fun saveApprovalNote(applicantContact: String, note: String) {
        val app = _pendingApprovals.value.find { it.applicantContact == applicantContact } ?: return
        _pendingApprovals.value = _pendingApprovals.value.map {
            if (it.applicantContact == applicantContact) it.copy(privateNote = note) else it
        }
        persistApproval(
            jobTitle = app.job.title,
            provider = app.job.postedBy,
            applicantName = app.applicantName,
            applicantContact = applicantContact,
            status = app.status,
            note = app.coverNote ?: "",
            privateNote = note,
            jobId = app.job.id
        )
    }

    /** Filter the queue by a free-text query over applicant, job title and provider. */
    fun searchApprovals(query: String): List<Application> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return _pendingApprovals.value
        return _pendingApprovals.value.filter {
            it.applicantName.lowercase().contains(q) ||
                    it.job.title.lowercase().contains(q) ||
                    it.job.postedBy.lowercase().contains(q)
        }
    }

    /**
     * Append an entry to the audit log: written to the Firestore
     * `activity_history` collection (cross-device, survives restarts) and
     * mirrored into local state for instant display.
     */
    private fun log(icon: String, message: String) {
        val entry = AdminActivity(nextActivityId++, icon, message)
        _activity.value = listOf(entry) + _activity.value.take(60)
        try {
            val db = fireDb ?: return
            db.collection("activity_history")
                .add(
                    mapOf(
                        "icon" to icon,
                        "message" to message,
                        "at" to entry.at,
                        "admin" to (_adminUser.value?.email ?: "system")
                    )
                )
                .addOnFailureListener { e ->
                    android.util.Log.w("PRC-Admin", "activity write FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Admin", "Firestore unavailable: ${e.message}")
        }
    }

    /**
     * Listen to the audit log (newest first). Call on admin sign-in; replaces
     * the local-only history with the shared Firestore record.
     */
    fun startActivitySync() {
        stopActivitySync()
        val db = fireDb ?: run {
            _activitySyncState.value = SyncState(loading = false, error = "Firestore unavailable on this device")
            return
        }
        _activitySyncState.value = SyncState(loading = true)
        activityListener = db.collection("activity_history")
            .orderBy("at", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(60)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    android.util.Log.w("PRC-Admin", "activity sync error: ${err.message}")
                    _activitySyncState.value = SyncState(loading = false, error = "Audit history offline — ${err.message?.take(80) ?: "unknown"}")
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                _activitySyncState.value = SyncState(loading = false, error = null)
                _activity.value = snap.documents.mapNotNull { doc ->
                    val at = doc.getLong("at") ?: return@mapNotNull null
                    AdminActivity(
                        id = at.toInt(),
                        icon = doc.getString("icon") ?: "•",
                        message = doc.getString("message") ?: return@mapNotNull null,
                        at = at,
                        admin = doc.getString("admin") ?: ""
                    )
                }
            }
    }

    fun stopActivitySync() {
        activityListener?.remove()
        activityListener = null
    }

}
