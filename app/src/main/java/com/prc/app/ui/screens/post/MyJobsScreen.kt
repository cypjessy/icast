package com.prc.app.ui.screens.post

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.tasks.await
import com.prc.app.data.AuthRepository
import com.prc.app.data.BillingProduct
import com.prc.app.data.Job
import com.prc.app.data.JobRepository

private enum class JobStatus(val label: String) {
    ACTIVE("Active"), DRAFT("Draft"), CLOSED("Closed");

    companion object {
        fun of(job: Job) = when {
            job.draft -> DRAFT
            job.closed -> CLOSED
            else -> ACTIVE
        }
    }
}

/**
 * Employer-side job manager: summary stats, status tabs, sort, rich posting cards
 * with applicant/view/time stats and quick actions (publish draft, duplicate,
 * close/reopen, view applicants).
 */
@Composable
fun MyJobsScreen(
    onJobClick: (Int) -> Unit,
    onPostClick: () -> Unit,
    onEditJob: (Int) -> Unit = {},
    onBoostCheckout: (jobId: Int, plan: BillingProduct) -> Unit = { _, _ -> }
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val jobs by JobRepository.jobs.collectAsState()
    val applications by JobRepository.applications.collectAsState()
    val me by AuthRepository.currentUser.collectAsState()

    var statusTab by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("recent") }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Int?>(null) }
    var boostJob by remember { mutableStateOf<Job?>(null) }
    var historyFor by remember { mutableStateOf<Job?>(null) }

    val myJobs = jobs.filter { it.posterContact == (me?.contact ?: "") }
    val applicantCountOf: (Job) -> Int = { job ->
        applications.count { it.job.id == job.id && it.applicantContact != me?.contact }
    }

    val activeCount = myJobs.count { JobStatus.of(it) == JobStatus.ACTIVE }
    val draftCount = myJobs.count { JobStatus.of(it) == JobStatus.DRAFT }
    val closedCount = myJobs.count { JobStatus.of(it) == JobStatus.CLOSED }
    val expiringCount = myJobs.count {
        JobStatus.of(it) == JobStatus.ACTIVE && (it.deadlineDays ?: 99) <= 3
    }
    val totalApplicants = myJobs.sumOf(applicantCountOf)

    // ---- boost spend + active boosts summary (Firestore payments) ----
    var boostSpend by remember { mutableStateOf<Int?>(null) }   // null = loading
    var boostCount by remember { mutableStateOf(0) }
    val activeBoosts = myJobs.count { it.isFeatured }
    LaunchedEffect(Unit) {
        try {
            val uid = com.prc.app.data.AuthRepository.currentUid()
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("payments")
                .whereEqualTo("uid", uid)
                .whereEqualTo("kind", "boost")
                .get()
                .await()
            val paid = snap.documents.filter { it.getString("status") == "success" }
            boostSpend = paid.sumOf { ((it.getLong("amount") ?: 0L) / 100).toInt() }
            boostCount = paid.size
        } catch (_: Exception) {
            boostSpend = 0
        }
    }

    val tabs = listOf(
        "All" to myJobs.size,
        "Active" to activeCount,
        "Expiring" to expiringCount,
        "Draft" to draftCount,
        "Closed" to closedCount
    )

    val visible = myJobs
        .filter {
            when (statusTab) {
                "All" -> true
                "Expiring" -> JobStatus.of(it) == JobStatus.ACTIVE && (it.deadlineDays ?: 99) <= 3
                else -> JobStatus.of(it).label == statusTab
            }
        }
        .let { list ->
            when (sortBy) {
                "applicants" -> list.sortedByDescending { applicantCountOf(it) }
                "closing" -> list.sortedBy { it.deadlineDays ?: 999 }
                else -> list
            }
        }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (myJobs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onPostClick,
                    shape = RoundedCornerShape(22.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("Post a job", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        if (myJobs.isEmpty()) {
            EmptyState(onPostClick, Modifier.padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 90.dp)
        ) {
            // ==================== boost spend summary ====================
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                if (boostSpend == null) "…" else "KSh $boostSpend",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Total boost spend",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFF9A825),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    activeBoosts.toString(),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                "Active boosts",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                boostCount.toString(),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Boosts bought",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==================== header ====================
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "My Jobs",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            pluralPostings(myJobs.size) + " · " + (me?.fullName?.split(" ")?.first() ?: "you"),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { sortMenuOpen = true }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.FilterList,
                                    contentDescription = "Sort",
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            listOf("recent" to "Most recent", "applicants" to "Most applicants", "closing" to "Closing soonest")
                                .forEach { (key, label) ->
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
            }

            // ==================== summary stats strip ====================
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(Modifier.padding(vertical = 13.dp)) {
                        StatCell(activeCount.toString(), "Active", accent = true, modifier = Modifier.weight(1f))
                        StatCell(draftCount.toString(), "Draft", modifier = Modifier.weight(1f))
                        StatCell(closedCount.toString(), "Closed", modifier = Modifier.weight(1f))
                        StatCell(totalApplicants.toString(), "Applicants", modifier = Modifier.weight(1.2f))
                    }
                }
            }

            // ==================== status tabs ====================
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    tabs.forEach { (label, count) ->
                        StatusTab(
                            label = label,
                            count = count,
                            active = statusTab == label,
                            onClick = { statusTab = label }
                        )
                    }
                }
                Text(
                    when (sortBy) {
                        "applicants" -> "Most applicants"
                        "closing" -> "Closing soonest"
                        else -> "Most recent"
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, bottom = 4.dp)
                )
            }

            // ==================== posting cards ====================
            items(visible, key = { it.id }) { job ->
                PostingCard(
                    job = job,
                    applicantCount = applicantCountOf(job),
                    viewCount = 0,  // real view tracking not implemented yet
                    menuOpen = menuFor == job.id,
                    onMenu = { open -> menuFor = if (open) job.id else null },
                    onOpen = { onJobClick(job.id) },
                    onApplicants = { onJobClick(job.id) },
                    onEdit = { menuFor = null; onEditJob(job.id) },
                    onPrimaryAction = {
                        menuFor = null
                        if (job.draft) JobRepository.publishDraft(job.id)
                        else JobRepository.duplicateJob(job.id)
                    },
                    onClose = {
                        menuFor = null
                        JobRepository.closeJob(job.id)
                    },
                    onReopen = {
                        menuFor = null
                        JobRepository.reopenJob(job.id)
                    },
                    onDuplicate = {
                        menuFor = null
                        JobRepository.duplicateJob(job.id)
                    },
                    onBoost = { boostJob = job },
                    onBoostHistory = {
                        menuFor = null
                        historyFor = job
                    }
                )
            }
        }
    }

    // Boost plan picker -> Paystack checkout
    boostJob?.let { job ->
        BoostSheet(
            job = job,
            onDismiss = { boostJob = null },
            onPlan = { plan ->
                boostJob = null
                onBoostCheckout(job.id, plan)
            }
        )
    }

    // Per-job boost history
    historyFor?.let { job ->
        BoostHistorySheet(job = job, onDismiss = { historyFor = null })
    }
}

