package com.prc.app.ui.screens.jobs

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.data.SavedRepository
import com.prc.app.ui.components.JobCard
import com.prc.app.ui.components.categoryAccent
import com.prc.app.ui.theme.SystemBarAppearance

// ============================================================
// View Job page — sections render only when the job has data,
// so empty jobs never leave blank stretches.
// ============================================================

@Composable
fun JobDetailsScreen(
    jobId: Int,
    onBack: () -> Unit,
    onViewApplicants: (Int) -> Unit = {},
    onApply: (Int) -> Unit = {}
) {
    SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val job = JobRepository.job(jobId)
    val applications by JobRepository.applications.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()
    val me = AuthRepository.currentUser.collectAsState().value
    val savedIds by SavedRepository.saved.collectAsState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(jobId) {
        com.prc.app.data.RecentlyViewedRepository.record(jobId)
    }

    if (job == null) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp)
        ) {
            Text("Job not found.", fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    val applied = applications.any { it.job.id == jobId && it.applicantContact == me?.contact }
    val isMine = job.posterContact.isNotEmpty() && job.posterContact == me?.contact
    val applicantCount = applications.count { it.job.id == jobId }
    val isSaved = job.id in savedIds
    val similar = jobs.filter {
        !it.draft && !it.closed && it.category == job.category && it.id != job.id
    }.take(5)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ApplyBar(
                job = job,
                isMine = isMine,
                applied = applied,
                isSaved = isSaved,
                applicantCount = applicantCount,
                onToggleSave = { SavedRepository.toggle(job.id) },
                onApply = { openApplyChannel(job, context) ?: onApply(job.id) },
                onViewApplicants = { onViewApplicants(jobId) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ==================== hero ====================
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 0.dp)
                        .offset()
                        .size(150.dp)
                        .alpha(0.05f)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                )
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 6.dp, bottom = 20.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        CircleAction(Icons.AutoMirrored.Filled.ArrowBack) { onBack() }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircleAction(Icons.Filled.Share) {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "${job.title} — ${job.pay} · ${job.location} (via PRC Jobs)"
                                    )
                                }
                                context.startActivity(Intent.createChooser(send, "Share job"))
                            }
                            CircleAction(
                                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
                            ) { SavedRepository.toggle(job.id) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(13.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    initials(job.postedBy),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column {
                            Text(
                                job.title,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 24.sp
                            )
                            Spacer(Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    job.postedBy,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Verified poster",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== info strip ====================
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(Modifier.padding(vertical = 12.dp)) {
                    InfoCell(
                        value = job.pay.substringBefore("/").trim().ifBlank { "Not stated" },
                        label = "Pay",
                        icon = Icons.Filled.WorkOutline,
                        modifier = Modifier.weight(1.3f)
                    )
                    VDivider()
                    InfoCell(
                        value = job.type,
                        label = "Job type",
                        icon = Icons.Filled.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                    VDivider()
                    InfoCell(
                        value = job.postedAgo,
                        label = "Posted",
                        icon = Icons.Filled.Schedule,
                        modifier = Modifier.weight(1f)
                    )
                    VDivider()
                    InfoCell(
                        value = applicantCount.toString(),
                        label = "Applicants",
                        icon = Icons.Filled.Group,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ==================== content sections (only when present) ====================
            if (job.description.isNotBlank()) {
                Section("About the role") {
                    Text(
                        job.description,
                        fontSize = 13.5.sp,
                        lineHeight = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (job.responsibilities.isNotEmpty()) {
                Section("Responsibilities") {
                    BulletList(job.responsibilities)
                }
            }

            if (job.requirements.isNotEmpty()) {
                Section("Requirements") {
                    BulletList(job.requirements)
                }
            }

            if (job.skills.isNotEmpty()) {
                Section("Skills") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        job.skills.forEach { SkillTag(it) }
                    }
                }
            }

            if (job.pay.isNotBlank()) {
                Section("Salary") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    job.pay,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "as posted by ${job.postedBy}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            com.prc.app.ui.components.CategoryGroupChip(job, modifier = Modifier)
                            if (job.premium) {
                                com.prc.app.ui.components.TagPill("🔒 Premium application")
                            }
                        }
                    }
                }
            }

            Section("About the poster") {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(Modifier.padding(16.dp)) {
                        Surface(
                            shape = RoundedCornerShape(9.dp),
                            color = categoryAccent(job.category),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    initials(job.postedBy),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    job.postedBy,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(8.dp))
                                if (job.isProviderJob) {
                                    com.prc.app.ui.components.TagPill(
                                        if (job.isCompany) "Company" else "Individual",
                                        company = job.isCompany,
                                        individual = !job.isCompany
                                    )
                                }
                            }
                            Text(
                                "${job.category} · ${job.location} · " +
                                        posterJobsLabel(jobs.count { it.postedBy == job.postedBy }),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Responds through the app",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==================== similar jobs ====================
            if (similar.isNotEmpty()) {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        "Similar jobs",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp)
                    ) {
                        items(similar, key = { it.id }) { sim ->
                            Box(Modifier.width(210.dp)) { JobCard(sim) { } }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ==================== pieces ====================

/** Modifiers wrapper kept minimal; offset deco removed in rebuild for clean spacing. */
private fun Modifier.offset(): Modifier = this

@Composable
private fun CircleAction(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .size(36.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun InfoCell(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.height(5.dp))
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface
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
private fun VDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun BulletList(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    item,
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillTag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/**
 * External application channel for a PROVIDER job, if any:
 * email address -> mail client, web link -> browser, else provider phone -> dialer.
 * Returns null for community (user-posted) jobs and provider jobs without an
 * external channel — those always apply in-app.
 */
private fun openApplyChannel(job: Job, context: android.content.Context): Boolean? {
    // Internal (user-posted) jobs apply in-app only — never leave the app.
    if (!job.isProviderJob) return null
    // Scraped LinkedIn posts whose only link is the LinkedIn login wall:
    // apply in-app instead of bouncing users to a login page.
    if (job.needsLinkedin) return null
    val url = job.applicationUrl.trim()
    return try {
        when {
            url.contains("@") && !url.startsWith("http") -> {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$url")).apply {
                    putExtra(Intent.EXTRA_SUBJECT, "Application: ${job.title}")
                }
                context.startActivity(Intent.createChooser(intent, "Apply via email"))
                true
            }
            url.startsWith("http") -> {
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build()
                    .launchUrl(context, Uri.parse(url))
                true
            }
            url.startsWith("tel:") || url.filter { it.isDigit() }.length >= 7 -> {
                val tel = url.removePrefix("tel:").filter { it.isDigit() || it == '+' }
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel")))
                true
            }
            else -> null
        }
    } catch (_: Exception) {
        null // no app can handle the channel; fall back to in-app apply
    }
}

/** Button caption reflecting how the user will apply. */
private fun applyButtonLabel(job: Job): String {
    if (!job.isProviderJob || job.needsLinkedin) return "Apply now"   // in-app apply
    val url = job.applicationUrl.trim()
    return when {
        url.contains("@") && !url.startsWith("http") -> "Apply via email"
        url.startsWith("http") -> "Apply on website"
        url.startsWith("tel:") || url.filter { it.isDigit() }.length >= 7 -> "Call to apply"
        else -> "Apply now"
    }
}

@Composable
private fun ApplyBar(
    job: Job,
    isMine: Boolean,
    applied: Boolean,
    isSaved: Boolean,
    applicantCount: Int,
    onToggleSave: () -> Unit,
    onApply: () -> Unit,
    onViewApplicants: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "Posted ${job.postedAgo}" +
                        (if (job.openings > 1) " · ${job.openings} openings" else ""),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            if (isMine) {
                Button(
                    onClick = onViewApplicants,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (applicantCount == 0) "View applicants"
                        else "View applicants ($applicantCount)",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(48.dp)
                            .clickable { onToggleSave() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (isSaved) "Unsave" else "Save",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = onApply,
                        enabled = !applied,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            if (applied) "Applied ✓"
                            else if (job.premium) "🔒 ${applyButtonLabel(job)}"
                            else applyButtonLabel(job),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun posterJobsLabel(count: Int): String =
    if (count == 1) "1 job posted" else "$count jobs posted"

private fun initials(name: String): String =
    name.split(" ", limit = 2).filter { it.isNotBlank() }
        .map { it.first().uppercase() }
        .joinToString("")
        .take(2)
        .ifEmpty { "\uD83D\uDCBC" }
