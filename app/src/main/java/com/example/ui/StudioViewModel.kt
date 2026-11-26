package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.example.core.localization.AppLanguage
import com.example.core.security.SecureKeyStore
import com.example.data.local.BackgroundJobEntity
import com.example.data.local.BrandKitEntity
import com.example.data.local.MediaAssetEntity
import com.example.data.local.ProjectBackupSerializer
import com.example.data.local.ProjectBundle
import com.example.data.local.SceneEntity
import com.example.data.local.ScheduledUploadEntity
import com.example.data.local.StudioPreferences
import com.example.data.local.SubtitleEntity
import com.example.data.local.TimelineClipEntity
import com.example.data.local.VideoProjectEntity
import com.example.data.remote.FactCheckResult
import com.example.data.remote.StockMediaItem
import com.example.media.AudioMixerAndBeatDetector
import com.example.media.ExportResolution
import com.example.media.ExportSettings
import com.example.media.SrtCodec
import com.example.media.ThumbnailConfig
import com.example.media.ThumbnailGenerator
import com.example.media.TtsVoiceOption
import com.example.workers.StudioWorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

sealed interface AiPipelineState {
    data object Idle : AiPipelineState
    data class Running(val step: String, val progressPercent: Int, val streamingPreview: String = "") : AiPipelineState
    data class Success(val projectId: String, val summary: String) : AiPipelineState
    data class Error(val message: String) : AiPipelineState
}

data class EditorHistorySnapshot(
    val scenes: List<SceneEntity>,
    val clips: List<TimelineClipEntity>,
    val subtitles: List<SubtitleEntity>
)

data class StorageStats(
    val freeDeviceBytes: Long = 0L,
    val totalDeviceBytes: Long = 0L,
    val projectsBytes: Long = 0L,
    val exportsBytes: Long = 0L,
    val tempCacheBytes: Long = 0L
)

data class VideoTemplate(
    val id: String,
    val titleEn: String,
    val titleHi: String,
    val category: String,
    val aspectRatio: String,
    val durationSec: Int,
    val sampleTopic: String,
    val descriptionEn: String
)

