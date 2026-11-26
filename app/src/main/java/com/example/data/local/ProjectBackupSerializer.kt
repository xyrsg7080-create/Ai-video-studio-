package com.example.data.local

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ProjectBundle(
    val formatVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val project: VideoProjectEntity,
    val scenes: List<SceneEntity>,
    val timelineClips: List<TimelineClipEntity>,
    val subtitles: List<SubtitleEntity>
)

object ProjectBackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportToJson(bundle: ProjectBundle): String = json.encodeToString(bundle)

    fun importFromJson(rawJson: String): Result<ProjectBundle> = runCatching {
        val parsed = json.decodeFromString<ProjectBundle>(rawJson)
        val newProjectId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        parsed.copy(
            project = parsed.project.copy(
                id = newProjectId,
                title = "${parsed.project.title} (Imported)",
                updatedAtMillis = now,
                lastAutosaveMillis = now,
                isCloudBackedUp = false
            ),
            scenes = parsed.scenes.map {
                it.copy(id = UUID.randomUUID().toString(), projectId = newProjectId)
            },
            timelineClips = parsed.timelineClips.map {
                it.copy(id = UUID.randomUUID().toString(), projectId = newProjectId)
            },
            subtitles = parsed.subtitles.map {
                it.copy(id = UUID.randomUUID().toString(), projectId = newProjectId)
            }
        )
    }
}
