package com.prc.app.ui.screens.post

import com.prc.app.ui.theme.SystemBarAppearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.JobRepository
import com.prc.app.ui.screens.profile.JOB_TITLE_CATALOGUE
import com.prc.app.ui.screens.profile.SKILL_OPTIONS

private val JOB_TYPES = listOf("Full-time", "Part-time", "Contract", "Gig")
private val EXPERIENCE_LEVELS = listOf("Entry (0–2 yrs)", "Mid (3–5 yrs)", "Senior (6+ yrs)")

/**
 * 4-step posting wizard: Job basics -> Description -> Compensation & skills -> Review & publish.
 * State lives across steps so Back keeps what you typed; Review step can save a draft.
 */
@Composable
fun PostJobScreen(
    editJobId: Int = -1,          // >= 0: prefill and update instead of post
    onPosted: () -> Unit,
    onBack: () -> Unit
) {
        SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val me = AuthRepository.currentUser.collectAsState().value
    val editing = remember(editJobId) { JobRepository.job(editJobId) }

    // ==================== wizard state (shared across steps) ====================
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var category by remember { mutableStateOf(editing?.category ?: "Construction") }
    // Live category suggestion from the title; the user's manual pick wins
    // until they edit the title again.
    var suggestedCategory by remember { mutableStateOf<String?>(null) }
    var suggestionDismissed by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf(editing?.type ?: "Gig") }
    var location by remember { mutableStateOf(editing?.location ?: "") }
    var openings by remember { mutableStateOf((editing?.openings ?: 1).toString()) }
    var remoteOk by remember { mutableStateOf(editing?.remoteOk ?: false) }

    var description by remember { mutableStateOf(editing?.description ?: "") }
    var responsibilities by remember { mutableStateOf(editing?.responsibilities ?: listOf<String>()) }
    var requirements by remember { mutableStateOf(editing?.requirements ?: listOf<String>()) }

    var payMin by remember { mutableStateOf(payPart(editing?.pay, true)) }
    var payMax by remember { mutableStateOf(payPart(editing?.pay, false)) }
    var negotiable by remember { mutableStateOf(editing?.negotiable ?: false) }
    var premium by remember { mutableStateOf(editing?.premium ?: false) }
    var experience by remember { mutableStateOf(editing?.experience?.ifBlank { null } ?: EXPERIENCE_LEVELS.first()) }
    var skills by remember { mutableStateOf(editing?.skills ?: listOf<String>()) }
    var skillDraft by remember { mutableStateOf("") }
    var deadlineDays by remember { mutableStateOf((editing?.deadlineDays ?: 7)?.toString() ?: "7") }

    val payLine = buildPayLine(payMin, payMax)

    // Re-suggest whenever the title changes; auto-select the suggestion and
    // surface it as a dismissible chip in the category step.
    androidx.compose.runtime.LaunchedEffect(title) {
        if (editing != null) return@LaunchedEffect   // keep the edited job's category
        val suggestion = JobRepository.suggestCategory(title)
        suggestedCategory = suggestion
        suggestionDismissed = false
        if (suggestion != null && suggestion != category) {
            category = suggestion
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            // ==================== wizard header ====================
            Column(Modifier.padding(horizontal = 20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .size(34.dp)
                            .clickable {
                                error = null
                                if (step == 0) onBack() else step--
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (editing != null) "Edit job" else "Post a job",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(4) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(3.dp)
                                )
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Step ${step + 1} of 4 — ${stepTitle(step)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }

            // ==================== step content ====================
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                when (step) {
                    0 -> StepBasics(
                        title, { title = it; error = null },
                        category, { category = it; suggestionDismissed = true },
                        suggestedCategory = if (suggestionDismissed) null else suggestedCategory,
                        onDismissSuggestion = { suggestionDismissed = true },
                        type, { type = it },
                        location, { location = it; error = null },
                        openings, { openings = it },
                        remoteOk, { remoteOk = it }
                    )
                    1 -> StepDescription(
                        description, { description = it; error = null },
                        responsibilities, { responsibilities = it },
                        requirements, { requirements = it }
                    )
                    2 -> StepCompensation(
                        payMin, { payMin = it; error = null },
                        payMax, { payMax = it },
                        negotiable, { negotiable = it },
                        premium, { premium = it },
                        experience, { experience = it },
                        skills, { skills = it },
                        skillDraft, { skillDraft = it },
                        deadlineDays, { deadlineDays = it }
                    )
                    3 -> StepReview(
                        title = title,
                        category = category,
                        type = type,
                        location = location,
                        remoteOk = remoteOk,
                        payLine = payLine,
                        negotiable = negotiable,
                        premium = premium,
                        experience = experience,
                        openings = openings,
                        skills = skills,
                        deadlineDays = deadlineDays,
                        posterName = me?.fullName ?: "you"
                    )
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ==================== bottom action bar ====================
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = {
                            val problem = validate(step, title, location, payLine, description)
                            if (problem != null) {
                                error = problem
                                return@Button
                            }
                            error = null
                            if (step < 3) {
                                step++
                            } else if (editing != null) {
                                JobRepository.updateJob(
                                    jobId = editing.id,
                                    title = title.trim(),
                                    category = category,
                                    location = location.trim(),
                                    pay = payLine,
                                    type = type,
                                    description = description.trim(),
                                    openings = openings.filter { it.isDigit() }.ifEmpty { "1" }.toInt().coerceAtLeast(1),
                                    remoteOk = remoteOk,
                                    responsibilities = responsibilities,
                                    requirements = requirements,
                                    experience = experience,
                                    skills = skills,
                                    deadlineDays = deadlineDays.filter { it.isDigit() }.ifEmpty { null }?.toInt(),
                                    negotiable = negotiable,
                                    premium = premium
                                )
                                onPosted()
                            } else {
                                JobRepository.postJob(
                                    posterName = me?.fullName ?: "Unknown",
                                    posterContact = me?.contact ?: "",
                                    title = title.trim(),
                                    category = category,
                                    location = location.trim(),
                                    pay = payLine,
                                    type = type,
                                    description = description.trim(),
                                    openings = openings.filter { it.isDigit() }.ifEmpty { "1" }.toInt().coerceAtLeast(1),
                                    remoteOk = remoteOk,
                                    responsibilities = responsibilities,
                                    requirements = requirements,
                                    experience = experience,
                                    skills = skills,
                                    deadlineDays = deadlineDays.filter { it.isDigit() }.ifEmpty { null }?.toInt(),
                                    negotiable = negotiable,
                                    premium = premium
                                )
                                onPosted()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            if (step == 3) (if (editing != null) "Save changes" else "Publish job") else "Continue",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (step == 3 && editing == null) {
                        TextButton(
                            onClick = {
                                if (title.trim().length >= 4) {
                                    JobRepository.postJob(
                                        posterName = me?.fullName ?: "Unknown",
                                        posterContact = me?.contact ?: "",
                                        title = title.trim(),
                                        category = category,
                                        location = location.trim().ifBlank { "Not set" },
                                        pay = payLine.ifBlank { "Pay not set" },
                                        type = type,
                                        description = description.trim().ifBlank { "Draft — description pending." },
                                        openings = openings.filter { it.isDigit() }.ifEmpty { "1" }.toInt().coerceAtLeast(1),
                                        remoteOk = remoteOk,
                                        responsibilities = responsibilities,
                                        requirements = requirements,
                                        experience = experience,
                                        skills = skills,
                                        deadlineDays = deadlineDays.filter { it.isDigit() }.ifEmpty { null }?.toInt(),
                                        negotiable = negotiable,
                                        premium = premium,
                                        draft = true
                                    )
                                    onPosted()
                                } else {
                                    error = "Give the job a title before saving a draft"
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Save as draft",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== steps ====================

@Composable
private fun StepBasics(
    title: String, onTitle: (String) -> Unit,
    category: String, onCategory: (String) -> Unit,
    suggestedCategory: String? = null,
    onDismissSuggestion: () -> Unit = {},
    type: String, onType: (String) -> Unit,
    location: String, onLocation: (String) -> Unit,
    openings: String, onOpenings: (String) -> Unit,
    remoteOk: Boolean, onRemote: (Boolean) -> Unit
) {
    FieldLabel("Job title")
    OutlinedTextField(
        value = title,
        onValueChange = onTitle,
        placeholder = { Text("e.g. Mason for wall construction") },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    FieldLabel("Category")
    if (suggestedCategory != null && suggestedCategory != category) {
        SuggestionBanner(suggestedCategory, onDismissSuggestion) { onCategory(suggestedCategory) }
        Spacer(Modifier.height(8.dp))
    }
    TwoStepCategoryPicker(selected = category, onSelect = onCategory)
    Spacer(Modifier.height(16.dp))
    FieldLabel("Job type")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        JOB_TYPES.forEach { t ->
            SelectPill(t, type == t) { onType(t) }
        }
    }
    Spacer(Modifier.height(16.dp))
    FieldLabel("Location")
    OutlinedTextField(
        value = location,
        onValueChange = onLocation,
        placeholder = { Text("e.g. Kasarani, Nairobi") },
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            FieldLabel("Openings")
            OutlinedTextField(
                value = openings,
                onValueChange = { v -> onOpenings(v.filter { it.isDigit() }.take(2)) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Column(Modifier.weight(1.4f)) {
            FieldLabel("Remote OK?")
            Surface(
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (remoteOk) "Yes" else "No",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = remoteOk, onCheckedChange = onRemote)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepDescription(
    description: String, onDescription: (String) -> Unit,
    responsibilities: List<String>, onResponsibilities: (List<String>) -> Unit,
    requirements: List<String>, onRequirements: (List<String>) -> Unit
) {
    FieldLabel("About the role")
    OutlinedTextField(
        value = description,
        onValueChange = onDescription,
        placeholder = { Text("Describe the work, the site, hours, what's provided…") },
        minLines = 4,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(18.dp))
    FieldLabel("Responsibilities")
    BulletListEditor(responsibilities, onResponsibilities, "Add responsibility")
    Spacer(Modifier.height(18.dp))
    FieldLabel("Requirements")
    BulletListEditor(requirements, onRequirements, "Add requirement")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepCompensation(
    payMin: String, onPayMin: (String) -> Unit,
    payMax: String, onPayMax: (String) -> Unit,
    negotiable: Boolean, onNegotiable: (Boolean) -> Unit,
    premium: Boolean, onPremium: (Boolean) -> Unit,
    experience: String, onExperience: (String) -> Unit,
    skills: List<String>, onSkills: (List<String>) -> Unit,
    skillDraft: String, onSkillDraft: (String) -> Unit,
    deadlineDays: String, onDeadlineDays: (String) -> Unit
) {
    FieldLabel("Salary range (KSh)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = payMin,
            onValueChange = { v -> onPayMin(v.filter { it.isDigit() }.take(7)) },
            placeholder = { Text("e.g. 1500") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        )
        Text(
            "—",
            Modifier.padding(horizontal = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = payMax,
            onValueChange = { v -> onPayMax(v.filter { it.isDigit() }.take(7)) },
            placeholder = { Text("optional") },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.weight(1f)
        )
    }
    Text(
        "Write the amount per day, month or job — it's shown exactly as typed.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp)
    )
    Spacer(Modifier.height(14.dp))
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "Salary is negotiable",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Switch(checked = negotiable, onCheckedChange = onNegotiable)
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "🔒 Premium job",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Applicants pay to apply. Choose this only for high-value roles.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = premium, onCheckedChange = onPremium)
        }
    }
    Spacer(Modifier.height(16.dp))
    FieldLabel("Experience level")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EXPERIENCE_LEVELS.forEach { lvl ->
            SelectPill(lvl, experience == lvl) { onExperience(lvl) }
        }
    }
    Spacer(Modifier.height(16.dp))
    FieldLabel("Required skills / occupations")
    OccupationPicker(skills = skills, onSkills = onSkills, skillDraft = skillDraft, onSkillDraft = onSkillDraft)
    Spacer(Modifier.height(16.dp))
    FieldLabel("Application deadline")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("3", "7", "14", "30").forEach { d ->
            SelectPill("$d days", deadlineDays == d) { onDeadlineDays(d) }
        }
        SelectPill("No deadline", deadlineDays.isEmpty()) { onDeadlineDays("") }
    }
}

@Composable
private fun StepReview(
    title: String,
    category: String,
    type: String,
    location: String,
    remoteOk: Boolean,
    payLine: String,
    negotiable: Boolean,
    premium: Boolean,
    experience: String,
    openings: String,
    skills: List<String>,
    deadlineDays: String,
    posterName: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "$type · $location" + if (remoteOk) " · Remote OK" else "",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )
            ReviewRow("Salary", payLine + if (negotiable) " (Negotiable)" else "")
            ReviewRow("Experience", experience)
            ReviewRow("Openings", openings.ifBlank { "1" })
            ReviewRow("Category", category)
            if (skills.isNotEmpty()) ReviewRow("Skills", skills.joinToString(", "))
            ReviewRow(
                "Matches seekers",
                if (skills.isEmpty()) "Add skills to match"
                else "${skills.size} occupation${if (skills.size == 1) "" else "s"} — recommended to the right people"
            )
            ReviewRow("Deadline", if (deadlineDays.isEmpty()) "Open-ended" else "In $deadlineDays days")
            if (premium) ReviewRow("Applications", "🔒 Paid application required (KSh 200)")
        }
    }
    Spacer(Modifier.height(14.dp))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "Posting as: $posterName",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Once published, this job appears in Search and on Home for matching seekers. You'll get a notification for every applicant.",
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// ==================== pieces ====================

/**
 * Occupation picker for job posting: search box suggests from the shared
 * JOB_TITLE_CATALOGUE (300+ occupations), quick field chips expand popular
 * titles, and the poster can still type a custom skill. Picked items become
 * removable chips. Because posters pick from the same vocabulary job seekers
 * use in their profiles, skill-based matching gets much sharper.
 */
@Composable
private fun OccupationPicker(
    skills: List<String>,
    onSkills: (List<String>) -> Unit,
    skillDraft: String,
    onSkillDraft: (String) -> Unit
) {
    var expandedField by remember { mutableStateOf<String?>(null) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            if (skills.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    skills.forEach { s ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp)
                            ) {
                                Text(
                                    s,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $s",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { onSkills(skills - s) }
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // autocomplete from the shared catalogue
            val q = skillDraft.trim()
            val suggestions = remember(skillDraft) {
                if (q.length < 2) emptyList()
                else SKILL_OPTIONS.filter { it.startsWith(q, ignoreCase = true) }
                    .plus(SKILL_OPTIONS.filter { !it.startsWith(q, true) && it.contains(q, ignoreCase = true) })
                    .distinct()
                    .filter { it !in skills }
                    .take(5)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                OutlinedTextField(
                    value = skillDraft,
                    onValueChange = onSkillDraft,
                    placeholder = { Text("e.g. Nurse, Mason, Sales Executive…", fontSize = 12.5.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                TextButton(onClick = {
                    val s = skillDraft.trim()
                    if (s.isNotEmpty() && s !in skills) onSkills(skills + s)
                    onSkillDraft("")
                }) { Text("Add", fontWeight = FontWeight.Bold) }
            }

            if (suggestions.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Column {
                        suggestions.forEach { title ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSkills(skills + title)
                                        onSkillDraft("")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    title,
                                    fontSize = 12.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Popular fields",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                JOB_TITLE_CATALOGUE.keys.forEach { field ->
                    Surface(
                        shape = CircleShape,
                        color = if (expandedField == field) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            1.dp,
                            if (expandedField == field) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable {
                            expandedField = if (expandedField == field) null else field
                        }
                    ) {
                        Text(
                            field,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (expandedField == field) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            expandedField?.let { field ->
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (JOB_TITLE_CATALOGUE[field] ?: emptyList()).filter { it !in skills }
                        .take(12)
                        .forEach { title ->
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    onSkills(skills + title)
                                }
                            ) {
                                Text(
                                    "+ $title",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                }
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 7.dp)
    )
}

/**
 * "Suggested from title" banner: one tap accepts the suggestion (though it
 * is normally auto-applied already), × dismisses it so the user can pick
 * manually without the chip nagging.
 */
@Composable
private fun SuggestionBanner(
    suggested: String,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                "\u2728 Based on your title: ",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                suggested,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { onAccept() }
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Dismiss",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Two-step category selection: pick a professional group first, then a
 * subcategory within that group. The chosen [selected] category (a
 * subcategory) highlights both its pill and its parent group.
 */
@Composable
private fun TwoStepCategoryPicker(selected: String, onSelect: (String) -> Unit) {
    val groups = JobRepository.categoryGroups
    // The group that contains the current selection (or null if none picked yet).
    var activeGroup by remember(selected) {
        mutableStateOf(groups.firstOrNull { selected in it.second }?.first)
    }

    // ---- step 1: professional groups ----
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (group, subs) ->
            val active = activeGroup == group
            SelectPill(group, active) {
                activeGroup = group
                // Jump straight to the group's single subcategory if there's only one.
                if (subs.size == 1) onSelect(subs.first())
            }
        }
    }

    // ---- step 2: subcategories within the chosen group ----
    val subs = groups.firstOrNull { it.first == activeGroup }?.second.orEmpty()
    if (subs.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\u203A ${activeGroup ?: ""}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            subs.forEach { sub ->
                SelectPill(sub, selected == sub) { onSelect(sub) }
            }
        }
    }
}

@Composable
private fun SelectPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun BulletListEditor(
    items: List<String>,
    onItems: (List<String>) -> Unit,
    addLabel: String
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    val commit = {
        val t = draft.trim()
        if (t.isNotEmpty()) onItems(items + t)
        draft = ""
        editing = false
    }
    Column {
        items.forEachIndexed { idx, item ->
            Surface(
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(start = 12.dp)
                            .size(5.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        item,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { onItems(items.filterIndexed { i, _ -> i != idx }) }
                            .padding(8.dp)
                    )
                }
            }
        }
        if (editing) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Describe it…") },
                singleLine = true,
                shape = RoundedCornerShape(9.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { commit() }
                ),
                trailingIcon = {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Add line",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(30.dp)
                            .clickable { commit() }
                            .padding(7.dp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { editing = true }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    addLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ReviewRow(k: String, v: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Text(
            k,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(90.dp)
        )
        Text(
            v,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== helpers ====================

private fun stepTitle(step: Int) = when (step) {
    0 -> "Job basics"
    1 -> "Description"
    2 -> "Compensation & skills"
    else -> "Review & publish"
}

/** Extracts the min (or max) digit part from a formatted pay line like "KSh 1,500–2,000". */
private fun payPart(pay: String?, min: Boolean): String {
    if (pay.isNullOrBlank()) return ""
    val digits = pay.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val numbers = Regex("\\d+").findAll(pay).map { it.value }.toList()
    return when {
        numbers.isEmpty() -> ""
        min -> numbers.first()
        else -> numbers.last()
    }
}

private fun buildPayLine(min: String, max: String): String {
    val m = min.filter { it.isDigit() }
    val x = max.filter { it.isDigit() }
    return when {
        m.isEmpty() && x.isEmpty() -> ""
        x.isEmpty() || m == x -> "KSh ${m.toIntOrNull() ?: 0}"
        else -> "KSh ${m.toIntOrNull() ?: 0}–${x.toIntOrNull() ?: 0}"
    }
}

private fun validate(
    step: Int,
    title: String,
    location: String,
    payLine: String,
    description: String
): String? = when {
    step == 0 && title.trim().length < 4 -> "Give the job a clear title"
    step == 0 && location.isBlank() -> "Where is the job?"
    step == 1 && description.trim().length < 10 -> "Describe the work a bit more"
    step == 2 && payLine.isBlank() -> "What does it pay?"
    else -> null
}
