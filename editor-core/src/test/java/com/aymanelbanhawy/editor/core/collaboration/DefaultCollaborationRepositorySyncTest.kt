package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import android.content.ContextWrapper
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
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationProvider
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.CollaborationBackendMode
import com.aymanelbanhawy.editor.core.enterprise.CollaborationServiceConfig
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class DefaultCollaborationRepositorySyncTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private val testContext = object : ContextWrapper(null) {
        private val filesRoot = File(System.getProperty("java.io.tmpdir"), "collaboration-sync-test").apply { mkdirs() }
        override fun getFilesDir(): File = filesRoot
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun processSyncCompletesQueuedShareLink() = runTest {
        val remote = RecordingRemoteDataSource(json)
        val repository = repository(remote)

        repository.createShareLink(document(), "Team review", SharePermission.Comment, null)
        val summary = repository.processSync(document().documentRef.sourceKey)

        assertThat(summary.completedCount).isEqualTo(2)
        assertThat(remote.shareLinksFor(document().documentRef.sourceKey)).hasSize(1)
        assertThat(remote.activityFor(document().documentRef.sourceKey)).hasSize(1)
    }

    @Test
    fun processSyncMergesConflictAndEventuallySucceeds() = runTest {
        val remote = ConflictOnceRemoteDataSource(json)
        val repository = repository(remote)
        val created = repository.addReviewThread(document(), "Needs revision", "@sam please update", 0, null)
        remote.seed(
            created.copy(
                title = "Needs revision",
                modifiedAtEpochMillis = created.modifiedAtEpochMillis + 100,
                comments = created.comments + ReviewCommentModel(
                    id = UUID.randomUUID().toString(),
                    threadId = created.id,
                    author = "Sam",
                    message = "Remote reply",
                    createdAtEpochMillis = created.createdAtEpochMillis + 10,
                    modifiedAtEpochMillis = created.modifiedAtEpochMillis + 100,
                ),
                remoteVersion = 2,
                serverUpdatedAtEpochMillis = created.modifiedAtEpochMillis + 100,
            ),
        )

        repository.addReviewReply(created.id, "Ayman", "Local reply")
        val firstPass = repository.processSync(document().documentRef.sourceKey)
        Thread.sleep(300)
        repository.processSync(document().documentRef.sourceKey)
        val threads = repository.reviewThreads(document().documentRef.sourceKey)

        assertThat(firstPass.conflictCount).isEqualTo(1)
        assertThat(threads.single().comments.map { it.message }).contains("Remote reply")
    }

    @Test
    fun processSyncRollsBackPermanentFailure() = runTest {
        val remote = ErrorRemoteDataSource(RemoteErrorMetadata(RemoteErrorCode.Forbidden, "Blocked", retryable = false))
        val repository = repository(remote)

        val shareLink = repository.createShareLink(document(), "Blocked share", SharePermission.View, null)
        val summary = repository.processSync(document().documentRef.sourceKey)

        assertThat(summary.rolledBackCount).isEqualTo(1)
        assertThat(repository.shareLinks(document().documentRef.sourceKey)).doesNotContain(shareLink)
    }

    @Test
    fun processSyncKeepsRetryableFailuresQueued() = runTest {
        val remote = ErrorRemoteDataSource(RemoteErrorMetadata(RemoteErrorCode.Offline, "Offline", retryable = true))
        val repository = repository(remote)

        repository.createShareLink(document(), "Offline share", SharePermission.View, null)
        val summary = repository.processSync(document().documentRef.sourceKey)
        val pending = repository.pendingSyncOperations(document().documentRef.sourceKey)

        assertThat(summary.failedCount).isAtLeast(1)
        assertThat(pending.any { it.state == SyncOperationState.Failed }).isTrue()
    }

    @Test
    fun processSyncPersistsVoiceCommentAttachments() = runTest {
        val remote = RecordingRemoteDataSource(json)
        val repository = repository(remote)
        val attachment = VoiceCommentAttachmentModel(
            id = UUID.randomUUID().toString(),
            localFilePath = "/tmp/voice-comment.m4a",
            mimeType = "audio/mp4",
            durationMillis = 1450L,
            createdAtEpochMillis = System.currentTimeMillis(),
            transcript = "Please confirm clause five.",
        )

        val created = repository.addReviewThread(
            document = document(),
            title = "Voice review",
            message = "See attached note",
            pageIndex = 0,
            anchorBounds = null,
            voiceAttachment = attachment,
        )
        val summary = repository.processSync(document().documentRef.sourceKey)
        val stored = repository.reviewThreads(document().documentRef.sourceKey, ReviewFilterModel()).single()

        assertThat(summary.completedCount).isAtLeast(1)
        assertThat(created.comments.single().voiceAttachment).isEqualTo(attachment)
        assertThat(stored.comments.single().voiceAttachment).isEqualTo(attachment)
        assertThat(remote.threadsFor(document().documentRef.sourceKey).single().comments.single().voiceAttachment).isEqualTo(attachment)
    }

    private fun repository(remote: CollaborationRemoteDataSource): DefaultCollaborationRepository {
        val adminRepository = TestEnterpriseAdminRepository()
        return DefaultCollaborationRepository(
            context = testContext,
            shareLinkDao = FakeShareLinkDao(),
            reviewThreadDao = FakeReviewThreadDao(),
            reviewCommentDao = FakeReviewCommentDao(),
            versionSnapshotDao = FakeVersionSnapshotDao(),
            activityEventDao = FakeActivityEventDao(),
            syncQueueDao = FakeSyncQueueDao(),
            remoteRegistry = object : CollaborationRemoteRegistry(
                context = testContext,
                enterpriseAdminRepository = adminRepository,
                credentialStore = CollaborationCredentialStore(testContext, json),
                json = json,
            ) {
                override suspend fun select(): CollaborationRemoteDataSource = remote
            },
            conflictResolver = CollaborationConflictResolver(),
            enterpriseAdminRepository = adminRepository,
            syncScheduler = object : CollaborationSyncScheduler {
                override fun schedule(documentKey: String) = Unit
            },
            json = json,
        )
    }

    private fun document(): DocumentModel {
        return DocumentModel(
            sessionId = "session",
            documentRef = PdfDocumentRef(
                uriString = "file:///tmp/review.pdf",
                displayName = "review.pdf",
                sourceType = DocumentSourceType.File,
                sourceKey = "/tmp/review.pdf",
                workingCopyPath = "/tmp/review.pdf",
            ),
            pages = listOf(PageModel(index = 0, label = "1")),
        )
    }
}

