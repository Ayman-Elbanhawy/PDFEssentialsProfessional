
package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import com.aymanelbanhawy.editor.core.data.ActivityEventDao
import com.aymanelbanhawy.editor.core.data.ActivityEventEntity
import com.aymanelbanhawy.editor.core.data.ReviewCommentDao
import com.aymanelbanhawy.editor.core.data.ReviewCommentEntity
import com.aymanelbanhawy.editor.core.data.ReviewThreadDao
import com.aymanelbanhawy.editor.core.data.ReviewThreadEntity
import com.aymanelbanhawy.editor.core.data.ShareLinkDao
import com.aymanelbanhawy.editor.core.data.ShareLinkEntity
import com.aymanelbanhawy.editor.core.data.SyncQueueDao
import com.aymanelbanhawy.editor.core.data.SyncQueueEntity
import com.aymanelbanhawy.editor.core.data.VersionSnapshotDao
import com.aymanelbanhawy.editor.core.data.VersionSnapshotEntity
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import java.io.File
import java.util.UUID
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class DefaultCollaborationRepository(
    private val context: Context,
    private val shareLinkDao: ShareLinkDao,
    private val reviewThreadDao: ReviewThreadDao,
    private val reviewCommentDao: ReviewCommentDao,
    private val versionSnapshotDao: VersionSnapshotDao,
    private val activityEventDao: ActivityEventDao,
    private val syncQueueDao: SyncQueueDao,
    private val remoteRegistry: CollaborationRemoteRegistry,
    private val conflictResolver: CollaborationConflictResolver,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val syncScheduler: CollaborationSyncScheduler,
    private val json: Json,
) : CollaborationRepository {

    override suspend fun shareLinks(documentKey: String): List<ShareLinkModel> {
        return shareLinkDao.forDocument(documentKey).map { it.toModel() }
    }

    override suspend fun createShareLink(document: DocumentModel, title: String, permission: SharePermission, expiresAtEpochMillis: Long?): ShareLinkModel {
        val state = enterpriseAdminRepository.loadState()
        if (!state.adminPolicy.allowExternalSharing) {
            throw CollaborationRemoteException(
                RemoteErrorMetadata(RemoteErrorCode.Forbidden, "External sharing is disabled by tenant policy.", retryable = false),
            )
        }
        val now = System.currentTimeMillis()
        val model = ShareLinkModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            token = UUID.randomUUID().toString().replace("-", ""),
            title = title.ifBlank { document.documentRef.displayName },
            createdBy = actorName(state),
            createdAtEpochMillis = now,
            expiresAtEpochMillis = expiresAtEpochMillis,
            permission = permission,
        )
        shareLinkDao.upsert(model.toEntity())
        enqueueMutation(
            documentKey = model.documentKey,
            type = SyncOperationType.UpsertShareLink,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.ShareLink,
                mutationKind = MutationKind.Upsert,
                entityId = model.id,
                currentJson = json.encodeToString(ShareLinkModel.serializer(), model),
                previousJson = null,
                baseRemoteVersion = model.remoteVersion,
                baseServerUpdatedAtEpochMillis = model.serverUpdatedAtEpochMillis,
            ),
            tombstone = false,
        )
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = model.documentKey,
                type = ActivityEventType.Shared,
                actor = model.createdBy,
                summary = "Created share link ${model.title}",
                createdAtEpochMillis = now,
                metadata = mapOf("permission" to permission.name, "url" to model.shareUrl),
            ),
        )
        return model
    }

    override suspend fun reviewThreads(documentKey: String, filter: ReviewFilterModel): List<ReviewThreadModel> {
        return reviewThreadDao.forDocument(documentKey)
            .map { entity -> entity.toModel(reviewCommentDao.forThread(entity.id).map { it.toModel() }) }
            .filter(filter::matches)
    }

    override suspend fun addReviewThread(document: DocumentModel, title: String, message: String, pageIndex: Int?, anchorBounds: NormalizedRect?, voiceAttachment: VoiceCommentAttachmentModel?): ReviewThreadModel {
        val now = System.currentTimeMillis()
        val actor = actorName(enterpriseAdminRepository.loadState())
        val threadId = UUID.randomUUID().toString()
        val comment = ReviewCommentModel(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            author = actor,
            message = message,
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            mentions = parseMentions(message),
            voiceAttachment = voiceAttachment,
        )
        val thread = ReviewThreadModel(
            id = threadId,
            documentKey = document.documentRef.sourceKey,
            pageIndex = pageIndex,
            anchorBounds = anchorBounds,
            title = title.ifBlank { "Review note" },
            createdBy = actor,
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            comments = listOf(comment),
        )
        reviewThreadDao.upsert(thread.toEntity(json))
        reviewCommentDao.upsert(comment.toEntity(json))
        enqueueMutation(
            documentKey = thread.documentKey,
            type = SyncOperationType.UpsertReviewThread,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.ReviewThread,
                mutationKind = MutationKind.Upsert,
                entityId = thread.id,
                currentJson = json.encodeToString(ReviewThreadModel.serializer(), thread),
                previousJson = null,
                baseRemoteVersion = thread.remoteVersion,
                baseServerUpdatedAtEpochMillis = thread.serverUpdatedAtEpochMillis,
            ),
            tombstone = false,
        )
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = thread.documentKey,
                type = ActivityEventType.Commented,
                actor = actor,
                summary = "Started review thread ${thread.title}",
                createdAtEpochMillis = now,
                threadId = thread.id,
                metadata = mapOf("page" to (pageIndex?.plus(1)?.toString() ?: "document")),
            ),
        )
        return thread
    }

    override suspend fun addReviewReply(threadId: String, author: String, message: String, voiceAttachment: VoiceCommentAttachmentModel?): ReviewThreadModel {
        val threadEntity = requireNotNull(reviewThreadDao.thread(threadId))
        val existing = threadEntity.toModel(reviewCommentDao.forThread(threadId).map { it.toModel() })
        val now = System.currentTimeMillis()
        val comment = ReviewCommentModel(
            id = UUID.randomUUID().toString(),
            threadId = threadId,
            author = author,
            message = message,
            createdAtEpochMillis = now,
            modifiedAtEpochMillis = now,
            mentions = parseMentions(message),
            voiceAttachment = voiceAttachment,
        )
        val updatedThread = existing.copy(
            modifiedAtEpochMillis = now,
            comments = (existing.comments + comment).sortedBy { it.createdAtEpochMillis },
        )
        reviewCommentDao.upsert(comment.toEntity(json))
        reviewThreadDao.upsert(updatedThread.toEntity(json))
        enqueueMutation(
            documentKey = updatedThread.documentKey,
            type = SyncOperationType.UpsertReviewThread,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.ReviewThread,
                mutationKind = MutationKind.Upsert,
                entityId = updatedThread.id,
                currentJson = json.encodeToString(ReviewThreadModel.serializer(), updatedThread),
                previousJson = json.encodeToString(ReviewThreadModel.serializer(), existing),
                baseRemoteVersion = existing.remoteVersion,
                baseServerUpdatedAtEpochMillis = existing.serverUpdatedAtEpochMillis,
            ),
            tombstone = false,
        )
        recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = updatedThread.documentKey,
                type = ActivityEventType.Commented,
                actor = author,
                summary = "Replied in ${updatedThread.title}",
                createdAtEpochMillis = now,
                threadId = updatedThread.id,
            ),
        )
        return updatedThread
    }

    override suspend fun setThreadResolved(threadId: String, resolved: Boolean): ReviewThreadModel? {
        val entity = reviewThreadDao.thread(threadId) ?: return null
        val existing = entity.toModel(reviewCommentDao.forThread(threadId).map { it.toModel() })
        val updated = existing.copy(
            state = if (resolved) ReviewThreadState.Resolved else ReviewThreadState.Open,
            modifiedAtEpochMillis = System.currentTimeMillis(),
        )
        reviewThreadDao.upsert(updated.toEntity(json))
        enqueueMutation(
            documentKey = updated.documentKey,
            type = SyncOperationType.UpsertReviewThread,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.ReviewThread,
                mutationKind = MutationKind.Upsert,
                entityId = updated.id,
                currentJson = json.encodeToString(ReviewThreadModel.serializer(), updated),
                previousJson = json.encodeToString(ReviewThreadModel.serializer(), existing),
                baseRemoteVersion = existing.remoteVersion,
                baseServerUpdatedAtEpochMillis = existing.serverUpdatedAtEpochMillis,
            ),
            tombstone = false,
        )
        return updated
    }

    override suspend fun versionSnapshots(documentKey: String): List<VersionSnapshotModel> {
        return versionSnapshotDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun createVersionSnapshot(document: DocumentModel, label: String): VersionSnapshotModel {
        val snapshotDir = File(context.filesDir, "collaboration-snapshots").apply { mkdirs() }
        val sourceFile = File(document.documentRef.workingCopyPath)
        val snapshotFile = File(snapshotDir, "${UUID.randomUUID()}_${sourceFile.name}")
        if (sourceFile.exists()) sourceFile.copyTo(snapshotFile, overwrite = true)
        val previous = versionSnapshots(document.documentRef.sourceKey).firstOrNull()
        val currentCommentCount = reviewThreads(document.documentRef.sourceKey).sumOf { it.comments.size }
        val currentAnnotationCount = document.pages.sumOf { it.annotations.size }
        val comparison = VersionComparisonMetadata(
            pageCountDelta = document.pageCount - (previous?.comparison?.pageCountDelta ?: document.pageCount),
            annotationDelta = currentAnnotationCount - (previous?.comparison?.annotationDelta ?: currentAnnotationCount),
            commentDelta = currentCommentCount - (previous?.comparison?.commentDelta ?: currentCommentCount),
            exportedAtEpochMillis = System.currentTimeMillis(),
        )
        val snapshot = VersionSnapshotModel(
            id = UUID.randomUUID().toString(),
            documentKey = document.documentRef.sourceKey,
            label = label.ifBlank { "Snapshot ${System.currentTimeMillis()}" },
            createdAtEpochMillis = System.currentTimeMillis(),
            snapshotPath = snapshotFile.absolutePath,
            comparison = comparison,
        )
        versionSnapshotDao.upsert(snapshot.toEntity(json))
        enqueueMutation(
            documentKey = snapshot.documentKey,
            type = SyncOperationType.CreateSnapshot,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.VersionSnapshot,
                mutationKind = MutationKind.Upsert,
                entityId = snapshot.id,
                currentJson = json.encodeToString(VersionSnapshotModel.serializer(), snapshot),
                previousJson = null,
                baseRemoteVersion = snapshot.remoteVersion,
                baseServerUpdatedAtEpochMillis = snapshot.serverUpdatedAtEpochMillis,
            ),
            tombstone = false,
        )
        return snapshot
    }

    override suspend fun activityEvents(documentKey: String): List<ActivityEventModel> {
        return activityEventDao.forDocument(documentKey).map { it.toModel(json) }
    }

    override suspend fun recordActivity(event: ActivityEventModel) {
        activityEventDao.upsert(event.toEntity(json))
        enqueueMutation(
            documentKey = event.documentKey,
            type = SyncOperationType.RecordActivity,
            payload = QueuedMutationEnvelope(
                artifactType = CollaborationArtifactType.ActivityEvent,
                mutationKind = MutationKind.Upsert,
                entityId = event.id,
                currentJson = json.encodeToString(ActivityEventModel.serializer(), event),
                previousJson = null,
            ),
            tombstone = false,
        )
    }

    override suspend fun pendingSyncOperations(documentKey: String): List<SyncOperationModel> {
        return syncQueueDao.forDocument(documentKey).map { it.toModel() }.filter { it.state != SyncOperationState.Completed }
    }

    override suspend fun processSync(documentKey: String): CollaborationSyncSummary {
        val eligible = syncQueueDao.eligible(documentKey, System.currentTimeMillis())
        if (eligible.isEmpty()) return CollaborationSyncSummary()
        var processed = 0
        var completed = 0
        var conflicts = 0
        var failed = 0
        var rolledBack = 0

        for (entity in eligible) {
            processed += 1
            val syncing = entity.copy(
                state = SyncOperationState.Syncing.name,
                updatedAtEpochMillis = System.currentTimeMillis(),
                attemptCount = entity.attemptCount + 1,
            )
            syncQueueDao.upsert(syncing)
            val payload = json.decodeFromString(QueuedMutationEnvelope.serializer(), syncing.payloadJson)
            val remote = try {
                remoteRegistry.select()
            } catch (error: Throwable) {
                val metadata = (error as? CollaborationRemoteException)?.error
                    ?: RemoteErrorMetadata(RemoteErrorCode.Offline, error.message ?: "Collaboration provider unavailable", retryable = true)
                syncQueueDao.upsert(syncing.failureState(metadata, backoffMillis(syncing.attemptCount)))
                failed += 1
                continue
            }
            runCatching {
                val result = remote.push(
                    RemoteMutationRequest(
                        documentKey = syncing.documentKey,
                        operationId = syncing.id,
                        idempotencyKey = syncing.idempotencyKey,
                        requestedAtEpochMillis = System.currentTimeMillis(),
                        payload = payload,
                    ),
                )
                applyRemoteResult(result, payload)
            }.onSuccess {
                completed += 1
                syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Completed.name, updatedAtEpochMillis = System.currentTimeMillis(), lastError = null, conflictPayloadJson = null, lastHttpStatus = 200))
            }.onFailure { error ->
                val metadata = (error as? CollaborationRemoteException)?.error
                    ?: RemoteErrorMetadata(RemoteErrorCode.Unknown, error.message ?: "Unknown sync error", retryable = false)
                if (metadata.code == RemoteErrorCode.Conflict && metadata.conflictRemoteJson != null) {
                    conflicts += 1
                    val merged = resolveConflict(payload, metadata.conflictRemoteJson)
                    if (merged != null) {
                        applyMergedLocalState(merged)
                        syncQueueDao.upsert(
                            syncing.copy(
                                state = SyncOperationState.Pending.name,
                                payloadJson = json.encodeToString(QueuedMutationEnvelope.serializer(), merged),
                                updatedAtEpochMillis = System.currentTimeMillis(),
                                lastError = metadata.message,
                                conflictPayloadJson = metadata.conflictRemoteJson,
                                nextAttemptAtEpochMillis = System.currentTimeMillis() + 250,
                                lastHttpStatus = metadata.httpStatus,
                            ),
                        )
                    } else {
                        syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Conflict.name, updatedAtEpochMillis = System.currentTimeMillis(), lastError = metadata.message, conflictPayloadJson = metadata.conflictRemoteJson, lastHttpStatus = metadata.httpStatus))
                    }
                } else if (metadata.retryable && syncing.attemptCount < syncing.maxAttempts) {
                    failed += 1
                    syncQueueDao.upsert(syncing.failureState(metadata, backoffMillis(syncing.attemptCount)))
                } else {
                    rolledBack += rollbackLocalChange(payload)
                    syncQueueDao.upsert(syncing.copy(state = SyncOperationState.Cancelled.name, updatedAtEpochMillis = System.currentTimeMillis(), lastError = metadata.message, lastHttpStatus = metadata.httpStatus, conflictPayloadJson = metadata.conflictRemoteJson))
                }
            }
        }

        runCatching { reconcileDocument(documentKey) }
        return CollaborationSyncSummary(processed, completed, conflicts, failed, rolledBack)
    }

    private suspend fun reconcileDocument(documentKey: String) {
        val remote = remoteRegistry.select()
        var shareToken: String? = null
        var threadToken: String? = null
        var activityToken: String? = null
        var snapshotToken: String? = null
        val shareLinks = mutableListOf<ShareLinkModel>()
        val reviewThreads = mutableListOf<ReviewThreadModel>()
        val activityEvents = mutableListOf<ActivityEventModel>()
        val snapshots = mutableListOf<VersionSnapshotModel>()
        do {
            val snapshot = remote.pull(
                CollaborationPullRequest(
                    documentKey = documentKey,
                    pageSize = 100,
                    shareLinksPageToken = shareToken,
                    reviewThreadsPageToken = threadToken,
                    activityPageToken = activityToken,
                    snapshotsPageToken = snapshotToken,
                ),
            )
            shareLinks += snapshot.shareLinks.items
            reviewThreads += snapshot.reviewThreads.items
            activityEvents += snapshot.activityEvents.items
            snapshots += snapshot.versionSnapshots.items
            shareToken = snapshot.shareLinks.nextPageToken
            threadToken = snapshot.reviewThreads.nextPageToken
            activityToken = snapshot.activityEvents.nextPageToken
            snapshotToken = snapshot.versionSnapshots.nextPageToken
        } while (shareToken != null || threadToken != null || activityToken != null || snapshotToken != null)

        shareLinkDao.deleteForDocument(documentKey)
        shareLinkDao.upsertAll(shareLinks.map { it.toEntity() })

        val currentThreadIds = reviewThreadDao.forDocument(documentKey).map { it.id }
        if (currentThreadIds.isNotEmpty()) {
            reviewCommentDao.deleteForThreads(currentThreadIds)
        }
        reviewThreadDao.deleteForDocument(documentKey)
        reviewThreadDao.upsertAll(reviewThreads.map { it.toEntity(json) })
        reviewThreads.forEach { thread ->
            if (thread.comments.isNotEmpty()) {
                reviewCommentDao.upsertAll(thread.comments.map { it.toEntity(json) })
            }
        }

        versionSnapshotDao.deleteForDocument(documentKey)
        versionSnapshotDao.upsertAll(snapshots.map { it.toEntity(json) })

        activityEventDao.deleteForDocument(documentKey)
        activityEventDao.upsertAll(activityEvents.map { it.toEntity(json) })
    }

    private suspend fun applyRemoteResult(result: RemoteMutationResult, payload: QueuedMutationEnvelope) {
        when (result.artifactType) {
            CollaborationArtifactType.ShareLink -> {
                if (result.deleted || payload.mutationKind == MutationKind.Delete) {
                    shareLinkDao.deleteById(payload.entityId)
                } else {
                    val model = json.decodeFromString(ShareLinkModel.serializer(), requireNotNull(result.appliedJson))
                    shareLinkDao.upsert(model.toEntity())
                }
            }
            CollaborationArtifactType.ReviewThread -> {
                if (result.deleted || payload.mutationKind == MutationKind.Delete) {
                    reviewCommentDao.deleteForThread(payload.entityId)
                    reviewThreadDao.deleteById(payload.entityId)
                } else {
                    val model = json.decodeFromString(ReviewThreadModel.serializer(), requireNotNull(result.appliedJson))
                    reviewThreadDao.upsert(model.toEntity(json))
                    reviewCommentDao.deleteForThread(model.id)
                    if (model.comments.isNotEmpty()) {
                        reviewCommentDao.upsertAll(model.comments.map { it.toEntity(json) })
                    }
                }
            }
            CollaborationArtifactType.ActivityEvent -> {
                val model = json.decodeFromString(ActivityEventModel.serializer(), requireNotNull(result.appliedJson))
                activityEventDao.upsert(model.toEntity(json))
            }
            CollaborationArtifactType.VersionSnapshot -> {
                val model = json.decodeFromString(VersionSnapshotModel.serializer(), requireNotNull(result.appliedJson))
                versionSnapshotDao.upsert(model.toEntity(json))
            }
        }
    }

    private suspend fun resolveConflict(payload: QueuedMutationEnvelope, remoteJson: String): QueuedMutationEnvelope? {
        return when (payload.artifactType) {
            CollaborationArtifactType.ShareLink -> {
                val local = payload.currentJson?.let { json.decodeFromString(ShareLinkModel.serializer(), it) } ?: return null
                val remote = json.decodeFromString(ShareLinkModel.serializer(), remoteJson)
                val merged = conflictResolver.resolveShareLink(local, remote)
                payload.copy(
                    currentJson = json.encodeToString(ShareLinkModel.serializer(), merged),
                    previousJson = payload.previousJson ?: payload.currentJson,
                    baseRemoteVersion = remote.remoteVersion,
                    baseServerUpdatedAtEpochMillis = remote.serverUpdatedAtEpochMillis,
                )
            }
            CollaborationArtifactType.ReviewThread -> {
                val local = payload.currentJson?.let { json.decodeFromString(ReviewThreadModel.serializer(), it) } ?: return null
                val remote = json.decodeFromString(ReviewThreadModel.serializer(), remoteJson)
                val merged = conflictResolver.resolveThread(local, remote)
                payload.copy(
                    currentJson = json.encodeToString(ReviewThreadModel.serializer(), merged),
                    previousJson = payload.previousJson ?: payload.currentJson,
                    baseRemoteVersion = remote.remoteVersion,
                    baseServerUpdatedAtEpochMillis = remote.serverUpdatedAtEpochMillis,
                )
            }
            else -> null
        }
    }

    private suspend fun applyMergedLocalState(payload: QueuedMutationEnvelope) {
        when (payload.artifactType) {
            CollaborationArtifactType.ShareLink -> payload.currentJson?.let {
                shareLinkDao.upsert(json.decodeFromString(ShareLinkModel.serializer(), it).toEntity())
            }
            CollaborationArtifactType.ReviewThread -> payload.currentJson?.let {
                val model = json.decodeFromString(ReviewThreadModel.serializer(), it)
                reviewThreadDao.upsert(model.toEntity(json))
                reviewCommentDao.deleteForThread(model.id)
                if (model.comments.isNotEmpty()) {
                    reviewCommentDao.upsertAll(model.comments.map { comment -> comment.toEntity(json) })
                }
            }
            else -> Unit
        }
    }

    private suspend fun rollbackLocalChange(payload: QueuedMutationEnvelope): Int {
        return when (payload.artifactType) {
            CollaborationArtifactType.ShareLink -> {
                val previous = payload.previousJson?.let { json.decodeFromString(ShareLinkModel.serializer(), it) }
                if (previous == null) shareLinkDao.deleteById(payload.entityId) else shareLinkDao.upsert(previous.toEntity())
                1
            }
            CollaborationArtifactType.ReviewThread -> {
                val previous = payload.previousJson?.let { json.decodeFromString(ReviewThreadModel.serializer(), it) }
                if (previous == null) {
                    reviewCommentDao.deleteForThread(payload.entityId)
                    reviewThreadDao.deleteById(payload.entityId)
                } else {
                    reviewThreadDao.upsert(previous.toEntity(json))
                    reviewCommentDao.deleteForThread(previous.id)
                    if (previous.comments.isNotEmpty()) {
                        reviewCommentDao.upsertAll(previous.comments.map { it.toEntity(json) })
                    }
                }
                1
            }
            else -> 0
        }
    }

    private suspend fun enqueueMutation(documentKey: String, type: SyncOperationType, payload: QueuedMutationEnvelope, tombstone: Boolean) {
        val now = System.currentTimeMillis()
        syncQueueDao.upsert(
            SyncQueueEntity(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = type.name,
                payloadJson = json.encodeToString(QueuedMutationEnvelope.serializer(), payload),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                state = SyncOperationState.Pending.name,
                attemptCount = 0,
                maxAttempts = 5,
                nextAttemptAtEpochMillis = now,
                lastError = null,
                idempotencyKey = UUID.randomUUID().toString(),
                lastHttpStatus = null,
                conflictPayloadJson = null,
                tombstone = tombstone,
            ),
        )
        syncScheduler.schedule(documentKey)
    }

    private fun actorName(state: EnterpriseAdminStateModel): String {
        return state.authSession.displayName.ifBlank { state.authSession.email.ifBlank { "Guest" } }
    }

    private fun backoffMillis(attempt: Int): Long {
        return (1_000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L)
    }

    private fun parseMentions(message: String): List<MentionModel> {
        return Regex("@([A-Za-z0-9_\\-.]+)").findAll(message)
            .map { MentionModel(it.groupValues[1]) }
            .distinctBy { it.username }
            .toList()
    }
}

