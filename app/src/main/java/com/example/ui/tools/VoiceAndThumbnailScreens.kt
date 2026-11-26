package com.example.ui.tools

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.core.localization.LocalAppLanguage
import com.example.core.security.ServiceAccessType
import com.example.data.local.BrandKitEntity
import com.example.data.local.MediaAssetEntity
import com.example.data.local.SceneEntity
import com.example.data.local.VideoProjectEntity
import com.example.data.remote.StockMediaItem
import com.example.media.ThumbnailConfig
import com.example.media.TtsVoiceOption
import com.example.ui.components.CapabilityBadge
import com.example.ui.components.formatBytes
import com.example.ui.components.shareFileViaProvider
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldWarning
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.io.File

@Composable
fun VoiceStudioScreen(
    voices: List<TtsVoiceOption>,
    activeProject: VideoProjectEntity?,
    activeScenes: List<SceneEntity>,
    voiceConsentGranted: Boolean,
    onSetVoiceConsent: (Boolean) -> Unit,
    onPreviewVoice: (text: String, langCode: String, voiceId: String?, speed: Float, pitch: Float) -> Unit,
    onSynthesizeToScene: (SceneEntity, langCode: String, voiceId: String?, speed: Float, pitch: Float) -> Unit,
    onImportAudioUri: (Uri, String) -> Unit
) {
    val appLang = LocalAppLanguage.current
    var selectedLang by remember(appLang) { mutableStateOf(appLang.code) }
    val filteredVoices = remember(voices, selectedLang) {
        voices.filter { it.languageCode == selectedLang }.ifEmpty { voices }
    }
    var selectedVoiceId by remember(filteredVoices) { mutableStateOf(filteredVoices.firstOrNull()?.id) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var pitch by remember { mutableFloatStateOf(1.0f) }
    var sampleText by remember(selectedLang) {
        mutableStateOf(
            if (selectedLang == "hi") "नमस्ते क्रिएटर्स! आज हम एक शानदार वीडियो बनाने जा रहे हैं।"
            else "Welcome back creators! Today we break down the top breakthrough in 60 seconds."
        )
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportAudioUri(uri, "Custom Voiceover Import")
    }

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
                Text("AI Voice Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CapabilityBadge(ServiceAccessType.OFFLINE_FREE)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedLang == "en", onClick = { selectedLang = "en" }, label = { Text("English Voices") })
                FilterChip(selected = selectedLang == "hi", onClick = { selectedLang = "hi" }, label = { Text("Hindi (हिन्दी)") })
            }

            Text("Available Device Voices (Male & Female)", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filteredVoices.forEach { v ->
                    FilterChip(
                        selected = selectedVoiceId == v.id,
                        onClick = { selectedVoiceId = v.id },
                        label = { Text(v.displayName) }
                    )
                }
            }

            OutlinedTextField(
                value = sampleText,
                onValueChange = { sampleText = it },
                label = { Text("Voiceover Test / Narration Text") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Speech Speed: ${"%.2f".format(speed)}x")
            Slider(value = speed, onValueChange = { speed = it }, valueRange = 0.6f..1.8f)

            Text("Voice Pitch: ${"%.2f".format(pitch)}x")
            Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.5f)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onPreviewVoice(sampleText, selectedLang, selectedVoiceId, speed, pitch) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.weight(1f).testTag("voice_preview_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Preview Voice", color = StudioObsidian)
                }
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Import Audio")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = voiceConsentGranted, onCheckedChange = onSetVoiceConsent)
                    Text(
                        text = "Voice Consent Policy: I confirm I own or hold explicit written permission for any custom voice profile or imported narration. Never clone a person's voice without consent.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (activeProject != null && activeScenes.isNotEmpty()) {
                Text("Synthesize & Sync Narration to '${activeProject.title}' Scenes", style = MaterialTheme.typography.titleSmall)
                activeScenes.sortedBy { it.orderIndex }.forEach { scene ->
                    Card(colors = CardDefaults.cardColors(containerColor = StudioSurface), shape = RoundedCornerShape(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Scene ${scene.orderIndex + 1}: ${scene.title}", style = MaterialTheme.typography.titleSmall)
                                Text(scene.narrationText.ifBlank { "(No narration text)" }, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (scene.voiceoverPath.isNotBlank()) {
                                    Text("Synced WAV ready", style = MaterialTheme.typography.labelSmall, color = EmeraldSuccess)
                                }
                            }
                            Button(
                                onClick = { onSynthesizeToScene(scene, selectedLang, selectedVoiceId, speed, pitch) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                            ) {
                                Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Sync", color = StudioObsidian)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThumbnailMakerScreen(
    activeProject: VideoProjectEntity?,
    activeScenes: List<SceneEntity>,
    brandKit: BrandKitEntity?,
    onRenderPreview: suspend (ThumbnailConfig) -> Bitmap,
    onSaveThumbnail: (VideoProjectEntity?, Bitmap) -> Unit
) {
    val context = LocalContext.current
    var headline by remember(activeProject?.id) {
        mutableStateOf(activeProject?.seoTitle?.ifBlank { activeProject.title } ?: "SECRET CREATOR WORKFLOW")
    }
    var badgeText by remember { mutableStateOf("MUST WATCH") }
    var sticker by remember { mutableStateOf("🔥") }
    var bgImagePath by remember(activeScenes) {
        mutableStateOf(
            activeScenes.firstOrNull { it.mediaPath.isNotBlank() && it.mediaType != "VIDEO" }?.mediaPath.orEmpty()
        )
    }
    var frameTimeMs by remember { mutableFloatStateOf(1500f) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) bgImagePath = uri.toString()
    }

    val sourceVideoPath = activeProject?.exportedVideoPath?.takeIf { File(it).exists() }
        ?: activeScenes.firstOrNull { it.mediaType == "VIDEO" && File(it.mediaPath).exists() }?.mediaPath
        ?: ""

    val config = ThumbnailConfig(
        headline = headline,
        subtitleBadge = badgeText,
        stickerText = sticker,
        primaryColorHex = brandKit?.primaryColorHex ?: "#22D3EE",
        accentColorHex = brandKit?.accentColorHex ?: "#FF6B4A",
        backgroundImagePath = bgImagePath,
        sourceVideoPath = sourceVideoPath,
        frameTimeMs = frameTimeMs.toLong()
    )

    LaunchedEffect(config) {
        previewBitmap = onRenderPreview(config)
    }

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
                Text("Thumbnail Maker (16:9)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CapabilityBadge(ServiceAccessType.OFFLINE_FREE)
            }

            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Generated thumbnail preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            OutlinedTextField(
                value = headline,
                onValueChange = { headline = it },
                label = { Text("Thumbnail Headline") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = badgeText,
                onValueChange = { badgeText = it },
                label = { Text("Corner Pill Badge") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Sticker Accent", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🔥", "⚡", "🎯", "🤯", "💡", "").forEach { s ->
                    FilterChip(
                        selected = sticker == s,
                        onClick = { sticker = s },
                        label = { Text(if (s.isEmpty()) "None" else s) }
                    )
                }
            }

            if (sourceVideoPath.isNotBlank()) {
                Text("Extract Frame Time: ${(frameTimeMs / 1000f).toInt()}s")
                Slider(value = frameTimeMs, onValueChange = { frameTimeMs = it }, valueRange = 0f..15000f)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Pick Background Photo")
                }
                Button(
                    onClick = { previewBitmap?.let { onSaveThumbnail(activeProject, it) } },
                    enabled = previewBitmap != null,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.weight(1f).testTag("save_thumbnail_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Save to Project", color = StudioObsidian)
                }
            }

            if (activeProject?.thumbnailPath?.isNotBlank() == true && File(activeProject.thumbnailPath).exists()) {
                OutlinedButton(
                    onClick = { shareFileViaProvider(context, File(activeProject.thumbnailPath), "image/jpeg", "Share Thumbnail") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Share Saved Thumbnail")
                }
            }
        }
    }
}

@Composable
fun MediaLibraryScreen(
    assets: List<MediaAssetEntity>,
    stockResults: List<StockMediaItem>,
    isSearchingStock: Boolean,
    stockSearchError: String?,
    videoAnalysisResult: String?,
    isAnalyzingVideo: Boolean,
    onImportLocalUri: (Uri, String, String) -> Unit,
    onSearchStock: (String) -> Unit,
    onRenameAsset: (String, String) -> Unit,
    onDeleteAsset: (MediaAssetEntity) -> Unit,
    onAnalyzeVideo: (Uri, String) -> Unit
) {
    var filterType by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }
    var stockQuery by remember { mutableStateOf("mountain timelapse") }
    var renameAsset by remember { mutableStateOf<MediaAssetEntity?>(null) }
    var renameTitle by remember { mutableStateOf("") }

    val visualPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onImportLocalUri(uri, "IMAGE", "Imported Visual")
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportLocalUri(uri, "AUDIO", "Imported Audio")
    }

    val analyzeVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) onAnalyzeVideo(uri, "Summarize scenes, suggest hooks, pacing improvements, and captions.")
    }

    val filteredAssets = remember(assets, filterType, searchQuery) {
        assets.filter { a ->
            val typeMatch = filterType == "ALL" || a.assetType.contains(filterType, true)
            val textMatch = searchQuery.isBlank() || a.title.contains(searchQuery, true) || a.sourceProvider.contains(searchQuery, true)
            typeMatch && textMatch
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()
    ) {
        item {
            Text("Media Library & Licensed Stock", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Zero broad storage permissions: uses Android Photo Picker & SAF. Tracks source URL & license metadata.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { visualPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.PermMedia, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Photo Picker", color = StudioObsidian)
                }
                OutlinedButton(
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import Audio")
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemini Multimodal Video Clip Analyzer", style = MaterialTheme.typography.titleSmall)
                    OutlinedButton(
                        onClick = { analyzeVideoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        enabled = !isAnalyzingVideo
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(if (isAnalyzingVideo) "Analyzing frames..." else "Pick Video & Analyze Highlights")
                    }
                    if (!videoAnalysisResult.isNullOrBlank()) {
                        Text(videoAnalysisResult, style = MaterialTheme.typography.bodySmall, color = ElectricCyan)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Online Stock Search (Wikimedia Free + Pexels/Pixabay)", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = stockQuery, onValueChange = { stockQuery = it }, label = { Text("Search Query") }, modifier = Modifier.weight(1f))
                        Button(onClick = { onSearchStock(stockQuery) }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search stock")
                        }
                    }
                    if (isSearchingStock) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    if (!stockSearchError.isNullOrBlank()) {
                        Text(stockSearchError, style = MaterialTheme.typography.bodySmall, color = GoldWarning)
                    }
                    stockResults.take(5).forEach { item ->
                        Surface(color = StudioSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.sourceProvider} • ${item.licenseName} • Commercial: ${if (item.commercialAllowed) "Yes" else "Check Attribution"}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                if (item.attributionText.isNotBlank()) {
                                    Text(item.attributionText, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, label = { Text("Filter local library") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL", "IMAGE", "VIDEO", "AUDIO", "AI_GENERATED").forEach { t ->
                    FilterChip(selected = filterType == t, onClick = { filterType = t }, label = { Text(t) })
                }
            }
        }

        items(filteredAssets, key = { it.id }) { asset ->
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurface), shape = RoundedCornerShape(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (asset.assetType != "AUDIO" && File(asset.filePath).exists()) {
                        AsyncImage(
                            model = File(asset.filePath),
                            contentDescription = asset.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(asset.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${asset.sourceProvider} • ${asset.licenseName} • ${formatBytes(asset.sizeBytes)}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                    IconButton(onClick = {
                        renameAsset = asset
                        renameTitle = asset.title
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename asset")
                    }
                    IconButton(onClick = { onDeleteAsset(asset) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete asset")
                    }
                }
            }
        }
    }

    if (renameAsset != null) {
        AlertDialog(
            onDismissRequest = { renameAsset = null },
            title = { Text("Rename Asset") },
            text = { OutlinedTextField(value = renameTitle, onValueChange = { renameTitle = it }) },
            confirmButton = {
                Button(onClick = {
                    renameAsset?.let { onRenameAsset(it.id, renameTitle) }
                    renameAsset = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameAsset = null }) { Text("Cancel") } }
        )
    }
}
