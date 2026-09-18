package com.prc.app.ui.screens.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.NamedShare

// =====================================================================
// WIDGET CARD SHELL
// =====================================================================

/**
 * Shared card shell for all overview widgets: paper surface, rounded corners,
 * optional icon + title header and a trailing slot (e.g. "last 7 days").
 */
@Composable
fun AdminWidgetCard(
    title: String,
    icon: ImageVector? = null,
    trailing: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val AC = AdminColors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = AC.Card,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Surface(shape = CircleShape, color = AC.GreenSoft) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = AC.Green,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(13.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.InkStrong,
                    modifier = Modifier.weight(1f)
                )
                if (trailing != null) {
                    Text(trailing, fontSize = 10.5.sp, color = AC.InkSoft)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// =====================================================================
// AREA CHART — weekly posting trend (community vs provider)
// =====================================================================

/** Two-series trend line with a soft fill under the community series. */
@Composable
fun AdminAreaChart(
    values: List<Pair<Int, Int>>,   // (community, provider) per bucket, oldest first
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val AC = AdminColors
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "adminAreaProgress"
    )

    Column(modifier.fillMaxWidth()) {
        // legend
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(AC.Green, "Community")
            LegendDot(AC.Gold, "Provider")
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            if (values.size < 2) return@Canvas
            val stepX = size.width / (values.size - 1)
            val maxV = maxOf(values.maxOf { it.first + it.second }, 1)
            val topPad = size.height * 0.08f
            val usable = size.height - topPad

            fun pointY(v: Float) = topPad + usable - (v / maxV) * usable

            fun animatedY(v: Float) = size.height - (size.height - pointY(v)) * progress

            // gridlines
            for (i in 0..3) {
                val gy = topPad + usable * i / 3f
                drawLine(
                    color = AC.Hairline,
                    start = Offset(0f, gy),
                    end = Offset(size.width, gy),
                    strokeWidth = 1.dp.toPx()
                )
            }

            val communityPts = values.mapIndexed { i, (c, _) ->
                Offset(i * stepX, animatedY(c.toFloat()))
            }
            val totalPts = values.mapIndexed { i, (c, p) ->
                Offset(i * stepX, animatedY((c + p).toFloat()))
            }

            fun smoothPath(pts: List<Offset>): Path {
                val path = Path()
                path.moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val prev = pts[i - 1]
                    val cur = pts[i]
                    val midX = (prev.x + cur.x) / 2f
                    path.quadraticBezierTo(midX, prev.y, cur.x, cur.y)
                }
                return path
            }

            // soft fill under community line
            val fillPath = smoothPath(communityPts).apply {
                lineTo(values.lastIndex * stepX, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                fillPath,
                brush = Brush.verticalGradient(
                    listOf(AC.Green.copy(alpha = 0.22f), Color.Transparent),
                    startY = 0f,
                    endY = size.height
                )
            )

            drawPath(
                smoothPath(communityPts),
                color = AC.Green,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawPath(
                smoothPath(totalPts),
                color = AC.Gold,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // end-point markers
            totalPts.lastOrNull()?.let {
                drawCircle(AC.Gold, radius = 3.5.dp.toPx(), center = it)
                drawCircle(Color.White, radius = 1.6.dp.toPx(), center = it)
            }
            communityPts.lastOrNull()?.let {
                drawCircle(AC.Green, radius = 3.5.dp.toPx(), center = it)
                drawCircle(Color.White, radius = 1.6.dp.toPx(), center = it)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    fontSize = 9.5.sp,
                    color = AC.InkSoft,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val AC = AdminColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 10.5.sp, color = AC.InkSoft)
    }
}

// =====================================================================
// HORIZONTAL BAR LIST — top categories
// =====================================================================

/** Ranked horizontal bars with animated fill and per-row counts. */
@Composable
fun AdminBarChart(
    data: List<NamedShare>,
    modifier: Modifier = Modifier,
    barColor: Color? = null
) {
    val AC = AdminColors
    val bar = barColor ?: AC.Green
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "adminBarProgress"
    )

    val maxCount = maxOf(data.maxOfOrNull { it.count } ?: 1, 1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        data.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    fontSize = 11.5.sp,
                    color = AdminColors.InkStrong,
                    modifier = Modifier.width(86.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(9.dp)
                        .background(AC.Hairline.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.count.toFloat() / maxCount * progress)
                            .height(9.dp)
                            .background(
                                Brush.horizontalGradient(listOf(bar, AC.Mint)),
                                RoundedCornerShape(5.dp)
                            )
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "${item.count}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AC.Green,
                    modifier = Modifier.width(20.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// =====================================================================
// DONUT — live job composition
// =====================================================================



data class DonutSlice(val label: String, val count: Int)

/** Donut chart with centre total and a compact legend below. */
@Composable
fun AdminDonut(
    slices: List<DonutSlice>,
    centerLabel: String,
    modifier: Modifier = Modifier
) {
    val AC = AdminColors
    val DonutPalette = listOf(
        AC.Green, AC.Mint, AC.Gold, Color(0xFF8FB8A8),
        AC.InkSoft.copy(alpha = 0.45f), AC.Hairline
    )
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 750),
        label = "adminDonutProgress"
    )

    val total = slices.sumOf { it.count }.coerceAtLeast(1)

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(112.dp)) {
            Canvas(Modifier.size(112.dp)) {
                val stroke = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Butt)
                val inset = stroke.width / 2 + 1.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = AC.Hairline.copy(alpha = 0.6f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke
                )
                var start = -90f
                slices.forEachIndexed { i, slice ->
                    val sweep = slice.count.toFloat() / total * 360f * progress
                    if (sweep > 0.5f) {
                        drawArc(
                            color = DonutPalette[i % DonutPalette.size],
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$total",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.InkStrong
                )
                Text(centerLabel, fontSize = 9.5.sp, color = AC.InkSoft)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(DonutPalette[i % DonutPalette.size], CircleShape)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        slice.label,
                        fontSize = 11.sp,
                        color = AdminColors.InkStrong,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${slice.count}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AC.InkSoft
                    )
                }
            }
        }
    }
}

// =====================================================================
// MINI GAUGE — approval rate
// =====================================================================

/** Half-donut gauge showing the approval decision rate. */
@Composable
fun AdminMiniGauge(
    percent: Int?,          // null → no decisions yet
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val AC = AdminColors
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
        label = "adminGaugeProgress"
    )

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.height(64.dp)) {
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val strokeW = 13.dp.toPx()
                val diameter = size.width.coerceAtMost(size.height * 2) - strokeW
                val arcSize = Size(diameter, diameter)
                val topLeft = Offset((size.width - diameter) / 2, 0f)
                drawArc(
                    color = AC.Hairline.copy(alpha = 0.7f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokeW, cap = StrokeCap.Round)
                )
                if (percent != null) {
                    drawArc(
                        brush = Brush.horizontalGradient(
                            listOf(AC.Green, AC.Mint)
                        ),
                        startAngle = 180f,
                        sweepAngle = 180f * (percent / 100f) * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(strokeW, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                percent?.let { "$it%" } ?: "—",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AdminColors.InkStrong,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Text(
            "approval rate",
            fontSize = 10.5.sp,
            color = AC.InkSoft
        )
        Text(
            if (pendingCount > 0) "$pendingCount pending now" else "queue clear",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (pendingCount > 0) AC.Gold else AC.Green,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

// =====================================================================
// MINI TABLE — provider performance
// =====================================================================

/** Compact data table: bold first column, centred numeric columns, hairline dividers. */
@Composable
fun AdminMiniTable(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier
) {
    val AC = AdminColors
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AC.GreenSoft, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                headers.forEachIndexed { i, h ->
                    Text(
                        h.uppercase(),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = AC.Green,
                        textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                        modifier = if (i == 0) Modifier.weight(2.2f) else Modifier.weight(1f)
                    )
                }
            }
            rows.forEachIndexed { r, cells ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    cells.forEachIndexed { i, cell ->
                        Text(
                            cell,
                            fontSize = if (i == 0) 12.sp else 11.5.sp,
                            fontWeight = if (i == 0) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (i == 0) AC.InkStrong else AC.InkSoft,
                            textAlign = if (i == 0) TextAlign.Start else TextAlign.Center,
                            maxLines = 1,
                            modifier = if (i == 0) Modifier.weight(2.2f) else Modifier.weight(1f)
                        )
                    }
                }
                if (r < rows.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AC.Hairline)
                    )
                }
            }
            if (rows.isEmpty()) {
                Text(
                    "No provider activity yet.",
                    fontSize = 11.5.sp,
                    color = AC.InkSoft,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
