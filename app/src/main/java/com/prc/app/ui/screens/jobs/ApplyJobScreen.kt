package com.prc.app.ui.screens.jobs

import com.prc.app.ui.theme.SystemBarAppearance

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.ui.components.categoryAccent
import com.prc.app.ui.components.initialsOf

/**
 * Apply flow ("Apply for this job"): professional multi-section form —
 * job recap, CV attachment (real file picker), cover note, poster-defined
 * screening questions, contact confirmation, validated sticky submit bar,
 * and a success confirmation state. Submissions persist to Firestore via
 * JobRepository.apply().
 */
@Composable
fun ApplyJobScreen(
    jobId: Int,
    onClose: () -> Unit,
    onSubmitted: () -> Unit,
    onViewApplications: () -> Unit,
    onBackToSearch: () -> Unit,
    onUpgradeToPro: () -> Unit = {}
) {
    SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val job = JobRepository.job(jobId) ?: run { onClose(); return }
    val me = AuthRepository.currentUser.collectAsState().value
    val alreadyApplied = JobRepository.hasApplied(jobId, me?.contact ?: "")

    val context = androidx.compose.ui.platform.LocalContext.current
    var submitted by remember { mutableStateOf(alreadyApplied) }

    // ---- form state ----
    var cvName by remember { mutableStateOf(me?.cvName?.takeIf { it.isNotBlank() } ?: "") }
    var cvUri by remember { mutableStateOf<Uri?>(null) }
    var coverNote by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var uploadStatus by remember { mutableStateOf("") }
    var showErrors by remember { mutableStateOf(false) }
    val questions = remember(job.id) { screeningQuestions(job) }
    var answers by remember(job.id) { mutableStateOf(questions.map { "" }) }

    val maxNote = 500
    val missingQuestions = questions.indices.count { answers[it].isBlank() }
    val canSubmit = cvName.isNotBlank() && missingQuestions == 0

    // ---- free-tier daily cap ----
    var isPro by remember { mutableStateOf<Boolean?>(null) }   // null = loading
    var appsToday by remember { mutableStateOf(0) }
    var showDailyCapDialog by remember { mutableStateOf(false) }
    LaunchedEffect(job.id) {
        val uid = AuthRepository.currentUid()
        isPro = com.prc.app.data.ProGate.isPro(uid)
        appsToday = com.prc.app.data.ProGate.applicationsToday(me?.contact ?: "")
    }
    if (showDailyCapDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDailyCapDialog = false },
            title = { Text("Daily limit reached", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Free accounts can send ${com.prc.app.data.ProGate.FREE_DAILY_APPLICATIONS} applications per day. " +
                            "Upgrade to Pro for unlimited applications — or come back tomorrow.",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDailyCapDialog = false
                    onUpgradeToPro()
                }) {
                    Text("Upgrade to Pro", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDailyCapDialog = false }) {
                    Text("Maybe later")
                }
            }
        )
    }

    // ---- real document picker for CV ----
    val cvPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            cvUri = uri
            // Use the display name when resolvable, otherwise the last path segment.
            val name = queryDisplayName(context, uri)
                    ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"
            cvName = name.substringAfterLast('/').take(60)
        }
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // ---- premium gate: paid application required for premium jobs ----
    var unlocks by remember { mutableStateOf<Int?>(null) }   // null = still loading
    var onCheckout by remember { mutableStateOf(false) }
    LaunchedEffect(job.id) {
        unlocks = if (job.premium)
            com.prc.app.data.AuthRepository.currentUid()?.let { com.prc.app.data.PaymentsRepository.unlocksRemaining(it) } ?: 0
        else 99
    }
    if (onCheckout) {
        com.prc.app.ui.screens.payments.CheckoutScreen(
            product = com.prc.app.data.BillingProduct.AppUnlock(),
            onDone = {
                onCheckout = false
                scope.launch { unlocks = com.prc.app.data.AuthRepository.currentUid()?.let { com.prc.app.data.PaymentsRepository.unlocksRemaining(it) } ?: 0 }
            },
            onBack = { onCheckout = false }
        )
        return
    }
    // Premium job without an unlock -> show the paywall instead of the form.
    if (job.premium && (unlocks ?: 1) <= 0 && !submitted) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 42.sp)
            Spacer(Modifier.height(10.dp))
            Text("Premium job", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Applying to this job requires an application unlock (KSh 200) or a Pro plan.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onCheckout = true },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) { Text("Unlock for KSh 200") }
        }
        return
    }

    fun submit() {
        if (!canSubmit) {
            showErrors = true
            return
        }
        // Free-tier daily cap: Pro users and premium-job unlocks are exempt.
        if (isPro == false && appsToday >= com.prc.app.data.ProGate.FREE_DAILY_APPLICATIONS) {
            showDailyCapDialog = true
            return
        }
        submitting = true
        scope.launch {
            // Upload the CV to Firebase Storage first (graceful: blank URL on
            // failure — the application still saves with the filename).
            var cvUrl = ""
            val uri = cvUri
            if (uri != null) {
                uploadStatus = "Uploading CV…"
                cvUrl = com.prc.app.data.CvStorage.uploadApplicationCv(job.id, uri, cvName) ?: ""
            }
            uploadStatus = ""
            val ok = JobRepository.apply(
                job,
                me?.fullName ?: "Unknown",
                me?.contact ?: "",
                note = coverNote.trim(),
                cvName = cvName,
                cvUrl = cvUrl,
                answers = questions.indices
                    .filter { answers[it].isNotBlank() }
                    .associate { questions[it].first to answers[it].trim() }
            )
            submitting = false
            submitted = true   // also covers "already applied" — same confirmation state
            if (ok) appsToday += 1
            if (!ok) android.util.Log.d("PRC-Apply", "duplicate apply suppressed for job $jobId")
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ==================== header ====================
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onClose() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (submitted) "" else "Apply for this job",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (!submitted) {
                        Text(
                            "${job.type} · ${job.location}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (submitted) {
                // ==================== confirmation state ====================
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(84.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Application sent!",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your application for ${job.title} has been sent to ${job.postedBy}. " +
                                "You'll be notified as soon as there's an update.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(26.dp))
                    // job card with Applied chip
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = categoryAccent(job.category),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        initialsOf(job.postedBy),
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(job.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${job.category} · ${job.postedBy}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    "Applied",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                    SubmitButton("View my applications") {
                        onSubmitted()
                        onViewApplications()
                    }
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Back to search") {
                        onSubmitted()
                        onBackToSearch()
                    }
                }
            } else {
                // ==================== form ====================
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 14.dp)
                ) {
                    // ---- job recap ----
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                            .padding(13.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = categoryAccent(job.category),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    initialsOf(job.postedBy),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(job.title, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Text(
                                job.postedBy + " · " + job.location + if (job.remoteOk) " · Remote" else "",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = categoryAccent(job.category).copy(alpha = 0.12f)
                        ) {
                            Text(
                                job.category,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = categoryAccent(job.category),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    // ---- CV selection (required) ----
                    SectionLabelRow(
                        "CV / Resume",
                        right = if (cvName.isBlank() && showErrors) "Required" else null
                    )
                    CvOption(
                        name = cvName.ifBlank { "No document selected" },
                        sub = if (cvName.isBlank()) "Choose a PDF or document from your device"
                              else "Attached to this application",
                        selected = cvName.isNotBlank(),
                        filled = cvName.isNotBlank(),
                        onClick = { cvPicker.launch("*/*") }
                    )
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp)
                            .border(
                                1.5.dp,
                                if (cvName.isBlank() && showErrors) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.medium
                            )
                            .clickable { cvPicker.launch("*/*") }
                            .padding(13.dp)
                    ) {
                        Icon(
                            Icons.Filled.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (cvName.isBlank()) "Select a CV from your device"
                            else "Choose a different document",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // ---- cover note ----
                    SectionLabelRow("Cover note", right = "Optional")
                    OutlinedTextField(
                        value = coverNote,
                        onValueChange = { if (it.length <= maxNote) coverNote = it },
                        placeholder = {
                            Text(
                                "Tell the employer briefly why you're a good fit for this role…",
                                fontSize = 12.5.sp,
                                lineHeight = 19.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        ),
                        minLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    )
                    Text(
                        "${coverNote.length} / $maxNote",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    )

                    // ---- screening questions ----
                    if (questions.isNotEmpty()) {
                        SectionLabelRow(
                            "Screening questions",
                            right = if (missingQuestions > 0 && showErrors) "$missingQuestions to answer" else null
                        )
                        questions.forEachIndexed { i, q ->
                            val unanswered = answers[i].isBlank() && showErrors
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 5.dp)
                                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                                    .border(
                                        if (unanswered) 1.dp else 0.dp,
                                        if (unanswered) MaterialTheme.colorScheme.error else Color.Transparent,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(14.dp)
                            ) {
                                Text(
                                    q.first,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(9.dp))
                                if (q.second.isNotEmpty()) {
                                    // multiple choice
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        q.second.forEach { option ->
                                            val selected = answers[i] == option
                                            Surface(
                                                shape = CircleShape,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.background,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (selected) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outlineVariant
                                                ),
                                                modifier = Modifier.clickable {
                                                    answers = answers.toMutableList().also { it[i] = option }
                                                }
                                            ) {
                                                Text(
                                                    option,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // short answer
                                    OutlinedTextField(
                                        value = answers[i],
                                        onValueChange = { newValue ->
                                            answers = answers.toMutableList().also { it[i] = newValue }
                                        },
                                        placeholder = { Text("Type your answer", fontSize = 11.5.sp) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            unfocusedContainerColor = MaterialTheme.colorScheme.background,
                                            focusedContainerColor = MaterialTheme.colorScheme.background
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // ---- contact confirmation ----
                    SectionLabelRow("Contact info shared")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                            .padding(horizontal = 15.dp, vertical = 6.dp)
                    ) {
                        ContactRow("Phone", me?.phone?.takeIf { it.isNotBlank() } ?: "—")
                        val email = me?.contact?.takeIf { it.contains("@") }
                        ContactRow("Email", email ?: "Not set")
                    }

                    // ---- validation summary ----
                    if (showErrors && !canSubmit) {
                        Text(
                            buildString {
                                append("Please complete: ")
                                val missing = mutableListOf<String>()
                                if (cvName.isBlank()) missing.add("CV")
                                if (missingQuestions > 0) missing.add("$missingQuestions screening answer(s)")
                                append(missing.joinToString(", "))
                            },
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }

                // ---- sticky submit bar ----
                Column(
                    Modifier
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        "${job.postedBy} will see your profile, CV, and answers above",
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                    if (uploadStatus.isNotBlank()) {
                        Text(
                            uploadStatus,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        )
                    }
                    SubmitButton(
                        text = when {
                            submitting && uploadStatus.isNotBlank() -> uploadStatus.removeSuffix("…")
                            submitting -> "Submitting…"
                            alreadyApplied -> "Already applied ✓"
                            else -> "Submit application"
                        },
                        enabled = !submitting && !alreadyApplied
                    ) { submit() }
                }
            }
        }
    }
}

/** Resolve a display name from a content Uri, best-effort. */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
    }
} catch (_: Exception) {
    null
}

// ==================== pieces ====================

@Composable
private fun SectionLabelRow(title: String, right: String? = null) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (right != null) {
            Text(
                right,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun CvOption(
    name: String,
    sub: String,
    selected: Boolean,
    filled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.5.dp,
            if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(13.dp)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (filled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (filled) Icons.Filled.Description else Icons.Filled.Work,
                        contentDescription = null,
                        tint = if (filled) MaterialTheme.colorScheme.onPrimaryContainer
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (filled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    sub,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // radio dot
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(18.dp)
                    .border(
                        2.dp,
                        if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    )
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(k: String, v: String) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(k, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            v,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SubmitButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GhostButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * Poster-defined screening questions. Derived from the posting's
 * requirements so each job has stable, relevant questions.
 */
private fun screeningQuestions(job: Job): List<Pair<String, List<String>>> {
    val q1 = "Do you have your own tools/equipment for this work?"
    val q2 = "When can you start?"
    val base = listOf(
        q1 to listOf("Yes", "No", "Some"),
        q2 to emptyList()
    )
    return if (job.requirements.isNotEmpty()) base else emptyList()
}
