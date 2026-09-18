package com.prc.app.ui.screens.onboarding

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.border
import com.prc.app.ui.screens.auth.AuroraBackdrop
import com.prc.app.ui.screens.auth.OnAurora
import com.prc.app.ui.screens.auth.auroraAccent
import com.prc.app.ui.screens.auth.auroraGoldAccent
import com.prc.app.ui.screens.auth.auroraIsDark
import com.prc.app.ui.screens.auth.auroraSurface
import com.prc.app.ui.screens.auth.EaseOutExpo
import com.prc.app.ui.screens.auth.rememberEntrance
import com.prc.app.ui.screens.auth.rememberFloatCycle
import kotlinx.coroutines.launch

private data class Slide(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val chips: List<Pair<String, ImageVector>>,
    val accent: Color
)

private val slides = listOf(
    Slide(
        icon = Icons.Filled.Work,
        title = "Find work near you",
        body = "Browse jobs posted every day — from gigs to full-time roles, right in your area.",
        chips = listOf("Nakuru" to Icons.Filled.LocationOn, "12 matches" to Icons.Filled.Bolt),
        accent = Color(0xFFD9A324)
    ),
    Slide(
        icon = Icons.Filled.Search,
        title = "Search that fits you",
        body = "Filter by category, location and pay. One tap to apply and track your applications.",
        chips = listOf("KSh 1,500/day" to Icons.Filled.Paid, "Gig" to Icons.Filled.Bolt),
        accent = Color(0xFFB6851A)
    ),
    Slide(
        icon = Icons.AutoMirrored.Filled.Send,
        title = "Post your own jobs",
        body = "Need help with something? Post it in seconds and review applicants as they come in.",
        chips = listOf("3 applicants" to Icons.Filled.Bolt, "New" to Icons.Filled.LocationOn),
        accent = Color(0xFF9EC5FF)
    )
)

@Composable
fun OnboardingScreen(
    onDone: () -> Unit
) {
        SystemBarAppearance(darkIcons = !auroraIsDark())

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == slides.size - 1

    val buttonLabel = if (isLast) "Get started" else "Next"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(Modifier.weight(1f)) {
            AuroraBackdrop(
                modifier = Modifier.fillMaxSize(),
                dotAlpha = 0.16f
            )

            Column(Modifier.fillMaxSize()) {
                // ---- Skip ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "Skip",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnAurora().copy(alpha = 0.55f),
                        modifier = Modifier
                            .clickable { onDone() }
                            .padding(8.dp)
                    )
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { page ->
                    val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    SlideContent(
                        slide = slides[page],
                        pageShown = pagerState.currentPage == page || pagerState.targetPage == page,
                        parallax = pageOffset
                    )
                }

                // ---- dots: expanding pill with spring ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(slides.size) { i ->
                        val selected = pagerState.currentPage == i
                        val width by animateDpAsState(
                            targetValue = if (selected) 26.dp else 7.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                            label = "dotW"
                        )
                        val color by animateColorAsState(
                            if (selected) OnAurora() else OnAurora().copy(alpha = 0.28f),
                            label = "dotC"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(width = width, height = 7.dp)
                                .background(color, RoundedCornerShape(4.dp))
                        )
                    }
                }

                // ---- CTA with press feedback ----
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val btnScale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "btnS")
                val btnColor by animateColorAsState(
                    if (isLast) auroraGoldAccent() else auroraAccent(),
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "btnC"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 30.dp)
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(btnScale)
                        .background(
                            Brush.linearGradient(
                                listOf(btnColor, btnColor.darken(0.82f))
                            ),
                            RoundedCornerShape(17.dp)
                        )
                        .clickable(interactionSource = interaction, indication = null) {
                            if (!isLast) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                onDone()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.animation.Crossfade(targetState = buttonLabel, label = "ctaLabel") { label ->
                            Text(
                                label,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlideContent(
    slide: Slide,
    pageShown: Boolean,
    parallax: Float
) {
    val floatY = rememberFloatCycle(-9f, 9f, 4600)
    val floatY2 = rememberFloatCycle(8f, -8f, 3800)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .graphicsLayer { translationX = parallax * 60f },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ---- illustration: layered glass card with floating chips ----
        val cardIn = rememberEntrance(key = slide.title, pop = false)
        // in dark mode the slide accent is dimmed to a neutral sheen (pure AMOLED)
        val slideGlow = if (auroraIsDark()) Color(0xFF3A3A38) else slide.accent
        Box(contentAlignment = Alignment.Center) {
            // back glow
            Box(
                Modifier
                    .size(230.dp)
                    .alpha(0.35f)
                    .background(
                        Brush.radialGradient(listOf(slideGlow.copy(alpha = 0.4f), Color.Transparent)),
                        CircleShape
                    )
            )
            // glass card
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = cardIn.coerceIn(0f, 1f)
                        translationY = (1f - cardIn) * 40f
                    }
                    .size(210.dp)
                    .clip(RoundedCornerShape(34.dp))
                    .background(auroraSurface(0.06f))
                    .border(1.dp, OnAurora().copy(alpha = 0.16f), RoundedCornerShape(34.dp)),
                contentAlignment = Alignment.Center
            ) {
                // gradient wash inside the card
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    slideGlow.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    slide.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .offset(y = floatY.dp)
                        .size(72.dp),
                    tint = if (auroraIsDark()) Color.White else MaterialTheme.colorScheme.primary
                )
            }
            // floating chip: top-right
            if (slide.chips.isNotEmpty()) {
                GlassChip(
                    text = slide.chips[0].first,
                    icon = slide.chips[0].second,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-2).dp, y = (26 + floatY2 * 0.7f).dp)
                )
            }
            // floating chip: bottom-left
            if (slide.chips.size > 1) {
                GlassChip(
                    text = slide.chips[1].first,
                    icon = slide.chips[1].second,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = 6.dp, y = (-18 + floatY * 0.6f).dp)
                )
            }
        }

        Spacer(Modifier.height(42.dp))

        // ---- text block, staggered on first entry ----
        val titleIn = rememberEntrance(key = slide.title, delayMillis = 120)
        val bodyIn = rememberEntrance(key = slide.title, delayMillis = 240)
        Text(
            text = slide.title,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            color = OnAurora(),
            lineHeight = 34.sp,
            modifier = Modifier.graphicsLayer {
                alpha = titleIn
                translationY = (1f - titleIn) * 30f
            }
        )
        Spacer(Modifier.height(13.dp))
        Text(
            text = slide.body,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center,
            color = OnAurora().copy(alpha = 0.62f),
            modifier = Modifier.graphicsLayer {
                alpha = bodyIn
                translationY = (1f - bodyIn) * 22f
            }
        )
    }
}

@Composable
private fun GlassChip(text: String, icon: ImageVector, modifier: Modifier = Modifier) {        Row(
            modifier = modifier
                .clip(RoundedCornerShape(13.dp))
                .background(auroraSurface(0.1f))
                .border(1.dp, OnAurora().copy(alpha = 0.2f), RoundedCornerShape(13.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = auroraGoldAccent())
            Spacer(Modifier.width(5.dp))
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = OnAurora())
        }
}

private fun Color.darken(f: Float): Color = Color(
    red = red * f, green = green * f, blue = blue * f, alpha = alpha
)
