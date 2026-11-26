package com.example.data.remote

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.core.security.SecureKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Real client for optional user-supplied Gemini API key video generation via
 * the Gemini Interactions API (`gemini-omni-1.2-flash`), supporting 16:9 and 9:16.
 * Never fakes or simulates video generation if no key is provided or if the API fails.
 */
class InteractionsVideoClient(
    private val context: Context,
    private val keyStore: SecureKeyStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateVideoClip(
        prompt: String,
        aspectRatio: String,
        referenceImageFile: File? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val apiKey = keyStore.getSecret(SecureKeyStore.KEY_CUSTOM_GEMINI_API)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Optional AI text/image-to-video requires your own Gemini API key with Interactions video quota in Settings. Use free stock video or AI image + motion effects instead."
                )
            )
        }
        val normalizedAspect = if (aspectRatio == "16:9") "16:9" else "9:16"
        try {
            val inputsArray = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", prompt))
                if (referenceImageFile != null && referenceImageFile.exists()) {
                    val bytes = referenceImageFile.readBytes()
                    if (bytes.size <= 10 * 1024 * 1024) {
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        put(
                            JSONObject()
                                .put("type", "image")
                                .put("mime_type", "image/jpeg")
                                .put("data", b64)
                        )
                    }
                }
            }

            val bodyJson = JSONObject().apply {
                put("model", "gemini-omni-1.2-flash")
                put("input", inputsArray)
                put("response_modalities", JSONArray().put("video"))
                put(
                    "generation_config",
                    JSONObject().put("aspect_ratio", normalizedAspect)
                )
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/interactions")
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val msg = runCatching {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                return@withContext Result.failure(
                    IllegalStateException("Interactions video API unavailable ($msg). Use free stock footage or motion-animated images.")
                )
            }

            var json = JSONObject(responseBody)
            val interactionId = json.optString("id", "")
            var status = json.optString("status", "")
            var attempts = 0
            while ((status == "in_progress" || status == "requires_action") && interactionId.isNotBlank() && attempts < 24) {
                delay(5_000L)
                attempts++
                val pollReq = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/interactions/$interactionId")
                    .addHeader("x-goog-api-key", apiKey)
                    .get()
                    .build()
                val pollResp = httpClient.newCall(pollReq).execute()
                val pollStr = pollResp.body?.string().orEmpty()
                if (!pollResp.isSuccessful) break
                json = JSONObject(pollStr)
                status = json.optString("status", "")
            }

            val outputs = json.optJSONArray("outputs")
            var videoBytes: ByteArray? = null
            var downloadUri: String? = null
            if (outputs != null) {
                for (i in 0 until outputs.length()) {
                    val item = outputs.getJSONObject(i)
                    if (item.optString("type") == "video" || item.has("video")) {
                        val b64 = item.optString("data", "").ifBlank {
                            item.optJSONObject("video")?.optString("data", "").orEmpty()
                        }
                        if (b64.isNotBlank()) {
                            videoBytes = Base64.decode(b64, Base64.DEFAULT)
                            break
                        }
                        val uri = item.optString("uri", "").ifBlank {
                            item.optJSONObject("video")?.optString("uri", "").orEmpty()
                        }
                        if (uri.isNotBlank()) {
                            downloadUri = uri
                            break
                        }
                    }
                }
            }

            val outDir = File(context.filesDir, "ai_video_clips").apply { mkdirs() }
            val outFile = File(outDir, "ai_clip_${System.currentTimeMillis()}.mp4")
            when {
                videoBytes != null && videoBytes.isNotEmpty() -> {
                    outFile.writeBytes(videoBytes)
                    Result.success(outFile)
                }
                !downloadUri.isNullOrBlank() -> {
                    val dlReq = Request.Builder()
                        .url(downloadUri)
                        .addHeader("x-goog-api-key", apiKey)
                        .get()
                        .build()
                    val dlResp = httpClient.newCall(dlReq).execute()
                    val bytes = dlResp.body?.bytes()
                    if (dlResp.isSuccessful && bytes != null && bytes.isNotEmpty()) {
                        outFile.writeBytes(bytes)
                        Result.success(outFile)
                    } else {
                        Result.failure(IllegalStateException("Video output download failed (HTTP ${dlResp.code})."))
                    }
                }
                else -> {
                    Result.failure(IllegalStateException("Interactions API returned status '$status' with no video payload."))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Interactions video generation failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "InteractionsVideoClient"
    }
}
