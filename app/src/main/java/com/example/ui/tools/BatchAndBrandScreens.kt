package com.example.ui.tools

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.localization.LocalAppLanguage
import com.example.core.security.ServiceAccessType
import com.example.data.local.BackgroundJobEntity
import com.example.data.local.BrandKitEntity
import com.example.data.local.VideoProjectEntity
import com.example.ui.components.CapabilityBadge
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun BatchCreatorScreen(
    projects: List<VideoProjectEntity>,
    backgroundJobs: List<BackgroundJobEntity>,
    onRunBatchTopics: (topics: List<String>, langCode: String, queueRender: Boolean) -> Unit,
    onConvertLongToShorts: (source: VideoProjectEntity, clipCount: Int) -> Unit
) {
    val appLang = LocalAppLanguage.current
    var topicsText by remember {
        mutableStateOf("3 Space Telescopes Facts\nWhy Cats Purr at 26Hz\nTop 3 Battery Myths")
    }
    var targetLang by remember(appLang) { mutableStateOf(appLang.code) }
    var queueRender by remember { mutableStateOf(false) }
    var selectedLongProject by remember(projects) { mutableStateOf(projects.firstOrNull()) }
    var splitCount by remember { mutableIntStateOf(3) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Batch Video Creator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CapabilityBadge(ServiceAccessType.ONLINE_FREE_NO_KEY)
            }
            Text("Generate multiple Shorts projects in one queue or split a long video into vertical 9:16 Shorts.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Multi-Topic Shorts Generator (One Topic Per Line)", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = topicsText,
                        onValueChange = { topicsText = it },
                        minLines = 4,
                        label = { Text("Shorts Topics List") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = targetLang == "en", onClick = { targetLang = "en" }, label = { Text("English") })
                        FilterChip(selected = targetLang == "hi", onClick = { targetLang = "hi" }, label = { Text("Hindi") })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Queue Background MP4 Render (WorkManager)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = queueRender, onCheckedChange = { queueRender = it })
                    }
                    Button(
                        onClick = {
                            onRunBatchTopics(topicsText.lines(), targetLang, queueRender)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.fillMaxWidth().testTag("start_batch_shorts_button")
                    ) {
                        Icon(Icons.Filled.BurstMode, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Generate Batch Shorts", color = StudioObsidian, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Long-to-Shorts Splitter (9:16)", style = MaterialTheme.typography.titleSmall)
                    if (projects.isEmpty()) {
                        Text("Create a project first to split its scenes into Shorts.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            projects.take(6).forEach { p ->
                                FilterChip(
                                    selected = selectedLongProject?.id == p.id,
                                    onClick = { selectedLongProject = p },
                                    label = { Text(p.title.take(20)) }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2, 3, 4).forEach { count ->
                                FilterChip(selected = splitCount == count, onClick = { splitCount = count }, label = { Text("$count Shorts") })
                            }
                        }
                        OutlinedButton(
                            onClick = { selectedLongProject?.let { onConvertLongToShorts(it, splitCount) } },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Split Into Vertical 9:16 Shorts")
                        }
                    }
                }
            }
        }

        if (backgroundJobs.isNotEmpty()) {
            item { Text("Render & Batch Queue", style = MaterialTheme.typography.titleMedium) }
            items(backgroundJobs, key = { it.id }) { job ->
                Card(colors = CardDefaults.cardColors(containerColor = StudioSurface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(job.title, style = MaterialTheme.typography.titleSmall)
                            Text(job.status, style = MaterialTheme.typography.labelSmall, color = if (job.status == "COMPLETED") EmeraldSuccess else ElectricCyan)
                        }
                        LinearProgressIndicator(progress = { job.progressPercent / 100f }, modifier = Modifier.fillMaxWidth())
                        if (job.detailMessage.isNotBlank()) {
                            Text(job.detailMessage, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrandKitScreen(
    brandKit: BrandKitEntity?,
    onSaveBrandKit: (BrandKitEntity) -> Unit,
    onSyncBrandKitCloud: ((BrandKitEntity) -> Unit)?
) {
    val initial = brandKit ?: BrandKitEntity(id = "default_brand_kit", channelName = "My Creator Channel")
    var channelName by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.channelName) }
    var primaryHex by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.primaryColorHex) }
    var accentHex by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.accentColorHex) }
    var introText by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.introText) }
    var outroText by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.outroText) }
    var watermarkText by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.watermarkText) }
    var watermarkEnabled by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.watermarkEnabled) }
    var defaultAspect by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.defaultAspectRatio) }
    var defaultCaptionStyle by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.defaultCaptionStyle) }
    var applyByDefault by remember(initial.id, initial.updatedAtMillis) { mutableStateOf(initial.applyByDefault) }

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
                Text("Channel Brand Kit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CapabilityBadge(ServiceAccessType.OFFLINE_FREE)
            }

            OutlinedTextField(value = channelName, onValueChange = { channelName = it }, label = { Text("Channel Name") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = primaryHex, onValueChange = { primaryHex = it }, label = { Text("Primary Color Hex") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = accentHex, onValueChange = { accentHex = it }, label = { Text("Accent Color Hex") }, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = introText, onValueChange = { introText = it }, label = { Text("Intro Tagline") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = outroText, onValueChange = { outroText = it }, label = { Text("Outro CTA Text") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = watermarkText, onValueChange = { watermarkText = it }, label = { Text("Watermark Handle (e.g. @Channel)") }, modifier = Modifier.fillMaxWidth())

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Video Watermark Overlay", style = MaterialTheme.typography.bodySmall)
                Switch(checked = watermarkEnabled, onCheckedChange = { watermarkEnabled = it })
            }

            Text("Default Aspect Ratio", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("9:16", "16:9", "1:1").forEach { ar ->
                    FilterChip(selected = defaultAspect == ar, onClick = { defaultAspect = ar }, label = { Text(ar) })
                }
            }

            Text("Default Caption Animation", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("WORD_HIGHLIGHT", "POP", "FADE").forEach { st ->
                    FilterChip(selected = defaultCaptionStyle == st, onClick = { defaultCaptionStyle = st }, label = { Text(st) })
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Apply Brand Kit to New Projects by Default", style = MaterialTheme.typography.bodySmall)
                Switch(checked = applyByDefault, onCheckedChange = { applyByDefault = it })
            }

            val updated = initial.copy(
                channelName = channelName,
                primaryColorHex = primaryHex,
                accentColorHex = accentHex,
                introText = introText,
                outroText = outroText,
                watermarkText = watermarkText,
                watermarkEnabled = watermarkEnabled,
                defaultAspectRatio = defaultAspect,
                defaultCaptionStyle = defaultCaptionStyle,
                applyByDefault = applyByDefault
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveBrandKit(updated) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.weight(1f).testTag("save_brand_kit_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Save Brand Kit", color = StudioObsidian)
                }
                if (onSyncBrandKitCloud != null) {
                    OutlinedButton(
                        onClick = { onSyncBrandKitCloud(updated) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Sync Cloud")
                    }
                }
            }
        }
    }
}
