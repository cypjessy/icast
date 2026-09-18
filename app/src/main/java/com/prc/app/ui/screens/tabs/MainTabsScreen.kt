package com.prc.app.ui.screens.tabs

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import com.prc.app.data.AuthRepository
import com.prc.app.ui.screens.applications.ApplicationsScreen
import com.prc.app.ui.screens.auth.AuroraGreen
import com.prc.app.ui.screens.home.HomeFeedScreen
import com.prc.app.ui.screens.profile.ProfileScreen
import com.prc.app.ui.screens.saved.SavedScreen
import com.prc.app.ui.screens.search.SearchScreen

private data class Tab(
    val route: String,
    val label: String,
    val filled: androidx.compose.ui.graphics.vector.ImageVector,
    val outlined: androidx.compose.ui.graphics.vector.ImageVector
)

private val NavInactive = Color(0xFF8A978F)

@Composable
fun MainTabsScreen(navController: NavHostController, initialTab: String = "home") {
    val tabs = listOf(
        Tab("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
        Tab("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
        Tab("saved", "Saved", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
        Tab("applications", "Applications", Icons.Filled.Work, Icons.Outlined.Work),
        Tab("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
    )
    var currentTab by remember { mutableStateOf(initialTab) }

    // All tabs are light-themed pages now: dark icons in light mode, light
    // icons in dark mode (transparent bars show the page background through).
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(currentTab, darkTheme) {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, w.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            PremiumBottomBar(tabs = tabs, current = currentTab, onSelect = { currentTab = it })
        },
        floatingActionButton = {
            if (currentTab == "home") {
                PremiumPostFab(onClick = { navController.navigate("post_job") })
            }
        },
        floatingActionButtonPosition = androidx.compose.material3.FabPosition.End
    ) { padding ->
        Crossfade(
            targetState = currentTab,
            animationSpec = tween(240),
            label = "tabCrossfade"
        ) { tab ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    "home" -> HomeFeedScreen(
                        onJobClick = { id -> navController.navigate("job/$id") },
                        onSearchClick = { currentTab = "search" },
                        onNotificationsClick = { navController.navigate("notifications") },
                        onEditProfileClick = { navController.navigate("profile_edit") },
                        onSeeApplicationsClick = { currentTab = "applications" },
                        onCompanyClick = { name -> navController.navigate("company/" + android.net.Uri.encode(name)) }
                    )
                    "search" -> SearchScreen(
                        onJobClick = { id -> navController.navigate("job/$id") },
                        onUpgradeToPro = {
                            com.prc.app.data.PlanAnalytics.recordUpgradeAttempt("alert_cap")
                            navController.navigate(com.prc.app.navigation.Routes.proPlans("alert_cap"))
                        }
                    )
                    "saved" -> SavedScreen(
                        onJobClick = { id -> navController.navigate("job/$id") },
                        onBrowse = { currentTab = "search" }
                    )
                    "applications" -> ApplicationsScreen(
                        onJobClick = { id -> navController.navigate("job/$id") }
                    )
                    "profile" -> ProfileScreen(
                        onLogout = {
                            AuthRepository.logout()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onOpenMyJobs = { navController.navigate("my_jobs") },
                        onOpenPostJob = { navController.navigate("post_job") },
                        onEditProfile = { navController.navigate("profile_edit") },
                        onOpenPayments = { navController.navigate("payments_history") },
                        onOpenProPlans = { navController.navigate(com.prc.app.navigation.Routes.proPlans("profile")) },
                        onOpenAdminPortal = { navController.navigate("admin_login") }
                    )
                }
            }
        }
    }
}

/** Custom bottom bar: white surface, hairline top, animated selection pill. */
@Composable
private fun PremiumBottomBar(
    tabs: List<Tab>,
    current: String,
    onSelect: (String) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val selected = current == tab.route
                val pillColor by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "pillBg"
                )
                val iconTint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else NavInactive,
                    label = "iconTint"
                )
                val labelTint by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.onSurface else NavInactive,
                    label = "labelTint"
                )
                val pressScale by animateFloatAsState(
                    if (selected) 1.05f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "tabScale"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .width(50.dp)
                            .height(28.dp)
                            .scale(pressScale)
                            .background(pillColor, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (selected) tab.filled else tab.outlined,
                            contentDescription = tab.label,
                            modifier = Modifier.size(19.dp),
                            tint = iconTint
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                        color = labelTint,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

/** Gradient "Post a job" FAB with green-tinted shadow and press feedback. */
private fun Color.darken(f: Float): Color = Color(red * f, green * f, blue * f, alpha)

@Composable
private fun PremiumPostFab(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "fabScale")
    Row(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            )
            .background(
                Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.darken(0.75f))),
                RoundedCornerShape(18.dp)
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Post a job",
            modifier = Modifier.size(18.dp),
            tint = Color.White
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "Post a job",
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}
