package com.prc.app.data

/** A work-experience entry on a user's profile. */
data class ExperienceEntry(
    val role: String,
    val org: String,
    val dates: String
)

/** An education entry on a user's profile. */
data class EducationEntry(
    val qualification: String,
    val institution: String,
    val dates: String
)

/** A spoken language + proficiency. */
data class LanguageEntry(
    val language: String,
    val proficiency: String
)

/** True when any of the optional profile sections has content. */
fun User.hasAnyExtra(): Boolean =
    cvName.isNotBlank() || experience.isNotEmpty() || education.isNotEmpty() ||
        certifications.isNotEmpty() || languages.isNotEmpty()