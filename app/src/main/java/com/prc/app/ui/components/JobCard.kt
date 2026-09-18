package com.prc.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.data.SavedRepository

/**
 * Rich job card (template's .job-card): poster logo square, save bookmark,
 * title + poster, type/match tags, pay + age footer split by a hairline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobCard(
    job: Job,
    showMatch: Boolean = false,
    showLocation: Boolean = false,
    quickApply: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val saved by SavedRepository.saved.collectAsState()
    val user by AuthRepository.currentUser.collectAsState()
    val isSaved = job.id in saved
    val match = matchPercent(job, user?.skills.orEmpty())

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            // logo square + bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(categoryAccent(job.category), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initials(job.postedBy),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Icon(
                    if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { SavedRepository.toggle(job.id) }
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                job.title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                job.postedBy,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            // tags: category+group, type, poster type, optional extras —
            // wraps instead of clipping so the card content never breaks out
            // of the card, no matter how many badges apply.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (job.isFeatured) TagPill("★ Featured", gold = true)
                CategoryGroupChip(job)
                if (job.premium) TagPill("🔒 Premium")
                TagPill(job.type)
                PosterTypeBadge(job)
                if (job.remoteOk) TagPill("Remote")
                if (job.isProviderJob) TagPill("Verified", verified = true)
                if (showLocation) TagPill(job.location)
                if (showMatch && match != null) TagPill("$match% match", gold = true)
                if (job.deadlineDays != null && job.deadlineDays <= 3) TagPill("Closing soon", danger = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    job.pay.ifBlank { "Pay not stated" },
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (job.pay.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                Text(
                    job.postedAgo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (quickApply != null) {
                    Spacer(Modifier.width(8.dp))
                    quickApply()
                }
            }
        }
    }
}

@Composable
fun TagPill(
    text: String,
    gold: Boolean = false,
    danger: Boolean = false,
    verified: Boolean = false,
    company: Boolean = false,
    individual: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = when {
            danger -> MaterialTheme.colorScheme.errorContainer
            gold -> MaterialTheme.colorScheme.tertiaryContainer
            verified -> MaterialTheme.colorScheme.primary
            company -> Color(0xFFE4F0EC)
            individual -> Color(0xFFFDF0E7)
            else -> MaterialTheme.colorScheme.primaryContainer
        }
    ) {
        Text(
            text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                danger -> MaterialTheme.colorScheme.onErrorContainer
                gold -> MaterialTheme.colorScheme.onTertiaryContainer
                verified -> MaterialTheme.colorScheme.onPrimary
                company -> androidx.compose.ui.graphics.Color(0xFF1F7A5C)
                individual -> androidx.compose.ui.graphics.Color(0xFFB65C2E)
                else -> MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** Poster-type badge: only provider jobs carry a classification. */
@Composable
fun PosterTypeBadge(job: Job) {
    if (!job.isProviderJob) return
    if (job.isCompany) TagPill("Company", company = true) else TagPill("Individual", individual = true)
}

/** Category chip with its professional group as a prefix, e.g. "Tech · IT & Software". */
@Composable
fun CategoryGroupChip(job: Job, modifier: Modifier = Modifier) {
    val group = JobRepository.groupOf(job.category) ?: return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                groupShortLabel(group),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                job.category,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
    }
}

private fun groupShortLabel(group: String): String = when (group) {
    "Technology & Engineering" -> "Tech"
    "Health & Education" -> "Health & Edu"
    "Business & Professional" -> "Business"
    "Services & Operations" -> "Services"
    "Media & Creative" -> "Creative"
    else -> group
}

/** Up-to-two-letter initials from a person or company name. */
fun initialsOf(name: String): String =
    name.split(" ", limit = 2).filter { it.isNotBlank() }
        .map { it.first().uppercase() }
        .joinToString("")
        .take(2)
        .ifEmpty { "\uD83D\uDCBC" }

private fun initials(name: String): String =
    name.split(" ", limit = 2).filter { it.isNotBlank() }
        .map { it.first().uppercase() }
        .joinToString("")
        .take(2)
        .ifEmpty { "\uD83D\uDCBC" }

/**
 * Real skill-based match: overlap between the user's skills and the job's
 * required skills/requirements. Returns null when it can't be computed
 * (no profile skills, or job lists none) — no fake numbers.
 */
fun matchPercent(job: Job, userSkills: List<String>): Int? {
    val targets = (job.skills + job.requirements)
        .map { it.trim().lowercase() }
        .filter { it.isNotBlank() }
        .distinct()
    if (userSkills.isEmpty() || targets.isEmpty()) return null
    val owned = userSkills.map { it.trim().lowercase() }.toSet()
    val hits = targets.count { target ->
        owned.any { skill -> target.contains(skill) || skill.contains(target) }
    }
    return ((hits * 100) / targets.size).coerceIn(0, 100).takeIf { it >= 40 }
}
