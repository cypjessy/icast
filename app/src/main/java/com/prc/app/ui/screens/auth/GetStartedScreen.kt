package com.prc.app.ui.screens.auth

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState

/**
 * Premium "Get started" gateway: aurora hero with halo mark, serif display
 * headline, gradient primary CTA and glass Google button — all staggered in.
 */
@Composable
fun GetStartedScreen(
    onContinuePhone: () -> Unit,
    onContinueGoogle: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    val markIn = rememberEntrance(delayMillis = 0, pop = true)
    val titleIn = rememberEntrance(delayMillis = 150)
    val bodyIn = rememberEntrance(delayMillis = 280)
    val ctaIn = rememberEntrance(delayMillis = 420)
    val footIn = rememberEntrance(delayMillis = 560)

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
                .padding(horizontal = 24.dp)
                .padding(bottom = 26.dp)
        ) {
            // ---- hero ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.graphicsLayer {
                    val p = markIn
                    scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p
                    alpha = p.coerceIn(0f, 1f)
                }) {
                    AuthMark(size = 74)
                }
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "Let's get you hired",
                    fontSize = 33.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp,
                    color = OnAurora(),
                    modifier = Modifier.graphicsLayer {
                        alpha = titleIn
                        translationY = (1f - titleIn) * 34f
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Create an account to start applying to jobs near you — or find the help you need, today.",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    color = OnAurora().copy(alpha = 0.62f),
                    modifier = Modifier.graphicsLayer {
                        alpha = bodyIn
                        translationY = (1f - bodyIn) * 24f
                    }
                )
            }

            // ---- CTAs ----
            Column(
                modifier = Modifier.graphicsLayer {
                    alpha = ctaIn
                    translationY = (1f - ctaIn) * 30f
                }
            ) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val scale by animateFloatAsState(if (pressed) 0.97f else 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(scale)
                        .background(
                            Brush.linearGradient(listOf(auroraAccent(), auroraAccent().darkenAuth(0.75f))),
                            RoundedCornerShape(17.dp)
                        )
                        .clickable(interactionSource = interaction, indication = null) { onContinuePhone() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Email,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color.White
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Continue with email",
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                GoogleAuthButton(onClick = onContinueGoogle)
            }

            // ---- login link + terms ----
            Column(
                modifier = Modifier.graphicsLayer { alpha = footIn },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "Already have an account? ",
                        fontSize = 12.5.sp,
                        color = OnAurora().copy(alpha = 0.55f)
                    )
                    Text(
                        "Log in",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = auroraGoldAccent(),
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "By continuing, you agree to PRC Jobs' Terms and Privacy Policy",
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    color = OnAurora().copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
