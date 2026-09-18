package com.prc.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AdminRepository
import com.prc.app.data.AiDraft
import com.prc.app.data.GeminiApi
import com.prc.app.data.JobRepository
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

// ==================== AI palette (violet accent from the design template) ====================

private val JOB_TYPES = listOf("Full-time", "Part-time", "Contract", "Gig")
private val EXPERIENCE_LEVELS = listOf("Entry (0–2 yrs)", "Mid (3–5 yrs)", "Senior (6+ yrs)")
private val DEADLINE_OPTIONS = listOf("3", "7", "14", "30")

/** Sample loose input shown behind the "See an example" toggle. */
private const val EXAMPLE_INPUT =
    "\"Looking for a delivery rider, Nakuru town, must have own motorbike and valid license, " +
            "KES 800/day plus fuel, need someone who knows the town well, urgent.\""



/**
 * Post with AI: the admin writes or pastes the job details in any format — full
 * sentences, bullet notes, even Sheng — and the AI structures it into the same
 * field model used by the manual paste-and-post flow. The admin reviews each
 * extracted field on the draft screen, can edit or regenerate, then publishes.
 *
 * Two frames:
 *  1. Compose — intro card, input tool chips, free-text area, example toggle,
 *     sticky generate bar.
 *  2. Review — "AI-generated, please review" banner, structured field cards
 *     each tagged ✨ AI with an edit pencil, regenerate row, publish bar.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPostJobScreen() {
    var frame by remember { mutableStateOf(1) }          // 1 = compose, 2 = review
    var rawText by remember { mutableStateOf("") }
    var providerContact by remember { mutableStateOf("") }
    var showExample by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<AiDraft?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var justPosted by remember { mutableStateOf<String?>(null) }
    var editingField by remember { mutableStateOf<String?>(null) } // which field card is open
    var generateCount by remember { mutableStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var pickedImageName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val wordCount = rawText.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    val enoughText = wordCount >= 12

    fun regenerate() {
        if (generating) return
        generating = true
        error = null
        scope.launch {
            GeminiApi.extractJobFromText(rawText)
                .onSuccess { d ->
                    val filled = if (d.providerContact.isBlank()) d.copy(providerContact = providerContact) else d
                    draft = filled
                    generateCount++
                    frame = 2
                }
                .onFailure { e -> error = e.message }
            generating = false
        }
    }

    // ---- image picker: send the flyer photo straight to Gemini vision ----
    val context = androidx.compose.ui.platform.LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            generating = true
            error = null
            pickedImageName = "flyer selected"
            scope.launch {
                try {
                    val bytes = context.contentResolver
                        .openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        error = "Couldn't read the selected image"
                    } else {
                        GeminiApi.extractJobFromImage(bytes)
                            .onSuccess { d ->
                                val filled = if (d.providerContact.isBlank()) d.copy(providerContact = providerContact) else d
                                draft = filled
                                generateCount++
                                frame = 2
                            }
                            .onFailure { e -> error = e.message }
                    }
                } catch (e: Exception) {
                    error = "Couldn't read the selected image: ${e.message}"
                }
                generating = false
                pickedImageName = null
            }
        }
    }

    fun publish(d: AiDraft) {
        val problem = when {
            d.title.isBlank() -> "Job title is required"
            d.about.isBlank() -> "Description is required"
            else -> null
        }
        if (problem != null) {
            error = problem
            return
        }
        AdminRepository.adminPostJob(
            providerName = d.providerName,
            providerContact = d.providerContact,
            title = d.title,
            category = d.category.ifBlank { "Retail" },
            location = d.location.ifBlank { "Not stated" },
            pay = d.pay,
            type = d.type,
            description = d.about,
            requirements = d.requirements,
            applicationUrl = d.applicationUrl,
            openings = d.openings.toIntOrNull()?.coerceAtLeast(1) ?: 1,
            remoteOk = d.remoteOk,
            experience = d.experience,
            skills = d.skills,
            deadlineDays = d.deadlineDays.toIntOrNull(),
            // only list as a company if AI spotted one AND a name was identified;
            // a company with no name is not shown under Companies hiring.
            isCompany = d.posterType.equals("company", ignoreCase = true) &&
                d.providerName.isNotBlank()
        )
        justPosted = d.title.trim()
        rawText = ""; providerContact = ""; draft = null
        frame = 1; showExample = false; editingField = null
        error = null
    }

    val d = draft

    Box(Modifier.fillMaxWidth()) {
        when (frame) {
            // =====================================================
            // FRAME 1 · Write job details
            // =====================================================
            1 -> Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (justPosted != null) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AdminColors.GreenSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Icon(
                                Icons.Filled.Add, contentDescription = null,
                                tint = AdminColors.Green, modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Posted: \"$justPosted\" — now live in the public feed.",
                                fontSize = 12.sp, lineHeight = 16.sp,
                                fontWeight = FontWeight.SemiBold, color = AdminColors.Green,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // ---- AI intro card ----
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(listOf(AdminColors.VioletTint, Color(0xFFE4F0EC))),
                            RoundedCornerShape(10.dp)
                        )
                        .border(1.dp, Color(0xFFDCD3EE), RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Surface(shape = RoundedCornerShape(10.dp), color = AdminColors.Violet) {
                        Icon(
                            Icons.Filled.AutoAwesome, contentDescription = null,
                            tint = Color.White, modifier = Modifier.padding(9.dp).size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Write it your way, AI arranges it", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AdminColors.InkStrong)
                        Text(
                            "Type or paste the job details — job title, pay, requirements, anything. Our AI will sort it into a proper listing for you to review.",
                            fontSize = 11.5.sp, lineHeight = 16.sp, color = AdminColors.InkSoft, modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }

                AdminSectionTitle("Job details", trailingText = "any format works")
                Surface(shape = RoundedCornerShape(16.dp), color = AdminColors.Card) {
                    Column(Modifier.padding(14.dp)) {
                        AdminField("Provider / company", draft?.providerName ?: "", { },
                            "e.g. Rift Valley Logistics", enabled = false)
                        Spacer(Modifier.height(12.dp))

                        // ---- input tools ----
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ToolChip(
                                "Photo of flyer",
                                highlight = pickedImageName != null,
                                leading = {
                                    Icon(
                                        androidx.compose.material.icons.Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = AdminColors.Violet,
                                        modifier = Modifier.size(12.dp)
                                    )
                                },
                                onClick = {
                                    imagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            ToolChip("Upload doc")
                            ToolChip("Voice note")
                        }
                        Spacer(Modifier.height(12.dp))

                        // ---- free-text textarea ----
                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it; error = null },
                            placeholder = {
                                Text(
                                    "e.g. \"Need a front desk person for our Nakuru clinic, full time, must know computers and be friendly, pay is around 28k to 35k, start immediately, at least 1 year experience preferred…\"",
                                    fontSize = 12.sp, lineHeight = 17.sp, color = AdminColors.InkSoft
                                )
                            },
                            minLines = 6,
                            shape = RoundedCornerShape(10.dp),
                            colors = aiFieldColors(),
                            textStyle = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp, color = AdminColors.InkStrong),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // ---- example toggle ----
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showExample = !showExample }
                                .padding(top = 12.dp, bottom = if (showExample) 4.dp else 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome, contentDescription = null,
                                tint = AdminColors.Violet, modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (showExample) "Hide example" else "See an example",
                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AdminColors.Violet
                            )
                        }
                        if (showExample) {
                            Surface(
                                shape = RoundedCornerShape(9.dp),
                                color = AdminColors.Paper,
                                border = androidx.compose.foundation.BorderStroke(1.dp, AdminColors.Hairline)
                            ) {
                                Column(Modifier.padding(12.dp, 12.dp, 14.dp, 12.dp)) {
                                    Text(
                                        "EXAMPLE INPUT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp, color = AdminColors.InkSoft
                                    )
                                    Text(
                                        EXAMPLE_INPUT,
                                        fontSize = 11.5.sp, lineHeight = 17.sp, color = AdminColors.InkSoft,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = AdminColors.RedSoft) {
                        Text(
                            it, fontSize = 12.sp, lineHeight = 16.sp, color = AdminColors.Red,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                // ---- generate bar ----
                Spacer(Modifier.height(18.dp))
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        if (enoughText) "$wordCount words ready" else "Write at least a few sentences for best results",
                        fontSize = 10.5.sp, color = AdminColors.InkSoft,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Button(
                        onClick = { regenerate() },
                        enabled = (enoughText || pickedImageName != null) && !generating,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AdminColors.Violet,
                            disabledContainerColor = AdminColors.Violet.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        if (generating) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Reading the advert…", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (pickedImageName != null) "Extract from flyer" else "Generate with AI",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 20.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudOff, contentDescription = null,
                            tint = AdminColors.InkSoft, modifier = Modifier.size(12.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Extraction is powered by Google Gemini",
                            fontSize = 10.5.sp, color = AdminColors.InkSoft
                        )
                    }
                }
            }

            // =====================================================
            // FRAME 2 · AI-organized draft
            // =====================================================
            2 -> {
                if (d == null) {
                    // Safety: frame 2 without a draft → bounce back to compose.
                    androidx.compose.runtime.LaunchedEffect(Unit) { frame = 1 }
                } else {
                    val duplicateWarning = d.providerName.isNotBlank() && d.title.isNotBlank() &&
                            AdminRepository.isDuplicatePost(d.providerName, d.title)

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // ---- review banner ----
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp)
                                .fillMaxWidth()
                                .background(Color(0xFFFBF0D9), RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFEED9A8), RoundedCornerShape(10.dp))
                                .padding(horizontal = 15.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome, contentDescription = null,
                                tint = Color(0xFFB6851A), modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("AI-generated — please review", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A6410))
                                Text("Check each field before publishing", fontSize = 11.sp, color = AdminColors.InkSoft)
                            }
                        }

                        AdminSectionTitle("Extracted fields", trailingText = "tap to edit")

                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            // Job title
                            FieldCard("Job title", d.title, onEdit = { editingField = "title" })
                            // Category + Type
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FieldCard("Category", d.category.ifBlank { "—" }, onEdit = { editingField = "category" }, modifier = Modifier.weight(1f))
                                FieldCard("Job type", d.type, onEdit = { editingField = "type" }, modifier = Modifier.weight(1f))
                            }
                            // Salary + Experience
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FieldCard(
                                    "Salary",
                                    d.pay.ifBlank { "Not stated — tap to add" },
                                    highlight = d.pay.isBlank(),
                                    onEdit = { editingField = "pay" },
                                    modifier = Modifier.weight(1f)
                                )
                                FieldCard("Experience", d.experience.ifBlank { "Not stated" }, onEdit = { editingField = "experience" }, modifier = Modifier.weight(1f))
                            }
                            // Location
                            FieldCard("Location", d.location.ifBlank { "Not stated" }, onEdit = { editingField = "location" })
                            // How to apply
                            FieldCard(
                                "How to apply",
                                when {
                                    d.applicationUrl.startsWith("mailto:") -> "By email: " + d.applicationUrl.removePrefix("mailto:")
                                    d.applicationUrl.startsWith("tel:") -> "By phone: " + d.applicationUrl.removePrefix("tel:")
                                    d.applicationUrl.isNotBlank() -> d.applicationUrl
                                    else -> "Through the app — tap to set email/website"
                                },
                                highlight = d.applicationUrl.isBlank(),
                                onEdit = { editingField = "howtoapply" }
                            )
                            // Provider
                            FieldCard(
                                "Provider",
                                d.providerName.ifBlank { "⚠ not found — tap to set" },
                                highlight = d.providerName.isBlank(),
                                onEdit = { editingField = "provider" }
                            )
                            // Poster type toggle (AI classification, admin can override)
                            PosterTypeToggle(
                                posterType = d.posterType,
                                onToggle = { newType -> draft = d.copy(posterType = newType) }
                            )
                            // About
                            FieldCard("About the role", d.about, small = true, onEdit = { editingField = "about" })
                            // Requirements
                            if (d.requirements.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(9.dp), color = AdminColors.Card, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp, 14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FieldLabel("Requirements")
                                            Spacer(Modifier.width(8.dp))
                                            AiTag()
                                            Spacer(Modifier.weight(1f))
                                            EditPencil { editingField = "requirements" }
                                        }
                                        d.requirements.forEach { r ->
                                            Row(modifier = Modifier.padding(top = 6.dp)) {
                                                Box(
                                                    Modifier
                                                        .padding(top = 6.dp)
                                                        .size(4.dp)
                                                        .background(AdminColors.Green, CircleShape)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(r, fontSize = 11.5.sp, lineHeight = 16.sp, color = AdminColors.InkSoft)
                                            }
                                        }
                                    }
                                }
                            }
                            // Skills
                            if (d.skills.isNotEmpty()) {
                                Surface(shape = RoundedCornerShape(9.dp), color = AdminColors.Card, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp, 14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FieldLabel("Skills")
                                            Spacer(Modifier.width(8.dp))
                                            AiTag()
                                            Spacer(Modifier.weight(1f))
                                            EditPencil { editingField = "skills" }
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.padding(top = 8.dp)
                                        ) {
                                            d.skills.forEach { s ->
                                                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFE4F0EC)) {
                                                    Text(
                                                        s, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold,
                                                        color = AdminColors.Green,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ---- regenerate row ----
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { frame = 1 }
                                .padding(top = 14.dp, bottom = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh, contentDescription = null,
                                tint = AdminColors.Violet, modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Edit source text",
                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = AdminColors.Violet
                            )
                        }

                        if (duplicateWarning) {
                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp), color = AdminColors.GoldSoft,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(14.dp)) {
                                    Icon(
                                        Icons.Filled.Warning, contentDescription = null,
                                        tint = Color(0xFF8A6410), modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "A live job with this title from this provider already exists.",
                                        fontSize = 12.sp, lineHeight = 16.sp, color = Color(0xFF8A6410),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        error?.let {
                            Spacer(Modifier.height(10.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = AdminColors.RedSoft, modifier = Modifier.padding(horizontal = 16.dp)) {
                                Text(it, fontSize = 12.sp, lineHeight = 16.sp, color = AdminColors.Red, modifier = Modifier.padding(14.dp))
                            }
                        }

                        // ---- publish bar ----
                        Spacer(Modifier.height(18.dp))
                        Button(
                            onClick = { publish(d) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AdminColors.Green),
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("Continue to review & publish", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 20.dp)
                        ) {
                            Icon(
                                Icons.Filled.CloudOff, contentDescription = null,
                                tint = AdminColors.InkSoft, modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Publishing posts the listing exactly as reviewed above",
                                fontSize = 10.5.sp, color = AdminColors.InkSoft
                            )
                        }
                    }

                    // ---- edit dialogs ----
                    editingField?.let { field ->
                        EditDraftDialog(
                            field = field,
                            draft = d,
                            onDismiss = { editingField = null },
                            onSave = { updated ->
                                draft = updated
                                editingField = null
                            }
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// Mock AI extraction — on-device heuristics for the demo
// =====================================================================

private val TOWNS = listOf(
    "Nairobi", "Nakuru", "Mombasa", "Kisumu", "Eldoret", "Thika", "Naivasha",
    "Athi River", "Machakos", "Nanyuki", "Kitale", "Malindi", "Kakamega", "Kericho"
)

private val SKILL_KEYWORDS = mapOf(
    "motorbike" to "Motorbike riding", "license" to "Valid license", "licence" to "Valid license",
    "computer" to "MS Office", "ms office" to "MS Office", "excel" to "MS Excel",
    "customer" to "Customer Service", "friendly" to "Customer Service", "communication" to "Communication",
    "cook" to "Cooking", "clean" to "Cleaning", "schedule" to "Scheduling",
    "bookkeep" to "Bookkeeping", "cash" to "Cash handling", "drive" to "Driving",
    "typing" to "Typing", "sales" to "Sales", "time" to "Time management"
)

private data class CategoryHint(val keywords: List<String>, val category: String)

private val CATEGORY_HINTS = listOf(
    CategoryHint(listOf("software", "developer", "programmer", "it ", "network", "system admin", "data"), "IT & Software"),
    CategoryHint(listOf("nurse", "doctor", "clinical", "health", "medical", "clinic", "pharmacy"), "Medicine & Health"),
    CategoryHint(listOf("hr ", "human resource", "recruit", "talent"), "Human Resources"),
    CategoryHint(listOf("accountant", "accounting", "finance", "bookkeep", "auditor", "cashier"), "Finance & Accounting"),
    CategoryHint(listOf("sales", "marketing", "business development", "brand"), "Sales & Marketing"),
    CategoryHint(listOf("teacher", "tutor", "lecturer", "school", "education", "training"), "Education"),
    CategoryHint(listOf("engineer", "surveyor", "architect"), "Engineering"),
    CategoryHint(listOf("lawyer", "advocate", "legal", "paralegal", "governance"), "Law & Governance"),
    CategoryHint(listOf("farm", "harvest", "plant", "greenhouse", "cattle", "agricultur"), "Agriculture"),
    CategoryHint(listOf("hotel", "waiter", "cook", "kitchen", "tourism", "travel", "housekeep"), "Hospitality & Tourism"),
    CategoryHint(listOf("logistic", "supply chain", "warehouse", "fleet", "procurement", "stock"), "Logistics & Supply Chain"),
    CategoryHint(listOf("design", "content", "writer", "video", "photograph", "social media", "media"), "Media & Creative"),
    CategoryHint(listOf("shop", "retail", "attendant", "supermarket", "reception", "front desk", "office", "administrator"), "Retail"),
    CategoryHint(listOf("house girl", "househelp", "nanny", "cleaner", "domestic"), "Domestic"),
    CategoryHint(listOf("driver", "driving", "matatu", "lorry", "pickup"), "Driving"),
    CategoryHint(listOf("construction", "site", "excavator", "builder", "mason"), "Construction"),
    CategoryHint(listOf("plumber", "electrician", "carpenter", "welder", "mechanic", "wiring"), "Skilled trade")
)

/**
 * Turns loose free-text into a structured [AiDraft] using simple on-device
 * heuristics. This stands in for a server-side AI call in the demo build.
 */
