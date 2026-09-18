package com.prc.app.ui.screens.payments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.BillingProduct
import com.prc.app.ui.screens.auth.AuroraBackdrop
import com.prc.app.ui.screens.auth.auroraBase
import com.prc.app.ui.screens.auth.auroraGoldAccent
import com.prc.app.ui.screens.auth.auroraIsDark
import com.prc.app.ui.screens.auth.auroraSurface

/**
 * Premium first-run plan choice, matching the aurora design language of the
 * auth screens it follows. Free vs Pro, with Pro visually elevated: gold
 * crown, glass card, gradient CTA. Free continues into the app; Pro opens
 * Paystack checkout.
 */
@Composable
fun PlanChoiceScreen(
    onChoseFree: () -> Unit,
    onProSubscribed: () -> Unit
) {
    var showCheckout by remember { mutableStateOf(false) }
    val entrance = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        androidx.compose.animation.core.Animatable(0f)
        entrance.animateTo(1f, androidx.compose.animation.core.tween(650))
    }
    val rise by animateFloatAsState(
        targetValue = entrance.value,
        animationSpec = tween(650),
        label = "rise"
    )
    val gold = auroraGoldAccent()
    val dark = auroraIsDark()

    if (showCheckout) {
        CheckoutScreen(
            product = BillingProduct.ProMonthly(),
            source = "registration",
            onDone = {
                com.prc.app.data.PlanAnalytics.recordChoice("pro", source = "registration")
                com.prc.app.data.PlanAnalytics.recordConversion(source = "registration")
                onProSubscribed()
            },
            onBack = { showCheckout = false }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(auroraBase())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
            .padding(bottom = 28.dp)
            .graphicsLayer {
                alpha = rise
                translationY = (1f - rise) * 30f
            }
    ) {
        Spacer(Modifier.height(34.dp))

        // ==================== header ====================
        Box(
            Modifier
                .size(46.dp)
                .background(
                    Brush.linearGradient(listOf(gold, gold.copy(alpha = 0.72f))),
                    RoundedCornerShape(13.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Choose how you\nshow up",
            fontSize = 27.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = com.prc.app.ui.screens.auth.OnAurora()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Start free. Go Pro when you're ready for more reach — upgrade or downgrade anytime.",
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(24.dp))

        // ==================== PRO card (elevated, first) ====================
        ProCard(gold = gold, dark = dark) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PRO",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                    color = gold
                )
                Spacer(Modifier.width(8.dp))
                // best-value ribbon
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = gold.copy(alpha = 0.16f)
                ) {
                    Text(
                        "MOST POPULAR",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = gold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "KSh 500",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = com.prc.app.ui.screens.auth.OnAurora()
                    )
                    Text(
                        "per month",
                        fontSize = 10.5.sp,
                        color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // hairline
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(auroraSurface(0.14f))
            )
            Spacer(Modifier.height(16.dp))

            ProFeature("Everything in Free, plus:", gold, dark, headline = true)
            AuroraFeature("Unlimited premium job applications", gold)
            AuroraFeature("Priority placement in employer searches", gold)
            AuroraFeature("Gold Pro badge employers see first", gold)
            AuroraFeature("Unlimited saved search alerts", gold)
            AuroraFeature("Early access to newly posted jobs", gold)

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { showCheckout = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = if (dark) Color(0xFF141414) else Color.White
                ),
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                contentPadding = PaddingValues()
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(listOf(gold, gold.copy(alpha = 0.82f))),
                            RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Go Pro — KSh 500/month",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFF141414) else Color(0xFF141414)
                    )
                }
            }
            Text(
                "Cancel anytime · secured by Paystack · M-Pesa, cards, bank & USSD",
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ==================== FREE card (quieter, second) ====================
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dark) Color.White.copy(alpha = 0.05f)
                else Color.White.copy(alpha = 0.72f)
            ),
            border = BorderStroke(1.dp, auroraSurface(0.16f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "FREE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "KSh 0",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                FreeFeature("Apply to regular jobs", dark)
                FreeFeature("Post jobs & receive applicants", dark)
                FreeFeature("1 saved search alert", dark)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        com.prc.app.data.PlanAnalytics.recordChoice("free", source = "registration")
                        onChoseFree()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, auroraSurface(0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = com.prc.app.ui.screens.auth.OnAurora()
                    )
                ) {
                    Text("Continue with Free", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "You can upgrade from Profile → Payments at any time.",
            fontSize = 11.sp,
            color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ==================== pieces ====================

/** Elevated glass card for the Pro plan: stronger border + soft gold edge glow. */
@Composable
private fun ProCard(
    gold: Color,
    dark: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Box {
        // soft gold under-glow
        Box(
            Modifier
                .matchParentSize()
                .padding(2.dp)
                .background(
                    Brush.radialGradient(
                        listOf(gold.copy(alpha = if (dark) 0.16f else 0.13f), Color.Transparent)
                    ),
                    RoundedCornerShape(18.dp)
                )
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (dark) Color.White.copy(alpha = 0.08f)
                else Color.White.copy(alpha = 0.9f)
            ),
            border = BorderStroke(1.6.dp, gold.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp), content = content)
        }
    }
}

@Composable
private fun ProFeature(text: String, gold: Color, dark: Boolean, headline: Boolean) {
    Text(
        text,
        fontSize = if (headline) 12.5.sp else 13.sp,
        fontWeight = if (headline) FontWeight.SemiBold else FontWeight.Normal,
        letterSpacing = if (headline) 0.6.sp else 0.sp,
        color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = if (headline) 0.7f else 1f),
        modifier = Modifier.padding(vertical = 5.dp)
    )
}

@Composable
private fun AuroraFeature(text: String, gold: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 5.dp)
    ) {
        Surface(shape = CircleShape, color = gold.copy(alpha = 0.16f)) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = gold,
                modifier = Modifier
                    .padding(3.dp)
                    .size(12.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            color = com.prc.app.ui.screens.auth.OnAurora()
        )
    }
}

@Composable
private fun FreeFeature(text: String, dark: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.55f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text,
            fontSize = 12.5.sp,
            color = com.prc.app.ui.screens.auth.OnAurora().copy(alpha = 0.72f)
        )
    }
}
