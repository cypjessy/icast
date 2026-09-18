package com.prc.app.ui.screens.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.PaymentsRepository
import com.prc.app.ui.theme.SystemBarAppearance
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PaymentRecord(
    val reference: String,
    val kind: String,          // boost | unlock | subscription
    val productId: String,
    val label: String,
    val amountKes: Int,
    val status: String,        // initialized | success
    val jobId: Int? = null,
    val createdAt: Long
)

/** User's past transactions, read from the Firestore `payments` collection. */
@Composable
fun PaymentsHistoryScreen(onBack: () -> Unit) {
    SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val scope = rememberCoroutineScope()
    var payments by remember { mutableStateOf<List<PaymentRecord>?>(null) }   // null = loading
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        val uid = AuthRepository.currentUid() ?: return
        try {
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("payments")
                .whereEqualTo("uid", uid)
                .get()
                .await()
            payments = snap.documents
                .mapNotNull { doc ->
                    val amount = (doc.getLong("amount") ?: 0L) / 100   // kobo -> KSh
                    val ts = doc.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    PaymentRecord(
                        reference = doc.id,
                        kind = doc.getString("kind") ?: "",
                        productId = doc.getString("productId") ?: "",
                        label = labelFor(doc.getString("productId") ?: "", doc.getString("kind") ?: ""),
                        amountKes = amount.toInt(),
                        status = doc.getString("status") ?: "initialized",
                        jobId = (doc.getLong("jobId") ?: 0L).toInt().takeIf { it > 0 },
                        createdAt = ts
                    )
                }
                .sortedByDescending { it.createdAt }
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Could not load payments"
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Payments", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { padding ->
        when {
            payments == null && error == null -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Couldn't load your payments", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(error ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { scope.launch { load() } }) { Text("Retry") }
                }
            }
            payments!!.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("🧾", fontSize = 40.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("No payments yet", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Boosts, application unlocks and Pro subscriptions you buy will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(payments!!, key = { it.reference }) { p ->
                        PaymentRow(p)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(p: PaymentRecord) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val (icon, tint) = when (p.kind) {
                "boost" -> Icons.Filled.Star to androidx.compose.ui.graphics.Color(0xFFF9A825)
                "unlock" -> Icons.Filled.Lock to MaterialTheme.colorScheme.primary
                else -> Icons.Filled.WorkspacePremium to MaterialTheme.colorScheme.tertiary
            }
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    buildString {
                        append(fmt.format(Date(p.createdAt)))
                        if (p.jobId != null) append(" · job #${p.jobId}")
                    },
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "KSh ${p.amountKes}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (p.status == "success") MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (p.status == "success") "Paid" else "Pending",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (p.status == "success") androidx.compose.ui.graphics.Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun labelFor(productId: String, kind: String): String = when (productId) {
    "boost_3d" -> "Featured Boost — 3 days"
    "boost_7d" -> "Featured Boost — 7 days"
    "app_unlock" -> "Application unlock"
    "pro_monthly" -> "Pro plan — monthly"
    "pro_yearly" -> "Pro plan — yearly"
    else -> when (kind) {
        "boost" -> "Job boost"
        "unlock" -> "Application unlock"
        "subscription" -> "Pro subscription"
        else -> "Payment"
    }
}
