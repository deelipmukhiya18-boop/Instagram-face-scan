package com.example.network

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.SavedProfileLinkEntity
import com.example.security.SecurityShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class ProfileGenderFilter(
    val id: String,
    val labelHindi: String,
    val searchKeywords: String
) {
    ALL("all", "सभी (Boys & Girls)", "people creators student model influencer"),
    BOYS("boys", "लड़के (Boys ID)", "boy male guy men model creator actor athlete"),
    GIRLS("girls", "लड़कियाँ (Girls ID)", "girl female woman model creator actress artist")
}

data class CandidateProfileLink(
    val handleOrQuery: String,
    val title: String,
    val instagramProfileUrl: String,
    val instagramSearchUrl: String,
    val publicWebSearchUrl: String,
    val clueReason: String,
    val isValidHandleFormat: Boolean
)

data class PublicPhotoClueResult(
    val imageWidth: Int,
    val imageHeight: Int,
    val photoPerceptualHash: String,
    val visibleHandles: List<String>,
    val visibleTextClues: List<String>,
    val publicKeywords: List<String>,
    val candidateLinks: List<CandidateProfileLink>,
    val aiAnalysisUsed: Boolean,
    val clearMessage: String
)

data class AreaLocationInfo(
    val latitude: Double?,
    val longitude: Double?,
    val locality: String,
    val city: String,
    val state: String,
    val country: String,
    val displayAddress: String,
    val genderFilter: ProfileGenderFilter,
    val areaHashtags: List<String>,
    val areaCandidateLinks: List<CandidateProfileLink>,
    val statusSummary: String
)

object MlAndInstagramApiClient {

    private val INSTAGRAM_HANDLE_REGEX = Regex("^[a-zA-Z0-9._]{1,30}$")
    private val EXTRACT_AT_MENTION_REGEX = Regex("@([a-zA-Z0-9._]{2,30})")
    private val IG_PROFILE_URL_REGEX =
        Regex("instagram\\.com/([a-zA-Z0-9._]{3,30})/?", RegexOption.IGNORE_CASE)

