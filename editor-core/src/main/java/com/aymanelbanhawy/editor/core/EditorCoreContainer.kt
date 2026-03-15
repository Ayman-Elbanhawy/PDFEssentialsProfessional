package com.aymanelbanhawy.editor.core

import android.content.Context
import androidx.work.WorkManager
import com.aymanelbanhawy.editor.core.collaboration.CollaborationConflictResolver
import com.aymanelbanhawy.editor.core.collaboration.CollaborationCredentialStore
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRemoteRegistry
import com.aymanelbanhawy.editor.core.collaboration.CollaborationRepository
import com.aymanelbanhawy.editor.core.compat.FileLegacyAnnotationCompatibilityStore
import com.aymanelbanhawy.editor.core.collaboration.DefaultCollaborationRepository
import com.aymanelbanhawy.editor.core.connectors.ConnectorRepository
import com.aymanelbanhawy.editor.core.connectors.DefaultConnectorRepository
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.data.createEditorCoreDatabase
import com.aymanelbanhawy.editor.core.enterprise.DefaultEnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseCredentialStore
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseRemoteRegistry
import com.aymanelbanhawy.editor.core.forms.DefaultFormSupportRepository
import com.aymanelbanhawy.editor.core.forms.DigitalSignatureService
import com.aymanelbanhawy.editor.core.forms.FormSupportRepository
import com.aymanelbanhawy.editor.core.forms.PdfBoxDigitalSignatureService
import com.aymanelbanhawy.editor.core.migration.CoreMigrationRepository
import com.aymanelbanhawy.editor.core.migration.DefaultCoreMigrationRepository
import com.aymanelbanhawy.editor.core.migration.FileLegacyEditCompatibilityBridge
import com.aymanelbanhawy.editor.core.ocr.OcrJobPipeline
import com.aymanelbanhawy.editor.core.ocr.OcrSessionStore
import com.aymanelbanhawy.editor.core.ocr.PdfBoxOcrPdfWriter
import com.aymanelbanhawy.editor.core.organize.DefaultPageThumbnailRepository
import com.aymanelbanhawy.editor.core.organize.PageThumbnailRepository
import com.aymanelbanhawy.editor.core.repository.DefaultDocumentRepository
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.runtime.DefaultRuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsRepository
import com.aymanelbanhawy.editor.core.scan.DefaultScanImportService
import com.aymanelbanhawy.editor.core.scan.ScanImportService
import com.aymanelbanhawy.editor.core.search.DefaultDocumentSearchService
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.PdfBoxTextExtractionService
import com.aymanelbanhawy.editor.core.search.RoomSearchIndexStore
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.DefaultSecurityRepository
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import com.aymanelbanhawy.editor.core.session.DefaultEditorSession
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.work.CleanupExportsWorker
import com.aymanelbanhawy.editor.core.work.ConnectorTransferScheduler
import com.aymanelbanhawy.editor.core.work.SearchIndexScheduler
import com.aymanelbanhawy.editor.core.work.TelemetryUploadScheduler
import com.aymanelbanhawy.editor.core.work.WorkManagerAutosaveScheduler
import com.aymanelbanhawy.editor.core.work.WorkManagerCollaborationSyncScheduler
import com.aymanelbanhawy.editor.core.workflow.DefaultWorkflowRepository
import com.aymanelbanhawy.editor.core.workflow.DocumentConversionRuntime
import com.aymanelbanhawy.editor.core.workflow.OpenXmlDocxDocumentConversionProvider
import com.aymanelbanhawy.editor.core.workflow.WorkflowRepository
import com.aymanelbanhawy.editor.core.write.PdfBoxWriteEngine
import com.aymanelbanhawy.editor.core.write.RoomMutationInvalidationHooks
import kotlinx.serialization.json.Json

