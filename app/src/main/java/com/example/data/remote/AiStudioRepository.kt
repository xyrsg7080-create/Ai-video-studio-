package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AspectRatio
import com.google.firebase.ai.type.ImageConfig
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ImageSize
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedScenePlan(
    val title: String,
    val narration: String,
    val visualQuery: String,
    val durationSec: Int,
    val transition: String,
    val motionEffect: String,
    val suggestedSfx: String
)

data class GeneratedVideoPlan(
    val title: String,
    val hook: String,
    val body: String,
    val cta: String,
    val scenes: List<GeneratedScenePlan>,
    val factCheckNotes: String,
    val needsVerification: Boolean,
    val researchSources: List<String>,
    val seoTitle: String,
    val seoDescription: String,
    val seoTags: List<String>,
    val seoHashtags: List<String>,
    val thumbnailHeadline: String,
    val chapters: List<String>
)

data class FactCheckResult(
    val verifiedSummary: String,
    val flaggedClaims: List<String>,
    val sources: List<String>,
    val needsVerification: Boolean
)

data class SeoBundle(
    val title: String,
    val description: String,
    val tags: List<String>,
    val hashtags: List<String>,
    val thumbnailText: String,
    val chapters: List<String>
)

class AiStudioRepository {
    private val generalModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    private val fastModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.5-flash-lite")
    }

    private val deepReasoningModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-4-argon")
    }

    private val structuredPlanModel by lazy {
        val sceneSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Short scene title"),
                "narration" to Schema.string(description = "Spoken voiceover narration for this scene"),
                "visualQuery" to Schema.string(description = "Clean English stock media search query"),
                "durationSec" to Schema.integer(description = "Duration of scene in seconds"),
                "transition" to Schema.enumeration(
                    values = listOf("CROSSFADE", "SLIDE_LEFT", "ZOOM_IN", "NONE"),
                    description = "Transition entering this scene"
                ),
                "motionEffect" to Schema.enumeration(
                    values = listOf("KEN_BURNS_ZOOM_IN", "KEN_BURNS_ZOOM_OUT", "PAN_LEFT", "PAN_RIGHT", "NONE"),
                    description = "Camera motion effect"
                ),
                "suggestedSfx" to Schema.enumeration(
                    values = listOf("NONE", "WHOOSH", "POP", "RISER", "CHIME"),
                    description = "Optional sound effect"
                )
            )
        )
        val planSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Compelling video title"),
                "hook" to Schema.string(description = "Opening 3-second hook"),
                "body" to Schema.string(description = "Full narration script body"),
                "cta" to Schema.string(description = "Closing call to action"),
                "scenes" to Schema.array(items = sceneSchema, description = "Ordered storyboard scenes"),
                "factCheckNotes" to Schema.string(description = "Honest notes flagging claims needing user verification"),
                "needsVerification" to Schema.boolean(description = "True if any statistic, date, or news claim should be verified"),
                "researchSources" to Schema.array(items = Schema.string(), description = "Suggested authoritative source types or references without fabricating URLs"),
                "seoTitle" to Schema.string(description = "YouTube SEO title under 100 chars"),
                "seoDescription" to Schema.string(description = "YouTube description without fake guarantees"),
                "seoTags" to Schema.array(items = Schema.string()),
                "seoHashtags" to Schema.array(items = Schema.string()),
                "thumbnailHeadline" to Schema.string(description = "2-4 punchy words for thumbnail"),
                "chapters" to Schema.array(items = Schema.string(), description = "Timestamp chapter suggestions for long videos")
            )
        )
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = planSchema
            }
        )
    }

    private fun imageModelForAspectRatio(aspectRatio: String) = Firebase.ai.generativeModel(
        modelName = "gemini-nano-banana-2.1",
        generationConfig = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
            imageConfig = ImageConfig.builder()
                .setAspectRatio(
                    when (aspectRatio) {
                        "9:16" -> AspectRatio.PORTRAIT_9x16
                        "16:9" -> AspectRatio.LANDSCAPE_16x9
                        else -> AspectRatio.SQUARE_1x1
                    }
                )
                .setImageSize(ImageSize.SIZE_1K)
                .build()
        }
    )

    suspend fun generateFullVideoPlan(
        topic: String,
        category: String,
        aspectRatio: String,
        targetDurationSec: Int,
        languageCode: String
    ): Result<GeneratedVideoPlan> {
        val langName = if (languageCode == "hi") "Hindi (Devanagari script for narration)" else "English"
        val sceneCount = when {
            targetDurationSec <= 45 -> 4
            targetDurationSec <= 90 -> 6
            targetDurationSec <= 180 -> 8
            else -> 10
        }
        val prompt = """
            You are an ethical, accurate video production assistant.
            Create a complete video production plan for:
            - Topic: "$topic"
            - Content Style: "$category"
            - Aspect Ratio: "$aspectRatio"
            - Target Total Duration: $targetDurationSec seconds
            - Output Language for title, hook, body, cta, narration, and SEO: $langName
            - visualQuery MUST always be in clean, descriptive English keywords suitable for Wikimedia Commons / Pexels / Pixabay search.
            - Create exactly $sceneCount scenes whose durationSec values sum closely to $targetDurationSec seconds.
            - NEVER fabricate statistics, dates, or fake URLs. If any claim requires verification, set needsVerification=true and explain clearly in factCheckNotes.
            - Do NOT promise views, viral results, or guaranteed rankings in SEO copy.
        """.trimIndent()

        return try {
            val response = withTimeout(60_000L) {
                structuredPlanModel.generateContent(prompt)
            }
            val raw = response.text ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))
            val json = JSONObject(raw)
            val scenesArr = json.getJSONArray("scenes")
            val scenes = (0 until scenesArr.length()).map { i ->
                val s = scenesArr.getJSONObject(i)
                GeneratedScenePlan(
                    title = s.optString("title", "Scene ${i + 1}"),
                    narration = s.optString("narration", ""),
                    visualQuery = s.optString("visualQuery", topic),
                    durationSec = s.optInt("durationSec", (targetDurationSec / sceneCount).coerceAtLeast(3)),
                    transition = s.optString("transition", "CROSSFADE"),
                    motionEffect = s.optString("motionEffect", "KEN_BURNS_ZOOM_IN"),
                    suggestedSfx = s.optString("suggestedSfx", "NONE")
                )
            }
            val sources = json.optJSONArray("researchSources")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val tags = json.optJSONArray("seoTags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val hashtags = json.optJSONArray("seoHashtags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val chapters = json.optJSONArray("chapters")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()

            Result.success(
                GeneratedVideoPlan(
                    title = json.optString("title", topic.take(80)),
                    hook = json.optString("hook", ""),
                    body = json.optString("body", ""),
                    cta = json.optString("cta", ""),
                    scenes = scenes,
                    factCheckNotes = json.optString("factCheckNotes", ""),
                    needsVerification = json.optBoolean("needsVerification", true),
                    researchSources = sources,
                    seoTitle = json.optString("seoTitle", topic.take(90)),
                    seoDescription = json.optString("seoDescription", ""),
                    seoTags = tags,
                    seoHashtags = hashtags,
                    thumbnailHeadline = json.optString("thumbnailHeadline", "WATCH THIS"),
                    chapters = chapters
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Full video plan generation timed out", e)
            Result.failure(Exception("AI generation timed out after 60 seconds. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Full video plan generation failed", e)
            Result.failure(e)
        }
    }

    fun streamScriptDraft(
        topic: String,
        category: String,
        languageCode: String
    ): Flow<String> = flow {
        val lang = if (languageCode == "hi") "Hindi" else "English"
        val prompt = "Write an engaging $category video script in $lang about '$topic' with a 3-second hook, clear scenes, and a concise call to action. Flag any factual claims that need verification."
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            generalModel.generateContentStream(prompt).collect { chunk ->
                val text = chunk.text.orEmpty()
                if (text.isNotEmpty()) {
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        }
    }

    suspend fun factCheckScript(
        scriptText: String,
        languageCode: String,
        useDeepReasoning: Boolean = false
    ): Result<FactCheckResult> {
        val schema = Schema.obj(
            properties = mapOf(
                "verifiedSummary" to Schema.string(description = "Overall accuracy review"),
                "flaggedClaims" to Schema.array(items = Schema.string(), description = "Specific claims, dates, or stats that need verification or may be outdated"),
                "sources" to Schema.array(items = Schema.string(), description = "Recommended primary sources to verify against"),
                "needsVerification" to Schema.boolean()
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = if (useDeepReasoning) "gemini-4-argon" else "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val lang = if (languageCode == "hi") "Hindi" else "English"
        val prompt = """
            Fact-check and audit the following video script (respond in $lang).
            Identify uncertain claims, statistics, or potentially outdated news.
            Do NOT invent facts or fake references.
            Script:
            $scriptText
        """.trimIndent()
        return try {
            val response = withTimeout(60_000L) { model.generateContent(prompt) }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty fact-check response"))
            val claims = json.optJSONArray("flaggedClaims")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val sources = json.optJSONArray("sources")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            Result.success(
                FactCheckResult(
                    verifiedSummary = json.optString("verifiedSummary", ""),
                    flaggedClaims = claims,
                    sources = sources,
                    needsVerification = json.optBoolean("needsVerification", claims.isNotEmpty())
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Fact-check timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fact-check failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateYouTubeSeo(
        topic: String,
        scriptText: String,
        durationSec: Int,
        languageCode: String
    ): Result<SeoBundle> {
        val schema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(),
                "description" to Schema.string(),
                "tags" to Schema.array(items = Schema.string()),
                "hashtags" to Schema.array(items = Schema.string()),
                "thumbnailText" to Schema.string(),
                "chapters" to Schema.array(items = Schema.string())
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val lang = if (languageCode == "hi") "Hindi" else "English"
        val prompt = """
            Generate realistic YouTube SEO metadata in $lang for a ${durationSec}s video about "$topic".
            Do not claim guaranteed views, rankings, or viral performance.
            Include timestamp chapter suggestions if duration >= 90s.
            Script excerpt: ${scriptText.take(1500)}
        """.trimIndent()
        return try {
            val response = withTimeout(45_000L) { model.generateContent(prompt) }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty SEO response"))
            Result.success(
                SeoBundle(
                    title = json.optString("title", topic.take(90)),
                    description = json.optString("description", ""),
                    tags = json.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                    hashtags = json.optJSONArray("hashtags")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
                    thumbnailText = json.optString("thumbnailText", "WATCH NOW"),
                    chapters = json.optJSONArray("chapters")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("SEO generation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "SEO generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun translateLines(lines: List<String>, targetLanguageCode: String): Result<List<String>> {
        if (lines.isEmpty()) return Result.success(emptyList())
        val schema = Schema.obj(
            properties = mapOf(
                "translatedLines" to Schema.array(items = Schema.string())
            )
        )
        val model = Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = schema
            }
        )
        val targetLang = if (targetLanguageCode == "hi") "Hindi (Devanagari)" else "English"
        val prompt = """
            Translate each line accurately into $targetLang, keeping the exact same count (${lines.size} items) and concise spoken duration so video timing stays aligned.
            Lines:
            ${lines.mapIndexed { idx, s -> "${idx + 1}. $s" }.joinToString("\n")}
        """.trimIndent()
        return try {
            val response = withTimeout(45_000L) { model.generateContent(prompt) }
            val json = JSONObject(response.text ?: throw IllegalStateException("Empty translation"))
            val arr = json.getJSONArray("translatedLines")
            Result.success((0 until arr.length()).map { arr.getString(it) })
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Translation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateAiImage(prompt: String, aspectRatio: String = "9:16"): Result<Bitmap> {
        return try {
            val model = imageModelForAspectRatio(aspectRatio)
            val response = withTimeout(60_000L) {
                model.generateContent(prompt)
            }
            val bitmap = response.candidates.firstOrNull()
                ?.content?.parts?.filterIsInstance<ImagePart>()
                ?.firstOrNull()?.image
            if (bitmap != null) {
                Result.success(bitmap)
            } else {
                Result.failure(IllegalStateException("Image model returned no image."))
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI image generation timed out after 60 seconds."))
        } catch (e: QuotaExceededException) {
            Result.failure(Exception("Image generation quota exceeded or Blaze billing is required."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI image generation failed", e)
            Result.failure(e)
        }
    }

    suspend fun analyzeVideoFrames(
        context: Context,
        videoUri: Uri,
        userPrompt: String
    ): Result<String> {
        return try {
            val frames = withContext(Dispatchers.IO) {
                extractKeyFrames(context, videoUri, maxFrames = 4)
            }
            if (frames.isEmpty()) {
                return Result.failure(IllegalStateException("Could not decode frames from selected video."))
            }
            val response = withTimeout(60_000L) {
                generalModel.generateContent(
                    content {
                        text(
                            "Analyze these sampled frames from the video. " +
                                "Summarize key moments, visual pacing, suggested Shorts cut timestamps, and caption ideas. " +
                                "User focus: $userPrompt"
                        )
                        frames.forEach { frame -> image(frame) }
                    }
                )
            }
            val text = response.text
            if (!text.isNullOrBlank()) Result.success(text)
            else Result.failure(IllegalStateException("Model returned empty video analysis."))
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Video analysis timed out after 60s."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Video analysis failed", e)
            Result.failure(e)
        }
    }

    private fun extractKeyFrames(context: Context, uri: Uri, maxFrames: Int): List<Bitmap> {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file" || uri.scheme == null) {
                retriever.setDataSource(uri.path ?: uri.toString())
            } else {
                retriever.setDataSource(context, uri)
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 4000L
            val stepUs = ((durationMs.coerceAtLeast(1000L) * 1000L) / (maxFrames + 1)).coerceAtLeast(250_000L)
            (1..maxFrames).mapNotNull { idx ->
                val raw = retriever.getFrameAtTime(idx * stepUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                raw?.let { bmp ->
                    val maxDim = 512
                    if (bmp.width > maxDim || bmp.height > maxDim) {
                        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * scale).toInt().coerceAtLeast(1),
                            (bmp.height * scale).toInt().coerceAtLeast(1),
                            true
                        )
                    } else bmp
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed extracting frames for video analysis", e)
            emptyList()
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "AiStudioRepository"
    }
}
