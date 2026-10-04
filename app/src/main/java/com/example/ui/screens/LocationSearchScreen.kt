package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.CandidateProfileLink
import com.example.network.MlAndInstagramApiClient
import com.example.ui.LocationDiscoveryUiState
import com.example.ui.theme.InstaCyan
import com.example.ui.theme.InstaMagenta
import com.example.ui.theme.InstaOrange
import com.example.ui.theme.MatchGreen
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationSearchScreen(
    locationState: LocationDiscoveryUiState,
    onUpdateAreaInput: (String) -> Unit,
    onEnableAndDiscoverLocation: (String) -> Unit,
    onDisableLocation: () -> Unit,
    onSaveCandidateLink: (CandidateProfileLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Whether GPS permission is granted or fallback area is used, run discovery immediately
        onEnableAndDiscoverLocation(locationState.areaFilterInput)
    }

    val quickAreas = listOf(
        "Patna, Bihar",
        "Darbhanga, Bihar",
        "New Delhi",
        "Mumbai",
        "Lucknow, UP",
        "Jaipur, Rajasthan"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Nearby Location → Area Instagram IDs",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "लोकेशन चालू (ON) करते ही उस एरिया के सार्वजनिक Instagram IDs, लोकल क्रिएटर्स, सिटी पेज और लोकेशन फीड अपने आप खुल जाएंगे।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 1. Live GPS Location Toggle & Radar Control Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_control_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        if (locationState.isLocationEnabled) {
                            listOf(MatchGreen, InstaCyan)
                        } else {
                            listOf(InstaMagenta, InstaOrange)
                        }
                    )
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (locationState.isLocationEnabled) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                                contentDescription = "GPS Status",
                                tint = if (locationState.isLocationEnabled) MatchGreen else InstaMagenta,
                                modifier = Modifier.size(26.dp)
                            )
                            Column {
                                Text(
                                    text = if (locationState.isLocationEnabled) {
                                        "Location Tracking: ON (एरिया Instagram IDs एक्टिव)"
                                    } else {
                                        "Location Tracking: OFF (लोकेशन चालू करें)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (locationState.isLocationEnabled) MatchGreen else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "चालू करते ही आपके एरिया के पब्लिक Instagram अकाउंट अपने आप दिखेंगे",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = locationState.isLocationEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    onDisableLocation()
                                }
                            },
                            modifier = Modifier.testTag("location_enable_switch")
                        )
                    }

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_location_scan_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InstaMagenta)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = stringResource(R.string.enable_location_scan)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "लोकेशन चालू करें और एरिया के Instagram ID खोजें",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Custom Area / City / District Input
                    Text(
                        text = "या किसी भी शहर / गाँव / एरिया का नाम डालकर वहाँ के Instagram IDs देखें:",
                        style = MaterialTheme.typography.labelLarge,
                        color = InstaCyan
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = locationState.areaFilterInput,
                            onValueChange = onUpdateAreaInput,
                            label = { Text("शहर / जिला / एरिया (e.g. Patna, Darbhanga, Delhi)") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("location_area_input")
                        )

                        FilledTonalButton(
                            onClick = {
                                onEnableAndDiscoverLocation(locationState.areaFilterInput)
                            },
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("search_area_ig_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search Area")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("खोजें")
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickAreas.forEachIndexed { idx, area ->
                            AssistChip(
                                onClick = {
                                    onUpdateAreaInput(area)
                                    onEnableAndDiscoverLocation(area)
                                },
                                label = { Text(area) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = InstaCyan
                                    )
                                },
                                modifier = Modifier.testTag("quick_area_chip_$idx")
                            )
                        }
                    }
                }
            }

            // Status Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("location_status_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (locationState.isTracking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Location Status",
                            tint = InstaCyan
                        )
                    }
                    Text(
                        text = locationState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Automatically Displayed Area Instagram Info & Candidate Profiles
            val info = locationState.detectedLocationInfo
            AnimatedVisibility(visible = info != null && locationState.isLocationEnabled) {
                if (info != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Detected Area Summary + Official Instagram Location Map Button
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("detected_area_summary_card"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📍 Detected Area: ${info.displayAddress}",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MatchGreen
                                        )
                                        if (info.latitude != null && info.longitude != null) {
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "GPS Coordinates: %.4f° N, %.4f° E",
                                                    info.latitude,
                                                    info.longitude
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = InstaCyan
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = {
                                        MlAndInstagramApiClient.openInstagramLocationExplore(
                                            context,
                                            info.displayAddress
                                        )
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("open_official_ig_locations_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = InstaOrange)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Instagram पर '${info.displayAddress}' के सभी लोकेशन पोस्ट खोलें",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (info.areaHashtags.isNotEmpty()) {
                                    Text(
                                        text = "इस एरिया के लाइव Instagram Hashtags (टैप करके खोलें):",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = InstaCyan
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        info.areaHashtags.forEach { tag ->
                                            AssistChip(
                                                onClick = {
                                                    MlAndInstagramApiClient.openInstagramHashtag(context, tag)
                                                },
                                                label = { Text("#$tag") },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Default.Tag,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(15.dp),
                                                        tint = InstaMagenta
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Automatic List of Area Public Instagram IDs
                        CandidateLinksSection(
                            context = context,
                            title = "इस लोकेशन/एरिया के सार्वजनिक Instagram IDs (${info.areaCandidateLinks.size})",
                            candidates = info.areaCandidateLinks,
                            onSaveCandidate = onSaveCandidateLink
                        )
                    }
                }
            }
        }
    }
}
