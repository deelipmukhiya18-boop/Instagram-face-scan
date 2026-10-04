package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.network.CandidateProfileLink
import com.example.network.MlAndInstagramApiClient
import com.example.ui.PhotoClueUiState
import com.example.ui.theme.InstaCyan
import com.example.ui.theme.InstaMagenta
import com.example.ui.theme.InstaOrange
import com.example.ui.theme.MatchGreen
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoScanScreen(
    photoState: PhotoClueUiState,
    savedLinksCount: Int,
    onPickPhotoUri: (android.net.Uri) -> Unit,
    onCameraCaptured: (android.graphics.Bitmap) -> Unit,
    onUpdateClueInput: (String) -> Unit,
    onApplyClueSearch: () -> Unit,
    onSaveCandidateLink: (CandidateProfileLink) -> Unit,
    onNavigateToManualSearch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPickPhotoUri(uri)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            onCameraCaptured(bitmap)
        }
    }

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
            // Safe & Official Instagram Login / Privacy Status Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("safe_instagram_auth_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MatchGreen.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MatchGreen.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Safe Official Instagram Integration",
                            tint = MatchGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "सुरक्षित और आधिकारिक Instagram इंटीग्रेशन",
                                style = MaterialTheme.typography.labelLarge,
                                color = MatchGreen,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "बिना फेशियल रिकग्निशन • केवल सार्वजनिक संकेतों और आधिकारिक वेब/ऐप सर्च का उपयोग",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { MlAndInstagramApiClient.openMetaAi(context, photoState.userAddedClueInput) },
                            modifier = Modifier.testTag("official_meta_ai_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Meta AI")
                        }
                        OutlinedButton(
                            onClick = { MlAndInstagramApiClient.openOfficialInstagramLogin(context) },
                            modifier = Modifier.testTag("official_instagram_login_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.official_instagram_login),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Instagram")
                        }
                    }
                }
            }

            // Hero Banner Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_banner_card"),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(164.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ml_hero_banner_1791054454477),
                        contentDescription = stringResource(R.string.hero_title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x660F1020),
                                        Color(0xEB0F1020)
                                    )
                                )
                            )
                            .padding(18.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = InstaMagenta.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(50),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, InstaMagenta)
                                ) {
                                    Text(
                                        text = "PUBLIC VISUAL CLUES & WEB SEARCH",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Surface(
                                    color = InstaCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "$savedLinksCount Saved Links",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = InstaCyan,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.hero_title),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = "फोटो में दिख रहे सार्वजनिक संकेतों (@handle वॉटरमार्क, नाम, ब्रांड) या मैन्युअल Username से खोजें",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFD5D7EC)
                            )
                        }
                    }
                }
            }

            // Feature 1: Upload Photo from Gallery or Camera
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "1. फोटो अपलोड करें (Upload Photo for Public Clues)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "गैलरी या कैमरे से फोटो चुनें। ऐप फोटो में दिख रहे सार्वजनिक और गैर-संवेदनशील संकेतों (जैसे @username वॉटरमार्क, पेज नाम या सार्वजनिक टेक्स्ट) की जाँच करेगा।",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("pick_photo_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.pick_photo_gallery)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = stringResource(R.string.pick_photo_gallery))
                        }

                        FilledTonalButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("camera_capture_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = stringResource(R.string.take_photo_camera)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.take_photo_camera))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                    // Feature 2: Public & Non-Sensitive Clue Input Helper
                    Text(
                        text = "2. फोटो में दिख रहा सार्वजनिक संकेत लिखें (Optional Public Clue):",
                        style = MaterialTheme.typography.labelLarge,
                        color = InstaCyan
                    )
                    OutlinedTextField(
                        value = photoState.userAddedClueInput,
                        onValueChange = onUpdateClueInput,
                        label = { Text("जैसे: फोटो पर लिखा @username, पेज का नाम, ब्रांड या इवेंट") },
                        placeholder = { Text("e.g. @natgeo या Studio Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("public_clue_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onApplyClueSearch,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("search_by_clue_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("सार्वजनिक संकेत से खोजें")
                        }

                        OutlinedButton(
                            onClick = { onNavigateToManualSearch(photoState.userAddedClueInput) },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("open_manual_search_tab_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PersonSearch, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Manual @ID")
                        }
                    }
                }
            }

            // Status / Progress Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_status_banner"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (photoState.isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Clue Status",
                            tint = InstaCyan
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = photoState.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (photoState.errorMessage != null) {
                            Text(
                                text = photoState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Uploaded Photo Preview Card
            AnimatedVisibility(visible = photoState.selectedBitmap != null) {
                val bmp = photoState.selectedBitmap
                if (bmp != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("uploaded_photo_preview_card"),
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
                                Text(
                                    text = "अपलोड की गई फोटो (Uploaded Photo)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${bmp.width} × ${bmp.height} px",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InstaCyan
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0A0B14))
                                    .border(1.dp, InstaCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Uploaded photo preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            val clueRes = photoState.clueResult
                            if (clueRes != null && (clueRes.visibleTextClues.isNotEmpty() || clueRes.publicKeywords.isNotEmpty())) {
                                Text(
                                    text = "फोटो में मिले सार्वजनिक टेक्स्ट/संकेत (Tap to Search):",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = InstaCyan
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    (clueRes.visibleTextClues + clueRes.publicKeywords).distinct().forEach { chipText ->
                                        AssistChip(
                                            onClick = { onNavigateToManualSearch(chipText) },
                                            label = { Text(chipText) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Feature 5 & 6: Candidate Public Profile Links OR Clear "No Profile Found" Message
            val clueResult = photoState.clueResult
            if (clueResult != null) {
                if (clueResult.candidateLinks.isNotEmpty()) {
                    CandidateLinksSection(
                        context = context,
                        title = "मिले हुए संभावित सार्वजनिक प्रोफाइल लिंक (${clueResult.candidateLinks.size})",
                        candidates = clueResult.candidateLinks,
                        onSaveCandidate = onSaveCandidateLink
                    )
                } else {
                    // Feature 6: Clear message when no profile is found
                    NoProfileFoundClearCard(
                        onSwitchToManualSearch = {
                            onNavigateToManualSearch(photoState.userAddedClueInput)
                        },
                        onOpenGoogleWebSearch = {
                            val q = photoState.userAddedClueInput.ifBlank { "instagram public profile" }
                            MlAndInstagramApiClient.openPublicWebSearch(context, q)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CandidateLinksSection(
    context: Context,
    title: String,
    candidates: List<CandidateProfileLink>,
    onSaveCandidate: (CandidateProfileLink) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_profile_links_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            brush = Brush.horizontalGradient(listOf(InstaMagenta, InstaOrange, InstaCyan))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            candidates.forEachIndexed { index, candidate ->
                CandidateLinkItemCard(
                    context = context,
                    candidate = candidate,
                    index = index,
                    onSave = {
                        onSaveCandidate(candidate)
                        Toast.makeText(context, "प्रोफाइल लिंक Saved Links में सेव हो गया", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun CandidateLinkItemCard(
    context: Context,
    candidate: CandidateProfileLink,
    index: Int,
    onSave: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("candidate_link_item_$index"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = candidate.handleOrQuery,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = InstaMagenta
                    )
                    Text(
                        text = candidate.clueReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.testTag("save_candidate_button_$index"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BookmarkBorder,
                        contentDescription = "Save Link",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }

            // Visible Public URL
            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = candidate.instagramProfileUrl,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = InstaCyan,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (candidate.isValidHandleFormat) {
                            MlAndInstagramApiClient.openInstagramProfile(context, candidate.handleOrQuery)
                        } else {
                            MlAndInstagramApiClient.openInstagramSearch(context, candidate.handleOrQuery)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_candidate_ig_button_$index"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InstaMagenta)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(R.string.open_in_instagram),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Instagram")
                }

                FilledTonalButton(
                    onClick = {
                        MlAndInstagramApiClient.openUrl(context, candidate.publicWebSearchUrl)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("open_candidate_web_button_$index"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = stringResource(R.string.search_public_web),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Web Search")
                }

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText("Instagram Profile URL", candidate.instagramProfileUrl)
                        )
                        Toast.makeText(context, "Copied: ${candidate.instagramProfileUrl}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("copy_candidate_link_button_$index"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.copy_instagram_id),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoProfileFoundClearCard(
    onSwitchToManualSearch: () -> Unit,
    onOpenGoogleWebSearch: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("no_profile_found_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, WarningAmber.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "No Profile Found",
                    tint = WarningAmber
                )
                Text(
                    text = stringResource(R.string.no_profile_found_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WarningAmber
                )
            }

            Text(
                text = stringResource(R.string.no_profile_found_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSwitchToManualSearch,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("no_result_manual_search_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Manual Username Search")
                }

                OutlinedButton(
                    onClick = onOpenGoogleWebSearch,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("no_result_web_search_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Web Search")
                }
            }
        }
    }
}
