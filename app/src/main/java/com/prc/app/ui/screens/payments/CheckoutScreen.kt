package com.prc.app.ui.screens.payments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paystack.android.core.Paystack
import com.paystack.android.ui.paymentsheet.PaymentSheet
import com.paystack.android.ui.paymentsheet.PaymentSheetResult
import com.paystack.android.ui.paymentsheet.PaymentSheetResultCallback
import kotlinx.coroutines.launch
import com.prc.app.data.AuthRepository
import com.prc.app.data.BillingProduct
import com.prc.app.data.CheckoutState
import com.prc.app.data.PaymentsRepository

/** Set your Paystack PUBLIC key here (pk_test_… / pk_live_…). Never put the secret key in the app. */
private const val PAYSTACK_PUBLIC_KEY = "pk_test_YOUR_PUBLIC_KEY"

/**
 * Universal checkout for boosts, application unlocks, and subscriptions.
 * Flow: initialize (Cloud Function) -> PaymentSheet.launch(accessCode) -> verify (Cloud Function).
 */
@Composable
fun CheckoutScreen(
    product: BillingProduct,
    jobId: Int? = null,
    source: String = "profile",
    variant: String = "standard",
    onDone: (success: Boolean) -> Unit,
    onBack: () -> Unit
) {
    val state by PaymentsRepository.checkout.collectAsState()
    val scope = rememberCoroutineScope()
    val me = AuthRepository.currentUser.value
    val snackbar = remember { SnackbarHostState() }

    // PaymentSheet callback — runs outside composition, so hop to a scope for suspend work.
    val onSheetResult = PaymentSheetResultCallback { result ->
        when (result) {
            is PaymentSheetResult.Completed -> scope.launch {
                val ok = PaymentsRepository.verify()
                if (ok) {
                    // Attribution: which surface + variant produced this sale.
                    com.prc.app.data.PlanAnalytics.recordConversion(source)
                    com.prc.app.data.PlanAnalytics.recordCheckoutCompleted(source, variant)
                }
                snackbar.showSnackbar(if (ok) "Payment successful 🎉" else "Verification failed")
                if (ok) onDone(true)
            }
            is PaymentSheetResult.Cancelled -> scope.launch {
                PaymentsRepository.reset()
                snackbar.showSnackbar("Payment cancelled")
            }
            is PaymentSheetResult.Failed -> scope.launch {
                PaymentsRepository.reset()
                snackbar.showSnackbar("Payment failed: ${result.error.message ?: "unknown error"}")
            }
        }
    }
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    val paymentSheet = remember(activity) {
        activity?.let { PaymentSheet(it, onSheetResult) }
    }

    // Initialize Paystack once (public key only).
    LaunchedEffect(Unit) {
        Paystack.builder()
            .setPublicKey(PAYSTACK_PUBLIC_KEY)
            .setLoggingEnabled(false)
            .build()
    }

    // Launch the sheet as soon as initialization returns an access code.
    LaunchedEffect(state.accessCode) {
        if (state.phase == CheckoutState.Phase.AwaitingSheet && state.accessCode.isNotBlank()) {
            paymentSheet?.launch(state.accessCode)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
                Text("Checkout", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(24.dp))

            // Product summary card
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(product.label, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(product.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "KSh ${product.amountKes}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Secured by Paystack · Cards, M-Pesa, bank transfer & USSD",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            when (state.phase) {
                CheckoutState.Phase.Success -> {
                    SuccessBlock(onDone = { onDone(true) })
                }
                CheckoutState.Phase.Failed -> {
                    ErrorBlock(message = state.message, onRetry = {
                        PaymentsRepository.reset()
                    }, onBack = onBack)
                }
                else -> {
                    Button(
                        onClick = {
                            scope.launch {
                                val email = me?.contact?.ifBlank { "user@prc.app" } ?: "user@prc.app"
                                val ok = PaymentsRepository.initialize(product, jobId, email)
                                if (!ok) snackbar.showSnackbar(state.message.ifBlank { "Could not start payment" })
                            }
                        },
                        enabled = state.phase == CheckoutState.Phase.Idle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        when (state.phase) {
                            CheckoutState.Phase.Initializing -> CircularProgressIndicator(
                                Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White
                            )
                            else -> Text("Pay KSh ${product.amountKes}", fontWeight = FontWeight.Bold)
                        }
                    }
                    if (state.phase == CheckoutState.Phase.AwaitingSheet || state.phase == CheckoutState.Phase.Verifying) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (state.phase == CheckoutState.Phase.Verifying) "Confirming payment…" else "Complete the payment in the Paystack sheet…",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessBlock(onDone: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Payment successful", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Text("Continue")
        }
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text(message, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Close") }
            Button(onClick = onRetry, modifier = Modifier.weight(1f)) { Text("Try again") }
        }
    }
}