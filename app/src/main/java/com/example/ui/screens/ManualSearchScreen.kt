package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.network.CandidateProfileLink
import com.example.network.MlAndInstagramApiClient
import com.example.ui.ManualSearchUiState
import com.example.ui.theme.InstaCyan
import com.example.ui.theme.MatchGreen
import com.example.ui.theme.WarningAmber

@Composable
fun ManualSearchScreen(
    searchState: ManualSearchUiState,
    onQueryChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onSaveCandidateLink: (CandidateProfileLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val rawClean = searchState.queryInput.trim().removePrefix("@")
    val isValidHandle = rawClean.isNotBlank() && MlAndInstagramApiClient.isValidInstagramUsername(rawClean)

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
                text = "Manual Instagram Username & Public Web Search",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "किसी भी व्यक्ति या पेज का Instagram Username (@handle) या सार्वजनिक नाम लिखकर आधिकारिक Instagram प्रोफाइल और Google Web Search (site:instagram.com) पर खोजें।",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_username_search_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchState.queryInput,
                        onValueChange = onQueryChange,
                        label = { Text("Instagram Username (@handle) या सार्वजनिक नाम") },
                        placeholder = { Text("e.g. @virat.kohli या Virat Kohli") },
                        leadingIcon = {
                            Icon(Icons.Default.PersonSearch, contentDescription = null, tint = InstaCyan)
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_username_input")
                    )

                    if (searchState.queryInput.isNotBlank()) {
                        Surface(
                            color = if (isValidHandle) MatchGreen.copy(alpha = 0.14f) else InstaCyan.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isValidHandle) Icons.Default.CheckCircle else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (isValidHandle) MatchGreen else InstaCyan
                                )
                                Text(
                                    text = if (isValidHandle) {
                                        "मान्य Instagram Username फॉर्मेट: https://www.instagram.com/$rawClean/"
                                    } else {
                                        "सार्वजनिक नाम/कीवर्ड मोड: इसके लिए संभावित @handle वेरिएंट और Google Web Search तैयार होंगे।"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onRunSearch,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("manual_search_submit_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("प्रोफाइल लिंक खोजें")
                        }

                        FilledTonalButton(
                            onClick = {
                                if (searchState.queryInput.isNotBlank()) {
                                    MlAndInstagramApiClient.openPublicWebSearch(context, searchState.queryInput)
                                }
                            },
                            enabled = searchState.queryInput.isNotBlank(),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("manual_google_web_search_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Google Search")
                        }
                    }
                }
            }

            // Status / Feedback Banner
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_search_status_banner"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Text(
                    text = searchState.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }

            if (searchState.hasSearched) {
                if (searchState.candidateLinks.isNotEmpty()) {
                    CandidateLinksSection(
                        context = context,
                        title = "मिले हुए संभावित सार्वजनिक प्रोफाइल लिंक (${searchState.candidateLinks.size})",
                        candidates = searchState.candidateLinks,
                        onSaveCandidate = onSaveCandidateLink
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_no_profile_found_card"),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningAmber)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "कोई प्रोफाइल लिंक नहीं मिला (No Candidate Profile Found)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WarningAmber
                            )
                            Text(
                                text = "कृपया एक मान्य Instagram Username (जैसे @username) या व्यक्ति/पेज का सार्वजनिक नाम लिखकर दोबारा खोजें।",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}
