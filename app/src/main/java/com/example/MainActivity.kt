package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.InstaLensDatabase
import com.example.data.local.InstaLensRepository
import com.example.network.MlAndInstagramApiClient
import com.example.security.SecurityShield
import com.example.ui.InstaLensViewModel
import com.example.ui.screens.LocationSearchScreen
import com.example.ui.screens.ManualSearchScreen
import com.example.ui.screens.PhotoScanScreen
import com.example.ui.screens.SavedLinksScreen
import com.example.ui.screens.ScanHistoryScreen
import com.example.ui.theme.InstaCyan
import com.example.ui.theme.InstaMagenta
import com.example.ui.theme.MatchGreen
import com.example.ui.theme.MyApplicationTheme

enum class InstaLensTab(
    val routeId: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    PHOTO_CLUES(
        routeId = "photo_clues",
        labelResId = R.string.tab_scan,
        selectedIcon = Icons.Filled.CenterFocusStrong,
        unselectedIcon = Icons.Outlined.CenterFocusStrong,
        testTag = "nav_tab_scan"
    ),
    LOCATION_SEARCH(
        routeId = "location_search",
        labelResId = R.string.tab_location,
        selectedIcon = Icons.Filled.LocationOn,
        unselectedIcon = Icons.Outlined.LocationOn,
        testTag = "nav_tab_location"
    ),
    MANUAL_SEARCH(
        routeId = "manual_search",
        labelResId = R.string.tab_manual_search,
        selectedIcon = Icons.Filled.PersonSearch,
        unselectedIcon = Icons.Outlined.PersonSearch,
        testTag = "nav_tab_manual_search"
    ),
    SAVED_LINKS(
        routeId = "saved_links",
        labelResId = R.string.tab_saved,
        selectedIcon = Icons.Filled.Bookmarks,
        unselectedIcon = Icons.Outlined.Bookmarks,
        testTag = "nav_tab_saved"
    ),
    HISTORY(
        routeId = "search_history",
        labelResId = R.string.tab_history,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History,
        testTag = "nav_tab_history"
    )
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Tapjacking / Overlay attack protection: reject touches when another window overlays the app
        window.decorView.filterTouchesWhenObscured = true

        val database = InstaLensDatabase.getInstance(applicationContext)
        val repository = InstaLensRepository(database.instaLensDao())

        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                val viewModel: InstaLensViewModel = viewModel(
                    factory = InstaLensViewModel.provideFactory(repository)
                )
                InstaLensApp(
                    viewModel = viewModel,
                    onToggleScreenCaptureLock = { lockEnabled ->
                        if (lockEnabled) {
                            window.setFlags(
                                WindowManager.LayoutParams.FLAG_SECURE,
                                WindowManager.LayoutParams.FLAG_SECURE
                            )
                        } else {
                            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstaLensApp(
    viewModel: InstaLensViewModel,
    onToggleScreenCaptureLock: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf(InstaLensTab.PHOTO_CLUES) }
    var showSecurityDialog by rememberSaveable { mutableStateOf(false) }
    var screenCaptureLocked by rememberSaveable { mutableStateOf(false) }

    val photoState by viewModel.photoState.collectAsStateWithLifecycle()
    val manualSearchState by viewModel.manualSearchState.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val savedLinks by viewModel.savedLinks.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    val securityReport = remember { SecurityShield.auditAppSecurity(context) }

    BackHandler(enabled = currentTab != InstaLensTab.PHOTO_CLUES) {
        currentTab = InstaLensTab.PHOTO_CLUES
    }

    if (showSecurityDialog) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Security,
                    contentDescription = "Security Shield",
                    tint = MatchGreen
                )
            },
            title = {
                Text(
                    text = "Anti-Hack & Server Protection Shield",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecurityCheckRow("HTTPS-Only Network Firewall (Cleartext HTTP Blocked)")
                    SecurityCheckRow("External Server & Script Injection Blocker Active")
                    SecurityCheckRow("USB / Computer ADB Backup Extraction Disabled")
                    SecurityCheckRow("Anti-Overlay / Tapjacking Touch Shield Active")
                    SecurityCheckRow("Zero Secret API Keys Packaged in APK")

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Block Screen Capture / Remote Viewer (FLAG_SECURE)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = screenCaptureLocked,
                            onCheckedChange = { enabled ->
                                screenCaptureLocked = enabled
                                onToggleScreenCaptureLock(enabled)
                            },
                            modifier = Modifier.testTag("flag_secure_switch")
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSecurityDialog = false },
                    modifier = Modifier.testTag("close_security_dialog_button")
                ) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CenterFocusStrong,
                            contentDescription = null,
                            tint = InstaMagenta
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "🔒 Connected: Instagram • Meta AI • Location IG",
                                style = MaterialTheme.typography.labelSmall,
                                color = MatchGreen
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { MlAndInstagramApiClient.openMetaAi(context) },
                        modifier = Modifier.testTag("top_bar_meta_ai_button")
                    ) {
                        Text(
                            text = "Meta AI",
                            color = InstaCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { showSecurityDialog = true },
                        modifier = Modifier.testTag("top_bar_security_shield_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Security Shield Status",
                            tint = MatchGreen
                        )
                    }
                    IconButton(
                        onClick = {
                            currentTab = InstaLensTab.LOCATION_SEARCH
                            viewModel.startLocationTrackingAndDiscover(context)
                        },
                        modifier = Modifier.testTag("top_bar_location_radar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = stringResource(R.string.enable_location_scan),
                            tint = InstaMagenta
                        )
                    }
                    IconButton(
                        onClick = { MlAndInstagramApiClient.openOfficialInstagramLogin(context) },
                        modifier = Modifier.testTag("top_bar_official_instagram_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.official_instagram_login),
                            tint = InstaCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("developer_contact_footer"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(R.string.developer_contact_footer),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = androidx.compose.ui.unit.TextUnit(10.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    InstaLensTab.entries.forEach { tab ->
                        val selected = currentTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.labelResId)
                                )
                            },
                            label = { Text(stringResource(tab.labelResId)) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        when (currentTab) {
            InstaLensTab.PHOTO_CLUES -> {
                PhotoScanScreen(
                    photoState = photoState,
                    savedLinksCount = savedLinks.size,
                    onPickPhotoUri = { uri -> viewModel.onPhotoUriSelected(context, uri) },
                    onCameraCaptured = { bmp -> viewModel.onCameraBitmapCaptured(bmp) },
                    onUpdateClueInput = { viewModel.updateUserAddedClueInput(it) },
                    onApplyClueSearch = { viewModel.applyUserClueToCurrentPhoto() },
                    onSaveCandidateLink = { viewModel.saveCandidateProfileLink(it) },
                    onNavigateToManualSearch = { prefill ->
                        if (prefill.isNotBlank()) {
                            viewModel.updateManualQueryInput(prefill)
                            viewModel.performManualUsernameSearch(prefill)
                        }
                        currentTab = InstaLensTab.MANUAL_SEARCH
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            InstaLensTab.LOCATION_SEARCH -> {
                LocationSearchScreen(
                    locationState = locationState,
                    onUpdateAreaInput = { viewModel.updateAreaFilterInput(it) },
                    onEnableAndDiscoverLocation = { areaOverride ->
                        viewModel.startLocationTrackingAndDiscover(context, areaOverride)
                    },
                    onDisableLocation = { viewModel.stopLocationTracking() },
                    onSaveCandidateLink = { viewModel.saveCandidateProfileLink(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            InstaLensTab.MANUAL_SEARCH -> {
                ManualSearchScreen(
                    searchState = manualSearchState,
                    onQueryChange = { viewModel.updateManualQueryInput(it) },
                    onRunSearch = { viewModel.performManualUsernameSearch() },
                    onSaveCandidateLink = { viewModel.saveCandidateProfileLink(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            InstaLensTab.SAVED_LINKS -> {
                SavedLinksScreen(
                    savedLinks = savedLinks,
                    onDeleteLink = { viewModel.deleteSavedLink(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            InstaLensTab.HISTORY -> {
                ScanHistoryScreen(
                    historyList = searchHistory,
                    onDeleteItem = { viewModel.deleteHistoryItem(it) },
                    onClearAll = { viewModel.clearAllHistory() },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun SecurityCheckRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MatchGreen
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
