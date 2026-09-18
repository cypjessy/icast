package com.prc.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** AI-extracted job draft. Every field carries its source so the review screen can tag it. */
data class AiDraft(
    val providerName: String = "",
    val providerContact: String = "",
    val title: String = "",
    val category: String = "",
    val type: String = "Full-time",
    val pay: String = "",
    val experience: String = "",
    val location: String = "",
    val openings: String = "1",
    val remoteOk: Boolean = false,
    val about: String = "",
    val requirements: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val applicationUrl: String = "",
    val deadlineDays: String = "",
    val posterType: String = ""   // AI classification: "company" | "individual"
)

/**
 * Gemini REST client for the admin "post with AI" flow.
 *
 * - [extractJobFromText]: structures loose job text into an [AiDraft].
 * - [extractJobFromImage]: reads a job advert photo (flyer/poster) and
 *   OCRs + structures it into the same draft.
 *
 * The key is supplied by the project owner; rotate it if it ever leaks.
 */
object GeminiApi {

    // Key injected at build time from local.properties / GEMINI_API_KEY env
    // (kept out of source control). TODO: backend proxy before production.
    private val API_KEY: String = com.prc.app.BuildConfig.GEMINI_API_KEY
    private const val MODEL = "gemini-3.6-flash"

    private fun endpoint() =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$API_KEY"

    /** Structured-extraction prompt shared by both entry points. */
    private val SYSTEM_PROMPT = """
        You extract structured job adverts. Work ONLY with information actually
        present in the advert — never invent salaries, providers, contacts,
        links or deadlines. If a detail is missing, return an empty string or
        empty array for it.

        Return ONLY a JSON object (no markdown, no code fences) with exactly
        these keys:
        {
          "providerName": "company/organization offering the job; empty if not stated",
          "providerContact": "phone number OR email address stated for applying; empty if none",
          "title": "job title",
          "category": "one of: IT & Software, Medicine & Health, Human Resources, Finance & Accounting, Sales & Marketing, Education, Engineering, Law & Governance, Agriculture, Hospitality & Tourism, Logistics & Supply Chain, Media & Creative, Retail, Domestic, Driving, Construction, Skilled trade",
          "type": "one of: Full-time, Part-time, Contract, Gig; empty if not stated",
          "pay": "salary/pay exactly as stated e.g. 'KSh 25,000/month'; empty if not stated",
          "experience": "experience requirement; empty if not stated",
          "location": "town/city e.g. 'Nakuru, Kenya'; empty if not stated",
          "openings": "number of positions as string, default '1'",
          "remoteOk": true only if remote work is mentioned, else false,
          "about": "full job description as stated",
          "requirements": ["one requirement per array item; only those stated"],
          "skills": ["required skills; only those stated or clearly implied"],
          "applicationUrl": "how to apply: a website URL if given, OR an email address if applications go by email (prefix mailto:), OR a phone number (prefix tel:); empty if not stated",
          "deadlineDays": "days from today until the application deadline as string; empty if not stated. Look carefully for deadline phrasing: 'apply by', 'deadline', 'closing date', 'applications close', 'submit before', an explicit date (e.g. '30th September' or '30/09'), or a timeframe like 'within 2 weeks' or 'apply in 5 days'. Convert any date given into days from today. If no deadline is stated anywhere, return an empty string",
          "posterType": "classify WHO is offering the job: \"company\" if the poster is a company, organization, business, agency, NGO, school, hospital, hotel, farm business, or any registered entity hiring staff (e.g. 'Safaricom', 'Kilimani Primary School', 'Green Valley Farm Ltd'); \"individual\" if the poster is a private person hiring help directly (e.g. someone needing a house girl, nanny, driver, or personal errand runner with no business name). Clues for company: a business/organization name, 'Ltd/Limited/Company/Group/School/Hospital/Agency', formal tone, a company website or email domain. Clues for individual: a first name only, 'I need/I am looking for', a personal phone number with no business name. Default to \"individual\" if genuinely ambiguous."
        }

        Rules:
        - Many real adverts have no salary, no provider name, or no website.
          Leave those fields empty — the admin will fill them in the review
          screen. Do NOT guess.
        - If applications are by email, set applicationUrl to "mailto:<email>".
        - If applications are by phone/WhatsApp, set applicationUrl to "tel:<number>".
        - If the text is not a job advert, return {"error": "not a job advert"}.
    """.trimIndent()

    /** @return parsed [AiDraft] or an error string in [Result.failure]. */
    suspend fun extractJobFromText(text: String): Result<AiDraft> =
        withContext(Dispatchers.IO) { run(text, null, null) }

    /** [imageBytes] are raw JPEG/PNG bytes of a job advert photo. */
    suspend fun extractJobFromImage(imageBytes: ByteArray, mime: String = "image/jpeg"): Result<AiDraft> =
        withContext(Dispatchers.IO) {
            val b64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            run(null, b64, mime)
        }

    private fun run(text: String?, imageB64: String?, imageMime: String?): Result<AiDraft> {
        try {
            val parts = JSONArray()
            if (text != null) parts.put(JSONObject().put("text", text))
            if (imageB64 != null) {
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject().put("mime_type", imageMime).put("data", imageB64)
                    )
                )
            }
            parts.put(JSONObject().put("text", SYSTEM_PROMPT))

            val body = JSONObject().put(
                "contents",
                JSONArray().put(JSONObject().put("parts", parts))
            )

            val conn = URL(endpoint()).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code !in 200..299) {
                return Result.failure(Exception("Gemini error $code: ${response.take(300)}"))
            }

            val json = JSONObject(response)
            val candidates = json.optJSONArray("candidates") ?: JSONArray()
            if (candidates.length() == 0) {
                return Result.failure(Exception("Gemini returned no candidates"))
            }
            val content = candidates.getJSONObject(0)
                .optJSONObject("content")
            val outParts = content?.optJSONArray("parts") ?: JSONArray()
            var outText = ""
            for (i in 0 until outParts.length()) {
                outText += outParts.getJSONObject(i).optString("text", "")
            }
            // strip possible markdown fences
            val cleaned = outText.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val obj = JSONObject(cleaned)
            if (obj.optString("error").isNotEmpty()) {
                return Result.failure(Exception("The image/text is not a job advert"))
            }

            fun strList(key: String): List<String> {
                val arr = obj.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }

            return Result.success(
                AiDraft(
                    providerName = obj.optString("providerName"),
                    providerContact = obj.optString("providerContact"),
                    title = obj.optString("title"),
                    category = obj.optString("category"),
                    type = obj.optString("type", "Full-time").ifBlank { "Full-time" },
                    pay = obj.optString("pay"),
                    experience = obj.optString("experience"),
                    location = obj.optString("location"),
                    openings = obj.optString("openings", "1").ifBlank { "1" },
                    remoteOk = obj.optBoolean("remoteOk", false),
                    about = obj.optString("about"),
                    requirements = strList("requirements"),
                    skills = strList("skills"),
                    applicationUrl = obj.optString("applicationUrl"),
                    deadlineDays = obj.optString("deadlineDays"),
                    posterType = obj.optString("posterType", "individual").lowercase()
                )
            )
        } catch (e: Exception) {
            return Result.failure(Exception("AI extraction failed: ${e.message}"))
        }
    }
}