private fun structureJobText(raw: String, providerContact: String): AiDraft {
    val text = raw.trim()
    val lower = text.lowercase()
    val sentences = text
        .split(Regex("[,;.\\n]"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    // ---- provider: "X is hiring / looking for / needs" or first capitalized phrase
    val providerName = Regex("([A-Z][A-Za-z&' ]{2,40}?)\\s+(?:is|are)\\s+(?:hiring|looking|recruiting|need)")
        .find(text)?.groupValues?.get(1)?.trim()
        ?: sentences.firstOrNull { it.any { c -> c.isUpperCase() } }?.takeIf { it.length in 3..40 }
        ?: ""

    // ---- title: role mentioned after need/looking for/hiring/seeking
    val title = Regex(
        "(?:need|looking for|hiring|seeking|recruiting|want)\\s+(?:a|an|the)?\\s*" +
                "([A-Za-z][A-Za-z /-]{2,40}?)(?=\\s*(?:for|who|that|,|\\.|to|with|in|\\bmust\\b|\\bdue\\b|$))",
        RegexOption.IGNORE_CASE
    ).find(text)?.groupValues?.get(1)?.trim()
        ?.replaceFirstChar { it.uppercase() }
        ?: sentences.first().take(40).trim()

    // ---- pay: KES/KSh amounts or plain numbers with unit
    val pay = Regex(
        "(?:KES|KSh|kshs?)\\s*([\\d,.]+\\s*(?:k|000)?)\\s*(?:to|-|–)?\\s*(?:KES|KSh|kshs?)?\\s*([\\d,.]+\\s*(?:k|000)?)?",
        RegexOption.IGNORE_CASE
    ).find(text)?.let { m ->
        val a = m.groupValues[1].replace(",", "")
        val b = m.groupValues[2].replace(",", "").trim()
        val unit = when {
            lower.contains("/day") || lower.contains("per day") || lower.contains("daily") -> "/day"
            lower.contains("per week") || lower.contains("/week") -> "/week"
            else -> "/month"
        }
        if (b.isNotEmpty()) "KSh ${a.uppercase()}–${b.uppercase()}$unit"
        else "KSh ${a.uppercase()}$unit"
    } ?: ""

    // ---- location
    val location = TOWNS.firstOrNull { lower.contains(it.lowercase()) }?.let { "$it, Kenya" } ?: ""

    // ---- job type
    val type = when {
        lower.contains("part-time") || lower.contains("part time") -> "Part-time"
        lower.contains("contract") -> "Contract"
        lower.contains("gig") || lower.contains("per task") || lower.contains("daily wage") -> "Gig"
        else -> "Full-time"
    }

    // ---- experience
    val expYears = Regex("(\\d+)\\s*(?:\\+\\s*)?(?:years?|yrs?)", RegexOption.IGNORE_CASE).find(text)
    val experience = expYears?.let {
        val y = it.groupValues[1].toIntOrNull() ?: 0
        when {
            y >= 6 -> "Senior (6+ yrs)"
            y >= 3 -> "Mid (3–5 yrs)"
            else -> "Entry (0–2 yrs)"
        }
    } ?: ""

    // ---- openings
    val openings = Regex("(\\d+)\\s*(?:openings?|positions?|people|staff)", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1) ?: "1"

    // ---- category from keyword hints
    val category = CATEGORY_HINTS
        .firstOrNull { hint -> hint.keywords.any { lower.contains(it) } }
        ?.category ?: "Retail"

    // ---- remote
    val remoteOk = lower.contains("remote") || lower.contains("work from home")

    // ---- requirements: sentences with obligation words
    val obligation = Regex("\\b(must|need|required|require|at least|valid|experience|know|able|own)\\b", RegexOption.IGNORE_CASE)
    val requirements = sentences
        .filter { obligation.containsMatchIn(it) && it.length > 8 && !it.contains("KES", true) }
        .map { it.replaceFirstChar { c -> c.lowercase() } }
        .distinct()
        .take(6)

    // ---- skills from keyword map
    val skills = SKILL_KEYWORDS.entries
        .filter { lower.contains(it.key) }
        .map { it.value }
        .distinct()
        .take(5)

    // ---- about: whole text, lightly cleaned
    val about = if (text.length > 320) text.take(317) + "…" else text

    return AiDraft(
        providerName = providerName,
        providerContact = providerContact,
        title = title,
        category = category,
        type = type,
        pay = pay,
        experience = experience,
        location = location,
        openings = openings,
        remoteOk = remoteOk,
        about = about,
        requirements = requirements,
        skills = skills
    )
}

// =====================================================================
// Edit dialog
// =====================================================================

@Composable
private fun EditDraftDialog(
    field: String,
    draft: AiDraft,
    onDismiss: () -> Unit,
    onSave: (AiDraft) -> Unit
) {
    var textValue by remember(field, generateKey(draft)) {
        mutableStateOf(
            when (field) {
                "title" -> draft.title
                "pay" -> draft.pay
                "location" -> draft.location
                "provider" -> draft.providerName
                "about" -> draft.about
                "experience" -> draft.experience
                "howtoapply" -> draft.applicationUrl
                "requirements" -> draft.requirements.joinToString("\n")
                "skills" -> draft.skills.joinToString(", ")
                else -> ""
            }
        )
    }
    var pickValue by remember(field) {
        mutableStateOf(
            when (field) {
                "category" -> draft.category
                "type" -> draft.type
                else -> ""
            }
        )
    }

    val heading = when (field) {
        "title" -> "Job title"
        "category" -> "Category"
        "type" -> "Job type"
        "pay" -> "Salary (as stated)"
        "experience" -> "Experience level"
        "location" -> "Location"
        "howtoapply" -> "How to apply (website URL, or email with mailto: prefix)"
        "provider" -> "Provider / company"
        "about" -> "About the role"
        "requirements" -> "Requirements (one per line)"
        "skills" -> "Skills (comma-separated)"
        else -> field
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (field == "category") {
                    // Same two-step picker as the user Post Job wizard:
                    // professional group first, then subcategory.
                    val groups = JobRepository.categoryGroups
                    var activeGroup by remember(pickValue) {
                        mutableStateOf(groups.firstOrNull { pickValue in it.second }?.first)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { (group, subs) ->
                            val active = activeGroup == group
                            AdminPill(group, active) {
                                activeGroup = group
                                if (subs.size == 1) pickValue = subs.first()
                            }
                        }
                    }
                    val subs = groups.firstOrNull { it.first == activeGroup }?.second.orEmpty()
                    if (subs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "\u203A ${activeGroup ?: ""}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AdminColors.Violet
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            subs.forEach { sub ->
                                AdminPill(sub, pickValue == sub) { pickValue = sub }
                            }
                        }
                    }
                } else if (field == "type") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        JOB_TYPES.forEach { t -> AdminPill(t, pickValue == t) { pickValue = t } }
                    }
                } else {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it },
                        minLines = if (field == "about" || field == "requirements") 4 else 1,
                        shape = RoundedCornerShape(10.dp),
                        colors = aiFieldColors(),
                        textStyle = TextStyle(fontSize = 13.sp, color = AdminColors.InkStrong),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    when (field) {
                        "title" -> draft.copy(title = textValue.trim())
                        "pay" -> draft.copy(pay = textValue.trim())
                        "location" -> draft.copy(location = textValue.trim())
                        "provider" -> draft.copy(providerName = textValue.trim())
                        "about" -> draft.copy(about = textValue.trim())
                        "experience" -> draft.copy(experience = textValue.trim())
                        "howtoapply" -> draft.copy(applicationUrl = textValue.trim())
                        "requirements" -> draft.copy(
                            requirements = textValue.split("\n")
                                .map { it.trim().trimStart('-', '•', ' ') }.filter { it.isNotBlank() }
                        )
                        "skills" -> draft.copy(
                            skills = textValue.split(",")
                                .map { it.trim() }.filter { it.isNotBlank() }.distinct()
                        )
                        "category" -> draft.copy(category = pickValue)
                        "type" -> draft.copy(type = pickValue)
                        else -> draft
                    }
                )
            }) {
                Text("Save", fontWeight = FontWeight.Bold, color = AdminColors.Violet)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = AdminColors.InkSoft) }
        }
    )
}

