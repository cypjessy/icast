package com.prc.app.ui.screens.auth

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import kotlinx.coroutines.delay

/**
 * OTP verification: aurora stage, contact pill, six decorated digit boxes with
 * pop-fill + active glow, mm:ss countdown pill, and the verify CTA.
 */
@Composable
fun OtpScreen(
    contact: String,
    isPhone: Boolean,
    mode: String,   // signup | login | reset
    onVerified: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    val boxCount = 6
    val digits = remember { mutableStateListOf(*Array(boxCount) { "" }) }
    val focusRequesters = remember { List(boxCount) { FocusRequester() } }
    var error by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableStateOf(34) }
    var verified by remember { mutableStateOf(false) }

    // Mock "SMS" — generate a code for this contact on entry.
    val mockCode = remember(contact) { AuthRepository.requestOtp(contact, isPhone) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            if (resendIn > 0) resendIn--
        }
    }

    LaunchedEffect(Unit) { focusRequesters[0].requestFocus() }

    fun submit() {
        if (digits.any { it.isBlank() } || verified) return
        if (AuthRepository.verifyOtp(contact, isPhone, digits.joinToString(""))) {
            verified = true
            onVerified()
        } else {
            error = "Incorrect code — in this demo build it is: $mockCode"
        }
    }

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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth()) {
                AuthBackCircle(onBack = { error = null; digits.fill(""); focusRequesters[0].requestFocus() })
            }
            Spacer(Modifier.height(18.dp))
            AuthMark()
            Spacer(Modifier.height(20.dp))

            val titleIn = rememberEntrance(delayMillis = 80)
            val subIn = rememberEntrance(delayMillis = 180)
            Text(
                if (isPhone) "Verify your number" else "Verify your email",
                fontSize = 27.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                color = OnAurora(),
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    alpha = titleIn
                    translationY = (1f - titleIn) * 26f
                }
            )
            Spacer(Modifier.height(10.dp))
            // contact pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(auroraSurface(0.07f))
                    .border(1.dp, OnAurora().copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Code sent to  ",
                    fontSize = 12.sp,
                    color = OnAurora().copy(alpha = 0.55f)
                )
                Text(
                    contact,
                    fontSize = 12.5.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = auroraGoldAccent()
                )
            }
            Spacer(Modifier.height(34.dp))

            // ---- six digit boxes ----
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                digits.forEachIndexed { index, digit ->
                    val filled = digit.isNotBlank()
                    val focused = index == digits.indexOfFirst { it.isBlank() } && !verified
                    val pop by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "otpPop$index"
                    )
                    Box {
                        // active glow (soft radial falloff)
                        if (focused && !filled) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .padding(vertical = 3.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.radialGradient(
                                            listOf(auroraGoldAccent().copy(alpha = 0.4f), androidx.compose.ui.graphics.Color.Transparent)
                                        ),
                                        RoundedCornerShape(18.dp)
                                    )
                            )
                        }
                        OutlinedTextField(
                            value = digit,
                            onValueChange = { value ->
                                val filtered = value.filter { it.isDigit() }
                                when {
                                    filtered.length == 1 -> {
                                        digits[index] = filtered
                                        if (index < boxCount - 1) focusRequesters[index + 1].requestFocus()
                                    }
                                    filtered.length > 1 -> {
                                        digits[index] = filtered.last().toString()
                                        if (index < boxCount - 1) focusRequesters[index + 1].requestFocus()
                                    }
                                    else -> {
                                        digits[index] = ""
                                        if (index > 0) focusRequesters[index - 1].requestFocus()
                                    }
                                }
                                error = null
                                if (digits.all { it.isNotBlank() }) submit()
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                textAlign = TextAlign.Center,
                                fontSize = 19.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = OnAurora()
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = auroraSurface(0.05f),
                                focusedContainerColor = auroraSurface(0.09f),
                                unfocusedBorderColor = if (filled) auroraGoldAccent().copy(alpha = 0.75f)
                                else OnAurora().copy(alpha = 0.16f),
                                focusedBorderColor = auroraGoldAccent(),
                                cursorColor = auroraGoldAccent(),
                                focusedTextColor = OnAurora(),
                                unfocusedTextColor = OnAurora()
                            ),
                            modifier = Modifier
                                .width(47.dp)
                                .height(58.dp)
                                .focusRequester(focusRequesters[index])
                        )
                        // filled dot-flash (a quick brightening on fill)
                        if (filled) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .scale(pop)
                                    .alpha((1f - pop) * 0.5f)
                                    .background(auroraGoldAccent().copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            error?.let {
                Text(
                    it,
                    color = if (auroraIsDark()) Color(0xFFFF8A7A) else Color(0xFFC2452F),
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
            }

            // ---- countdown / resend ----
            if (resendIn > 0) {
                Text(
                    "Resend code in 00:${resendIn.coerceAtMost(59).toString().padStart(2, '0')}",
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp,                    color = OnAurora().copy(alpha = 0.5f)
            )
            } else {
                Text(
                    "Resend code",
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = auroraGoldAccent(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            AuthRepository.requestOtp(contact, isPhone)
                            resendIn = 34
                            error = null
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            PrimaryAuthButton("Verify & continue", enabled = digits.all { it.isNotBlank() }) { submit() }

            Spacer(Modifier.height(16.dp))
            Text(
                "Wrong number? Go back and change it",
                fontSize = 11.5.sp,
                color = OnAurora().copy(alpha = 0.45f)
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

