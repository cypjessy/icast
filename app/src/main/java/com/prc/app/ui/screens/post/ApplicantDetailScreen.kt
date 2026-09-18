package com.prc.app.ui.screens.post

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Application
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.data.User
import com.prc.app.ui.components.initialsOf
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.tasks.await

/**
 * Applicant detail (template's "View Application"): candidate summary card,
 * quick actions, status stepper, cover note, CV card, skills match,
 * experience/education summary, private notes, sticky decision bar.
 */
@Composable
fun ApplicantDetailScreen(
    jobId: Int,
    applicantContact: String,
    onBack: () -> Unit
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val context = androidx.compose.ui.platform.LocalContext.current
    val allApplications by JobRepository.applications.collectAsState()
    val app = allApplications.find {
        it.job.id == jobId && it.applicantContact == applicantContact
    } ?: run { onBack(); return }
    val job: Job = app.job
    val profile: User? = AuthRepository.publicProfileOf(applicantContact)

    // Pro badge state: the applicant's subscription, live from Firestore.
    var applicantProActive by remember { mutableStateOf(false) }
    LaunchedEffect(applicantContact) {
        applicantProActive = try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("contact", applicantContact)
                .limit(1)
                .get().await()
            val doc = snap.documents.firstOrNull()
            val until = doc?.getTimestamp("proUntil")?.toDate()?.time ?: 0L
            until > System.currentTimeMillis()
        } catch (_: Exception) { false }
    }

    var decisionNote by remember(app.privateNote) { mutableStateOf(app.privateNote) }

    val stage = when (app.status) {
        "Shortlisted" -> 2
        "Accepted" -> 3
        "Rejected" -> -1
        else -> 0
    }
    val advanceLabel = when (app.status) {
        "Pending" -> "Shortlist"
        "Shortlisted" -> "Accept"
        else -> "Accepted ✓"
    }

    val jobSkills = job.skills.ifEmpty { fallbackSkills(job.category) }
    val isMatched: (String) -> Boolean = { s ->
        profile?.skills?.any { it.equals(s, ignoreCase = true) } == true
    }
    val matched = jobSkills.filter(isMatched)
    val missing = jobSkills.filterNot(isMatched)
    val matchPct = matchScoreFor(app, jobSkills)
    val headline = headlineFor(profile, app)

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
                        .clickable { onBack() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "Application for",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        job.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 14.dp)
            ) {
                // ==================== candidate card ====================
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(18.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(62.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                initialsOf(app.applicantName),
                                fontSize = 21.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.applicantName,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (applicantProActive) {
                            Spacer(Modifier.width(6.dp))
                            com.prc.app.ui.components.ProBadge(size = 18.dp)
                        }
                    }
                    Text(
                        headline,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 7.dp)
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (app.applicantContact.any { it.isDigit() } &&
                                app.applicantContact.none { it == '@' }
                            ) "Phone verified" else "Email verified",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                    ) {
                        MatchCell(
                            if (matchPct >= 0) "$matchPct%" else "—",
                            "Match score",
                            gold = true
                        )
                        MatchCell(agoShort(app.appliedAt), "Applied ago")
                        MatchCell(
                            profile?.location?.takeIf { it.isNotBlank() } ?: "—",
                            "Location"
                        )
                    }
                }

                // ==================== quick actions ====================
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    QuickAction(
                        icon = Icons.Filled.Call,
                        label = "Call",
                        primary = false,
                        onClick = {
                            val phone = app.applicantContact.takeIf { c ->
                                c.any { ch -> ch.isDigit() } && c.none { ch -> ch == '@' }
                            } ?: return@QuickAction
                            try {
                                context.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:$phone")
                                    )
                                )
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    QuickAction(
                        icon = Icons.AutoMirrored.Filled.Chat,
                        label = "Message",
                        primary = true,
                        onClick = {
                            try {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                        data = android.net.Uri.parse(
                                            "smsto:${app.applicantContact.takeIf { c -> !c.contains("@") } ?: ""}"
                                        )
                                        putExtra("sms_body", "Hi ${app.applicantName}, regarding your application for \"${job.title}\"...")
                                    }
                                )
                            } catch (_: Exception) { }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                // ==================== status stepper ====================
                SectionTitle("Application status")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (stage < 0) {
                            StepNode("!", StepState.REJECT)
                            StepConnector(false)
                            repeat(3) {
                                StepNode("${it + 1}", StepState.TODO)
                                StepConnector(false)
                            }
                            StepNode("4", StepState.TODO)
                        } else {
                            repeat(4) { i ->
                                val st = when {
                                    i < stage -> StepState.DONE
                                    i == stage -> StepState.CURRENT
                                    else -> StepState.TODO
                                }
                                StepNode("${i + 1}", st)
                                if (i < 3) StepConnector(st == StepState.DONE)
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        StepLabel("Applied", 0, stage)
                        StepLabel("Review", 1, stage)
                        StepLabel("Shortlist", 2, stage)
                        StepLabel("Hired", 3, stage)
                    }
                    if (stage < 0) {
                        Text(
                            "Rejected — this candidate is no longer in the pipeline.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                // ==================== cover note ====================
                SectionTitle("Cover note")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(15.dp)
                ) {
                    Text(
                        app.coverNote?.takeIf { it.isNotBlank() }
                            ?: profile?.bio?.takeIf { it.isNotBlank() }
                            ?: "No cover note was attached to this application.",
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // ==================== screening answers ====================
                if (app.answers.isNotEmpty()) {
                    SectionTitle("Screening answers")
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        app.answers.forEach { (question, answer) ->
                            Column(Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    question,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    answer,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (question != app.answers.keys.last()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                // ==================== CV ====================
                SectionTitle("CV / Resume")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .clickable(enabled = app.cvUrl.isNotBlank()) {
                            com.prc.app.data.CvStorage.openCv(app.cvUrl, context)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        // CV attached to THIS application first, then profile CV.
                        val cvName = app.cvName.takeIf { it.isNotBlank() }
                            ?: profile?.cvName?.takeIf { it.isNotBlank() }
                        Text(
                            cvName ?: "No CV attached",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                app.cvUrl.isNotBlank() -> "Tap to open the uploaded file"
                                app.cvName.isNotBlank() -> "Attached with this application"
                                profile?.cvName?.isNotBlank() == true -> "From the applicant's profile"
                                else -> "The applicant hasn't attached one"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (app.cvName.isNotBlank() && profile?.cvName?.takeIf { it.isNotBlank() } != null &&
                        !app.cvName.equals(profile?.cvName, ignoreCase = true)
                    ) {
                        Text(
                            "Profile: ${profile?.cvName}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ==================== skills match ====================
                SectionTitle("Skills match")
                Column(
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    matched.forEach { SkillMatchRow(it, yes = true) }
                    missing.forEach { SkillMatchRow(it, yes = false) }
                    if (matched.isEmpty() && missing.isEmpty()) {
                        Text(
                            "No skill requirements on this job.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // ==================== experience & education ====================
                SectionTitle("Experience & education")
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val hasEntries = profile?.experience?.isNotEmpty() == true ||
                            profile?.education?.isNotEmpty() == true
                    if (!hasEntries) {
                        Text(
                            "The candidate hasn't filled in experience or education yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    profile?.experience?.forEach { e ->
                        TimelineRow(Icons.Filled.Work, e.role, e.org, e.dates)
                    }
                    profile?.education?.forEach { e ->
                        TimelineRow(Icons.Filled.School, e.qualification, e.institution, e.dates)
                    }
                }

                // ==================== private notes ====================
                SectionTitle("Private notes")
                OutlinedTextField(
                    value = decisionNote,
                    onValueChange = {
                        decisionNote = it
                        JobRepository.saveNote(jobId, applicantContact, it)
                    },
                    placeholder = {
                        Text(
                            "Tap to add a private note about this candidate — only visible to you.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
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
                    minLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(16.dp))
            }
        }

        // ==================== sticky decision bar ====================
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            DecisionButton(
                enabled = app.status != "Rejected",
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                container = MaterialTheme.colorScheme.surface,
                modifier = Modifier.weight(1f),
                onClick = {
                    if (app.status != "Rejected")
                        JobRepository.decide(jobId, applicantContact, false)
                }
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Reject",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            DecisionButton(
                enabled = app.status != "Accepted",
                border = null,
                container = if (app.status == "Accepted")
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    when (app.status) {
                        "Pending" -> JobRepository.shortlist(jobId, applicantContact)
                        "Shortlisted" -> JobRepository.decide(jobId, applicantContact, true)
                    }
                }
            ) {
                Text(
                    advanceLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                if (app.status != "Accepted") {
                    Spacer(Modifier.width(6.dp))
                    Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

// ==================== pieces ====================

private enum class StepState { DONE, CURRENT, TODO, REJECT }

@Composable
private fun DecisionButton(
    enabled: Boolean,
    border: BorderStroke?,
    container: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    label: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = border,
        modifier = modifier
            .height(46.dp)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
            content = label
        )
    }
}

@Composable
private fun StepNode(text: String, state: StepState) {
    val (bg, fg) = when (state) {
        StepState.DONE ->
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        StepState.CURRENT ->
            MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
        StepState.REJECT ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        StepState.TODO ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = CircleShape, color = bg, modifier = Modifier.size(26.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
        }
    }
}

@Composable
private fun RowScope.StepConnector(done: Boolean) {
    Box(
        Modifier
            .weight(1f)
            .height(2.dp)
            .background(
                if (done) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
    )
}

@Composable
private fun StepLabel(label: String, index: Int, stage: Int) {
    Text(
        label,
        fontSize = 9.sp,
        fontWeight = if (index == stage) FontWeight.Bold else FontWeight.SemiBold,
        color = when {
            index == stage && stage >= 0 -> MaterialTheme.colorScheme.tertiary
            stage >= 0 && index < stage -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        textAlign = TextAlign.Center,
        modifier = Modifier.width(46.dp)
    )
}

@Composable
private fun MatchCell(value: String, label: String, gold: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (gold) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (primary) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surface,
        border = if (primary) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(44.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (primary) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp)
    )
}

@Composable
private fun SkillMatchRow(skill: String, yes: Boolean) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(9.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(9.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Text(
            skill,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (yes) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (yes) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    tint = if (yes) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (yes) "Matched" else "Missing",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (yes) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun TimelineRow(icon: ImageVector, title: String, org: String, dates: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            Text(
                org,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                dates,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== helpers ====================

private fun headlineFor(profile: User?, app: Application): String = when {
    profile == null -> app.applicantContact
    profile.skills.isNotEmpty() && profile.location.isNotBlank() ->
        profile.skills.take(2).joinToString(" · ") + " · " + profile.location
    profile.skills.isNotEmpty() -> profile.skills.take(3).joinToString(" · ")
    else -> app.applicantContact
}

private fun agoShort(at: Long): String {
    val mins = (System.currentTimeMillis() - at) / 60000
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 1440 -> "${mins / 60}h"
        else -> "${mins / 1440}d"
    }
}

/** Skill-overlap score; -1 when neither side has data to compare. */
private fun matchScoreFor(app: Application, jobSkills: List<String>): Int {
    val profile = AuthRepository.publicProfileOf(app.applicantContact) ?: return -1
    if (jobSkills.isEmpty() || profile.skills.isEmpty()) return -1
    val overlap = profile.skills.count { userSkill ->
        jobSkills.any { it.equals(userSkill, ignoreCase = true) }
    }
    return ((overlap.toDouble() / jobSkills.size) * 100).toInt().coerceIn(0, 100)
}

/** Category-derived fallback skill set (same mapping as the list screen). */
private fun fallbackSkills(category: String): List<String> = when (category) {
    "IT & Software" -> listOf("Programming", "Debugging", "Databases", "Problem solving")
    "Medicine & Health" -> listOf("Patient care", "Clinical skills", "Record keeping", "Empathy")
    "Human Resources" -> listOf("Recruitment", "Onboarding", "Communication", "Conflict resolution")
    "Finance & Accounting" -> listOf("Bookkeeping", "Reconciliation", "Excel", "Attention to detail")
    "Sales & Marketing" -> listOf("Prospecting", "Negotiation", "CRM tools", "Presentation")
    "Education" -> listOf("Lesson planning", "Classroom management", "Mentoring", "Assessment")
    "Engineering" -> listOf("CAD", "Project management", "Site inspection", "Technical drawing")
    "Law & Governance" -> listOf("Legal research", "Drafting", "Compliance", "Advocacy")
    "Agriculture" -> listOf("Crop management", "Animal care", "Irrigation", "Tool use")
    "Hospitality & Tourism" -> listOf("Food service", "Guest relations", "Hygiene", "Shift work")
    "Logistics & Supply Chain" -> listOf("Fleet coordination", "Inventory", "Route planning", "Documentation")
    "Media & Creative" -> listOf("Content writing", "Design tools", "Photography", "Social media")
    "Retail" -> listOf("Cash handling", "Restocking", "Customer service", "Arithmetic")
    "Domestic" -> listOf("Cleaning", "Laundry", "Cooking", "Childcare")
    "Driving" -> listOf("Defensive driving", "Navigation", "Vehicle care", "Timekeeping")
    "Construction" -> listOf("Masonry", "Bricklaying", "Plastering", "Site safety")
    "Skilled trade" -> listOf("Electrical", "Plumbing", "Diagnostics", "Tool maintenance")
    else -> listOf("Reliability", "Communication", "Teamwork")
}
