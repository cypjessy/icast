package com.prc.app.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Application(
    val job: Job,
    val applicantName: String,   // for jobs the user posted: who applied
    val applicantContact: String,
    val status: String,          // Pending | Shortlisted | Accepted | Rejected
    val appliedAt: Long = System.currentTimeMillis(),
    val coverNote: String? = null,
    val privateNote: String = "",
    val cvName: String = "",                       // CV attached to this application
    val cvUrl: String = "",                        // Firebase Storage download URL (may be blank)
    val answers: Map<String, String> = emptyMap()   // screening question -> answer
)

/**
 * Fake job data + in-memory applications. Swap for an API-backed repo later;
 * screens only see this surface.
 */
object JobRepository {

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _applications = MutableStateFlow<List<Application>>(emptyList())
    val applications: StateFlow<List<Application>> = _applications.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    // ==== Firestore sync ====
    private var firestore = try { FirebaseFirestore.getInstance() } catch (_: Exception) { null }
    private var jobsListener: ListenerRegistration? = null
    private var applicationsListener: ListenerRegistration? = null
    private var syncingFromFirestore = false   // suppress persist during remote updates
    /** Highest local job id seen (sample + Firestore), so new posts never collide. */
    private var maxSeenId = 0

    /**
     * Start real-time sync of jobs + applications from Firestore.
     * Call once from MainActivity after AuthRepository.init(). Sample jobs stay
     * as the base; Firestore documents merge in keyed by job_{localId}, and the
     * local id counter advances past anything remote.
     */
    fun startFirestoreSync() {
        val db = firestore ?: run {
            Log.w("PRC-Jobs", "Firestore unavailable — running on sample data only")
            return
        }

        maxSeenId = (_jobs.value.maxOfOrNull { it.id } ?: 0).coerceAtLeast(maxSeenId)

        jobsListener?.remove()
        jobsListener = db.collection("jobs").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w("PRC-Jobs", "jobs sync error: ${err.message}")
                return@addSnapshotListener
            }
            if (snap == null || snap.isEmpty) return@addSnapshotListener

            syncingFromFirestore = true
            val remote = snap.documents.mapNotNull { it.toJob() }
            if (remote.isNotEmpty()) {
                maxSeenId = maxOf(maxSeenId, remote.maxOf { it.id })
                if (nextId <= maxSeenId) nextId = maxSeenId + 1

                val byId = remote.associateBy { it.id }
                val merged = (_jobs.value.map { byId[it.id] ?: it } + remote.filter { r -> _jobs.value.none { it.id == r.id } })
                    .distinctBy { it.id }
                    .sortedByDescending { it.postedAtMillis }
                _jobs.value = merged
            }
            syncingFromFirestore = false
            Log.d("PRC-Jobs", "jobs synced from Firestore: ${remote.size} docs -> " +
                    remote.joinToString { "id=${it.id} src=${it.source} title=${it.title.take(30)}" })

