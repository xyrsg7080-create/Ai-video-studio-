package com.example.ui.autocreate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.Surface
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
import com.example.core.localization.LocalAppStrings
import com.example.core.security.ServiceAccessType
import com.example.data.local.SceneEntity
import com.example.data.local.VideoProjectEntity
import com.example.ui.AiPipelineState
import com.example.ui.components.CapabilityBadge
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldWarning
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun AutoVideoCreatorScreen(
    initialCreateBlankMode: Boolean,
    pipelineState: AiPipelineState,
    activeProject: VideoProjectEntity?,
    activeScenes: List<SceneEntity>,
    onGenerateAutoVideo: (
        topic: String,
        category: String,
        aspectRatio: String,
        durationSec: Int,
        languageCode: String,
        enableAiImages: Boolean,
        enableStockMedia: Boolean,
        enableVoiceover: Boolean,
        enableSubtitles: Boolean,
        enableMusicBeatSync: Boolean
    ) -> Unit,
    onCreateBlankProject: (
        title: String,
        topic: String,
        category: String,
        aspectRatio: String,
        durationSec: Int,
        customW: Int,
        customH: Int
    ) -> Unit,
    onFactCheck: (Boolean) -> Unit,
    onOpenManualEditor: () -> Unit,
    onResetState: () -> Unit
) {
    val strings = LocalAppStrings.current
    val appLang = LocalAppLanguage.current

    var isBlankMode by remember(initialCreateBlankMode) { mutableStateOf(initialCreateBlankMode) }
    var topicInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Facts") }
    var selectedAspect by remember { mutableStateOf("9:16") }
    var customWidthText by remember { mutableStateOf("1080") }
    var customHeightText by remember { mutableStateOf("1350") }
    var selectedDurationSec by remember { mutableIntStateOf(60) }
    var customDurationText by remember { mutableStateOf("60") }
    var targetLanguage by remember(appLang) { mutableStateOf(appLang.code) }

    var enableStockMedia by remember { mutableStateOf(true) }
    var enableAiImages by remember { mutableStateOf(false) }
    var enableVoiceover by remember { mutableStateOf(true) }
    var enableSubtitles by remember { mutableStateOf(true) }
    var enableMusicBeatSync by remember { mutableStateOf(true) }

    val categories = listOf("Facts", "Gaming", "Tutorial", "News", "Educational")
    val aspects = listOf("9:16" to "9:16 Shorts/Reels", "16:9" to "16:9 YouTube", "1:1" to "1:1 Square", "custom" to "Custom")
    val durations = listOf(30 to "30s", 60 to "60s", 90 to "90s", 180 to "3m", 300 to "5m", 600 to "10m", -1 to "Custom")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBlankMode) strings.moduleCreateNew else strings.moduleAiAutoMaker,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                CapabilityBadge(
                    if (isBlankMode) ServiceAccessType.OFFLINE_FREE else ServiceAccessType.ONLINE_FREE_NO_KEY
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !isBlankMode,
                    onClick = { isBlankMode = false },
                    label = { Text("AI Full Pipeline") },
                    modifier = Modifier.testTag("mode_ai_auto_chip")
                )
                FilterChip(
                    selected = isBlankMode,
                    onClick = { isBlankMode = true },
                    label = { Text("Blank Manual Setup") },
                    modifier = Modifier.testTag("mode_blank_setup_chip")
                )
            }

            OutlinedTextField(
                value = topicInput,
                onValueChange = { topicInput = it },
                label = {
                    Text(if (isBlankMode) "Project Title / Topic" else "Enter Title, Topic, or Video Prompt")
                },
                placeholder = {
                    Text("e.g., 5 Deep Ocean Mysteries Scientists Just Explained")
                },
                minLines = if (isBlankMode) 1 else 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("video_topic_input")
            )

            Text("Content Category", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }

            Text("Video Format & Aspect Ratio", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                aspects.forEach { (key, label) ->
                    FilterChip(
                        selected = selectedAspect == key,
                        onClick = { selectedAspect = key },
                        label = { Text(label) },
                        modifier = Modifier.testTag("aspect_chip_$key")
                    )
                }
            }

            if (selectedAspect == "custom") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customWidthText,
                        onValueChange = { customWidthText = it.filter(Char::isDigit) },
                        label = { Text("Width px") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customHeightText,
                        onValueChange = { customHeightText = it.filter(Char::isDigit) },
                        label = { Text("Height px") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Target Duration", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                durations.forEach { (sec, label) ->
                    val isCustom = sec == -1
                    val selected = if (isCustom) selectedDurationSec !in listOf(30, 60, 90, 180, 300, 600) else selectedDurationSec == sec
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedDurationSec = if (isCustom) {
                                customDurationText.toIntOrNull()?.coerceIn(10, 1800) ?: 45
                            } else sec
                        },
                        label = { Text(label) }
                    )
                }
            }

            if (selectedDurationSec !in listOf(30, 60, 90, 180, 300, 600)) {
                OutlinedTextField(
                    value = customDurationText,
                    onValueChange = { raw ->
                        customDurationText = raw.filter(Char::isDigit)
                        customDurationText.toIntOrNull()?.let { selectedDurationSec = it.coerceIn(5, 1800) }
                    },
                    label = { Text("Custom Duration (seconds)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Script & Voice Language:", style = MaterialTheme.typography.labelLarge)
                FilterChip(
                    selected = targetLanguage == "en",
                    onClick = { targetLanguage = "en" },
                    label = { Text("English") }
                )
                FilterChip(
                    selected = targetLanguage == "hi",
                    onClick = { targetLanguage = "hi" },
                    label = { Text("हिन्दी (Hindi)") }
                )
            }

            if (!isBlankMode) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Automation Pipeline Options", style = MaterialTheme.typography.titleSmall)
                        ToggleOptionRow("Licensed Stock Media (Wikimedia + Pexels/Pixabay)", enableStockMedia) {
                            enableStockMedia = it
                        }
                        ToggleOptionRow("AI Images (Gemini Nano Banana — Blaze quota; stock fallback)", enableAiImages) {
                            enableAiImages = it
                        }
                        ToggleOptionRow("AI Voiceover Narration (Offline Android TTS WAV)", enableVoiceover) {
                            enableVoiceover = it
                        }
                        ToggleOptionRow("Auto Subtitles & Word-Highlight Captions", enableSubtitles) {
                            enableSubtitles = it
                        }
                        ToggleOptionRow("Royalty-Free Background Music + Beat-Sync Cuts", enableMusicBeatSync) {
                            enableMusicBeatSync = it
                        }
                    }
                }
            }

            Surface(
                color = StudioSurface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.factCheckNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldWarning,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (isBlankMode) {
                Button(
                    onClick = {
                        onCreateBlankProject(
                            topicInput.ifBlank { "New Creator Video" },
                            topicInput,
                            selectedCategory,
                            selectedAspect,
                            selectedDurationSec,
                            customWidthText.toIntOrNull() ?: 1080,
                            customHeightText.toIntOrNull() ?: 1920
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("create_blank_project_submit")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = StudioObsidian)
                    Spacer(Modifier.size(8.dp))
                    Text("Create Blank Project & Open Editor", color = StudioObsidian, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = {
                        onGenerateAutoVideo(
                            topicInput,
                            selectedCategory,
                            selectedAspect,
                            selectedDurationSec,
                            targetLanguage,
                            enableAiImages,
                            enableStockMedia,
                            enableVoiceover,
                            enableSubtitles,
                            enableMusicBeatSync
                        )
                    },
                    enabled = pipelineState !is AiPipelineState.Running,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("generate_auto_video_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioObsidian)
                    Spacer(Modifier.size(8.dp))
                    Text(strings.generateVideo, color = StudioObsidian, fontWeight = FontWeight.Bold)
                }
            }

            when (pipelineState) {
                is AiPipelineState.Running -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(pipelineState.step, style = MaterialTheme.typography.titleSmall, color = ElectricCyan)
                            LinearProgressIndicator(
                                progress = { pipelineState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${pipelineState.progressPercent}% complete", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                is AiPipelineState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = pipelineState.message,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(onClick = onResetState) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                is AiPipelineState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = pipelineState.summary,
                                color = EmeraldSuccess,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (activeProject != null) {
                                Text("Title: ${activeProject.title}", style = MaterialTheme.typography.titleMedium)
                                if (activeProject.scriptHook.isNotBlank()) {
                                    Text("Hook: ${activeProject.scriptHook}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (activeProject.needsVerification || activeProject.factCheckNotes.isNotBlank()) {
                                    Surface(
                                        color = GoldWarning.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, tint = GoldWarning)
                                            Text(
                                                text = "Verification Notes: ${activeProject.factCheckNotes}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = GoldWarning
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "Generated ${activeScenes.size} editable scenes",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ElectricCyan
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = onOpenManualEditor,
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("open_generated_in_editor_button")
                                    ) {
                                        Icon(Icons.Filled.MovieCreation, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Edit & Export", color = StudioObsidian)
                                    }
                                    OutlinedButton(
                                        onClick = { onFactCheck(true) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.size(6.dp))
                                        Text("Deep Check")
                                    }
                                }
                            }
                        }
                    }
                }
                AiPipelineState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun ToggleOptionRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
