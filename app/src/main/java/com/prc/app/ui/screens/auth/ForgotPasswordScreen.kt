package com.prc.app.ui.screens.auth

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository

/**
 * Two-stage forgot password on the aurora stage:
 *  "request" — enter contact, send reset code
 *  "newpass" — after OTP verification, set the new password
 * Stage transitions animate (slide + fade), and the request stage carries the
 * premium "what happens next" card.
 */
@Composable
fun ForgotPasswordScreen(
    otpVerifiedContact: String?,   // non-null when arriving back from OTP verification
    onNavigateToOtp: (contact: String, isPhone: Boolean) -> Unit,
    onDone: () -> Unit             // reset complete -> go to login
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    var stage by remember { mutableStateOf(if (otpVerifiedContact == null) "request" else "newpass") }
    var contact by remember { mutableStateOf(otpVerifiedContact ?: "") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        AuroraBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            AuthBackCircle(onBack = onDone)
            Spacer(Modifier.height(18.dp))

            AnimatedContent(
                targetState = stage,
                transitionSpec = {
                    (slideInVertically(tween(420, easing = EaseOutExpo)) { it / 4 } + fadeIn(tween(420)))
                        .togetherWith(slideOutVertically(tween(300)) { -it / 6 } + fadeOut(tween(220)))
                },
                label = "forgotStage"
            ) { currentStage ->
                Column {
                    if (currentStage == "request") {
                        AuthHeadline(
                            title = "Reset your password",
                            body = "Enter your account email and we'll send a reset link."
                        )
                        Spacer(Modifier.height(28.dp))

                        AuthField(
                            value = contact,
                            onValueChange = {
                                contact = it
                                error = null
                            },
                            label = "Email address",
                            icon = Icons.Filled.AlternateEmail,
                            entranceKey = "reset-contact"
                        )

                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                it,
                                color = Color(0xFFFF8A7A),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        PrimaryAuthButton("Send reset link") {
                            val emailOk = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                                .matches(contact.trim())
                            if (!emailOk) {
                                error = "Enter a valid email address"
                            } else {
                                AuthRepository.sendPasswordReset(contact) { result ->
                                    result.fold(
                                        onSuccess = {
                                            error = null
                                            // Firebase emails the link; surface a hint and return to login
                                            onNavigateToOtp(contact, false)
                                        },
                                        onFailure = { e -> error = e.message }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        BottomAuthLink("Remembered your password?", "Log in", onDone)

                        // ---- what-happens-next card ----
                        Spacer(Modifier.height(32.dp))
                        SuccessCard()
                    } else {
                        AuthHeadline(
                            title = "Set a new password",
                            body = "Verified! Now choose a new password for $contact."
                        )
                        Spacer(Modifier.height(28.dp))

                        AuthField(
                            value = newPass,
                            onValueChange = { newPass = it; error = null },
                            label = "New password",
                            icon = Icons.Filled.Lock,
                            isPassword = true,
                            entranceKey = "new-pass"
                        )
                        Spacer(Modifier.height(16.dp))
                        AuthField(
                            value = confirmPass,
                            onValueChange = { confirmPass = it; error = null },
                            label = "Confirm new password",
                            icon = Icons.Filled.Lock,
                            isPassword = true,
                            entranceKey = "new-confirm",
                            entranceDelay = 120
                        )

                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                it,
                                color = Color(0xFFFF8A7A),
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                        PrimaryAuthButton("Save new password") {
                            if (newPass.length < 6) {
                                error = "Password must be at least 6 characters"
                            } else if (newPass != confirmPass) {
                                error = "Passwords don't match"
                            } else {
                                // Password reset now completes via the emailed Firebase link,
                                // so this legacy path just returns to login.
                                onDone()
                            }
                        }

                        Spacer(Modifier.height(18.dp))
                        BottomAuthLink("Remembered it after all?", "Log in", onDone)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Dashed-border explainer card with a pulsing check ring. */
@Composable
private fun SuccessCard() {
    val pulse = rememberFloatCycle(0.85f, 1f, 1800)
    val cardIn = rememberEntrance(delayMillis = 300)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(cardIn.coerceIn(0f, 1f))
            .background(auroraSurface(0.04f), RoundedCornerShape(18.dp))
            .border(1.dp, OnAurora().copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(58.dp)
                        .scale(pulse)
                        .alpha(0.3f)
                        .background(auroraGoldAccent().copy(alpha = 0.35f), CircleShape)
                )
                Box(
                    Modifier
                        .size(46.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(auroraAccent(), auroraAccent().darkenAuth(0.75f))
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "How it works",
                fontSize = 15.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                color = OnAurora()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "We'll text you a 6-digit code. Verify it, then choose a new password — it takes about a minute.",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                color = OnAurora().copy(alpha = 0.55f)
            )
        }
    }
}