private open class RecordingRemoteDataSource(
    private val json: Json,
) : CollaborationRemoteDataSource {
    private val shareLinks = linkedMapOf<String, ShareLinkModel>()
    private val threads = linkedMapOf<String, ReviewThreadModel>()
    private val activity = linkedMapOf<String, ActivityEventModel>()
    private val snapshots = linkedMapOf<String, VersionSnapshotModel>()

    override suspend fun healthCheck(): RemoteServiceHealth = RemoteServiceHealth(true, "test", System.currentTimeMillis(), true, true)

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot {
        return CollaborationRemoteSnapshot(
            shareLinks = CollaborationRemotePage(shareLinks.values.filter { it.documentKey == request.documentKey }, null, System.currentTimeMillis()),
            reviewThreads = CollaborationRemotePage(threads.values.filter { it.documentKey == request.documentKey }, null, System.currentTimeMillis()),
            activityEvents = CollaborationRemotePage(activity.values.filter { it.documentKey == request.documentKey }, null, System.currentTimeMillis()),
            versionSnapshots = CollaborationRemotePage(snapshots.values.filter { it.documentKey == request.documentKey }, null, System.currentTimeMillis()),
        )
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult {
        val now = System.currentTimeMillis()
        return when (request.payload.artifactType) {
            CollaborationArtifactType.ShareLink -> {
                val model = json.decodeFromString(ShareLinkModel.serializer(), requireNotNull(request.payload.currentJson)).copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now)
                shareLinks[model.id] = model
                RemoteMutationResult(CollaborationArtifactType.ShareLink, model.id, json.encodeToString(ShareLinkModel.serializer(), model), remoteVersion = 1, serverTimestampEpochMillis = now)
            }
            CollaborationArtifactType.ReviewThread -> {
                val model = json.decodeFromString(ReviewThreadModel.serializer(), requireNotNull(request.payload.currentJson)).copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now)
                threads[model.id] = model
                RemoteMutationResult(CollaborationArtifactType.ReviewThread, model.id, json.encodeToString(ReviewThreadModel.serializer(), model), remoteVersion = 1, serverTimestampEpochMillis = now)
            }
            CollaborationArtifactType.ActivityEvent -> {
                val model = json.decodeFromString(ActivityEventModel.serializer(), requireNotNull(request.payload.currentJson)).copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now)
                activity[model.id] = model
                RemoteMutationResult(CollaborationArtifactType.ActivityEvent, model.id, json.encodeToString(ActivityEventModel.serializer(), model), remoteVersion = 1, serverTimestampEpochMillis = now)
            }
            CollaborationArtifactType.VersionSnapshot -> {
                val model = json.decodeFromString(VersionSnapshotModel.serializer(), requireNotNull(request.payload.currentJson)).copy(remoteVersion = 1, serverUpdatedAtEpochMillis = now, lastSyncedAtEpochMillis = now)
                snapshots[model.id] = model
                RemoteMutationResult(CollaborationArtifactType.VersionSnapshot, model.id, json.encodeToString(VersionSnapshotModel.serializer(), model), remoteVersion = 1, serverTimestampEpochMillis = now)
            }
        }
    }

    fun shareLinksFor(documentKey: String): List<ShareLinkModel> = shareLinks.values.filter { it.documentKey == documentKey }
    fun threadsFor(documentKey: String): List<ReviewThreadModel> = threads.values.filter { it.documentKey == documentKey }
    fun activityFor(documentKey: String): List<ActivityEventModel> = activity.values.filter { it.documentKey == documentKey }
}

