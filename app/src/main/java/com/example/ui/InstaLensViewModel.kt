package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.InstaLensRepository
import com.example.data.local.SavedProfileLinkEntity
import com.example.data.local.SearchHistoryEntity
import com.example.network.AreaLocationInfo
import com.example.network.CandidateProfileLink
import com.example.network.MlAndInstagramApiClient
import com.example.network.PublicPhotoClueResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PhotoClueUiState(
    val selectedBitmap: Bitmap? = null,
    val uploadedFileName: String = "",
    val isAnalyzing: Boolean = false,
    val clueResult: PublicPhotoClueResult? = null,
    val userAddedClueInput: String = "",
    val statusMessage: String = "फोटो अपलोड करें — ऐप फोटो से जुड़े सार्वजनिक संकेत, पब्लिक क्रिएटर या आपके सेव किए गए फोटो इंडेक्स से Instagram ID खोजेगा।",
    val errorMessage: String? = null
)

data class ManualSearchUiState(
    val queryInput: String = "",
    val candidateLinks: List<CandidateProfileLink> = emptyList(),
    val hasSearched: Boolean = false,
    val statusMessage: String = "Instagram Username (@handle) या सार्वजनिक नाम डालकर आधिकारिक प्रोफाइल लिंक और Google Web Search खोजें।"
)

data class LocationDiscoveryUiState(
    val isLocationEnabled: Boolean = false,
    val isTracking: Boolean = false,
    val areaFilterInput: String = "",
    val detectedLocationInfo: AreaLocationInfo? = null,
    val statusMessage: String = "लोकेशन चालू (Turn ON) करते ही आपके एरिया के सभी सार्वजनिक Instagram IDs, लोकल पेज और लोकेशन टैग अपने आप दिख जाएंगे।"
)

