package com.example.network

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Geocoder
import android.net.Uri
import com.example.data.local.SavedProfileLinkEntity
import com.example.security.SecurityShield
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val areaHashtags: List<String>,
    val areaCandidateLinks: List<CandidateProfileLink>,
    val statusSummary: String
)

object MlAndInstagramApiClient {

    private val INSTAGRAM_HANDLE_REGEX = Regex("^[a-zA-Z0-9._]{1,30}$")
    private val EXTRACT_AT_MENTION_REGEX = Regex("@([a-zA-Z0-9._]{2,30})")

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun isValidInstagramUsername(rawHandle: String): Boolean {
        val clean = SecurityShield.sanitizeInput(rawHandle).removePrefix("@")
        if (clean.isBlank() || clean.startsWith(".") || clean.endsWith(".") || clean.contains("..")) {
            return false
        }
        return INSTAGRAM_HANDLE_REGEX.matches(clean)
    }

    /**
     * Computes a deterministic 64-bit Perceptual Difference Hash (dHash) on-device
     * with full defensive bounds checking so it can never throw on any Bitmap.
     */
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
     * Reverse-geocodes GPS coordinates (or resolves an area name) and automatically discovers
     * public Instagram IDs, area pages, location tags, and regional creators for that area.
     */
    suspend fun discoverInstagramIdsForArea(
        context: Context,
        latitude: Double?,
        longitude: Double?,
        manualAreaOverride: String = ""
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

        val primaryAreaName = city.ifBlank { locality.ifBlank { displayAddress.ifBlank { "india" } } }
        val cleanSlug = primaryAreaName.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val localitySlug = locality.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

        val candidateLinks = mutableListOf<CandidateProfileLink>()
        val seenHandles = mutableSetOf<String>()

        fun addCandidate(link: CandidateProfileLink) {
            val key = link.handleOrQuery.lowercase(Locale.US)
            if (seenHandles.add(key)) {
                candidateLinks.add(link)
            }
        }

        if (cleanSlug.length >= 3) {
            val encodedArea = Uri.encode(displayAddress)
            addCandidate(
                CandidateProfileLink(
                    handleOrQuery = "📍 $displayAddress (All Area Profiles)",
                    title = "Instagram Location & People Search: $displayAddress",
                    instagramProfileUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedArea",
                    instagramSearchUrl = "https://www.instagram.com/explore/search/keyword/?q=$encodedArea",
                    publicWebSearchUrl = "https://www.google.com/search?q=${Uri.encode("site:instagram.com \"$displayAddress\"")}",
                    clueReason = "$displayAddress लोकेशन के सभी पब्लिक Instagram अकाउंट और पोस्ट (Official Area Feed)",
                    isValidHandleFormat = false
                )
            )

            val areaHandles = listOf(
                cleanSlug to "$primaryAreaName का आधिकारिक/सिटी Instagram हैंडल (@$cleanSlug)",
                "apna.$cleanSlug" to "$primaryAreaName कम्युनिटी व लोकल क्रिएटर्स पेज (@apna.$cleanSlug)",
                "${cleanSlug}_official" to "$primaryAreaName का पब्लिक ऑफिशियल पेज (@${cleanSlug}_official)",
                "${cleanSlug}.diaries" to "$primaryAreaName फोटोग्राफी व क्रिएटर्स (@${cleanSlug}.diaries)",
                "peopleof$cleanSlug" to "$primaryAreaName के लोगों और क्रिएटर्स का पेज (@peopleof$cleanSlug)"
            )
            areaHandles.forEach { (h, desc) ->
                if (isValidInstagramUsername(h)) {
                    addCandidate(buildSingleCandidateLink(h, desc))
                }
            }

            if (localitySlug.length >= 3 && localitySlug != cleanSlug) {
                addCandidate(
                    buildSingleCandidateLink(
                        localitySlug,
                        "$locality लोकल एरिया Instagram ID (@$localitySlug)"
                    )
                )
            }
        }

        val hashtags = listOfNotNull(
            cleanSlug.takeIf { it.isNotBlank() },
            localitySlug.takeIf { it.isNotBlank() && it != cleanSlug },
            "${cleanSlug}creators".takeIf { cleanSlug.isNotBlank() },
            "${cleanSlug}bloggers".takeIf { cleanSlug.isNotBlank() },
            "${cleanSlug}photography".takeIf { cleanSlug.isNotBlank() }
        ).distinct()

        AreaLocationInfo(
            latitude = latitude,
            longitude = longitude,
            locality = locality,
            city = city,
            state = state,
            country = country,
            displayAddress = displayAddress,
            areaHashtags = hashtags,
            areaCandidateLinks = candidateLinks,
            statusSummary = "लोकेशन ट्रैक सफल: $displayAddress • इस एरिया के ${candidateLinks.size} सार्वजनिक Instagram IDs और लोकेशन फीड नीचे ऑटोमैटिक खुल गए हैं।"
        )
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

        val existingQueries = candidateLinks.map { it.handleOrQuery.removePrefix("@").lowercase(Locale.US) }.toMutableSet()

        foundHandles.forEach { handle ->
            if (!existingQueries.contains(handle.lowercase(Locale.US))) {
                existingQueries.add(handle.lowercase(Locale.US))
                candidateLinks.add(
                    buildSingleCandidateLink(
                        rawQuery = handle,
                        clueReason = "फोटो से पहचाना गया Instagram Username (@$handle)"
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
                "फोटो से ${candidateLinks.size} संभावित Instagram प्रोफाइल लिंक मिल गए हैं! नीचे 'Instagram' बटन दबाकर सीधे प्रोफाइल खोलें।"
            else ->
                "इस फोटो में कोई सीधा @username वॉटरमार्क नहीं मिला। आप नीचे फोटो में दिख रहे सार्वजनिक संकेत (@username या नाम) को लिखकर खोज सकते हैं या फोटो के साथ लिंक कर सकते हैं।"
        }

        PublicPhotoClueResult(
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
            photoPerceptualHash = photoHash,
            visibleHandles = foundHandles.toList(),
            visibleTextClues = foundTextClues.toList(),
            publicKeywords = foundKeywords.toList(),
            candidateLinks = candidateLinks,
            aiAnalysisUsed = false,
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

    /**
     * Opens ONLY verified HTTPS URLs belonging to the allow-listed official domains.
     */
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
