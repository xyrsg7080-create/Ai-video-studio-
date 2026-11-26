package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.core.security.SecureKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StockMediaItem(
    val id: String,
    val title: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val previewUrl: String,
    val downloadUrl: String,
    val sourceProvider: String, // "Wikimedia Commons", "Pexels", "Pixabay"
    val sourceUrl: String,
    val licenseName: String,
    val attributionText: String,
    val commercialAllowed: Boolean,
    val durationSec: Int = 5
)

class StockMediaRepository(
    private val context: Context,
    private val keyStore: SecureKeyStore
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun searchAllAvailable(query: String): Result<List<StockMediaItem>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim().ifBlank { "technology nature" }
        val results = mutableListOf<StockMediaItem>()
        val errors = mutableListOf<String>()

        // 1. Wikimedia Commons (100% free & keyless)
        searchWikimediaCommons(cleanQuery).fold(
            onSuccess = { results.addAll(it) },
            onFailure = { errors.add("Wikimedia: ${it.localizedMessage}") }
        )

        // 2. Pexels (if user configured free API key)
        val pexelsKey = keyStore.getSecret(SecureKeyStore.KEY_PEXELS_API)
        if (pexelsKey.isNotBlank()) {
            searchPexels(cleanQuery, pexelsKey).fold(
                onSuccess = { results.addAll(it) },
                onFailure = { errors.add("Pexels: ${it.localizedMessage}") }
            )
        }

        // 3. Pixabay (if user configured free API key)
        val pixabayKey = keyStore.getSecret(SecureKeyStore.KEY_PIXABAY_API)
        if (pixabayKey.isNotBlank()) {
            searchPixabay(cleanQuery, pixabayKey).fold(
                onSuccess = { results.addAll(it) },
                onFailure = { errors.add("Pixabay: ${it.localizedMessage}") }
            )
        }

        if (results.isEmpty() && errors.isNotEmpty()) {
            Result.failure(IllegalStateException(errors.joinToString(" | ")))
        } else {
            Result.success(results)
        }
    }

    suspend fun searchWikimediaCommons(query: String): Result<List<StockMediaItem>> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=12&prop=imageinfo&iiprop=url|mime|extmetadata|size&iiurlwidth=640&format=json"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "AIVideoStudioAndroid/1.0 (creator-app)")
                .get()
                .build()
            val resp = httpClient.newCall(req).execute()
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return@withContext Result.failure(IllegalStateException("HTTP ${resp.code}"))
            }
            val pages = JSONObject(body).optJSONObject("query")?.optJSONObject("pages")
                ?: return@withContext Result.success(emptyList())

            val items = mutableListOf<StockMediaItem>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val page = pages.getJSONObject(key)
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime", "")
                if (!mime.startsWith("image/jpeg") && !mime.startsWith("image/png")) continue
                val downloadUrl = info.optString("thumburl", "").ifBlank { info.optString("url", "") }
                if (downloadUrl.isBlank()) continue
                val meta = info.optJSONObject("extmetadata")
                val license = meta?.optJSONObject("LicenseShortName")?.optString("value", "CC / Public Domain")
                    ?: "CC / Public Domain"
                val artistRaw = meta?.optJSONObject("Artist")?.optString("value", "Wikimedia Contributor")
                    ?: "Wikimedia Contributor"
                val artistClean = artistRaw.replace(Regex("<[^>]*>"), "").trim().take(60)
                val commercialOk = !license.contains("NC", ignoreCase = true)
                val title = page.optString("title", "Media").removePrefix("File:").take(60)
                val descUrl = info.optString("descriptionurl", "https://commons.wikimedia.org")

                items.add(
                    StockMediaItem(
                        id = "wiki_$key",
                        title = title,
                        mediaType = "IMAGE",
                        previewUrl = downloadUrl,
                        downloadUrl = downloadUrl,
                        sourceProvider = "Wikimedia Commons",
                        sourceUrl = descUrl,
                        licenseName = license,
                        attributionText = "Image: $title by $artistClean ($license) via Wikimedia Commons",
                        commercialAllowed = commercialOk
                    )
                )
            }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Wikimedia search failed", e)
            Result.failure(e)
        }
    }

    private fun searchPexels(query: String, apiKey: String): Result<List<StockMediaItem>> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val items = mutableListOf<StockMediaItem>()

            // Videos
            val videoReq = Request.Builder()
                .url("https://api.pexels.com/videos/search?query=$encoded&per_page=6")
                .addHeader("Authorization", apiKey)
                .get()
                .build()
            httpClient.newCall(videoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val videos = JSONObject(resp.body?.string().orEmpty()).optJSONArray("videos")
                    if (videos != null) {
                        for (i in 0 until videos.length()) {
                            val v = videos.getJSONObject(i)
                            val files = v.optJSONArray("video_files") ?: continue
                            var bestUrl = ""
                            for (fIdx in 0 until files.length()) {
                                val f = files.getJSONObject(fIdx)
                                if (f.optString("file_type") == "video/mp4") {
                                    bestUrl = f.optString("link", "")
                                    if (f.optInt("width") in 720..1280) break
                                }
                            }
                            if (bestUrl.isBlank()) continue
                            val user = v.optJSONObject("user")?.optString("name", "Pexels Creator") ?: "Pexels"
                            val pageUrl = v.optString("url", "https://www.pexels.com")
                            items.add(
                                StockMediaItem(
                                    id = "pexels_v_${v.optInt("id")}",
                                    title = "$query (Pexels Video)",
                                    mediaType = "VIDEO",
                                    previewUrl = v.optString("image", ""),
                                    downloadUrl = bestUrl,
                                    sourceProvider = "Pexels",
                                    sourceUrl = pageUrl,
                                    licenseName = "Pexels License (Free Commercial Use)",
                                    attributionText = "Video by $user on Pexels ($pageUrl)",
                                    commercialAllowed = true,
                                    durationSec = v.optInt("duration", 6).coerceIn(3, 20)
                                )
                            )
                        }
                    }
                }
            }

            // Photos
            val photoReq = Request.Builder()
                .url("https://api.pexels.com/v1/search?query=$encoded&per_page=6")
                .addHeader("Authorization", apiKey)
                .get()
                .build()
            httpClient.newCall(photoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val photos = JSONObject(resp.body?.string().orEmpty()).optJSONArray("photos")
                    if (photos != null) {
                        for (i in 0 until photos.length()) {
                            val p = photos.getJSONObject(i)
                            val src = p.optJSONObject("src") ?: continue
                            val url = src.optString("large", "").ifBlank { src.optString("medium", "") }
                            if (url.isBlank()) continue
                            val photographer = p.optString("photographer", "Pexels Photographer")
                            val pageUrl = p.optString("url", "https://www.pexels.com")
                            items.add(
                                StockMediaItem(
                                    id = "pexels_p_${p.optInt("id")}",
                                    title = p.optString("alt", query).ifBlank { query }.take(60),
                                    mediaType = "IMAGE",
                                    previewUrl = src.optString("medium", url),
                                    downloadUrl = url,
                                    sourceProvider = "Pexels",
                                    sourceUrl = pageUrl,
                                    licenseName = "Pexels License (Free Commercial Use)",
                                    attributionText = "Photo by $photographer on Pexels ($pageUrl)",
                                    commercialAllowed = true
                                )
                            )
                        }
                    }
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Pexels search failed", e)
            Result.failure(e)
        }
    }

    private fun searchPixabay(query: String, apiKey: String): Result<List<StockMediaItem>> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val items = mutableListOf<StockMediaItem>()

            val videoReq = Request.Builder()
                .url("https://pixabay.com/api/videos/?key=$apiKey&q=$encoded&per_page=6")
                .get()
                .build()
            httpClient.newCall(videoReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val hits = JSONObject(resp.body?.string().orEmpty()).optJSONArray("hits")
                    if (hits != null) {
                        for (i in 0 until hits.length()) {
                            val h = hits.getJSONObject(i)
                            val vids = h.optJSONObject("videos") ?: continue
                            val med = vids.optJSONObject("medium") ?: vids.optJSONObject("small") ?: continue
                            val url = med.optString("url", "")
                            if (url.isBlank()) continue
                            val user = h.optString("user", "Pixabay")
                            val pageUrl = h.optString("pageURL", "https://pixabay.com")
                            items.add(
                                StockMediaItem(
                                    id = "pixabay_v_${h.optInt("id")}",
                                    title = h.optString("tags", query).take(50),
                                    mediaType = "VIDEO",
                                    previewUrl = med.optString("thumbnail", ""),
                                    downloadUrl = url,
                                    sourceProvider = "Pixabay",
                                    sourceUrl = pageUrl,
                                    licenseName = "Pixabay Content License (Free Commercial Use)",
                                    attributionText = "Video by $user from Pixabay ($pageUrl)",
                                    commercialAllowed = true,
                                    durationSec = h.optInt("duration", 6).coerceIn(3, 20)
                                )
                            )
                        }
                    }
                }
            }

            val imgReq = Request.Builder()
                .url("https://pixabay.com/api/?key=$apiKey&q=$encoded&image_type=photo&per_page=6")
                .get()
                .build()
            httpClient.newCall(imgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val hits = JSONObject(resp.body?.string().orEmpty()).optJSONArray("hits")
                    if (hits != null) {
                        for (i in 0 until hits.length()) {
                            val h = hits.getJSONObject(i)
                            val url = h.optString("largeImageURL", "").ifBlank { h.optString("webformatURL", "") }
                            if (url.isBlank()) continue
                            val user = h.optString("user", "Pixabay")
                            val pageUrl = h.optString("pageURL", "https://pixabay.com")
                            items.add(
                                StockMediaItem(
                                    id = "pixabay_p_${h.optInt("id")}",
                                    title = h.optString("tags", query).take(50),
                                    mediaType = "IMAGE",
                                    previewUrl = h.optString("previewURL", url),
                                    downloadUrl = url,
                                    sourceProvider = "Pixabay",
                                    sourceUrl = pageUrl,
                                    licenseName = "Pixabay Content License (Free Commercial Use)",
                                    attributionText = "Image by $user from Pixabay ($pageUrl)",
                                    commercialAllowed = true
                                )
                            )
                        }
                    }
                }
            }
            Result.success(items)
        } catch (e: Exception) {
            Log.e(TAG, "Pixabay search failed", e)
            Result.failure(e)
        }
    }

    suspend fun downloadStockMedia(item: StockMediaItem): Result<File> = withContext(Dispatchers.IO) {
        try {
            val ext = if (item.mediaType == "VIDEO") "mp4" else "jpg"
            val dir = File(context.filesDir, "stock_media").apply { mkdirs() }
            val file = File(dir, "${item.id.replace(Regex("[^a-zA-Z0-9_]"), "_")}.$ext")
            if (file.exists() && file.length() > 1024L) {
                return@withContext Result.success(file)
            }
            val req = Request.Builder()
                .url(item.downloadUrl)
                .header("User-Agent", "AIVideoStudioAndroid/1.0")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Download failed: HTTP ${resp.code}"))
                }
                val bodyBytes = resp.body?.bytes()
                    ?: return@withContext Result.failure(IllegalStateException("Empty download body"))
                file.writeBytes(bodyBytes)
                Result.success(file)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Download stock media failed", e)
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "StockMediaRepository"
    }
}
