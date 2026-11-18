package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AfkMacro
import com.example.model.BookmarkCategory
import com.example.model.DownloadRecord
import com.example.model.DownloadStatus
import com.example.model.InstanceProfileMode
import com.example.model.InstanceStatus
import com.example.model.ResolutionPreset
import com.example.model.SecureBookmark
import com.example.model.UserAgentMode
import com.example.model.VirtualInstance
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AetherRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchedUserId(userId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        check(uid == userId) { "Authenticated user mismatch; refusing cross-user access." }
        return uid
    }

    private fun <T> wrapSnapshotCatch(
        upstream: Flow<T>,
        path: String,
        uid: String
    ): Flow<T> = upstream.catch { error ->
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

    // --- Virtual Instances ---

    fun observeInstances(userId: String): Flow<List<VirtualInstance>> = flow {
        val uid = requireMatchedUserId(userId)
        val path = "users/$uid/instances"
        val upstream = db.collection("users").document(uid).collection("instances")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    VirtualInstance::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        emitAll(wrapSnapshotCatch(upstream, path, uid))
    }

    suspend fun createInstance(
        userId: String,
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String = "",
        keepAwake: Boolean = true,
        adShieldEnabled: Boolean = true,
        httpsOnly: Boolean = true
    ): Result<String> {
        val trimmedName = name.trim().take(50)
        val trimmedUrl = targetUrl.trim().take(500)
        val path = "users/$userId/instances"
        return try {
            val uid = requireMatchedUserId(userId)
            require(trimmedName.isNotEmpty()) { "Instance name is required." }
            require(trimmedUrl.isNotEmpty()) { "Target URL is required." }

            val docRef = db.collection("users").document(uid).collection("instances").document()
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmedName,
                "profileMode" to profileMode.name,
                "resolutionPreset" to resolutionPreset.name,
                "userAgentMode" to userAgentMode.name,
                "targetUrl" to trimmedUrl,
                "status" to InstanceStatus.STOPPED.name,
                "assignedMacroId" to assignedMacroId.take(128),
                "keepAwake" to keepAwake,
                "adShieldEnabled" to adShieldEnabled,
                "httpsOnly" to httpsOnly,
                "totalRunSeconds" to 0L,
                "cycleCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateInstanceConfig(
        userId: String,
        instanceId: String,
        name: String,
        profileMode: InstanceProfileMode,
        resolutionPreset: ResolutionPreset,
        userAgentMode: UserAgentMode,
        targetUrl: String,
        assignedMacroId: String,
        keepAwake: Boolean,
        adShieldEnabled: Boolean,
        httpsOnly: Boolean
    ): Result<Unit> {
        val docPath = "users/$userId/instances/$instanceId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(instanceId.isNotBlank()) { "Instance ID required." }
            val trimmedName = name.trim().take(50)
            val trimmedUrl = targetUrl.trim().take(500)
            require(trimmedName.isNotEmpty() && trimmedUrl.isNotEmpty()) {
                "Instance name and URL must not be empty."
            }

            db.collection("users").document(uid).collection("instances").document(instanceId)
                .update(
                    mapOf(
                        "name" to trimmedName,
                        "profileMode" to profileMode.name,
                        "resolutionPreset" to resolutionPreset.name,
                        "userAgentMode" to userAgentMode.name,
                        "targetUrl" to trimmedUrl,
                        "assignedMacroId" to assignedMacroId.take(128),
                        "keepAwake" to keepAwake,
                        "adShieldEnabled" to adShieldEnabled,
                        "httpsOnly" to httpsOnly,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInstanceRuntimeState(
        userId: String,
        instanceId: String,
        status: InstanceStatus,
        totalRunSeconds: Long? = null,
        cycleCount: Long? = null
    ): Result<Unit> {
        val docPath = "users/$userId/instances/$instanceId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(instanceId.isNotBlank()) { "Instance ID required." }
            val updates = mutableMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (totalRunSeconds != null) {
                updates["totalRunSeconds"] = totalRunSeconds.coerceIn(0L, 999_999_999L)
            }
            if (cycleCount != null) {
                updates["cycleCount"] = cycleCount.coerceIn(0L, 999_999_999L)
            }
            db.collection("users").document(uid).collection("instances").document(instanceId)
                .update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateInstanceUrl(
        userId: String,
        instanceId: String,
        newUrl: String
    ): Result<Unit> {
        val docPath = "users/$userId/instances/$instanceId"
        return try {
            val uid = requireMatchedUserId(userId)
            val safeUrl = newUrl.trim().take(500)
            require(instanceId.isNotBlank() && safeUrl.isNotEmpty())
            db.collection("users").document(uid).collection("instances").document(instanceId)
                .update(
                    mapOf(
                        "targetUrl" to safeUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteInstance(userId: String, instanceId: String): Result<Unit> {
        val docPath = "users/$userId/instances/$instanceId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(instanceId.isNotBlank())
            db.collection("users").document(uid).collection("instances").document(instanceId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- AFK Macros ---

    fun observeMacros(userId: String): Flow<List<AfkMacro>> = flow {
        val uid = requireMatchedUserId(userId)
        val path = "users/$uid/macros"
        val upstream = db.collection("users").document(uid).collection("macros")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AfkMacro::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        emitAll(wrapSnapshotCatch(upstream, path, uid))
    }

    suspend fun createMacro(
        userId: String,
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ): Result<String> {
        val path = "users/$userId/macros"
        return try {
            val uid = requireMatchedUserId(userId)
            val trimmedName = name.trim().take(50)
            require(trimmedName.isNotEmpty()) { "Macro name is required." }
            val boundedSteps = steps.take(30)
            val docRef = db.collection("users").document(uid).collection("macros").document()
            val payload = mapOf(
                "userId" to uid,
                "name" to trimmedName,
                "description" to description.trim().take(200),
                "loopForever" to loopForever,
                "maxLoops" to maxLoops.coerceIn(1L, 100_000L),
                "intervalMs" to intervalMs.coerceIn(200L, 3_600_000L),
                "steps" to boundedSteps,
                "runCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateMacro(
        userId: String,
        macroId: String,
        name: String,
        description: String,
        loopForever: Boolean,
        maxLoops: Long,
        intervalMs: Long,
        steps: List<String>
    ): Result<Unit> {
        val docPath = "users/$userId/macros/$macroId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(macroId.isNotBlank())
            val trimmedName = name.trim().take(50)
            require(trimmedName.isNotEmpty()) { "Macro name is required." }
            db.collection("users").document(uid).collection("macros").document(macroId)
                .update(
                    mapOf(
                        "name" to trimmedName,
                        "description" to description.trim().take(200),
                        "loopForever" to loopForever,
                        "maxLoops" to maxLoops.coerceIn(1L, 100_000L),
                        "intervalMs" to intervalMs.coerceIn(200L, 3_600_000L),
                        "steps" to steps.take(30),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun incrementMacroRunCount(userId: String, macroId: String): Result<Unit> {
        val docPath = "users/$userId/macros/$macroId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(macroId.isNotBlank())
            db.collection("users").document(uid).collection("macros").document(macroId)
                .update(
                    mapOf(
                        "runCount" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteMacro(userId: String, macroId: String): Result<Unit> {
        val docPath = "users/$userId/macros/$macroId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(macroId.isNotBlank())
            db.collection("users").document(uid).collection("macros").document(macroId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Secure Bookmarks ---

    fun observeBookmarks(userId: String): Flow<List<SecureBookmark>> = flow {
        val uid = requireMatchedUserId(userId)
        val path = "users/$uid/bookmarks"
        val upstream = db.collection("users").document(uid).collection("bookmarks")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    SecureBookmark::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        emitAll(wrapSnapshotCatch(upstream, path, uid))
    }

    suspend fun addBookmark(
        userId: String,
        title: String,
        url: String,
        category: BookmarkCategory
    ): Result<String> {
        val path = "users/$userId/bookmarks"
        return try {
            val uid = requireMatchedUserId(userId)
            val safeTitle = title.trim().take(60)
            val safeUrl = url.trim().take(500)
            require(safeTitle.isNotEmpty() && safeUrl.isNotEmpty()) {
                "Title and URL are required."
            }
            val docRef = db.collection("users").document(uid).collection("bookmarks").document()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "url" to safeUrl,
                    "category" to category.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteBookmark(userId: String, bookmarkId: String): Result<Unit> {
        val docPath = "users/$userId/bookmarks/$bookmarkId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(bookmarkId.isNotBlank())
            db.collection("users").document(uid).collection("bookmarks").document(bookmarkId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Download Records ---

    fun observeDownloads(userId: String): Flow<List<DownloadRecord>> = flow {
        val uid = requireMatchedUserId(userId)
        val path = "users/$uid/downloads"
        val upstream = db.collection("users").document(uid).collection("downloads")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    DownloadRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
        emitAll(wrapSnapshotCatch(upstream, path, uid))
    }

    suspend fun createDownloadRecord(
        userId: String,
        fileName: String,
        url: String,
        mimeType: String
    ): Result<String> {
        val path = "users/$userId/downloads"
        return try {
            val uid = requireMatchedUserId(userId)
            val safeName = fileName.trim().take(150).ifBlank { "download.bin" }
            val safeUrl = url.trim().take(500)
            require(safeUrl.isNotEmpty())
            val docRef = db.collection("users").document(uid).collection("downloads").document()
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "fileName" to safeName,
                    "url" to safeUrl,
                    "mimeType" to mimeType.take(100),
                    "sizeBytes" to 0L,
                    "status" to DownloadStatus.QUEUED.name,
                    "localUri" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateDownloadRecord(
        userId: String,
        downloadId: String,
        status: DownloadStatus,
        sizeBytes: Long,
        localUri: String
    ): Result<Unit> {
        val docPath = "users/$userId/downloads/$downloadId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(downloadId.isNotBlank())
            db.collection("users").document(uid).collection("downloads").document(downloadId)
                .update(
                    mapOf(
                        "status" to status.name,
                        "sizeBytes" to sizeBytes.coerceIn(0L, 10_737_418_240L),
                        "localUri" to localUri.take(500),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDownloadRecord(userId: String, downloadId: String): Result<Unit> {
        val docPath = "users/$userId/downloads/$downloadId"
        return try {
            val uid = requireMatchedUserId(userId)
            require(downloadId.isNotBlank())
            db.collection("users").document(uid).collection("downloads").document(downloadId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
