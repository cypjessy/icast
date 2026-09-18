package com.prc.app.ui.screens.home

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Application
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.data.NotificationRepository
import com.prc.app.data.DismissedJobsRepository
import com.prc.app.data.RecentlyViewedRepository
import com.prc.app.data.User
import com.prc.app.data.hasAnyExtra
import com.prc.app.ui.components.JobCard
import com.prc.app.ui.components.JobListRow
import com.prc.app.ui.screens.auth.AuroraGold
import com.prc.app.ui.screens.auth.AuroraGreen
import com.prc.app.ui.screens.auth.AuroraMint
import com.prc.app.ui.screens.auth.EaseOutExpo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val brandPalette = listOf(
    Color(0xFF161616), Color(0xFF2E2E2E), Color(0xFF0F5C4E),
    Color(0xFF2F6F3E), Color(0xFF4B5A55), Color(0xFFB65C2E)
)

private fun brandColor(name: String): Color =
    brandPalette[kotlin.math.abs(name.hashCode()) % brandPalette.size]

private val HeroDark = Color(0xFF141414)

/** Entrance helper: kept for call sites, but the old preferred look is static — always fully visible. */
@Composable
private fun rememberRise(delayMillis: Int = 0): Float = 1f

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeFeedScreen(
    onJobClick: (Int) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onEditProfileClick: () -> Unit = {},
    onSeeApplicationsClick: () -> Unit = {},
    onCompanyClick: (String) -> Unit = {}
) {
        SystemBarAppearance(darkIcons = false, statusBarColor = MaterialTheme.colorScheme.primary)

    val user by AuthRepository.currentUser.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()
    val allJobs = jobs.filter { !it.draft && !it.closed }
    val applications by JobRepository.applications.collectAsState()
    val allNotifications by NotificationRepository.notifications.collectAsState()
    val readKeys by NotificationRepository.readBy.collectAsState()
    val unread = allNotifications.count {
        it.recipientContact == user?.contact && NotificationRepository.readKey(user?.contact, it.id) !in readKeys
    }
    var selectedCategory by remember { mutableStateOf("All") }
    // Filter toggles persist across restarts via PersistedState/FilterState
    var filterRemote by remember { mutableStateOf(com.prc.app.data.FilterState.remote) }
    var filterVerified by remember { mutableStateOf(com.prc.app.data.FilterState.verified) }
    var filterClosingSoon by remember { mutableStateOf(com.prc.app.data.FilterState.closingSoon) }
    fun persistFilters() {
        com.prc.app.data.FilterState.remote = filterRemote
        com.prc.app.data.FilterState.verified = filterVerified
        com.prc.app.data.FilterState.closingSoon = filterClosingSoon
        com.prc.app.data.PersistedState.persistFilters(filterRemote, filterVerified, filterClosingSoon)
    }
    var showFilterSheet by remember { mutableStateOf(false) }
    val activeFilterCount = listOf(filterRemote, filterVerified, filterClosingSoon).count { it }
    val viewedIds by RecentlyViewedRepository.ids.collectAsState()
    val viewedJobs = viewedIds.mapNotNull { id -> allJobs.firstOrNull { it.id == id } }

    val visibleJobs = allJobs
        .filter { job ->
            (selectedCategory == "All" || job.category == selectedCategory) &&
                    (!filterRemote || job.remoteOk) &&
                    (!filterVerified || job.isProviderJob) &&
                    (!filterClosingSoon || (job.deadlineDays != null && job.deadlineDays <= 3))
        }
    val recent = visibleJobs.sortedByDescending { it.postedAtMillis }
        .sortedWith(compareByDescending<Job> { it.isFeatured }.thenByDescending { it.postedAtMillis })
        .take(5)

    // Keep the Pro directory warm so applicant lists can rank Pro seekers
    // first ("priority placement in employer searches").
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.prc.app.data.ProDirectory.start()
    }

    // Recommended for you: jobs matching the user's saved occupations/skills.
    // Fully dynamic — the section only exists when there are real matches.
    val userSkills = user?.skills.orEmpty().map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val dismissedIds by DismissedJobsRepository.ids.collectAsState()
    // Companies/categories from dismissed jobs get de-prioritized, not hard-banned:
    // one dismissal drops them down the ranking rather than removing them forever.
    val dismissedMeta = allJobs.filter { it.id in dismissedIds }
        .map { it.postedBy.lowercase() to it.category.lowercase() }
        .toSet()
    fun penalty(job: Job): Int =
        if ((job.postedBy.lowercase() to job.category.lowercase()) in dismissedMeta) 1 else 0

    fun matchReason(job: Job): String? {
        val targets = (job.skills + job.requirements + listOf(job.title, job.category))
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val hit = userSkills.firstOrNull { skill ->
            targets.any { target -> target.contains(skill) || skill.contains(target) }
        } ?: return null
        // Show the user's original (properly-cased) skill text
        val display = user?.skills?.firstOrNull { it.trim().lowercase() == hit } ?: hit
        return "Matches your skill: $display"
    }

    val recommended = allJobs
        .filter { job ->
            val targets = (job.skills + job.requirements + listOf(job.title, job.category))
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            targets.any { target -> userSkills.any { target.contains(it) || it.contains(target) } }
        }
        .filter { it !in recent && it.id !in dismissedIds }
        .sortedWith(compareBy({ penalty(it) }, { -it.postedAtMillis }))
        .take(8)
        .map { it to matchReason(it) }
    val mine = applications.filter { it.applicantContact == user?.contact }
    val context = androidx.compose.ui.platform.LocalContext.current
    val syncing by JobRepository.syncing.collectAsState()
    val triggeredAlerts by com.prc.app.data.JobAlertRepository.triggered.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = syncing,
        onRefresh = { scope.launch { JobRepository.refresh() } },
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        // ==================== premium gradient hero ====================
        item {
            val greetIn = rememberRise(0)
            val nameIn = rememberRise(90)
            val searchIn = rememberRise(180)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                            )
                        )
                    )
                    .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            ) {
                // soft glow accents
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(40f, 0f),
                                radius = 700f
                            )
                        )
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(AuroraGold.copy(alpha = 0.18f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(1100f, 60f),
                                radius = 560f
                            )
                        )
                )

                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(top = 14.dp, bottom = 20.dp)
                ) {
                    // avatar + greeting + bell
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // avatar circle with initials
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White.copy(alpha = 0.22f), CircleShape)
                                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                                    .clickable { onEditProfileClick() }
                            ) {
                                Text(
                                    (user?.fullName ?: "You").split(" ").filter { it.isNotBlank() }
                                        .take(2)
                                        .map { it.first().uppercase() }
                                        .joinToString(""),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    greeting(),
                                    fontSize = 11.5.sp,
                                    letterSpacing = 1.1.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.graphicsLayer {
                                        alpha = greetIn
                                        translationY = (1f - greetIn) * 14f
                                    }
                                )
                                Text(
                                    (user?.fullName ?: "there").split(" ").first(),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.graphicsLayer {
                                        alpha = nameIn
                                        translationY = (1f - nameIn) * 18f
                                    }
                                )
                            }
                        }
                        // bell: translucent glass circle + unread dot
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                                .clickable { onNotificationsClick() }
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(19.dp)
                            )
                            if (unread > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 8.dp, end = 8.dp)
                                        .size(9.dp)
                                        .background(Color(0xFFFFD54F), CircleShape)
                                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    }

                    // search field — glass style, inside the hero
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .graphicsLayer {
                                alpha = searchIn
                                translationY = (1f - searchIn) * 22f
                            }
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                            .clickable { onSearchClick() }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Job title, company, or skill",
                            fontSize = 13.5.sp,
                            color = Color.White.copy(alpha = 0.80f),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // filter chips row — quick access to the filter sheet
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                    ) {
                        if (activeFilterCount > 0) {
                            Surface(
                                onClick = { showFilterSheet = true },
                                shape = RoundedCornerShape(20.dp),
                                color = if (filterRemote) Color.White else Color.White.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = null,
                                        tint = if (filterRemote) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "$activeFilterCount filter${if (activeFilterCount == 1) "" else "s"} on",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (filterRemote) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = { showFilterSheet = true },
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White.copy(alpha = 0.14f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.24f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        "Filters",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White.copy(alpha = 0.92f)
                                    )
                                }
                            }
                        }
                    }

                    // location pill — translucent on gradient
                    if (!user?.location.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .border(1.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                                .clickable { onEditProfileClick() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Place,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "${user?.location}, Kenya",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        // ==================== category chips ====================
        item {
            val chipsIn = rememberRise(320)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier.graphicsLayer { alpha = chipsIn }
            ) {
                items(JobRepository.categories) { cat ->
                    val active = selectedCategory == cat
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = CircleShape,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = if (!active) androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.outlineVariant
                        ) else null
                    ) {
                        Text(
                            cat,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) androidx.compose.ui.graphics.Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)                        )
                    }
                }
            }
        }

        // ==================== recently viewed ====================
        if (viewedJobs.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                SectionHeader("Recently viewed")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(viewedJobs, key = { it.id }) { job ->
                        Surface(
                            onClick = { onJobClick(job.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.width(190.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(
                                            com.prc.app.ui.components.categoryAccent(job.category),
                                            CircleShape
                                        )
                                ) {
                                    if (job.postedBy.isBlank()) {
                                        androidx.compose.material3.Icon(
                                            androidx.compose.material.icons.Icons.Filled.BusinessCenter,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Text(
                                            job.postedBy.split(" ").filter { it.isNotBlank() }
                                                .take(2).map { it.first().uppercase() }.joinToString(""),
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        job.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        job.postedBy,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==================== job alert matches (popup cards) ====================
        if (triggeredAlerts.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    triggeredAlerts.take(3).forEach { (alert, job) ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(13.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                ) {
                                    Icon(
                                        Icons.Filled.Notifications,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Job alert match",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "\"${job.title}\" at ${job.postedBy}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Matches your alert for \"${alert.keyword}\" · ${job.location}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    "View",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            com.prc.app.data.JobAlertRepository.clearTriggered(job.id)
                                            onJobClick(job.id)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { com.prc.app.data.JobAlertRepository.clearTriggered(job.id) }
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== empty state ====================
        if (allJobs.isEmpty()) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(84.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Filled.WorkOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "No jobs posted yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "New openings will appear here the moment employers post them. Be the first — post a job or check back soon.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        // ==================== profile completion banner ====================
        if (profileCompletion(user?.location, user?.skills, user?.bio, user?.hasAnyExtra() == true) < 100) {
            item {
                val pct = profileCompletion(user?.location, user?.skills, user?.bio, user?.hasAnyExtra() == true)
                val bannerIn = rememberRise(380)
                Surface(
                    onClick = onEditProfileClick,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            alpha = bannerIn
                            translationY = (1f - bannerIn) * 26f
                        }
                ) {
                    Row(
                        Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressRing(pct)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Your profile is $pct% complete",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                completionHint(user),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Chevron()
                    }
                }
            }
        }

        // ==================== recommended for you ====================
        if (recommended.isNotEmpty()) {
            item {
                SectionHeader("Recommended for you")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(recommended, key = { it.first.id }) { (job, reason) ->
                        Box(Modifier.width(230.dp)) {
                            Column {
                                JobCard(job, showMatch = true) { onJobClick(job.id) }
                                if (reason != null) {
                                    Text(
                                        reason,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                                    )
                                }
                                Text(
                                    "Not interested",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(top = 4.dp, start = 2.dp)
                                        .clickable {
                                            DismissedJobsRepository.dismiss(job.id)
                                            scope.launch {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Got it — we'll show fewer jobs like this",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ==================== newly added jobs ====================
        if (recent.isNotEmpty()) {
            item {
                SectionHeader("Newly added jobs")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(recent, key = { it.id }) { job ->
                        Box(Modifier.width(230.dp)) {
                            JobCard(job, showMatch = true) { onJobClick(job.id) }
                        }
                    }
                }
            }
        }

        // ==================== companies hiring ====================
        if (selectedCategory == "All") {
            item {
                SectionHeader("Companies hiring")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    posterChips(visibleJobs).forEach { name ->
                        val count = visibleJobs.count { it.postedBy == name }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onCompanyClick(name) }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                brandColor(name),
                                                brandColor(name).darken(0.75f)
                                            )
                                        ),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.split(" ").filter { it.isNotBlank() }.take(2)
                                        .map { it.first().uppercase() }.joinToString(""),
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "$count open job${if (count == 1) "" else "s"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                "View",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // ==================== applications snapshot ====================
        item {
            val statsIn = rememberRise(200)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 22.dp)
                    .graphicsLayer {
                        alpha = statsIn
                        translationY = (1f - statsIn) * 26f
                    }
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Your applications",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "View all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSeeApplicationsClick() }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row {
                        SnapStat(mine.size.toString(), "Applied", Modifier.weight(1f))
                        StatDivider()
                        SnapStat(
                            mine.count { it.status == "Accepted" }.toString(),
                            "Accepted",
                            Modifier.weight(1f)
                        )
                        StatDivider()
                        SnapStat(
                            mine.count { it.status == "Pending" }.toString(),
                            "Pending",
                            Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
    }
    // ==================== smart filter sheet ====================
    if (showFilterSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(
                    "Filter jobs",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Show only jobs that match your filters",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))

                FilterToggle(
                    "Remote only",
                    "Jobs that can be done from anywhere",
                    filterRemote
                ) { filterRemote = it; persistFilters() }
                FilterToggle(
                    "Verified employers",
                    "Only admin-approved / provider jobs",
                    filterVerified
                ) { filterVerified = it; persistFilters() }
                FilterToggle(
                    "Closing soon",
                    "Applications close within 3 days",
                    filterClosingSoon
                ) { filterClosingSoon = it; persistFilters() }

                Spacer(Modifier.height(20.dp))
                Row {
                    Surface(
                        onClick = {
                            filterRemote = false
                            filterVerified = false
                            filterClosingSoon = false
                            persistFilters()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Clear all",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = { showFilterSheet = false },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            "Apply",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterToggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(14.dp),
        color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onChange
            )
        }
    }
}

// ==================== hero + shared pieces ====================

@Composable
private fun ProgressRing(pct: Int) {
    val animated by animateFloatAsState(
        targetValue = pct / 100f,
        animationSpec = tween(1100, 300),
        label = "ringPct"
    )
    val track = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(
            Modifier.size(48.dp)
        ) {
            val stroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = track, startAngle = 0f, sweepAngle = 360f,
                useCenter = false, style = stroke
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(AuroraMint, AuroraGold, AuroraMint)),
                startAngle = -90f, sweepAngle = 360f * animated,
                useCenter = false, style = stroke
            )
        }
        Text(
            "$pct%",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun Chevron() {
    Text(
        "›",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun SectionHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 15.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (action != null) action()
    }
}

@Composable
private fun HorizontalHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    )
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun SnapStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== helpers ====================

private fun Color.darken(f: Float): Color = Color(
    red = red * f, green = green * f, blue = blue * f, alpha = alpha
)

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        h < 12 -> "GOOD MORNING"
        h < 17 -> "GOOD AFTERNOON"
        else -> "GOOD EVENING"
    }
}

/** Profile completeness: base 40 + location 20 + 2+ skills 20 + bio 10 + one extra section (CV/experience/education/…) 10. */
fun profileCompletion(location: String?, skills: List<String>?, bio: String? = null, hasExtraSection: Boolean = false): Int {
    var pct = 40
    if (!location.isNullOrBlank()) pct += 20
    if (!skills.isNullOrEmpty() && skills.size >= 2) pct += 20
    if (!bio.isNullOrBlank()) pct += 10
    if (hasExtraSection) pct += 10
    return pct
}

private fun completionHint(u: User?): String = when {
    u == null -> "Sign in to complete your profile"
    u.location.isBlank() -> "Add your location so employers find you faster"
    u.skills.size < 2 -> "Add your skills so employers find you faster"
    u.bio.isBlank() -> "Add a short bio so employers know you"
    else -> "Add a CV, experience or languages to reach 100%"
}

private fun posterChips(jobs: List<Job>): List<String> =
    // Companies hiring lists only jobs AI-classified as posted by a company/organization
    // AND whose poster name was actually identified — no name = not a company listing.
    // User-posted (community) jobs never appear here — companies post via admin only.
    jobs.filter { it.isProviderJob && it.isCompany && it.postedBy.isNotBlank() }
        .map { it.postedBy }.distinct().take(8)
