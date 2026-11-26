package com.example.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.core.security.SecureKeyStore
import com.example.data.local.AppDatabase
import com.example.data.local.BackgroundJobEntity
import com.example.data.local.PreferencesRepository
import com.example.data.remote.YouTubeRepository
import com.example.media.ExportResolution
import com.example.media.ExportSettings
import com.example.media.VideoRenderEngine
import java.io.File
import java.util.concurrent.TimeUnit

object StudioWorkScheduler {
    const val CHANNEL_ID = "studio_background_jobs"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Video Studio Tasks",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background video rendering, media downloads, and YouTube uploads"
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun enqueueRenderJob(
        context: Context,
        projectId: String,
        resolutionName: String,
        fps: Int,
        batterySaver: Boolean
    ) {
        ensureNotificationChannel(context)
        val req = OneTimeWorkRequestBuilder<RenderVideoWorker>()
            .setInputData(
                workDataOf(
                    "project_id" to projectId,
                    "resolution" to resolutionName,
                    "fps" to fps,
                    "battery_saver" to batterySaver
                )
            )
            .addTag("render_$projectId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "render_queue_$projectId",
            ExistingWorkPolicy.KEEP,
            req
        )
    }

    fun enqueueScheduledUpload(
        context: Context,
        uploadId: String,
        delayMillis: Long
    ) {
        ensureNotificationChannel(context)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<ScheduledUploadWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("upload_id" to uploadId))
            .addTag("upload_$uploadId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "youtube_upload_$uploadId",
            ExistingWorkPolicy.REPLACE,
            req
        )
    }

    fun cancelScheduledUpload(context: Context, uploadId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("youtube_upload_$uploadId")
    }
}

class RenderVideoWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val projectId = inputData.getString("project_id") ?: return Result.failure()
        val resName = inputData.getString("resolution") ?: ExportResolution.HD_720.name
        val fps = inputData.getInt("fps", 30)
        val batterySaver = inputData.getBoolean("battery_saver", false)

        val db = AppDatabase.getInstance(applicationContext)
        val jobId = "render_$projectId"
        val project = db.projectDao().getProjectById(projectId) ?: return Result.failure()
        val scenes = db.projectDao().getScenes(projectId)
        val clips = db.projectDao().getTimelineClips(projectId)
        val subtitles = db.projectDao().getSubtitles(projectId)
        val brandKit = db.brandKitDao().getDefaultBrandKit()

        setForeground(createForegroundInfo("Rendering ${project.title}", 5))
        db.backgroundJobDao().upsertJob(
            BackgroundJobEntity(
                id = jobId,
                jobType = "RENDER",
                title = "Render: ${project.title}",
                projectId = projectId,
                status = "RUNNING",
                progressPercent = 5,
                detailMessage = "Starting Media3 Transformer"
            )
        )

        val engine = VideoRenderEngine(applicationContext)
        val resEnum = runCatching { ExportResolution.valueOf(resName) }.getOrDefault(ExportResolution.HD_720)
        val exportResult = engine.exportProjectMp4(
            project = project,
            scenes = scenes,
            clips = clips,
            subtitles = subtitles,
            brandKit = brandKit,
            settings = ExportSettings(resolution = resEnum, fps = fps, batterySaverMode = batterySaver),
            onProgress = { pct, msg ->
                // Best-effort status update
                setProgressAsync(workDataOf("progress" to pct, "message" to msg))
            }
        )

        return exportResult.fold(
            onSuccess = { outFile ->
                db.projectDao().upsertProject(
                    project.copy(
                        exportedVideoPath = outFile.absolutePath,
                        status = "EXPORTED",
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                db.backgroundJobDao().upsertJob(
                    BackgroundJobEntity(
                        id = jobId,
                        jobType = "RENDER",
                        title = "Render: ${project.title}",
                        projectId = projectId,
                        status = "COMPLETED",
                        progressPercent = 100,
                        detailMessage = "Saved ${outFile.name}"
                    )
                )
                Result.success(workDataOf("output_path" to outFile.absolutePath))
            },
            onFailure = { err ->
                db.backgroundJobDao().upsertJob(
                    BackgroundJobEntity(
                        id = jobId,
                        jobType = "RENDER",
                        title = "Render: ${project.title}",
                        projectId = projectId,
                        status = "FAILED",
                        progressPercent = 0,
                        detailMessage = err.localizedMessage ?: "Export failed"
                    )
                )
                Result.failure(workDataOf("error" to (err.localizedMessage ?: "Export failed")))
            }
        )
    }

    private fun createForegroundInfo(title: String, progress: Int): ForegroundInfo {
        StudioWorkScheduler.ensureNotificationChannel(applicationContext)
        val notification = NotificationCompat.Builder(applicationContext, StudioWorkScheduler.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Processing video ($progress%)")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1001, notification)
        }
    }
}

class ScheduledUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uploadId = inputData.getString("upload_id") ?: return Result.failure()
        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.uploadScheduleDao()
        val item = dao.getUploadById(uploadId) ?: return Result.failure()
        if (item.status == "CANCELLED" || item.status == "PAUSED" || item.status == "COMPLETED") {
            return Result.success()
        }

        val jobId = "upload_$uploadId"
        dao.upsertUpload(item.copy(status = "UPLOADING", updatedAtMillis = System.currentTimeMillis()))
        db.backgroundJobDao().upsertJob(
            BackgroundJobEntity(
                id = jobId,
                jobType = "UPLOAD",
                title = "YouTube Upload: ${item.videoTitle}",
                projectId = item.projectId,
                status = "RUNNING",
                progressPercent = 5,
                detailMessage = "Uploading to YouTube"
            )
        )

        val ytRepo = YouTubeRepository(applicationContext, SecureKeyStore(applicationContext))
        val videoFile = File(item.videoFilePath)
        val thumbFile = item.thumbnailPath.takeIf { it.isNotBlank() }?.let(::File)

        val res = ytRepo.uploadVideo(
            videoFile = videoFile,
            thumbnailFile = thumbFile,
            title = item.videoTitle,
            description = item.description,
            tags = item.tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
            categoryId = item.categoryId,
            visibility = item.visibility,
            onProgress = { pct ->
                setProgressAsync(workDataOf("progress" to pct))
            }
        )

        return res.fold(
            onSuccess = { videoId ->
                PreferencesRepository(applicationContext).addYouTubeQuotaUnits(1600)
                dao.upsertUpload(
                    item.copy(
                        status = "COMPLETED",
                        progressPercent = 100,
                        youtubeVideoId = videoId,
                        errorMessage = null,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                db.backgroundJobDao().upsertJob(
                    BackgroundJobEntity(
                        id = jobId,
                        jobType = "UPLOAD",
                        title = "YouTube Upload: ${item.videoTitle}",
                        projectId = item.projectId,
                        status = "COMPLETED",
                        progressPercent = 100,
                        detailMessage = "Video ID: $videoId"
                    )
                )
                Result.success()
            },
            onFailure = { err ->
                dao.upsertUpload(
                    item.copy(
                        status = "FAILED",
                        errorMessage = err.localizedMessage ?: "Upload failed",
                        updatedAtMillis = System.currentTimeMillis()
                    )
                )
                db.backgroundJobDao().upsertJob(
                    BackgroundJobEntity(
                        id = jobId,
                        jobType = "UPLOAD",
                        title = "YouTube Upload: ${item.videoTitle}",
                        projectId = item.projectId,
                        status = "FAILED",
                        progressPercent = 0,
                        detailMessage = err.localizedMessage ?: "Upload failed"
                    )
                )
                Result.failure()
            }
        )
    }
}
