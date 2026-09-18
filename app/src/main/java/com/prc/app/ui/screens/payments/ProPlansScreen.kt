package com.prc.app.ui.screens.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.BillingProduct
import com.prc.app.data.PaymentsRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pro subscription screen — monthly/yearly plans, live status from Firestore. */
@Composable
fun ProPlansScreen(onBack: () -> Unit, entrySource: String = "profile") {
    val me = AuthRepository.currentUser.collectAsState().value
    val scope = rememberCoroutineScope()
    var proUntil by remember { mutableStateOf<Long?>(null) }
    var checkoutProduct by remember { mutableStateOf<BillingProduct?>(null) }

    LaunchedEffect(AuthRepository.currentUid()) {
        proUntil = AuthRepository.currentUid()?.let { PaymentsRepository.proUntil(it) }
    }

    if (checkoutProduct != null) {
        CheckoutScreen(
            product = checkoutProduct!!,
            source = entrySource,
            onDone = {
                checkoutProduct = null
                scope.launch { proUntil = AuthRepository.currentUid()?.let { PaymentsRepository.proUntil(it) } }
            },
            onBack = { checkoutProduct = null }
        )
        return
    }

    val active = (proUntil ?: 0) > System.currentTimeMillis()
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Go Pro", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(20.dp)) {
                if (active) {
                    Text("Pro is active ✨", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Renews / expires ${fmt.format(Date(proUntil!!))}", fontSize = 13.sp)
                } else {
                    Text("You're on the free plan", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Upgrade to apply to premium jobs without per-application fees.", fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        listOf(
            "Unlimited premium job applications",
            "Priority placement in employer searches",
            "Featured profile badge",
            "Early access to newly posted jobs"
        ).forEach { perk ->
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(perk, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(24.dp))

        PlanCard(
            title = BillingProduct.ProMonthly().label,
            price = "KSh ${BillingProduct.ProMonthly().amountKes} / month",
            highlight = false
        ) { checkoutProduct = BillingProduct.ProMonthly() }
        Spacer(Modifier.height(12.dp))
        PlanCard(
            title = BillingProduct.ProYearly().label,
            price = "KSh ${BillingProduct.ProYearly().amountKes} / year",
            highlight = true
        ) { checkoutProduct = BillingProduct.ProYearly() }
        Spacer(Modifier.height(12.dp))
        Text(
            "Payments secured by Paystack · Cards, M-Pesa, bank transfer & USSD",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanCard(title: String, price: String, highlight: Boolean, onBuy: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = if (highlight) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                if (highlight) Text("Best value", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(price, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBuy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(if (highlight) "Save 17% — Subscribe" else "Subscribe")
            }
        }
    }
}