            // Clean up stray auto-id docs left by the earlier add()-based persistence.
            val localIds = snap.documents.mapNotNull { doc ->
                (doc.get("localId") as? Long ?: doc.get("localId") as? Int)?.toInt()
            }
            // 1) delete docs whose id doesn't match job_{localId} (duplicates)
            snap.documents.forEach { doc ->
                val lid = (doc.get("localId") as? Long ?: doc.get("localId") as? Int)?.toInt()
                if (lid != null && doc.id != "job_$lid") {
                    db.collection("jobs").document(doc.id).delete()
                    Log.d("PRC-Jobs", "deleted duplicate doc ${doc.id}")
                }
            }
        }
        startApplicationsSync()
    }

    /**
     * Manual re-sync: force a full re-read of jobs + applications.
     * The snapshot listeners stay live; this just guarantees fresh data now
     * (covers cases where a listener missed events while offline).
     */
    suspend fun refresh() {
        val db = firestore ?: return
        if (_syncing.value) return
        _syncing.value = true
        try {
            val jobsSnap = db.collection("jobs").get().await()
            val remote = jobsSnap.documents.mapNotNull { it.toJob() }
            if (remote.isNotEmpty()) {
                maxSeenId = maxOf(maxSeenId, remote.maxOf { it.id })
                if (nextId <= maxSeenId) nextId = maxSeenId + 1
                val byId = remote.associateBy { it.id }
                val merged = (_jobs.value.map { byId[it.id] ?: it } +
                        remote.filter { r -> _jobs.value.none { it.id == r.id } })
                    .distinctBy { it.id }
                    .sortedByDescending { it.postedAtMillis }
                _jobs.value = merged
            }
            val appsSnap = db.collection("applications").get().await()
            val remoteApps = appsSnap.documents.mapNotNull { it.toApplication() }
            if (remoteApps.isNotEmpty()) {
                val keyOf: (Application) -> String = { it.applicantContact + "|" + it.job.title }
                val remoteByKey = remoteApps.associateBy(keyOf)
                val local = _applications.value.filter { keyOf(it) !in remoteByKey }
                val resolved = remoteByKey.values.mapNotNull { ra ->
                    val job = _jobs.value.find { it.title == ra.job.title && it.postedBy == ra.job.postedBy }
                        ?: _jobs.value.find { it.title == ra.job.title }
                    job?.let { ra.copy(job = it) }
                }
                _applications.value = resolved + local
            }
            Log.d("PRC-Jobs", "manual refresh: ${remote.size} jobs, ${remoteApps.size} applications")
        } catch (e: Exception) {
            Log.w("PRC-Jobs", "manual refresh failed: ${e.message}")
        } finally {
            // small delay so the spinner is visible even on instant responses
            kotlinx.coroutines.delay(400)
            _syncing.value = false
        }
    }

    private fun startApplicationsSync() {
        val db = firestore ?: return
        applicationsListener?.remove()
        applicationsListener = db.collection("applications").addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w("PRC-Jobs", "applications sync error: ${err.message}")
                return@addSnapshotListener
            }
            if (snap == null) return@addSnapshotListener

            syncingFromFirestore = true
            val remoteApps = snap.documents.mapNotNull { it.toApplication() }
            if (remoteApps.isNotEmpty()) {
                // Merge by (applicantContact + jobTitle) key; remote wins.
                val keyOf: (Application) -> String = { it.applicantContact + "|" + it.job.title }
                val remoteByKey = remoteApps.associateBy(keyOf)
                val local = _applications.value.filter { keyOf(it) !in remoteByKey }
                // Resolve job references for remote applications against current jobs
                val resolved = remoteByKey.values.mapNotNull { ra ->
                    val job = _jobs.value.find { it.title == ra.job.title && it.postedBy == ra.job.postedBy }
                        ?: _jobs.value.find { it.title == ra.job.title }
                    job?.let { ra.copy(job = it) }
                }
                _applications.value = resolved + local
            }
            syncingFromFirestore = false
            Log.d("PRC-Jobs", "applications synced from Firestore: ${remoteApps.size} docs")
        }
    }

    /** Public wrapper so the digest worker can parse remote job docs. */
    fun parseRemoteJob(doc: com.google.firebase.firestore.DocumentSnapshot): Job? = doc.toJob()

    private fun com.google.firebase.firestore.DocumentSnapshot.toJob(): Job? {
        val id = (get("localId") as? Long ?: get("localId") as? Int)?.toInt() ?: return null
        val title = getString("title") ?: return null
        @Suppress("UNCHECKED_CAST")
        fun strList(key: String): List<String> = (get(key) as? List<String>) ?: emptyList()
        return Job(
            id = id,
            title = title,
            category = getString("category") ?: "Retail",
            location = getString("location") ?: "",
            pay = getString("pay") ?: "",
            type = getString("type") ?: "Full-time",
            postedAgo = relativeAgo(getLong("postedAtMillis") ?: 0L),
            description = getString("description") ?: "",
            requirements = strList("requirements"),
            postedBy = getString("postedBy") ?: "",
            posterContact = getString("posterContact") ?: "",
            deadlineDays = (get("deadlineDays") as? Long)?.toInt(),
            openings = (get("openings") as? Long)?.toInt() ?: 1,
            remoteOk = getBoolean("remoteOk") ?: false,
            experience = getString("experience") ?: "",
            skills = strList("skills"),
            draft = getBoolean("draft") ?: false,
            closed = getBoolean("closed") ?: false,
            source = getString("source") ?: "community",
            applicationUrl = getString("applicationUrl") ?: "",
            isCompany = getBoolean("isCompany") ?: false,
            featuredUntil = getLong("featuredUntil") ?: 0L,
            premium = getBoolean("premium") ?: false,
            postedAtMillis = getLong("postedAtMillis") ?: System.currentTimeMillis()
        )
    }

    private fun relativeAgo(millis: Long): String {
        if (millis <= 0) return "Just now"
        val diff = System.currentTimeMillis() - millis
        val hour = 60 * 60 * 1000L
        val day = 24 * hour
        return when {
            diff < hour -> "Just now"
            diff < day -> "${diff / hour}h ago"
            diff < 7 * day -> "${diff / day}d ago"
            else -> "${diff / (7 * day)}w ago"
        }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toApplication(): Application? {
        val applicantContact = getString("applicantContact") ?: return null
        val applicantName = getString("applicantName") ?: return null
        val jobTitle = getString("jobTitle") ?: return null
        val provider = getString("provider") ?: ""
        val status = getString("status") ?: "Pending"
        val note = getString("coverNote") ?: ""
        val updated = getLong("updatedAt") ?: System.currentTimeMillis()
        // Job reference resolved later against the jobs list; placeholder keeps
        // the title/postedBy so matching works.
        return Application(
            job = Job(
                id = (get("jobId") as? Long)?.toInt() ?: -1,
                title = jobTitle, category = "", location = "", pay = "", type = "",
                postedAgo = "", description = "", requirements = emptyList(), postedBy = provider,
                postedAtMillis = updated
            ),
            applicantName = applicantName,
            applicantContact = applicantContact,
            status = status,
            appliedAt = (getLong("appliedAt") ?: updated),
            coverNote = note,
            privateNote = getString("privateNote") ?: "",
            cvName = getString("cvName") ?: "",
            cvUrl = getString("cvUrl") ?: "",
            answers = @Suppress("UNCHECKED_CAST") (get("answers") as? Map<String, String>) ?: emptyMap()
        )
    }

    val categories = listOf(
        "All",
        "IT & Software", "Medicine & Health", "Human Resources", "Finance & Accounting",
        "Sales & Marketing", "Education", "Engineering", "Law & Governance",
        "Agriculture", "Hospitality & Tourism", "Logistics & Supply Chain",
        "Media & Creative", "Retail", "Domestic", "Driving", "Construction", "Skilled trade"
    )

    /** Professional groups for the two-step post-job picker: group -> subcategories. */
    val categoryGroups: List<Pair<String, List<String>>> = listOf(
        "Technology & Engineering" to listOf("IT & Software", "Engineering", "Skilled trade", "Construction"),
        "Health & Education" to listOf("Medicine & Health", "Education"),
        "Business & Professional" to listOf("Finance & Accounting", "Human Resources", "Sales & Marketing", "Law & Governance"),
        "Services & Operations" to listOf("Hospitality & Tourism", "Retail", "Domestic", "Driving", "Logistics & Supply Chain", "Agriculture"),
        "Media & Creative" to listOf("Media & Creative")
    )

    /** The group containing [category], or null if unknown. */
    fun groupOf(category: String): String? =
        categoryGroups.firstOrNull { category in it.second }?.first

    /** Keyword -> subcategory hints for live suggestions from a job title. */
    private val categoryHints = listOf(
        listOf("software", "developer", "programmer", "web", "app ", "it ", "data", "network", "system admin", "support") to "IT & Software",
        listOf("nurse", "doctor", "clinical", "medical", "health", "caregiver", "pharmacy", "lab") to "Medicine & Health",
        listOf("teacher", "tutor", "lecturer", "school", "teaching", "education", "trainer") to "Education",
        listOf("accountant", "accounting", "finance", "bookkeep", "auditor", "cashier", "credit") to "Finance & Accounting",
        listOf("hr ", "human resource", "recruit", "talent") to "Human Resources",
        listOf("sales", "marketing", "brand", "business development") to "Sales & Marketing",
        listOf("lawyer", "advocate", "legal", "paralegal", "compliance") to "Law & Governance",
        listOf("engineer", "surveyor", "architect", "civil ", "mechanical ", "electrical engineer") to "Engineering",
        listOf("plumber", "electrician", "carpenter", "welder", "mechanic", "technician", "wiring") to "Skilled trade",
        listOf("mason", "construction", "builder", "site foreman", "brick", "plaster") to "Construction",
        listOf("farm", "harvest", "agricultur", "greenhouse", "cattle", "poultry", "crop") to "Agriculture",
        listOf("hotel", "waiter", "waitress", "cook", "chef", "kitchen", "tourism", "travel", "housekeep") to "Hospitality & Tourism",
        listOf("shop", "retail", "attendant", "supermarket", "storekeeper", "merchandiser") to "Retail",
        listOf("house girl", "househelp", "house help", "nanny", "cleaner", "domestic", "maid") to "Domestic",
        listOf("driver", "driving", "matatu", "lorry", "taxi", "boda", "courier", "rider") to "Driving",
        listOf("logistic", "supply chain", "warehouse", "fleet", "procurement", "stock", "dispatcher") to "Logistics & Supply Chain",
        listOf("design", "content", "writer", "video", "photograph", "social media", "media", "journalist", "editor") to "Media & Creative"
    )

    /**
     * Suggest a subcategory from a (partial) job title. Returns null when no
     * keyword matches yet. First match wins — hints are ordered so the most
     * specific trades come before generic terms.
     */
    fun suggestCategory(title: String): String? {
        val t = title.lowercase()
        if (t.trim().length < 3) return null
        return categoryHints.firstOrNull { (keywords, _) -> keywords.any { t.contains(it) } }?.second
    }

    private var nextId = 17

    fun job(id: Int): Job? = _jobs.value.find { it.id == id }

    /** Post a new job; it appears in the public feed immediately. */
    fun postJob(
        posterName: String,
        posterContact: String,
        title: String,
        category: String,
        location: String,
        pay: String,
        type: String,
        description: String,
        openings: Int = 1,
        remoteOk: Boolean = false,
        responsibilities: List<String> = emptyList(),
        requirements: List<String> = emptyList(),
        experience: String = "",
        skills: List<String> = emptyList(),
        deadlineDays: Int? = null,
        negotiable: Boolean = false,
        premium: Boolean = false,
        draft: Boolean = false
    ): Job {
        val job = Job(
            id = nextId++,
            title = title,
            category = category,
            location = location,
            pay = pay,
            type = type,
            postedAgo = if (draft) "Draft" else "Just now",
            description = description,
            requirements = requirements.ifEmpty { listOf("Contact $posterContact through the app") },
            postedBy = posterName,
            posterContact = posterContact,
            deadlineDays = deadlineDays,
            openings = openings,
            remoteOk = remoteOk,
            responsibilities = responsibilities,
            experience = experience,
            skills = skills,
            negotiable = negotiable,
            premium = premium,
            draft = draft
        )
        _jobs.value = listOf(job) + _jobs.value
        persistJob(job)
        return job
    }

    /** Update an existing posting (edit flow). Keeps id, poster and posted-age. */
    fun updateJob(
        jobId: Int,
        title: String,
        category: String,
        location: String,
        pay: String,
        type: String,
        description: String,
        openings: Int,
        remoteOk: Boolean,
        responsibilities: List<String>,
        requirements: List<String>,
        experience: String,
        skills: List<String>,
        deadlineDays: Int?,
        negotiable: Boolean,
        premium: Boolean = false
    ): Job? {
        val existing = _jobs.value.find { it.id == jobId } ?: return null
        val updated = existing.copy(
            title = title,
            category = category,
            location = location,
            pay = pay,
            type = type,
            description = description,
            openings = openings,
            remoteOk = remoteOk,
            responsibilities = responsibilities,
            requirements = requirements.ifEmpty { existing.requirements },
            experience = experience,
            skills = skills,
            deadlineDays = deadlineDays,
            negotiable = negotiable,
            premium = premium,
            draft = false
        )
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        _jobs.value.find { it.id == jobId }?.let { persistJob(it) }
        return updated
    }

    /** Publish a draft: it becomes visible in the public feed. */
    fun publishDraft(jobId: Int): Job? {
        val draft = _jobs.value.find { it.id == jobId && it.draft } ?: return null
        val published = draft.copy(draft = false, closed = false, postedAgo = "Just now")
        _jobs.value = _jobs.value.map { if (it.id == jobId) published else it }
        persistJob(published)
        return published
    }

    /** Close a job to new applications (poster action). */
    fun closeJob(jobId: Int): Job? {
        val target = _jobs.value.find { it.id == jobId } ?: return null
        val closedJob = target.copy(closed = true, draft = false)
        _jobs.value = _jobs.value.map { if (it.id == jobId) closedJob else it }
        persistJob(closedJob)
        return closedJob
    }

    /** Reopen a closed job. */
    fun reopenJob(jobId: Int): Job? {
        val target = _jobs.value.find { it.id == jobId } ?: return null
        val reopened = target.copy(closed = false)
        _jobs.value = _jobs.value.map { if (it.id == jobId) reopened else it }
        persistJob(reopened)
        return reopened
    }

    /** Duplicate a job as a new posting (keeps all fields, new id, public immediately). */
    fun duplicateJob(jobId: Int): Job? {
        val src = _jobs.value.find { it.id == jobId } ?: return null
        val copy = src.copy(
            id = nextId++,
            postedAgo = "Just now",
            draft = false,
            closed = false,
            deadlineDays = src.deadlineDays
        )
        _jobs.value = listOf(copy) + _jobs.value
        persistJob(copy)
        return copy
    }

    /** Jobs the given user posted. */
    fun myJobs(posterContact: String): List<Job> =
        _jobs.value.filter { it.posterContact == posterContact }

    /** Applications received for a given job (visible to the poster). */
    fun applicantsFor(jobId: Int): List<Application> =
        _applications.value.filter { it.job.id == jobId }

    /** Poster shortlists an applicant (soft-accept for later review). */
    fun shortlist(jobId: Int, applicantContact: String) {
        _applications.value = _applications.value.map {
            if (it.job.id == jobId && it.applicantContact == applicantContact && it.status == "Pending")
                it.copy(status = "Shortlisted") else it
        }
        _applications.value.find {
            it.job.id == jobId && it.applicantContact == applicantContact && it.status == "Shortlisted"
        }?.let { persistApplication(it) }
    }

    /** Poster accepts/rejects an applicant. */
    fun decide(jobId: Int, applicantContact: String, accepted: Boolean) {
        val job = job(jobId) ?: return
        val applicant = _applications.value.find {
            it.job.id == jobId && it.applicantContact == applicantContact
        } ?: return
        val updated = _applications.value.map {
            if (it.job.id == jobId && it.applicantContact == applicantContact)
                it.copy(status = if (accepted) "Accepted" else "Rejected") else it
        }
        _applications.value = updated
        updated.find { it.job.id == jobId && it.applicantContact == applicantContact }
            ?.let { persistApplication(it) }
        NotificationRepository.onDecision(job, applicantContact, applicant.applicantName, accepted)
    }

    /** Update only the status of an application (used by the stepper/decision bar). */
    fun setStatus(jobId: Int, applicantContact: String, status: String) {
        val job = job(jobId) ?: return
        val applicant = _applications.value.find {
            it.job.id == jobId && it.applicantContact == applicantContact
        } ?: return
        val updated = _applications.value.map {
            if (it.job.id == jobId && it.applicantContact == applicantContact)
                it.copy(status = status) else it
        }
        _applications.value = updated
        updated.find { it.job.id == jobId && it.applicantContact == applicantContact }
            ?.let { persistApplication(it) }
        when (status) {
            "Accepted" -> NotificationRepository.onDecision(job, applicantContact, applicant.applicantName, true)
            "Rejected" -> NotificationRepository.onDecision(job, applicantContact, applicant.applicantName, false)
            else -> Unit
        }
    }

    /** Employer-only private note attached to one application. */
    fun saveNote(jobId: Int, applicantContact: String, note: String) {
        val updated = _applications.value.map {
            if (it.job.id == jobId && it.applicantContact == applicantContact)
                it.copy(privateNote = note) else it
        }
        _applications.value = updated
        updated.find { it.job.id == jobId && it.applicantContact == applicantContact }
            ?.let { persistApplication(it) }
    }

    /** Persist one application to Firestore (merge by stable doc id). */
    private fun persistApplication(application: Application) {
        if (syncingFromFirestore) return
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val doc = mapOf(
                // Ownership stamp for security rules — set client-side from the
                // signed-in session, never accepted from untrusted callers.
                "applicantUid" to AuthRepository.currentUid(),
                "jobId" to application.job.id,
                "jobTitle" to application.job.title,
                "provider" to application.job.postedBy,
                "applicantName" to application.applicantName,
                "applicantContact" to application.applicantContact,
                "status" to application.status,
                "coverNote" to (application.coverNote ?: ""),
                "privateNote" to application.privateNote,
                "cvName" to application.cvName,
                "cvUrl" to application.cvUrl,
                "answers" to application.answers,
                "appliedAt" to application.appliedAt,
                "updatedAt" to System.currentTimeMillis(),
                "updatedAtServer" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            val docId = "app_${application.applicantContact.hashCode()}_${application.job.id}"
            db.collection("applications").document(docId)
                .set(doc, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("PRC-Jobs", "application saved to Firestore: $docId")
                }
                .addOnFailureListener { e ->
                    Log.w("PRC-Jobs", "firestore application save FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("PRC-Jobs", "Firestore unavailable: ${e.message}")
        }
    }

    fun search(query: String, category: String?): List<Job> {
        val q = query.trim().lowercase()
        return _jobs.value.filter { job ->
            if (job.draft || job.closed) return@filter false
            val matchesCat = category == null || category == "All" || job.category == category
            val matchesQuery = q.isEmpty() ||
                    job.title.lowercase().contains(q) ||
                    job.category.lowercase().contains(q) ||
                    job.location.lowercase().contains(q) ||
                    job.description.lowercase().contains(q) ||
                    job.postedBy.lowercase().contains(q) ||
                    job.skills.any { it.lowercase().contains(q) } ||
                    job.requirements.any { it.lowercase().contains(q) }
            matchesCat && matchesQuery
        }
    }

    fun byCategory(category: String): List<Job> = search("", category)

    /** @return true if a new application was recorded, false if the user had already applied. */
    fun apply(
        job: Job,
        applicantName: String,
        applicantContact: String,
        note: String = "",
        cvName: String = "",
        cvUrl: String = "",
        answers: Map<String, String> = emptyMap()
    ): Boolean {
        if (job.draft || job.closed) return false
        if (_applications.value.any { it.job.id == job.id && it.applicantContact == applicantContact }) return false
        val application = Application(
            job = job,
            applicantName = applicantName,
            applicantContact = applicantContact,
            status = "Pending",
            coverNote = note,
            cvName = cvName,
            cvUrl = cvUrl,
            answers = answers
        )
        _applications.value = listOf(application) + _applications.value
        persistApplication(application)
        // Provider jobs route through admin for approval instead of notifying a poster.
        if (job.isProviderJob) {
            AdminRepository.onApplicationSubmitted(job, applicantName, applicantContact, note)
        } else {
            NotificationRepository.onNewApplication(job, applicantName)
        }
        return true
    }

    fun hasApplied(jobId: Int, applicantContact: String): Boolean =
        _applications.value.any { it.job.id == jobId && it.applicantContact == applicantContact }

    /** Applicant withdraws a pending application; removal persists to Firestore. */
    fun withdraw(jobId: Int, applicantContact: String) {
        val app = _applications.value.find {
            it.job.id == jobId && it.applicantContact == applicantContact && it.status == "Pending"
        } ?: return
        _applications.value = _applications.value - app
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("applications")
                .document("app_${applicantContact.hashCode()}_${jobId}")
                .delete()
                .addOnFailureListener { e ->
                    Log.w("PRC-Jobs", "firestore application delete FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w("PRC-Jobs", "Firestore unavailable: ${e.message}")
        }
    }


    /**
     * One-time backfill: provider jobs written before the AI company/
     * individual classification existed have no `isCompany` field (reads as
     * false). Mark them all as companies so they keep appearing under
     * "Companies hiring". Runs once per install; individual jobs posted
     * after the classifier shipped are unaffected.
     */
    fun backfillIsCompanyForProviderJobs(context: android.content.Context) {
        val prefs = context.getSharedPreferences("prc_migrations", android.content.Context.MODE_PRIVATE)
        val done = try {
            prefs?.getBoolean("iscompany_backfill_done", false) ?: false
        } catch (_: Exception) { false }
        if (done) return
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3000)   // let the Firestore snapshot land first
            val targets = _jobs.value.filter { it.isProviderJob && !it.isCompany }
            if (targets.isEmpty()) {
                prefs?.edit()?.putBoolean("iscompany_backfill_done", true)?.apply()
                return@launch
            }
            // A job can only be listed as a company if a company NAME was actually
            // identified — a blank poster name is not a company.
            val eligible = targets.filter { it.postedBy.isNotBlank() }
            eligible.forEach { job ->
                // update local state + write the flag to Firestore
                _jobs.value = _jobs.value.map { if (it.id == job.id) it.copy(isCompany = true) else it }
                persistJob(job.copy(isCompany = true))
                Log.d("PRC-Jobs", "backfilled isCompany=true for job_${job.id}")
            }
            prefs?.edit()?.putBoolean("iscompany_backfill_done", true)?.apply()
            Log.d("PRC-Jobs", "isCompany backfill complete: ${targets.size} provider jobs updated")
        }
    }

    /**
     * One-time migration: map legacy categories to the professional taxonomy
     * on every locally-saved job that references an old name. Covers saved-
     * job references implicitly (saved jobs resolve by id at render time, so
     * migrating the job records themselves updates the Saved screen too).
     * Runs once per install; legacy category docs in Firestore are updated
     * in place so other devices converge on the same values.
     */
    fun migrateLegacyCategories(context: android.content.Context) {
        val prefs = context.getSharedPreferences("prc_migrations", android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean("category_migration_done", false)) return
        CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(3500)   // after both Firestore snapshots land
            val mapping = legacyCategoryMap()
            val legacy = _jobs.value.filter { it.category in mapping }
            legacy.forEach { job ->
                val newCat = mapping.getValue(job.category)
                _jobs.value = _jobs.value.map { if (it.id == job.id) it.copy(category = newCat) else it }
                persistJob(_jobs.value.find { it.id == job.id } ?: job)
                Log.d("PRC-Jobs", "migrated category '${job.category}' -> '$newCat' (job_${job.id})")
            }
            prefs.edit().putBoolean("category_migration_done", true).apply()
            Log.d("PRC-Jobs", "category migration complete: ${legacy.size} jobs updated")
        }
    }

    /** Legacy -> professional category mapping. */
    fun legacyCategoryMap(): Map<String, String> = mapOf(
        "Farming" to "Agriculture",
        "Delivery" to "Logistics & Supply Chain",
        "Hospitality" to "Hospitality & Tourism",
        "Skilled trade" to "Skilled trade"   // unchanged name; kept for completeness
    )

    /** Permanently delete a job (local + Firestore). Applications to it stay for records. */
    fun deleteJob(jobId: Int): Boolean {
        val target = _jobs.value.find { it.id == jobId } ?: return false
        _jobs.value = _jobs.value - target
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            db.collection("jobs").document("job_$jobId")
                .delete()
                .addOnSuccessListener { Log.d("PRC-Jobs", "job deleted from Firestore: job_$jobId") }
                .addOnFailureListener { e -> Log.w("PRC-Jobs", "firestore job delete FAILED: ${e.message}") }
        } catch (e: Exception) {
            Log.w("PRC-Jobs", "Firestore unavailable: ${e.message}")
        }
        return true
    }

    /**
     * Admin edit of a provider job: updates the editable fields and persists
     * to Firestore under the stable doc id. @return the updated job or null.
     */
    fun updateProviderJob(
        jobId: Int,
        title: String,
        category: String,
        location: String,
        pay: String,
        type: String,
        description: String,
        openings: Int,
        experience: String,
        applicationUrl: String,
        isCompany: Boolean
    ): Job? {
        val existing = _jobs.value.find { it.id == jobId && it.isProviderJob } ?: return null
        val updated = existing.copy(
            title = title.trim(),
            category = category,
            location = location.trim(),
            pay = pay.trim(),
            type = type,
            description = description.trim(),
            openings = openings.coerceAtLeast(1),
            experience = experience.trim(),
            applicationUrl = applicationUrl.trim(),
            isCompany = isCompany
        )
        _jobs.value = _jobs.value.map { if (it.id == jobId) updated else it }
        persistJob(updated)
        return updated
    }

    fun publishProviderJob(job: Job): Job? {
        if (!job.isProviderJob) return null
        val published = job.copy(
            id = nextId++,
            postedAgo = "Just now",
            draft = false,
            closed = false
        )
        _jobs.value = listOf(published) + _jobs.value
        persistJob(published)
        return published
    }

    /**
     * Mirror any job (community or provider) into Firestore under a stable
     * document id so re-posts update instead of duplicating. Success/failure
     * is logged so posting problems are visible in logcat.
     */    private fun persistJob(job: Job) {
        if (syncingFromFirestore) return
        try {
            val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val doc = mapOf(
                "localId" to job.id,
                "title" to job.title,
                "category" to job.category,
                "location" to job.location,
                "pay" to job.pay,
                "type" to job.type,
                "description" to job.description,
                "requirements" to job.requirements,
                "skills" to job.skills,
                "postedBy" to job.postedBy,
                "posterContact" to job.posterContact,
                "source" to job.source,
                "applicationUrl" to job.applicationUrl,
                "openings" to job.openings,
                "remoteOk" to job.remoteOk,
                "experience" to job.experience,
                "deadlineDays" to job.deadlineDays,
                "closed" to job.closed,
                "draft" to job.draft,
                "postedAtMillis" to job.postedAtMillis,
                "isCompany" to job.isCompany,
                "featuredUntil" to job.featuredUntil,
                "premium" to job.premium,
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            db.collection("jobs").document("job_${job.id}")
                .set(doc, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    android.util.Log.d("PRC-Jobs", "job saved to Firestore: job_${job.id} (\"${job.title}\")")
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("PRC-Jobs", "firestore job save FAILED: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("PRC-Jobs", "Firestore unavailable: ${e.message}")
        }
    }

    /** Provider-sourced jobs already published via the admin portal. */
}
