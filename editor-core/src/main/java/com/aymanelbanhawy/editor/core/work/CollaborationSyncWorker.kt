package com.aymanelbanhawy.editor.core.work

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aymanelbanhawy.editor.core.collaboration.CollaborationConflictResolver
import com.aymanelbanhawy.editor.core.collaboration.CollaborationCredentialStore
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRemoteRegistry
import com.aymanelbanhawy.editor.core.collaboration.DefaultCollaborationRepository
import com.aymanelbanhawy.editor.core.data.MIGRATION_10_11
import com.aymanelbanhawy.editor.core.data.MIGRATION_7_8
import com.aymanelbanhawy.editor.core.data.MIGRATION_8_9
import com.aymanelbanhawy.editor.core.data.MIGRATION_9_10
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.enterprise.DefaultEnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseCredentialStore
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseRemoteRegistry
import kotlinx.serialization.json.Json

class CollaborationSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentKey = inputData.getString(KEY_DOCUMENT_KEY) ?: return Result.failure()
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "_type"
        }
        val database = Room.databaseBuilder(applicationContext, PdfWorkspaceDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
            .build()
        return try {
            val enterpriseAdminRepository = DefaultEnterpriseAdminRepository(
                context = applicationContext,
                settingsDao = database.enterpriseSettingsDao(),
                telemetryDao = database.telemetryEventDao(),
                credentialStore = EnterpriseCredentialStore(applicationContext, json),
                remoteRegistry = EnterpriseRemoteRegistry(applicationContext, json),
                json = json,
            )
            val repository = DefaultCollaborationRepository(
                context = applicationContext,
                shareLinkDao = database.shareLinkDao(),
                reviewThreadDao = database.reviewThreadDao(),
                reviewCommentDao = database.reviewCommentDao(),
                versionSnapshotDao = database.versionSnapshotDao(),
                activityEventDao = database.activityEventDao(),
                syncQueueDao = database.syncQueueDao(),
                remoteRegistry = CollaborationRemoteRegistry(
                    context = applicationContext,
                    enterpriseAdminRepository = enterpriseAdminRepository,
                    credentialStore = CollaborationCredentialStore(applicationContext, json),
                    json = json,
                ),
                conflictResolver = CollaborationConflictResolver(),
                enterpriseAdminRepository = enterpriseAdminRepository,
                syncScheduler = object : com.aymanelbanhawy.editor.core.collaboration.CollaborationSyncScheduler {
                    override fun schedule(documentKey: String) = Unit
                },
                json = json,
            )
            val summary = repository.processSync(documentKey)
            if (summary.failedCount > 0 && summary.completedCount == 0 && runAttemptCount < 5) Result.retry() else Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        } finally {
            database.close()
        }
    }

    companion object {
        const val KEY_DOCUMENT_KEY = "document_key"
        private const val DB_NAME = "enterprise-editor.db"
    }
}