    private val RESERVED_IG_PATHS = setOf(
        "p", "reel", "reels", "stories", "explore", "accounts", "about", "legal",
        "developer", "directory", "tv", "tags", "locations", "challenge", "direct", "web"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(35, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(35, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun isGeminiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() &&
            key != "MY_GEMINI_API_KEY" &&
            !key.startsWith("YOUR_")
    }

    fun isValidInstagramUsername(rawHandle: String): Boolean {
        val clean = SecurityShield.sanitizeInput(rawHandle).removePrefix("@")
        if (clean.isBlank() || clean.startsWith(".") || clean.endsWith(".") || clean.contains("..")) {
            return false
        }
        if (RESERVED_IG_PATHS.contains(clean.lowercase(Locale.US))) {
            return false
        }
        return INSTAGRAM_HANDLE_REGEX.matches(clean)
    }

    fun computePerceptualHash(bitmap: Bitmap): String {
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            val bits = StringBuilder(64)
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val leftPixel = scaled.getPixel(x, y)
                    val rightPixel = scaled.getPixel(x + 1, y)
                    val leftLuma =
                        Color.red(leftPixel) * 299 + Color.green(leftPixel) * 587 + Color.blue(leftPixel) * 114
                    val rightLuma =
                        Color.red(rightPixel) * 299 + Color.green(rightPixel) * 587 + Color.blue(rightPixel) * 114
                    bits.append(if (leftLuma >= rightLuma) '1' else '0')
                }
            }
            bits.toString()
        } catch (_: Throwable) {
            "0".repeat(64)
        }
    }

    fun calculateHashSimilarity(hashA: String, hashB: String): Float {
        if (hashA.length != 64 || hashB.length != 64) return 0f
        var matchingBits = 0
        for (i in 0 until 64) {
            if (hashA[i] == hashB[i]) matchingBits++
        }
        return matchingBits / 64f
    }

    /**
     * Discovers real personal/individual Instagram IDs (filtered by Boys / Girls / All)
     * in the specified GPS or entered location using:
     * 1. Gemini API (`BuildConfig.GEMINI_API_KEY`) with Google Search Grounding (`gemini-2.5-flash` / `gemini-3.5-flash`)
     * 2. Real Live Public Index Parsing (`site:instagram.com`) for personal profiles in that location
     * 3. Direct Official Instagram People Search & Google Web Search links for that location + gender filter
     * Never invents fake local city handles (`apna.<city>` etc.).
     */
    suspend fun discoverInstagramIdsForArea(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        manualAreaOverride: String = "",
        genderFilter: ProfileGenderFilter = ProfileGenderFilter.ALL
    ): AreaLocationInfo = withContext(Dispatchers.IO) {
        var locality = ""
        var city = ""
        var state = ""
        var country = "India"
        var displayAddress = SecurityShield.sanitizeInput(manualAreaOverride)

        if (displayAddress.isBlank() && latitude != null && longitude != null) {
            try {
                @Suppress("DEPRECATION")
                val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(latitude, longitude, 1)
                val addr = addresses?.firstOrNull()
                if (addr != null) {
                    locality = SecurityShield.sanitizeInput(addr.subLocality ?: addr.featureName.orEmpty())
                    city = SecurityShield.sanitizeInput(addr.locality ?: addr.subAdminArea.orEmpty())
                    state = SecurityShield.sanitizeInput(addr.adminArea.orEmpty())
                    country = SecurityShield.sanitizeInput(addr.countryName ?: "India")
                }
            } catch (_: Throwable) {
            }

            if (city.isBlank() && locality.isBlank()) {
                try {
                    val osmUrl =
                        "https://nominatim.openstreetmap.org/reverse?format=json&lat=$latitude&lon=$longitude&zoom=14&addressdetails=1"
                    if (SecurityShield.isTrustedHttpsUrl(osmUrl)) {
                        val req = Request.Builder()
                            .url(osmUrl)
                            .header("User-Agent", "InstaLensML-Android/1.0")
                            .get()
                            .build()
                        httpClient.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val json = JSONObject(resp.body?.string().orEmpty())
                                val addrObj = json.optJSONObject("address")
                                if (addrObj != null) {
                                    locality = SecurityShield.sanitizeInput(
                                        addrObj.optString("suburb", addrObj.optString("neighbourhood", ""))
                                    )
                                    city = SecurityShield.sanitizeInput(
                                        addrObj.optString(
                                            "city",
                                            addrObj.optString("town", addrObj.optString("county", ""))
                                        )
                                    )
                                    state = SecurityShield.sanitizeInput(addrObj.optString("state", ""))
                                    country = SecurityShield.sanitizeInput(addrObj.optString("country", "India"))
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }

            displayAddress = listOf(locality, city, state)
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(", ")
                .ifBlank {
                    String.format(Locale.US, "GPS %.4f, %.4f", latitude, longitude)
                }
        } else if (displayAddress.isNotBlank()) {
            val parts = displayAddress.split(",").map { it.trim() }.filter { it.isNotBlank() }
            locality = parts.getOrNull(0).orEmpty()
            city = parts.getOrNull(1) ?: locality
            state = parts.getOrNull(2).orEmpty()
        }

        val candidateLinks = mutableListOf<CandidateProfileLink>()
        val seenHandles = mutableSetOf<String>()

        fun addCandidate(link: CandidateProfileLink) {
            val key = link.handleOrQuery.removePrefix("@").lowercase(Locale.US)
            if (seenHandles.add(key)) {
                candidateLinks.add(link)
            }
        }

        val genderInstruction = when (genderFilter) {
            ProfileGenderFilter.BOYS -> "ONLY real male individuals / boys / male creators, male students, male athletes, male actors, or male influencers"
            ProfileGenderFilter.GIRLS -> "ONLY real female individuals / girls / female creators, female models, female artists, actresses, or female influencers"
            ProfileGenderFilter.ALL -> "real individual people (both boys and girls: personal creators, influencers, students, models, artists, athletes)"
        }

        // 1. Live Gemini API + Google Search Grounding for REAL individual profiles in that location
        if (isGeminiKeyConfigured() && displayAddress.isNotBlank()) {
            val prompt = """
                Find REAL, existing public personal Instagram usernames (@handle) of $genderInstruction who live in, are from, or publicly tag their location as "$displayAddress" (City: $city, State: $state).
                CRITICAL RULES:
                1. Do NOT return generic city/local news pages, meme pages, or invented handles like apna.<city> or <city>_official.
                2. Return ONLY real individual people's Instagram usernames that actually exist on instagram.com.
                3. Include the person's full name, gender/category (${genderFilter.labelHindi}), and how they are connected to "$displayAddress".
                Return ONLY valid JSON with this exact schema:
                {
                  "profiles": [
                    {
                      "handle": "real_instagram_username_without_at",
                      "person_name": "Full Name of Person",
                      "bio_or_location_reason": "Real Boy/Girl profile from $displayAddress — brief bio/profession"
                    }
                  ]
                }
            """.trimIndent()

            // Try gemini-2.5-flash with google_search grounding first, then fallback to gemini-3.5-flash
            val modelsToTry = listOf(
                "gemini-2.5-flash" to true,
                "gemini-3.5-flash" to false
            )

            for ((modelName, useSearchGrounding) in modelsToTry) {
                if (candidateLinks.isNotEmpty()) break
                try {
                    val requestJson = JSONObject().apply {
                        put(
                            "contents",
                            JSONArray().put(
                                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                            )
                        )
                        if (useSearchGrounding) {
                            put(
                                "tools",
                                JSONArray().put(JSONObject().put("google_search", JSONObject()))
                            )
                        } else {
                            put(
                                "generationConfig",
                                JSONObject().apply {
                                    put("responseMimeType", "application/json")
                                    put("temperature", 0.1)
                                }
                            )
                        }
                    }

                    val url =
                        "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
                    val request = Request.Builder()
                        .url(url)
                        .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (response.isSuccessful && bodyStr.isNotBlank()) {
                            val root = JSONObject(bodyStr)
                            val parts = root.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                            val rawText = StringBuilder()
                            if (parts != null) {
                                for (p in 0 until parts.length()) {
                                    rawText.append(parts.optJSONObject(p)?.optString("text", "").orEmpty())
                                }
                            }
                            val text = rawText.toString().trim()
                            val jsonStart = text.indexOf('{')
                            val jsonEnd = text.lastIndexOf('}')
                            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                                val parsed = JSONObject(text.substring(jsonStart, jsonEnd + 1))
                                val arr = parsed.optJSONArray("profiles")
                                if (arr != null) {
                                    for (i in 0 until arr.length()) {
                                        val item = arr.optJSONObject(i) ?: continue
                                        val h = item.optString("handle", "").trim().removePrefix("@")
                                        val personName = item.optString("person_name", "").trim()
                                        val reason = item.optString(
                                            "bio_or_location_reason",
                                            "$displayAddress • ${genderFilter.labelHindi}"
                                        )
                                        if (isValidInstagramUsername(h) && !isGenericCitySlug(h, city, locality)) {
                                            addCandidate(
                                                buildSingleCandidateLink(
                                                    rawQuery = h,
                                                    clueReason = if (personName.isNotBlank()) {
                                                        "$personName ($reason)"
                                                    } else {
                                                        reason
                                                    }
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }

        // 2. Also query real public web index (`site:instagram.com`) for real individual profiles in this location & gender
        if (displayAddress.isNotBlank()) {
            try {
                val genderQueryTerm = when (genderFilter) {
                    ProfileGenderFilter.BOYS -> "boy OR male OR guy OR model OR student"
                    ProfileGenderFilter.GIRLS -> "girl OR female OR model OR actress OR artist"
                    ProfileGenderFilter.ALL -> "profile OR creator OR model OR student"
                }
                val searchQuery = "site:instagram.com \"$displayAddress\" ($genderQueryTerm) -explore -p -reel"
                val ddgUrl = "https://html.duckduckgo.com/html/?q=${Uri.encode(searchQuery)}"
                if (SecurityShield.isTrustedHttpsUrl(ddgUrl)) {
                    val req = Request.Builder()
                        .url(ddgUrl)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                        )
                        .get()
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val html = URLDecoder.decode(resp.body?.string().orEmpty(), "UTF-8")
                            IG_PROFILE_URL_REGEX.findAll(html).forEach { match ->
                                val handle = match.groupValues[1].trim().trim('.')
                                if (
                                    isValidInstagramUsername(handle) &&
                                    !isGenericCitySlug(handle, city, locality) &&
                                    candidateLinks.size < 18
                                ) {
                                    addCandidate(
                                        buildSingleCandidateLink(
                                            rawQuery = handle,
                                            clueReason = "Real Public Instagram ID in $displayAddress (${genderFilter.labelHindi})"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // 3. Always include direct verified People Search links for Boys / Girls / All in that exact location
        if (displayAddress.isNotBlank()) {
            val genderWord = when (genderFilter) {
                ProfileGenderFilter.BOYS -> "Boys / Male"
                ProfileGenderFilter.GIRLS -> "Girls / Female"
                ProfileGenderFilter.ALL -> "All People"
            }
            val specificQuery = when (genderFilter) {
                ProfileGenderFilter.BOYS -> "$displayAddress boys"
                ProfileGenderFilter.GIRLS -> "$displayAddress girls"
                ProfileGenderFilter.ALL -> displayAddress
            }
            val encodedSpecific = Uri.encode(specificQuery)
            val webQuery = when (genderFilter) {
                ProfileGenderFilter.BOYS -> "site:instagram.com \"$displayAddress\" (boy OR male OR guy OR mr) -/p/ -/reel/ -/explore/"
                ProfileGenderFilter.GIRLS -> "site:instagram.com \"$displayAddress\" (girl OR female OR miss OR queen) -/p/ -/reel/ -/explore/"
                ProfileGenderFilter.ALL -> "site:instagram.com \"$displayAddress\" -/p/ -/reel/ -/explore/"
            }

            addCandidate(
                CandidateProfileLink(
                    handleOrQuery = "🔍 $displayAddress • $genderWord Real IDs",
                    title = "Live Instagram People Search: $specificQuery",
                    instagramProfileUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedSpecific",
                    instagramSearchUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedSpecific",
                    publicWebSearchUrl = "https://www.google.com/search?q=${Uri.encode(webQuery)}",
                    clueReason = "$displayAddress में ${genderFilter.labelHindi} के सभी असली पब्लिक Instagram प्रोफाइल खोलें",
                    isValidHandleFormat = false
                )
            )
        }

        val cleanSlug = city.ifBlank { locality.ifBlank { displayAddress } }
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]"), "")

        val hashtags = listOfNotNull(
            "${cleanSlug}boys".takeIf { cleanSlug.isNotBlank() && genderFilter != ProfileGenderFilter.GIRLS },
            "${cleanSlug}girls".takeIf { cleanSlug.isNotBlank() && genderFilter != ProfileGenderFilter.BOYS },
            "${cleanSlug}models".takeIf { cleanSlug.isNotBlank() },
            "${cleanSlug}creators".takeIf { cleanSlug.isNotBlank() }
        ).distinct()

        val realHandleCount = candidateLinks.count { it.isValidHandleFormat }
        val summaryMsg = when {
            realHandleCount > 0 ->
                "📍 $displayAddress (${genderFilter.labelHindi}): $realHandleCount असली पब्लिक Instagram IDs (@username) और लाइव प्रोफाइल सर्च लिंक मिल गए हैं!"
            isGeminiKeyConfigured() ->
                "📍 $displayAddress (${genderFilter.labelHindi}): नीचे दिए गए 'Instagram' या 'Web Search' बटन पर टैप करके इस लोकेशन के असली लोगों के प्रोफाइल खोलें।"
            else ->
                "📍 $displayAddress (${genderFilter.labelHindi}): अधिक डायरेक्ट @username निकालने के लिए AI Studio Secrets में GEMINI_API_KEY डालें, या नीचे के डायरेक्ट लिंक से इस लोकेशन के असली प्रोफाइल खोलें।"
        }

        AreaLocationInfo(
            latitude = latitude,
            longitude = longitude,
            locality = locality,
            city = city,
            state = state,
            country = country,
            displayAddress = displayAddress,
            genderFilter = genderFilter,
            areaHashtags = hashtags,
            areaCandidateLinks = candidateLinks,
            statusSummary = summaryMsg
        )
    }

    private fun isGenericCitySlug(handle: String, city: String, locality: String): Boolean {
        val h = handle.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val c = city.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val l = locality.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        if (c.isNotBlank() && (h == c || h == "apna$c" || h == "${c}official" || h == "${c}diaries" || h == "peopleof$c" || h == "${c}city")) {
            return true
        }
        if (l.isNotBlank() && (h == l || h == "apna$l" || h == "${l}official")) {
            return true
        }
        return false
    }

    suspend fun analyzePhotoForPublicClues(
        bitmap: Bitmap,
        fileNameOrNote: String = "",
        savedDirectory: List<SavedProfileLinkEntity> = emptyList()
    ): PublicPhotoClueResult = withContext(Dispatchers.IO) {
        val safeNote = SecurityShield.sanitizeInput(fileNameOrNote)
        val photoHash = computePerceptualHash(bitmap)
        val foundHandles = linkedSetOf<String>()
        val foundTextClues = linkedSetOf<String>()
        val foundKeywords = linkedSetOf<String>()
        val candidateLinks = mutableListOf<CandidateProfileLink>()

        savedDirectory.filter { it.photoSignatureHash.length == 64 }.forEach { saved ->
            val sim = calculateHashSimilarity(photoHash, saved.photoSignatureHash)
            if (sim >= 0.85f) {
                val pct = (sim * 100).toInt()
                val cleanHandle = SecurityShield.sanitizeInput(saved.usernameOrQuery).removePrefix("@")
                if (cleanHandle.isNotBlank()) {
                    foundHandles.add(cleanHandle)
                    candidateLinks.add(
                        buildSingleCandidateLink(
                            rawQuery = cleanHandle,
                            clueReason = "Saved Photo Match ($pct% Visual Hash Match • ${saved.displayTitle})"
                        )
                    )
                }
            }
        }

        if (safeNote.isNotBlank()) {
            EXTRACT_AT_MENTION_REGEX.findAll(safeNote).forEach { match ->
                val handle = match.groupValues[1]
                if (isValidInstagramUsername(handle)) {
                    foundHandles.add(handle.lowercase(Locale.US))
                }
            }
            val cleanedName = safeNote
                .substringBeforeLast(".")
                .replace(Regex("^(IMG|DSC|Screenshot|Photo|PXL)_?[-0-9_]*", RegexOption.IGNORE_CASE), "")
                .trim()
            if (cleanedName.length >= 3 && !cleanedName.all { it.isDigit() }) {
                foundTextClues.add(cleanedName)
                if (isValidInstagramUsername(cleanedName)) {
                    foundHandles.add(cleanedName.lowercase(Locale.US))
                }
            }
        }

        var aiUsed = false
        var aiSummary = ""

        // Use GEMINI_API_KEY (`gemini-3.5-flash`) for real photo clue & public creator recognition
        if (isGeminiKeyConfigured()) {
            try {
                val outputStream = ByteArrayOutputStream()
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    512.coerceAtMost(bitmap.width.coerceAtLeast(64)),
                    512.coerceAtMost(bitmap.height.coerceAtLeast(64)),
                    true
                )
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

                val prompt = """
                    Analyze this uploaded image to help the user find the associated real person's Instagram account:
                    1. If this image shows a public figure, creator, model, actor, athlete, or influencer, provide their real Instagram handle(s) in `visible_handles` and their full name in `visible_text_clues`.
                    2. Extract any visible @username watermark, social media handle, or name printed on the image.
                    3. Provide helpful search keywords in `public_keywords`.
                    Return ONLY valid JSON with this exact schema:
                    {
                      "visible_handles": ["real_instagram_handle_without_at"],
                      "visible_text_clues": ["Full Name or Visible Text Clue"],
                      "public_keywords": ["search keyword"],
                      "summary": "Summary in Hindi/English of the identified person, handle, or visual clues."
                    }
                """.trimIndent()

                val requestJson = JSONObject().apply {
                    put(
                        "contents",
                        JSONArray().put(
                            JSONObject().apply {
                                put(
                                    "parts",
                                    JSONArray()
                                        .put(JSONObject().put("text", prompt))
                                        .put(
                                            JSONObject().put(
                                                "inlineData",
                                                JSONObject()
                                                    .put("mimeType", "image/jpeg")
                                                    .put("data", base64Image)
                                            )
                                        )
                                )
                            }
                        )
                    )
                    put(
                        "generationConfig",
                        JSONObject().apply {
                            put("responseMimeType", "application/json")
                            put("temperature", 0.1)
                        }
                    )
                }

                val url =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=${BuildConfig.GEMINI_API_KEY}"
                val request = Request.Builder()
                    .url(url)
                    .post(requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        val root = JSONObject(bodyStr)
                        val text = root.optJSONArray("candidates")
                            ?.optJSONObject(0)
                            ?.optJSONObject("content")
                            ?.optJSONArray("parts")
                            ?.optJSONObject(0)
                            ?.optString("text")
                            .orEmpty()

                        if (text.isNotBlank()) {
                            val parsed = JSONObject(text)
                            aiUsed = true
                            aiSummary = parsed.optString("summary", "").trim()

                            val handlesArr = parsed.optJSONArray("visible_handles")
                            if (handlesArr != null) {
                                for (i in 0 until handlesArr.length()) {
                                    val h = handlesArr.optString(i, "").trim().removePrefix("@")
                                    if (isValidInstagramUsername(h)) {
                                        foundHandles.add(h.lowercase(Locale.US))
                                    }
                                }
                            }

                            val textArr = parsed.optJSONArray("visible_text_clues")
                            if (textArr != null) {
                                for (i in 0 until textArr.length()) {
                                    val t = SecurityShield.sanitizeInput(textArr.optString(i, ""))
                                    if (t.isNotBlank()) foundTextClues.add(t)
                                }
                            }

                            val kwArr = parsed.optJSONArray("public_keywords")
                            if (kwArr != null) {
                                for (i in 0 until kwArr.length()) {
                                    val k = SecurityShield.sanitizeInput(kwArr.optString(i, ""))
                                    if (k.isNotBlank()) foundKeywords.add(k)
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        val existingQueries = candidateLinks.map { it.handleOrQuery.removePrefix("@").lowercase(Locale.US) }.toMutableSet()

        foundHandles.forEach { handle ->
            if (!existingQueries.contains(handle.lowercase(Locale.US))) {
                existingQueries.add(handle.lowercase(Locale.US))
                candidateLinks.add(
                    buildSingleCandidateLink(
                        rawQuery = handle,
                        clueReason = "फोटो से पहचाना गया असली Instagram Username (@$handle)"
                    )
                )
            }
        }

        foundTextClues.forEach { textClue ->
            val linksFromText = buildCandidateLinksForManualQuery(textClue)
            linksFromText.forEach { candidate ->
                val key = candidate.handleOrQuery.removePrefix("@").lowercase(Locale.US)
                if (!existingQueries.contains(key)) {
                    existingQueries.add(key)
                    candidateLinks.add(candidate)
                }
            }
        }

        val statusMsg = when {
            candidateLinks.isNotEmpty() ->
                aiSummary.ifBlank {
                    "फोटो से ${candidateLinks.size} संभावित असली Instagram प्रोफाइल लिंक मिल गए हैं! नीचे 'Instagram' बटन दबाकर सीधे प्रोफाइल खोलें।"
                }
            else ->
                "इस फोटो में कोई सीधा @username वॉटरमार्क नहीं मिला। आप नीचे फोटो से जुड़ा नाम या @username लिखकर खोज सकते हैं।"
        }

        PublicPhotoClueResult(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            photoPerceptualHash = photoHash,
            visibleHandles = foundHandles.toList(),
            visibleTextClues = foundTextClues.toList(),
            publicKeywords = foundKeywords.toList(),
            candidateLinks = candidateLinks,
            aiAnalysisUsed = aiUsed,
            clearMessage = statusMsg
        )
    }

    fun buildCandidateLinksForManualQuery(rawInput: String): List<CandidateProfileLink> {
        val cleanInput = SecurityShield.sanitizeInput(rawInput)
        if (cleanInput.isBlank()) return emptyList()

        val results = mutableListOf<CandidateProfileLink>()
        val withoutAt = cleanInput.removePrefix("@").trim()

        if (isValidInstagramUsername(withoutAt)) {
            val handle = withoutAt.lowercase(Locale.US)
            results.add(
                buildSingleCandidateLink(
                    rawQuery = handle,
                    clueReason = "Direct Official Instagram Username (@$handle)"
                )
            )
        }

        if (withoutAt.contains(" ") || !isValidInstagramUsername(withoutAt)) {
            val encodedFull = Uri.encode(withoutAt)
            results.add(
                CandidateProfileLink(
                    handleOrQuery = withoutAt,
                    title = "Public Search: \"$withoutAt\"",
                    instagramProfileUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedFull",
                    instagramSearchUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedFull",
                    publicWebSearchUrl = "https://www.google.com/search?q=${Uri.encode("site:instagram.com \"$withoutAt\"")}",
                    clueReason = "सार्वजनिक नाम / कीवर्ड वेब व Instagram सर्च (Public Name & Web Search)",
                    isValidHandleFormat = false
                )
            )

            val dotVariant = withoutAt.lowercase(Locale.US)
                .replace(Regex("\\s+"), ".")
                .replace(Regex("[^a-z0-9._]"), "")
            val compactVariant = withoutAt.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]"), "")

            if (isValidInstagramUsername(compactVariant) && compactVariant.length >= 3) {
                results.add(
                    buildSingleCandidateLink(
                        rawQuery = compactVariant,
                        clueReason = "संभावित सार्वजनिक Username वेरिएंट (@$compactVariant)"
                    )
                )
            }
            if (isValidInstagramUsername(dotVariant) && dotVariant != compactVariant && dotVariant.length >= 3) {
                results.add(
                    buildSingleCandidateLink(
                        rawQuery = dotVariant,
                        clueReason = "संभावित सार्वजनिक Username वेरिएंट (@$dotVariant)"
                    )
                )
            }
        }

        return results
    }

    private fun buildSingleCandidateLink(
        rawQuery: String,
        clueReason: String
    ): CandidateProfileLink {
        val clean = SecurityShield.sanitizeInput(rawQuery).removePrefix("@")
        val validHandle = isValidInstagramUsername(clean)
        val normalizedHandle = clean.lowercase(Locale.US).replace(Regex("[^a-z0-9._]"), "")
        val profileUrl = if (validHandle) {
            "https://www.instagram.com/$clean/"
        } else if (normalizedHandle.isNotBlank()) {
            "https://www.instagram.com/$normalizedHandle/"
        } else {
            "https://www.instagram.com/"
        }
        val searchUrl = "https://www.instagram.com/explore/search/keyword/?q=${Uri.encode(clean)}"
        val googleWebUrl = "https://www.google.com/search?q=${Uri.encode("site:instagram.com \"$clean\"")}"

        return CandidateProfileLink(
            handleOrQuery = if (validHandle) "@$clean" else clean,
            title = if (validHandle) "https://www.instagram.com/$clean/" else "Public Clue: $clean",
            instagramProfileUrl = profileUrl,
            instagramSearchUrl = searchUrl,
            publicWebSearchUrl = googleWebUrl,
            clueReason = clueReason,
            isValidHandleFormat = validHandle
        )
    }

    fun openOfficialInstagramLogin(context: Context) {
        val loginUrl = "https://www.instagram.com/accounts/login/"
        try {
            val appIntent = context.packageManager.getLaunchIntentForPackage("com.instagram.android")
            if (appIntent != null) {
                appIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(appIntent)
                return
            }
        } catch (_: Throwable) {
        }
        openUrl(context, loginUrl)
    }

    fun openOfficialInstagramSignUp(context: Context) {
        val signUpUrl = "https://www.instagram.com/accounts/emailsignup/"
        openUrl(context, signUpUrl)
    }

    fun openInstagramProfile(context: Context, rawHandleOrUrl: String) {
        if (rawHandleOrUrl.startsWith("https://")) {
            openUrl(context, rawHandleOrUrl)
            return
        }
        val cleanHandle = SecurityShield.sanitizeInput(rawHandleOrUrl)
            .removePrefix("@")
            .removePrefix("https://instagram.com/")
            .removePrefix("https://www.instagram.com/")
            .trim('/')
        if (cleanHandle.isBlank()) return

        val appUri = Uri.parse("https://www.instagram.com/_u/$cleanHandle")
        val webUrl = "https://www.instagram.com/$cleanHandle/"

        try {
            val appIntent = Intent(Intent.ACTION_VIEW, appUri).apply {
                setPackage("com.instagram.android")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appIntent)
        } catch (_: Throwable) {
            openUrl(context, webUrl)
        }
    }

    fun openInstagramHashtag(context: Context, hashtag: String) {
        val cleanTag = SecurityShield.sanitizeInput(hashtag).removePrefix("#")
        if (cleanTag.isBlank()) return
        val url = "https://www.instagram.com/explore/tags/${Uri.encode(cleanTag)}/"
        openUrl(context, url)
    }

    fun openInstagramLocationExplore(context: Context, areaName: String = "") {
        val cleanArea = SecurityShield.sanitizeInput(areaName)
        val url = if (cleanArea.isBlank()) {
            "https://www.instagram.com/explore/locations/"
        } else {
            "https://www.instagram.com/explore/search/keyword/?q=${Uri.encode(cleanArea)}"
        }
        openUrl(context, url)
    }

    fun openInstagramSearch(context: Context, query: String) {
        val cleanQuery = SecurityShield.sanitizeInput(query).removePrefix("@")
        if (cleanQuery.isBlank()) return
        val encoded = Uri.encode(cleanQuery)
        val searchUrl = "https://www.instagram.com/explore/search/keyword/?q=$encoded"
        openUrl(context, searchUrl)
    }

    fun openPublicWebSearch(context: Context, query: String) {
        val cleanQuery = SecurityShield.sanitizeInput(query).removePrefix("@")
        if (cleanQuery.isBlank()) return
        val webUrl = "https://www.google.com/search?q=${Uri.encode("site:instagram.com \"$cleanQuery\"")}"
        openUrl(context, webUrl)
    }

    fun openMetaAi(context: Context, query: String = "") {
        val cleanQuery = SecurityShield.sanitizeInput(query)
        val url = if (cleanQuery.isBlank()) {
            "https://www.meta.ai/"
        } else {
            "https://www.meta.ai/?q=${Uri.encode(cleanQuery)}"
        }
        openUrl(context, url)
    }

    fun openUrl(context: Context, url: String) {
        if (!SecurityShield.isTrustedHttpsUrl(url)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
        }
    }
}