private fun SyncQueueEntity.failureState(metadata: RemoteErrorMetadata, backoffMillis: Long): SyncQueueEntity {
    return copy(
        state = SyncOperationState.Failed.name,
        updatedAtEpochMillis = System.currentTimeMillis(),
        lastError = metadata.message,
        nextAttemptAtEpochMillis = System.currentTimeMillis() + backoffMillis,
        lastHttpStatus = metadata.httpStatus,
        conflictPayloadJson = metadata.conflictRemoteJson,
    )
}

private fun ShareLinkModel.toEntity(): ShareLinkEntity = ShareLinkEntity(
    id = id,
    documentKey = documentKey,
    token = token,
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    permission = permission.name,
    isRevoked = isRevoked,
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ShareLinkEntity.toModel(): ShareLinkModel = ShareLinkModel(
    id = id,
    documentKey = documentKey,
    token = token,
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    expiresAtEpochMillis = expiresAtEpochMillis,
    permission = SharePermission.valueOf(permission),
    isRevoked = isRevoked,
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ReviewThreadModel.toEntity(json: Json): ReviewThreadEntity = ReviewThreadEntity(
    id = id,
    documentKey = documentKey,
    pageIndex = pageIndex,
    anchorBoundsJson = anchorBounds?.let { json.encodeToString(NormalizedRect.serializer(), it) },
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    state = state.name,
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ReviewThreadEntity.toModel(comments: List<ReviewCommentModel>): ReviewThreadModel = ReviewThreadModel(
    id = id,
    documentKey = documentKey,
    pageIndex = pageIndex,
    anchorBounds = anchorBoundsJson?.let { Json { ignoreUnknownKeys = true }.decodeFromString(NormalizedRect.serializer(), it) },
    title = title,
    createdBy = createdBy,
    createdAtEpochMillis = createdAtEpochMillis,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    state = ReviewThreadState.valueOf(state),
    comments = comments,
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ReviewCommentModel.toEntity(json: Json): ReviewCommentEntity = ReviewCommentEntity(
    id = id,
    threadId = threadId,
    author = author,
    message = message,
    createdAtEpochMillis = createdAtEpochMillis,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    mentionsJson = json.encodeToString(ListSerializer(MentionModel.serializer()), mentions),
    voiceAttachmentJson = voiceAttachment?.let { json.encodeToString(VoiceCommentAttachmentModel.serializer(), it) },
)

private fun ReviewCommentEntity.toModel(): ReviewCommentModel = ReviewCommentModel(
    id = id,
    threadId = threadId,
    author = author,
    message = message,
    createdAtEpochMillis = createdAtEpochMillis,
    modifiedAtEpochMillis = modifiedAtEpochMillis,
    mentions = Json { ignoreUnknownKeys = true }.decodeFromString(ListSerializer(MentionModel.serializer()), mentionsJson),
    voiceAttachment = voiceAttachmentJson?.let { Json { ignoreUnknownKeys = true }.decodeFromString(VoiceCommentAttachmentModel.serializer(), it) },
)

private fun VersionSnapshotModel.toEntity(json: Json): VersionSnapshotEntity = VersionSnapshotEntity(
    id = id,
    documentKey = documentKey,
    label = label,
    createdAtEpochMillis = createdAtEpochMillis,
    snapshotPath = snapshotPath,
    comparisonJson = json.encodeToString(VersionComparisonMetadata.serializer(), comparison),
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun VersionSnapshotEntity.toModel(json: Json): VersionSnapshotModel = VersionSnapshotModel(
    id = id,
    documentKey = documentKey,
    label = label,
    createdAtEpochMillis = createdAtEpochMillis,
    snapshotPath = snapshotPath,
    comparison = json.decodeFromString(VersionComparisonMetadata.serializer(), comparisonJson),
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ActivityEventModel.toEntity(json: Json): ActivityEventEntity = ActivityEventEntity(
    id = id,
    documentKey = documentKey,
    type = type.name,
    actor = actor,
    summary = summary,
    createdAtEpochMillis = createdAtEpochMillis,
    threadId = threadId,
    metadataJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), metadata),
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun ActivityEventEntity.toModel(json: Json): ActivityEventModel = ActivityEventModel(
    id = id,
    documentKey = documentKey,
    type = ActivityEventType.valueOf(type),
    actor = actor,
    summary = summary,
    createdAtEpochMillis = createdAtEpochMillis,
    threadId = threadId,
    metadata = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), metadataJson),
    remoteVersion = remoteVersion,
    serverUpdatedAtEpochMillis = serverUpdatedAtEpochMillis,
    lastSyncedAtEpochMillis = lastSyncedAtEpochMillis,
)

private fun SyncQueueEntity.toModel(): SyncOperationModel = SyncOperationModel(
    id = id,
    documentKey = documentKey,
    type = SyncOperationType.valueOf(type),
    payloadJson = payloadJson,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    state = SyncOperationState.valueOf(state),
    attemptCount = attemptCount,
    maxAttempts = maxAttempts,
    nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
    lastError = lastError,
    idempotencyKey = idempotencyKey,
    lastHttpStatus = lastHttpStatus,
    conflictPayloadJson = conflictPayloadJson,
    tombstone = tombstone,
)



