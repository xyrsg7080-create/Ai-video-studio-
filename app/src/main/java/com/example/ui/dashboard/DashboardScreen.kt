package com.example.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.security.ServiceAccessType
import com.example.data.local.StudioPreferences
import com.example.data.local.VideoProjectEntity
import com.example.ui.components.CapabilityBadge
import com.example.ui.theme.CoralAmber
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldWarning
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.VioletTrack

data class DashboardModuleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val accessType: ServiceAccessType
)

@Composable
fun DashboardScreen(
    activeProject: VideoProjectEntity?,
    projectCount: Int,
    preferences: StudioPreferences,
    isCloudSignedIn: Boolean,
    onModuleClick: (String) -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit,
    onQuickCreateBlank: () -> Unit,
    onDismissOnboarding: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = LocalAppLanguage.current

    val modules = listOf(
        DashboardModuleItem("create_new", strings.moduleCreateNew, "9:16, 16:9, 1:1, Custom", Icons.Filled.AddCircle, ElectricCyan, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("ai_auto", strings.moduleAiAutoMaker, "Prompt -> Full Timeline", Icons.Filled.AutoAwesome, CoralAmber, ServiceAccessType.ONLINE_FREE_NO_KEY),
        DashboardModuleItem("manual_editor", strings.moduleManualEditor, "Multi-track, FX, Export", Icons.Filled.MovieCreation, EmeraldSuccess, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("my_projects", strings.moduleMyProjects, "$projectCount saved locally", Icons.Filled.FolderOpen, ElectricCyan, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("templates", strings.moduleTemplates, "Shorts, Gaming, Tutorials", Icons.Filled.Style, VioletTrack, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("voice_studio", strings.moduleVoiceStudio, "Hindi & English TTS", Icons.Filled.RecordVoiceOver, EmeraldSuccess, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("thumbnail_maker", strings.moduleThumbnailMaker, "16:9 Frame + Text", Icons.Filled.Image, CoralAmber, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("media_library", strings.moduleMediaLibrary, "Stock + Photo Picker", Icons.Filled.PermMedia, ElectricCyan, ServiceAccessType.ONLINE_FREE_NO_KEY),
        DashboardModuleItem("youtube_studio", strings.moduleYouTubeStudio, "OAuth 2.0 + SEO + Upload", Icons.Filled.CloudUpload, GoldWarning, ServiceAccessType.ONLINE_FREE_WITH_KEY),
        DashboardModuleItem("upload_scheduler", strings.moduleUploadScheduler, "Queue & WorkManager", Icons.Filled.ScheduleSend, GoldWarning, ServiceAccessType.ONLINE_FREE_WITH_KEY),
        DashboardModuleItem("batch_creator", strings.moduleBatchCreator, "Multi-Short & Splitter", Icons.Filled.BurstMode, VioletTrack, ServiceAccessType.ONLINE_FREE_NO_KEY),
        DashboardModuleItem("brand_kit", strings.moduleBrandKit, "Colors, Outro, Watermark", Icons.Filled.Palette, CoralAmber, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("usage_dashboard", strings.moduleUsageDashboard, "Storage, Quotas, Cache", Icons.Filled.Analytics, EmeraldSuccess, ServiceAccessType.OFFLINE_FREE),
        DashboardModuleItem("settings", strings.moduleSettings, "Keys, Language, Privacy", Icons.Filled.Settings, ElectricCyan, ServiceAccessType.OFFLINE_FREE)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = when {
            maxWidth >= 900.dp -> 4
            maxWidth >= 600.dp -> 3
            else -> 2
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxSize()
                .align(Alignment.TopCenter)
        ) {
            item(span = { GridItemSpan(columns) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.dashboardTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = strings.dashboardSubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onToggleLanguage(if (lang == AppLanguage.ENGLISH) AppLanguage.HINDI else AppLanguage.ENGLISH)
                        },
                        modifier = Modifier.testTag("dashboard_language_toggle")
                    ) {
                        Text(if (lang == AppLanguage.ENGLISH) "हिन्दी" else "EN")
                    }
                }
            }

            item(span = { GridItemSpan(columns) }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_studio_hero_1795671821141),
                            contentDescription = "AI Video Studio timeline workspace",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            StudioObsidian.copy(alpha = 0.25f),
                                            StudioObsidian.copy(alpha = 0.90f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    color = if (isCloudSignedIn) EmeraldSuccess.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isCloudSignedIn) "Cloud Backup Active" else "Local Offline Studio",
                                        color = if (isCloudSignedIn) EmeraldSuccess else ElectricCyan,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                if (preferences.youtubeChannelTitle.isNotBlank()) {
                                    Surface(
                                        color = CoralAmber.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = "YT: ${preferences.youtubeChannelTitle}",
                                            color = CoralAmber,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onModuleClick("ai_auto") },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hero_ai_auto_button")
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(6.dp))
                                    Text("AI Auto Make", color = StudioObsidian, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = onQuickCreateBlank,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("hero_new_blank_button")
                                ) {
                                    Text("Blank Edit", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            if (activeProject != null) {
                item(span = { GridItemSpan(columns) }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModuleClick("manual_editor") }
                            .testTag("resume_active_project_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Resume Active Project",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                                Text(
                                    text = activeProject.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeProject.aspectRatio} • ${activeProject.targetDurationSec}s • ${activeProject.status}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Open active project in editor",
                                tint = ElectricCyan,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }
            }

            if (!preferences.onboardingCompleted) {
                item(span = { GridItemSpan(columns) }) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Quick Start Guide", style = MaterialTheme.typography.titleSmall, color = GoldWarning)
                                Text(
                                    "1. Pick AI Auto Maker or Blank Edit  2. Customize scenes, voice & captions  3. Export MP4 locally or upload to YouTube.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = onDismissOnboarding,
                                modifier = Modifier.testTag("dismiss_onboarding_button")
                            ) {
                                Text("Got it")
                            }
                        }
                    }
                }
            }

            items(modules, key = { it.id }) { mod ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModuleClick(mod.id) }
                        .testTag("module_${mod.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(mod.accent.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = mod.icon,
                                    contentDescription = mod.title,
                                    tint = mod.accent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            CapabilityBadge(accessType = mod.accessType)
                        }
                        Text(
                            text = mod.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = mod.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
