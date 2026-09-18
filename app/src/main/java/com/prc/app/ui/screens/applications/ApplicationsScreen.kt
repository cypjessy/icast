package com.prc.app.ui.screens.applications

import com.prc.app.ui.theme.SystemBarAppearance

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.Application
import com.prc.app.data.AuthRepository
import com.prc.app.data.JobRepository
import com.prc.app.ui.components.categoryAccent

@Composable
fun ApplicationsScreen(onJobClick: (Int) -> Unit) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val allApplications by JobRepository.applications.collectAsState()
    val me by AuthRepository.currentUser.collectAsState()
    val syncing by JobRepository.syncing.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val applications = allApplications.filter { it.applicantContact == me?.contact }

    if (applications.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("📋", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text("No applications yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Jobs you apply for will show up here with their status.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    var statusTab by remember { mutableStateOf("All") }
    var sortBy by remember { mutableStateOf("recent") }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val pending = applications.count { it.status == "Pending" }
    val accepted = applications.count { it.status == "Accepted" }

    val filtered = applications
        .filter { statusTab == "All" || it.status == statusTab }
        .let { list ->
            when (sortBy) {
                "status" -> list.sortedBy { it.status }
                "pay" -> list.sortedByDescending { payNumber(it.job.pay) }
                else -> list
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // ==================== pull-to-refresh ====================
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = syncing,
            onRefresh = { scope.launch { JobRepository.refresh() } },
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            Modifier
                .fillMaxSize()
        ) {
        // ==================== header ====================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "My Applications",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .size(38.dp)
                        .clickable { sortMenuOpen = true }
                ) {}
                Icon(
                    Icons.Filled.FilterList,
                    contentDescription = "Sort",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(17.dp)
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    listOf(
                        "recent" to "Most recent",
                        "status" to "By status",
                        "pay" to "Highest pay"
                    ).forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                sortBy = key
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // ==================== stats strip ====================
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(vertical = 16.dp, horizontal = 6.dp)) {
                    Stat(applications.size.toString(), "Applied", Modifier.weight(1f))
                    StatDivider()
                    Stat(accepted.toString(), "Accepted", Modifier.weight(1f))
                    StatDivider()
                    Stat(pending.toString(), "Pending", Modifier.weight(1f))
                }
            }
            // deco circle
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = (-40).dp)
                    .size(120.dp)
                    .alpha(0.07f)
                    .background(Color.White, CircleShape)
            )
        }

        // ==================== status tabs ====================
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
        ) {
            val tabs = listOf(
                "All" to applications.size,
                "Pending" to pending,
                "Accepted" to accepted,
                "Rejected" to applications.count { it.status == "Rejected" }
            )
            items(tabs, key = { it.first }) { (label, count) ->
                val active = statusTab == label
                Surface(
                    onClick = { statusTab = label },
                    shape = CircleShape,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface,
                    border = if (!active) androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.outlineVariant
                    ) else null
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Text(
                            label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$count",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    if (active) Color.White.copy(alpha = 0.22f)
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // ==================== list ====================
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered, key = { it.job.id }) { app ->
                var confirmWithdraw by remember { mutableStateOf(false) }
                ApplicationCard(
                    app,
                    onWithdraw = if (app.status == "Pending" && !app.job.closed) {{ confirmWithdraw = true }} else null
                ) {
                    if (app.job.id > 0) onJobClick(app.job.id)
                }
                if (confirmWithdraw) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { confirmWithdraw = false },
                        title = { Text("Withdraw application?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                        text = {
                            Text(
                                "Your application for \"${app.job.title}\" will be removed. The employer will no longer see it.",
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )
                        },
                        confirmButton = {
                            androidx.compose.material3.TextButton(onClick = {
                                confirmWithdraw = false
                                JobRepository.withdraw(app.job.id, app.applicantContact)
                            }) { Text("Withdraw", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { confirmWithdraw = false }) { Text("Keep") }
                        }
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No $statusTab applications.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
        }
    }
    }
}


@Composable
private fun ApplicationCard(app: Application, onWithdraw: (() -> Unit)? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row {
                // logo square
                Box(
                    Modifier
                        .size(42.dp)
                        .background(categoryAccent(app.job.category), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        app.job.postedBy.split(" ").filter { it.isNotBlank() }.take(2)
                            .map { it.first().uppercase() }.joinToString(""),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.job.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        app.job.postedBy,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    Text(
                        "Applied ${timeAgo(app.appliedAt)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusBadge(app.status)
            }
            // footer: last update line
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = when (app.status) {
                            "Accepted" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        when (app.status) {
                            "Accepted" -> "Accepted — contact them via the job page"
                            "Rejected" -> "Not selected this time"
                            "Shortlisted" -> "Shortlisted — you're a step closer"
                            else -> "No updates yet"
                        },
                        fontSize = 11.sp,
                        fontWeight = if (app.status == "Accepted") FontWeight.SemiBold else FontWeight.Normal,
                        color = if (app.status == "Accepted") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onWithdraw != null) {
                    Text(
                        "Withdraw",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .clickable { onWithdraw() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, fg) = when (status) {
        "Accepted" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "Rejected" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            status,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary,
            lineHeight = 22.sp
        )
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color.White.copy(alpha = 0.18f))
    )
}

private fun payNumber(pay: String): Int =
    Regex("KSh\\s*([0-9][0-9,]*)").find(pay)
        ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0

private fun timeAgo(millis: Long): String {
    if (millis <= 0) return "recently"
    val mins = (System.currentTimeMillis() - millis) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}
