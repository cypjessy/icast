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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prc.app.data.AuthRepository
import com.prc.app.data.EducationEntry
import com.prc.app.data.ExperienceEntry

/** Professional job titles by field — shown as quick picks; the search field
 *  autocompletes from the full catalogue as the user types. */
val JOB_TITLE_CATALOGUE: Map<String, List<String>> = mapOf(
    "IT & Software" to listOf(
        "Software Engineer", "Frontend Developer", "Backend Developer", "Full-Stack Developer",
        "Mobile Developer", "Web Developer", "Data Analyst", "Data Scientist", "Data Engineer",
        "DevOps Engineer", "QA Engineer", "Test Automation Engineer", "UI/UX Designer",
        "Product Designer", "IT Support Specialist", "System Administrator", "Network Engineer",
        "Cybersecurity Analyst", "Cloud Engineer", "Solutions Architect", "Database Administrator",
        "Machine Learning Engineer", "Game Developer", "Scrum Master", "IT Project Manager"
    ),
    "Medicine & Health" to listOf(
        "Medical Doctor", "Orthopaedic Doctor", "Paediatrician", "Surgeon", "Gynaecologist",
        "Cardiologist", "Dermatologist", "Psychiatrist", "Anaesthesiologist", "Radiologist",
        "Nurse", "Registered Nurse", "Nursing Assistant", "Midwife", "Clinical Officer",
        "Pharmacist", "Pharmacy Technician", "Lab Technologist", "Phlebotomist",
        "Radiographer", "Sonographer", "Physiotherapist", "Occupational Therapist",
        "Dentist", "Dental Assistant", "Optometrist", "Public Health Officer",
        "Nutritionist", "Dietitian", "Psychologist", "Counsellor", "Community Health Worker",
        "Paramedic", "Veterinary Officer"
    ),
    "Human Resources" to listOf(
        "HR Manager", "HR Officer", "HR Assistant", "Recruiter", "Talent Acquisition Specialist",
        "Training & Development Officer", "Payroll Officer", "HR Business Partner",
        "Employee Relations Officer", "Compensation & Benefits Analyst"
    ),
    "Finance & Accounting" to listOf(
        "Accountant", "Senior Accountant", "Financial Analyst", "Finance Manager", "Auditor",
        "Internal Auditor", "Bookkeeper", "Tax Consultant", "Investment Analyst",
        "Credit Officer", "Loan Officer", "Actuary", "Bank Teller", "Bank Manager",
        "Insurance Agent", "Underwriter", "Risk Analyst", "Payroll Accountant",
        "Budget Analyst", "Treasurer", "M-Pesa Agent", "Microfinance Officer"
    ),
    "Sales & Marketing" to listOf(
        "Sales Executive", "Sales Manager", "Sales Representative", "Marketing Manager",
        "Digital Marketer", "Social Media Manager", "SEO Specialist", "Brand Manager",
        "Business Development Officer", "Business Management Officer", "Account Manager",
        "Customer Success Manager", "Customer Service Representative", "Call Centre Agent",
        "Field Sales Agent", "Retail Sales Associate", "Sales & Marketing Officer",
        "Market Research Analyst", "Public Relations Officer", "Communications Officer"
    ),
    "Business & Administration" to listOf(
        "Business Management Officer", "Administrative Assistant", "Office Administrator",
        "Executive Assistant", "Office Manager", "Receptionist", "Data Entry Clerk",
        "Operations Manager", "Operations Officer", "Program Coordinator", "Project Manager",
        "Project Officer", "Management Consultant", "Business Analyst", "Entrepreneur",
        "Company Director", "Secretary", "Front Office Manager", "Facilities Manager"
    ),
    "Education & Training" to listOf(
        "Teacher", "Primary School Teacher", "Secondary School Teacher", "Nursery School Teacher",
        "Special Needs Teacher", "Lecturer", "Professor", "Teaching Assistant",
        "School Administrator", "Headteacher", "Education Officer", "Corporate Trainer",
        "Private Tutor", "Curriculum Developer", "Librarian", "Driving Instructor"
    ),
    "Engineering & Technical" to listOf(
        "Civil Engineer", "Structural Engineer", "Mechanical Engineer", "Electrical Engineer",
        "Chemical Engineer", "Industrial Engineer", "Site Engineer", "Project Engineer",
        "Surveyor", "Quantity Surveyor", "Land Surveyor", "Architect", "Draughtsman",
        "CAD Technician", "Construction Manager", "Site Foreman", "Building Contractor",
        "Electrician", "Plumber", "Welder", "Carpenter", "Mason", "Painter & Decorator",
        "Steel Fixer", "HVAC Technician", "Machine Operator", "Maintenance Technician",
        "Mechanic", "Auto Electrician", "Generator Technician"
    ),
    "Law & Governance" to listOf(
        "Advocate", "Lawyer", "Legal Officer", "Legal Assistant", "Compliance Officer",
        "Paralegal", "Magistrate", "Judge", "State Counsel", "Policy Analyst",
        "Procurement Officer", "Contract Manager", "Immigration Officer", "Court Clerk",
        "Human Rights Officer", "Governance Advisor"
    ),
    "Agriculture & Environment" to listOf(
        "Agronomist", "Farm Manager", "Agricultural Officer", "Veterinarian", "Vet Assistant",
        "Food Scientist", "Extension Officer", "Horticulturist", "Fisheries Officer",
        "Livestock Officer", "Greenhouse Manager", "Coffee Expert", "Tea Factory Manager",
        "Forestry Officer", "Environmental Officer", "Conservation Officer",
        "Climate Analyst", "Waste Management Officer", "Beekeeper"
    ),
    "Hospitality & Tourism" to listOf(
        "Hotel Manager", "Assistant Hotel Manager", "Chef", "Sous Chef", "Cook", "Baker",
        "Kitchen Assistant", "Waiter/Waitress", "Bartender", "Housekeeper", "Housekeeping Supervisor",
        "Tour Guide", "Tour Operator", "Travel Agent", "Front Desk Officer", "Receptionist (Hotel)",
        "Events Manager", "Event Planner", "Flight Attendant", "Pilot", "Safari Driver-Guide",
        "Camp Manager", "Lodge Manager", "Caterer", "Catering Manager"
    ),
    "Logistics & Transport" to listOf(
        "Supply Chain Analyst", "Logistics Coordinator", "Logistics Manager", "Fleet Manager",
        "Warehouse Supervisor", "Warehouse Assistant", "Store Keeper", "Inventory Clerk",
        "Procurement Officer", "Customs Officer", "Clearing & Forwarding Agent",
        "Delivery Driver", "Truck Driver", "Boda Boda Rider", "Taxi Driver",
        "Courier", "Shipping Officer", "Port Worker", "Forklift Operator", "Dispatch Rider"
    ),
    "Media & Creative" to listOf(
        "Journalist", "News Reporter", "Editor", "Sub-Editor", "Graphic Designer",
        "Videographer", "Video Editor", "Photographer", "Content Writer", "Copywriter",
        "Radio Presenter", "TV Presenter", "Podcast Producer", "Animation Artist",
        "Illustrator", "Musician", "Actor", "Film Director", "Producer", "DJ",
        "Social Media Influencer", "Community Manager", "Voice Over Artist"
    ),
    "Security & Safety" to listOf(
        "Security Guard", "Security Supervisor", "Security Manager", "CCTV Operator",
        "Police Officer", "Prison Officer", "Wildlife Ranger", "Firefighter",
        "Fire Safety Officer", "Safety Officer (HSE)", "Occupational Health Officer",
        "Bodyguard", "Bouncer", "Loss Prevention Officer"
    ),
    "Beauty & Wellness" to listOf(
        "Hairdresser", "Barber", "Beautician", "Makeup Artist", "Nail Technician",
        "Massage Therapist", "Spa Therapist", "Salon Manager", "Tattoo Artist",
        "Fitness Trainer", "Gym Instructor", "Yoga Instructor", "Sports Coach"
    ),
    "Retail & Trades" to listOf(
        "Shop Attendant", "Shop Manager", "Cashier", "Supermarket Attendant",
        "Merchandiser", "Sales Promoter", "Wholesaler", "Distributor", "Market Vendor",
        "Tailor", "Fashion Designer", "Cobbler", "Jeweller", "Watch Repairer",
        "Phone Repair Technician", "Computer Repair Technician", "Dry Cleaner", "Laundry Attendant"
    ),
    "NGO & Community" to listOf(
        "Program Officer", "Field Officer", "Community Development Officer",
        "Social Worker", "Case Worker", "Monitoring & Evaluation Officer",
        "Grant Writer", "Advocacy Officer", "Volunteer Coordinator",
        "Youth Worker", "Gender Officer", "Refugee Settlement Worker", "Peacebuilding Officer"
    ),
    "Government & Public Service" to listOf(
        "Civil Servant", "Administrative Officer", "Chief Officer", "County Representative",
        "Public Health Officer", "Licensing Officer", "Revenue Officer", "Tax Officer",
        "Registration Officer", "Election Officer", "Immigration Case Officer",
        "Foreign Service Officer", "Intelligence Analyst"
    )
)

