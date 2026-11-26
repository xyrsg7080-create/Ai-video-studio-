package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppLanguage
import com.example.core.security.SecureKeyStore
import com.example.core.security.ServiceCapabilities
import com.example.data.local.StudioPreferences
import com.example.ui.StorageStats
import com.example.ui.components.CapabilityBadge
import com.example.ui.components.formatBytes
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated

@Composable
fun UsageDashboardScreen(
    stats: StorageStats,
    preferences: StudioPreferences,
    projectCount: Int,
    assetCount: Int,
    onCleanCache: () -> Unit
) {
    val lang = LocalAppLanguage.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.widthIn(max = 860.dp).fillMaxSize()
    ) {
        item {
            Text("Usage, Storage & Free-Tier Matrix", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Device Storage & Studio Usage", style = MaterialTheme.typography.titleSmall)
                    Text("Projects: $projectCount • Media Assets: $assetCount", style = MaterialTheme.typography.bodySmall)
                    Text("Available Device Space: ${formatBytes(stats.freeDeviceBytes)}", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                    Text("Project Media: ${formatBytes(stats.projectsBytes)} • Exported MP4s: ${formatBytes(stats.exportsBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("Temporary Render Cache: ${formatBytes(stats.tempCacheBytes)}", style = MaterialTheme.typography.bodySmall)
                    Text("AI Requests Logged: ${preferences.aiRequestsToday} • YouTube API Units Today: ${preferences.youtubeQuotaUnitsToday}/10,000", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    OutlinedButton(onClick = onCleanCache, modifier = Modifier.testTag("clean_cache_button")) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Clean Temporary Render Cache")
                    }
                }
            }
        }

        item {
            Text("Transparent Service Tiers & Offline Fallbacks", style = MaterialTheme.typography.titleMedium)
        }

        items(ServiceCapabilities.all, key = { it.id }) { cap ->
            Card(colors = CardDefaults.cardColors(containerColor = StudioSurface), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (lang == AppLanguage.HINDI) cap.titleHi else cap.titleEn,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        CapabilityBadge(cap.accessType)
                    }
                    Text(
                        text = if (lang == AppLanguage.HINDI) cap.freeQuotaNoteHi else cap.freeQuotaNoteEn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Fallback: ${if (lang == AppLanguage.HINDI) cap.fallbackHi else cap.fallbackEn}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    preferences: StudioPreferences,
    keyStore: SecureKeyStore,
    signedInUserEmail: String?,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetBatterySaver: (Boolean) -> Unit,
    onSetProxyPreview: (Boolean) -> Unit,
    onSaveKeysFeedback: (String) -> Unit,
    onSignOutCloud: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onDeleteAllLocalData: () -> Unit
) {
    var pexelsKey by remember { mutableStateOf(keyStore.getSecret(SecureKeyStore.KEY_PEXELS_API)) }
    var pixabayKey by remember { mutableStateOf(keyStore.getSecret(SecureKeyStore.KEY_PIXABAY_API)) }
    var customGeminiKey by remember { mutableStateOf(keyStore.getSecret(SecureKeyStore.KEY_CUSTOM_GEMINI_API)) }
    var ytClientId by remember { mutableStateOf(keyStore.getSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Studio Settings & Key Vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App Language (UI & Defaults)", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = preferences.language == AppLanguage.ENGLISH,
                            onClick = { onSetLanguage(AppLanguage.ENGLISH) },
                            label = { Text("English") },
                            modifier = Modifier.testTag("settings_lang_en")
                        )
                        FilterChip(
                            selected = preferences.language == AppLanguage.HINDI,
                            onClick = { onSetLanguage(AppLanguage.HINDI) },
                            label = { Text("हिन्दी (Hindi)") },
                            modifier = Modifier.testTag("settings_lang_hi")
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Low-RAM / Battery Saver Default Render (720p24)", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = preferences.batterySaverRender, onCheckedChange = onSetBatterySaver)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Proxy / Lightweight Timeline Previews", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = preferences.proxyPreviewEnabled, onCheckedChange = onSetProxyPreview)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = ElectricCyan)
                        Text("Encrypted User API Keys (Android Keystore AES-256)", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "Gemini AI script/storyboard/fact-check works via Firebase App Check with zero key needed. Add optional keys below for Pexels, Pixabay, YouTube OAuth, or Interactions video generation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pexelsKey,
                        onValueChange = { pexelsKey = it },
                        label = { Text("Pexels API Key (Free 200 req/hr)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pixabayKey,
                        onValueChange = { pixabayKey = it },
                        label = { Text("Pixabay API Key (Free 100 req/min)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ytClientId,
                        onValueChange = { ytClientId = it },
                        label = { Text("YouTube OAuth 2.0 Android/Installed Client ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customGeminiKey,
                        onValueChange = { customGeminiKey = it },
                        label = { Text("Optional Gemini Interactions Video API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            keyStore.putSecret(SecureKeyStore.KEY_PEXELS_API, pexelsKey.trim())
                            keyStore.putSecret(SecureKeyStore.KEY_PIXABAY_API, pixabayKey.trim())
                            keyStore.putSecret(SecureKeyStore.KEY_YOUTUBE_CLIENT_ID, ytClientId.trim())
                            keyStore.putSecret(SecureKeyStore.KEY_CUSTOM_GEMINI_API, customGeminiKey.trim())
                            onSaveKeysFeedback("API keys encrypted & saved to Android Keystore.")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.fillMaxWidth().testTag("save_api_keys_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Save Encrypted Keys", color = StudioObsidian)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Account & Cloud Backup", style = MaterialTheme.typography.titleSmall)
                    if (signedInUserEmail != null) {
                        Text("Signed in as: $signedInUserEmail", style = MaterialTheme.typography.bodySmall, color = EmeraldSuccess)
                        OutlinedButton(onClick = onSignOutCloud, modifier = Modifier.testTag("sign_out_button")) {
                            Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Sign Out")
                        }
                    } else {
                        Text("Running in Local Offline Mode", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = onNavigateToSignIn) {
                            Text("Sign In with Google for Cloud Backup")
                        }
                    }
                }
            }

            Surface(color = CrimsonError.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy & Data Deletion", style = MaterialTheme.typography.titleSmall, color = CrimsonError)
                    Text("Permanently erase all local projects, exported clips, encrypted API keys, and preferences from this device.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.testTag("delete_all_local_data_button")
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = CrimsonError, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Delete All Local Data", color = CrimsonError)
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete All Local Studio Data?") },
            text = { Text("This permanently deletes local Room projects, renders, media assets, and encrypted keys.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteAllLocalData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonError)
                ) { Text("Delete Everything") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}
