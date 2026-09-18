package com.prc.app.ui.screens.auth

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ---------- shared motion + tokens ----------

val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

val InkDark = Color(0xFF0B100D)   // green-tinted near-black (dark aurora base)
val AuroraGreen = Color(0xFF2E8B63)   // luminous emerald (dark aurora accent)
val AuroraGold = Color(0xFFD9A324)
val AuroraMint = Color(0xFFD9A324)    // gold accent

// ---------- aurora theme tokens (light ivory / dark ink) ----------

/** True when the immersive aurora screens should render their dark variant. */
@Composable
fun auroraIsDark(): Boolean = androidx.compose.foundation.isSystemInDarkTheme()

/** Aurora backdrop base: near-black ink in dark mode, warm ivory in light. */
@Composable
fun auroraBase(): Color = if (auroraIsDark()) InkDark else Color(0xFFFAF9F6)

/** Primary text/glows on the aurora backdrop. */
@Composable
fun OnAurora(): Color = if (auroraIsDark()) Color.White else Color(0xFF1A1A18)

/** Accent for glow blobs and brand tiles on the aurora backdrop. */
@Composable
fun auroraAccent(): Color = if (auroraIsDark()) AuroraGreen else Color(0xFF0E7A55)

/**
 * Accent that stays readable on the backdrop. Dark mode uses a soft warm
 * grey (no gold shine — pure AMOLED look); light mode uses deep gold.
 */
@Composable
fun auroraGoldAccent(): Color = if (auroraIsDark()) Color(0xFFE8BC55) else Color(0xFFB6851A)

/** Glow-blob color for the dark backdrop: faint grey sheen, never gold. */
@Composable
private fun auroraGlowDark(): Color = Color(0xFF1E3A2D)

/** Translucent surface/border layer on the backdrop (glass cards, fields). */
@Composable
fun auroraSurface(alpha: Float): Color =
    if (auroraIsDark()) Color.White.copy(alpha = alpha)
    else Color(0xFF141414).copy(alpha = (alpha * 1.9f).coerceAtMost(0.28f))

/** One-shot entrance: fade + rise + (optional) spring pop. */
@Composable
fun rememberEntrance(key: Any? = Unit, delayMillis: Int = 0, pop: Boolean = false): Float {
    val anim = remember(key) { Animatable(if (pop) 0.6f else 0f) }
    LaunchedEffect(key) {
        delay(delayMillis.toLong())
        anim.animateTo(
            1f,
            animationSpec = if (pop) spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ) else tween(650, easing = EaseOutExpo)
        )
    }
    return anim.value
}

/** Gentle forever-floating value for decorative elements. */
@Composable
fun rememberFloatCycle(from: Float, to: Float, durationMillis: Int = 4200): Float {
    val t = rememberInfiniteTransition(label = "floatCycle")
    val v by t.animateFloat(
        initialValue = from,
        targetValue = to,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing), RepeatMode.Reverse),
        label = "floatCycleValue"
    )
    return v
}

/**
 * Aurora backdrop: deep-ink base, three slow-drifting blurred glow blobs
 * (green / gold / mint) and a fine dot grid for texture. Used behind auth
 * screens; splash/onboarding use richer variants of the same idea.
 */
@Composable
fun AuroraBackdrop(
    modifier: Modifier = Modifier,
    base: Color? = null,
    dotAlpha: Float = 0.10f
) {
    val t = rememberInfiniteTransition(label = "aurora")
    val drift1 by t.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift1"
    )
    val drift2 by t.animateFloat(
        initialValue = 1f, targetValue = -1f,
        animationSpec = infiniteRepeatable(tween(11000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift2"
    )
    val drift3 by t.animateFloat(
        initialValue = 0.6f, targetValue = -0.6f,
        animationSpec = infiniteRepeatable(tween(13000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift3"
    )

    val accent = auroraAccent()
    val gold = auroraGoldAccent()
    val dark = auroraIsDark()
    val glowDark = auroraGlowDark()

    Box(modifier.background(base ?: auroraBase())) {
        GlowBlob(
            color = accent, alpha = if (dark) 0.30f else 0.20f, sizeDp = 520.dp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-140).dp + 100.dp * drift1, y = (-160).dp + 40.dp * drift1)
        )
        GlowBlob(
            color = if (dark) glowDark else gold, alpha = if (dark) 0.16f else 0.16f, sizeDp = 480.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp * drift2, y = (-40).dp + 120.dp * -drift2 + 60.dp)
        )
        GlowBlob(
            color = if (dark) glowDark else gold, alpha = if (dark) 0.14f else 0.14f, sizeDp = 500.dp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-120).dp + 70.dp * drift3, y = (-140).dp + 50.dp * drift3)
        )

        // fine dot grid texture
        Box(
            Modifier
                .fillMaxSize()
                .alpha(dotAlpha)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to (if (auroraIsDark()) Color.White else Color(0xFF141414)).copy(alpha = 0.06f),
                        1f to Color.Transparent
                    )
                )
        )
    }
}

