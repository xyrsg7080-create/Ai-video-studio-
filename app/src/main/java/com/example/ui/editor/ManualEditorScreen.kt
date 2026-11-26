package com.example.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.localization.LocalAppStrings
import com.example.data.local.BrandKitEntity
import com.example.data.local.SceneEntity
import com.example.data.local.SubtitleEntity
import com.example.data.local.TimelineClipEntity
import com.example.data.local.VideoProjectEntity
import com.example.data.remote.StockMediaItem
import com.example.media.AudioMixerAndBeatDetector
import com.example.media.ExportResolution
import com.example.media.ExportSettings
import com.example.ui.components.ScenePreviewCanvas
import com.example.ui.components.VideoPreviewDialog
import com.example.ui.components.formatDurationMs
import com.example.ui.components.shareFileViaProvider
import com.example.ui.components.trackColorFor
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import java.io.File

@Composable
fun ManualEditorScreen(
    project: VideoProjectEntity?,
    scenes: List<SceneEntity>,
    clips: List<TimelineClipEntity>,
    subtitles: List<SubtitleEntity>,
    brandKit: BrandKitEntity?,
    canUndo: Boolean,
    canRedo: Boolean,
    exportProgress: Int?,
    exportMessage: String,
    is4kSupported: Boolean,
    stockResults: List<StockMediaItem>,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUpdateProject: (VideoProjectEntity) -> Unit,
    onUpdateScene: (SceneEntity) -> Unit,
    onSplitScene: (SceneEntity) -> Unit,
    onMoveScene: (SceneEntity, Int) -> Unit,
    onDuplicateScene: (SceneEntity) -> Unit,
    onDeleteScene: (SceneEntity) -> Unit,
    onAddScene: (String) -> Unit,
    onAddOverlayClip: (String, String, String, String, String, Long, Long) -> Unit,
    onCopyClip: (TimelineClipEntity) -> Unit,
    onPasteClip: (String, Long) -> Unit,
    onDeleteClip: (TimelineClipEntity) -> Unit,
    onUpdateSubtitle: (SubtitleEntity) -> Unit,
    onAddSubtitle: (String, String, Long, Long) -> Unit,
    onDeleteSubtitle: (SubtitleEntity) -> Unit,
    onImportSrt: (String) -> Unit,
    onExportSrtText: () -> String,
    onTranslateProject: (String) -> Unit,
    onSearchStock: (String) -> Unit,
    onAttachStockToScene: (StockMediaItem, SceneEntity) -> Unit,
    onGenerateAiImage: (SceneEntity, String) -> Unit,
    onGenerateAiVideo: (SceneEntity, String) -> Unit,
    onImportLocalMediaToScene: (Uri, SceneEntity) -> Unit,
    onExportNow: (ExportSettings) -> Unit,
    onEnqueueBackgroundExport: (ExportSettings) -> Unit,
    onCancelExport: () -> Unit,
    onCreateBlankProject: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(Icons.Filled.MovieCreation, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(48.dp))
                Text("No Active Project Loaded", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Start a blank timeline or create one with AI Auto Video Maker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onCreateBlankProject,
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.testTag("editor_create_blank_btn")
                ) {
                    Text("New Timeline Project", color = StudioObsidian)
                }
            }
        }
        return
    }

    val orderedScenes = remember(scenes) { scenes.sortedBy { it.orderIndex } }
    var selectedSceneId by remember(project.id, orderedScenes) {
        mutableStateOf(orderedScenes.firstOrNull()?.id)
    }
    val selectedScene = orderedScenes.firstOrNull { it.id == selectedSceneId } ?: orderedScenes.firstOrNull()
    var selectedInspectorTab by remember { mutableIntStateOf(0) }
    var timelineZoom by remember { mutableFloatStateOf(1.0f) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showStockPickerDialog by remember { mutableStateOf(false) }
    var showPreviewExportedPlayer by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && selectedScene != null) {
            onImportLocalMediaToScene(uri, selectedScene)
        }
    }

    val srtImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let(onImportSrt)
        }
    }

    val srtExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri ->
        if (uri != null) {
            val content = onExportSrtText()
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) }
            }
        }
    }

    val activeSubForPreview = remember(subtitles, selectedScene, orderedScenes) {
        val sceneIndex = orderedScenes.indexOfFirst { it.id == selectedScene?.id }.coerceAtLeast(0)
        var startCursor = 0L
        orderedScenes.take(sceneIndex).forEach { startCursor += it.durationMs }
        subtitles.firstOrNull {
            it.startTimeMs <= startCursor + 500L && it.endTimeMs >= startCursor
        } ?: subtitles.getOrNull(sceneIndex)
    }

    val activeOverlayForPreview = remember(clips, selectedScene, orderedScenes) {
        val sceneIndex = orderedScenes.indexOfFirst { it.id == selectedScene?.id }.coerceAtLeast(0)
        var startCursor = 0L
        orderedScenes.take(sceneIndex).forEach { startCursor += it.durationMs }
        clips.firstOrNull {
            (it.trackType == "TEXT_OVERLAY" || it.trackType == "STICKER_MEME") &&
                it.startTimeMs <= startCursor + 1000L &&
                (it.startTimeMs + it.durationMs) >= startCursor
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Editor Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${project.aspectRatio} • ${orderedScenes.size} scenes • Autosaved",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier.testTag("editor_undo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier.testTag("editor_redo_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }
                Button(
                    onClick = { showExportDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.testTag("editor_export_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(strings.exportMp4, color = StudioObsidian, maxLines = 1)
                }
            }
        }

        if (exportProgress != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(exportMessage, style = MaterialTheme.typography.labelLarge, color = ElectricCyan)
                        TextButton(onClick = onCancelExport) { Text(strings.cancel) }
                    }
                    LinearProgressIndicator(progress = { exportProgress / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Video Preview Canvas
        ScenePreviewCanvas(
            aspectRatio = project.aspectRatio,
            scene = selectedScene,
            activeSubtitle = activeSubForPreview,
            overlayClip = activeOverlayForPreview,
            watermarkText = if (project.applyBrandKit && brandKit?.watermarkEnabled == true) brandKit.watermarkText else ""
        )

        if (project.exportedVideoPath.isNotBlank() && File(project.exportedVideoPath).exists()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPreviewExportedPlayer = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_exported_mp4_button")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Play Exported MP4")
                }
                OutlinedButton(
                    onClick = {
                        shareFileViaProvider(context, File(project.exportedVideoPath), "video/mp4", "Share Video")
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_exported_mp4_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Share MP4")
                }
            }
        }

        // Multi-Track Visual Timeline
        Card(
            colors = CardDefaults.cardColors(containerColor = StudioSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Multi-Track Timeline", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Zoom", style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = timelineZoom,
                            onValueChange = { timelineZoom = it },
                            valueRange = 0.6f..2.2f,
                            modifier = Modifier.width(100.dp)
                        )
                    }
                }

                // Track 1: Main Video / Scenes
                TimelineLane(
                    trackName = "V1 Main",
                    trackColor = trackColorFor("VIDEO_MAIN")
                ) {
                    orderedScenes.forEach { scene ->
                        val widthDp = ((scene.durationMs / 1000f) * 26f * timelineZoom).coerceIn(68f, 260f).dp
                        val isSelected = scene.id == selectedScene?.id
                        Box(
                            modifier = Modifier
                                .width(widthDp)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) ElectricCyan.copy(alpha = 0.34f) else StudioSurfaceElevated
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) ElectricCyan else ElectricCyan.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSceneId = scene.id }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "${scene.orderIndex + 1}. ${scene.title} (${scene.durationMs / 1000}s)",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { onAddScene(project.id) },
                        modifier = Modifier
                            .height(42.dp)
                            .testTag("timeline_add_scene_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add scene", modifier = Modifier.size(16.dp))
                    }
                }

                // Track 2: Overlays & Text/Stickers
                val overlayClips = clips.filter { it.trackType == "TEXT_OVERLAY" || it.trackType == "STICKER_MEME" }
                TimelineLane(trackName = "V2 Overlay", trackColor = CoralAmber) {
                    if (overlayClips.isEmpty()) {
                        Text(
                            "No overlays yet — add in Overlays tab",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        overlayClips.forEach { clip ->
                            ChipTrackBlock(
                                label = clip.overlayText.ifBlank { clip.label },
                                color = CoralAmber,
                                onCopy = { onCopyClip(clip) },
                                onDelete = { onDeleteClip(clip) }
                            )
                        }
                    }
                }

                // Track 3: Voiceover
                val voClips = clips.filter { it.trackType == "AUDIO_VOICEOVER" }
                TimelineLane(trackName = "A1 Voice", trackColor = EmeraldSuccess) {
                    if (voClips.isEmpty()) {
                        Text(
                            "Generate scene voiceovers in AI Voice Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        voClips.forEach { clip ->
                            ChipTrackBlock(
                                label = clip.label,
                                color = EmeraldSuccess,
                                onCopy = { onCopyClip(clip) },
                                onDelete = { onDeleteClip(clip) }
                            )
                        }
                    }
                }

                // Track 4: Music & SFX
                val audioClips = clips.filter { it.trackType == "AUDIO_MUSIC" || it.trackType == "AUDIO_SFX" }
                TimelineLane(trackName = "A2 Music/SFX", trackColor = trackColorFor("AUDIO_MUSIC")) {
                    if (audioClips.isEmpty()) {
                        Text(
                            "No music/SFX clips — configure in Audio tab",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        audioClips.forEach { clip ->
                            ChipTrackBlock(
                                label = clip.label,
                                color = trackColorFor(clip.trackType),
                                onCopy = { onCopyClip(clip) },
                                onDelete = { onDeleteClip(clip) }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onPasteClip(project.id, 0L) }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Paste Clip")
                    }
                }
            }
        }

        // Inspector Tabs
        val tabs = listOf("Scenes & FX", "Overlays", "Subtitles", "Audio")
        TabRow(selectedTabIndex = selectedInspectorTab) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedInspectorTab == idx,
                    onClick = { selectedInspectorTab = idx },
                    text = { Text(title, maxLines = 1) },
                    modifier = Modifier.testTag("editor_tab_$idx")
                )
            }
        }

        when (selectedInspectorTab) {
            0 -> {
                if (selectedScene != null) {
                    SceneInspectorPanel(
                        scene = selectedScene,
                        aspectRatio = project.aspectRatio,
                        onUpdateScene = onUpdateScene,
                        onSplitScene = { onSplitScene(selectedScene) },
                        onMoveLeft = { onMoveScene(selectedScene, -1) },
                        onMoveRight = { onMoveScene(selectedScene, 1) },
                        onDuplicate = { onDuplicateScene(selectedScene) },
                        onDelete = { onDeleteScene(selectedScene) },
                        onPickLocalMedia = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        onOpenStockSearch = {
                            onSearchStock(selectedScene.visualSearchQuery.ifBlank { selectedScene.title })
                            showStockPickerDialog = true
                        },
                        onGenerateAiImage = { onGenerateAiImage(selectedScene, project.aspectRatio) },
                        onGenerateAiVideo = { onGenerateAiVideo(selectedScene, project.aspectRatio) }
                    )
                }
            }
            1 -> {
                OverlaysInspectorPanel(
                    projectId = project.id,
                    overlayClips = clips.filter { it.trackType == "TEXT_OVERLAY" || it.trackType == "STICKER_MEME" },
                    onAddOverlay = { type, label, text ->
                        onAddOverlayClip(project.id, type, label, text, "", 0L, 4000L)
                    },
                    onDeleteClip = onDeleteClip
                )
            }
            2 -> {
                SubtitlesInspectorPanel(
                    project = project,
                    subtitles = subtitles,
                    onUpdateSubtitle = onUpdateSubtitle,
                    onAddSubtitle = { text, startMs, endMs -> onAddSubtitle(project.id, text, startMs, endMs) },
                    onDeleteSubtitle = onDeleteSubtitle,
                    onImportSrt = { srtImportLauncher.launch(arrayOf("*/*")) },
                    onExportSrt = { srtExportLauncher.launch("${project.title.take(20)}.srt") },
                    onTranslate = onTranslateProject
                )
            }
            3 -> {
                AudioMusicInspectorPanel(
                    project = project,
                    onUpdateProject = onUpdateProject,
                    onAddSfx = { sfxId ->
                        onAddOverlayClip(project.id, "AUDIO_SFX", "SFX: $sfxId", "", "", 500L, 1000L)
                    }
                )
            }
        }
    }

    if (showExportDialog) {
        ExportSettingsDialog(
            is4kSupported = is4kSupported,
            defaultBatterySaver = project.targetDurationSec >= 180,
            onDismiss = { showExportDialog = false },
            onExportNow = {
                showExportDialog = false
                onExportNow(it)
            },
            onEnqueueBackground = {
                showExportDialog = false
                onEnqueueBackgroundExport(it)
            }
        )
    }

    if (showStockPickerDialog && selectedScene != null) {
        StockPickerDialog(
            initialQuery = selectedScene.visualSearchQuery,
            results = stockResults,
            onSearch = onSearchStock,
            onSelect = { item ->
                onAttachStockToScene(item, selectedScene)
                showStockPickerDialog = false
            },
            onDismiss = { showStockPickerDialog = false }
        )
    }

    if (showPreviewExportedPlayer && project.exportedVideoPath.isNotBlank()) {
        VideoPreviewDialog(
            videoFile = File(project.exportedVideoPath),
            title = project.title,
            onDismiss = { showPreviewExportedPlayer = false }
        )
    }
}

