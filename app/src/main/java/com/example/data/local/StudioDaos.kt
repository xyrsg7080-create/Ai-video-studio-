package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM video_projects ORDER BY updatedAtMillis DESC")
    fun observeAllProjects(): Flow<List<VideoProjectEntity>>

    @Query("SELECT * FROM video_projects WHERE id = :projectId LIMIT 1")
    fun observeProject(projectId: String): Flow<VideoProjectEntity?>

    @Query("SELECT * FROM video_projects WHERE id = :projectId LIMIT 1")
    suspend fun getProjectById(projectId: String): VideoProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: VideoProjectEntity)

    @Query("DELETE FROM video_projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("SELECT * FROM project_scenes WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun observeScenes(projectId: String): Flow<List<SceneEntity>>

    @Query("SELECT * FROM project_scenes WHERE projectId = :projectId ORDER BY orderIndex ASC")
    suspend fun getScenes(projectId: String): List<SceneEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScenes(scenes: List<SceneEntity>)

    @Query("DELETE FROM project_scenes WHERE projectId = :projectId")
    suspend fun deleteScenesForProject(projectId: String)

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, startTimeMs ASC")
    fun observeTimelineClips(projectId: String): Flow<List<TimelineClipEntity>>

    @Query("SELECT * FROM timeline_clips WHERE projectId = :projectId ORDER BY trackIndex ASC, startTimeMs ASC")
    suspend fun getTimelineClips(projectId: String): List<TimelineClipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineClips(clips: List<TimelineClipEntity>)

    @Query("DELETE FROM timeline_clips WHERE projectId = :projectId")
    suspend fun deleteTimelineClipsForProject(projectId: String)

    @Query("SELECT * FROM project_subtitles WHERE projectId = :projectId ORDER BY orderIndex ASC, startTimeMs ASC")
    fun observeSubtitles(projectId: String): Flow<List<SubtitleEntity>>

    @Query("SELECT * FROM project_subtitles WHERE projectId = :projectId ORDER BY orderIndex ASC, startTimeMs ASC")
    suspend fun getSubtitles(projectId: String): List<SubtitleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubtitles(subtitles: List<SubtitleEntity>)

    @Query("DELETE FROM project_subtitles WHERE projectId = :projectId")
    suspend fun deleteSubtitlesForProject(projectId: String)

    @Transaction
    suspend fun deleteFullProject(projectId: String) {
        deleteScenesForProject(projectId)
        deleteTimelineClipsForProject(projectId)
        deleteSubtitlesForProject(projectId)
        deleteProjectById(projectId)
    }
}

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets ORDER BY createdAtMillis DESC")
    fun observeAllAssets(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE assetType = :type ORDER BY createdAtMillis DESC")
    fun observeAssetsByType(type: String): Flow<List<MediaAssetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: MediaAssetEntity)

    @Query("UPDATE media_assets SET title = :newTitle WHERE id = :id")
    suspend fun renameAsset(id: String, newTitle: String)

    @Query("DELETE FROM media_assets WHERE id = :id")
    suspend fun deleteAssetById(id: String)
}

@Dao
interface BrandKitDao {
    @Query("SELECT * FROM brand_kits ORDER BY updatedAtMillis DESC")
    fun observeAllBrandKits(): Flow<List<BrandKitEntity>>

    @Query("SELECT * FROM brand_kits LIMIT 1")
    suspend fun getDefaultBrandKit(): BrandKitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBrandKit(kit: BrandKitEntity)

    @Query("DELETE FROM brand_kits WHERE id = :id")
    suspend fun deleteBrandKit(id: String)
}

@Dao
interface UploadScheduleDao {
    @Query("SELECT * FROM scheduled_uploads ORDER BY scheduledAtMillis ASC")
    fun observeAllUploads(): Flow<List<ScheduledUploadEntity>>

    @Query("SELECT * FROM scheduled_uploads WHERE id = :id LIMIT 1")
    suspend fun getUploadById(id: String): ScheduledUploadEntity?

    @Query("SELECT COUNT(*) FROM scheduled_uploads WHERE uploadFingerprint = :fingerprint AND status IN ('QUEUED', 'UPLOADING', 'COMPLETED')")
    suspend fun countDuplicateFingerprint(fingerprint: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUpload(upload: ScheduledUploadEntity)

    @Query("DELETE FROM scheduled_uploads WHERE id = :id")
    suspend fun deleteUploadById(id: String)
}

@Dao
interface BackgroundJobDao {
    @Query("SELECT * FROM background_jobs ORDER BY updatedAtMillis DESC")
    fun observeAllJobs(): Flow<List<BackgroundJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: BackgroundJobEntity)

    @Query("DELETE FROM background_jobs WHERE status IN ('COMPLETED', 'CANCELLED', 'FAILED')")
    suspend fun clearFinishedJobs()
}
