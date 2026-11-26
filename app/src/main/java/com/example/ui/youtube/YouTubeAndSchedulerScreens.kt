package com.example.ui.youtube

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.localization.LocalAppStrings
import com.example.core.security.ServiceAccessType
import com.example.data.local.ScheduledUploadEntity
import com.example.data.local.StudioPreferences
import com.example.data.local.VideoProjectEntity
import com.example.ui.components.CapabilityBadge
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldWarning
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun YouTubeStudioScreen(
    preferences: StudioPreferences,
    hasOAuthClientId: Boolean,
    isConnected: Boolean,
    activeProject: VideoProjectEntity?,
    onConnectYouTube: () -> Unit,
    onDisconnectYouTube: () -> Unit,
    onOpenSettingsForKey: () -> Unit,
    onGenerateSeo: () -> Unit,
    onScheduleOrUpload: (
        project: VideoProjectEntity,
        videoTitle: String,
        description: String,
        tagsCsv: String,
        visibility: String,
        scheduledAtMillis: Long,
        userExplicitlyAuthorized: Boolean
    ) -> Unit
) {
    val strings = LocalAppStrings.current
    var seoTitle by remember(activeProject?.id, activeProject?.seoTitle) {
        mutableStateOf(activeProject?.seoTitle?.ifBlank { activeProject.title } ?: "")
    }
    var seoDescription by remember(activeProject?.id, activeProject?.seoDescription) {
        mutableStateOf(activeProject?.seoDescription.orEmpty())
    }
    var seoTags by remember(activeProject?.id, activeProject?.seoTagsCsv) {
        mutableStateOf(activeProject?.seoTagsCsv.orEmpty())
    }
    var visibility by remember { mutableStateOf("private") }
    var delayMillis by remember { mutableLongStateOf(0L) }
    var explicitConsent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YouTube Studio & SEO", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CapabilityBadge(ServiceAccessType.ONLINE_FREE_WITH_KEY)
            }

            // Channel Connection Card
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. YouTube Channel Connection (OAuth 2.0 PKCE)", style = MaterialTheme.typography.titleSmall)
                    if (isConnected && preferences.youtubeChannelTitle.isNotBlank()) {
                        Text(
                            text = "Connected: ${preferences.youtubeChannelTitle} (${preferences.youtubeChannelSubs} subscribers)",
                            color = EmeraldSuccess,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        OutlinedButton(onClick = onDisconnectYouTube) {
                            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Disconnect Channel")
                        }
                    } else {
                        Text(
                            text = if (hasOAuthClientId) {
                                "Client ID configured. Authorize access to upload videos & thumbnails."
                            } else {
                                "No YouTube OAuth Client ID set yet. Add your Google Cloud Android/Installed OAuth Client ID in Settings first."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onConnectYouTube,
                                enabled = hasOAuthClientId,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                modifier = Modifier.testTag("connect_youtube_button")
                            ) {
                                Icon(Icons.Filled.Link, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Connect Channel", color = StudioObsidian)
                            }
                            OutlinedButton(onClick = onOpenSettingsForKey) {
                                Text("Configure Client ID")
                            }
                        }
                    }
                }
            }

            // SEO & Direct Upload Card
            if (activeProject == null) {
                Card(colors = CardDefaults.cardColors(containerColor = StudioSurface)) {
                    Text("Open or create a project first to generate SEO and upload to YouTube.", modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("2. YouTube SEO & Metadata", style = MaterialTheme.typography.titleSmall)
                            OutlinedButton(onClick = onGenerateSeo, modifier = Modifier.testTag("generate_seo_button")) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("AI SEO")
                            }
                        }
                        OutlinedTextField(value = seoTitle, onValueChange = { seoTitle = it }, label = { Text("YouTube Title (max 100)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = seoDescription, onValueChange = { seoDescription = it }, label = { Text("Description & Chapters") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = seoTags, onValueChange = { seoTags = it }, label = { Text("Tags (comma-separated)") }, modifier = Modifier.fillMaxWidth())

                        Text("Visibility", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("private", "unlisted", "public").forEach { vis ->
                                FilterChip(selected = visibility == vis, onClick = { visibility = vis }, label = { Text(vis.replaceFirstChar { it.uppercase() }) })
                            }
                        }

                        Text("Schedule Timing", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0L to "Upload Now", 900_000L to "+15m", 3_600_000L to "+1h", 21_600_000L to "+6h").forEach { (d, lbl) ->
                                FilterChip(selected = delayMillis == d, onClick = { delayMillis = d }, label = { Text(lbl) })
                            }
                        }

                        val hasExportedFile = activeProject.exportedVideoPath.isNotBlank() && File(activeProject.exportedVideoPath).exists()
                        if (!hasExportedFile) {
                            Surface(color = GoldWarning.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    "Export this project to MP4 in the Manual Editor before uploading.",
                                    color = GoldWarning,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = explicitConsent, onCheckedChange = { explicitConsent = it })
                            Text(strings.uploadConsentNotice, style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick = {
                                onScheduleOrUpload(
                                    activeProject,
                                    seoTitle,
                                    seoDescription,
                                    seoTags,
                                    visibility,
                                    System.currentTimeMillis() + delayMillis,
                                    explicitConsent
                                )
                            },
                            enabled = explicitConsent && hasExportedFile && isConnected,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.fillMaxWidth().testTag("submit_youtube_upload_button")
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(if (delayMillis == 0L) "Upload to YouTube Now" else "Schedule Upload", color = StudioObsidian, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadSchedulerScreen(
    uploads: List<ScheduledUploadEntity>,
    onPause: (ScheduledUploadEntity) -> Unit,
    onResumeOrRetry: (ScheduledUploadEntity) -> Unit,
    onReschedulePlusOneHour: (ScheduledUploadEntity) -> Unit,
    onCancel: (ScheduledUploadEntity) -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()
    ) {
        item {
            Text("Upload Scheduler & History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Managed via Android WorkManager. Note: OEM battery optimization can delay background tasks; keep network connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (uploads.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)) {
                    Text(
                        "No scheduled or completed uploads yet. Export a video and configure an upload in YouTube Studio.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        items(uploads, key = { it.id }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.videoTitle, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        val statusColor = when (item.status) {
                            "SUCCEEDED" -> EmeraldSuccess
                            "FAILED", "CANCELLED" -> CrimsonError
                            "PAUSED" -> GoldWarning
                            else -> ElectricCyan
                        }
                        Text(item.status, color = statusColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text("Visibility: ${item.visibility} • Scheduled: ${formatter.format(Date(item.scheduledAtMillis))}", style = MaterialTheme.typography.bodySmall)
                    if (item.status == "UPLOADING") {
                        LinearProgressIndicator(progress = { item.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    if (!item.youtubeVideoId.isNullOrBlank()) {
                        Text("YouTube Video ID: ${item.youtubeVideoId}", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                    }
                    if (!item.errorMessage.isNullOrBlank()) {
                        Text(item.errorMessage, style = MaterialTheme.typography.bodySmall, color = CrimsonError)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.status == "QUEUED" || item.status == "UPLOADING") {
                            OutlinedButton(onClick = { onPause(item) }) {
                                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Pause")
                            }
                        }
                        if (item.status == "PAUSED" || item.status == "FAILED") {
                            OutlinedButton(onClick = { onResumeOrRetry(item) }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Resume/Retry")
                            }
                        }
                        if (item.status != "SUCCEEDED" && item.status != "CANCELLED") {
                            OutlinedButton(onClick = { onReschedulePlusOneHour(item) }) {
                                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("+1h")
                            }
                            OutlinedButton(onClick = { onCancel(item) }) {
                                Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}