private class ConflictOnceRemoteDataSource(
    private val json: Json,
) : RecordingRemoteDataSource(json) {
    private val conflictIds = mutableSetOf<String>()
    private val seededThreads = linkedMapOf<String, ReviewThreadModel>()

    fun seed(thread: ReviewThreadModel) {
        seededThreads[thread.id] = thread
    }

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot {
        val base = super.pull(request)
        val seeded = seededThreads.values.filter { it.documentKey == request.documentKey }
        return base.copy(reviewThreads = CollaborationRemotePage(seeded, null, System.currentTimeMillis()))
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult {
        if (request.payload.artifactType == CollaborationArtifactType.ReviewThread && conflictIds.add(request.payload.entityId)) {
            val remote = seededThreads.getValue(request.payload.entityId)
            throw CollaborationRemoteException(
                RemoteErrorMetadata(
                    code = RemoteErrorCode.Conflict,
                    message = "Conflict",
                    retryable = false,
                    conflictRemoteJson = json.encodeToString(ReviewThreadModel.serializer(), remote),
                ),
            )
        }
        val result = super.push(request)
        if (request.payload.artifactType == CollaborationArtifactType.ReviewThread && result.appliedJson != null) {
            seededThreads[request.payload.entityId] = json.decodeFromString(ReviewThreadModel.serializer(), result.appliedJson)
        }
        return result
    }
}

private class ErrorRemoteDataSource(
    private val error: RemoteErrorMetadata,
) : CollaborationRemoteDataSource {
    override suspend fun healthCheck(): RemoteServiceHealth = RemoteServiceHealth(false, "test", System.currentTimeMillis(), true, true)
    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot = throw CollaborationRemoteException(error)
    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult = throw CollaborationRemoteException(error)
}

private class TestEnterpriseAdminRepository : EnterpriseAdminRepository {
    private var state = EnterpriseAdminStateModel(
        plan = LicensePlan.Enterprise,
        authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, provider = AuthenticationProvider.Oidc, isSignedIn = true, displayName = "Ayman"),
        tenantConfiguration = TenantConfigurationModel(collaboration = CollaborationServiceConfig(backendMode = CollaborationBackendMode.RemoteHttp, baseUrl = "https://reviews.example.com")),
        adminPolicy = AdminPolicyModel(allowCollaborationSync = true, allowExternalSharing = true),
    )

    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) { this.state = state }
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Collaboration))
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File = destination
}

