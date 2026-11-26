package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.core.localization.LocalAppStrings
import com.example.core.security.ServiceAccessType
import com.example.data.local.SceneEntity
import com.example.data.local.SubtitleEntity
import com.example.data.local.TimelineClipEntity
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldWarning
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceHighest
import com.example.ui.theme.VioletTrack
import java.io.File
import java.util.Locale

@Composable
fun CapabilityBadge(
    accessType: ServiceAccessType,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val (label, color) = when (accessType) {
        ServiceAccessType.OFFLINE_FREE -> strings.offlineBadge to EmeraldSuccess
        ServiceAccessType.ONLINE_FREE_NO_KEY -> strings.onlineFreeBadge to ElectricCyan
        ServiceAccessType.ONLINE_FREE_WITH_KEY -> strings.apiKeyBadge to GoldWarning
        ServiceAccessType.ONLINE_PAID_OR_QUOTA -> strings.quotaBadge to CoralAmber
    }
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun ScenePreviewCanvas(
    aspectRatio: String,
    scene: SceneEntity?,
    activeSubtitle: SubtitleEntity?,
    overlayClip: TimelineClipEntity?,
    watermarkText: String,
    modifier: Modifier = Modifier
) {
    val ratio = when (aspectRatio) {
        "16:9" -> 16f / 9f
        "1:1" -> 1f
        else -> 9f / 16f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (aspectRatio == "16:9") 210.dp else 260.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = aspectRatio != "16:9")
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(StudioObsidian, StudioSurface, StudioSurfaceHighest)
                    )
                )
                .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
        ) {
            if (scene != null && scene.mediaPath.isNotBlank() && scene.mediaType != "VIDEO" && scene.mediaType != "AI_VIDEO") {
                val model = if (scene.mediaPath.startsWith("content://")) {
                    Uri.parse(scene.mediaPath)
                } else {
                    File(scene.mediaPath)
                }
                AsyncImage(
                    model = model,
                    contentDescription = scene.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (scene != null && (scene.mediaType == "VIDEO" || scene.mediaType == "AI_VIDEO") && scene.mediaPath.isNotBlank()) {
                val uri = if (scene.mediaPath.startsWith("content://")) {
                    Uri.parse(scene.mediaPath)
                } else {
                    Uri.fromFile(File(scene.mediaPath))
                }
                InlineVideoPlayer(uri = uri, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = scene?.title ?: "No Scene Selected",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (scene?.narrationText?.isNotBlank() == true) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = scene.narrationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Watermark
            if (watermarkText.isNotBlank()) {
                Text(
                    text = watermarkText,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }

            // Overlay Text / Sticker
            if (overlayClip != null && overlayClip.overlayText.isNotBlank()) {
                Surface(
                    color = ElectricCyan.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 28.dp, start = 12.dp, end = 12.dp)
                ) {
                    Text(
                        text = overlayClip.overlayText,
                        color = StudioObsidian,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Subtitle preview HUD
            if (activeSubtitle != null && activeSubtitle.text.isNotBlank()) {
                val align = when (activeSubtitle.position) {
                    "TOP" -> Alignment.TopCenter
                    "CENTER" -> Alignment.Center
                    else -> Alignment.BottomCenter
                }
                Surface(
                    color = StudioObsidian.copy(alpha = 0.80f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(align)
                        .padding(12.dp)
                ) {
                    Text(
                        text = activeSubtitle.text,
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InlineVideoPlayer(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        update = { view ->
            if (view.player != exoPlayer) view.player = exoPlayer
        },
        modifier = modifier
    )
}

@Composable
fun VideoPreviewDialog(
    videoFile: File,
    title: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = StudioObsidian,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_video_preview_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close preview")
                    }
                }
                InlineVideoPlayer(
                    uri = Uri.fromFile(videoFile),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

fun shareFileViaProvider(context: Context, file: File, mimeType: String, chooserTitle: String) {
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}

fun formatDurationMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.US, "%.2f GB", mb / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

fun trackColorFor(trackType: String): Color = when (trackType) {
    "VIDEO_MAIN" -> ElectricCyan
    "TEXT_OVERLAY", "STICKER_MEME", "VIDEO_OVERLAY" -> CoralAmber
    "AUDIO_VOICEOVER" -> EmeraldSuccess
    "AUDIO_MUSIC" -> VioletTrack
    else -> GoldWarning
}