/** Soft radial glow disc — smooth falloff on every API level (no RenderEffect). */
@Composable
fun GlowBlob(color: Color, alpha: Float, sizeDp: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(sizeDp)
            .background(
                Brush.radialGradient(
                    listOf(color.copy(alpha = alpha), Color.Transparent)
                ),
                CircleShape
            )
    )
}

/** Brand mark: gradient tile + soft glow ring + briefcase glyph, pop-in on mount. */
@Composable
fun AuthMark(modifier: Modifier = Modifier, size: Int = 54) {
    val pop = rememberEntrance(pop = true)
    val glow = rememberFloatCycle(0.9f, 1f, 2600)
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pop; scaleY = pop
                alpha = pop.coerceIn(0f, 1f)
            }
            .size((size + 18).dp),
        contentAlignment = Alignment.Center
    ) {
        // breathing glow
        Box(
            Modifier
                .size((size + 16).dp)
                .alpha(0.35f * glow)
                .background(
                    Brush.radialGradient(
                        listOf(
                            (if (auroraIsDark()) Color(0xFF3A3A38) else auroraGoldAccent())
                                .copy(alpha = 0.5f), Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        Box(
            Modifier
                .size(size.dp)
                .background(
                    Brush.linearGradient(listOf(auroraAccent(), auroraAccent().darkenAuth(0.75f))),
                    RoundedCornerShape((size * 0.28f).dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Work,
                contentDescription = null,
                modifier = Modifier.size((size * 0.5f).dp),
                tint = Color.White
            )
        }
    }
}

/** Small circular back button with press-scale feedback. */
@Composable
fun AuthBackCircle(onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f)
    Box(
        modifier = Modifier
            .scale(scale)
            .size(40.dp)
            .background(auroraSurface(0.06f), CircleShape)
            .border(1.dp, OnAurora().copy(alpha = 0.14f), CircleShape)
            .clickable(interactionSource = interaction, indication = null) { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(17.dp),
            tint = Color.White.copy(alpha = 0.9f)
        )
    }
}

/** Uppercase field label. */
@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = OnAurora().copy(alpha = 0.55f),
        modifier = modifier.padding(bottom = 7.dp)
    )
}

/**
 * Glow input: rises in on its entrance key, border + outer glow brighten on
 * focus. Field content is always light-on-dark per the premium kit.
 */
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
    entranceKey: Any? = Unit,
    entranceDelay: Int = 0
) {
    var focused by remember { mutableStateOf(false) }
    val entrance = rememberEntrance(key = entranceKey, delayMillis = entranceDelay)
    val focusColor by animateColorAsState(
        if (focused) auroraGoldAccent() else OnAurora().copy(alpha = 0.22f)
    )

    Column(
        modifier = modifier.graphicsLayer {
            alpha = entrance
            translationY = (1f - entrance) * 34f
        }
    ) {
        FieldLabel(label)
        Box {
            // outer glow when focused (soft radial — no RenderEffect needed)
            if (focused) {
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(auroraGoldAccent().copy(alpha = 0.30f), Color.Transparent)
                            ),
                            RoundedCornerShape(20.dp)
                        )
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                leadingIcon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = if (focused) auroraGoldAccent() else OnAurora().copy(alpha = 0.6f)
                    )
                },
                trailingIcon = trailing,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = OnAurora(),
                    fontSize = 14.5.sp
                ),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = auroraSurface(0.05f),
                    focusedContainerColor = auroraSurface(0.08f),
                    unfocusedBorderColor = OnAurora().copy(alpha = 0.18f),
                    focusedBorderColor = focusColor,
                    cursorColor = auroraGoldAccent(),
                    focusedTextColor = OnAurora(),
                    unfocusedTextColor = OnAurora()
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusEvent { focused = it.isFocused }
            )
        }
    }
}

/** "OR" hairline divider. */
@Composable
fun OrDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = OnAurora().copy(alpha = 0.14f), thickness = 1.dp)
        Text(
            "  or  ",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = OnAurora().copy(alpha = 0.5f)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = OnAurora().copy(alpha = 0.14f), thickness = 1.dp)
    }
}

/** Outlined "Continue with Google" with the real four-color glyph. */
@Composable
fun GoogleAuthButton(text: String = "Continue with Google", onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f)
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OnAurora().copy(alpha = 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = auroraSurface(0.04f),
            contentColor = OnAurora()
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
    ) {
        GoogleGlyph()
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = OnAurora()
        )
    }
}

