package com.prc.app.data

data class Job(
    val id: Int,
    val title: String,
    val category: String,
    val location: String,
    val pay: String,
    val type: String,        // Full-time | Part-time | Gig | Contract
    val postedAgo: String,   // "2h ago", "Today", "Draft", ...
    val description: String,
    val requirements: List<String>,
    val postedBy: String,
    val posterContact: String = "",   // set for user-posted jobs
    val deadlineDays: Int? = null,    // days until applications close (null = open-ended)
    val openings: Int = 1,
    val remoteOk: Boolean = false,
    val responsibilities: List<String> = emptyList(),
    val experience: String = "",      // "Entry (0–2 yrs)" | "Mid (3–5 yrs)" | "Senior (6+ yrs)"
    val skills: List<String> = emptyList(),
    val negotiable: Boolean = false,
    val draft: Boolean = false,
    val closed: Boolean = false,
    val source: String = "community",   // community (user-posted) | provider (via admin)
    val applicationUrl: String = "",    // provider's own application link (provider jobs)
    val isCompany: Boolean = false,      // AI-classified: true = company/organization poster
    val featuredUntil: Long = 0L,        // paid boost expiry (ms); > now = Featured
    val premium: Boolean = false,        // paid application required to apply
    val needsLinkedin: Boolean = false,  // scraped job whose only apply route is LinkedIn's login wall
    val postedAtMillis: Long = System.currentTimeMillis() - postedAgoSeedMillis(postedAgo)
) {
    val isProviderJob: Boolean get() = source == SOURCE_PROVIDER
    val isFeatured: Boolean get() = featuredUntil > System.currentTimeMillis()

    companion object {
        const val SOURCE_COMMUNITY = "community"
        const val SOURCE_PROVIDER = "provider"

        /** Derives a plausible absolute post time from the human "postedAgo" label. */
        fun postedAgoSeedMillis(label: String): Long {
            val now = System.currentTimeMillis()
            val hour = 60 * 60 * 1000L
            val day = 24 * hour
            return when (label) {
                "Just now", "Today" -> 0L
                "Draft" -> 0L
                else -> {
                    val hours = Regex("(\\d+)h").find(label)?.groupValues?.get(1)?.toLongOrNull()
                    val days = Regex("(\\d+)d").find(label)?.groupValues?.get(1)?.toLongOrNull()
                    (hours?.times(hour) ?: days?.times(day)) ?: day
                }
            }
        }
    }
}
