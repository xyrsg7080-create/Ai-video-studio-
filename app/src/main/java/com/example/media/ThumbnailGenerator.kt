package com.example.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ThumbnailConfig(
    val headline: String,
    val subtitleBadge: String = "HD STUDIO",
    val stickerText: String = "",
    val width: Int = 1280,
    val height: Int = 720,
    val primaryColorHex: String = "#22D3EE",
    val accentColorHex: String = "#FF6B4A",
    val backgroundImagePath: String = "",
    val sourceVideoPath: String = "",
    val frameTimeMs: Long = 1500L,
    val vignetteStrength: Float = 0.72f
)

object ThumbnailGenerator {
    suspend fun renderThumbnailBitmap(
        context: Context,
        config: ThumbnailConfig
    ): Bitmap = withContext(Dispatchers.IO) {
        val w = config.width.coerceIn(480, 1920)
        val h = config.height.coerceIn(480, 1920)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgBitmap = loadSourceBitmap(context, config.backgroundImagePath, config.sourceVideoPath, config.frameTimeMs)
        if (bgBitmap != null) {
            drawCenterCrop(canvas, bgBitmap, w, h)
        } else {
            val c1 = runCatching { Color.parseColor("#0B0F1A") }.getOrDefault(Color.BLACK)
            val c2 = runCatching { Color.parseColor(config.primaryColorHex) }.getOrDefault(Color.CYAN)
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(c1, Color.rgb(18, 28, 48), c2),
                    floatArrayOf(0f, 0.65f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)
        }

        // Cinematic gradient scrim for legible typography
        val scrimPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(
                    Color.argb((80 * config.vignetteStrength).toInt(), 8, 12, 22),
                    Color.argb((220 * config.vignetteStrength).toInt(), 8, 12, 22)
                ),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), scrimPaint)

        // Accent pill badge
        val accentColor = runCatching { Color.parseColor(config.accentColorHex) }.getOrDefault(Color.RED)
        if (config.subtitleBadge.isNotBlank()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor }
            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = h * 0.045f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val textBounds = Rect()
            val badgeLabel = config.subtitleBadge.uppercase()
            badgeTextPaint.getTextBounds(badgeLabel, 0, badgeLabel.length, textBounds)
            val padH = w * 0.025f
            val padV = h * 0.02f
            val rect = RectF(
                w * 0.06f,
                h * 0.10f,
                w * 0.06f + textBounds.width() + padH * 2,
                h * 0.10f + textBounds.height() + padV * 2
            )
            canvas.drawRoundRect(rect, 18f, 18f, badgePaint)
            canvas.drawText(badgeLabel, rect.left + padH, rect.bottom - padV, badgeTextPaint)
        }

        // Main bold headline with stroke + fill
        val words = config.headline.trim().ifBlank { "AI VIDEO STUDIO" }.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            if (current.length + word.length > 14 && current.isNotEmpty()) {
                lines.add(current.toString().trim())
                current = StringBuilder()
            }
            current.append(word).append(" ")
        }
        if (current.isNotEmpty()) lines.add(current.toString().trim())

        val fontSize = (h * 0.125f).coerceIn(36f, 120f)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = fontSize * 0.14f
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = fontSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val startY = h * 0.84f - (lines.size - 1) * fontSize * 1.12f
        lines.take(3).forEachIndexed { idx, line ->
            val y = startY + idx * fontSize * 1.12f
            canvas.drawText(line, w * 0.06f, y, strokePaint)
            canvas.drawText(line, w * 0.06f, y, fillPaint)
        }

        if (config.stickerText.isNotBlank()) {
            val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = h * 0.12f
            }
            canvas.drawText(config.stickerText, w * 0.80f, h * 0.25f, stickerPaint)
        }

        bmp
    }

    suspend fun saveThumbnailToAppAndGallery(
        context: Context,
        bitmap: Bitmap,
        fileNamePrefix: String
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, "thumbnails").apply { mkdirs() }
            val file = File(dir, "${fileNamePrefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AI Video Studio")
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    }
                }
            }
            file
        }
    }

    private fun loadSourceBitmap(
        context: Context,
        imagePath: String,
        videoPath: String,
        frameTimeMs: Long
    ): Bitmap? {
        if (imagePath.isNotBlank()) {
            runCatching {
                if (imagePath.startsWith("content://")) {
                    context.contentResolver.openInputStream(Uri.parse(imagePath))?.use {
                        return BitmapFactory.decodeStream(it)
                    }
                } else {
                    val f = File(imagePath)
                    if (f.exists()) return BitmapFactory.decodeFile(f.absolutePath)
                }
            }
        }
        if (videoPath.isNotBlank()) {
            val retriever = MediaMetadataRetriever()
            return try {
                if (videoPath.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(videoPath))
                } else {
                    retriever.setDataSource(videoPath)
                }
                retriever.getFrameAtTime(frameTimeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
        return null
    }

    private fun drawCenterCrop(canvas: Canvas, src: Bitmap, dstW: Int, dstH: Int) {
        val scale = maxOf(dstW.toFloat() / src.width, dstH.toFloat() / src.height)
        val scaledW = src.width * scale
        val scaledH = src.height * scale
        val left = (dstW - scaledW) / 2f
        val top = (dstH - scaledH) / 2f
        canvas.drawBitmap(src, null, RectF(left, top, left + scaledW, top + scaledH), null)
    }
}
