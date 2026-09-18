package com.prc.app.ui.screens.post

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Application
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository

/**
 * Employer-side applicants pipeline for one job: pipeline strip, search,
 * stage tabs, sorting, and rich applicant cards with quick decisions.
 */
@Composable
fun ApplicantsScreen(
    jobId: Int,
    onBack: () -> Unit,
    onOpenApplicant: (String) -> Unit = {}
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val job = JobRepository.job(jobId)
    val allApplications by JobRepository.applications.collectAsState()
    val list = allApplications.filter { it.job.id == jobId }

    var query by remember { mutableStateOf("") }
    var stageTab by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("match") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    // Pro priority placement: keep the live Pro-contact snapshot warm.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.prc.app.data.ProDirectory.start()
    }


    val pending = list.count { it.status == "Pending" }
    val shortlisted = list.count { it.status == "Shortlisted" }
    val accepted = list.count { it.status == "Accepted" }
    val rejected = list.count { it.status == "Rejected" }

    val stageTabs = listOf(
        "All" to list.size,
        "Pending" to pending,
        "Shortlisted" to shortlisted,
        "Accepted" to accepted,
        "Rejected" to rejected
    )

    val jobSkills = job?.let { it.skills.ifEmpty { skillsForCategory(it.category) } } ?: emptyList()

    val filtered = list
        .filter { stageTab == "All" || it.status == stageTab }
        .filter {
            val q = query.trim().lowercase()
            q.isEmpty() || it.applicantName.lowercase().contains(q) ||
                    it.applicantContact.lowercase().contains(q) ||
                    AuthRepository.publicProfileOf(it.applicantContact)?.skills
                        ?.any { s -> s.lowercase().contains(q) } == true
        }
        .let { l ->
            when (sortBy) {
                "name" -> l.sortedBy { it.applicantName.lowercase() }
                "newest" -> l.sortedByDescending { it.appliedAt }
                else -> l.sortedByDescending { matchScore(it, jobSkills) }
            }
        }
        // Pro priority placement: within each sort, applicants with an active
        // Pro subscription surface above equally-ranked free applicants.
        .sortedByDescending { com.prc.app.data.ProDirectory.isProContact(it.applicantContact) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
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
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        job?.title ?: "Applicants",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1
                    )
                    Text(
                        if (list.size == 1) "1 applicant" else "${list.size} applicants",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(36.dp)
                            .clickable { sortMenuOpen = true }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        listOf(
                            "match" to "Best match",
                            "newest" to "Newest first",
                            "name" to "Name A–Z"
                        ).forEach { (key, label) ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        label,
                                        fontWeight = if (sortBy == key) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.5.sp
                                    )
                                },
                                onClick = {
                                    sortBy = key
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // ==================== pipeline strip ====================
            if (list.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    PipelineCell(pending.toString(), "New", accent = true)
                    PipelineCell(shortlisted.toString(), "Shortlisted")
                    PipelineCell(accepted.toString(), "Accepted")
                    PipelineCell(rejected.toString(), "Rejected")
                }
            }

            // ==================== search ====================
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search applicants", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )

            // ==================== stage tabs ====================
            if (list.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    stageTabs.forEach { (label, count) ->
                        StageTab(
                            label = label,
                            count = count,
                            active = stageTab == label,
                            onClick = { stageTab = label }
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${filtered.size} shown",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        when (sortBy) {
                            "newest" -> "Newest first"
                            "name" -> "Name A–Z"
                            else -> "Best match"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // ==================== list ====================
            if (list.isEmpty()) {
                EmptyApplicants(Modifier.padding(padding))
            } else if (filtered.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔍", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No applicants match",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filtered, key = { it.applicantContact + it.job.id }) { app ->
                        ApplicantCard(
                            app = app,
                            job = job,
                            jobSkills = jobSkills,
                            onOpen = { onOpenApplicant(app.applicantContact) },
                            onDecide = { acceptedDecision ->
                                JobRepository.decide(jobId, app.applicantContact, acceptedDecision)
                            },
                            onShortlist = {
                                JobRepository.shortlist(jobId, app.applicantContact)
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==================== pieces ====================

@Composable
private fun PipelineCell(value: String, label: String, accent: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(
                value,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                label,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StageTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(5.dp))
            Surface(
                shape = CircleShape,
                color = if (active)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
            ) {
                Text(
                    count.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun ApplicantCard(
    app: Application,
    job: Job?,
    jobSkills: List<String>,
    onDecide: (Boolean) -> Unit,
    onShortlist: () -> Unit,
    onOpen: () -> Unit
) {
    val profile = AuthRepository.publicProfileOf(app.applicantContact)
    val score = matchScore(app, jobSkills)
    val headline = buildHeadline(profile, app)
    val decided = app.status == "Accepted" || app.status == "Rejected"

    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // ---- top: avatar + identity + stage badge ----
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            initials(app.applicantName),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.applicantName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        headline,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 1.dp, bottom = 5.dp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (score >= 0) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    "$score% match",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            profile?.location?.takeIf { it.isNotBlank() } ?: "",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                StageBadge(app.status)
            }

            // ---- skill tags ----
            val topSkills = (profile?.skills ?: emptyList()).take(3)
            if (topSkills.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 9.dp)
                ) {
                    topSkills.forEach { s ->
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                s,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // ---- footer: applied-ago + actions ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Applied ${timeAgo(app.appliedAt)}",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    app.status == "Pending" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            IconAction(
                                Icons.Filled.PushPin,
                                desc = "Shortlist",
                                onClick = onShortlist
                            )
                            IconAction(
                                Icons.Filled.Close,
                                desc = "Reject",
                                danger = true,
                                onClick = { onDecide(false) }
                            )
                            Button(
                                onClick = { onDecide(true) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 6.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    app.status == "Shortlisted" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            IconAction(
                                Icons.Filled.Close,
                                desc = "Reject",
                                danger = true,
                                onClick = { onDecide(false) }
                            )
                            Button(
                                onClick = { onDecide(true) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 14.dp, vertical = 6.dp
                                ),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text("Accept", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    decided -> {
                        Text(
                            if (app.status == "Accepted") "✓ Accepted" else "✗ Rejected",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (app.status == "Accepted")
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageBadge(status: String) {
    val (bg, fg, label) = when (status) {
        "Accepted" -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Hired"
        )
        "Shortlisted" -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Shortlisted"
        )
        "Rejected" -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Rejected"
        )
        else -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "New"
        )
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (danger)
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .size(30.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = desc,
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun EmptyApplicants(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🪑", fontSize = 32.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("No applicants yet", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "When people apply from the job page, they'll appear here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
    }
}

// ==================== helpers ====================

/** Simple skill-overlap score; -1 when neither side has data to compare. */
private fun matchScore(app: Application, jobSkills: List<String>): Int {
    val profile = AuthRepository.publicProfileOf(app.applicantContact) ?: return -1
    if (jobSkills.isEmpty() || profile.skills.isEmpty()) return -1
    val overlap = profile.skills.count { userSkill ->
        jobSkills.any { it.equals(userSkill, ignoreCase = true) }
    }
    return ((overlap.toDouble() / jobSkills.size) * 100).toInt().coerceIn(0, 100)
}

private fun buildHeadline(
    profile: com.prc.app.data.User?,
    app: Application
): String = when {
    profile == null -> app.applicantContact
    profile.skills.isNotEmpty() && profile.location.isNotBlank() ->
        profile.skills.take(2).joinToString(" · ") + " · " + profile.location
    profile.skills.isNotEmpty() -> profile.skills.take(3).joinToString(" · ")
    else -> app.applicantContact
}

private fun initials(name: String): String =
    name.split(" ", limit = 2).filter { it.isNotBlank() }
        .map { it.first().uppercase() }
        .joinToString("")
        .take(2)
        .ifEmpty { "\uD83D\uDCBC" }

private fun timeAgo(at: Long): String {
    val mins = (System.currentTimeMillis() - at) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

/** Category-derived fallback skill set (same mapping as job details). */
private fun skillsForCategory(category: String): List<String> = when (category) {
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