/** The authentic four-color Google "G" drawn as paths. */
@Composable
fun GoogleGlyph(size: Int = 17) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size.dp)) {
        val s = this.size.width
        fun px(f: Float) = f * s
        // blue
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(px(0.9509f), px(0.4993f))
                cubicTo(px(0.9509f), px(0.4616f), px(0.9474f), px(0.4252f), px(0.9409f), px(0.3899f))
                lineTo(px(0.5f), px(0.3899f))
                lineTo(px(0.5f), px(0.6149f))
                lineTo(px(0.7926f), px(0.6149f))
                lineTo(px(0.7791f), px(0.6667f))
                quadraticBezierTo(px(0.7592f), px(0.7458f), px(0.6924f), px(0.8068f))
                lineTo(px(0.6924f), px(0.9323f))
                lineTo(px(0.8311f), px(0.9323f))
                quadraticBezierTo(px(0.9137f), px(0.8581f), px(0.9509f), px(0.7492f))
                quadraticBezierTo(px(0.9733f), px(0.6823f), px(0.9509f), px(0.6155f))
                close()
            },
            color = Color(0xFF4285F4)
        )
        // green
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(px(0.5f), px(0.95f))
                cubicTo(px(0.6516f), px(0.95f), px(0.7792f), px(0.9023f), px(0.8698f), px(0.8209f))
                lineTo(px(0.7311f), px(0.7135f))
                quadraticBezierTo(px(0.6642f), px(0.7584f), px(0.5f), px(0.7584f))
                quadraticBezierTo(px(0.3384f), px(0.7584f), px(0.2352f), px(0.6494f))
                lineTo(px(0.0919f), px(0.6494f))
                lineTo(px(0.0919f), px(0.7794f))
                quadraticBezierTo(px(0.2059f), px(0.9797f), px(0.5f), px(0.95f))
                close()
            },
            color = Color(0xFF34A853)
        )
        // yellow
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(px(0.2352f), px(0.6494f))
                quadraticBezierTo(px(0.2066f), px(0.5751f), px(0.2066f), px(0.5f))
                quadraticBezierTo(px(0.2066f), px(0.4249f), px(0.2352f), px(0.3506f))
                lineTo(px(0.2352f), px(0.2206f))
                lineTo(px(0.0919f), px(0.2206f))
                quadraticBezierTo(px(0.02f), px(0.3572f), px(0.02f), px(0.5f))
                quadraticBezierTo(px(0.02f), px(0.6428f), px(0.0919f), px(0.7794f))
                close()
            },
            color = Color(0xFFFBBC05)
        )
        // red
        drawPath(
            androidx.compose.ui.graphics.Path().apply {
                moveTo(px(0.5f), px(0.2416f))
                quadraticBezierTo(px(0.6101f), px(0.2416f), px(0.6879f), px(0.3156f))
                lineTo(px(0.8068f), px(0.1967f))
                quadraticBezierTo(px(0.7323f), px(0.1267f), px(0.5f), px(0.05f))
                quadraticBezierTo(px(0.2059f), px(0.05f), px(0.0919f), px(0.2206f))
                lineTo(px(0.2352f), px(0.3506f))
                quadraticBezierTo(px(0.3384f), px(0.2416f), px(0.5f), px(0.2416f))
                close()
            },
            color = Color(0xFFEA4335)
        )
    }
}

/** Primary full-width action with sheen sweep, press-scale and loading state. */
@Composable
fun PrimaryAuthButton(
    text: String,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f)
    val sheen = rememberFloatCycle(-1.2f, 1.2f, 2800)

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) auroraAccent() else auroraAccent().copy(alpha = 0.45f),
            contentColor = Color.White,
            disabledContainerColor = auroraAccent().copy(alpha = 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(text, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                if (enabled) {
                    // moving sheen highlight
                    Box(
                        Modifier
                            .offset(x = 300.dp * sheen, y = 0.dp)
                            .size(width = 40.dp, height = 54.dp)
                            .alpha(0.16f)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White, Color.Transparent)
                                )
                            )
                    )
                }
            }
        }
    }
}

/** Footer link with a bold action. */
@Composable
fun BottomAuthLink(prefix: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(1f))
        Text(
            prefix,
            fontSize = 12.5.sp,
            color = OnAurora().copy(alpha = 0.55f),
            textAlign = TextAlign.End
        )
        Text(
            action,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = auroraGoldAccent(),
            modifier = Modifier
                .padding(start = 3.dp)
                .clickable { onClick() }
        )
        Spacer(Modifier.weight(1f))
    }
}

/** Section headline pair in the premium style (serif display + soft body). */
@Composable
fun AuthHeadline(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            title,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = OnAurora(),
            lineHeight = 36.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            fontSize = 13.5.sp,
            lineHeight = 20.sp,
            color = OnAurora().copy(alpha = 0.6f)
        )
    }
}

/** Small helper: darken a color by a factor. */
fun Color.darkenAuth(f: Float): Color = Color(
    red = red * f, green = green * f, blue = blue * f, alpha = alpha
)
