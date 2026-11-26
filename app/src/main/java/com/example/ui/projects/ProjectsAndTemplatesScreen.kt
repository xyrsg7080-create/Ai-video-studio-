package com.example.ui.projects

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.localization.LocalAppLanguage
import com.example.core.localization.AppLanguage
import com.example.data.local.SceneEntity
import com.example.data.local.VideoProjectEntity
import com.example.data.remote.CloudProjectBackup
import com.example.ui.CloudUiState
import com.example.ui.VideoTemplate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun MyProjectsScreen(
    projects: List<VideoProjectEntity>,
    activeProject: VideoProjectEntity?,
    activeScenes: List<SceneEntity>,
    cloudProjectsState: CloudUiState<List<CloudProjectBackup>>?,
    cloudSyncMessage: String?,
    onSelectAndOpenEditor: (VideoProjectEntity) -> Unit,
    onRenameProject: (VideoProjectEntity, String) -> Unit,
    onDuplicateProject: (VideoProjectEntity) -> Unit,
    onDeleteProject: (String) -> Unit,
    onExportBundleJson: suspend (String) -> String?,
    onImportBundleJson: (String) -> Unit,
    onBackupProjectToCloud: ((VideoProjectEntity, List<SceneEntity>) -> Unit)?,
    onRestoreCloudProject: ((CloudProjectBackup) -> Unit)?,
    onDeleteCloudBackup: ((String) -> Unit)?,
    onNavigateCreateNew: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<VideoProjectEntity?>(null) }
    var renameText by remember { mutableStateOf("") }
    var pendingExportProjectId by remember { mutableStateOf<String?>(null) }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val pid = pendingExportProjectId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                val json = onExportBundleJson(pid) ?: return@launch
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                }
            }
        }
    }

    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let(onImportBundleJson)
        }
    }

    val filtered = remember(projects, search) {
        if (search.isBlank()) projects
        else projects.filter { it.title.contains(search, true) || it.topic.contains(search, true) }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .widthIn(max = 860.dp)
            .fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Projects (${projects.size})", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importJsonLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Import")
                    }
                    Button(
                        onClick = onNavigateCreateNew,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("New", color = StudioObsidian)
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Search projects") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (filtered.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("No Projects Found", style = MaterialTheme.typography.titleMedium)
                        Text("Tap 'New' to create an AI Auto Video or Blank Timeline.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { proj ->
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("project_card_${proj.id}")
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(proj.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${proj.aspectRatio} • ${proj.targetDurationSec}s • ${proj.status} • ${proj.languageCode.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        Row {
                            IconButton(onClick = {
                                renameTarget = proj
                                renameText = proj.title
                            }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Rename project")
                            }
                            IconButton(onClick = { onDuplicateProject(proj) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Duplicate project")
                            }
                            IconButton(onClick = {
                                pendingExportProjectId = proj.id
                                exportJsonLauncher.launch("${proj.title.take(24)}.aivs.json")
                            }) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Export local JSON backup")
                            }
                            IconButton(onClick = { onDeleteProject(proj.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete project")
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSelectAndOpenEditor(proj) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.MovieCreation, contentDescription = null, tint = StudioObsidian, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Open Editor", color = StudioObsidian)
                        }
                        if (onBackupProjectToCloud != null && activeProject?.id == proj.id) {
                            OutlinedButton(
                                onClick = { onBackupProjectToCloud(proj, activeScenes) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Cloud Backup")
                            }
                        }
                    }
                }
            }
        }

        // Firestore Cloud Backup section (ONLY shown when user is signed in)
        if (cloudProjectsState != null && onRestoreCloudProject != null && onDeleteCloudBackup != null) {
            item {
                Spacer(Modifier.height(6.dp))
                Text("Firestore Cloud Backups", style = MaterialTheme.typography.titleMedium, color = EmeraldSuccess)
                if (!cloudSyncMessage.isNullOrBlank()) {
                    Surface(color = StudioSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text(cloudSyncMessage, style = MaterialTheme.typography.bodySmall, color = ElectricCyan, modifier = Modifier.padding(10.dp))
                    }
                }
            }
            when (cloudProjectsState) {
                CloudUiState.Loading -> item { CircularProgressIndicator(modifier = Modifier.padding(12.dp)) }
                is CloudUiState.Error -> item {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp)) {
                        Text(cloudProjectsState.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
                    }
                }
                is CloudUiState.Success -> {
                    if (cloudProjectsState.data.isEmpty()) {
                        item {
                            Text("No cloud backups yet. Open a project and tap Cloud Backup.", style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        items(cloudProjectsState.data, key = { "cloud_${it.id}" }) { backup ->
                            Card(colors = CardDefaults.cardColors(containerColor = StudioSurface), shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(backup.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${backup.aspectRatio} • ${backup.targetDurationSec}s", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                    }
                                    OutlinedButton(onClick = { onRestoreCloudProject(backup) }) {
                                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.size(4.dp))
                                        Text("Restore")
                                    }
                                    IconButton(onClick = { onDeleteCloudBackup(backup.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete cloud backup")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Project") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, label = { Text("Project Name") })
            },
            confirmButton = {
                Button(onClick = {
                    renameTarget?.let { onRenameProject(it, renameText) }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun TemplatesScreen(
    templates: List<VideoTemplate>,
    onUseTemplate: (VideoTemplate) -> Unit
) {
    val lang = LocalAppLanguage.current
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .widthIn(max = 860.dp)
            .fillMaxSize()
    ) {
        item {
            Text("Creator Templates", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Ready-made structures for Shorts, Gaming, Tutorials, Explainers & News.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(templates, key = { it.id }) { tpl ->
            val coverRes = if (tpl.aspectRatio == "9:16") {
                R.drawable.img_template_shorts_1795671822388
            } else {
                R.drawable.img_template_explainer_1795671822122
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(
                        painter = painterResource(id = coverRes),
                        contentDescription = tpl.titleEn,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Text(
                        text = if (lang == AppLanguage.HINDI) tpl.titleHi else tpl.titleEn,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${tpl.category} • ${tpl.aspectRatio} • ${tpl.durationSec}s — ${tpl.descriptionEn}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { onUseTemplate(tpl) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        modifier = Modifier.testTag("use_template_${tpl.id}")
                    ) {
                        Text("Use Template", color = StudioObsidian)
                    }
                }
            }
        }
    }
}