val SKILL_OPTIONS = JOB_TITLE_CATALOGUE.values.flatten()

private val EXPERIENCE_LEVELS = listOf("0–1 years", "1–3 years", "3–5 years", "5+ years")

/**
 * Detailed first-signup profile setup: 3 steps — (1) location + skills,
 * (2) bio + experience level, (3) experience/education entries — each step
 * skippable, with a global "Skip for now" that lands the user in the app
 * with whatever was entered persisted to their profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    isEditMode: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit = onDone
) {
    SystemBarAppearance(darkIcons = !androidx.compose.foundation.isSystemInDarkTheme())

    val me = AuthRepository.currentUser.collectAsState().value
    var step by remember { mutableStateOf(0) }

    // shared state across steps
    var location by remember { mutableStateOf(me?.location ?: "") }
    var selected by remember { mutableStateOf(me?.skills?.toSet() ?: emptySet()) }
    var bio by remember { mutableStateOf(me?.bio ?: "") }
    var expLevel by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf(me?.experience ?: emptyList()) }
    var education by remember { mutableStateOf(me?.education ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun persistAndFinish() {
        val derivedSkills = if (selected.isEmpty() && expLevel.isNotBlank()) selected else selected
        AuthRepository.updateProfile(
            location = location.trim(),
            skills = derivedSkills.toList(),
            bio = buildBio(bio.trim(), expLevel)
        )
        onDone()
    }

    val titles = listOf("Where are you based?", "Tell us about you", "Your background")
    val subtitles = listOf(
        "Pick your trade so we can match you with nearby work.",
        "A short bio helps employers trust you faster.",
        "Add work experience and education — optional, but it boosts your profile."
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ==================== top bar ====================
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = {
                if (step > 0) step-- else onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isEditMode) "Edit profile" else "Set up your profile",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Step ${step + 1} of 3",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!isEditMode) {
                Text(
                    "Skip for now",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { persistAndFinish() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // ==================== progress dots ====================
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                titles[step],
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 31.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitles[step],
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            when (step) {
                0 -> StepLocation(location, { location = it; error = null }, selected, { selected = it })
                1 -> StepBio(bio, { bio = it; error = null }, expLevel, { expLevel = it })
                2 -> StepBackground(
                    experience = experience,
                    onExperienceChange = { experience = it },
                    education = education,
                    onEducationChange = { education = it }
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(28.dp))
        }

        // ==================== bottom actions ====================
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Button(
                onClick = {
                    when (step) {
                        0 -> {
                            if (location.isBlank()) {
                                error = "Add your location, or skip for now"
                            } else {
                                error = null
                                step = 1
                            }
                        }
                        1 -> {
                            error = null
                            step = 2
                        }
                        else -> persistAndFinish()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(
                    if (step == 2) (if (isEditMode) "Save changes" else "Finish setup") else "Continue",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (step < 2) {
                TextButton(
                    onClick = { step++ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Skip this step",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ==================== steps ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepLocation(
    location: String,
    onLocation: (String) -> Unit,
    selected: Set<String>,
    onToggle: (Set<String>) -> Unit
) {
    OutlinedTextField(
        value = location,
        onValueChange = onLocation,
        label = { Text("Your location (e.g. Westlands, Nairobi)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(26.dp))
    Text("Your profession", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(4.dp))
    Text(
        "Start typing your job title and pick from the suggestions — or tap a popular field below.",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    JobTitlePicker(selected = selected, onToggle = onToggle)
}

@Composable
private fun StepBio(
    bio: String,
    onBio: (String) -> Unit,
    expLevel: String,
    onExpLevel: (String) -> Unit
) {
    OutlinedTextField(
        value = bio,
        onValueChange = onBio,
        label = { Text("Short bio") },
        placeholder = { Text("Tell employers a little about yourself…") },
        minLines = 4,
        maxLines = 6,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(26.dp))
    Text("Experience level", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(12.dp))
    EXPERIENCE_LEVELS.chunked(2).forEach { rowLevels ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowLevels.forEach { lvl ->
                FilterChip(
                    selected = expLevel == lvl,
                    onClick = { onExpLevel(if (expLevel == lvl) "" else lvl) },
                    label = { Text(lvl, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepBackground(
    experience: List<ExperienceEntry>,
    onExperienceChange: (List<ExperienceEntry>) -> Unit,
    education: List<EducationEntry>,
    onEducationChange: (List<EducationEntry>) -> Unit
) {
    var showExpDialog by remember { mutableStateOf<ExperienceEntry?>(null) }
    var showEduDialog by remember { mutableStateOf<EducationEntry?>(null) }

    Text("Work experience", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(10.dp))
    experience.forEach { e ->
        EntryRow(
            icon = Icons.Filled.Work,
            title = e.role,
            subtitle = "${e.org} · ${e.dates}",
            onRemove = { onExperienceChange(experience - e) },
            onClick = { showExpDialog = e }
        )
        Spacer(Modifier.height(8.dp))
    }
    AddTile("Add work experience") {
        showExpDialog = ExperienceEntry("", "", "")
    }

    Spacer(Modifier.height(26.dp))
    Text("Education", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(10.dp))
    education.forEach { e ->
        EntryRow(
            icon = Icons.Filled.School,
            title = e.qualification,
            subtitle = "${e.institution} · ${e.dates}",
            onRemove = { onEducationChange(education - e) },
            onClick = { showEduDialog = e }
        )
        Spacer(Modifier.height(8.dp))
    }
    AddTile("Add education") {
        showEduDialog = EducationEntry("", "", "")
    }

    if (showExpDialog != null) {
        val initial = showExpDialog!!
        ThreeFieldDialog(
            title = if (initial.role.isBlank()) "Add work experience" else "Edit work experience",
            labels = listOf("Job title", "Company / client", "Dates (e.g. 2024 — 2025)"),
            initial = listOf(initial.role, initial.org, initial.dates),
            onDismiss = { showExpDialog = null },
            onSave = { f ->
                val entry = ExperienceEntry(f[0].trim(), f[1].trim(), f[2].trim())
                onExperienceChange(
                    if (initial.role.isBlank()) experience + entry
                    else experience.map { if (it == initial) entry else it }
                )
                showExpDialog = null
            }
        )
    }
    if (showEduDialog != null) {
        val initial = showEduDialog!!
        ThreeFieldDialog(
            title = if (initial.qualification.isBlank()) "Add education" else "Edit education",
            labels = listOf("Qualification", "Institution", "Dates (e.g. 2020 — 2024)"),
            initial = listOf(initial.qualification, initial.institution, initial.dates),
            onDismiss = { showEduDialog = null },
            onSave = { f ->
                val entry = EducationEntry(f[0].trim(), f[1].trim(), f[2].trim())
                onEducationChange(
                    if (initial.qualification.isBlank()) education + entry
                    else education.map { if (it == initial) entry else it }
                )
                showEduDialog = null
            }
        )
    }
}

// ==================== pieces ====================

/**
 * Job-title picker: a search box that suggests titles from the catalogue as
 * the user types, plus quick "field" chips (IT, Medicine, HR…) that expand a
 * handful of popular titles from that field.
 */
