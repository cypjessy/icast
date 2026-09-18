package com.prc.app.ui.screens.admin

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.SpaceDashboard
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== palette ====================
// Theme-aware "operations console" identity: paper in light mode, AMOLED
// pure-black in dark mode. Composable accessor returns the active palette;
// the AdminColors shim below keeps the existing call sites compiling.

/**
 * Theme-aware accessor for the active admin palette. Use inside composables:
 * `val C = AdminColors; C.Paper`. Resolution happens per recomposition, so
 * toggling system dark mode re-themes the whole portal instantly.
 */
val AdminColors: AdminPalette
    @Composable get() = adminPalette()

/** Icon set for the admin portal bottom bar. */
object AdminIcons {
    val Overview = Icons.Filled.SpaceDashboard
    val Post = Icons.Filled.PostAdd
    val Approvals = Icons.Filled.HowToReg
    val Jobs = Icons.Outlined.WorkOutline
    val Audit = Icons.Filled.History
    val Payments = Icons.Filled.Payment
}

/**
 * Premium admin top bar: dark chrome under the status bar, serif wordmark,
 * optional subtitle slot on the right and trailing actions.
 */
@Composable
fun AdminTopBar(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(AdminColors.Chrome)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = AdminColors.OnChrome,
                    letterSpacing = 0.3.sp
                )
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = AdminColors.OnChromeSoft,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            trailing()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(AdminColors.Green, AdminColors.Mint.copy(alpha = 0.7f))
                    )
                )
        )
    }
}

private data class AdminTabSpec(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int?
)

/**
 * Premium admin bottom bar: dark chrome with hairline, animated pill glow and
 * press feedback. Mirrors the user-side PremiumBottomBar language.
 */
@Composable
fun AdminBottomBar(
    current: String,
    onSelect: (String) -> Unit,
    approvalsBadge: Int
) {
    val tabs = listOf(
        AdminTabSpec("overview", "Overview", AdminIcons.Overview, null),
        AdminTabSpec("post", "Post", AdminIcons.Post, null),
        AdminTabSpec("approvals", "Approvals", AdminIcons.Approvals, approvalsBadge),
        AdminTabSpec("jobs", "Jobs", AdminIcons.Jobs, null),
        AdminTabSpec("payments", "Payments", AdminIcons.Payments, null),
        AdminTabSpec("audit", "Audit", AdminIcons.Audit, null)
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(AdminColors.Chrome)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(AdminColors.ChromeHairline)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(66.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = current == tab.key
                val pillColor by animateColorAsState(
                    if (selected) AdminColors.Green.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "adminPillBg"
                )
                val iconTint by animateColorAsState(
                    if (selected) AdminColors.ChromeSelected else AdminColors.ChromeInactive,
                    label = "adminIconTint"
                )
                val labelTint by animateColorAsState(
                    if (selected) AdminColors.OnChrome else AdminColors.ChromeInactive,
                    label = "adminLabelTint"
                )
                val pressScale by animateFloatAsState(
                    if (selected) 1.04f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "adminTabScale"
                )
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { onSelect(tab.key) }
                        .padding(vertical = 8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = pillColor,
                        modifier = Modifier.scale(if (pressed) 0.92f else pressScale)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                        color = labelTint,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

/** Section heading on paper background. */
@Composable
fun AdminSectionTitle(text: String, trailingText: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
    ) {
        Text(
            text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            color = AdminColors.InkStrong,
            modifier = Modifier.weight(1f)
        )
        if (trailingText != null) {
            Text(
                trailingText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = AdminColors.InkSoft
            )
        }
    }
}

/** Small status chip (e.g. New / Published / Rejected / Pending). */
@Composable
fun AdminStatusChip(text: String) {
    val (bg, fg) = when (text) {
        "New", "Pending" -> AdminColors.GoldSoft to AdminColors.Gold
        "Shortlisted" -> AdminColors.VioletTint to AdminColors.Violet
        "Published", "Approved", "Accepted", "Live", "Active" -> AdminColors.GreenSoft to AdminColors.Green
        "Rejected", "Closed", "Flagged" -> AdminColors.RedSoft to AdminColors.Red
        else -> AdminColors.Hairline to AdminColors.InkSoft
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Paper list row used across inbox/approvals/jobs: card surface, leading
 * colored monogram/avatar slot, title + sub lines, optional trailing chip.
 */
@Composable
fun AdminListRow(
    monogram: String,
    monogramTint: Color,
    title: String,
    subtitle: String,
    chipText: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = AdminColors.InkSoft,
            modifier = Modifier.size(16.dp)
        )
    }
) {        Surface(
            shape = RoundedCornerShape(14.dp),
            color = AdminColors.Card,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = onClick != null) { onClick?.invoke() }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(13.dp)
            ) {
                Surface(shape = RoundedCornerShape(10.dp), color = monogramTint) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
                        Text(
                            monogram,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = AdminColors.InkStrong,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        color = AdminColors.InkSoft,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (chipText != null) AdminStatusChip(chipText)
                trailing()
            }
        }
}

/** Empty-state block for list pages. */
@Composable
fun AdminEmptyState(emoji: String, title: String, body: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp)
    ) {
        Text(emoji, fontSize = 40.sp, modifier = Modifier.alpha(0.9f))
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = AdminColors.InkStrong
        )
        Text(
            body,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            color = AdminColors.InkSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}