class InstaLensViewModel(
    private val repository: InstaLensRepository
) : ViewModel() {

    val savedLinks: StateFlow<List<SavedProfileLinkEntity>> = repository.savedLinks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = repository.searchHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _photoState = MutableStateFlow(PhotoClueUiState())
    val photoState: StateFlow<PhotoClueUiState> = _photoState.asStateFlow()

    private val _manualSearchState = MutableStateFlow(ManualSearchUiState())
    val manualSearchState: StateFlow<ManualSearchUiState> = _manualSearchState.asStateFlow()

    private val _locationState = MutableStateFlow(LocationDiscoveryUiState())
    val locationState: StateFlow<LocationDiscoveryUiState> = _locationState.asStateFlow()

    fun updateUserAddedClueInput(input: String) {
        _photoState.update { it.copy(userAddedClueInput = input, errorMessage = null) }
    }

    fun updateManualQueryInput(input: String) {
        _manualSearchState.update { it.copy(queryInput = input) }
    }

    fun updateAreaFilterInput(input: String) {
        _locationState.update { it.copy(areaFilterInput = input) }
    }

    /**
     * Turns ON location tracking, reads real device GPS/Network coordinates, reverse-geocodes the area,
     * and automatically populates all public Instagram IDs & location feeds for that area.
     */
    @SuppressLint("MissingPermission")
    fun startLocationTrackingAndDiscover(context: Context, customAreaOverride: String = "") {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        _locationState.update {
            it.copy(
                isLocationEnabled = true,
                isTracking = true,
                statusMessage = "GPS लोकेशन ट्रैक हो रही है और इस एरिया के Instagram IDs खोजे जा रहे हैं..."
            )
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        var bestLocation: Location? = null

        if ((hasFine || hasCoarse) && locationManager != null) {
            try {
                val providers = listOf(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER
                )
                for (provider in providers) {
                    if (locationManager.isProviderEnabled(provider)) {
                        val loc = locationManager.getLastKnownLocation(provider)
                        if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                            bestLocation = loc
                        }
                    }
                }

                // Also register a single one-shot listener in case a fresher fix arrives
                val activeProvider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (activeProvider != null && bestLocation == null) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            resolveAreaInstagramProfiles(
                                context = context,
                                lat = location.latitude,
                                lon = location.longitude,
                                areaOverride = customAreaOverride
                            )
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    locationManager.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            } catch (_: Exception) {
            }
        }

        // Immediately resolve using the best available GPS coordinates (or area input / default India coordinates if emulator has no GPS fix yet)
        val lat = bestLocation?.latitude ?: 25.5941
        val lon = bestLocation?.longitude ?: 85.1376
        val effectiveArea = customAreaOverride.ifBlank { _locationState.value.areaFilterInput }

        resolveAreaInstagramProfiles(
            context = context,
            lat = lat,
            lon = lon,
            areaOverride = effectiveArea
        )
    }

    fun stopLocationTracking() {
        _locationState.update {
            it.copy(
                isLocationEnabled = false,
                isTracking = false,
                statusMessage = "लोकेशन ट्रैकिंग बंद है। अपने एरिया के Instagram IDs देखने के लिए लोकेशन चालू करें।"
            )
        }
    }

    private fun resolveAreaInstagramProfiles(
        context: Context,
        lat: Double?,
        lon: Double?,
        areaOverride: String
    ) {
        viewModelScope.launch {
            val info = MlAndInstagramApiClient.discoverInstagramIdsForArea(
                context = context,
                latitude = lat,
                longitude = lon,
                manualAreaOverride = areaOverride
            )

            repository.insertHistoryItem(
                SearchHistoryEntity(
                    queryOrClue = "📍 ${info.displayAddress}",
                    searchType = "Location Area Instagram Search",
                    primaryUrl = info.areaCandidateLinks.firstOrNull()?.instagramProfileUrl ?: "",
                    candidateCount = info.areaCandidateLinks.size,
                    summaryMessage = info.statusSummary
                )
            )

            _locationState.update {
                it.copy(
                    isLocationEnabled = true,
                    isTracking = false,
                    detectedLocationInfo = info,
                    statusMessage = info.statusSummary
                )
            }
        }
    }

    fun onPhotoUriSelected(context: Context, uri: Uri) {
        viewModelScope.launch {
            _photoState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    statusMessage = "फोटो स्कैन की जा रही है और Instagram प्रोफाइल लिंक खोजा जा रहा है..."
                )
            }

            val fileName = withContext(Dispatchers.IO) {
                resolveFileName(context, uri)
            }

            val decodedBitmap = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val original = BitmapFactory.decodeStream(stream)
                        original?.let { scaleDownIfNeeded(it, 800) }
                    }
                } catch (_: Exception) {
                    null
                }
            }

            if (decodedBitmap != null) {
                analyzeUploadedBitmap(decodedBitmap, fileName)
            } else {
                _photoState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = "चुनी गई फोटो को पढ़ने में समस्या आई। कृपया दूसरी फोटो चुनें।"
                    )
                }
            }
        }
    }

    fun onCameraBitmapCaptured(bitmap: Bitmap) {
        viewModelScope.launch {
            val scaled = scaleDownIfNeeded(bitmap, 800)
            analyzeUploadedBitmap(scaled, _photoState.value.userAddedClueInput)
        }
    }

    fun applyUserClueToCurrentPhoto() {
        val current = _photoState.value
        val clueText = current.userAddedClueInput.trim()
        if (clueText.isBlank()) {
            _photoState.update {
                it.copy(errorMessage = "कृपया फोटो से जुड़ा कोई Instagram @username या नाम लिखें।")
            }
            return
        }

        viewModelScope.launch {
            val bmp = current.selectedBitmap
            if (bmp != null) {
                val photoHash = withContext(Dispatchers.Default) {
                    MlAndInstagramApiClient.computePerceptualHash(bmp)
                }
                val candidates = MlAndInstagramApiClient.buildCandidateLinksForManualQuery(clueText)
                val primaryCandidate = candidates.firstOrNull()
                if (primaryCandidate != null) {
                    repository.insertSavedLink(
                        SavedProfileLinkEntity(
                            usernameOrQuery = primaryCandidate.handleOrQuery,
                            displayTitle = primaryCandidate.title,
                            instagramUrl = primaryCandidate.instagramProfileUrl,
                            publicWebSearchUrl = primaryCandidate.publicWebSearchUrl,
                            clueSource = "Photo Linked to ${primaryCandidate.handleOrQuery}",
                            notes = "64-Bit Perceptual Photo Hash Linked",
                            photoSignatureHash = photoHash
                        )
                    )
                }
                analyzeUploadedBitmap(bmp, clueText)
            } else {
                val candidates = MlAndInstagramApiClient.buildCandidateLinksForManualQuery(clueText)
                val result = PublicPhotoClueResult(
                    imageWidth = 0,
                    imageHeight = 0,
                    photoPerceptualHash = "",
                    visibleHandles = candidates.filter { it.isValidHandleFormat }.map { it.handleOrQuery },
                    visibleTextClues = listOf(clueText),
                    publicKeywords = emptyList(),
                    candidateLinks = candidates,
                    aiAnalysisUsed = false,
                    clearMessage = if (candidates.isNotEmpty()) {
                        "\"$clueText\" के लिए ${candidates.size} संभावित Instagram प्रोफाइल लिंक तैयार हैं।"
                    } else {
                        "कोई संभावित सार्वजनिक प्रोफाइल लिंक नहीं मिला।"
                    }
                )
                repository.insertHistoryItem(
                    SearchHistoryEntity(
                        queryOrClue = clueText,
                        searchType = "Public Clue Search",
                        primaryUrl = candidates.firstOrNull()?.instagramProfileUrl ?: "",
                        candidateCount = candidates.size,
                        summaryMessage = result.clearMessage
                    )
                )
                _photoState.update {
                    it.copy(
                        clueResult = result,
                        statusMessage = result.clearMessage,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private suspend fun analyzeUploadedBitmap(bitmap: Bitmap, clueNote: String) {
        _photoState.update {
            it.copy(
                selectedBitmap = bitmap,
                uploadedFileName = clueNote,
                isAnalyzing = true,
                errorMessage = null
            )
        }

        val combinedClue = listOf(clueNote, _photoState.value.userAddedClueInput)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")

        val savedSnapshot = repository.getSavedLinksSnapshot()
        val result = MlAndInstagramApiClient.analyzePhotoForPublicClues(
            bitmap = bitmap,
            fileNameOrNote = combinedClue,
            savedDirectory = savedSnapshot
        )

        repository.insertHistoryItem(
            SearchHistoryEntity(
                queryOrClue = result.candidateLinks.firstOrNull()?.handleOrQuery
                    ?: combinedClue.ifBlank { "Uploaded Photo (${bitmap.width}x${bitmap.height})" },
                searchType = "Photo Scan & Clue Match",
                primaryUrl = result.candidateLinks.firstOrNull()?.instagramProfileUrl ?: "",
                candidateCount = result.candidateLinks.size,
                summaryMessage = result.clearMessage
            )
        )

        _photoState.update {
            it.copy(
                selectedBitmap = bitmap,
                isAnalyzing = false,
                clueResult = result,
                statusMessage = result.clearMessage,
                errorMessage = null
            )
        }
    }

    fun performManualUsernameSearch(rawQuery: String = _manualSearchState.value.queryInput) {
        val cleanQuery = rawQuery.trim()
        if (cleanQuery.isBlank()) {
            _manualSearchState.update {
                it.copy(
                    hasSearched = true,
                    candidateLinks = emptyList(),
                    statusMessage = "कृपया खोजने के लिए कोई Instagram Username (@handle) या सार्वजनिक नाम लिखें।"
                )
            }
            return
        }

        viewModelScope.launch {
            val links = MlAndInstagramApiClient.buildCandidateLinksForManualQuery(cleanQuery)
            val msg = if (links.isNotEmpty()) {
                "\"$cleanQuery\" के लिए ${links.size} संभावित सार्वजनिक प्रोफाइल और वेब सर्च लिंक मिले हैं।"
            } else {
                "\"$cleanQuery\" के लिए कोई मान्य सार्वजनिक Instagram Username पैटर्न नहीं मिला। कृपया सही @username लिखें।"
            }

            repository.insertHistoryItem(
                SearchHistoryEntity(
                    queryOrClue = cleanQuery,
                    searchType = "Manual Username Search",
                    primaryUrl = links.firstOrNull()?.instagramProfileUrl ?: "",
                    candidateCount = links.size,
                    summaryMessage = msg
                )
            )

            _manualSearchState.update {
                it.copy(
                    queryInput = cleanQuery,
                    candidateLinks = links,
                    hasSearched = true,
                    statusMessage = msg
                )
            }
        }
    }

    fun saveCandidateProfileLink(candidate: CandidateProfileLink) {
        viewModelScope.launch {
            val currentHash = _photoState.value.clueResult?.photoPerceptualHash.orEmpty()
            repository.insertSavedLink(
                SavedProfileLinkEntity(
                    usernameOrQuery = candidate.handleOrQuery,
                    displayTitle = candidate.title,
                    instagramUrl = candidate.instagramProfileUrl,
                    publicWebSearchUrl = candidate.publicWebSearchUrl,
                    clueSource = candidate.clueReason,
                    notes = if (candidate.isValidHandleFormat) {
                        "Valid Official Instagram Handle Format"
                    } else {
                        "Public Keyword / Name Search Link"
                    },
                    photoSignatureHash = currentHash
                )
            )
        }
    }

    fun deleteSavedLink(id: Int) {
        viewModelScope.launch {
            repository.deleteSavedLink(id)
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        var result = ""
        if (uri.scheme == "content") {
            val cursor: Cursor? = try {
                context.contentResolver.query(uri, null, null, null, null)
            } catch (_: Exception) {
                null
            }
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index).orEmpty()
                    }
                }
            }
        }
        if (result.isBlank()) {
            result = uri.lastPathSegment.orEmpty()
        }
        return result
    }

    private fun scaleDownIfNeeded(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val (newW, newH) = if (ratio > 1f) {
            maxDimension to (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            (maxDimension * ratio).toInt().coerceAtLeast(1) to maxDimension
        }
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    companion object {
        fun provideFactory(repository: InstaLensRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return InstaLensViewModel(repository) as T
                }
            }
        }
    }
}