private class FakeShareLinkDao : ShareLinkDao {
    private val items = linkedMapOf<String, ShareLinkEntity>()
    override suspend fun upsert(entity: ShareLinkEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ShareLinkEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ShareLinkEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun deleteForDocument(documentKey: String) { items.values.removeAll { it.documentKey == documentKey } }
    override suspend fun deleteById(id: String) { items.remove(id) }
}

private class FakeReviewThreadDao : ReviewThreadDao {
    private val items = linkedMapOf<String, ReviewThreadEntity>()
    override suspend fun upsert(entity: ReviewThreadEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ReviewThreadEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ReviewThreadEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun thread(threadId: String): ReviewThreadEntity? = items[threadId]
    override suspend fun deleteForDocument(documentKey: String) { items.values.removeAll { it.documentKey == documentKey } }
    override suspend fun deleteById(threadId: String) { items.remove(threadId) }
}

private class FakeReviewCommentDao : ReviewCommentDao {
    private val items = mutableListOf<ReviewCommentEntity>()
    override suspend fun upsert(entity: ReviewCommentEntity) { items.removeAll { it.id == entity.id }; items += entity }
    override suspend fun upsertAll(entities: List<ReviewCommentEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forThread(threadId: String): List<ReviewCommentEntity> = items.filter { it.threadId == threadId }
    override suspend fun deleteForThread(threadId: String) { items.removeAll { it.threadId == threadId } }
    override suspend fun deleteForThreads(threadIds: List<String>) { items.removeAll { it.threadId in threadIds } }
}

private class FakeVersionSnapshotDao : VersionSnapshotDao {
    private val items = linkedMapOf<String, VersionSnapshotEntity>()
    override suspend fun upsert(entity: VersionSnapshotEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<VersionSnapshotEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<VersionSnapshotEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun deleteForDocument(documentKey: String) { items.values.removeAll { it.documentKey == documentKey } }
}

private class FakeActivityEventDao : ActivityEventDao {
    private val items = linkedMapOf<String, ActivityEventEntity>()
    override suspend fun upsert(entity: ActivityEventEntity) { items[entity.id] = entity }
    override suspend fun upsertAll(entities: List<ActivityEventEntity>) { entities.forEach { upsert(it) } }
    override suspend fun forDocument(documentKey: String): List<ActivityEventEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun deleteForDocument(documentKey: String) { items.values.removeAll { it.documentKey == documentKey } }
}

private class FakeSyncQueueDao : SyncQueueDao {
    private val items = linkedMapOf<String, SyncQueueEntity>()
    override suspend fun upsert(entity: SyncQueueEntity) { items[entity.id] = entity }
    override suspend fun all(): List<SyncQueueEntity> = items.values.toList()
    override suspend fun forDocument(documentKey: String): List<SyncQueueEntity> = items.values.filter { it.documentKey == documentKey }
    override suspend fun eligible(documentKey: String, nowEpochMillis: Long): List<SyncQueueEntity> = items.values.filter { it.documentKey == documentKey && it.state in listOf(SyncOperationState.Pending.name, SyncOperationState.Failed.name, SyncOperationState.Conflict.name) && it.nextAttemptAtEpochMillis <= nowEpochMillis }
    override suspend fun eligibleAll(nowEpochMillis: Long): List<SyncQueueEntity> = items.values.filter { it.state in listOf(SyncOperationState.Pending.name, SyncOperationState.Failed.name, SyncOperationState.Conflict.name) && it.nextAttemptAtEpochMillis <= nowEpochMillis }
}
