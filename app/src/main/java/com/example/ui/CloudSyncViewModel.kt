package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.local.BrandKitEntity
import com.example.data.local.SceneEntity
import com.example.data.local.VideoProjectEntity
import com.example.data.remote.CloudBackupRepository
import com.example.data.remote.CloudProjectBackup
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface CloudUiState<out T> {
    data object Loading : CloudUiState<Nothing>
    data class Success<T>(val data: T) : CloudUiState<T>
    data class Error(val message: String) : CloudUiState<Nothing>
}

class CloudSyncViewModel(
    private val cloudRepo: CloudBackupRepository,
    private val currentUserId: String
) : ViewModel() {

    val cloudProjects: StateFlow<CloudUiState<List<CloudProjectBackup>>> =
        cloudRepo.observeCloudProjects(currentUserId)
            .map<List<CloudProjectBackup>, CloudUiState<List<CloudProjectBackup>>> {
                CloudUiState.Success(it)
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val root = firestoreError ?: error
                Log.w(TAG, "Error observing cloud projects", root)
                emit(CloudUiState.Error(root.message ?: "Failed to load cloud backups."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = CloudUiState.Loading
            )

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun syncProfile(displayName: String, langCode: String) {
        viewModelScope.launch {
            cloudRepo.ensureUserProfile(displayName, langCode)
        }
    }

    fun backupProject(project: VideoProjectEntity, scenes: List<SceneEntity>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            cloudRepo.backupProject(project, scenes).fold(
                onSuccess = {
                    _syncMessage.value = "Backed up '${project.title}' to Firestore cloud."
                    onSuccess()
                },
                onFailure = { err ->
                    _syncMessage.value = "Cloud backup failed: ${err.localizedMessage}"
                }
            )
        }
    }

    fun backupBrandKit(kit: BrandKitEntity) {
        viewModelScope.launch {
            cloudRepo.backupBrandKit(kit).fold(
                onSuccess = { _syncMessage.value = "Brand Kit synced to cloud." },
                onFailure = { _syncMessage.value = "Brand Kit sync failed: ${it.localizedMessage}" }
            )
        }
    }

    fun deleteCloudBackup(projectId: String) {
        viewModelScope.launch {
            cloudRepo.deleteCloudProject(projectId).fold(
                onSuccess = { _syncMessage.value = "Cloud backup removed." },
                onFailure = { _syncMessage.value = "Delete failed: ${it.localizedMessage}" }
            )
        }
    }

    fun buildRestoredProjectFromCloud(backup: CloudProjectBackup): Pair<VideoProjectEntity, List<SceneEntity>> {
        val newId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = VideoProjectEntity(
            id = newId,
            title = "${backup.title} (Restored)",
            topic = backup.topic,
            aspectRatio = backup.aspectRatio,
            targetDurationSec = backup.targetDurationSec.toInt().coerceIn(5, 3600),
            languageCode = backup.languageCode,
            scriptHook = backup.scriptHook,
            scriptBody = backup.scriptBody,
            scriptCta = backup.scriptCta,
            seoTitle = backup.seoTitle,
            seoDescription = backup.seoDescription,
            seoTagsCsv = backup.seoTags.joinToString(", "),
            status = "DRAFT",
            createdAtMillis = now,
            updatedAtMillis = now,
            isCloudBackedUp = true
        )
        val scenes = backup.sceneSummaries.ifEmpty { listOf("Restored Scene: ${backup.topic}") }
            .mapIndexed { idx, summary ->
                SceneEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = newId,
                    orderIndex = idx,
                    title = "Scene ${idx + 1}",
                    narrationText = summary,
                    visualSearchQuery = backup.topic.ifBlank { "cinema studio" },
                    durationMs = 5000L,
                    trimEndMs = 5000L
                )
            }
        return project to scenes
    }

    companion object {
        private const val TAG = "CloudSyncViewModel"

        fun provideFactory(currentUserId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CloudSyncViewModel(CloudBackupRepository(db), currentUserId)
            }
        }
    }
}