@Composable
private fun JobTitlePicker(selected: Set<String>, onToggle: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var expandedField by remember { mutableStateOf<String?>(null) }

    // suggestions: prefix + substring matches across the whole catalogue
    val suggestions = remember(query) {
        val q = query.trim()
        if (q.length < 2) emptyList()
        else SKILL_OPTIONS.filter { it.startsWith(q, ignoreCase = true) }
            .plus(SKILL_OPTIONS.filter { !it.startsWith(q, true) && it.contains(q, ignoreCase = true) })
            .distinct()
            .take(6)
    }

    // selected titles as removable chips
    if (selected.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            selected.take(3).forEach { title ->
                SelectedTitleChip(title) { onToggle(selected - title) }
            }
        }
        if (selected.size > 3) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                selected.drop(3).forEach { title ->
                    SelectedTitleChip(title) { onToggle(selected - title) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text("e.g. Software Engineer, Nurse, Accountant…", fontSize = 13.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )

    // autocomplete suggestions
    if (suggestions.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                suggestions.forEachIndexed { i, title ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onToggle(selected + title)
                                query = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 11.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            title,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (i < suggestions.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Browse by field", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Spacer(Modifier.height(4.dp))
    Text(
        "${JOB_TITLE_CATALOGUE.values.sumOf { it.size }} occupations across ${JOB_TITLE_CATALOGUE.size} fields",
        fontSize = 11.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))

    // all fields as a wrapping chip grid
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        JOB_TITLE_CATALOGUE.keys.forEach { field ->
            FieldChip(field, expandedField == field) {
                expandedField = if (expandedField == field) null else field
            }
        }
    }

    // titles inside the expanded field
    expandedField?.let { field ->
        Spacer(Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(10.dp)) {
                Text(
                    field,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                (JOB_TITLE_CATALOGUE[field] ?: emptyList()).forEach { title ->
                    val picked = title in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onToggle(if (picked) selected - title else selected + title)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            if (picked) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            tint = if (picked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            title,
                            fontSize = 13.sp,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                            color = if (picked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedTitleChip(title: String, onRemove: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onRemove() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove $title",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun FieldChip(label: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (expanded) FontWeight.Bold else FontWeight.Medium,
            color = if (expanded) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun EntryRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
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
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(17.dp)
                    .clickable { onRemove() }
            )
        }
    }
}

@Composable
private fun AddTile(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 14.dp)
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
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ThreeFieldDialog(
    title: String,
    labels: List<String>,
    initial: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val state = remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEachIndexed { i, label ->
                    OutlinedTextField(
                        value = state.value[i],
                        onValueChange = { v ->
                            state.value = state.value.toMutableList().also { it[i] = v }
                        },
                        label = { Text(label, fontSize = 13.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (state.value[0].isNotBlank()) onSave(state.value) else onDismiss() }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Append the chosen experience level to the bio so it shows on the profile headline. */
private fun buildBio(bio: String, expLevel: String): String =
    if (expLevel.isBlank()) bio
    else if (bio.isBlank()) "Experience: $expLevel"
    else "$bio\nExperience: $expLevel"
