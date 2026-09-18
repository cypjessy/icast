package com.prc.app.ui.screens.splash

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.ui.screens.auth.OnAurora
import com.prc.app.ui.screens.auth.auroraAccent
import com.prc.app.ui.screens.auth.auroraBase
import com.prc.app.ui.screens.auth.auroraGoldAccent
import com.prc.app.ui.screens.auth.auroraIsDark
import com.prc.app.ui.screens.auth.EaseOutExpo
import com.prc.app.ui.screens.auth.GlowBlob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Premium splash: deep-ink aurora field with three drifting glows, a halo-ringed
 * brand mark that springs in, staggered wordmark/tagline, and an expanding
 * progress line. Fades out before handing off to onboarding / get-started.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    // entrance
    val markIn = remember { Animatable(0f) }
    val wordIn = remember { Animatable(0f) }
    val tagIn = remember { Animatable(0f) }
    val lineGrow = remember { Animatable(0f) }
    // exit
    var leaving by remember { mutableStateOf(false) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        launch { markIn.animateTo(1f, tween(700, easing = EaseOutExpo)) }
        delay(220)
        launch { wordIn.animateTo(1f, tween(600, easing = EaseOutExpo)) }
        delay(120)
        launch { tagIn.animateTo(1f, tween(600, easing = EaseOutExpo)) }
        delay(180)
        launch { lineGrow.animateTo(1f, tween(900, easing = EaseOutExpo)) }
        delay(1250)
        leaving = true
        exit.animateTo(0f, tween(420))
        onFinished()
    }

    val drift = rememberInfiniteTransition(label = "splashDrift")
    val d1 by drift.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), "d1"
    )
    val d2 by drift.animateFloat(
        1f, -1f, infiniteRepeatable(tween(10500, easing = LinearEasing), RepeatMode.Reverse), "d2"
    )
    val d3 by drift.animateFloat(
        0.7f, -0.7f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), "d3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = exit.value; scaleX = 1f + (1f - exit.value) * 0.04f; scaleY = 1f + (1f - exit.value) * 0.04f }
            .background(auroraBase())
    ) {
        val accent = auroraAccent()
        val gold = auroraGoldAccent()
        val dark = auroraIsDark()
        val glowDark = if (dark) Color(0xFF3A3A38) else gold
        // ---- aurora glows ----
        GlowBlob(
            color = accent, alpha = if (dark) 0.30f else 0.22f, sizeDp = 560.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-160).dp + 130.dp * d1, y = (-180).dp + 50.dp * d1)
        )
        GlowBlob(
            color = glowDark, alpha = if (dark) 0.18f else 0.18f, sizeDp = 520.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp * d2, y = 60.dp * -d2 + 40.dp)
        )
        GlowBlob(
            color = glowDark, alpha = if (dark) 0.16f else 0.16f, sizeDp = 500.dp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-150).dp * d3.absoluteValue + (-60).dp, y = 120.dp * d3)
        )
        // vignette to keep the center readable
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, auroraBase().copy(alpha = 0.55f)),
                        radius = 1100f
                    )
                )
        )

        // ---- center brand ----
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // halo mark
            Box(contentAlignment = Alignment.Center) {
                // expanding halo ring
                val halo = rememberInfiniteTransition(label = "halo")
                val haloP by halo.animateFloat(
                    0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), "haloP"
                )
                Box(
                    Modifier
                        .size(96.dp)
                        .scale(0.8f + haloP * 0.5f)
                        .alpha((1f - haloP) * 0.35f * markIn.value)
                        .border(1.5.dp, glowDark, CircleShape)
                )
                // glow disc
                Box(
                    Modifier
                        .size(92.dp)
                        .alpha(0.5f * markIn.value)
                        .background(
                            Brush.radialGradient(listOf(glowDark.copy(alpha = 0.45f), Color.Transparent)),
                            CircleShape
                        )
                )
                // tile
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val p = markIn.value
                            scaleX = 0.6f + 0.4f * p
                            scaleY = 0.6f + 0.4f * p
                            alpha = p.coerceIn(0f, 1f)
                        }
                        .size(64.dp)
                        .background(
                            Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.75f))),
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        imageVector = Icons.Filled.Work,
                        contentDescription = "PRC logo",
                        modifier = Modifier.size(30.dp),
                        colorFilter = ColorFilter.tint(Color.White)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = "PRC Jobs",
                color = OnAurora(),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                letterSpacing = 0.5.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = wordIn.value
                    translationY = (1f - wordIn.value) * 26f
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Find work. Get hired.",
                color = OnAurora().copy(alpha = 0.65f),
                fontSize = 13.sp,
                letterSpacing = 2.2.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    alpha = tagIn.value
                    translationY = (1f - tagIn.value) * 18f
                }
            )
        }

        // ---- progress line ----
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 46.dp)
                .width(150.dp)
                .height(3.dp)
                .background(OnAurora().copy(alpha = 0.12f), RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .scale(lineGrow.value, 1f)
                    .background(
                        Brush.horizontalGradient(listOf(accent, gold, accent)),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}
