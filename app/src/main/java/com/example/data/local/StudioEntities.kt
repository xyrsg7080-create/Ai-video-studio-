package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "video_projects")
data class VideoProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val topic: String,
    val contentCategory: String = "Explainer",
    val aspectRatio: String = "9:16",
    val customWidth: Int = 1080,
    val customHeight: Int = 1920,
    val targetDurationSec: Int = 60,
    val languageCode: String = "en",
    val scriptHook: String = "",
    val scriptBody: String = "",
    val scriptCta: String = "",
    val factCheckNotes: String = "",
    val needsVerification: Boolean = false,
    val researchSourcesJson: String = "[]",
    val seoTitle: String = "",
    val seoDescription: String = "",
    val seoTagsCsv: String = "",
    val seoHashtagsCsv: String = "",
    val seoChaptersJson: String = "[]",
    val thumbnailPath: String = "",
    val exportedVideoPath: String = "",
    val status: String = "DRAFT",
    val autoEditEnabled: Boolean = true,
    val beatSyncEnabled: Boolean = true,
    val audioDuckingEnabled: Boolean = true,
    val selectedMusicTrackId: String = "ambient_pulse",
    val musicVolume: Float = 0.28f,
    val voiceVolume: Float = 1.0f,
    val brandKitId: String? = null,
    val applyBrandKit: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val lastAutosaveMillis: Long = System.currentTimeMillis(),
    val isCloudBackedUp: Boolean = false
)

@Serializable
@Entity(tableName = "project_scenes")
data class SceneEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int,
    val title: String,
    val narrationText: String,
    val visualSearchQuery: String,
    val durationMs: Long = 5000L,
    val mediaPath: String = "",
    val mediaType: String = "NONE",
    val sourceName: String = "Local",
    val sourceUrl: String = "",
    val licenseName: String = "User Owned",
    val attributionText: String = "",
    val commercialAllowed: Boolean = true,
    val transitionType: String = "CROSSFADE",
    val motionEffect: String = "KEN_BURNS_ZOOM_IN",
    val colorFilter: String = "NONE",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val speed: Float = 1f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val cropScale: Float = 1f,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5000L,
    val voiceoverPath: String = "",
    val sfxType: String = "NONE",
    val keyframeStartScale: Float = 1.0f,
    val keyframeEndScale: Float = 1.12f
)

@Serializable
@Entity(tableName = "timeline_clips")
data class TimelineClipEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val trackType: String,
    val trackIndex: Int,
    val label: String,
    val mediaPath: String = "",
    val overlayText: String = "",
    val textColorHex: String = "#FFFFFF",
    val fontSizeSp: Float = 26f,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 4000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 4000L,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val posX: Float = 0.5f,
    val posY: Float = 0.5f,
    val scaleStart: Float = 1.0f,
    val scaleEnd: Float = 1.0f,
    val transitionIn: String = "NONE",
    val filterPreset: String = "NONE",
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val zOrder: Int = 0
)

@Serializable
@Entity(tableName = "project_subtitles")
data class SubtitleEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val orderIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
    val translatedText: String = "",
    val fontFamily: String = "Space Grotesk",
    val fontSizeSp: Float = 22f,
    val textColorHex: String = "#FFFFFF",
    val backgroundColorHex: String = "#990B0F1A",
    val outlineEnabled: Boolean = true,
    val position: String = "BOTTOM",
    val animationStyle: String = "WORD_HIGHLIGHT"
)

@Serializable
@Entity(tableName = "media_assets")
data class MediaAssetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val filePath: String,
    val assetType: String,
    val mimeType: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val sourceProvider: String = "Local Import",
    val sourceUrl: String = "",
    val licenseName: String = "User Licensed",
    val attributionText: String = "",
    val commercialAllowed: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "brand_kits")
data class BrandKitEntity(
    @PrimaryKey val id: String,
    val channelName: String,
    val logoPath: String = "",
    val primaryColorHex: String = "#22D3EE",
    val accentColorHex: String = "#FF6B4A",
    val fontFamily: String = "Space Grotesk",
    val introText: String = "",
    val outroText: String = "Subscribe for more!",
    val watermarkText: String = "",
    val watermarkEnabled: Boolean = false,
    val defaultCaptionStyle: String = "WORD_HIGHLIGHT",
    val defaultAspectRatio: String = "9:16",
    val applyByDefault: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "scheduled_uploads")
data class ScheduledUploadEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val projectTitle: String,
    val videoFilePath: String,
    val thumbnailPath: String = "",
    val videoTitle: String,
    val description: String,
    val tagsCsv: String = "",
    val categoryId: String = "22",
    val visibility: String = "private",
    val scheduledAtMillis: Long,
    val status: String = "QUEUED",
    val progressPercent: Int = 0,
    val youtubeVideoId: String? = null,
    val errorMessage: String? = null,
    val uploadFingerprint: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Serializable
@Entity(tableName = "background_jobs")
data class BackgroundJobEntity(
    @PrimaryKey val id: String,
    val jobType: String,
    val title: String,
    val projectId: String = "",
    val status: String = "PENDING",
    val progressPercent: Int = 0,
    val detailMessage: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
