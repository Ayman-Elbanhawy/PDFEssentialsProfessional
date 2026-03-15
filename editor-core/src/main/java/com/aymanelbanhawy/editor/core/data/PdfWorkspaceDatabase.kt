package com.aymanelbanhawy.editor.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        RecentDocumentEntity::class,
        DraftEntity::class,
        EditHistoryMetadataEntity::class,
        FormProfileEntity::class,
        SavedSignatureEntity::class,
        SigningIdentityEntity::class,
        RecentSearchEntity::class,
        SearchIndexEntity::class,
        OcrJobEntity::class,
        OcrSettingsEntity::class,
        ShareLinkEntity::class,
        ReviewThreadEntity::class,
        ReviewCommentEntity::class,
        VersionSnapshotEntity::class,
        ActivityEventEntity::class,
        SyncQueueEntity::class,
        AppLockSettingsEntity::class,
        DocumentSecurityEntity::class,
        AuditTrailEventEntity::class,
        EnterpriseSettingsEntity::class,
        TelemetryEventEntity::class,
        ConnectorAccountEntity::class,
        RemoteDocumentMetadataEntity::class,
        ConnectorTransferJobEntity::class,
        RuntimeBreadcrumbEntity::class,
        CompareReportEntity::class,
        FormTemplateEntity::class,
        WorkflowRequestEntity::class,
    ],
    version = 16,
    exportSchema = false,
)
abstract class PdfWorkspaceDatabase : RoomDatabase() {
    abstract fun recentDocumentDao(): RecentDocumentDao
    abstract fun draftDao(): DraftDao
    abstract fun editHistoryMetadataDao(): EditHistoryMetadataDao
    abstract fun formProfileDao(): FormProfileDao
    abstract fun savedSignatureDao(): SavedSignatureDao
    abstract fun signingIdentityDao(): SigningIdentityDao
    abstract fun recentSearchDao(): RecentSearchDao
    abstract fun searchIndexDao(): SearchIndexDao
    abstract fun ocrJobDao(): OcrJobDao
    abstract fun ocrSettingsDao(): OcrSettingsDao
    abstract fun shareLinkDao(): ShareLinkDao
    abstract fun reviewThreadDao(): ReviewThreadDao
    abstract fun reviewCommentDao(): ReviewCommentDao
    abstract fun versionSnapshotDao(): VersionSnapshotDao
    abstract fun activityEventDao(): ActivityEventDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun appLockSettingsDao(): AppLockSettingsDao
    abstract fun documentSecurityDao(): DocumentSecurityDao
    abstract fun auditTrailEventDao(): AuditTrailEventDao
    abstract fun enterpriseSettingsDao(): EnterpriseSettingsDao
    abstract fun telemetryEventDao(): TelemetryEventDao
    abstract fun connectorAccountDao(): ConnectorAccountDao
    abstract fun remoteDocumentMetadataDao(): RemoteDocumentMetadataDao
    abstract fun connectorTransferJobDao(): ConnectorTransferJobDao
    abstract fun runtimeBreadcrumbDao(): RuntimeBreadcrumbDao
    abstract fun compareReportDao(): CompareReportDao
    abstract fun formTemplateDao(): FormTemplateDao
    abstract fun workflowRequestDao(): WorkflowRequestDao
}


