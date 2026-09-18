package com.prc.app.ui.screens.search

import com.prc.app.ui.theme.SystemBarAppearance

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.prc.app.data.AuthRepository
import com.prc.app.data.Job
import com.prc.app.data.JobRepository
import com.prc.app.ui.components.JobCard
import com.prc.app.ui.components.TagPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onJobClick: (Int) -> Unit, onUpgradeToPro: () -> Unit = {}) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val user by AuthRepository.currentUser.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("All") }
    var jobType by remember { mutableStateOf("Any type") }
    var posted by remember { mutableStateOf("Any time") }
    var minPay by remember { mutableStateOf<Int?>(null) }
    var remoteOnly by remember { mutableStateOf(false) }
    var verifiedOnly by remember { mutableStateOf(false) }
    var closingSoon by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("newest") }
    var showSortMenu by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    // An alert is only possible once the user has actually typed a search.
    val hasSearchableAlert = query.trim().isNotBlank()
    val alertExists = com.prc.app.data.JobAlertRepository.hasAlert(query.trim(), category)
    var alertOn by remember(query, category, alertExists) {
        mutableStateOf(alertExists)
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Free-tier alert cap: free users keep 1 alert, Pro is unlimited.
    var isProUser by remember { mutableStateOf<Boolean?>(null) }
    var showAlertLimitDialog by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        isProUser = com.prc.app.data.ProGate.isPro(com.prc.app.data.AuthRepository.currentUid())
    }
    if (showAlertLimitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showAlertLimitDialog = false },
            title = { Text("Alert limit reached", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Free accounts keep ${com.prc.app.data.ProGate.FREE_MAX_ALERTS} job alert. Upgrade to Pro for unlimited alerts and never miss a opening that fits you.",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showAlertLimitDialog = false
                    onUpgradeToPro()
                }) {
                    Text("Upgrade to Pro", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showAlertLimitDialog = false }) {
                    Text("Maybe later")
                }
            }
        )
    }

    val activeFilterCount =
        (if (category != "All") 1 else 0) +
                (if (jobType != "Any type") 1 else 0) +
                (if (posted != "Any time") 1 else 0) +
                (if (minPay != null) 1 else 0) +
                (if (remoteOnly) 1 else 0) +
                (if (verifiedOnly) 1 else 0) +
                (if (closingSoon) 1 else 0)

    val userSkills = user?.skills.orEmpty().map { it.trim().lowercase() }.filter { it.isNotBlank() }
    val results = remember(query, category, jobType, posted, minPay, remoteOnly, verifiedOnly, closingSoon, sortBy, jobs) {
        val now = System.currentTimeMillis()
        val filtered = JobRepository.search(query, category)
            .filter { jobType == "Any type" || it.type == jobType }
            .filter { posted == "Any time" || (now - it.postedAtMillis) <= if (posted == "Last 24 hours") 1 else 3 * 24 * 60 * 60 * 1000L }
            .filter { minPay == null || payNumber(it.pay) >= minPay!! }
            .filter { !remoteOnly || it.remoteOk }
            .filter { !verifiedOnly || it.isProviderJob }
            .filter { !closingSoon || (it.deadlineDays != null && it.deadlineDays <= 3) }
        when (sortBy) {
            "pay" -> filtered.sortedByDescending { payNumber(it.pay) }
            "match" -> filtered.sortedByDescending { job ->
                val targets = (job.skills + job.requirements).map { it.trim().lowercase() }
                targets.count { t -> userSkills.any { t.contains(it) || it.contains(t) } }
            }
            "oldest" -> filtered.sortedBy { it.postedAtMillis }
            else -> filtered.sortedByDescending { it.postedAtMillis }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        // ==================== search header ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Search",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.5.dp, MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(9.dp))
                androidx.compose.material3.TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Job title, company, or skill",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Outlined.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ==================== filter bar ====================
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
        ) {
            item {
                FilterChipPill(
                    label = if (activeFilterCount > 0) "Filters ($activeFilterCount)" else "Filters",
                    primary = activeFilterCount > 0,
                    leading = "☰",
                    onClick = { sheetFor = "category" }
                )
            }
            item {
                FilterChipPill(
                    label = if (category == "All") "Category" else category,
                    active = category != "All",
                    onClick = { sheetFor = "category" }
                )
            }
            item {
                FilterChipPill(
                    label = if (jobType == "Any type") "Job type" else jobType,
                    active = jobType != "Any type",
                    onClick = { sheetFor = "type" }
                )
            }
            item {
                FilterChipPill(
                    label = if (posted == "Any time") "Date posted" else posted,
                    active = posted != "Any time",
                    onClick = { sheetFor = "posted" }
                )
            }
            item {
                FilterChipPill(
                    label = if (minPay == null) "Salary" else "KSh ${minPay}+",
                    active = minPay != null,
                    onClick = { sheetFor = "pay" }
                )
            }
            item {
                FilterChipPill(
                    label = if (remoteOnly) "Remote ✓" else "Remote",
                    active = remoteOnly,
                    onClick = { remoteOnly = !remoteOnly }
                )
            }
            item {
                FilterChipPill(
                    label = if (verifiedOnly) "Verified ✓" else "Verified",
                    active = verifiedOnly,
                    onClick = { verifiedOnly = !verifiedOnly }
                )
            }
            item {
                FilterChipPill(
                    label = if (closingSoon) "Closing soon ✓" else "Closing soon",
                    active = closingSoon,
                    onClick = { closingSoon = !closingSoon }
                )
            }
        }

        // ==================== results bar + sort ====================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                buildString {
                    append(results.size)
                    append(" job")
                    if (results.size != 1) append("s")
                    append(" found")
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSortMenu = true }
                ) {
                    Text(
                        when (sortBy) {
                            "pay" -> "Highest pay"
                            "match" -> "Best match for me"
                            "oldest" -> "Oldest first"
                            else -> "Newest first"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    listOf(
                        "newest" to "Newest first",
                        "match" to "Best match for me",
                        "pay" to "Highest pay",
                        "oldest" to "Oldest first"
                    ).forEach { (key, label) ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                sortBy = key
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        // ==================== active filter tags ====================
        if (activeFilterCount > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                if (category != "All") {
                    ActiveTag(category) { category = "All" }
                }
                if (jobType != "Any type") {
                    ActiveTag(jobType) { jobType = "Any type" }
                }
                if (posted != "Any time") {
                    ActiveTag(posted) { posted = "Any time" }
                }
                if (minPay != null) {
                    ActiveTag("KSh $minPay+") { minPay = null }
                }
                if (remoteOnly) {
                    ActiveTag("Remote") { remoteOnly = false }
                }
                if (verifiedOnly) {
                    ActiveTag("Verified") { verifiedOnly = false }
                }
                if (closingSoon) {
                    ActiveTag("Closing soon") { closingSoon = false }
                }
            }
        }

        // ==================== results ====================
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(results, key = { _, j -> j.id }) { _, job ->
                JobCard(
                    job,
                    showLocation = true,
                    showMatch = true
                ) { onJobClick(job.id) }
            }

            // ==================== job alert prompt (only with a real search) ====================
            if (hasSearchableAlert) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Get notified for jobs like this",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "We'll alert you when a new job matching \"${query.trim()}\"${
                                        if (category != "All") " in $category" else ""
                                    } is posted.",
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Surface(
                                    onClick = {
                                        if (alertOn) {
                                            com.prc.app.data.JobAlertRepository.remove(query.trim(), category)
                                            alertOn = false
                                        } else {
                                            // Free-tier cap: 1 alert, Pro unlimited.
                                            val atCap = isProUser == false &&
                                                    com.prc.app.data.JobAlertRepository.alertCount() >= com.prc.app.data.ProGate.FREE_MAX_ALERTS
                                            if (atCap) {
                                                showAlertLimitDialog = true
                                            } else {
                                                com.prc.app.data.JobAlertRepository.add(query.trim(), category)
                                                alertOn = true
                                                android.widget.Toast.makeText(
                                                    context,
                                                    "Alert saved for \"${query.trim()}\" — we'll notify you about new matches",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (alertOn) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.tertiary
                                ) {
                                    Text(
                                        if (alertOn) "Alert on ✓ (tap to remove)" else "Create job alert",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alertOn) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (results.isEmpty()) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 40.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Nothing found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Try a different keyword or clear some filters.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ==================== filter bottom sheets ====================
    when (sheetFor) {
        "category" -> OptionSheet(JobRepository.categories, category) { category = it; sheetFor = null }
        "type" -> OptionSheet(
            listOf("Any type", "Full-time", "Part-time", "Gig", "Contract"), jobType
        ) { jobType = it; sheetFor = null }
        "posted" -> OptionSheet(
            listOf("Any time", "Last 24 hours", "Last 3 days"), posted
        ) { posted = it; sheetFor = null }
        "pay" -> PaySheet(minPay) { minPay = it; sheetFor = null }
    }
}

// ==================== pieces ====================

@Composable
private fun FilterChipPill(
    label: String,
    active: Boolean = false,
    primary: Boolean = false,
    leading: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            primary -> MaterialTheme.colorScheme.primary
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (!primary && !active) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant
        ) else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        ) {
            if (leading != null) {
                Text(leading, fontSize = 12.sp)
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    primary -> MaterialTheme.colorScheme.onPrimary
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = when {
                    primary -> MaterialTheme.colorScheme.onPrimary
                    active -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun ActiveTag(label: String, onRemove: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 11.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove $label filter",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    options: List<String>,
    current: String,
    onSelect: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { onSelect(current) }, sheetState = sheetState) {
        Text(
            "Choose an option",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        options.forEach { option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                RadioButton(selected = option == current, onClick = { onSelect(option) })
                Text(option, fontSize = 14.5.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySheet(current: Int?, onSelect: (Int?) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val options: List<Pair<String, Int?>> = listOf(
        "Any pay" to null,
        "KSh 1,000 and up" to 1000,
        "KSh 5,000 and up" to 5000,
        "KSh 10,000 and up" to 10000
    )
    ModalBottomSheet(onDismissRequest = { onSelect(current) }, sheetState = sheetState) {
        Text(
            "Minimum pay",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        options.forEach { (label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(value) }
                    .padding(horizontal = 20.dp, vertical = 2.dp)
            ) {
                RadioButton(selected = value == current, onClick = { onSelect(value) })
                Text(label, fontSize = 14.5.sp)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ==================== helpers ====================

private fun payNumber(pay: String): Int =
    Regex("KSh\\s*([0-9][0-9,]*)").find(pay)
        ?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull() ?: 0