@Composable
private fun TimelineLane(
    trackName: String,
    trackColor: Color,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(StudioSurfaceElevated.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = trackName,
            color = trackColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(74.dp)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

@Composable
private fun ChipTrackBlock(
    label: String,
    color: Color,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
        ) {
            Text(label.take(18), style = MaterialTheme.typography.labelSmall, color = color)
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy clip", tint = color, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete clip", tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun SceneInspectorPanel(
    scene: SceneEntity,
    aspectRatio: String,
    onUpdateScene: (SceneEntity) -> Unit,
    onSplitScene: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPickLocalMedia: () -> Unit,
    onOpenStockSearch: () -> Unit,
    onGenerateAiImage: () -> Unit,
    onGenerateAiVideo: () -> Unit
) {
    var title by remember(scene.id) { mutableStateOf(scene.title) }
    var narration by remember(scene.id) { mutableStateOf(scene.narrationText) }
    var durationSec by remember(scene.id) { mutableFloatStateOf((scene.durationMs / 1000f).coerceIn(2f, 60f)) }

    Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Scene ${scene.orderIndex + 1} Inspector", style = MaterialTheme.typography.titleSmall)
                Row {
                    IconButton(onClick = onMoveLeft) { Icon(Icons.Filled.ArrowBack, contentDescription = "Move earlier") }
                    IconButton(onClick = onMoveRight) { Icon(Icons.Filled.ArrowForward, contentDescription = "Move later") }
                    IconButton(onClick = onSplitScene) { Icon(Icons.Filled.ContentCut, contentDescription = "Split scene") }
                    IconButton(onClick = onDuplicate) { Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate scene") }
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete scene") }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Scene Title") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = narration,
                onValueChange = { narration = it },
                label = { Text("Narration / Script Line") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Duration: ${durationSec.toInt()}s | Trim Start: ${scene.trimStartMs / 1000}s")
            Slider(
                value = durationSec,
                onValueChange = { durationSec = it },
                valueRange = 2f..45f
            )
            Button(
                onClick = {
                    val ms = (durationSec.toLong() * 1000L).coerceAtLeast(2000L)
                    onUpdateScene(scene.copy(title = title, narrationText = narration, durationMs = ms, trimEndMs = scene.trimStartMs + ms))
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
            ) {
                Text("Apply Scene Changes", color = StudioObsidian)
            }

            Text("Visual Source (${scene.sourceName} • ${scene.licenseName})", style = MaterialTheme.typography.labelMedium, color = ElectricCyan)
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPickLocalMedia) {
                    Icon(Icons.Filled.PermMedia, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Photo Picker")
                }
                OutlinedButton(onClick = onOpenStockSearch) {
                    Text("Stock Search")
                }
                OutlinedButton(onClick = onGenerateAiImage) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("AI Image")
                }
                OutlinedButton(onClick = onGenerateAiVideo) {
                    Icon(Icons.Filled.VideoFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("AI Video ($aspectRatio)")
                }
            }

            Text("Motion & Keyframe Effect", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("KEN_BURNS_ZOOM_IN", "KEN_BURNS_ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT", "SHAKE", "NONE").forEach { fx ->
                    FilterChip(
                        selected = scene.motionEffect == fx,
                        onClick = { onUpdateScene(scene.copy(motionEffect = fx)) },
                        label = { Text(fx.replace("_", " ")) }
                    )
                }
            }

            Text("Color Filter & Transform", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("NONE", "CINEMATIC", "WARM", "VIVID", "NOIR").forEach { flt ->
                    FilterChip(
                        selected = scene.colorFilter == flt,
                        onClick = { onUpdateScene(scene.copy(colorFilter = flt)) },
                        label = { Text(flt) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onUpdateScene(scene.copy(rotationDegrees = (scene.rotationDegrees + 90f) % 360f)) }) {
                    Text("Rotate (${scene.rotationDegrees.toInt()}°)")
                }
                OutlinedButton(onClick = { onUpdateScene(scene.copy(flipHorizontal = !scene.flipHorizontal)) }) {
                    Text(if (scene.flipHorizontal) "Flipped H" else "Flip H")
                }
                OutlinedButton(onClick = {
                    val nextSpeed = if (scene.speed >= 1.5f) 0.75f else scene.speed + 0.25f
                    onUpdateScene(scene.copy(speed = nextSpeed))
                }) {
                    Text("Speed ${scene.speed}x")
                }
            }

            Text("Brightness (${"%.2f".format(scene.brightness)}) & Contrast (${"%.2f".format(scene.contrast)})")
            Slider(
                value = scene.brightness,
                onValueChange = { onUpdateScene(scene.copy(brightness = it)) },
                valueRange = -0.5f..0.5f
            )
            Slider(
                value = scene.contrast,
                onValueChange = { onUpdateScene(scene.copy(contrast = it)) },
                valueRange = 0.6f..1.6f
            )
        }
    }
}

@Composable
private fun OverlaysInspectorPanel(
    projectId: String,
    overlayClips: List<TimelineClipEntity>,
    onAddOverlay: (String, String, String) -> Unit,
    onDeleteClip: (TimelineClipEntity) -> Unit
) {
    var overlayText by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Text, Meme & Sticker Overlays", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = overlayText,
                onValueChange = { overlayText = it },
                label = { Text("Overlay Headline / Meme Caption") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (overlayText.isNotBlank()) {
                        onAddOverlay("TEXT_OVERLAY", "Text Overlay", overlayText.trim())
                        overlayText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
            ) {
                Text("Add Text Overlay", color = StudioObsidian)
            }

            Text("Quick Stickers & Badges", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("🔥 VIRAL", "⚡ KEY FACT", "🎯 PRO TIP", "🔔 SUBSCRIBE", "🤯 WAIT FOR IT").forEach { sticker ->
                    OutlinedButton(onClick = { onAddOverlay("STICKER_MEME", sticker, sticker) }) {
                        Text(sticker)
                    }
                }
            }

            overlayClips.forEach { clip ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${clip.overlayText} (${formatDurationMs(clip.startTimeMs)})", style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onDeleteClip(clip) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove overlay")
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitlesInspectorPanel(
    project: VideoProjectEntity,
    subtitles: List<SubtitleEntity>,
    onUpdateSubtitle: (SubtitleEntity) -> Unit,
    onAddSubtitle: (String, Long, Long) -> Unit,
    onDeleteSubtitle: (SubtitleEntity) -> Unit,
    onImportSrt: () -> Unit,
    onExportSrt: () -> Unit,
    onTranslate: (String) -> Unit
) {
    var newCapText by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onImportSrt, modifier = Modifier.weight(1f)) {
                    Text("Import SRT")
                }
                OutlinedButton(onClick = onExportSrt, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Export SRT")
                }
                OutlinedButton(
                    onClick = { onTranslate(if (project.languageCode == "hi") "en" else "hi") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(if (project.languageCode == "hi") "To EN" else "To HI")
                }
            }

            OutlinedTextField(
                value = newCapText,
                onValueChange = { newCapText = it },
                label = { Text("Add Caption Line") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    if (newCapText.isNotBlank()) {
                        val lastEnd = subtitles.maxOfOrNull { it.endTimeMs } ?: 0L
                        onAddSubtitle(newCapText.trim(), lastEnd, lastEnd + 3500L)
                        newCapText = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
            ) {
                Text("Add Subtitle Block", color = StudioObsidian)
            }

            subtitles.sortedBy { it.orderIndex }.forEach { sub ->
                Surface(color = StudioSurface, shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${formatDurationMs(sub.startTimeMs)} - ${formatDurationMs(sub.endTimeMs)}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            IconButton(onClick = { onDeleteSubtitle(sub) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete caption", modifier = Modifier.size(16.dp))
                            }
                        }
                        var edited by remember(sub.id, sub.text) { mutableStateOf(sub.text) }
                        OutlinedTextField(
                            value = edited,
                            onValueChange = { edited = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("BOTTOM", "CENTER", "TOP").forEach { pos ->
                                FilterChip(
                                    selected = sub.position == pos,
                                    onClick = { onUpdateSubtitle(sub.copy(text = edited, position = pos)) },
                                    label = { Text(pos) }
                                )
                            }
                            TextButton(onClick = { onUpdateSubtitle(sub.copy(text = edited)) }) {
                                Text("Save")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioMusicInspectorPanel(
    project: VideoProjectEntity,
    onUpdateProject: (VideoProjectEntity) -> Unit,
    onAddSfx: (String) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Royalty-Free Music Library (CC0 Offline)", style = MaterialTheme.typography.titleSmall)
            AudioMixerAndBeatDetector.builtInMusicTracks.forEach { track ->
                FilterChip(
                    selected = project.selectedMusicTrackId == track.id,
                    onClick = { onUpdateProject(project.copy(selectedMusicTrackId = track.id)) },
                    label = { Text("${track.title} • ${track.mood}") }
                )
            }
            Text("Music Volume: ${(project.musicVolume * 100).toInt()}%")
            Slider(
                value = project.musicVolume,
                onValueChange = { onUpdateProject(project.copy(musicVolume = it)) },
                valueRange = 0f..1f
            )
            Text("Voiceover Volume: ${(project.voiceVolume * 100).toInt()}%")
            Slider(
                value = project.voiceVolume,
                onValueChange = { onUpdateProject(project.copy(voiceVolume = it)) },
                valueRange = 0.2f..1.5f
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Auto Audio Ducking during Voiceover", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = project.audioDuckingEnabled,
                    onCheckedChange = { onUpdateProject(project.copy(audioDuckingEnabled = it)) }
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Beat-Synchronized Cuts", style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = project.beatSyncEnabled,
                    onCheckedChange = { onUpdateProject(project.copy(beatSyncEnabled = it)) }
                )
            }
            Text("Sound Effects (SFX)", style = MaterialTheme.typography.labelLarge)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioMixerAndBeatDetector.builtInSfx.forEach { sfx ->
                    OutlinedButton(onClick = { onAddSfx(sfx.id) }) {
                        Text("+ ${sfx.title}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportSettingsDialog(
    is4kSupported: Boolean,
    defaultBatterySaver: Boolean,
    onDismiss: () -> Unit,
    onExportNow: (ExportSettings) -> Unit,
    onEnqueueBackground: (ExportSettings) -> Unit
) {
    var resolution by remember { mutableStateOf(ExportResolution.HD_720) }
    var fps by remember { mutableIntStateOf(30) }
    var bitrateMbps by remember { mutableIntStateOf(6) }
    var batterySaver by remember { mutableStateOf(defaultBatterySaver) }
    var includeSubs by remember { mutableStateOf(true) }
    var includeWatermark by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Video (MP4 H.264 + AAC)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resolution", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportResolution.entries.forEach { res ->
                        val enabled = res != ExportResolution.UHD_4K || is4kSupported
                        FilterChip(
                            selected = resolution == res,
                            onClick = { if (enabled) resolution = res },
                            enabled = enabled,
                            label = { Text(if (res == ExportResolution.UHD_4K && !is4kSupported) "4K (Unsupported)" else res.label) }
                        )
                    }
                }
                Text("Frame Rate (FPS)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(24, 30, 60).forEach { f ->
                        FilterChip(selected = fps == f, onClick = { fps = f }, label = { Text("${f}fps") })
                    }
                }
                Text("Bitrate: $bitrateMbps Mbps")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 6, 12, 20).forEach { mbps ->
                        FilterChip(selected = bitrateMbps == mbps, onClick = { bitrateMbps = mbps }, label = { Text("${mbps}M") })
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Low-RAM / Battery Saver (720p24)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = batterySaver, onCheckedChange = { batterySaver = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Burn in Subtitles", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = includeSubs, onCheckedChange = { includeSubs = it })
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Include Brand Watermark", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = includeWatermark, onCheckedChange = { includeWatermark = it })
                }
            }
        },
        confirmButton = {
            val settings = ExportSettings(resolution, fps, bitrateMbps * 1_000_000, batterySaver, includeSubs, includeWatermark)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onEnqueueBackground(settings) }) {
                    Text("Background")
                }
                Button(
                    onClick = { onExportNow(settings) },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                ) {
                    Text("Export Now", color = StudioObsidian)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun StockPickerDialog(
    initialQuery: String,
    results: List<StockMediaItem>,
    onSearch: (String) -> Unit,
    onSelect: (StockMediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf(initialQuery) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Licensed Stock Media Search") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(340.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Query") }, modifier = Modifier.weight(1f))
                    Button(onClick = { onSearch(query) }) { Text("Go") }
                }
                if (results.isEmpty()) {
                    Text("No results loaded yet. Tap Go to search Wikimedia Commons (free keyless) & configured Pexels/Pixabay.", style = MaterialTheme.typography.bodySmall)
                } else {
                    results.forEach { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = StudioSurface),
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${item.sourceProvider} • ${item.mediaType} • ${item.licenseName}", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
