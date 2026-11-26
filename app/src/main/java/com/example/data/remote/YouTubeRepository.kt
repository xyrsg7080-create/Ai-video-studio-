package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.example.core.security.SecureKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class YouTubeChannelInfo(
    val channelId: String,
    val title: String,
    val subscriberCount: String
)

class YouTubeRepository(
    private val context: Context,
    private val keyStore: SecureKeyStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val authPrefs = context.applicationContext.getSharedPreferences(
        "yt_oauth_pkce_state",
        Context.MODE_PRIVATE
    )

    val redirectUri = "com.aistudio.aivideostudio.kqzxpm:/oauth2redirect"

    fun hasConfiguredClientId(): Boolean =
        keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID).isNotBlank()

    fun isConnected(): Boolean =
        keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_ACCESS_TOKEN).isNotBlank()

    fun launchOAuthSignIn(): Result<Unit> {
        val clientId = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID)
        if (clientId.isBlank()) {
            return Result.failure(
                IllegalStateException("Enter your Google OAuth 2.0 Android/Installed Client ID in Settings or YouTube Studio first.")
            )
        }
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateCodeVerifier().take(24)

        authPrefs.edit()
            .putString("pkce_verifier", verifier)
            .putString("oauth_state", state)
            .apply()

        val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter(
                "scope",
                "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly"
            )
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .appendQueryParameter("state", state)
            .build()

        return try {
            val customTabs = CustomTabsIntent.Builder().build()
            customTabs.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            customTabs.launchUrl(context, authUri)
            Result.success(Unit)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, authUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                Result.success(Unit)
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }

    suspend fun handleOAuthRedirect(uri: Uri): Result<YouTubeChannelInfo> = withContext(Dispatchers.IO) {
        val errorParam = uri.getQueryParameter("error")
        if (!errorParam.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("YouTube authorization denied: $errorParam"))
        }
        val code = uri.getQueryParameter("code")
            ?: return@withContext Result.failure(IllegalStateException("Missing OAuth authorization code."))
        val expectedState = authPrefs.getString("oauth_state", null)
        val actualState = uri.getQueryParameter("state")
        if (expectedState != null && actualState != expectedState) {
            return@withContext Result.failure(IllegalStateException("OAuth state verification failed."))
        }
        val verifier = authPrefs.getString("pkce_verifier", null)
            ?: return@withContext Result.failure(IllegalStateException("Missing PKCE verifier."))
        val clientId = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID)
        if (clientId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Missing YouTube OAuth Client ID."))
        }

        try {
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("code", code)
                .add("code_verifier", verifier)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", redirectUri)
                .build()

            val tokenReq = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()

            val resp = httpClient.newCall(tokenReq).execute()
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Token exchange failed: $bodyStr"))
            }
            val json = JSONObject(bodyStr)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token", "")
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiryMs = System.currentTimeMillis() + (expiresIn - 60L) * 1000L

            keyStore.putSecret(SecureKeyStore.KEY_YOUTUBE_ACCESS_TOKEN, accessToken)
            if (refreshToken.isNotBlank()) {
                keyStore.putSecret(SecureKeyStore.KEY_YOUTUBE_REFRESH_TOKEN, refreshToken)
            }
            keyStore.putSecret(SecureKeyStore.KEY_YOUTUBE_TOKEN_EXPIRY, expiryMs.toString())

            fetchConnectedChannel()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "OAuth redirect exchange failed", e)
            Result.failure(e)
        }
    }

    suspend fun getValidAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val currentToken = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_ACCESS_TOKEN)
        val expiryMs = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_TOKEN_EXPIRY).toLongOrNull() ?: 0L
        if (currentToken.isNotBlank() && System.currentTimeMillis() < expiryMs) {
            return@withContext Result.success(currentToken)
        }
        val refreshToken = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_REFRESH_TOKEN)
        val clientId = keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID)
        if (refreshToken.isBlank() || clientId.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("YouTube session expired or not connected. Please authorize your channel.")
            )
        }
        try {
            val form = FormBody.Builder()
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form)
                .build()
            val resp = httpClient.newCall(req).execute()
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("Token refresh failed: HTTP ${resp.code}"))
            }
            val json = JSONObject(bodyStr)
            val newToken = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            keyStore.putSecret(SecureKeyStore.KEY_YOUTUBE_ACCESS_TOKEN, newToken)
            keyStore.putSecret(
                SecureKeyStore.KEY_YOUTUBE_TOKEN_EXPIRY,
                (System.currentTimeMillis() + (expiresIn - 60L) * 1000L).toString()
            )
            Result.success(newToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchConnectedChannel(): Result<YouTubeChannelInfo> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&mine=true")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("YouTube channels.list failed (HTTP ${resp.code}): $body"))
            }
            val items = JSONObject(body).optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext Result.failure(IllegalStateException("No YouTube channel found on this Google account."))
            }
            val ch = items.getJSONObject(0)
            val snippet = ch.getJSONObject("snippet")
            val stats = ch.optJSONObject("statistics")
            Result.success(
                YouTubeChannelInfo(
                    channelId = ch.getString("id"),
                    title = snippet.getString("title"),
                    subscriberCount = stats?.optString("subscriberCount", "0") ?: "0"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadVideo(
        videoFile: File,
        thumbnailFile: File?,
        title: String,
        description: String,
        tags: List<String>,
        categoryId: String,
        visibility: String,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!videoFile.exists() || videoFile.length() <= 0L) {
            return@withContext Result.failure(IllegalStateException("Exported MP4 file does not exist."))
        }
        val token = getValidAccessToken().getOrElse { return@withContext Result.failure(it) }
        try {
            val safeVisibility = when (visibility.lowercase()) {
                "public", "unlisted", "private" -> visibility.lowercase()
                else -> "private"
            }
            val metadata = JSONObject().apply {
                put(
                    "snippet",
                    JSONObject().apply {
                        put("title", title.take(100))
                        put("description", description.take(5000))
                        put("tags", JSONArray(tags.take(25)))
                        put("categoryId", categoryId.ifBlank { "22" })
                    }
                )
                put(
                    "status",
                    JSONObject().apply {
                        put("privacyStatus", safeVisibility)
                        put("selfDeclaredMadeForKids", false)
                    }
                )
            }

            val initReq = Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Upload-Content-Length", videoFile.length().toString())
                .addHeader("X-Upload-Content-Type", "video/mp4")
                .post(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val initResp = httpClient.newCall(initReq).execute()
            val uploadUrl = initResp.header("Location")
            if (!initResp.isSuccessful || uploadUrl.isNullOrBlank()) {
                val errBody = initResp.body?.string().orEmpty()
                return@withContext Result.failure(IllegalStateException("Failed to initiate YouTube upload (HTTP ${initResp.code}): $errBody"))
            }

            val totalBytes = videoFile.length().coerceAtLeast(1L)
            val progressBody = object : RequestBody() {
                override fun contentType() = "video/mp4".toMediaType()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    val buf = ByteArray(64 * 1024)
                    var uploaded = 0L
                    videoFile.inputStream().use { input ->
                        var read = input.read(buf)
                        while (read != -1) {
                            sink.write(buf, 0, read)
                            uploaded += read
                            onProgress(((uploaded * 90) / totalBytes).toInt().coerceIn(0, 90))
                            read = input.read(buf)
                        }
                    }
                }
            }

            val uploadReq = Request.Builder()
                .url(uploadUrl)
                .put(progressBody)
                .build()
            val uploadResp = httpClient.newCall(uploadReq).execute()
            val uploadBody = uploadResp.body?.string().orEmpty()
            if (!uploadResp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("YouTube upload failed (HTTP ${uploadResp.code}): $uploadBody"))
            }

            val videoId = JSONObject(uploadBody).optString("id", "")
            if (videoId.isBlank()) {
                return@withContext Result.failure(IllegalStateException("YouTube did not return a video ID."))
            }

            if (thumbnailFile != null && thumbnailFile.exists() && thumbnailFile.length() in 1L..(2 * 1024 * 1024L)) {
                val thumbReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=$videoId&uploadType=media")
                    .addHeader("Authorization", "Bearer $token")
                    .post(thumbnailFile.asRequestBody("image/jpeg".toMediaType()))
                    .build()
                runCatching { httpClient.newCall(thumbReq).execute().close() }
            }
            onProgress(100)
            Result.success(videoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "YouTube video upload failed", e)
            Result.failure(e)
        }
    }

    fun disconnectChannel() {
        keyStore.removeSecret(SecureKeyStore.KEY_YOUTUBE_ACCESS_TOKEN)
        keyStore.removeSecret(SecureKeyStore.KEY_YOUTUBE_REFRESH_TOKEN)
        keyStore.removeSecret(SecureKeyStore.KEY_YOUTUBE_TOKEN_EXPIRY)
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object {
        const val TAG = "YouTubeRepository"
    }
}