private fun generateKey(d: AiDraft) = d.hashCode()

// =====================================================================
// Shared field pieces
// =====================================================================

/**
 * AI poster-type classification (company vs individual) with a manual
 * override toggle. Shows what the AI decided; tapping the pill flips it.
 */
@Composable
private fun PosterTypeToggle(
    posterType: String,
    onToggle: (String) -> Unit
) {
    val isCompany = posterType.equals("company", ignoreCase = true)
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (isCompany) Color(0xFFE4F0EC) else Color(0xFFFDF0E7),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCompany) AdminColors.Green.copy(alpha = 0.35f) else Color(0xFFF3D5BE)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp, 12.dp)
        ) {
            Icon(
                if (isCompany) Icons.Filled.Apartment else Icons.Filled.Person,
                contentDescription = null,
                tint = if (isCompany) AdminColors.Green else Color(0xFFB65C2E),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isCompany) "Company" else "Individual",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCompany) AdminColors.Green else Color(0xFFB65C2E)
                    )
                    Spacer(Modifier.width(8.dp))
                    AiTag()
                }
                Text(
                    if (isCompany) "Listed under Companies hiring"
                    else "Not listed under Companies hiring",
                    fontSize = 11.sp,
                    color = AdminColors.InkSoft
                )
            }
            TextButton(onClick = { onToggle(if (isCompany) "individual" else "company") }) {
                Text(
                    "Switch to ${if (isCompany) "Individual" else "Company"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AdminColors.Violet
                )
            }
        }
    }
}

