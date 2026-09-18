package com.prc.app.data

data class User(
    val fullName: String,
    val contact: String,      // phone number or email (login identity)
    val isPhone: Boolean,
    val phone: String = "",   // verified contact number used when applying to jobs
    val password: String,     // mock only — never do this in production
    val location: String = "",
    val skills: List<String> = emptyList(),
    val bio: String = "",
    val experience: List<ExperienceEntry> = emptyList(),
    val education: List<EducationEntry> = emptyList(),
    val certifications: List<String> = emptyList(),
    val languages: List<LanguageEntry> = emptyList(),
    val cvName: String = "",   // CV attached to applications (filename)
    val memberSince: Long = 0,  // signup timestamp (0 for legacy accounts)
    val notifyAlerts: Boolean = true,   // job-alert notifications
    val notifyDigest: Boolean = true    // weekly digest notifications
)