class EditorCoreContainer(
    context: Context,
) {
    val appContext: Context = context.applicationContext
    private val database: PdfWorkspaceDatabase = createEditorCoreDatabase(appContext)
    private val workManager: WorkManager = WorkManager.getInstance(appContext)
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }
    private val ocrSessionStore = OcrSessionStore(json, database.ocrJobDao())
    private val legacyEditCompatibilityBridge = FileLegacyEditCompatibilityBridge(json)
    private val legacyAnnotationCompatibilityStore = FileLegacyAnnotationCompatibilityStore(json)
    private val secureFileCipher = AndroidSecureFileCipher(appContext)
    private val enterpriseCredentialStore = EnterpriseCredentialStore(appContext, json)
    private val enterpriseRemoteRegistry = EnterpriseRemoteRegistry(appContext, json)
    val runtimeDiagnosticsRepository: RuntimeDiagnosticsRepository = DefaultRuntimeDiagnosticsRepository(
        context = appContext,
        breadcrumbDao = database.runtimeBreadcrumbDao(),
        draftDao = database.draftDao(),
        ocrJobDao = database.ocrJobDao(),
        syncQueueDao = database.syncQueueDao(),
        connectorTransferJobDao = database.connectorTransferJobDao(),
        connectorAccountDao = database.connectorAccountDao(),
        json = json,
    )
    val digitalSignatureService: DigitalSignatureService = PdfBoxDigitalSignatureService(
        context = appContext,
        signingIdentityDao = database.signingIdentityDao(),
        secureFileCipher = secureFileCipher,
    )
    val enterpriseAdminRepository: EnterpriseAdminRepository = DefaultEnterpriseAdminRepository(
        context = appContext,
        settingsDao = database.enterpriseSettingsDao(),
        telemetryDao = database.telemetryEventDao(),
        credentialStore = enterpriseCredentialStore,
        remoteRegistry = enterpriseRemoteRegistry,
        json = json,
    )
    val securityRepository: SecurityRepository = DefaultSecurityRepository(
        context = appContext,
        appLockSettingsDao = database.appLockSettingsDao(),
        documentSecurityDao = database.documentSecurityDao(),
        auditTrailEventDao = database.auditTrailEventDao(),
        json = json,
        digitalSignatureService = digitalSignatureService,
    )
    private val searchIndexStore = RoomSearchIndexStore(
        searchIndexDao = database.searchIndexDao(),
        recentSearchDao = database.recentSearchDao(),
        json = json,
    )
    private val extractionService = PdfBoxTextExtractionService()
    val documentSearchService: DocumentSearchService = DefaultDocumentSearchService(
        store = searchIndexStore,
        extractionService = extractionService,
        ocrSessionStore = ocrSessionStore,
        diagnosticsRepository = runtimeDiagnosticsRepository,
    )
    val ocrJobPipeline: OcrJobPipeline = OcrJobPipeline(
        ocrJobDao = database.ocrJobDao(),
        ocrSettingsDao = database.ocrSettingsDao(),
        searchService = documentSearchService,
        workManager = workManager,
        json = json,
        ocrSessionStore = ocrSessionStore,
        ocrPdfWriter = PdfBoxOcrPdfWriter(),
        diagnosticsRepository = runtimeDiagnosticsRepository,
    )
    val formSupportRepository: FormSupportRepository = DefaultFormSupportRepository(
        context = appContext,
        profileDao = database.formProfileDao(),
        savedSignatureDao = database.savedSignatureDao(),
    )
    val documentRepository: DocumentRepository = DefaultDocumentRepository(
        context = appContext,
        recentDocumentDao = database.recentDocumentDao(),
        draftDao = database.draftDao(),
        editHistoryMetadataDao = database.editHistoryMetadataDao(),
        documentSecurityDao = database.documentSecurityDao(),
        pdfWriteEngine = PdfBoxWriteEngine(
            context = appContext,
            invalidationHooks = RoomMutationInvalidationHooks(appContext, database.searchIndexDao()),
        ),
        secureFileCipher = secureFileCipher,
        ocrSessionStore = ocrSessionStore,
        legacyEditCompatibilityBridge = legacyEditCompatibilityBridge,
        legacyAnnotationCompatibilityStore = legacyAnnotationCompatibilityStore,
        json = json,
        digitalSignatureService = digitalSignatureService,
        securityRepository = securityRepository,
        diagnosticsRepository = runtimeDiagnosticsRepository,
    )
    val connectorRepository: ConnectorRepository = DefaultConnectorRepository(
        context = appContext,
        accountDao = database.connectorAccountDao(),
        remoteDocumentMetadataDao = database.remoteDocumentMetadataDao(),
        transferJobDao = database.connectorTransferJobDao(),
        documentRepository = documentRepository,
        enterpriseAdminRepository = enterpriseAdminRepository,
        securityRepository = securityRepository,
        secureFileCipher = secureFileCipher,
        json = json,
    )
    val pageThumbnailRepository: PageThumbnailRepository = DefaultPageThumbnailRepository(appContext, runtimeDiagnosticsRepository)
    val searchIndexScheduler: SearchIndexScheduler = SearchIndexScheduler(workManager)
    val scanImportService: ScanImportService = DefaultScanImportService(appContext, ocrJobPipeline)
    private val documentConversionRuntime = DocumentConversionRuntime(listOf(OpenXmlDocxDocumentConversionProvider(appContext)))
    private val collaborationSyncScheduler = WorkManagerCollaborationSyncScheduler(workManager)
    private val collaborationCredentialStore = CollaborationCredentialStore(appContext, json)
    private val collaborationRemoteRegistry = CollaborationRemoteRegistry(
        context = appContext,
        enterpriseAdminRepository = enterpriseAdminRepository,
        credentialStore = collaborationCredentialStore,
        json = json,
    )
    val workflowRepository: WorkflowRepository = DefaultWorkflowRepository(
        context = appContext,
        compareReportDao = database.compareReportDao(),
        formTemplateDao = database.formTemplateDao(),
        workflowRequestDao = database.workflowRequestDao(),
        activityEventDao = database.activityEventDao(),
        enterpriseAdminRepository = enterpriseAdminRepository,
        extractionService = extractionService,
        ocrSessionStore = ocrSessionStore,
        documentRepository = documentRepository,
        scanImportService = scanImportService,
        conversionRuntime = documentConversionRuntime,
        json = json,
    )
    val collaborationRepository: CollaborationRepository = DefaultCollaborationRepository(
        context = appContext,
        shareLinkDao = database.shareLinkDao(),
        reviewThreadDao = database.reviewThreadDao(),
        reviewCommentDao = database.reviewCommentDao(),
        versionSnapshotDao = database.versionSnapshotDao(),
        activityEventDao = database.activityEventDao(),
        syncQueueDao = database.syncQueueDao(),
        remoteRegistry = collaborationRemoteRegistry,
        conflictResolver = CollaborationConflictResolver(),
        enterpriseAdminRepository = enterpriseAdminRepository,
        syncScheduler = collaborationSyncScheduler,
        json = json,
    )
    val coreMigrationRepository: CoreMigrationRepository = DefaultCoreMigrationRepository(
        context = appContext,
        draftDao = database.draftDao(),
        ocrJobDao = database.ocrJobDao(),
        searchIndexDao = database.searchIndexDao(),
        syncQueueDao = database.syncQueueDao(),
        diagnosticsRepository = runtimeDiagnosticsRepository,
        json = json,
        legacyEditCompatibilityBridge = legacyEditCompatibilityBridge,
    )
    private val autosaveScheduler = WorkManagerAutosaveScheduler(documentRepository, workManager)
    private val telemetryUploadScheduler = TelemetryUploadScheduler(workManager)
    private val connectorTransferScheduler = ConnectorTransferScheduler(workManager)

    init {
        CleanupExportsWorker.enqueue(workManager)
        telemetryUploadScheduler.enqueue()
        connectorTransferScheduler.enqueue()
    }

    fun newSession(): EditorSession = DefaultEditorSession(documentRepository, autosaveScheduler)

    fun recentDocumentDao() = database.recentDocumentDao()
}






