/** One editable extracted field on the review frame. */
@Composable
private fun FieldCard(
    label: String,
    value: String,
    small: Boolean = false,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (highlight) AdminColors.GoldSoft else AdminColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (highlight) Color(0xFFEED9A8) else AdminColors.Hairline),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onEdit() }
    ) {
        Column(Modifier.padding(12.dp, 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldLabel(label)
                Spacer(Modifier.width(8.dp))
                AiTag()
                Spacer(Modifier.weight(1f))
                EditPencil(onClick = onEdit)
            }
            Text(
                value,
                fontSize = if (small) 12.sp else 13.sp,
                fontWeight = if (small) FontWeight.Medium else FontWeight.SemiBold,
                lineHeight = if (small) 17.sp else 18.sp,
                color = if (small) AdminColors.InkSoft else AdminColors.InkStrong,
                maxLines = if (small) 4 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = AdminColors.InkSoft
    )
}

/** "✨ AI" chip shown on every extracted field. */
@Composable
private fun AiTag() {
    Surface(shape = RoundedCornerShape(8.dp), color = AdminColors.VioletTint) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Icon(
                Icons.Filled.AutoAwesome, contentDescription = null,
                tint = AdminColors.Violet, modifier = Modifier.size(9.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text("AI", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = AdminColors.Violet)
        }
    }
}

@Composable
private fun EditPencil(onClick: () -> Unit) {
    Icon(
        Icons.Filled.Edit,
        contentDescription = "Edit",
        tint = AdminColors.InkSoft,
        modifier = Modifier
            .size(13.dp)
            .clickable { onClick() }
    )
}

/** Input-tool chip on the compose frame (doc upload / voice / photo). */
@Composable
private fun ToolChip(
    label: String,
    highlight: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = CircleShape,
        color = if (highlight) AdminColors.VioletTint else AdminColors.Paper,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, if (highlight) AdminColors.Violet else AdminColors.Hairline
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(5.dp))
            }
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) AdminColors.Violet else AdminColors.InkSoft
            )
        }
    }
}

@Composable
private fun AdminField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    enabled: Boolean = true
) {
    Column {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = AdminColors.InkSoft,
            modifier = Modifier.padding(bottom = 7.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValue,
            placeholder = { Text(placeholder, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            minLines = minLines,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            colors = aiFieldColors(),
            textStyle = TextStyle(fontSize = 13.sp, color = AdminColors.InkStrong),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Admin pill reused from the manual flow for edit dialogs. */
@Composable
private fun AdminPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (selected) AdminColors.Green else AdminColors.Paper,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) AdminColors.Green else AdminColors.Hairline
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else AdminColors.InkSoft,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

/** Shared AI text-field colors (violet focus to match the AI accent). */
@Composable
private fun aiFieldColors() =
    androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AdminColors.Violet,
        unfocusedBorderColor = AdminColors.Hairline,
        cursorColor = AdminColors.Violet
    )
