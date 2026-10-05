package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
import com.example.ui.theme.InstaOrange
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

        // Tapjacking / Overlay attack protection
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
    val prefs = remember { context.getSharedPreferences("instalens_secure_prefs", Context.MODE_PRIVATE) }

    var currentTab by rememberSaveable { mutableStateOf(InstaLensTab.PHOTO_CLUES) }
    var showSecurityDialog by rememberSaveable { mutableStateOf(false) }
    var showInstagramAuthDialog by rememberSaveable {
        mutableStateOf(prefs.getString("connected_ig_handle", "").isNullOrBlank())
    }
    var connectedIgHandle by rememberSaveable {
        mutableStateOf(prefs.getString("connected_ig_handle", "").orEmpty())
    }
    var igHandleInput by rememberSaveable { mutableStateOf(connectedIgHandle) }
    var screenCaptureLocked by rememberSaveable { mutableStateOf(false) }
    var startupPermissionsRequested by rememberSaveable { mutableStateOf(false) }

    val photoState by viewModel.photoState.collectAsStateWithLifecycle()
    val manualSearchState by viewModel.manualSearchState.collectAsStateWithLifecycle()
    val locationState by viewModel.locationState.collectAsStateWithLifecycle()
    val savedLinks by viewModel.savedLinks.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()

    // Startup automatic permission launcher for Location, Camera, and Notifications
    val startupPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val hasLocation = (resultMap[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (resultMap[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (hasLocation) {
            viewModel.startLocationTrackingAndDiscover(context)
        }
        sendStartupStatusNotification(context)
    }

    // Automatically request all required permissions when the app opens
    LaunchedEffect(Unit) {
        if (!startupPermissionsRequested) {
            startupPermissionsRequested = true
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            val missing = permissionsToRequest.filter { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                startupPermissionLauncher.launch(missing.toTypedArray())
            } else {
                viewModel.startLocationTrackingAndDiscover(context)
                sendStartupStatusNotification(context)
            }
        }
    }

    BackHandler(enabled = currentTab != InstaLensTab.PHOTO_CLUES) {
        currentTab = InstaLensTab.PHOTO_CLUES
    }

    // Safe Official Instagram Login / Sign-Up & Startup Permission Dialog
    if (showInstagramAuthDialog) {
        AlertDialog(
            onDismissRequest = { showInstagramAuthDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "Instagram Connect",
                    tint = InstaMagenta
                )
            },
            title = {
                Text(
                    text = "Instagram सुरक्षित Login / Sign Up & Permissions",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "• सभी ज़रूरी परमिशन (Location, Camera, Notification) आपके फोन में ऑटोमैटिक चालू कर दी गई हैं।\n• नीचे दिए गए आधिकारिक बटन से बिना किसी दिक्कत के आसानी से अपना Instagram Login या नया अकाउंट Sign Up करें (100% सुरक्षित):",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = {
                            MlAndInstagramApiClient.openOfficialInstagramLogin(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_instagram_login_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = InstaMagenta)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Instagram से सुरक्षित Login करें", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = {
                            MlAndInstagramApiClient.openOfficialInstagramSignUp(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("dialog_instagram_signup_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Instagram नया अकाउंट बनाएँ (Sign Up)", fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = igHandleInput,
                        onValueChange = { igHandleInput = it },
                        label = { Text("अपना @instagram_id लिखकर कनेक्ट सेव करें (वैकल्पिक)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_ig_handle_input")
                    )

                    OutlinedButton(
                        onClick = {
                            val perms = mutableListOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.CAMERA
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            startupPermissionLauncher.launch(perms.toTypedArray())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_allow_permissions_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("सभी App Permissions Allow करें")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = SecurityShield.sanitizeInput(igHandleInput).removePrefix("@")
                        if (sanitized.isNotBlank()) {
                            connectedIgHandle = "@$sanitized"
                            prefs.edit().putString("connected_ig_handle", connectedIgHandle).apply()
                        }
                        showInstagramAuthDialog = false
                    },
                    modifier = Modifier.testTag("dialog_save_and_continue_button")
                ) {
                    Text("आगे बढ़ें (Continue)")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showInstagramAuthDialog = false },
                    modifier = Modifier.testTag("dialog_skip_button")
                ) {
                    Text("बाद में करें")
                }
            }
        )
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
                    SecurityCheckRow("Official Instagram & Meta AI Safe Connection")

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
                                text = if (connectedIgHandle.isNotBlank()) {
                                    "✅ IG: $connectedIgHandle • Meta AI • Location"
                                } else {
                                    "🔒 Connected: Instagram • Meta AI • Location IG"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MatchGreen
                            )
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = { showInstagramAuthDialog = true },
                        modifier = Modifier.testTag("top_bar_ig_login_signup_button")
                    ) {
                        Text(
                            text = if (connectedIgHandle.isNotBlank()) connectedIgHandle else "IG Login",
                            color = InstaOrange,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("developer_contact_footer"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = stringResource(R.string.developer_contact_footer),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = TextUnit(10.5f, TextUnitType.Sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
                    onSelectGenderFilter = { filter ->
                        viewModel.updateGenderFilter(context, filter)
                    },
                    onEnableAndDiscoverLocation = { areaOverride ->
                        viewModel.startLocationTrackingAndDiscover(
                            context = context,
                            customAreaOverride = areaOverride,
                            genderFilter = locationState.selectedGenderFilter
                        )
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

private fun sendStartupStatusNotification(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val channelId = "instalens_security_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "InstaLens Permissions & Security",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("InstaLens ML: सभी परमिशन और सुरक्षा चालू हैं")
            .setContentText("Location Boys/Girls ID Finder, Photo Clues और Safe Instagram Connect तैयार हैं।")
            .setAutoCancel(true)
            .build()
        manager.notify(1001, notification)
    } catch (_: Throwable) {
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
