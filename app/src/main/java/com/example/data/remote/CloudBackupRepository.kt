package com.example.data.remote

import android.content.Context
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.BrandKitEntity
import com.example.data.local.SceneEntity
import com.example.data.local.VideoProjectEntity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@Keep
@IgnoreExtraProperties
data class CloudProjectBackup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val topic: String = "",
    val aspectRatio: String = "9:16",
    val targetDurationSec: Long = 60L,
    val languageCode: String = "en",
    val scriptHook: String = "",
    val scriptBody: String = "",
    val scriptCta: String = "",
    val sceneSummaries: List<String> = emptyList(),
    val seoTitle: String = "",
    val seoDescription: String = "",
    val seoTags: List<String> = emptyList(),
    val status: String = "DRAFT",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudBrandKit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val channelName: String = "",
    val primaryColorHex: String = "#22D3EE",
    val accentColorHex: String = "#FF6B4A",
    val fontFamily: String = "Space Grotesk",
    val watermarkText: String = "",
    val watermarkEnabled: Boolean = false,
    val defaultAspectRatio: String = "9:16",
    val defaultCaptionStyle: String = "WORD_HIGHLIGHT",
    val applyByDefault: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class CloudBackupRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Google Sign-In is required for Cloud Backup & Restore.")
    }

    private fun sanitizeDocId(raw: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        return cleaned.ifBlank { "item_${System.currentTimeMillis()}" }
    }

    fun observeCloudProjects(userId: String): Flow<List<CloudProjectBackup>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "User session mismatch" }
        val path = "users/$uid/projects"
        emitAll(
            db.collection("users").document(uid).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudProjectBackup::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(displayName: String, preferredLanguage: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "Creator" }.take(80)
            val safeLang = if (preferredLanguage == "hi") "hi" else "en"
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "preferredLanguage" to safeLang,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "displayName" to safeName,
                        "preferredLanguage" to safeLang,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun backupProject(
        project: VideoProjectEntity,
        scenes: List<SceneEntity>
    ): Result<Unit> {
        var docPath = "users/unauthenticated/projects"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(project.id)
            val docRef = db.collection("users").document(uid)
                .collection("projects").document(safeId)
            docPath = docRef.path

            val safeAspect = when (project.aspectRatio) {
                "9:16", "16:9", "1:1", "custom" -> project.aspectRatio
                else -> "9:16"
            }
            val safeLang = if (project.languageCode == "hi") "hi" else "en"
            val safeStatus = when (project.status) {
                "DRAFT", "READY", "EXPORTED", "UPLOADED" -> project.status
                else -> "DRAFT"
            }
            val sceneSummaries = scenes.take(50).map { scene ->
                "${scene.title.take(40)}: ${scene.narrationText.take(120)} (${scene.durationMs / 1000}s)"
            }
            val tags = project.seoTagsCsv.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(30)

            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to project.title.trim().ifBlank { "Untitled Video" }.take(140),
                    "topic" to project.topic.take(500),
                    "aspectRatio" to safeAspect,
                    "targetDurationSec" to project.targetDurationSec.coerceIn(5, 3600),
                    "languageCode" to safeLang,
                    "scriptHook" to project.scriptHook.take(2000),
                    "scriptBody" to project.scriptBody.take(12000),
                    "scriptCta" to project.scriptCta.take(1000),
                    "sceneSummaries" to sceneSummaries,
                    "seoTitle" to project.seoTitle.take(140),
                    "seoDescription" to project.seoDescription.take(5000),
                    "seoTags" to tags,
                    "status" to safeStatus,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                docRef.update(
                    mapOf(
                        "title" to project.title.trim().ifBlank { "Untitled Video" }.take(140),
                        "topic" to project.topic.take(500),
                        "aspectRatio" to safeAspect,
                        "targetDurationSec" to project.targetDurationSec.coerceIn(5, 3600),
                        "languageCode" to safeLang,
                        "scriptHook" to project.scriptHook.take(2000),
                        "scriptBody" to project.scriptBody.take(12000),
                        "scriptCta" to project.scriptCta.take(1000),
                        "sceneSummaries" to sceneSummaries,
                        "seoTitle" to project.seoTitle.take(140),
                        "seoDescription" to project.seoDescription.take(5000),
                        "seoTags" to tags,
                        "status" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun backupBrandKit(kit: BrandKitEntity): Result<Unit> {
        var docPath = "users/unauthenticated/brandKits"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(kit.id)
            val docRef = db.collection("users").document(uid)
                .collection("brandKits").document(safeId)
            docPath = docRef.path
            val safeAspect = when (kit.defaultAspectRatio) {
                "9:16", "16:9", "1:1", "custom" -> kit.defaultAspectRatio
                else -> "9:16"
            }

            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "channelName" to kit.channelName.trim().ifBlank { "My Channel" }.take(100),
                        "primaryColorHex" to kit.primaryColorHex.take(12),
                        "accentColorHex" to kit.accentColorHex.take(12),
                        "fontFamily" to kit.fontFamily.take(50),
                        "watermarkText" to kit.watermarkText.take(60),
                        "watermarkEnabled" to kit.watermarkEnabled,
                        "defaultAspectRatio" to safeAspect,
                        "defaultCaptionStyle" to kit.defaultCaptionStyle.take(40),
                        "applyByDefault" to kit.applyByDefault,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "channelName" to kit.channelName.trim().ifBlank { "My Channel" }.take(100),
                        "primaryColorHex" to kit.primaryColorHex.take(12),
                        "accentColorHex" to kit.accentColorHex.take(12),
                        "fontFamily" to kit.fontFamily.take(50),
                        "watermarkText" to kit.watermarkText.take(60),
                        "watermarkEnabled" to kit.watermarkEnabled,
                        "defaultAspectRatio" to safeAspect,
                        "defaultCaptionStyle" to kit.defaultCaptionStyle.take(40),
                        "applyByDefault" to kit.applyByDefault,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudProject(projectId: String): Result<Unit> {
        var docPath = "users/unauthenticated/projects/$projectId"
        return try {
            val uid = requireUserId()
            val safeId = sanitizeDocId(projectId)
            val docRef = db.collection("users").document(uid)
                .collection("projects").document(safeId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