@OptIn(ExperimentalCoroutinesApi::class)
@UnstableApi
class StudioViewModel(
    private val appContext: Context,
    private val container: StudioContainer
) : ViewModel() {

    private val projectDao = container.database.projectDao()
    private val assetDao = container.database.mediaAssetDao()
    private val brandKitDao = container.database.brandKitDao()
    private val uploadDao = container.database.uploadScheduleDao()
    private val jobDao = container.database.backgroundJobDao()
    val prefsRepo = container.preferencesRepository
    val keyStore = container.keyStore

    val preferences: StateFlow<StudioPreferences> = prefsRepo.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudioPreferences())

    val projects: StateFlow<List<VideoProjectEntity>> = projectDao.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val mediaAssets: StateFlow<List<MediaAssetEntity>> = assetDao.observeAllAssets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val brandKits: StateFlow<List<BrandKitEntity>> = brandKitDao.observeAllBrandKits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val scheduledUploads: StateFlow<List<ScheduledUploadEntity>> = uploadDao.observeAllUploads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val backgroundJobs: StateFlow<List<BackgroundJobEntity>> = jobDao.observeAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val availableVoices: StateFlow<List<TtsVoiceOption>> = container.voiceStudioManager.voices

    private val _activeProjectId = MutableStateFlow<String?>(null)
    val activeProjectId: StateFlow<String?> = _activeProjectId.asStateFlow()

    val activeProject: StateFlow<VideoProjectEntity?> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(null) else projectDao.observeProject(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val activeScenes: StateFlow<List<SceneEntity>> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else projectDao.observeScenes(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeTimelineClips: StateFlow<List<TimelineClipEntity>> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else projectDao.observeTimelineClips(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val activeSubtitles: StateFlow<List<SubtitleEntity>> = _activeProjectId
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else projectDao.observeSubtitles(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _aiPipelineState = MutableStateFlow<AiPipelineState>(AiPipelineState.Idle)
    val aiPipelineState: StateFlow<AiPipelineState> = _aiPipelineState.asStateFlow()

    private val _stockResults = MutableStateFlow<List<StockMediaItem>>(emptyList())
    val stockResults: StateFlow<List<StockMediaItem>> = _stockResults.asStateFlow()

    private val _stockSearchError = MutableStateFlow<String?>(null)
    val stockSearchError: StateFlow<String?> = _stockSearchError.asStateFlow()

    private val _isSearchingStock = MutableStateFlow(false)
    val isSearchingStock: StateFlow<Boolean> = _isSearchingStock.asStateFlow()

    private val _exportProgress = MutableStateFlow<Int?>(null)
    val exportProgress: StateFlow<Int?> = _exportProgress.asStateFlow()

    private val _exportMessage = MutableStateFlow("")
    val exportMessage: StateFlow<String> = _exportMessage.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    private val _factCheckState = MutableStateFlow<FactCheckResult?>(null)
    val factCheckState: StateFlow<FactCheckResult?> = _factCheckState.asStateFlow()

    private val _videoAnalysisResult = MutableStateFlow<String?>(null)
    val videoAnalysisResult: StateFlow<String?> = _videoAnalysisResult.asStateFlow()

    private val _isAnalyzingVideo = MutableStateFlow(false)
    val isAnalyzingVideo: StateFlow<Boolean> = _isAnalyzingVideo.asStateFlow()

    private val undoStack = ArrayDeque<EditorHistorySnapshot>()
    private val redoStack = ArrayDeque<EditorHistorySnapshot>()
    private var clipboardClip: TimelineClipEntity? = null

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    val templates = listOf(
        VideoTemplate(
            id = "tpl_shorts_facts",
            titleEn = "Viral Facts Short (9:16)",
            titleHi = "वायरल फैक्ट्स शॉर्ट (9:16)",
            category = "Facts",
            aspectRatio = "9:16",
            durationSec = 45,
            sampleTopic = "3 Surprising Deep Ocean Discoveries",
            descriptionEn = "Fast hook, 4 punchy scenes, word-highlight captions & beat-synced cuts"
        ),
        VideoTemplate(
            id = "tpl_gaming_top5",
            titleEn = "Gaming Recommendations (9:16)",
            titleHi = "गेमिंग रिकमेंडेशंस (9:16)",
            category = "Gaming",
            aspectRatio = "9:16",
            durationSec = 60,
            sampleTopic = "Top 3 Offline Indie Games for Android",
            descriptionEn = "High-energy beat track, pop-up ratings & neon overlays"
        ),
        VideoTemplate(
            id = "tpl_youtube_explainer",
            titleEn = "YouTube Deep Explainer (16:9)",
            titleHi = "YouTube डिटेल एक्सप्लेनर (16:9)",
            category = "Educational",
            aspectRatio = "16:9",
            durationSec = 180,
            sampleTopic = "How Quantum Computers Actually Work",
            descriptionEn = "Structured chapters, verified references, ambient music & ducked voiceover"
        ),
        VideoTemplate(
            id = "tpl_tutorial_square",
            titleEn = "Quick Tutorial (1:1)",
            titleHi = "क्विक ट्यूटोरियल (1:1)",
            category = "Tutorial",
            aspectRatio = "1:1",
            durationSec = 90,
            sampleTopic = "5 Smartphone Photography Framing Rules",
            descriptionEn = "Step-by-step scenes, clean subtitles & call-to-action outro"
        ),
        VideoTemplate(
            id = "tpl_news_brief",
            titleEn = "News & Tech Brief (16:9)",
            titleHi = "न्यूज़ और टेक ब्रीफ (16:9)",
            category = "News",
            aspectRatio = "16:9",
            durationSec = 90,
            sampleTopic = "Latest Renewable Energy Storage Breakthroughs",
            descriptionEn = "Fact-checked outline with source citations & verification badges"
        )
    )

    init {
        viewModelScope.launch {
            if (brandKitDao.getDefaultBrandKit() == null) {
                brandKitDao.upsertBrandKit(
                    BrandKitEntity(
                        id = "default_brand_kit",
                        channelName = "My Creator Channel",
                        primaryColorHex = "#22D3EE",
                        accentColorHex = "#FF6B4A",
                        fontFamily = "Space Grotesk",
                        outroText = "Subscribe for more!",
                        watermarkText = "@MyChannel",
                        watermarkEnabled = false,
                        applyByDefault = true
                    )
                )
            }
            refreshStorageStats()
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun selectProject(projectId: String) {
        _activeProjectId.value = projectId
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        viewModelScope.launch {
            prefsRepo.setLastActiveProjectId(projectId)
        }
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefsRepo.setLanguage(lang) }
    }

    fun createBlankProject(
        title: String,
        topic: String = "",
        category: String = "Tutorial",
        aspectRatio: String = "9:16",
        durationSec: Int = 60,
        customW: Int = 1080,
        customH: Int = 1920,
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            val brandKit = brandKitDao.getDefaultBrandKit()
            val lang = preferences.value.language.code
            val projectId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val effectiveAspect = if (brandKit?.applyByDefault == true && aspectRatio == "9:16") {
                brandKit.defaultAspectRatio
            } else aspectRatio

            val project = VideoProjectEntity(
                id = projectId,
                title = title.trim().ifBlank { "New Studio Project" },
                topic = topic.ifBlank { title },
                contentCategory = category,
                aspectRatio = effectiveAspect,
                customWidth = customW,
                customHeight = customH,
                targetDurationSec = durationSec.coerceIn(5, 3600),
                languageCode = lang,
                brandKitId = brandKit?.id,
                applyBrandKit = brandKit?.applyByDefault ?: true,
                createdAtMillis = now,
                updatedAtMillis = now,
                lastAutosaveMillis = now
            )
            val initialScene = SceneEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                orderIndex = 0,
                title = "Intro Scene",
                narrationText = topic.ifBlank { "Welcome to ${project.title}" },
                visualSearchQuery = topic.ifBlank { "studio cinema" },
                durationMs = 5000L,
                trimEndMs = 5000L
            )
            val initialClip = TimelineClipEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                trackType = "VIDEO_MAIN",
                trackIndex = 0,
                label = initialScene.title,
                startTimeMs = 0L,
                durationMs = 5000L,
                trimEndMs = 5000L
            )
            projectDao.upsertProject(project)
            projectDao.upsertScenes(listOf(initialScene))
            projectDao.upsertTimelineClips(listOf(initialClip))
            selectProject(projectId)
            onCreated(projectId)
        }
    }

    fun createFromTemplate(template: VideoTemplate, onCreated: (String) -> Unit) {
        createBlankProject(
            title = template.titleEn,
            topic = template.sampleTopic,
            category = template.category,
            aspectRatio = template.aspectRatio,
            durationSec = template.durationSec,
            onCreated = onCreated
        )
    }

    fun generateFullAutoVideo(
        topic: String,
        category: String,
        aspectRatio: String,
        durationSec: Int,
        languageCode: String,
        enableAiImages: Boolean,
        enableStockMedia: Boolean,
        enableVoiceover: Boolean,
        enableSubtitles: Boolean,
        enableMusicBeatSync: Boolean,
        onReady: (String) -> Unit
    ) {
        if (topic.isBlank()) {
            _aiPipelineState.value = AiPipelineState.Error("Please enter a title, topic, or prompt.")
            return
        }
        viewModelScope.launch {
            val warnings = mutableListOf<String>()
            try {
                _aiPipelineState.value = AiPipelineState.Running("Drafting script & storyboard via Gemini...", 15)
                val plan = container.aiRepository.generateFullVideoPlan(
                    topic = topic,
                    category = category,
                    aspectRatio = aspectRatio,
                    targetDurationSec = durationSec,
                    languageCode = languageCode
                ).getOrElse { err ->
                    _aiPipelineState.value = AiPipelineState.Error(
                        err.localizedMessage ?: "AI script generation failed."
                    )
                    return@launch
                }
                prefsRepo.incrementAiRequestCount()

                val projectId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val brandKit = brandKitDao.getDefaultBrandKit()

                val project = VideoProjectEntity(
                    id = projectId,
                    title = plan.title.ifBlank { topic.take(60) },
                    topic = topic,
                    contentCategory = category,
                    aspectRatio = aspectRatio,
                    targetDurationSec = durationSec,
                    languageCode = languageCode,
                    scriptHook = plan.hook,
                    scriptBody = plan.body,
                    scriptCta = plan.cta,
                    factCheckNotes = plan.factCheckNotes,
                    needsVerification = plan.needsVerification,
                    researchSourcesJson = plan.researchSources.joinToString("\n"),
                    seoTitle = plan.seoTitle,
                    seoDescription = plan.seoDescription,
                    seoTagsCsv = plan.seoTags.joinToString(", "),
                    seoHashtagsCsv = plan.seoHashtags.joinToString(" "),
                    seoChaptersJson = plan.chapters.joinToString("\n"),
                    selectedMusicTrackId = if (aspectRatio == "9:16") "shorts_energy" else "ambient_pulse",
                    beatSyncEnabled = enableMusicBeatSync,
                    audioDuckingEnabled = true,
                    status = "READY",
                    brandKitId = brandKit?.id,
                    applyBrandKit = brandKit?.applyByDefault ?: true,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                    lastAutosaveMillis = now
                )

                val sceneEntities = mutableListOf<SceneEntity>()
                val timelineClips = mutableListOf<TimelineClipEntity>()
                val subtitleEntities = mutableListOf<SubtitleEntity>()
                var cursorMs = 0L

                // Optional beat alignment
                val beatOffsets = if (enableMusicBeatSync) {
                    val musicFile = AudioMixerAndBeatDetector.ensureMusicTrackWav(
                        appContext,
                        project.selectedMusicTrackId,
                        16_000L
                    )
                    AudioMixerAndBeatDetector.detectBeatTimestampsMs(musicFile)
                } else emptyList()

                val totalScenes = plan.scenes.size.coerceAtLeast(1)
                plan.scenes.forEachIndexed { idx, scenePlan ->
                    val basePct = 25 + ((idx * 65) / totalScenes)
                    _aiPipelineState.value = AiPipelineState.Running(
                        "Building Scene ${idx + 1}/$totalScenes: ${scenePlan.title}",
                        basePct
                    )

                    var durationMs = (scenePlan.durationSec.coerceIn(3, 60) * 1000L)
                    if (beatOffsets.isNotEmpty() && idx > 0) {
                        val targetEnd = cursorMs + durationMs
                        val nearestBeat = beatOffsets.minByOrNull { kotlin.math.abs((it % 16000L) - (targetEnd % 16000L)) }
                        if (nearestBeat != null) {
                            val delta = (nearestBeat % 16000L) - (targetEnd % 16000L)
                            if (kotlin.math.abs(delta) <= 450L) {
                                durationMs = (durationMs + delta).coerceAtLeast(2500L)
                            }
                        }
                    }

                    var mediaPath = ""
                    var mediaType = "NONE"
                    var sourceName = "Generated Card"
                    var sourceUrl = ""
                    var licenseName = "Project Owned"
                    var attribution = ""
                    var commercialOk = true

                    if (enableAiImages && idx < 2) {
                        val imgRes = container.aiRepository.generateAiImage(
                            prompt = "${scenePlan.visualQuery}, cinematic high contrast photography, no text",
                            aspectRatio = aspectRatio
                        )
                        imgRes.onSuccess { bmp ->
                            val file = saveBitmapToAppStorage(bmp, "ai_scene_${projectId}_$idx.jpg")
                            mediaPath = file.absolutePath
                            mediaType = "AI_IMAGE"
                            sourceName = "Gemini Nano Banana 2.1"
                            licenseName = "AI Generated Asset"
                            assetDao.upsertAsset(
                                MediaAssetEntity(
                                    id = UUID.randomUUID().toString(),
                                    title = scenePlan.title,
                                    filePath = file.absolutePath,
                                    assetType = "AI_GENERATED",
                                    mimeType = "image/jpeg",
                                    sizeBytes = file.length(),
                                    sourceProvider = sourceName,
                                    licenseName = licenseName
                                )
                            )
                        }.onFailure {
                            if (idx == 0) warnings.add("AI image gen unavailable (${it.localizedMessage}); used stock/card fallback.")
                        }
                    }

                    if (mediaPath.isBlank() && enableStockMedia) {
                        val stockRes = container.stockMediaRepository.searchAllAvailable(scenePlan.visualQuery)
                        val match = stockRes.getOrNull()?.firstOrNull { it.commercialAllowed }
                            ?: stockRes.getOrNull()?.firstOrNull()
                        if (match != null) {
                            container.stockMediaRepository.downloadStockMedia(match).onSuccess { dlFile ->
                                mediaPath = dlFile.absolutePath
                                mediaType = match.mediaType
                                sourceName = match.sourceProvider
                                sourceUrl = match.sourceUrl
                                licenseName = match.licenseName
                                attribution = match.attributionText
                                commercialOk = match.commercialAllowed
                                assetDao.upsertAsset(
                                    MediaAssetEntity(
                                        id = match.id,
                                        title = match.title,
                                        filePath = dlFile.absolutePath,
                                        assetType = match.mediaType,
                                        mimeType = if (match.mediaType == "VIDEO") "video/mp4" else "image/jpeg",
                                        sizeBytes = dlFile.length(),
                                        sourceProvider = match.sourceProvider,
                                        sourceUrl = match.sourceUrl,
                                        licenseName = match.licenseName,
                                        attributionText = match.attributionText,
                                        commercialAllowed = match.commercialAllowed
                                    )
                                )
                            }
                        }
                    }

                    var voPath = ""
                    if (enableVoiceover && scenePlan.narration.isNotBlank()) {
                        container.voiceStudioManager.synthesizeToWav(
                            text = scenePlan.narration,
                            languageCode = languageCode,
                            voiceId = null,
                            speed = 1.0f,
                            pitch = 1.0f,
                            outputFileName = "vo_${projectId}_$idx.wav"
                        ).onSuccess { wavFile ->
                            voPath = wavFile.absolutePath
                        }
                    }

                    val sceneId = UUID.randomUUID().toString()
                    sceneEntities.add(
                        SceneEntity(
                            id = sceneId,
                            projectId = projectId,
                            orderIndex = idx,
                            title = scenePlan.title,
                            narrationText = scenePlan.narration,
                            visualSearchQuery = scenePlan.visualQuery,
                            durationMs = durationMs,
                            mediaPath = mediaPath,
                            mediaType = mediaType,
                            sourceName = sourceName,
                            sourceUrl = sourceUrl,
                            licenseName = licenseName,
                            attributionText = attribution,
                            commercialAllowed = commercialOk,
                            transitionType = scenePlan.transition,
                            motionEffect = scenePlan.motionEffect,
                            trimEndMs = durationMs,
                            voiceoverPath = voPath,
                            sfxType = scenePlan.suggestedSfx
                        )
                    )

                    timelineClips.add(
                        TimelineClipEntity(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            trackType = "VIDEO_MAIN",
                            trackIndex = 0,
                            label = scenePlan.title,
                            mediaPath = mediaPath,
                            startTimeMs = cursorMs,
                            durationMs = durationMs,
                            trimEndMs = durationMs,
                            transitionIn = scenePlan.transition
                        )
                    )

                    if (voPath.isNotBlank()) {
                        timelineClips.add(
                            TimelineClipEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                trackType = "AUDIO_VOICEOVER",
                                trackIndex = 2,
                                label = "VO ${idx + 1}",
                                mediaPath = voPath,
                                startTimeMs = cursorMs,
                                durationMs = durationMs,
                                trimEndMs = durationMs
                            )
                        )
                    }

                    if (enableSubtitles && scenePlan.narration.isNotBlank()) {
                        subtitleEntities.add(
                            SubtitleEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = projectId,
                                orderIndex = idx,
                                startTimeMs = cursorMs,
                                endTimeMs = cursorMs + durationMs,
                                text = scenePlan.narration,
                                animationStyle = brandKit?.defaultCaptionStyle ?: "WORD_HIGHLIGHT"
                            )
                        )
                    }

                    cursorMs += durationMs
                }

                // Add Background Music track clip
                if (enableMusicBeatSync) {
                    val musicWav = AudioMixerAndBeatDetector.ensureMusicTrackWav(
                        appContext,
                        project.selectedMusicTrackId,
                        16_000L
                    )
                    timelineClips.add(
                        TimelineClipEntity(
                            id = UUID.randomUUID().toString(),
                            projectId = projectId,
                            trackType = "AUDIO_MUSIC",
                            trackIndex = 3,
                            label = "Music: ${project.selectedMusicTrackId}",
                            mediaPath = musicWav.absolutePath,
                            startTimeMs = 0L,
                            durationMs = cursorMs.coerceAtLeast(3000L),
                            trimEndMs = cursorMs.coerceAtLeast(3000L),
                            volume = project.musicVolume
                        )
                    )
                }

                projectDao.upsertProject(project)
                projectDao.upsertScenes(sceneEntities)
                projectDao.upsertTimelineClips(timelineClips)
                projectDao.upsertSubtitles(subtitleEntities)

                selectProject(projectId)
                refreshStorageStats()
                val note = if (warnings.isEmpty()) {
                    "Built ${sceneEntities.size} scenes & timeline ready for preview/export."
                } else {
                    "Project ready. Note: ${warnings.joinToString("; ")}"
                }
                _aiPipelineState.value = AiPipelineState.Success(projectId, note)
                onReady(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiPipelineState.value = AiPipelineState.Error(e.localizedMessage ?: "Pipeline failed.")
            }
        }
    }

    fun resetAiPipelineState() {
        _aiPipelineState.value = AiPipelineState.Idle
    }

    // --- Editor Undo/Redo & Mutations ---
    private fun pushUndoSnapshot() {
        undoStack.addLast(
            EditorHistorySnapshot(
                scenes = activeScenes.value,
                clips = activeTimelineClips.value,
                subtitles = activeSubtitles.value
            )
        )
        if (undoStack.size > 25) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = false
    }

    fun undo() {
        val projectId = _activeProjectId.value ?: return
        val snap = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(
            EditorHistorySnapshot(activeScenes.value, activeTimelineClips.value, activeSubtitles.value)
        )
        viewModelScope.launch {
            projectDao.deleteScenesForProject(projectId)
            projectDao.deleteTimelineClipsForProject(projectId)
            projectDao.deleteSubtitlesForProject(projectId)
            projectDao.upsertScenes(snap.scenes)
            projectDao.upsertTimelineClips(snap.clips)
            projectDao.upsertSubtitles(snap.subtitles)
            touchAutosave(projectId)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    fun redo() {
        val projectId = _activeProjectId.value ?: return
        val snap = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(
            EditorHistorySnapshot(activeScenes.value, activeTimelineClips.value, activeSubtitles.value)
        )
        viewModelScope.launch {
            projectDao.deleteScenesForProject(projectId)
            projectDao.deleteTimelineClipsForProject(projectId)
            projectDao.deleteSubtitlesForProject(projectId)
            projectDao.upsertScenes(snap.scenes)
            projectDao.upsertTimelineClips(snap.clips)
            projectDao.upsertSubtitles(snap.subtitles)
            touchAutosave(projectId)
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = redoStack.isNotEmpty()
        }
    }

    fun updateScene(updated: SceneEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            projectDao.upsertScenes(listOf(updated))
            syncMainTrackFromScenes(updated.projectId)
            touchAutosave(updated.projectId)
        }
    }

    fun splitSceneAtMidpoint(scene: SceneEntity) {
        if (scene.durationMs < 3000L) {
            showBanner("Scene must be at least 3 seconds to split.")
            return
        }
        pushUndoSnapshot()
        viewModelScope.launch {
            val half = scene.durationMs / 2L
            val all = activeScenes.value.sortedBy { it.orderIndex }.toMutableList()
            val idx = all.indexOfFirst { it.id == scene.id }
            if (idx == -1) return@launch

            val first = scene.copy(durationMs = half, trimEndMs = scene.trimStartMs + half)
            val second = scene.copy(
                id = UUID.randomUUID().toString(),
                title = "${scene.title} (Part 2)",
                durationMs = scene.durationMs - half,
                trimStartMs = scene.trimStartMs + half,
                trimEndMs = scene.trimEndMs
            )
            all[idx] = first
            all.add(idx + 1, second)
            val reindexed = all.mapIndexed { i, item -> item.copy(orderIndex = i) }
            projectDao.deleteScenesForProject(scene.projectId)
            projectDao.upsertScenes(reindexed)
            syncMainTrackFromScenes(scene.projectId)
            touchAutosave(scene.projectId)
        }
    }

    fun moveScene(scene: SceneEntity, direction: Int) {
        val all = activeScenes.value.sortedBy { it.orderIndex }.toMutableList()
        val idx = all.indexOfFirst { it.id == scene.id }
        val target = idx + direction
        if (idx == -1 || target !in all.indices) return
        pushUndoSnapshot()
        viewModelScope.launch {
            val swapped = all.removeAt(idx)
            all.add(target, swapped)
            val reindexed = all.mapIndexed { i, item -> item.copy(orderIndex = i) }
            projectDao.deleteScenesForProject(scene.projectId)
            projectDao.upsertScenes(reindexed)
            syncMainTrackFromScenes(scene.projectId)
            touchAutosave(scene.projectId)
        }
    }

    fun duplicateScene(scene: SceneEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            val all = activeScenes.value.sortedBy { it.orderIndex }.toMutableList()
            val idx = all.indexOfFirst { it.id == scene.id }
            val copy = scene.copy(id = UUID.randomUUID().toString(), title = "${scene.title} Copy")
            if (idx >= 0) all.add(idx + 1, copy) else all.add(copy)
            val reindexed = all.mapIndexed { i, item -> item.copy(orderIndex = i) }
            projectDao.deleteScenesForProject(scene.projectId)
            projectDao.upsertScenes(reindexed)
            syncMainTrackFromScenes(scene.projectId)
            touchAutosave(scene.projectId)
        }
    }

    fun deleteScene(scene: SceneEntity) {
        val all = activeScenes.value.sortedBy { it.orderIndex }.toMutableList()
        if (all.size <= 1) {
            showBanner("A project requires at least 1 scene.")
            return
        }
        pushUndoSnapshot()
        viewModelScope.launch {
            all.removeAll { it.id == scene.id }
            val reindexed = all.mapIndexed { i, item -> item.copy(orderIndex = i) }
            projectDao.deleteScenesForProject(scene.projectId)
            projectDao.upsertScenes(reindexed)
            syncMainTrackFromScenes(scene.projectId)
            touchAutosave(scene.projectId)
        }
    }

    fun addNewScene(projectId: String, title: String = "New Scene") {
        pushUndoSnapshot()
        viewModelScope.launch {
            val nextIdx = activeScenes.value.size
            val scene = SceneEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                orderIndex = nextIdx,
                title = title,
                narrationText = "",
                visualSearchQuery = "abstract background",
                durationMs = 4000L,
                trimEndMs = 4000L
            )
            projectDao.upsertScenes(listOf(scene))
            syncMainTrackFromScenes(projectId)
            touchAutosave(projectId)
        }
    }

    fun addOverlayClip(
        projectId: String,
        trackType: String,
        label: String,
        overlayText: String = "",
        mediaPath: String = "",
        startTimeMs: Long = 0L,
        durationMs: Long = 3500L
    ) {
        pushUndoSnapshot()
        viewModelScope.launch {
            val trackIdx = when (trackType) {
                "TEXT_OVERLAY", "STICKER_MEME", "VIDEO_OVERLAY" -> 1
                "AUDIO_VOICEOVER" -> 2
                "AUDIO_MUSIC" -> 3
                else -> 4
            }
            val clip = TimelineClipEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                trackType = trackType,
                trackIndex = trackIdx,
                label = label,
                mediaPath = mediaPath,
                overlayText = overlayText,
                startTimeMs = startTimeMs,
                durationMs = durationMs,
                trimEndMs = durationMs
            )
            projectDao.upsertTimelineClips(listOf(clip))
            touchAutosave(projectId)
        }
    }

    fun copyClip(clip: TimelineClipEntity) {
        clipboardClip = clip
        showBanner("Copied '${clip.label}' to clipboard.")
    }

    fun pasteClip(projectId: String, targetStartMs: Long) {
        val src = clipboardClip ?: return
        pushUndoSnapshot()
        viewModelScope.launch {
            val pasted = src.copy(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                label = "${src.label} (Paste)",
                startTimeMs = targetStartMs.coerceAtLeast(0L)
            )
            projectDao.upsertTimelineClips(listOf(pasted))
            touchAutosave(projectId)
        }
    }

    fun updateClip(clip: TimelineClipEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            projectDao.upsertTimelineClips(listOf(clip))
            touchAutosave(clip.projectId)
        }
    }

    fun deleteClip(clip: TimelineClipEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            val remaining = activeTimelineClips.value.filterNot { it.id == clip.id }
            projectDao.deleteTimelineClipsForProject(clip.projectId)
            projectDao.upsertTimelineClips(remaining)
            touchAutosave(clip.projectId)
        }
    }

    fun updateSubtitle(sub: SubtitleEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            projectDao.upsertSubtitles(listOf(sub))
            touchAutosave(sub.projectId)
        }
    }

    fun addSubtitle(projectId: String, text: String, startMs: Long, endMs: Long) {
        pushUndoSnapshot()
        viewModelScope.launch {
            val sub = SubtitleEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                orderIndex = activeSubtitles.value.size,
                startTimeMs = startMs,
                endTimeMs = endMs.coerceAtLeast(startMs + 800L),
                text = text
            )
            projectDao.upsertSubtitles(listOf(sub))
            touchAutosave(projectId)
        }
    }

    fun deleteSubtitle(sub: SubtitleEntity) {
        pushUndoSnapshot()
        viewModelScope.launch {
            val remaining = activeSubtitles.value.filterNot { it.id == sub.id }
            projectDao.deleteSubtitlesForProject(sub.projectId)
            projectDao.upsertSubtitles(remaining)
            touchAutosave(sub.projectId)
        }
    }

    fun importSrtIntoActiveProject(srtText: String) {
        val projectId = _activeProjectId.value ?: return
        val parsed = SrtCodec.parseSrt(projectId, srtText)
        if (parsed.isEmpty()) {
            showBanner("No valid SRT subtitle blocks found.")
            return
        }
        pushUndoSnapshot()
        viewModelScope.launch {
            projectDao.deleteSubtitlesForProject(projectId)
            projectDao.upsertSubtitles(parsed)
            touchAutosave(projectId)
            showBanner("Imported ${parsed.size} SRT captions.")
        }
    }

    fun exportActiveSubtitlesToSrt(): String = SrtCodec.exportToSrt(activeSubtitles.value)

    fun translateProjectToLanguage(targetLangCode: String) {
        val project = activeProject.value ?: return
        val subs = activeSubtitles.value
        val scenes = activeScenes.value.sortedBy { it.orderIndex }
        viewModelScope.launch {
            val lines = if (subs.isNotEmpty()) subs.map { it.text } else scenes.map { it.narrationText }
            container.aiRepository.translateLines(lines, targetLangCode).fold(
                onSuccess = { translated ->
                    pushUndoSnapshot()
                    if (subs.isNotEmpty()) {
                        val updatedSubs = subs.mapIndexed { i, sub ->
                            val t = translated.getOrNull(i) ?: sub.text
                            sub.copy(text = t, translatedText = sub.text)
                        }
                        projectDao.upsertSubtitles(updatedSubs)
                    }
                    val updatedScenes = scenes.mapIndexed { i, sc ->
                        val t = translated.getOrNull(i) ?: sc.narrationText
                        sc.copy(narrationText = t)
                    }
                    projectDao.upsertScenes(updatedScenes)
                    projectDao.upsertProject(project.copy(languageCode = targetLangCode, updatedAtMillis = System.currentTimeMillis()))
                    showBanner("Translated ${translated.size} lines to ${if (targetLangCode == "hi") "Hindi" else "English"} keeping timing aligned.")
                },
                onFailure = {
                    showBanner("Translation error: ${it.localizedMessage}")
                }
            )
        }
    }

    fun updateProjectMetadata(project: VideoProjectEntity) {
        viewModelScope.launch {
            projectDao.upsertProject(
                project.copy(
                    updatedAtMillis = System.currentTimeMillis(),
                    lastAutosaveMillis = System.currentTimeMillis()
                )
            )
        }
    }

    fun runFactCheckOnActiveProject(deepReasoning: Boolean) {
        val project = activeProject.value ?: return
        val fullText = buildString {
            appendLine(project.scriptHook)
            appendLine(project.scriptBody)
            activeScenes.value.forEach { appendLine(it.narrationText) }
        }
        viewModelScope.launch {
            container.aiRepository.factCheckScript(fullText, project.languageCode, deepReasoning).fold(
                onSuccess = { result ->
                    _factCheckState.value = result
                    projectDao.upsertProject(
                        project.copy(
                            factCheckNotes = result.verifiedSummary + "\n" + result.flaggedClaims.joinToString("\n• "),
                            needsVerification = result.needsVerification,
                            researchSourcesJson = result.sources.joinToString("\n"),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                },
                onFailure = {
                    showBanner("Fact-check failed: ${it.localizedMessage}")
                }
            )
        }
    }

    fun refreshSeoForActiveProject() {
        val project = activeProject.value ?: return
        val fullText = activeScenes.value.joinToString(" ") { it.narrationText }
        viewModelScope.launch {
            container.aiRepository.generateYouTubeSeo(
                topic = project.topic.ifBlank { project.title },
                scriptText = fullText,
                durationSec = project.targetDurationSec,
                languageCode = project.languageCode
            ).fold(
                onSuccess = { seo ->
                    projectDao.upsertProject(
                        project.copy(
                            seoTitle = seo.title,
                            seoDescription = seo.description,
                            seoTagsCsv = seo.tags.joinToString(", "),
                            seoHashtagsCsv = seo.hashtags.joinToString(" "),
                            seoChaptersJson = seo.chapters.joinToString("\n"),
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    showBanner("YouTube SEO metadata updated.")
                },
                onFailure = {
                    showBanner("SEO generation failed: ${it.localizedMessage}")
                }
            )
        }
    }

    // --- Stock Media & Asset Import ---
    fun searchStockMedia(query: String) {
        viewModelScope.launch {
            _isSearchingStock.value = true
            _stockSearchError.value = null
            container.stockMediaRepository.searchAllAvailable(query).fold(
                onSuccess = { _stockResults.value = it },
                onFailure = { _stockSearchError.value = it.localizedMessage ?: "Search failed." }
            )
            _isSearchingStock.value = false
        }
    }

    fun attachStockMediaToScene(item: StockMediaItem, scene: SceneEntity) {
        viewModelScope.launch {
            container.stockMediaRepository.downloadStockMedia(item).fold(
                onSuccess = { file ->
                    assetDao.upsertAsset(
                        MediaAssetEntity(
                            id = item.id,
                            title = item.title,
                            filePath = file.absolutePath,
                            assetType = item.mediaType,
                            mimeType = if (item.mediaType == "VIDEO") "video/mp4" else "image/jpeg",
                            sizeBytes = file.length(),
                            sourceProvider = item.sourceProvider,
                            sourceUrl = item.sourceUrl,
                            licenseName = item.licenseName,
                            attributionText = item.attributionText,
                            commercialAllowed = item.commercialAllowed
                        )
                    )
                    updateScene(
                        scene.copy(
                            mediaPath = file.absolutePath,
                            mediaType = item.mediaType,
                            sourceName = item.sourceProvider,
                            sourceUrl = item.sourceUrl,
                            licenseName = item.licenseName,
                            attributionText = item.attributionText,
                            commercialAllowed = item.commercialAllowed
                        )
                    )
                    showBanner("Applied ${item.sourceProvider} media (${item.licenseName}).")
                },
                onFailure = {
                    showBanner("Download failed: ${it.localizedMessage}")
                }
            )
        }
    }

    fun generateAiImageForScene(scene: SceneEntity, aspectRatio: String) {
        viewModelScope.launch {
            showBanner("Generating AI image for '${scene.title}'...")
            container.aiRepository.generateAiImage(scene.visualSearchQuery, aspectRatio).fold(
                onSuccess = { bmp ->
                    val file = saveBitmapToAppStorage(bmp, "ai_scene_${scene.id}.jpg")
                    assetDao.upsertAsset(
                        MediaAssetEntity(
                            id = UUID.randomUUID().toString(),
                            title = "AI: ${scene.title}",
                            filePath = file.absolutePath,
                            assetType = "AI_GENERATED",
                            mimeType = "image/jpeg",
                            sizeBytes = file.length(),
                            sourceProvider = "Gemini Nano Banana 2.1",
                            licenseName = "AI Generated Asset"
                        )
                    )
                    updateScene(
                        scene.copy(
                            mediaPath = file.absolutePath,
                            mediaType = "AI_IMAGE",
                            sourceName = "Gemini Nano Banana 2.1",
                            licenseName = "AI Generated Asset"
                        )
                    )
                    showBanner("AI image applied to ${scene.title}.")
                },
                onFailure = {
                    showBanner("AI image unavailable: ${it.localizedMessage}. Use Wikimedia/Pexels stock search instead.")
                }
            )
        }
    }

    fun generateAiVideoForScene(scene: SceneEntity, aspectRatio: String) {
        viewModelScope.launch {
            showBanner("Requesting AI video via Interactions API (gemini-omni-1.2-flash)...")
            val refImage = scene.mediaPath.takeIf { it.isNotBlank() && scene.mediaType != "VIDEO" }
                ?.let(::File)?.takeIf { it.exists() }
            container.interactionsVideoClient.generateVideoClip(
                prompt = scene.visualSearchQuery,
                aspectRatio = aspectRatio,
                referenceImageFile = refImage
            ).fold(
                onSuccess = { mp4 ->
                    assetDao.upsertAsset(
                        MediaAssetEntity(
                            id = UUID.randomUUID().toString(),
                            title = "AI Clip: ${scene.title}",
                            filePath = mp4.absolutePath,
                            assetType = "VIDEO",
                            mimeType = "video/mp4",
                            sizeBytes = mp4.length(),
                            sourceProvider = "Gemini Interactions API",
                            licenseName = "AI Generated Video"
                        )
                    )
                    updateScene(
                        scene.copy(
                            mediaPath = mp4.absolutePath,
                            mediaType = "AI_VIDEO",
                            sourceName = "Gemini Interactions API",
                            licenseName = "AI Generated Video"
                        )
                    )
                    showBanner("AI video clip applied to ${scene.title}.")
                },
                onFailure = {
                    showBanner(it.localizedMessage ?: "AI video generation unavailable.")
                }
            )
        }
    }

    fun importLocalUriAsAsset(uri: Uri, assetType: String, title: String, attachToScene: SceneEntity? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dir = File(appContext.filesDir, "imported_media").apply { mkdirs() }
                val mime = appContext.contentResolver.getType(uri).orEmpty()
                val ext = when {
                    mime.contains("video") -> "mp4"
                    mime.contains("audio") -> "wav"
                    else -> "jpg"
                }
                val dest = File(dir, "import_${System.currentTimeMillis()}.$ext")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                }
                val resolvedType = if (mime.contains("video")) "VIDEO" else if (mime.contains("audio")) "AUDIO" else assetType
                val asset = MediaAssetEntity(
                    id = UUID.randomUUID().toString(),
                    title = title.ifBlank { dest.name },
                    filePath = dest.absolutePath,
                    assetType = resolvedType,
                    mimeType = mime.ifBlank { "application/octet-stream" },
                    sizeBytes = dest.length(),
                    sourceProvider = "User Import",
                    licenseName = "User Owned / Licensed"
                )
                assetDao.upsertAsset(asset)
                if (attachToScene != null) {
                    updateScene(
                        attachToScene.copy(
                            mediaPath = dest.absolutePath,
                            mediaType = if (resolvedType == "VIDEO") "VIDEO" else "IMAGE",
                            sourceName = "User Import",
                            licenseName = "User Licensed"
                        )
                    )
                }
                refreshStorageStats()
                showBanner("Imported '${asset.title}' locally.")
            }.onFailure {
                showBanner("Import failed: ${it.localizedMessage}")
            }
        }
    }

    fun renameAsset(id: String, newTitle: String) {
        viewModelScope.launch { assetDao.renameAsset(id, newTitle.trim()) }
    }

    fun deleteAsset(asset: MediaAssetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(asset.filePath).delete() }
            assetDao.deleteAssetById(asset.id)
            refreshStorageStats()
        }
    }

    // --- Export System ---
    fun exportActiveProjectNow(settings: ExportSettings) {
        val project = activeProject.value ?: return
        val scenes = activeScenes.value
        val clips = activeTimelineClips.value
        val subs = activeSubtitles.value
        viewModelScope.launch {
            val brandKit = brandKitDao.getDefaultBrandKit()
            _exportProgress.value = 1
            _exportMessage.value = "Starting export..."
            val res = container.videoRenderEngine.exportProjectMp4(
                project = project,
                scenes = scenes,
                clips = clips,
                subtitles = subs,
                brandKit = brandKit,
                settings = settings,
                onProgress = { pct, msg ->
                    _exportProgress.value = pct
                    _exportMessage.value = msg
                }
            )
            res.fold(
                onSuccess = { outFile ->
                    projectDao.upsertProject(
                        project.copy(
                            exportedVideoPath = outFile.absolutePath,
                            status = "EXPORTED",
                            updatedAtMillis = System.currentTimeMillis()
                        )
                    )
                    assetDao.upsertAsset(
                        MediaAssetEntity(
                            id = UUID.randomUUID().toString(),
                            title = "${project.title} (Export MP4)",
                            filePath = outFile.absolutePath,
                            assetType = "VIDEO",
                            mimeType = "video/mp4",
                            sizeBytes = outFile.length(),
                            sourceProvider = "AI Video Studio Render",
                            licenseName = "Creator Export"
                        )
                    )
                    refreshStorageStats()
                    _exportProgress.value = null
                    showBanner("MP4 exported: ${outFile.name} (also saved to Movies/AI Video Studio)")
                },
                onFailure = { err ->
                    _exportProgress.value = null
                    showBanner("Export error: ${err.localizedMessage}")
                }
            )
        }
    }

    fun enqueueBackgroundExport(settings: ExportSettings) {
        val project = activeProject.value ?: return
        StudioWorkScheduler.enqueueRenderJob(
            context = appContext,
            projectId = project.id,
            resolutionName = settings.resolution.name,
            fps = settings.fps,
            batterySaver = settings.batterySaverMode
        )
        showBanner("Queued '${project.title}' in background Render Queue.")
    }

    fun cancelActiveExport() {
        container.videoRenderEngine.cancelExport()
        _exportProgress.value = null
        showBanner("Export cancelled.")
    }

    // --- Voice Studio ---
    fun previewVoice(text: String, langCode: String, voiceId: String?, speed: Float, pitch: Float) {
        viewModelScope.launch {
            val ok = container.voiceStudioManager.previewVoice(text, langCode, voiceId, speed, pitch)
            if (!ok) showBanner("Device TTS engine is not ready or unavailable.")
        }
    }

    fun synthesizeVoiceToScene(
        scene: SceneEntity,
        langCode: String,
        voiceId: String?,
        speed: Float,
        pitch: Float
    ) {
        viewModelScope.launch {
            container.voiceStudioManager.synthesizeToWav(
                text = scene.narrationText.ifBlank { scene.title },
                languageCode = langCode,
                voiceId = voiceId,
                speed = speed,
                pitch = pitch,
                outputFileName = "vo_${scene.projectId}_${scene.id}.wav"
            ).fold(
                onSuccess = { wav ->
                    updateScene(scene.copy(voiceoverPath = wav.absolutePath))
                    showBanner("Voiceover synced to '${scene.title}'.")
                },
                onFailure = {
                    showBanner("TTS synthesis failed: ${it.localizedMessage}")
                }
            )
        }
    }

    // --- Thumbnail Maker ---
    suspend fun generateThumbnailPreview(config: ThumbnailConfig): Bitmap =
        ThumbnailGenerator.renderThumbnailBitmap(appContext, config)

    fun saveThumbnailForProject(project: VideoProjectEntity?, bitmap: Bitmap) {
        viewModelScope.launch {
            ThumbnailGenerator.saveThumbnailToAppAndGallery(
                appContext,
                bitmap,
                project?.title?.take(20) ?: "thumb"
            ).fold(
                onSuccess = { file ->
                    if (project != null) {
                        projectDao.upsertProject(
                            project.copy(
                                thumbnailPath = file.absolutePath,
                                updatedAtMillis = System.currentTimeMillis()
                            )
                        )
                    }
                    assetDao.upsertAsset(
                        MediaAssetEntity(
                            id = UUID.randomUUID().toString(),
                            title = "Thumbnail: ${project?.title ?: "Custom"}",
                            filePath = file.absolutePath,
                            assetType = "IMAGE",
                            mimeType = "image/jpeg",
                            sizeBytes = file.length(),
                            sourceProvider = "Thumbnail Maker",
                            licenseName = "Creator Asset"
                        )
                    )
                    showBanner("Thumbnail saved to Pictures/AI Video Studio")
                },
                onFailure = {
                    showBanner("Thumbnail save failed: ${it.localizedMessage}")
                }
            )
        }
    }

    // --- Batch Creator ---
    fun runBatchShortsGeneration(topics: List<String>, languageCode: String, queueRender: Boolean) {
        val cleanTopics = topics.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanTopics.isEmpty()) return
        viewModelScope.launch {
            var createdCount = 0
            cleanTopics.forEachIndexed { idx, topic ->
                val jobId = "batch_${System.currentTimeMillis()}_$idx"
                jobDao.upsertJob(
                    BackgroundJobEntity(
                        id = jobId,
                        jobType = "AI_BATCH",
                        title = "Batch Short: ${topic.take(32)}",
                        status = "RUNNING",
                        progressPercent = 10
                    )
                )
                val res = container.aiRepository.generateFullVideoPlan(
                    topic = topic,
                    category = "Facts",
                    aspectRatio = "9:16",
                    targetDurationSec = 45,
                    languageCode = languageCode
                )
                res.fold(
                    onSuccess = { plan ->
                        val pid = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        val proj = VideoProjectEntity(
                            id = pid,
                            title = plan.title.ifBlank { topic },
                            topic = topic,
                            aspectRatio = "9:16",
                            targetDurationSec = 45,
                            languageCode = languageCode,
                            scriptHook = plan.hook,
                            scriptBody = plan.body,
                            scriptCta = plan.cta,
                            seoTitle = plan.seoTitle,
                            seoDescription = plan.seoDescription,
                            seoTagsCsv = plan.seoTags.joinToString(", "),
                            seoHashtagsCsv = plan.seoHashtags.joinToString(" "),
                            status = "READY",
                            createdAtMillis = now,
                            updatedAtMillis = now
                        )
                        val scenes = plan.scenes.mapIndexed { sIdx, sp ->
                            SceneEntity(
                                id = UUID.randomUUID().toString(),
                                projectId = pid,
                                orderIndex = sIdx,
                                title = sp.title,
                                narrationText = sp.narration,
                                visualSearchQuery = sp.visualQuery,
                                durationMs = (sp.durationSec.coerceIn(3, 20) * 1000L),
                                trimEndMs = (sp.durationSec.coerceIn(3, 20) * 1000L)
                            )
                        }
                        projectDao.upsertProject(proj)
                        projectDao.upsertScenes(scenes)
                        syncMainTrackFromScenes(pid, scenes)
                        if (queueRender) {
                            StudioWorkScheduler.enqueueRenderJob(appContext, pid, ExportResolution.HD_720.name, 30, true)
                        }
                        jobDao.upsertJob(
                            BackgroundJobEntity(
                                id = jobId,
                                jobType = "AI_BATCH",
                                title = "Batch Short: ${proj.title.take(32)}",
                                projectId = pid,
                                status = "COMPLETED",
                                progressPercent = 100,
                                detailMessage = "${scenes.size} scenes created"
                            )
                        )
                        createdCount++
                    },
                    onFailure = { err ->
                        jobDao.upsertJob(
                            BackgroundJobEntity(
                                id = jobId,
                                jobType = "AI_BATCH",
                                title = "Batch Short: ${topic.take(32)}",
                                status = "FAILED",
                                progressPercent = 0,
                                detailMessage = err.localizedMessage ?: "Failed"
                            )
                        )
                    }
                )
            }
            showBanner("Batch completed: $createdCount/${cleanTopics.size} Shorts created safely.")
        }
    }

    fun convertLongProjectToShorts(sourceProject: VideoProjectEntity, clipCount: Int) {
        viewModelScope.launch {
            val scenes = projectDao.getScenes(sourceProject.id).sortedBy { it.orderIndex }
            if (scenes.isEmpty()) {
                showBanner("Source project has no scenes to split.")
                return@launch
            }
            val chunks = scenes.chunked((scenes.size / clipCount.coerceIn(2, 5)).coerceAtLeast(1))
            chunks.forEachIndexed { idx, chunk ->
                val shortId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val shortProject = sourceProject.copy(
                    id = shortId,
                    title = "${sourceProject.title} • Short #${idx + 1}",
                    aspectRatio = "9:16",
                    targetDurationSec = (chunk.sumOf { it.durationMs } / 1000L).toInt().coerceIn(15, 60),
                    exportedVideoPath = "",
                    status = "READY",
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
                val shortScenes = chunk.mapIndexed { sIdx, sc ->
                    sc.copy(id = UUID.randomUUID().toString(), projectId = shortId, orderIndex = sIdx)
                }
                projectDao.upsertProject(shortProject)
                projectDao.upsertScenes(shortScenes)
                syncMainTrackFromScenes(shortId, shortScenes)
            }
            showBanner("Created ${chunks.size} vertical 9:16 Shorts from '${sourceProject.title}'.")
        }
    }

    // --- Video Analysis (Multimodal Gemini) ---
    fun analyzeSelectedVideo(uri: Uri, focusPrompt: String) {
        viewModelScope.launch {
            _isAnalyzingVideo.value = true
            _videoAnalysisResult.value = null
            container.aiRepository.analyzeVideoFrames(appContext, uri, focusPrompt).fold(
                onSuccess = { _videoAnalysisResult.value = it },
                onFailure = { _videoAnalysisResult.value = "Analysis error: ${it.localizedMessage}" }
            )
            _isAnalyzingVideo.value = false
        }
    }

    // --- Project Management ---
    fun renameProject(project: VideoProjectEntity, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            projectDao.upsertProject(project.copy(title = newTitle.trim(), updatedAtMillis = System.currentTimeMillis()))
        }
    }

    fun duplicateProject(project: VideoProjectEntity) {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val scenes = projectDao.getScenes(project.id).map { it.copy(id = UUID.randomUUID().toString(), projectId = newId) }
            val clips = projectDao.getTimelineClips(project.id).map { it.copy(id = UUID.randomUUID().toString(), projectId = newId) }
            val subs = projectDao.getSubtitles(project.id).map { it.copy(id = UUID.randomUUID().toString(), projectId = newId) }
            projectDao.upsertProject(
                project.copy(
                    id = newId,
                    title = "${project.title} (Copy)",
                    exportedVideoPath = "",
                    isCloudBackedUp = false,
                    createdAtMillis = now,
                    updatedAtMillis = now
                )
            )
            projectDao.upsertScenes(scenes)
            projectDao.upsertTimelineClips(clips)
            projectDao.upsertSubtitles(subs)
            showBanner("Duplicated '${project.title}'.")
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (_activeProjectId.value == projectId) _activeProjectId.value = null
            projectDao.deleteFullProject(projectId)
            refreshStorageStats()
            showBanner("Project deleted.")
        }
    }

    suspend fun exportProjectBundleJson(projectId: String): String? {
        val proj = projectDao.getProjectById(projectId) ?: return null
        val bundle = ProjectBundle(
            project = proj,
            scenes = projectDao.getScenes(projectId),
            timelineClips = projectDao.getTimelineClips(projectId),
            subtitles = projectDao.getSubtitles(projectId)
        )
        return ProjectBackupSerializer.exportToJson(bundle)
    }

    fun importProjectBundleJson(rawJson: String) {
        viewModelScope.launch {
            ProjectBackupSerializer.importFromJson(rawJson).fold(
                onSuccess = { bundle ->
                    projectDao.upsertProject(bundle.project)
                    projectDao.upsertScenes(bundle.scenes)
                    projectDao.upsertTimelineClips(bundle.timelineClips)
                    projectDao.upsertSubtitles(bundle.subtitles)
                    selectProject(bundle.project.id)
                    showBanner("Imported project '${bundle.project.title}'.")
                },
                onFailure = {
                    showBanner("Invalid project file: ${it.localizedMessage}")
                }
            )
        }
    }

    // --- Brand Kit ---
    fun saveBrandKit(kit: BrandKitEntity) {
        viewModelScope.launch {
            brandKitDao.upsertBrandKit(kit.copy(updatedAtMillis = System.currentTimeMillis()))
            showBanner("Brand Kit saved.")
        }
    }

    // --- YouTube & Upload Scheduler ---
    fun launchYouTubeOAuth() {
        container.youTubeRepository.launchOAuthSignIn().onFailure {
            showBanner(it.localizedMessage ?: "Could not open OAuth sign-in.")
        }
    }

    fun handleYouTubeOAuthRedirect(uri: Uri) {
        viewModelScope.launch {
            container.youTubeRepository.handleOAuthRedirect(uri).fold(
                onSuccess = { info ->
                    prefsRepo.setYouTubeChannel(info.channelId, info.title, info.subscriberCount)
                    showBanner("Connected YouTube channel: ${info.title}")
                },
                onFailure = {
                    showBanner("YouTube OAuth failed: ${it.localizedMessage}")
                }
            )
        }
    }

    fun disconnectYouTubeChannel() {
        container.youTubeRepository.disconnectChannel()
        viewModelScope.launch {
            prefsRepo.setYouTubeChannel("", "", "")
            showBanner("YouTube channel disconnected.")
        }
    }

    fun scheduleOrUploadNow(
        project: VideoProjectEntity,
        videoTitle: String,
        description: String,
        tagsCsv: String,
        visibility: String,
        scheduledAtMillis: Long,
        userExplicitlyAuthorized: Boolean
    ) {
        if (!userExplicitlyAuthorized) {
            showBanner("Explicit authorization is required before uploading to YouTube.")
            return
        }
        if (project.exportedVideoPath.isBlank() || !File(project.exportedVideoPath).exists()) {
            showBanner("Please export the project as an MP4 first before uploading.")
            return
        }
        if (!container.youTubeRepository.isConnected()) {
            showBanner("Connect your YouTube channel via OAuth 2.0 first.")
            return
        }
        viewModelScope.launch {
            val fingerprint = sha256("${project.exportedVideoPath}|${videoTitle.trim()}|$visibility")
            if (uploadDao.countDuplicateFingerprint(fingerprint) > 0) {
                showBanner("Duplicate upload blocked: this video & title is already queued or uploaded.")
                return@launch
            }
            val uploadId = UUID.randomUUID().toString()
            val delay = (scheduledAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val entity = ScheduledUploadEntity(
                id = uploadId,
                projectId = project.id,
                projectTitle = project.title,
                videoFilePath = project.exportedVideoPath,
                thumbnailPath = project.thumbnailPath,
                videoTitle = videoTitle.trim().take(100),
                description = description.take(5000),
                tagsCsv = tagsCsv,
                visibility = visibility,
                scheduledAtMillis = scheduledAtMillis,
                status = "QUEUED",
                uploadFingerprint = fingerprint
            )
            uploadDao.upsertUpload(entity)
            StudioWorkScheduler.enqueueScheduledUpload(appContext, uploadId, delay)
            showBanner(if (delay < 5000L) "Uploading video to YouTube..." else "Upload scheduled safely.")
        }
    }

    fun pauseUpload(item: ScheduledUploadEntity) {
        viewModelScope.launch {
            StudioWorkScheduler.cancelScheduledUpload(appContext, item.id)
            uploadDao.upsertUpload(item.copy(status = "PAUSED", updatedAtMillis = System.currentTimeMillis()))
        }
    }

    fun resumeOrRetryUpload(item: ScheduledUploadEntity) {
        viewModelScope.launch {
            val delay = (item.scheduledAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            uploadDao.upsertUpload(item.copy(status = "QUEUED", errorMessage = null, updatedAtMillis = System.currentTimeMillis()))
            StudioWorkScheduler.enqueueScheduledUpload(appContext, item.id, delay)
        }
    }

    fun cancelUpload(item: ScheduledUploadEntity) {
        viewModelScope.launch {
            StudioWorkScheduler.cancelScheduledUpload(appContext, item.id)
            uploadDao.upsertUpload(item.copy(status = "CANCELLED", updatedAtMillis = System.currentTimeMillis()))
        }
    }

    fun rescheduleUpload(item: ScheduledUploadEntity, newTimestampMillis: Long) {
        viewModelScope.launch {
            val delay = (newTimestampMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            uploadDao.upsertUpload(
                item.copy(
                    scheduledAtMillis = newTimestampMillis,
                    status = "QUEUED",
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
            StudioWorkScheduler.enqueueScheduledUpload(appContext, item.id, delay)
        }
    }

    // --- Storage & Cleanup ---
    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val stat = StatFs(appContext.filesDir.absolutePath)
            val exportsSize = dirSize(File(appContext.filesDir, "exports"))
            val stockSize = dirSize(File(appContext.filesDir, "stock_media")) +
                dirSize(File(appContext.filesDir, "voiceovers")) +
                dirSize(File(appContext.filesDir, "imported_media")) +
                dirSize(File(appContext.filesDir, "thumbnails"))
            val cacheSize = dirSize(appContext.cacheDir)
            _storageStats.value = StorageStats(
                freeDeviceBytes = stat.availableBytes,
                totalDeviceBytes = stat.totalBytes,
                projectsBytes = stockSize,
                exportsBytes = exportsSize,
                tempCacheBytes = cacheSize
            )
        }
    }

    fun cleanTemporaryCache() {
        viewModelScope.launch(Dispatchers.IO) {
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            refreshStorageStats()
            showBanner("Temporary render cache cleaned.")
        }
    }

    fun deleteAllUserLocalData() {
        viewModelScope.launch(Dispatchers.IO) {
            container.database.clearAllTables()
            keyStore.clearAll()
            prefsRepo.resetAllPreferences()
            appContext.filesDir.listFiles()?.forEach { if (it.name != "datastore") it.deleteRecursively() }
            appContext.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            _activeProjectId.value = null
            refreshStorageStats()
            showBanner("All local projects, keys, and temporary files deleted.")
        }
    }

    private suspend fun syncMainTrackFromScenes(projectId: String, explicitScenes: List<SceneEntity>? = null) {
        val ordered = (explicitScenes ?: projectDao.getScenes(projectId)).sortedBy { it.orderIndex }
        val existingNonMain = projectDao.getTimelineClips(projectId).filter { it.trackType != "VIDEO_MAIN" }
        var cursor = 0L
        val mainClips = ordered.map { sc ->
            val clip = TimelineClipEntity(
                id = "main_${sc.id}",
                projectId = projectId,
                trackType = "VIDEO_MAIN",
                trackIndex = 0,
                label = sc.title,
                mediaPath = sc.mediaPath,
                startTimeMs = cursor,
                durationMs = sc.durationMs,
                trimStartMs = sc.trimStartMs,
                trimEndMs = sc.trimEndMs,
                speed = sc.speed,
                rotationDegrees = sc.rotationDegrees,
                flipHorizontal = sc.flipHorizontal,
                transitionIn = sc.transitionType,
                filterPreset = sc.colorFilter
            )
            cursor += sc.durationMs
            clip
        }
        projectDao.deleteTimelineClipsForProject(projectId)
        projectDao.upsertTimelineClips(mainClips + existingNonMain)
    }

    private suspend fun touchAutosave(projectId: String) {
        val current = projectDao.getProjectById(projectId) ?: return
        val now = System.currentTimeMillis()
        projectDao.upsertProject(current.copy(updatedAtMillis = now, lastAutosaveMillis = now))
    }

    private suspend fun saveBitmapToAppStorage(bitmap: Bitmap, fileName: String): File = withContext(Dispatchers.IO) {
        val dir = File(appContext.filesDir, "ai_images").apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out) }
        file
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    override fun onCleared() {
        super.onCleared()
        container.voiceStudioManager.release()
    }

    companion object {
        fun provideFactory(appContext: Context, container: StudioContainer): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    StudioViewModel(appContext.applicationContext, container)
                }
            }
    }
}
