package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.data.local.BrandKitEntity
import com.example.data.local.SceneEntity
import com.example.data.local.SubtitleEntity
import com.example.data.local.TimelineClipEntity
import com.example.data.local.VideoProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.math.sin

enum class ExportResolution(val label: String, val shortEdge: Int) {
    HD_720("720p", 720),
    FHD_1080("1080p", 1080),
    UHD_4K("4K", 2160)
}

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.HD_720,
    val fps: Int = 30,
    val bitrateBps: Int = 6_000_000,
    val batterySaverMode: Boolean = false,
    val includeSubtitles: Boolean = true,
    val includeBrandWatermark: Boolean = true
)

@UnstableApi
class VideoRenderEngine(private val context: Context) {

    private var activeTransformer: Transformer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun is4kSupported(): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            list.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, true) } &&
                    runCatching {
                        val caps = info.getCapabilitiesForType(MimeTypes.VIDEO_H264).videoCapabilities
                        caps != null && caps.isSizeSupported(3840, 2160)
                    }.getOrDefault(false)
            }
        }.getOrDefault(false)
    }

    fun cancelExport() {
        mainHandler.post {
            runCatching { activeTransformer?.cancel() }
            activeTransformer = null
        }
    }

    suspend fun exportProjectMp4(
        project: VideoProjectEntity,
        scenes: List<SceneEntity>,
        clips: List<TimelineClipEntity>,
        subtitles: List<SubtitleEntity>,
        brandKit: BrandKitEntity?,
        settings: ExportSettings,
        onProgress: (Int, String) -> Unit
    ): Result<File> {
        val effectiveRes = if (settings.batterySaverMode) ExportResolution.HD_720 else settings.resolution
        val effectiveFps = if (settings.batterySaverMode) 24 else settings.fps.coerceIn(24, 60)
        val effectiveBitrate = if (settings.batterySaverMode) 3_000_000 else settings.bitrateBps.coerceIn(1_500_000, 35_000_000)

        val (outWidth, outHeight) = computeTargetDimensions(
            project.aspectRatio,
            project.customWidth,
            project.customHeight,
            effectiveRes.shortEdge
        )

        onProgress(5, "Preparing scenes & audio mix...")
        val renderCardsDir = File(context.cacheDir, "render_cards").apply { mkdirs() }
        val orderedScenes = scenes.sortedBy { it.orderIndex }
        if (orderedScenes.isEmpty()) {
            return Result.failure(IllegalStateException("Add at least one scene before exporting."))
        }

        val voiceSegments = mutableListOf<Pair<Long, File>>()
        var cursorMs = 0L
        val editedVideoItems = mutableListOf<EditedMediaItem>()

        withContext(Dispatchers.IO) {
            for ((index, scene) in orderedScenes.withIndex()) {
                val durationMs = scene.durationMs.coerceIn(1500L, 120_000L)
                if (scene.voiceoverPath.isNotBlank()) {
                    val voFile = File(scene.voiceoverPath)
                    if (voFile.exists()) voiceSegments.add(cursorMs to voFile)
                }

                val sceneOverlaySub = if (settings.includeSubtitles) {
                    subtitles.firstOrNull {
                        it.startTimeMs < cursorMs + durationMs && it.endTimeMs > cursorMs
                    }?.text ?: ""
                } else ""

                val overlayText = clips.firstOrNull {
                    it.trackType == "TEXT_OVERLAY" && it.startTimeMs < cursorMs + durationMs && (it.startTimeMs + it.durationMs) > cursorMs
                }?.overlayText.orEmpty()

                val watermark = if (settings.includeBrandWatermark && project.applyBrandKit && brandKit?.watermarkEnabled == true) {
                    brandKit.watermarkText
                } else ""

                val sceneEffects = buildSceneEffects(
                    scene = scene,
                    outWidth = outWidth,
                    outHeight = outHeight,
                    durationMs = durationMs,
                    subtitleText = sceneOverlaySub,
                    overlayText = overlayText,
                    watermarkText = watermark,
                    brandPrimaryHex = brandKit?.primaryColorHex ?: "#22D3EE"
                )

                val mediaItem = buildMediaItemForScene(
                    scene = scene,
                    durationMs = durationMs,
                    outWidth = outWidth,
                    outHeight = outHeight,
                    cardsDir = renderCardsDir,
                    cardIndex = index,
                    brandPrimaryHex = brandKit?.primaryColorHex ?: "#22D3EE"
                )

                val builder = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(true)
                    .setEffects(androidx.media3.transformer.Effects(emptyList(), sceneEffects))
                if (scene.mediaType != "VIDEO" && scene.mediaType != "AI_VIDEO") {
                    builder.setFrameRate(effectiveFps)
                }
                editedVideoItems.add(builder.build())
                cursorMs += durationMs
            }
        }

        val totalDurationMs = cursorMs.coerceAtLeast(2000L)
        val musicFile = if (project.musicVolume > 0.01f && project.selectedMusicTrackId != "NONE") {
            AudioMixerAndBeatDetector.ensureMusicTrackWav(context, project.selectedMusicTrackId, 16_000L)
        } else null

        val mixedAudioFile = File(renderCardsDir, "mixed_audio_${project.id}.wav")
        val mixRes = AudioMixerAndBeatDetector.mixAudioTracksToWav(
            context = context,
            voiceoverFiles = voiceSegments,
            musicFile = musicFile,
            musicVolume = project.musicVolume,
            voiceVolume = project.voiceVolume,
            duckingEnabled = project.audioDuckingEnabled,
            totalDurationMs = totalDurationMs,
            outputFile = mixedAudioFile
        )

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeName = project.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(36).ifBlank { "video" }
        val outputFile = File(exportsDir, "${safeName}_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val videoSequence = EditedMediaItemSequence.Builder(editedVideoItems).build()
        val sequences = mutableListOf(videoSequence)
        if (mixRes.isSuccess && mixedAudioFile.exists() && mixedAudioFile.length() > 44L) {
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(mixedAudioFile))).build()
            sequences.add(EditedMediaItemSequence.Builder(listOf(audioItem)).build())
        }
        val composition = Composition.Builder(sequences).build()

        onProgress(15, "Encoding MP4 (${outWidth}x${outHeight} @ ${effectiveFps}fps)...")

        val transformResult = runTransformerOnMain(
            composition = composition,
            outputPath = outputFile.absolutePath,
            bitrateBps = effectiveBitrate,
            onProgress = { pct ->
                val scaled = 15 + (pct * 0.80f).toInt()
                onProgress(scaled.coerceIn(15, 95), "Rendering video ($pct%)...")
            }
        )

        return transformResult.mapCatching {
            if (!outputFile.exists() || outputFile.length() < 1024L) {
                throw IllegalStateException("Rendered MP4 file was empty.")
            }
            onProgress(98, "Saving to Movies/AI Video Studio...")
            copyToMediaStoreMovies(outputFile)
            onProgress(100, "Export complete")
            outputFile
        }
    }

    private suspend fun runTransformerOnMain(
        composition: Composition,
        outputPath: String,
        bitrateBps: Int,
        onProgress: (Int) -> Unit
    ): Result<Unit> = suspendCancellableCoroutine { cont ->
        mainHandler.post {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(bitrateBps)
                            .build()
                    )
                    .build()

                var finished = false
                val transformer = Transformer.Builder(context.applicationContext)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (!finished) {
                                finished = true
                                activeTransformer = null
                                if (cont.isActive) cont.resume(Result.success(Unit))
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (!finished) {
                                finished = true
                                activeTransformer = null
                                if (cont.isActive) cont.resume(Result.failure(exportException))
                            }
                        }
                    })
                    .build()

                activeTransformer = transformer
                transformer.start(composition, outputPath)

                val holder = ProgressHolder()
                val poller = object : Runnable {
                    override fun run() {
                        if (finished) return
                        val state = runCatching { transformer.getProgress(holder) }
                            .getOrDefault(Transformer.PROGRESS_STATE_NOT_STARTED)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress)
                        }
                        if (!finished && state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                            mainHandler.postDelayed(this, 350L)
                        }
                    }
                }
                mainHandler.postDelayed(poller, 350L)

                cont.invokeOnCancellation {
                    finished = true
                    mainHandler.post {
                        runCatching { transformer.cancel() }
                        activeTransformer = null
                    }
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Result.failure(e))
            }
        }
    }

    private fun buildMediaItemForScene(
        scene: SceneEntity,
        durationMs: Long,
        outWidth: Int,
        outHeight: Int,
        cardsDir: File,
        cardIndex: Int,
        brandPrimaryHex: String
    ): MediaItem {
        val isVideo = scene.mediaType == "VIDEO" || scene.mediaType == "AI_VIDEO"
        val hasLocalFile = scene.mediaPath.isNotBlank() &&
            (scene.mediaPath.startsWith("content://") || File(scene.mediaPath).exists())

        if (isVideo && hasLocalFile) {
            val uri = if (scene.mediaPath.startsWith("content://")) {
                Uri.parse(scene.mediaPath)
            } else {
                Uri.fromFile(File(scene.mediaPath))
            }
            val startMs = scene.trimStartMs.coerceAtLeast(0L)
            val endMs = (startMs + durationMs).coerceAtLeast(startMs + 1000L)
            return MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        }

        val imageUri = if (!isVideo && hasLocalFile) {
            if (scene.mediaPath.startsWith("content://")) Uri.parse(scene.mediaPath)
            else Uri.fromFile(File(scene.mediaPath))
        } else {
            val cardFile = File(cardsDir, "scene_card_${scene.projectId}_$cardIndex.png")
            renderSceneFallbackCard(cardFile, scene, outWidth, outHeight, brandPrimaryHex)
            Uri.fromFile(cardFile)
        }

        return MediaItem.Builder()
            .setUri(imageUri)
            .setImageDurationMs(durationMs)
            .build()
    }

    private fun buildSceneEffects(
        scene: SceneEntity,
        outWidth: Int,
        outHeight: Int,
        durationMs: Long,
        subtitleText: String,
        overlayText: String,
        watermarkText: String,
        brandPrimaryHex: String
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // 1. Scale / Flip / Rotation
        val flipX = if (scene.flipHorizontal) -1f else 1f
        if (scene.rotationDegrees != 0f || scene.flipHorizontal || scene.cropScale != 1f) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(scene.rotationDegrees)
                    .setScale(flipX * scene.cropScale, scene.cropScale)
                    .build()
            )
        }

        // 2. Keyframe / Motion Effects (Ken Burns zoom, pan, shake)
        val durUs = (durationMs * 1000f).coerceAtLeast(1_000_000f)
        when (scene.motionEffect) {
            "KEN_BURNS_ZOOM_IN" -> {
                val startS = scene.keyframeStartScale.coerceIn(1.0f, 1.3f)
                val endS = scene.keyframeEndScale.coerceIn(1.05f, 1.4f)
                effects.add(MatrixTransformation { timeUs ->
                    val t = (timeUs / durUs).coerceIn(0f, 1f)
                    val s = startS + (endS - startS) * t
                    Matrix().apply { postScale(s, s) }
                })
            }
            "KEN_BURNS_ZOOM_OUT" -> {
                effects.add(MatrixTransformation { timeUs ->
                    val t = (timeUs / durUs).coerceIn(0f, 1f)
                    val s = 1.18f - 0.18f * t
                    Matrix().apply { postScale(s, s) }
                })
            }
            "PAN_LEFT" -> {
                effects.add(MatrixTransformation { timeUs ->
                    val t = (timeUs / durUs).coerceIn(0f, 1f)
                    Matrix().apply {
                        postScale(1.15f, 1.15f)
                        postTranslate(0.06f - 0.12f * t, 0f)
                    }
                })
            }
            "PAN_RIGHT" -> {
                effects.add(MatrixTransformation { timeUs ->
                    val t = (timeUs / durUs).coerceIn(0f, 1f)
                    Matrix().apply {
                        postScale(1.15f, 1.15f)
                        postTranslate(-0.06f + 0.12f * t, 0f)
                    }
                })
            }
            "SHAKE" -> {
                effects.add(MatrixTransformation { timeUs ->
                    val sec = timeUs / 1_000_000f
                    val dx = sin(sec * 28f) * 0.015f
                    val dy = sin(sec * 35f) * 0.012f
                    Matrix().apply {
                        postScale(1.06f, 1.06f)
                        postTranslate(dx, dy)
                    }
                })
            }
        }

        // 3. Color Grading / Filters / Brightness / Contrast
        when (scene.colorFilter) {
            "NOIR" -> effects.add(RgbFilter.createGrayscaleFilter())
            "WARM" -> effects.add(Contrast(0.12f))
            "CINEMATIC" -> effects.add(Contrast(0.22f))
            "VIVID" -> effects.add(Contrast(0.30f))
        }
        if (scene.brightness != 0f) {
            effects.add(Brightness(scene.brightness.coerceIn(-0.8f, 0.8f)))
        }
        if (scene.contrast != 1f) {
            effects.add(Contrast((scene.contrast - 1f).coerceIn(-0.8f, 0.8f)))
        }

        // 4. Target Presentation Size & Aspect Ratio
        effects.add(
            Presentation.createForWidthAndHeight(
                outWidth,
                outHeight,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        // 5. Burned-in Subtitles, Text Overlay, and Brand Watermark via BitmapOverlay
        if (subtitleText.isNotBlank() || overlayText.isNotBlank() || watermarkText.isNotBlank()) {
            val overlayBmp = createTextOverlayBitmap(
                width = outWidth,
                height = outHeight,
                subtitleText = subtitleText,
                overlayText = overlayText,
                watermarkText = watermarkText,
                accentHex = brandPrimaryHex
            )
            effects.add(
                OverlayEffect(
                    listOf(BitmapOverlay.createStaticBitmapOverlay(overlayBmp))
                )
            )
        }

        return effects
    }

    private fun renderSceneFallbackCard(
        outFile: File,
        scene: SceneEntity,
        w: Int,
        h: Int,
        brandPrimaryHex: String
    ) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val accent = runCatching { Color.parseColor(brandPrimaryHex) }.getOrDefault(Color.CYAN)
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(Color.rgb(11, 15, 26), Color.rgb(20, 30, 52), Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (w * 0.055f).coerceIn(28f, 72f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val title = scene.title.ifBlank { "AI Video Studio" }
        val layout = StaticLayout.Builder.obtain(title, 0, title.length, textPaint, (w * 0.82f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
        canvas.save()
        canvas.translate(w * 0.09f, h * 0.42f)
        layout.draw(canvas)
        canvas.restore()

        FileOutputStream(outFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        bmp.recycle()
    }

    private fun createTextOverlayBitmap(
        width: Int,
        height: Int,
        subtitleText: String,
        overlayText: String,
        watermarkText: String,
        accentHex: String
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        if (watermarkText.isNotBlank()) {
            val wmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(170, 255, 255, 255)
                textSize = (width * 0.032f).coerceIn(18f, 40f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(watermarkText, width * 0.05f, height * 0.07f, wmPaint)
        }

        if (overlayText.isNotBlank()) {
            val accent = runCatching { Color.parseColor(accentHex) }.getOrDefault(Color.CYAN)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent))
            }
            val topPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(11, 15, 26)
                textSize = (width * 0.042f).coerceIn(22f, 52f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val layout = StaticLayout.Builder.obtain(overlayText, 0, overlayText.length, topPaint, (width * 0.84f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            val topY = height * 0.14f
            canvas.drawRoundRect(
                RectF(width * 0.06f, topY - 14f, width * 0.94f, topY + layout.height + 14f),
                20f, 20f, pillPaint
            )
            canvas.save()
            canvas.translate(width * 0.08f, topY)
            layout.draw(canvas)
            canvas.restore()
        }

        if (subtitleText.isNotBlank()) {
            val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = (width * 0.045f).coerceIn(24f, 56f)
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val layout = StaticLayout.Builder.obtain(subtitleText, 0, subtitleText.length, subPaint, (width * 0.84f).toInt())
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
            val subY = height * 0.78f - layout.height / 2f
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(185, 10, 14, 24)
            }
            canvas.drawRoundRect(
                RectF(width * 0.05f, subY - 16f, width * 0.95f, subY + layout.height + 16f),
                22f, 22f, boxPaint
            )
            canvas.save()
            canvas.translate(width * 0.08f, subY)
            layout.draw(canvas)
            canvas.restore()
        }

        return bmp
    }

    private fun copyToMediaStoreMovies(mp4File: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, mp4File.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/AI Video Studio")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return
        context.contentResolver.openOutputStream(uri)?.use { out ->
            mp4File.inputStream().use { input -> input.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }

    companion object {
        fun computeTargetDimensions(
            aspectRatio: String,
            customW: Int,
            customH: Int,
            shortEdge: Int
        ): Pair<Int, Int> {
            val base = shortEdge.coerceIn(480, 2160)
            val (w, h) = when (aspectRatio) {
                "16:9" -> ((base * 16) / 9) to base
                "1:1" -> base to base
                "custom" -> {
                    val safeW = customW.coerceIn(360, 3840)
                    val safeH = customH.coerceIn(360, 3840)
                    val scale = base.toFloat() / minOf(safeW, safeH)
                    (safeW * scale).toInt() to (safeH * scale).toInt()
                }
                else -> base to ((base * 16) / 9) // 9:16
            }
            // H.264 encoders require even dimensions
            return (w and -2) to (h and -2)
        }
    }
}
