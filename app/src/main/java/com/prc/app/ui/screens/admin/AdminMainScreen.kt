package com.prc.app.ui.screens.admin

import com.prc.app.data.AuthRepository
import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AdminRepository
import com.prc.app.data.Application
import com.prc.app.data.Job
import com.prc.app.data.JobRepository

/**
 * Admin portal shell: [AdminTopBar] + bottom navigation + crossfading pages
 * (Overview / Post / Approvals / Jobs).
 */
@Composable
fun AdminMainScreen(
    onLogout: () -> Unit = {}
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    var currentTab by remember { mutableStateOf("overview") }
    var showPostJob by remember { mutableStateOf(false) }
    // Jump target: opening Approvals filtered to one provider job.
    var approvalsJobFilter by remember { mutableStateOf<Int?>(null) }

    val approvals by AdminRepository.pendingApprovals.collectAsState()
    val approvalsBadge = approvals.count { it.status == "Pending" }

    androidx.compose.material3.Scaffold(
        containerColor = AdminColors.Paper,
        topBar = {
            AdminTopBar(
                title = "PRC Admin",
                subtitle = "Operations console · provider jobs & approvals",
                trailing = {
                    Surface(
                        shape = CircleShape,
                        color = AdminColors.Hairline,
                        modifier = Modifier.clickable { onLogout() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Log out of portal",
                            tint = AdminColors.OnChrome,
                            modifier = Modifier
                                .padding(9.dp)
                                .size(16.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            AdminBottomBar(
                current = currentTab,
                onSelect = {
                    showPostJob = false
                    currentTab = it
                },
                approvalsBadge = approvalsBadge
            )
        },
        floatingActionButton = {
            if (currentTab == "jobs" && !showPostJob) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = { showPostJob = true },
                    containerColor = AdminColors.Green,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Filled.PostAdd,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Paste provider job", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Crossfade(
            targetState = currentTab to showPostJob,
            animationSpec = tween(220),
            label = "adminTabCrossfade"
        ) { (tab, posting) ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when {
                    posting || tab == "post" -> AdminPostJobScreen()
                    tab == "overview" -> OverviewPage(pendingApprovals = approvalsBadge)
                    tab == "approvals" -> ApprovalsPage(
                        initialJobFilter = approvalsJobFilter,
                        onClearJobFilter = { approvalsJobFilter = null }
                    )
                    else -> when (tab) {
                        "audit" -> AuditLogPage()
                        "payments" -> PaymentsPage()
                        else -> ProviderJobsPage(
                            onReviewApplicants = { jobId ->
                                approvalsJobFilter = jobId
                                currentTab = "approvals"
                            }
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// PAYMENTS — client payment features control (revenue, revoke, grant)
// =====================================================================

@Composable
private fun PaymentsPage() {
    val payments by AdminRepository.payments.collectAsState()
    val syncState by AdminRepository.paymentsSyncState.collectAsState()
    val adminEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email

    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var kindFilter by remember { mutableStateOf("All") }
    var detailFor by remember { mutableStateOf<AdminRepository.AdminPayment?>(null) }
    var showGrant by remember { mutableStateOf(false) }

    val stats = AdminRepository.paymentStats()
    val conversion by AdminRepository.conversionStats.collectAsState()

    val filtered = payments.filter { p ->
        (statusFilter == "All" || p.status == statusFilter) &&
                (kindFilter == "All" || p.kind == kindFilter) &&
                (query.isBlank() ||
                        p.reference.contains(query, ignoreCase = true) ||
                        p.email.contains(query, ignoreCase = true) ||
                        p.productId.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize()) {
        // ==================== KPI strip ====================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaymentKpi("KSh ${stats.totalRevenue}", "Total revenue", Modifier.weight(1f))
            PaymentKpi("KSh ${stats.todayRevenue}", "Today", Modifier.weight(1f))
            PaymentKpi("${stats.transactions}", "Paid", Modifier.weight(1f))
            PaymentKpi("${stats.pending}", "Pending", Modifier.weight(1f))
        }

        // ==================== kind breakdown ====================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PaymentKpi("⭐ ${stats.boosts}", "Boosts", Modifier.weight(1f))
            PaymentKpi("🔒 ${stats.unlocks}", "Unlocks", Modifier.weight(1f))
            PaymentKpi("🏅 ${stats.subscriptions}", "Pro plans", Modifier.weight(1f))
        }

        // ==================== conversion funnel ====================
        Surface(
            color = AdminColors.Card,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Free → Pro conversion",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkStrong
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${conversion.ratePercent}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (conversion.ratePercent > 0) AdminColors.Green else AdminColors.InkSoft
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentKpi("${conversion.signups}", "Signups", Modifier.weight(1f))
                    PaymentKpi("${conversion.choseProAtSignup}", "Pro at signup", Modifier.weight(1f))
                    PaymentKpi("${conversion.converted}", "Converted", Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // ==================== search ====================
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search reference, email or product", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // ==================== filters ====================
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "success", "initialized", "revoked").forEach { s ->
                FilterChip(
                    selected = statusFilter == s,
                    onClick = { statusFilter = s },
                    label = { Text(if (s == "All") "All statuses" else s.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "boost", "unlock", "subscription").forEach { k ->
                FilterChip(
                    selected = kindFilter == k,
                    onClick = { kindFilter = k },
                    label = { Text(if (k == "All") "All types" else k.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                )
            }
        }

        // ==================== loading / error banners ====================
        if (syncState.loading) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Connecting to payments feed…", fontSize = 12.sp)
            }
        }
        syncState.error?.let {
            Surface(
                color = Color(0xFFFDECEA),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text(
                        "Retry",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF7B2D26),
                        modifier = Modifier.clickable { AdminRepository.retryPaymentsSync() }
                    )
                }
            }
        }

        // ==================== transaction list ====================
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                androidx.compose.material3.TextButton(onClick = { showGrant = true }) {
                    Text("🎁 Grant entitlement manually", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text(
                        if (payments.isEmpty()) "No payments recorded yet"
                        else "No payments match the current filters",
                        fontSize = 13.sp,
                        color = AdminColors.InkSoft,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(filtered, key = { it.reference }) { p ->
                PaymentRow(p, onClick = { detailFor = p })
            }
        }
    }

    // ==================== detail sheet ====================
    detailFor?.let { p ->
        PaymentDetailSheet(
            payment = p,
            adminEmail = adminEmail,
            onDismiss = { detailFor = null }
        )
    }

    if (showGrant) {
        GrantEntitlementDialog(
            adminEmail = adminEmail,
            onDismiss = { showGrant = false }
        )
    }
}

@Composable
private fun PaymentKpi(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        color = AdminColors.Card,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
        modifier = modifier
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = AdminColors.InkStrong)
            Text(label, fontSize = 10.sp, color = AdminColors.InkSoft)
        }
    }
}

@Composable
private fun PaymentRow(p: AdminRepository.AdminPayment, onClick: () -> Unit) {
    Surface(
        color = AdminColors.Card,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.productId.ifBlank { p.kind },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AdminColors.InkStrong
                )
                Text(
                    p.email.ifBlank { p.uid.take(12) } + "  ·  " + p.reference.take(16),
                    fontSize = 11.sp,
                    color = AdminColors.InkSoft,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "KSh ${p.amountKes}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.InkStrong
                )
                Text(
                    when (p.status) {
                        "success" -> "Paid"
                        "revoked" -> "Revoked"
                        else -> "Pending"
                    },
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (p.status) {
                        "success" -> Color(0xFF1B5E20)
                        "revoked" -> Color(0xFFB71C1C)
                        else -> Color(0xFF9E6A00)
                    }
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDetailSheet(
    payment: AdminRepository.AdminPayment,
    adminEmail: String?,
    onDismiss: () -> Unit
) {
    var revoking by remember { mutableStateOf(false) }
    val fmt = remember { java.text.SimpleDateFormat("MMM d, yyyy · HH:mm", java.util.Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("Transaction", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AdminColors.InkStrong)
            Spacer(Modifier.height(12.dp))
            DetailLine("Reference", payment.reference)
            DetailLine("Product", payment.productId.ifBlank { payment.kind })
            DetailLine("Type", payment.kind)
            DetailLine("Customer", payment.email.ifBlank { payment.uid })
            DetailLine("Amount", "KSh ${payment.amountKes}")
            DetailLine("Status", payment.status)
            if (payment.jobId != null) DetailLine("Job", "#${payment.jobId}")
            if (payment.createdAt > 0) DetailLine("Created", fmt.format(java.util.Date(payment.createdAt)))
            if (payment.verifiedAt > 0) DetailLine("Verified", fmt.format(java.util.Date(payment.verifiedAt)))

            Spacer(Modifier.height(18.dp))
            if (payment.status == "success") {
                Button(
                    onClick = {
                        revoking = true
                        AdminRepository.revokePayment(payment, adminEmail ?: "admin") { ok ->
                            revoking = false
                            if (ok) onDismiss()
                        }
                    },
                    enabled = !revoking,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (revoking) "Revoking…" else "Revoke payment & entitlement", fontSize = 13.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "For boosts this removes the job's featured status immediately. The client is not automatically refunded — process refunds in the Paystack dashboard.",
                    fontSize = 11.sp,
                    color = AdminColors.InkSoft
                )
            }
        }
    }
}

@Composable
private fun GrantEntitlementDialog(adminEmail: String?, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf("boost") }
    var email by remember { mutableStateOf("") }
    var jobId by remember { mutableStateOf("") }
    var productId by remember { mutableStateOf("boost_3d") }
    var granting by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grant entitlement", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Manually grant a paid feature to a client (support / goodwill). Logged to the audit history.",
                    fontSize = 12.sp,
                    color = AdminColors.InkSoft
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("boost", "unlock", "subscription").forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                kind = k
                                productId = when (k) {
                                    "boost" -> "boost_3d"
                                    "unlock" -> "app_unlock"
                                    else -> "pro_monthly"
                                }
                            },
                            label = { Text(k.replaceFirstChar { it.uppercase() }, fontSize = 11.sp) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Client email (account)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (kind == "boost") {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jobId,
                        onValueChange = { jobId = it.filter { c -> c.isDigit() } },
                        label = { Text("Job id to feature") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = productId == "boost_3d", onClick = { productId = "boost_3d" }, label = { Text("3 days", fontSize = 11.sp) })
                        FilterChip(selected = productId == "boost_7d", onClick = { productId = "boost_7d" }, label = { Text("7 days", fontSize = 11.sp) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !granting && email.isNotBlank() && (kind != "boost" || jobId.isNotBlank()),
                onClick = {
                    granting = true
                    // Resolve the uid from the email via the users collection.
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users")
                        .whereEqualTo("contact", email.trim())
                        .limit(1)
                        .get()
                        .addOnSuccessListener { snap ->
                            val uid = snap.documents.firstOrNull()?.id
                            if (uid == null) {
                                granting = false
                                return@addOnSuccessListener
                            }
                            AdminRepository.grantEntitlement(
                                kind = kind,
                                uid = uid,
                                userEmail = email.trim(),
                                jobId = jobId.toIntOrNull(),
                                productId = productId,
                                adminEmail = adminEmail ?: "admin"
                            ) { ok ->
                                granting = false
                                if (ok) onDismiss()
                            }
                        }
                        .addOnFailureListener { granting = false }
                }
            ) { Text(if (granting) "Granting…" else "Grant") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// =====================================================================
// AUDIT LOG — full-screen, searchable, filterable activity history
// =====================================================================

private val AuditEventTypes = listOf(
    "All" to "All events",
    "✅" to "Approvals",
    "🚫" to "Rejections",
    "⭐" to "Shortlisted",
    "🚀" to "Job posts",
    "🙋" to "Applications"
)

@Composable
private fun AuditLogPage() {
    val activity by AdminRepository.activity.collectAsState()
    val syncState by AdminRepository.activitySyncState.collectAsState()
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("All") }
    var adminFilter by remember { mutableStateOf<String?>(null) }

    val admins = activity.map { it.admin }.filter { it.isNotBlank() }.distinct().sorted()

    fun eventTypeMatches(icon: String): Boolean = when (typeFilter) {
        "All" -> true
        "✅" -> icon == "✅"
        "🚫" -> icon == "🚫"
        "⭐" -> icon == "⭐"
        "🚀" -> icon == "🚀"
        "🙋" -> icon == "🙋"
        else -> true
    }

    val visible = activity.filter { entry ->
        val typeOk = eventTypeMatches(entry.icon)
        val adminOk = adminFilter == null || entry.admin == adminFilter
        val q = query.trim().lowercase()
        val queryOk = q.isEmpty() || entry.message.lowercase().contains(q) ||
                entry.admin.lowercase().contains(q)
        typeOk && adminOk && queryOk
    }

    Column(Modifier.fillMaxSize()) {

        // ============ connection banner ============
        when {
            syncState.loading -> Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.Card,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AdminColors.Violet
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Loading audit history…", fontSize = 11.5.sp, color = AdminColors.InkSoft)
                }
            }
            syncState.error != null -> Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.RedSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    syncState.error ?: "",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = AdminColors.Red,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // ============ search ============
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search actions and admins", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = aiFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = AdminColors.InkStrong),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // ============ event-type chips ============
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            items(AuditEventTypes, key = { it.first }) { (icon, label) ->
                val active = typeFilter == icon
                Surface(
                    shape = CircleShape,
                    color = if (active) AdminColors.Violet else AdminColors.Card,
                    border = if (active) null
                    else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                    modifier = Modifier.clickable { typeFilter = icon }
                ) {
                    Text(
                        "$icon  $label".removePrefix("All  "),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else AdminColors.InkSoft,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // ============ admin filter chips ============
        if (admins.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                item {
                    Surface(
                        shape = CircleShape,
                        color = if (adminFilter == null) AdminColors.Green else AdminColors.Card,
                        border = if (adminFilter == null) null
                        else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                        modifier = Modifier.clickable { adminFilter = null }
                    ) {
                        Text(
                            "All admins",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (adminFilter == null) Color.White else AdminColors.InkSoft,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                items(admins, key = { it }) { admin ->
                    val active = adminFilter == admin
                    Surface(
                        shape = CircleShape,
                        color = if (active) AdminColors.Green else AdminColors.Card,
                        border = if (active) null
                        else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                        modifier = Modifier.clickable { adminFilter = if (active) null else admin }
                    ) {
                        Text(
                            admin.substringBefore("@"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else AdminColors.InkSoft,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ============ entries ============
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (visible.isEmpty()) {
                AdminEmptyState(
                    "📜",
                    "No matching activity",
                    if (activity.isEmpty())
                        "Admin actions will be recorded here as they happen."
                    else
                        "Try a different search, event type, or admin filter."
                )
            }
            visible.forEachIndexed { i, entry ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AdminColors.Card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp)
                    ) {
                        Text(entry.icon, fontSize = 15.sp)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.message,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = AdminColors.InkStrong
                            )
                            Text(
                                buildString {
                                    append(timeAgo(entry.at))
                                    if (entry.admin.isNotBlank()) {
                                        append("  ·  ")
                                        append(entry.admin.substringBefore("@"))
                                    }
                                },
                                fontSize = 10.sp,
                                color = AdminColors.InkSoft.copy(alpha = 0.85f),
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                        Text(
                            java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                                .format(java.util.Date(entry.at)),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AdminColors.InkSoft
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// =====================================================================
// OVERVIEW
// =====================================================================

@Composable
private fun OverviewPage(pendingApprovals: Int) {
    val activity by AdminRepository.activity.collectAsState()
    val approvals by AdminRepository.pendingApprovals.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()
    val liveUserCount by AdminRepository.userCount.collectAsState()
    val stats = AdminRepository.stats().copy(users = liveUserCount)
    // Recompute analytics whenever jobs or approvals change.
    val trend = remember(jobs) { AdminRepository.postingTrend() }
    val categoryMix = remember(jobs) { AdminRepository.categoryMix() }
    val typeMix = remember(jobs) { AdminRepository.jobTypeMix() }
    val providerRows = remember(jobs) { AdminRepository.providerRollup() }
    val approvalRate = remember(approvals) { AdminRepository.approvalRate() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // hero band
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(AdminColors.Card, AdminColors.Paper))
                )
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(
                    "${stats.jobs}",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.InkStrong,
                    lineHeight = 42.sp
                )
                Text(
                    "live jobs on PRC right now",
                    fontSize = 12.sp,
                    color = AdminColors.InkSoft
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HeroChip(
                        "${stats.providerJobs}",
                        "from providers",
                        tint = AdminColors.Green
                    )
                    HeroChip(
                        "${stats.applications}",
                        "applications",
                        tint = AdminColors.Gold
                    )
                    HeroChip("${stats.users}", "users", tint = AdminColors.InkSoft)
                }
            }
        }

        // needs attention
        AdminSectionTitle("Needs attention")
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AttentionCard(
                icon = Icons.Filled.PostAdd,
                iconTint = AdminColors.Green,
                container = AdminColors.GreenSoft,
                title = "Jobs posted for providers",
                subtitle = "${stats.postedToday} today · ${stats.adminPosts} all time",
                badge = null
            )
            AttentionCard(
                icon = Icons.Filled.HowToReg,
                iconTint = AdminColors.Gold,
                container = AdminColors.GoldSoft,
                title = "Application approvals",
                subtitle = if (pendingApprovals > 0)
                    "$pendingApprovals awaiting your decision" else "Queue is clear",
                badge = pendingApprovals.takeIf { it > 0 }
            )
        }

        // at a glance
        AdminSectionTitle("At a glance")
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${stats.postedToday}", "jobs posted\ntoday", Modifier.weight(1f))
                StatTile("${stats.providerJobs}", "provider jobs\nlive now", Modifier.weight(1f))
                StatTile("${stats.jobs - stats.providerJobs}", "community\njobs", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("${stats.adminPosts}", "all provider\nposts", Modifier.weight(1f))
                StatTile("${stats.applications}", "applications\nreceived", Modifier.weight(1f))
                StatTile("${approvals.count { it.status == "Pending" }}", "pending\napprovals", Modifier.weight(1f))
            }
        }

        // KPI cards — bigger numbers with delta hints
        AdminSectionTitle("Key metrics")
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    value = "${stats.users}",
                    label = "Platform users",
                    delta = "registered accounts",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    value = "${stats.applications}",
                    label = "Applications",
                    delta = if (approvals.count { it.status == "Pending" } > 0)
                        "${approvals.count { it.status == "Pending" }} awaiting decision"
                    else "all decided",
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                    value = "${providerRows.sumOf { it.live + it.closed }}",
                    label = "Jobs from providers",
                    delta = "${providerRows.size} providers on board",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    value = "${stats.jobs}",
                    label = "Live jobs",
                    delta = "${trend.sumOf { it.community + it.provider }} posted this week",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // posting trend graph
        AdminSectionTitle("Posting trend", trailingText = "last 7 days")
        AdminWidgetCard(
            title = "New posts per day",
            icon = Icons.Filled.Insights,
            trailing = "community vs provider",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AdminAreaChart(
                values = trend.map { it.community to it.provider },
                labels = trend.map { it.label }
            )
        }

        // category mix
        AdminSectionTitle("Top categories")
        AdminWidgetCard(
            title = "Live jobs by category",
            icon = Icons.Filled.Category,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AdminBarChart(data = categoryMix.take(6))
        }

        // composition row: donut + gauge
        AdminSectionTitle("Composition")
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AdminWidgetCard(
                title = "Job types",
                icon = Icons.Filled.DonutLarge,
                modifier = Modifier.weight(1.35f)
            ) {
                AdminDonut(
                    slices = typeMix.take(6).map { DonutSlice(it.label, it.count) },
                    centerLabel = "live jobs"
                )
            }
            AdminWidgetCard(
                title = "Approvals",
                icon = Icons.Filled.FactCheck,
                modifier = Modifier.weight(1f)
            ) {
                AdminMiniGauge(
                    percent = approvalRate.label.takeIf { it != "—" }?.removeSuffix("%").orEmpty().toIntOrNull(),
                    pendingCount = approvalRate.count
                )
            }
        }

        // provider performance table
        AdminSectionTitle("Provider performance", trailingText = "by latest activity")
        AdminWidgetCard(
            title = "Provider jobs on board",
            icon = Icons.Filled.TableChart,
            trailing = "${providerRows.size} providers",
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AdminMiniTable(
                headers = listOf("Provider", "Live", "Closed"),
                rows = providerRows.take(5).map {
                    listOf(it.providerName, "${it.live}", "${it.closed}")
                }
            )
        }

        // recent activity
        AdminSectionTitle("Recent activity", trailingText = "latest 6")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AdminColors.Card,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                activity.take(6).forEachIndexed { i, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Text(item.icon, fontSize = 14.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            item.message,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = AdminColors.InkStrong,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            timeAgo(item.at),
                            fontSize = 10.sp,
                            color = AdminColors.InkSoft.copy(alpha = 0.8f)
                        )
                    }
                    if (i < minOf(5, activity.lastIndex)) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 40.dp)
                                .height(1.dp)
                                .background(AdminColors.Hairline)
                        )
                    }
                }
                if (activity.isEmpty()) {
                    Text(
                        "Nothing yet — actions you take will appear here.",
                        fontSize = 12.sp,
                        color = AdminColors.InkSoft,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun KpiCard(
    value: String,
    label: String,
    delta: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AdminColors.Card,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                value,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = AdminColors.InkStrong,
                lineHeight = 28.sp
            )
            Text(
                label,
                fontSize = 11.sp,
                color = AdminColors.InkSoft,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(5.dp)
                        .background(AdminColors.Mint, CircleShape)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    delta,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AdminColors.Green,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HeroChip(value: String, label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = AdminColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = AdminColors.InkSoft)
        }
    }
}

@Composable
private fun AttentionCard(
    icon: ImageVector,
    iconTint: Color,
    container: Color,
    title: String,
    subtitle: String,
    badge: Int?
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = container,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Surface(shape = CircleShape, color = AdminColors.Paper) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.InkStrong
                )
                Text(
                    subtitle,
                    fontSize = 10.5.sp,
                    color = AdminColors.InkSoft,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            if (badge != null) {
                Text(
                    "$badge",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(AdminColors.Green, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = AdminColors.InkSoft,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AdminColors.Card,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(
                value,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = AdminColors.Green
            )
            Text(
                label,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                color = AdminColors.InkSoft,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

// =====================================================================
// CLIENT-SIDE JOB PREVIEW — what users see in the public feed
// =====================================================================

/**
 * Full-screen overlay replicating the user-facing job details screen
 * (hero, info strip, about, requirements, skills, salary, provider card)
 * rendered inside the admin portal so the admin can inspect the listing
 * exactly as job seekers see it before/after publishing.
 */
@Composable
private fun AdminJobPreviewOverlay(job: Job, onClose: () -> Unit) {
    val applications by JobRepository.applications.collectAsState()
    val applicantCount = applications.count { it.job.id == job.id }

    Surface(
        color = AdminColors.Paper,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(Modifier.fillMaxSize()) {
            // ==================== preview chrome bar ====================
            Surface(color = AdminColors.Chrome) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPaddingCompat()
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AdminColors.Hairline,
                        modifier = Modifier.clickable { onClose() }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to jobs",
                            tint = AdminColors.OnChrome,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Client view",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminColors.OnChrome
                        )
                        Text(
                            "Exactly what job seekers see",
                            fontSize = 9.5.sp,
                            color = AdminColors.OnChromeSoft
                        )
                    }
                    AdminStatusChip(if (job.closed) "Closed" else "Live")
                }
            }

            // ==================== scrollable client replica ====================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // ---- hero (green, like the user-side job details) ----
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(AdminColors.GreenSoft)
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 22.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(13.dp),
                                color = AdminColors.Card,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        job.postedBy.take(2).uppercase(),
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = AdminColors.Green
                                    )
                                }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column {
                                Text(
                                    job.title,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 23.sp,
                                    color = AdminColors.InkStrong
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        job.postedBy,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = AdminColors.InkSoft
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    // verified badge like the client screen
                                    Surface(shape = CircleShape, color = AdminColors.Gold) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Verified poster",
                                            tint = Color.White,
                                            modifier = Modifier.padding(3.dp).size(9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- floating info strip ----
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .offset(y = (-14).dp)
                ) {
                    Row(Modifier.padding(vertical = 11.dp)) {
                        PreviewInfoCell(job.pay.substringBefore("/").trim(), "Pay", Modifier.weight(1.3f))
                        PreviewVDivider()
                        PreviewInfoCell(job.type, "Job type", Modifier.weight(1f))
                        PreviewVDivider()
                        PreviewInfoCell(job.postedAgo, "Posted", Modifier.weight(1f))
                        PreviewVDivider()
                        PreviewInfoCell("$applicantCount", "Applicants", Modifier.weight(1f))
                    }
                }

                // ---- about ----
                PreviewSection("About the role") {
                    Text(
                        job.description,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = AdminColors.InkSoft
                    )
                }

                // ---- responsibilities ----
                if (job.responsibilities.isNotEmpty()) {
                    PreviewSection("Responsibilities") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            job.responsibilities.forEach { resp ->
                                PreviewBullet(resp)
                            }
                        }
                    }
                }

                // ---- requirements ----
                PreviewSection("Requirements") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        job.requirements.forEach { req ->
                            PreviewBullet(req)
                        }
                    }
                }

                // ---- skills ----
                PreviewSection("Skills") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        job.skills.ifEmpty { listOf("Reliability", "Communication", "Teamwork") }
                            .chunked(3)
                            .forEach { rowSkills ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowSkills.forEach { s ->
                                        Surface(shape = CircleShape, color = Color(0xFFE4F0EC)) {
                                            Text(
                                                s,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF0F5C4E),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                    }
                }

                // ---- salary card ----
                PreviewSection("Salary") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminColors.Card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(15.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    job.pay,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AdminColors.InkStrong
                                )
                                Text(
                                    "as posted by ${job.postedBy}",
                                    fontSize = 11.5.sp,
                                    color = AdminColors.InkSoft
                                )
                            }
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFBF3E0)) {
                                Text(
                                    job.category,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminColors.Gold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // ---- about the poster ----
                PreviewSection("About the poster") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminColors.Card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline)
                    ) {
                        Row(Modifier.padding(15.dp)) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = Color(0xFF0F5C4E),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        job.postedBy.take(2).uppercase(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    job.postedBy,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminColors.InkStrong
                                )
                                Text(
                                    "${job.category} · ${job.location}",
                                    fontSize = 11.sp,
                                    color = AdminColors.InkSoft,
                                    modifier = Modifier.padding(vertical = 3.dp)
                                )
                                Text(
                                    "Responds through the app",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F5C4E)
                                )
                            }
                        }
                    }
                }

                // ---- openings / deadline meta ----
                PreviewSection("Listing details") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminColors.Card,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            DetailLine("Openings", "${job.openings} position${if (job.openings == 1) "" else "s"}")
                            if (job.experience.isNotBlank()) DetailLine("Experience", job.experience)
                            if (job.deadlineDays != null) DetailLine("Deadline", "${job.deadlineDays} days from posting")
                            DetailLine("Status", if (job.closed) "Closed to new applications" else "Accepting applications")
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            // ==================== bottom note (replaces client apply bar) ====================
            Surface(color = AdminColors.Card) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPaddingCompat()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (job.closed)
                            "This listing is closed — job seekers can no longer see an Apply button."
                        else
                            "Job seekers see an Apply now button here · ${applicantCount} have applied so far",
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp,
                        color = AdminColors.InkSoft,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AdminColors.Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClose() }
                            .height(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Back to console",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewInfoCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            color = AdminColors.InkStrong,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            label,
            fontSize = 9.5.sp,
            color = AdminColors.InkSoft
        )
    }
}

@Composable
private fun PreviewVDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(30.dp)
            .background(AdminColors.Hairline)
    )
}

@Composable
private fun PreviewSection(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp)
    ) {
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = AdminColors.InkStrong
        )
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun PreviewBullet(text: String) {
    Row {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(5.dp)
                .background(AdminColors.Green, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = AdminColors.InkSoft
        )
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat() = this.then(
    Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
)

@Composable
private fun Modifier.navigationBarsPaddingCompat() = this.then(
    Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
)

// =====================================================================
// APPROVALS — queue, review and decide on provider-job applications
// =====================================================================

// AI-violet accents for shortlist selection (mirrors AdminPostJobScreen)

/** Violet-focused text field colors shared by the approvals search and note dialog. */
@Composable
private fun aiFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AdminColors.Violet,
        unfocusedBorderColor = AdminColors.Hairline,
        cursorColor = AdminColors.Violet
    )

private val ApprovalFilters = listOf("Pending", "Shortlisted", "Accepted", "Rejected")

@Composable
private fun ApprovalsPage(
    initialJobFilter: Int? = null,
    onClearJobFilter: () -> Unit = {}
) {
    val approvals by AdminRepository.pendingApprovals.collectAsState()
    val syncState by AdminRepository.approvalsSyncState.collectAsState()
    var filter by remember { mutableStateOf("Pending") }
    var jobFilter by remember(initialJobFilter) { mutableStateOf(initialJobFilter) }
    var query by remember { mutableStateOf("") }
    var selectionMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }   // row keys: contact#jobId
    var confirmBulk by remember { mutableStateOf<Boolean?>(null) }  // true=approve, false=reject
    var noteTarget by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var previewJobId by remember { mutableStateOf<Int?>(null) }

    // pending count lives outside the text filter so the header stays truthful
    // Firestore is source of truth: dedupe by (contact+jobId) defensively and
    // drop rows whose job vanished (deleted provider job) so counts stay truthful.
    val queue = approvals
        .distinctBy { it.applicantContact to it.job.id }
        .filter { JobRepository.job(it.job.id) != null || it.status != "Pending" }
    val pendingCount = queue.count { it.status == "Pending" }
    val base = queue
        .filter { jobFilter == null || it.job.id == jobFilter }
        .filter {
            query.isBlank() ||
                    it.applicantName.lowercase().contains(query.trim().lowercase()) ||
                    it.job.title.lowercase().contains(query.trim().lowercase()) ||
                    it.job.postedBy.lowercase().contains(query.trim().lowercase())
    }
    val visible = base.filter { it.status == filter }
    val decided = queue.size - pendingCount
    val approvedCount = queue.count { it.status == "Accepted" }
    val rejectedCount = queue.count { it.status == "Rejected" }
    val approvalPct =
        if (approvedCount + rejectedCount == 0) 0
        else approvedCount * 100 / (approvedCount + rejectedCount)

    Column(Modifier.fillMaxSize()) {

        // ============ connection state banner ============
        when {
            syncState.loading -> Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.Card,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = AdminColors.Violet
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Connecting to live queue…",
                        fontSize = 11.5.sp,
                        color = AdminColors.InkSoft
                    )
                }
            }
            syncState.error != null -> Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.RedSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        syncState.error ?: "",
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = AdminColors.Red,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AdminColors.Red,
                        modifier = Modifier.clickable { AdminRepository.retryApprovalsSync() }
                    ) {
                        Text(
                            "Retry",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ============ stats strip ============
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp)
        ) {
            QueueStat("$pendingCount", "pending", AdminColors.Gold, Modifier.weight(1f))
            QueueStat("${approvals.count { it.status == "Shortlisted" }}", "shortlisted", AdminColors.Violet, Modifier.weight(1f))
            QueueStat("$decided", "decided", AdminColors.Green, Modifier.weight(1f))
            QueueStat(if (decided == 0) "—" else "$approvalPct%", "approved", AdminColors.InkSoft, Modifier.weight(1f))
        }

        // ============ posted-jobs management strip ============
        ProviderJobsQuickStrip()
        Spacer(Modifier.height(12.dp))

        // ============ active job-filter banner ============
        if (jobFilter != null) {
            val job = JobRepository.job(jobFilter!!)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.VioletTint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Showing queue for: ${job?.title ?: "job #$jobFilter"}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.Violet,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "Show all ×",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkSoft,
                        modifier = Modifier
                            .clickable {
                                jobFilter = null
                                onClearJobFilter()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ============ search + bulk ============
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search applicant, job, provider", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = aiFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.5.sp,
                    color = AdminColors.InkStrong
                ),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (selectionMode) AdminColors.Green else AdminColors.Card,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selectionMode) AdminColors.Green else AdminColors.Hairline
                ),
                modifier = Modifier.clickable {
                    selectionMode = !selectionMode
                    selected = emptySet()
                }
            ) {
                Text(
                    if (selectionMode) "Done" else "Select",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectionMode) Color.White else AdminColors.InkSoft,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }

        // ============ bulk action bar ============
        androidx.compose.animation.AnimatedVisibility(visible = selectionMode && selected.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                Text(
                    "${selected.size} selected",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AdminColors.InkSoft,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.Button(
                    onClick = { confirmBulk = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = AdminColors.Green
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Approve all", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { confirmBulk = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = AdminColors.Red
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Reject all", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ============ filter chips ============
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 12.dp)
        ) {
            ApprovalFilters.forEach { f ->
                val active = filter == f
                Surface(
                    shape = CircleShape,
                    color = if (active) AdminColors.Gold else AdminColors.Card,
                    modifier = Modifier.clickable { filter = f }
                ) {
                    Text(
                        "$f  ${queue.count { it.status == f }}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else AdminColors.InkSoft,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ============ queue list ============
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            if (visible.isEmpty()) {
                AdminEmptyState(
                    if (filter == "Pending") "🤝" else "🧾",
                    if (filter == "Pending") "Queue is clear" else "Nothing recorded",
                    if (query.isBlank())
                        "Applications users submit to provider jobs will queue here for your approval."
                    else
                        "No matches for \"$query\" in this tab."
                )
            }
            visible.forEach { app ->
                val rowKey = "${app.applicantContact}#${app.job.id}"
                key(rowKey) {
                    ApprovalRow(
                        app = app,
                        selectionMode = selectionMode,
                        isSelected = rowKey in selected,
                        onToggleSelect = {
                            selected = if (rowKey in selected)
                                selected - rowKey else selected + rowKey
                        },
                        isExpanded = expanded == rowKey,
                        onToggleExpand = {
                            expanded = if (expanded == rowKey) null else rowKey
                        },
                        onDecide = { approved -> AdminRepository.decideApproval(app.applicantContact, approved) },
                        onShortlist = { AdminRepository.shortlistApproval(app.applicantContact) },
                        onNote = { noteTarget = app.applicantContact },
                        onPreview = { previewJobId = app.job.id }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    // ============ client-side job preview overlay ============
    previewJobId?.let { id ->
        JobRepository.job(id)?.let { job ->
            AdminJobPreviewOverlay(job = job, onClose = { previewJobId = null })
        }
    }

    // ============ dialogs ============
    confirmBulk?.let { approve ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmBulk = null },
            title = {
                Text(
                    if (approve) "Approve ${selected.size}?" else "Reject ${selected.size}?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (approve)
                        "All selected pending applicants will be approved and forwarded to the providers."
                    else
                        "All selected pending applicants will be rejected. This can be undone individually."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    selected.forEach { AdminRepository.decideApproval(it, approve) }
                    selected = emptySet()
                    selectionMode = false
                    confirmBulk = null
                }) {
                    Text("Confirm", fontWeight = FontWeight.Bold, color = AdminColors.Green)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmBulk = null }) {
                    Text("Cancel", color = AdminColors.InkSoft)
                }
            }
        )
    }

    noteTarget?.let { contact ->
        val app = queue.find { it.applicantContact == contact }
        var note by remember(contact) { mutableStateOf(app?.privateNote ?: "") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { noteTarget = null },
            title = { Text("Private note", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Visible only in this portal — attached to ${app?.applicantName ?: "applicant"}.",
                        fontSize = 11.5.sp,
                        color = AdminColors.InkSoft
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        placeholder = { Text("e.g. verified referees, call Tuesday…", fontSize = 12.sp) },
                        minLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = aiFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    AdminRepository.saveApprovalNote(contact, note)
                    noteTarget = null
                }) {
                    Text("Save", fontWeight = FontWeight.Bold, color = AdminColors.Green)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { noteTarget = null }) {
                    Text("Cancel", color = AdminColors.InkSoft)
                }
            }
        )
    }
}

// ---------------------------------------------------------------------

@Composable
private fun QueueStat(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AdminColors.Card,
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent, lineHeight = 21.sp)
            Text(
                label,
                fontSize = 10.sp,
                color = AdminColors.InkSoft,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

/** 'Expires in Xd' badge for provider jobs; red when <=3 days, amber otherwise. */
@Composable
private fun ExpiryBadge(job: com.prc.app.data.Job) {
    val deadlineDays = job.deadlineDays ?: return
    val remaining = ((job.postedAtMillis + deadlineDays * 86_400_000L - System.currentTimeMillis())
        / 86_400_000L).toInt()
    if (job.closed) return
    val label = if (remaining <= 0) "Expired" else "Expires in ${remaining}d"
    val urgent = remaining <= 3
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (urgent) AdminColors.Red.copy(alpha = 0.12f) else AdminColors.Gold.copy(alpha = 0.14f),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Text(
            label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (urgent) AdminColors.Red else AdminColors.Gold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun timeAgo(millis: Long): String {
    val mins = (System.currentTimeMillis() - millis) / 60000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 1440 -> "${mins / 60}h ago"
        else -> "${mins / 1440}d ago"
    }
}

@Composable
private fun ApprovalRow(
    app: Application,
    selectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onDecide: (Boolean) -> Unit,
    onShortlist: () -> Unit,
    onNote: () -> Unit,
    onPreview: () -> Unit
) {
    val decisional = app.status == "Pending" || app.status == "Shortlisted"
    Surface(
        shape = RoundedCornerShape(14.dp),                        color = if (isSelected) AdminColors.VioletTint else AdminColors.Card,
        border = if (isSelected)
            androidx.compose.foundation.BorderStroke(1.5.dp, AdminColors.Violet)
        else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode && decisional) {
                    // checkbox slot
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) AdminColors.Violet else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp,
                            if (isSelected) AdminColors.Violet else AdminColors.Hairline
                        ),
                        modifier = Modifier.clickable { onToggleSelect() }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(11.dp))
                }
                Surface(shape = CircleShape, color = AdminColors.GreenSoft) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Text(
                            app.applicantName.take(2).uppercase(),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminColors.Green
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        app.applicantName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkStrong
                    )
                    Text(
                        "${app.job.title} · ${app.job.postedBy}",
                        fontSize = 11.sp,
                        color = AdminColors.InkSoft,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "${timeAgo(app.appliedAt)}${if (app.privateNote.isNotBlank()) "  ·  📝 note" else ""}",
                        fontSize = 10.sp,
                        color = AdminColors.InkSoft.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                AdminStatusChip(app.status)
            }

            // expandable detail
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminColors.Paper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            DetailLine("Contact", app.applicantContact)
                            DetailLine("Pay offered", app.job.pay)
                            DetailLine("Location", app.job.location)
                            DetailLine("Type", "${app.job.type} · ${app.job.category}")
                            if (app.privateNote.isNotBlank()) {
                                DetailLine("Private note", app.privateNote)
                            }
                        }
                    }
                    if (!app.coverNote.isNullOrBlank()) {
                        Text(
                            "\u201C${app.coverNote}\u201D",
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = AdminColors.InkSoft,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(top = 9.dp)
                        )
                    }

                    // screening answers (what the applicant actually submitted)
                    if (app.answers.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AdminColors.Card,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    "SCREENING ANSWERS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = AdminColors.InkSoft
                                )
                                app.answers.forEach { (question, answer) ->
                                    Column(Modifier.padding(top = 8.dp)) {
                                        Text(
                                            question,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AdminColors.InkStrong
                                        )
                                        Text(
                                            answer,
                                            fontSize = 11.5.sp,
                                            lineHeight = 16.sp,
                                            color = AdminColors.InkSoft,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // CV attachment (this application's CV first, then profile)
                    val profile = AuthRepository.publicProfileOf(app.applicantContact)
                    val cvName = app.cvName.takeIf { it.isNotBlank() }
                        ?: profile?.cvName?.takeIf { it.isNotBlank() }
                    if (cvName != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint = AdminColors.Violet,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                cvName,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AdminColors.InkStrong,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(8.dp))
                            if (app.cvUrl.isNotBlank()) {
                                Text(
                                    "openable",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AdminColors.Green
                                )
                            }
                        }
                    }
                }
            }
            if (!isExpanded && !app.coverNote.isNullOrBlank()) {
                Text(
                    "\u201C${app.coverNote}\u201D",
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = AdminColors.InkSoft,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 11.dp)
            ) {
                Text(
                    if (isExpanded) "Hide details" else "Details",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Violet,
                    modifier = Modifier
                        .clickable { onToggleExpand() }
                        .padding(vertical = 6.dp)
                )
                Text(
                    "View job",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Gold,
                    modifier = Modifier
                        .clickable { onPreview() }
                        .padding(vertical = 6.dp)
                )
                if (decisional && !app.privateNote.startsWith("verified")) {
                    Text(
                        if (app.privateNote.isBlank()) "Add note" else "Edit note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkSoft,
                        modifier = Modifier
                            .clickable { onNote() }
                            .padding(vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (app.status == "Pending") {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = AdminColors.VioletTint,
                        modifier = Modifier.clickable { onShortlist() }
                    ) {
                        Text(
                            "★ Shortlist",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminColors.Violet,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)
                        )
                    }
                }
                if (decisional) {
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = AdminColors.Green,
                        modifier = Modifier.clickable { onDecide(true) }
                    ) {
                        Text(
                            "Approve",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(9.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Red),
                        modifier = Modifier.clickable { onDecide(false) }
                    ) {
                        Text(
                            "Reject",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminColors.Red,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            label.uppercase(),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = AdminColors.InkSoft,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = AdminColors.InkStrong,
            modifier = Modifier.weight(1f)
        )
    }
}

// =====================================================================
// POSTED-JOBS MANAGEMENT STRIP (shown above the approvals queue)
// =====================================================================

/**
 * Collapsible strip listing live provider jobs with quick close/reopen —
 * the admin can manage postings without leaving the approvals page.
 */
@Composable
private fun ProviderJobsQuickStrip() {
    val jobs by JobRepository.jobs.collectAsState()
    var open by remember { mutableStateOf(false) }
    val providerJobs = jobs.filter { it.isProviderJob }
    val liveCount = providerJobs.count { !it.closed && !it.draft }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AdminColors.Card,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { open = !open }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Icon(
                    Icons.Filled.PostAdd,
                    contentDescription = null,
                    tint = AdminColors.Green,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Posted provider jobs",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkStrong
                    )
                    Text(
                        "$liveCount live of ${providerJobs.size} total — tap to manage",
                        fontSize = 10.5.sp,
                        color = AdminColors.InkSoft
                    )
                }
                Text(
                    if (open) "▲" else "▼",
                    fontSize = 10.sp,
                    color = AdminColors.InkSoft
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = open) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    providerJobs.forEach { job ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    job.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AdminColors.InkStrong,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    "${job.postedBy} · ${timeAgo(job.postedAtMillis)}",
                                    fontSize = 10.sp,
                                    color = AdminColors.InkSoft
                                )
                            }
                            ExpiryBadge(job)
                            Spacer(Modifier.width(9.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AdminColors.Paper,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                                modifier = Modifier.clickable {
                                    if (job.closed) JobRepository.reopenJob(job.id) else JobRepository.closeJob(job.id)
                                }
                            ) {
                                Text(
                                    if (job.closed) "Reopen" else "Close",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (job.closed) AdminColors.Green else AdminColors.Red,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                        Box(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(AdminColors.Hairline)
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// PROVIDER JOBS — manage every provider posting from one console
// =====================================================================

private val JobFilters = listOf("Live", "Closed", "All")
private val JobSorts = listOf("Newest", "Oldest", "Provider")

@Composable
private fun ProviderJobsPage(onReviewApplicants: (Int) -> Unit = {}) {
    val jobs by JobRepository.jobs.collectAsState()
    val allApplications by JobRepository.applications.collectAsState()
    var filter by remember { mutableStateOf("Live") }
    var sort by remember { mutableStateOf("Newest") }
    var query by remember { mutableStateOf("") }
    var providerFilter by remember { mutableStateOf<String?>(null) }   // per-provider filtering
    var expandedId by remember { mutableStateOf<Int?>(null) }
    var confirmClose by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<Int?>(null) }
    var editJobId by remember { mutableStateOf<Int?>(null) }
    var justClosed by remember { mutableStateOf<String?>(null) }
    var previewJobId by remember { mutableStateOf<Int?>(null) }

    val allProvider = jobs.filter { it.isProviderJob }
    val providers = allProvider.map { it.postedBy }.distinct().sorted()

    fun matches(job: Job): Boolean {
        val q = query.trim().lowercase()
        val statusOk = when (filter) {
            "Live" -> !job.closed && !job.draft
            "Closed" -> job.closed
            else -> true
        }
        val providerOk = providerFilter == null || job.postedBy == providerFilter
        val queryOk = q.isEmpty() ||
                job.title.lowercase().contains(q) ||
                job.postedBy.lowercase().contains(q) ||
                job.location.lowercase().contains(q) ||
                job.category.lowercase().contains(q)
        return statusOk && providerOk && queryOk
    }

    val visible = allProvider
        .filter { matches(it) }
        .let { list ->
            when (sort) {
                "Oldest" -> list.sortedBy { it.postedAtMillis }
                "Provider" -> list.sortedBy { it.postedBy.lowercase() }
                else -> list.sortedByDescending { it.postedAtMillis }
            }
        }

    val liveCount = allProvider.count { !it.closed && !it.draft }
    val closedCount = allProvider.count { it.closed }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

        // ============ stats strip ============
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp)
        ) {
            QueueStat("$liveCount", "live now", AdminColors.Green, Modifier.weight(1f))
            QueueStat("$closedCount", "closed", AdminColors.Red, Modifier.weight(1f))
            QueueStat(
                allProvider.groupBy { it.postedBy }.size.toString(),
                "providers",
                AdminColors.Gold,
                Modifier.weight(1f)
            )
            QueueStat(
                allProvider.sumOf { it.openings }.toString(),
                "openings",
                AdminColors.Violet,
                Modifier.weight(1f)
            )
        }

        // ============ search ============
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search title, provider, location", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = aiFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp, color = AdminColors.InkStrong),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        )

        // ============ per-provider filter chips ============
        if (providers.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                item {
                    Surface(
                        shape = CircleShape,
                        color = if (providerFilter == null) AdminColors.Violet else AdminColors.Card,
                        border = if (providerFilter == null) null
                        else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                        modifier = Modifier.clickable { providerFilter = null }
                    ) {
                        Text(
                            "All providers",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (providerFilter == null) Color.White else AdminColors.InkSoft,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                items(providers) { p ->   // LazyRow items
                    val active = providerFilter == p
                    Surface(
                        shape = CircleShape,
                        color = if (active) AdminColors.Violet else AdminColors.Card,
                        border = if (active) null
                        else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                        modifier = Modifier.clickable { providerFilter = if (active) null else p }
                    ) {
                        Text(
                            p,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else AdminColors.InkSoft,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // ============ filter + sort rows ============
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            JobFilters.forEach { f ->
                val active = filter == f
                Surface(
                    shape = CircleShape,
                    color = if (active) AdminColors.Green else AdminColors.Card,
                    modifier = Modifier.clickable { filter = f }
                ) {
                    Text(
                        "$f  ${when (f) {
                            "Live" -> liveCount
                            "Closed" -> closedCount
                            else -> allProvider.size
                        }}",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) Color.White else AdminColors.InkSoft,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                "⇅ $sort",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = AdminColors.InkSoft,
                modifier = Modifier.clickable {
                    sort = JobSorts[(JobSorts.indexOf(sort) + 1) % JobSorts.size]
                }
            )
        }

        // ============ toast ============
        justClosed?.let {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = AdminColors.GoldSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp)
            ) {
                Text(
                    "\"$it\" ${if (filter == "Closed") "reopened" else "closed"} — ${if (filter == "Closed") "accepting applications again" else "no longer accepting applications"}",
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = AdminColors.Gold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ============ job list ============
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp)
        ) {
            if (visible.isEmpty()) {
                AdminEmptyState(
                    "🧭",
                    if (query.isBlank()) "No ${filter.lowercase()} provider jobs"
                    else "No matches",
                    if (query.isBlank())
                        "Tap \"Paste provider job\" on the Post tab and the listing goes live here immediately."
                    else
                        "Try a different search term or switch the filter."
                )
            }
            visible.forEach { job ->
                key(job.id) {
                    ProviderJobCard(
                        job = job,
                        applicationCount = JobRepository.applicantsFor(job.id).size,
                        applicants = allApplications.filter {
                            it.job.id == job.id
                        }.distinctBy { it.applicantContact }.take(3),
                        isExpanded = expandedId == job.id,
                        onToggleExpand = { expandedId = if (expandedId == job.id) null else job.id },
                        onCloseToggle = {
                            if (job.closed) {
                                JobRepository.reopenJob(job.id)
                                justClosed = job.title
                            } else {
                                confirmClose = job.id
                            }
                        },
                        onDuplicate = {
                            JobRepository.duplicateJob(job.id)
                            justClosed = job.title
                        },
                        onEdit = { editJobId = job.id },
                        onDelete = { confirmDelete = job.id },
                        onPreview = { previewJobId = job.id },
                        onReviewApplicants = { onReviewApplicants(job.id) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        }

        // ============ client-side job preview overlay ============
        previewJobId?.let { id ->
            jobs.find { it.id == id }?.let { job ->
                AdminJobPreviewOverlay(job = job, onClose = { previewJobId = null })
            }
        }
    }

    // ============ close confirmation ============
    confirmClose?.let { id ->
        val job = allProvider.find { it.id == id }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmClose = null },
            title = { Text("Close \"${job?.title}\"?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The listing will be removed from the public feed and stop accepting " +
                            "applications. You can reopen it any time from this page."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    JobRepository.closeJob(id)
                    justClosed = job?.title
                    confirmClose = null
                }) {
                    Text("Close job", fontWeight = FontWeight.Bold, color = AdminColors.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmClose = null }) {
                    Text("Cancel", color = AdminColors.InkSoft)
                }
            }
        )
    }

    // ============ delete confirmation (permanent) ============
    confirmDelete?.let { id ->
        val job = allProvider.find { it.id == id }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete \"${job?.title}\"?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This permanently removes the listing from the app and the database. " +
                            "Applications already received are kept for records. This cannot be undone."
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    JobRepository.deleteJob(id)
                    confirmDelete = null
                }) {
                    Text("Delete forever", fontWeight = FontWeight.Bold, color = AdminColors.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) {
                    Text("Cancel", color = AdminColors.InkSoft)
                }
            }
        )
    }

    // ============ edit dialog ============
    editJobId?.let { id ->
        allProvider.find { it.id == id }?.let { job ->
            EditProviderJobDialog(
                job = job,
                onDismiss = { editJobId = null },
                onSave = { t, c, l, p, ty, d, o, e, u, comp ->
                    JobRepository.updateProviderJob(
                        jobId = id,
                        title = t, category = c, location = l, pay = p, type = ty,
                        description = d, openings = o, experience = e,
                        applicationUrl = u, isCompany = comp
                    )
                    editJobId = null
                }
            )
        }
    }
}

// ---------------------------------------------------------------------

/** Edit dialog for a provider job — all changes persist to Firestore. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditProviderJobDialog(
    job: Job,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, Int, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(job.title) }
    var category by remember { mutableStateOf(job.category) }
    var location by remember { mutableStateOf(job.location) }
    var pay by remember { mutableStateOf(job.pay) }
    var type by remember { mutableStateOf(job.type) }
    var description by remember { mutableStateOf(job.description) }
    var openings by remember { mutableStateOf(job.openings.toString()) }
    var experience by remember { mutableStateOf(job.experience) }
    var applicationUrl by remember { mutableStateOf(job.applicationUrl) }
    var isCompany by remember { mutableStateOf(job.isCompany) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit provider job", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Job title", fontSize = 12.sp) }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
                // Category: subcategory pills (professional groups)
                Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AdminColors.InkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JobRepository.categories.filter { it != "All" }.forEach { cat ->
                        val active = category == cat
                        Surface(
                            shape = CircleShape,
                            color = if (active) AdminColors.Violet else AdminColors.Paper,
                            border = if (active) null
                            else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                cat,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else AdminColors.InkSoft,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Location", fontSize = 12.sp) }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = pay, onValueChange = { pay = it },
                        label = { Text("Pay", fontSize = 12.sp) }, singleLine = true,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = openings,
                        onValueChange = { openings = it.filter { ch -> ch.isDigit() }.take(2) },
                        label = { Text("Openings", fontSize = 12.sp) }, singleLine = true,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.width(90.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Full-time", "Part-time", "Contract", "Gig").forEach { t ->
                        val active = type == t
                        Surface(
                            shape = CircleShape,
                            color = if (active) AdminColors.Violet else AdminColors.Paper,
                            border = if (active) null
                            else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline),
                            modifier = Modifier.clickable { type = t }
                        ) {
                            Text(
                                t,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) Color.White else AdminColors.InkSoft,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                androidx.compose.material3.OutlinedTextField(
                    value = experience, onValueChange = { experience = it },
                    label = { Text("Experience", fontSize = 12.sp) }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = applicationUrl, onValueChange = { applicationUrl = it },
                    label = { Text("Apply at (URL / mailto: / tel:)", fontSize = 12.sp) }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = description, onValueChange = { description = it },
                    label = { Text("Description", fontSize = 12.sp) }, minLines = 3,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                )
                // Company / individual toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCompany) "Company — listed under Companies hiring"
                        else "Individual — not under Companies hiring",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCompany) AdminColors.Green else Color(0xFFB65C2E),
                        modifier = Modifier.weight(1f)
                    )
                    androidx.compose.material3.Switch(
                        checked = isCompany,
                        onCheckedChange = { isCompany = it }
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(
                            title, category, location, pay, type, description,
                            openings.toIntOrNull() ?: 1, experience, applicationUrl, isCompany
                        )
                    }
                }
            ) { Text("Save", fontWeight = FontWeight.Bold, color = AdminColors.Green) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel", color = AdminColors.InkSoft)
            }
        }
    )
}

// ---------------------------------------------------------------------

@Composable
private fun ProviderJobCard(
    job: Job,
    applicationCount: Int,
    applicants: List<Application>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCloseToggle: () -> Unit,
    onDuplicate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPreview: () -> Unit,
    onReviewApplicants: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AdminColors.Card,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(10.dp), color = AdminColors.Green) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                        Text(
                            job.postedBy.take(2).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        job.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkStrong,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "${job.postedBy} · ${timeAgo(job.postedAtMillis)}",
                        fontSize = 11.sp,
                        color = AdminColors.InkSoft
                    )
                    Text(
                        "${job.location} · ${job.pay}",
                        fontSize = 10.5.sp,
                        color = AdminColors.InkSoft.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    AdminStatusChip(if (job.closed) "Closed" else "Live")
                    ExpiryBadge(job)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "$applicationCount app${if (applicationCount == 1) "" else "s"}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (applicationCount > 0) AdminColors.Gold else AdminColors.InkSoft.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // expandable details
            androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = AdminColors.Paper,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            DetailLine("Category", job.category)
                            DetailLine("Type", job.type)
                            DetailLine("Openings", "${job.openings} position${if (job.openings == 1) "" else "s"}")
                            if (job.experience.isNotBlank()) DetailLine("Experience", job.experience)
                            if (job.applicationUrl.isNotBlank()) DetailLine("Apply at", job.applicationUrl)
                            if (job.deadlineDays != null) DetailLine("Deadline", "${job.deadlineDays} days")
                            if (job.requirements.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "REQUIREMENTS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = AdminColors.InkSoft
                                )
                                job.requirements.take(4).forEach { r ->
                                    Row(modifier = Modifier.padding(top = 4.dp)) {
                                        Box(
                                            Modifier
                                                .padding(top = 5.dp)
                                                .size(4.dp)
                                                .background(AdminColors.Green, CircleShape)
                                        )
                                        Spacer(Modifier.width(7.dp))
                                        Text(
                                            r,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            color = AdminColors.InkSoft
                                        )
                                    }
                                }
                            }

                            // inline applicant list
                            if (applicants.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "APPLICANTS",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = AdminColors.InkSoft
                                )
                                applicants.forEach { a ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 7.dp)
                                    ) {
                                        Surface(shape = CircleShape, color = AdminColors.GreenSoft) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
                                                Text(
                                                    a.applicantName.take(2).uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AdminColors.Green
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            a.applicantName,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AdminColors.InkStrong,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        AdminStatusChip(a.status)
                                    }
                                }
                                if (applicationCount > applicants.size) {
                                    Text(
                                        "+${applicationCount - applicants.size} more",
                                        fontSize = 10.sp,
                                        color = AdminColors.InkSoft,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AdminColors.Violet,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onReviewApplicants() }
                                ) {
                                    Text(
                                        "Review all applicants in queue →",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 9.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 11.dp)
            ) {
                Text(
                    if (isExpanded) "Hide details" else "Details",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Violet,
                    modifier = Modifier
                        .clickable { onToggleExpand() }
                        .padding(vertical = 6.dp)
                )
                Text(
                    "Preview",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Gold,
                    modifier = Modifier
                        .clickable { onPreview() }
                        .padding(vertical = 6.dp)
                )
                Text(
                    "Edit",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Violet,
                    modifier = Modifier
                        .clickable { onEdit() }
                        .padding(vertical = 6.dp)
                )
                Text(
                    "Delete",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Red,
                    modifier = Modifier
                        .clickable { onDelete() }
                        .padding(vertical = 6.dp)
                )
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (job.closed) AdminColors.Green else Color.Transparent,
                    border = if (job.closed) null
                    else androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Red),
                    modifier = Modifier.clickable { onCloseToggle() }
                ) {
                    Text(
                        if (job.closed) "Reopen" else "Close",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (job.closed) Color.White else AdminColors.Red,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
