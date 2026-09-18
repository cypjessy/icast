package com.prc.app.ui.screens.profile

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.EducationEntry
import com.prc.app.data.ExperienceEntry
import com.prc.app.data.JobRepository
import com.prc.app.data.LanguageEntry
import com.prc.app.data.SavedRepository
import com.prc.app.data.User
import com.prc.app.data.hasAnyExtra
import com.prc.app.ui.screens.home.profileCompletion


private val PROFICIENCY_LEVELS = listOf("Basic", "Conversational", "Fluent", "Native")

@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenMyJobs: () -> Unit = {},
    onOpenPostJob: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenPayments: () -> Unit = {},
    onOpenProPlans: () -> Unit = {},
    onOpenAdminPortal: () -> Unit = {}
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val user by AuthRepository.currentUser.collectAsState()
    val applications by JobRepository.applications.collectAsState()
    val jobs by JobRepository.jobs.collectAsState()
    val savedIds by SavedRepository.saved.collectAsState()

    val myApplications = applications.count { it.applicantContact == user?.contact }
    val myJobs = jobs.count { it.posterContact == user?.contact }
    val pct = profileCompletion(user?.location, user?.skills, user?.bio, user?.hasAnyExtra() == true)

    // dialog state
    var showExp by remember { mutableStateOf(false) }
    var expInitial by remember { mutableStateOf<ExperienceEntry?>(null) }
    var showEdu by remember { mutableStateOf(false) }
    var eduInitial by remember { mutableStateOf<EducationEntry?>(null) }
    var showLang by remember { mutableStateOf(false) }
    var langInitial by remember { mutableStateOf<LanguageEntry?>(null) }
    var showCert by remember { mutableStateOf(false) }
    var certInitial by remember { mutableStateOf<String?>(null) }
    var showCv by remember { mutableStateOf(false) }
    var showNotifPrefs by remember { mutableStateOf(false) }
    var showAccountSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showMyAlerts by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Pro subscription status (live from Firestore users/{uid}.proUntil)
    var proUntil by remember { mutableStateOf(0L) }
    LaunchedEffect(user) {
        proUntil = com.prc.app.data.AuthRepository.currentUid()
            ?.let { com.prc.app.data.PaymentsRepository.proUntil(it) } ?: 0L
    }
    val proActive = proUntil > System.currentTimeMillis()
    val proUntilText = remember(proUntil) {
        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(proUntil))
    }

    fun save(transformed: User) {
        AuthRepository.updateProfile(
            location = transformed.location,
            skills = transformed.skills,
            bio = transformed.bio,
            experience = transformed.experience,
            education = transformed.education,
            certifications = transformed.certifications,
            languages = transformed.languages,
            cvName = transformed.cvName,
            phone = transformed.phone
        )
    }

    val cvPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val name = try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            } catch (_: Exception) { null }
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"
            user?.let { save(it.copy(cvName = name.take(60))) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ==================== premium header ====================
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // soft radial glow behind the avatar
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = androidx.compose.ui.geometry.Offset(420f, 60f),
                            radius = 900f
                        )
                    )
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                            .clickable { onEditProfile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Box {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(96.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                initialsOf(user?.fullName ?: ""),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(28.dp)
                            .clickable { onEditProfile() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Edit profile",
                                tint = MaterialTheme.colorScheme.onTertiary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                    if (user?.isPhone == true || !user?.contact.isNullOrBlank()) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user?.fullName ?: "Unknown",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (proActive) {
                        Spacer(Modifier.width(7.dp))
                        com.prc.app.ui.components.ProBadge(size = 22.dp)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (user?.isPhone == true) "Phone verified" else "Email verified",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    // current plan tier chip — always visible
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (proActive) Color(0xFFD9A324) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            if (proActive) {
                                Icon(
                                    Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                            }
                            Text(
                                if (proActive) "PRO" else "FREE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (proActive) Color.White
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (!headline(user).isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        headline(user),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(20.dp))

                // ---- floating stats pill ----
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QuickStat(myApplications.toString(), "Applied", Modifier.weight(1f))
                        StatVDivider()
                        QuickStat(myJobs.toString(), "Jobs posted", Modifier.weight(1f))
                        StatVDivider()
                        QuickStat(savedIds.size.toString(), "Saved", Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(14.dp))

                // ---- completion banner ----
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clickable { showCv = true }
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Profile $pct% complete",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (user?.cvName.isNullOrBlank()) "Add CV" else "CV ✓",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { showCv = true }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(4.dp)
                                )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(pct / 100f)
                                    .height(6.dp)
                                    .background(
                                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.tertiary
                                            )
                                        ),
                                        RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                        if (pct < 100) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Complete your profile to appear in more searches",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        // ==================== contact info ====================
        Section("Contact info") {
            InfoCard {
                InfoRow(Icons.Filled.Work, "Phone", user?.phone?.ifBlank { null } ?: contactFallbackPhone(user))
                InfoRow(Icons.Filled.Mail, "Email", contactEmail(user?.contact, user?.isPhone))
                InfoRow(Icons.Filled.Place, "Location", user?.location?.ifBlank { "Not set" } ?: "Not set")
                if ((user?.memberSince ?: 0L) > 0L) {
                    InfoRow(
                        Icons.Filled.Verified,
                        "Member since",
                        java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(user!!.memberSince))
                    )
                }
            }
        }

        // ==================== about ====================
        Section("About", action = "edit", onAction = { onEditProfile() }) {
            InfoCard {
                if (user?.bio.isNullOrBlank()) {
                    Text(
                        "Add a short bio so employers know who you are.",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        user?.bio ?: "",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ==================== saved job alerts ====================
        val activeAlerts by com.prc.app.data.JobAlertRepository.alerts.collectAsState()
        Section(
            "Job alerts",
            action = if (activeAlerts.isNotEmpty()) "edit" else null,
            onAction = if (activeAlerts.isNotEmpty()) ({ showMyAlerts = true }) else null
        ) {
            if (activeAlerts.isEmpty()) {
                GhostRow("No alerts yet — create one from Search") { showMyAlerts = true }
            } else {
                InfoCard {
                    activeAlerts.take(3).forEachIndexed { i, alert ->
                        PrefRow(
                            if (alert.keyword.isBlank()) "All jobs in ${alert.category}" else "\"${alert.keyword}\" · ${alert.category}",
                            "Active",
                            i < minOf(activeAlerts.size, 3) - 1
                        )
                    }
                    if (activeAlerts.size > 3) {
                        Text(
                            "+${activeAlerts.size - 3} more — tap to manage",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .clickable { showMyAlerts = true }
                        )
                    }
                }
            }
        }

        // ==================== CV ====================
        Section("CV / Resume") {
            val cv = user?.cvName.orEmpty()
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showCv = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Surface(
                        onClick = { cvPicker.launch("*/*") },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            cv.ifBlank { "Add your CV" },
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (cv.isBlank()) "A CV makes employers 3× more likely to reply"
                            else "Tap to manage",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // ==================== work experience ====================
        Section("Work experience", action = "add", onAction = {
            expInitial = null
            showExp = true
        }) {
            val list = user?.experience.orEmpty()
            if (list.isEmpty()) {
                GhostRow("Add your work experience") {
                    expInitial = null
                    showExp = true
                }
            } else {
                InfoCard {
                    list.forEachIndexed { i, e ->
                        TimelineItem(
                            icon = Icons.Filled.Work,
                            title = e.role,
                            org = e.org,
                            dates = e.dates,
                            showDivider = i < list.lastIndex
                        ) {
                            expInitial = e
                            showExp = true
                        }
                    }
                }
            }
        }

        // ==================== education ====================
        Section("Education", action = "add", onAction = {
            eduInitial = null
            showEdu = true
        }) {
            val list = user?.education.orEmpty()
            if (list.isEmpty()) {
                GhostRow("Add your education") {
                    eduInitial = null
                    showEdu = true
                }
            } else {
                InfoCard {
                    list.forEachIndexed { i, e ->
                        TimelineItem(
                            icon = Icons.Filled.School,
                            title = e.qualification,
                            org = e.institution,
                            dates = e.dates,
                            showDivider = i < list.lastIndex
                        ) {
                            eduInitial = e
                            showEdu = true
                        }
                    }
                }
            }
        }

        // ==================== skills ====================
        Section("Skills", action = "edit", onAction = { onEditProfile() }) {
            val skills = user?.skills.orEmpty()
            if (skills.isEmpty()) {
                GhostRow("Add skills so employers can find you") { onEditProfile() }
            } else {
                ChipWrap(skills) { s ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            s,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ==================== certifications ====================
        Section("Certifications & licenses", action = "add", onAction = {
            certInitial = null
            showCert = true
        }) {
            val list = user?.certifications.orEmpty()
            if (list.isEmpty()) {
                GhostRow("Add certifications or licenses") {
                    certInitial = null
                    showCert = true
                }
            } else {
                ChipWrap(list) { s ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            s,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ==================== languages ====================
        Section("Languages", action = "add", onAction = {
            langInitial = null
            showLang = true
        }) {
            val list = user?.languages.orEmpty()
            if (list.isEmpty()) {
                GhostRow("Add languages you speak") {
                    langInitial = null
                    showLang = true
                }
            } else {
                InfoCard {
                    list.forEachIndexed { i, l ->
                        PrefRow(l.language, l.proficiency, i < list.lastIndex)
                    }
                }
            }
        }

        // ==================== payments ====================
        Section("Payments") {
            MenuCard {
                MenuRow(
                    Icons.Filled.WorkspacePremium,
                    if (proActive) "Go Pro — active" else "Go Pro",
                    subLabel = if (proActive)
                        "Renews ${proUntilText}"
                    else
                        "Unlimited premium applications & priority profile",
                    onClick = onOpenProPlans
                )
                MenuRow(
                    Icons.Filled.ReceiptLong,
                    "Payments history",
                    subLabel = "Boosts, unlocks & subscriptions",
                    onClick = onOpenPayments
                )
            }
        }

        // ==================== poster tools ====================
        Section("For posters") {
            MenuCard {
                MenuRow(Icons.Filled.Add, "Post a job", onClick = onOpenPostJob)
                MenuRow(
                    Icons.Filled.Work,
                    "My jobs & applicants",
                    badge = myJobs.takeIf { it > 0 },
                    onClick = onOpenMyJobs
                )
            }
        }

        // ==================== admin portal ====================
        Section("Administration") {
            MenuCard {
                MenuRow(
                    Icons.Filled.AdminPanelSettings,
                    "Admin portal",
                    subLabel = "Provider jobs & approvals",
                    onClick = onOpenAdminPortal
                )
            }
        }

        // ==================== account ====================
        Section("Account") {
            MenuCard {
                MenuRow(Icons.Filled.Notifications, "Notification preferences", onClick = { showNotifPrefs = true })
                MenuRow(Icons.Filled.AlternateEmail, "Account settings", onClick = { showAccountSettings = true })
                MenuRow(
                    Icons.Filled.Description,
                    "About PRC Jobs",
                    subLabel = "v1.0",
                    onClick = { showAbout = true }
                )
                MenuRow(
                    Icons.Filled.Logout,
                    "Log out",
                    danger = true,
                    onClick = onLogout
                )
            }
        }

        Spacer(Modifier.height(28.dp))
    }

    // ==================== dialogs ====================

    if (showExp) {
        EntryDialog(
            title = if (expInitial == null) "Add work experience" else "Edit work experience",
            dismissLabel = if (expInitial == null) "Cancel" else "Done",
            onDismiss = { showExp = false },
            onDelete = expInitial?.let { entry ->
                {
                    user?.let { save(it.copy(experience = it.experience - entry)) }
                    showExp = false
                }
            },
            fields = listOf("Job title" to (expInitial?.role ?: ""), "Company / client" to (expInitial?.org ?: ""), "Dates (e.g. 2024 — 2025)" to (expInitial?.dates ?: "")),
            onSave = { f ->
                val entry = ExperienceEntry(f[0].trim(), f[1].trim(), f[2].trim())
                user?.let { u ->
                    val updated = if (expInitial != null)
                        u.copy(experience = u.experience.map { if (it == expInitial) entry else it })
                    else
                        u.copy(experience = u.experience + entry)
                    save(updated)
                }
                showExp = false
            }
        )
    }

    if (showEdu) {
        EntryDialog(
            title = if (eduInitial == null) "Add education" else "Edit education",
            dismissLabel = if (eduInitial == null) "Cancel" else "Done",
            onDismiss = { showEdu = false },
            onDelete = eduInitial?.let { entry ->
                {
                    user?.let { save(it.copy(education = it.education - entry)) }
                    showEdu = false
                }
            },
            fields = listOf("Qualification" to (eduInitial?.qualification ?: ""), "Institution" to (eduInitial?.institution ?: ""), "Dates (e.g. 2025 — Present)" to (eduInitial?.dates ?: "")),
            onSave = { f ->
                val entry = EducationEntry(f[0].trim(), f[1].trim(), f[2].trim())
                user?.let { u ->
                    val updated = if (eduInitial != null)
                        u.copy(education = u.education.map { if (it == eduInitial) entry else it })
                    else
                        u.copy(education = u.education + entry)
                    save(updated)
                }
                showEdu = false
            }
        )
    }

    if (showLang) {
        LanguageDialog(
            initial = langInitial,
            onDismiss = { showLang = false },
            onDelete = langInitial?.let { entry ->
                {
                    user?.let { save(it.copy(languages = it.languages - entry)) }
                    showLang = false
                }
            },
            onSave = { entry ->
                user?.let { u ->
                    val updated = if (langInitial != null)
                        u.copy(languages = u.languages.map { if (it == langInitial) entry else it })
                    else
                        u.copy(languages = u.languages + entry)
                    save(updated)
                }
                showLang = false
            }
        )
    }

    if (showCert) {
        TextDialog(
            title = if (certInitial == null) "Add certification" else "Edit certification",
            placeholder = "e.g. First Aid Certified",
            initial = certInitial ?: "",
            onDismiss = { showCert = false },
            onDelete = certInitial?.let { old ->
                {
                    user?.let { save(it.copy(certifications = it.certifications - old)) }
                    showCert = false
                }
            },
            onSave = { value ->
                val v = value.trim()
                user?.let { u ->
                    val updated = if (certInitial != null)
                        u.copy(certifications = u.certifications.map { if (it == certInitial) v else it })
                    else if (v.isNotEmpty())
                        u.copy(certifications = u.certifications + v)
                    else u
                    save(updated)
                }
                showCert = false
            }
        )
    }

    if (showCv) {
        CvDialog(
            current = user?.cvName.orEmpty(),
            onDismiss = { showCv = false },
            onRemove = {
                user?.let { save(it.copy(cvName = "")) }
                showCv = false
            },
            onPick = {
                showCv = false
                cvPicker.launch("*/*")
            },
            onSave = { name ->
                user?.let { save(it.copy(cvName = name.trim())) }
                showCv = false
            }
        )
    }

    if (showNotifPrefs) {
        NotificationPrefsDialog(
            alerts = user?.notifyAlerts ?: true,
            digest = user?.notifyDigest ?: true,
            onDismiss = { showNotifPrefs = false },
            onToggleAlerts = { AuthRepository.updateProfile("", emptyList(), notifyAlerts = it) },
            onToggleDigest = { AuthRepository.updateProfile("", emptyList(), notifyDigest = it) }
        )
    }

    if (showAccountSettings) {
        AccountSettingsDialog(
            phone = user?.phone.orEmpty(),
            location = user?.location.orEmpty(),
            onDismiss = { showAccountSettings = false },
            onSave = { newPhone, newLocation ->
                user?.let { u ->
                    save(u.copy(phone = newPhone.trim(), location = newLocation.trim()))
                }
                showAccountSettings = false
            }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About PRC Jobs", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRC Jobs v1.0", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Find work near you — from skilled trades to professional roles. " +
                                "Apply in-app, track your applications, and get alerted when jobs matching " +
                                "your search are posted.",
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAbout = false }) { Text("OK") }
            }
        )
    }

    if (showMyAlerts) {
        ManageAlertsDialog(
            alerts = com.prc.app.data.JobAlertRepository.alerts.collectAsState().value,
            onDismiss = { showMyAlerts = false },
            onRemove = { kw, cat -> com.prc.app.data.JobAlertRepository.remove(kw, cat) }
        )
    }
}

// ==================== dialogs ====================

/** 3-field dialog used for experience and education entries. */
@Composable
private fun EntryDialog(
    title: String,
    fields: List<Pair<String, String>>,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (List<String>) -> Unit
) {
    val state = remember { mutableStateOf(fields.map { it.second }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEachIndexed { i, f ->
                    OutlinedTextField(
                        value = state.value[i],
                        onValueChange = { v ->
                            state.value = state.value.toMutableList().also { it[i] = v }
                        },
                        label = { Text(f.first, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (state.value[0].isNotBlank()) onSave(state.value) else onDismiss()
                }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(dismissLabel) }
            }
        }
    )
}

/** Language dialog: name + proficiency chip picker. */
@Composable
private fun LanguageDialog(
    initial: LanguageEntry?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (LanguageEntry) -> Unit
) {
    var name by remember { mutableStateOf(initial?.language ?: "") }
    var level by remember { mutableStateOf(initial?.proficiency ?: "Conversational") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add language" else "Edit language", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Language", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Proficiency", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PROFICIENCY_LEVELS.forEach { lvl ->
                        val active = lvl == level
                        Surface(
                            shape = CircleShape,
                            color = if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { level = lvl }
                        ) {
                            Text(
                                lvl,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (active) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onSave(LanguageEntry(name.trim(), level)) else onDismiss() }
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** Single-text dialog used for certifications. */
@Composable
private fun TextDialog(
    title: String,
    placeholder: String,
    initial: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(placeholder, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

/** CV manager: pick a real file from the device or remove the attached CV. */
@Composable
private fun CvDialog(
    current: String,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    onPick: () -> Unit,
    onSave: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your CV", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (current.isBlank()) "No CV attached yet."
                    else "Attached: $current",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (current.isBlank()) "Choose a file from your device"
                            else "Choose a different file",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            Row {
                if (current.isNotBlank()) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    )
}

/** Notification preferences with immediate Firestore persistence. */
@Composable
private fun NotificationPrefsDialog(
    alerts: Boolean,
    digest: Boolean,
    onDismiss: () -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onToggleDigest: (Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification preferences", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToggleRow(
                    title = "Job alerts",
                    sub = "Notify me when a new job matches my saved search alerts",
                    checked = alerts,
                    onToggle = onToggleAlerts
                )
                ToggleRow(
                    title = "Weekly digest",
                    sub = "A weekly summary of new jobs matching my skills",
                    checked = digest,
                    onToggle = onToggleDigest
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ToggleRow(
    title: String,
    sub: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                sub,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onToggle)
    }
}

/** Edit the Firestore-backed contact details (phone + location). */
@Composable
private fun AccountSettingsDialog(
    phone: String,
    location: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var phoneValue by remember { mutableStateOf(phone) }
    var locationValue by remember { mutableStateOf(location) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account settings", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = phoneValue,
                    onValueChange = { phoneValue = it },
                    label = { Text("Phone number", fontSize = 13.sp) },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = locationValue,
                    onValueChange = { locationValue = it },
                    label = { Text("Location", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(phoneValue, locationValue) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Manage saved search alerts: view and remove each one. */
@Composable
private fun ManageAlertsDialog(
    alerts: List<com.prc.app.data.JobAlert>,
    onDismiss: () -> Unit,
    onRemove: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your job alerts", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            if (alerts.isEmpty()) {
                Text(
                    "No alerts yet. Search for a job and tap \"Create job alert\".",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column {
                    alerts.forEach { alert ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (alert.keyword.isBlank()) "All jobs" else "\"${alert.keyword}\"",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    alert.category,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Remove alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onRemove(alert.keyword, alert.category) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

// ==================== pieces ====================

@Composable
private fun Section(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Box(
                    Modifier
                        .size(width = 3.dp, height = 14.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            if (action != null && onAction != null) {
                Icon(
                    if (action == "add") Icons.Filled.Add else Icons.Filled.Edit,
                    contentDescription = action,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onAction() }
                )
            }
        }
        content()
    }
}

/** Quiet "+ add" row shown when a section is empty. */
@Composable
private fun GhostRow(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ChipWrap(
    items: List<String>,
    chip: @Composable (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { chip(it) }
            }
        }
    }
}

@Composable
private fun TimelineItem(
    icon: ImageVector,
    title: String,
    org: String,
    dates: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    org,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dates,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun PrefRow(label: String, value: String, showDivider: Boolean) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Text(
                value,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 9.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text(
                label,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun QuickStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatVDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(34.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column { content() }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    badge: Int? = null,
    danger: Boolean = false,
    subLabel: String? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(9.dp),
            color = if (danger) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (danger) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (danger) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Text(
                "$badge",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (subLabel != null) {
            Text(
                subLabel,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
    }
}

// ==================== helpers ====================

private fun initialsOf(name: String): String =
    name.split(" ", limit = 2).filter { it.isNotBlank() }
        .map { it.first().uppercase() }
        .joinToString("")
        .take(2)
        .ifEmpty { "\uD83D\uDCBC" }

private fun headline(user: User?): String {
    val u = user ?: return "PRC Jobs member"
    val skill = u.skills.firstOrNull()
    return when {
        skill != null && u.location.isNotBlank() -> "$skill · ${u.location}"
        skill != null -> skill
        u.location.isNotBlank() -> u.location
        else -> "PRC Jobs member"
    }
}

private fun contactEmail(contact: String?, isPhone: Boolean?): String =
    if (isPhone == false && !contact.isNullOrBlank()) contact else "Not set"

/** Dedicated phone field, falling back to the login contact if it IS a phone. */
private fun contactFallbackPhone(user: com.prc.app.data.User?): String =
    if (user?.isPhone == true) user.contact else "Not set"
