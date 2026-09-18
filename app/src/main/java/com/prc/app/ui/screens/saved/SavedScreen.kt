package com.prc.app.ui.screens.saved

import com.prc.app.ui.theme.SystemBarAppearance

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.data.SavedRepository
import com.prc.app.ui.components.categoryAccent
import com.prc.app.ui.components.initialsOf
import com.prc.app.ui.components.TagPill

@Composable
fun SavedScreen(
    onJobClick: (Int) -> Unit,
    onBrowse: () -> Unit = {}
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val savedMap by SavedRepository.saved.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()
    val user by AuthRepository.currentUser.collectAsState()
    val applications by JobRepository.applications.collectAsState()
    val syncing by JobRepository.syncing.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    // Selection state for bulk actions
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Int>()) }

    var sortIdx by remember { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val sorts = listOf("Recently saved", "Closing soonest", "Highest pay")

    val savedJobs = jobs.filter { it.id in savedMap.keys && !it.draft && !it.closed }.sortedByDescending { it.postedAtMillis }.let { list ->
        when (sortIdx) {
            1 -> list.sortedBy { it.deadlineDays ?: 999 }
            2 -> list.sortedByDescending { parsePay(it.pay) }
            else -> list.sortedByDescending { savedMap[it.id] ?: 0L }
        }
    }

    val urgent = savedJobs.count { (it.deadlineDays ?: 99) <= 7 }

    if (savedJobs.isEmpty()) {
        // ==================== empty state ====================
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text("You haven't saved any jobs yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Bookmark jobs to find them here later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onBrowse() }
            ) {
                Text(
                    "Browse jobs",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
        return
    }

    // ==================== list ====================
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = syncing,
        onRefresh = { scope.launch { JobRepository.refresh() } },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
    ) {
        // header
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Saved Jobs", fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (savedJobs.size == 1) "1 job saved" else "${savedJobs.size} jobs saved",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Box {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Sort",
                            modifier = Modifier
                                .padding(10.dp)
                                .size(17.dp)
                                .clickable { sortMenu = true },
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            sorts.forEachIndexed { i, s ->
                                DropdownMenuItem(
                                    text = { Text(s, fontSize = 13.sp) },
                                    onClick = {
                                        sortIdx = i
                                        sortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // expiring-soon banner
        if (urgent > 0) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(13.dp)
                    ) {
                        ClockDot()
                        Spacer(Modifier.width(11.dp))
                        Column {
                            Text(
                                if (urgent == 1) "1 saved job closes this week"
                                else "$urgent saved jobs close this week",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Apply before they expire",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // toolbar
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (selectionMode) "${selected.size} selected"
                    else if (savedJobs.size == 1) "1 saved job" else "${savedJobs.size} saved jobs",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectionMode) {
                        Text(
                            if (selected.size == savedJobs.size) "Deselect all" else "Select all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    selected = if (selected.size == savedJobs.size) emptySet()
                                    else savedJobs.map { it.id }.toSet()
                                }
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            "Done",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable {
                                    selectionMode = false
                                    selected = emptySet()
                                }
                                .padding(horizontal = 8.dp)
                        )
                    } else {
                        Text(
                            "Select",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { selectionMode = true }
                                .padding(horizontal = 8.dp)
                        )
                    }
                    Text(
                        sorts[sortIdx],
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { sortMenu = true }
                    )
                }
            }
        }

        // saved cards
        itemsIndexed(savedJobs, key = { _, j -> j.id }) { _, job ->
            val isApplied = applications.any {
                it.applicantContact == user?.contact && it.job.id == job.id
            }
            SavedCard(
                job = job,
                isApplied = isApplied,
                selectionMode = selectionMode,
                isSelected = job.id in selected,
                onToggleSelect = {
                    selected = if (job.id in selected) selected - job.id else selected + job.id
                },
                onRemove = { SavedRepository.remove(job.id) },
                onClick = {
                    if (selectionMode) {
                        selected = if (job.id in selected) selected - job.id else selected + job.id
                    } else onJobClick(job.id)
                }
            )
        }

        // bulk action bar
        if (selectionMode && selected.isNotEmpty()) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selected.forEach { SavedRepository.remove(it) }
                                selected = emptySet()
                                selectionMode = false
                                android.widget.Toast.makeText(
                                    context, "Removed from saved", android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                    ) {
                        Text(
                            "Remove (${selected.size})",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun SavedCard(
    job: Job,
    isApplied: Boolean,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    val user by AuthRepository.currentUser.collectAsState()
    val isOwn = user != null && job.posterContact == user?.contact
    val deadline = job.deadlineDays

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(14.dp)) {
            // top: logo, body, remove
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(categoryAccent(job.category), RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        initialsOf(job.postedBy),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        job.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        job.postedBy,
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TagPill(job.type)
                        if (deadline != null && deadline <= 7) {
                            TagPill("Closes in $deadline days", danger = true)
                        }
                    }
                }
                Spacer(Modifier.width(10.dp))
                if (selectionMode) {
                    // selection checkbox
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { onToggleSelect() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    // remove = filled bookmark in tinted circle
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.clickable { onRemove() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "Remove from saved",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(11.dp))
            // footer: pay + saved date | apply
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(job.pay, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        SavedRepository.savedAgo(job.id),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                when {
                    isOwn -> Text(
                        "Your job",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    isApplied -> Text(
                        "Applied ✓",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    else -> Text(
                        "View & apply →",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onClick() }
                    )
                }
            }
        }
    }
}

/** Small red clock dot for the urgency banner. */
@Composable
private fun ClockDot() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
        modifier = Modifier.size(26.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text("⏰", fontSize = 12.sp)
        }
    }
}

/** Extracts the leading number from a "KSh 1,500/day" style pay string. */
private fun parsePay(pay: String): Int =
    pay.filter { it.isDigit() }.take(5).toIntOrNull() ?: 0