// ==================== pieces ====================

@Composable
private fun EmptyState(onPostClick: () -> Unit, modifier: Modifier = Modifier) {
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
            modifier = Modifier.size(84.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("📦", fontSize = 34.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("You haven't posted any jobs", fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Need a hand with something? Post it and let people come to you.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPostClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Post your first job", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatCell(value: String, label: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
private fun StatusTab(label: String, count: Int, active: Boolean, onClick: () -> Unit) {
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
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
        ) {
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (active)
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
            ) {
                Text(
                    count.toString(),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun PostingCard(
    job: Job,
    applicantCount: Int,
    viewCount: Int,
    menuOpen: Boolean,
    onMenu: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onApplicants: () -> Unit,
    onEdit: () -> Unit,
    onPrimaryAction: () -> Unit,
    onClose: () -> Unit,
    onReopen: () -> Unit,
    onDuplicate: () -> Unit,
    onBoost: () -> Unit,
    onBoostHistory: () -> Unit
) {
    val status = JobStatus.of(job)
    val expiring = status == JobStatus.ACTIVE && (job.deadlineDays ?: 99) <= 3

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onOpen() }
    ) {
        Column(Modifier.padding(14.dp)) {
            // ---- title + status badge ----
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        job.title,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        job.type + " · " + job.location + if (job.remoteOk) " · Remote OK" else "",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (job.isFeatured) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFF9A825),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Featured · ${boostTimeLeft(job.featuredUntil)}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFB28704)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(status, expiring, job.deadlineDays)
            }

            // ---- stats row ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 11.dp, bottom = 11.dp)
            ) {
                if (status == JobStatus.DRAFT) {
                    MiniStat(Icons.Filled.Edit, null, "—", "Not published")
                } else {
                    MiniStat(Icons.Filled.Group, null, applicantCount.toString(), "Applicants")
                    MiniStat(Icons.Filled.Visibility, null, viewCount.toString(), "Views")
                    when {
                        status == JobStatus.CLOSED -> MiniStat(Icons.Filled.Schedule, null, "—", "Closed")
                        job.deadlineDays != null -> MiniStat(
                            Icons.Filled.Schedule, null,
                            "${job.deadlineDays}d", "Left"
                        )
                        else -> MiniStat(Icons.Filled.Schedule, null, "∞", "Deadline")
                    }
                }
            }

            // ---- footer: meta + actions ----
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (status == JobStatus.DRAFT) "Last edited ${job.postedAgo.lowercase()}"
                    else "Posted ${job.postedAgo.lowercase()}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    // boost (active jobs only)
                    if (status == JobStatus.ACTIVE) {
                        ActionChip(
                            icon = Icons.Filled.Star,
                            primary = false,
                            desc = if (job.isFeatured) "Extend featured boost" else "Boost this job",
                            onClick = onBoost
                        )
                    }
                    // edit
                    ActionChip(
                        icon = Icons.Filled.Edit,
                        primary = false,
                        desc = "Edit job",
                        onClick = onEdit
                    )
                    // publish draft / duplicate
                    ActionChip(
                        icon = if (job.draft) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        primary = job.draft,
                        desc = if (job.draft) "Publish draft" else "Duplicate job",
                        onClick = onPrimaryAction
                    )
                    // more menu: close / reopen / duplicate
                    Box {
                        ActionChip(
                            icon = Icons.Filled.MoreVert,
                            primary = false,
                            desc = "More actions",
                            onClick = { onMenu(!menuOpen) }
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { onMenu(false) }
                        ) {
                            if (status == JobStatus.CLOSED) {
                                DropdownMenuItem(
                                    text = { Text("Reopen applications", fontSize = 13.5.sp) },
                                    onClick = onReopen
                                )
                            } else if (status == JobStatus.ACTIVE) {
                                DropdownMenuItem(
                                    text = { Text("Close applications", fontSize = 13.5.sp) },
                                    onClick = onClose
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Duplicate job", fontSize = 13.5.sp) },
                                onClick = onDuplicate
                            )
                            DropdownMenuItem(
                                text = { Text("Boost history", fontSize = 13.5.sp) },
                                onClick = onBoostHistory
                            )
                        }
                    }
                    // primary: view applicants
                    ActionChip(
                        icon = Icons.Filled.Group,
                        primary = !job.draft,
                        desc = "View applicants",
                        onClick = onApplicants
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: JobStatus, expiring: Boolean, deadlineDays: Int?) {
    val (bg, fg, label) = when {
        expiring -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            if (deadlineDays == 0) "Closes today" else "Closes in ${deadlineDays}d"
        )
        status == JobStatus.ACTIVE -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "Active"
        )
        status == JobStatus.DRAFT -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Draft"
        )
        else -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Closed"
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
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    unused: Any?,
    value: String,
    label: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (label == "Applicants") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(label, fontSize = 9.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    desc: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(32.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = desc,
                tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

private fun pluralPostings(n: Int): String = if (n == 1) "1 posting" else "$n postings"

/** Bottom sheet showing every boost purchase made for one job (from Firestore payments). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostHistorySheet(job: Job, onDismiss: () -> Unit) {
    var entries by remember { mutableStateOf<List<Triple<Long, String, String>>?>(null) } // (time, plan, status)

    LaunchedEffect(job.id) {
        entries = try {
            val uid = com.prc.app.data.AuthRepository.currentUid()
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("payments")
                .whereEqualTo("jobId", job.id.toLong())
                .get()
                .await()
            snap.documents
                .filter { it.getString("kind") == "boost" && (uid == null || it.getString("uid") == uid) }
                .map { doc ->
                    Triple(
                        doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                        when (doc.getString("productId")) {
                            "boost_7d" -> "Featured — 7 days"
                            else -> "Featured — 3 days"
                        },
                        doc.getString("status") ?: "initialized"
                    )
                }
                .sortedByDescending { it.first }
        } catch (_: Exception) {
            emptyList()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "★ Boost history",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                job.title,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )
            when {
                entries == null -> {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
                entries!!.isEmpty() -> {
                    Text(
                        "No boosts yet. Feature this job to keep it at the top of the home feed.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> {
                    val fmt = remember { java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault()) }
                    entries!!.forEach { (time, plan, status) ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = Color(0xFFF9A825),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(plan, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(
                                    fmt.format(java.util.Date(time)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                if (status == "success") "Paid" else "Pending",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (status == "success") Color(0xFF2E7D32)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Human-readable time remaining on a featured boost. */
private fun boostTimeLeft(featuredUntil: Long): String {
    val ms = featuredUntil - System.currentTimeMillis()
    if (ms <= 0) return "expired"
    val hours = ms / 3600000
    return when {
        hours < 1 -> "${ms / 60000}m left"
        hours < 24 -> "${hours}h left"
        else -> "${ms / 86400000}d left"
    }
}

/** Bottom sheet offering the two Featured Boost plans for a job. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoostSheet(
    job: Job,
    onDismiss: () -> Unit,
    onPlan: (BillingProduct) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                "★ Boost \"${job.title}\"",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Featured jobs get a gold badge and stay at the top of the home feed.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            listOf(
                BillingProduct.Boost3d() to "Quick visibility",
                BillingProduct.Boost7d() to "Best value — save 17%"
            ).forEach { (plan, note) ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clickable { onPlan(plan) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFF9A825),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(plan.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(note, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "KSh ${plan.amountKes}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                "Secured by Paystack · Cards, M-Pesa, bank & USSD",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
