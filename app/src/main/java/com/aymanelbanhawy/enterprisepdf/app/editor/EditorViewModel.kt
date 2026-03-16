package com.aymanelbanhawy.enterprisepdf.app.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.BitmapFactory
import android.net.Uri
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AssistantAudioUiState
import com.aymanelbanhawy.aiassistant.core.AssistantAvailability
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.ReadAloudProgress
import com.aymanelbanhawy.aiassistant.core.ReadAloudRequest
import com.aymanelbanhawy.aiassistant.core.ReadAloudStatus
import com.aymanelbanhawy.aiassistant.core.SpeechCaptureEvent
import com.aymanelbanhawy.aiassistant.core.VoiceCaptureStatus
import com.aymanelbanhawy.aiassistant.core.readAloudPaused
import com.aymanelbanhawy.aiassistant.core.readAloudStopped
import com.aymanelbanhawy.aiassistant.core.reduceReadAloudEvent
import com.aymanelbanhawy.aiassistant.core.voiceCaptureCancelled
import com.aymanelbanhawy.aiassistant.core.voiceCaptureStopped
import com.aymanelbanhawy.aiassistant.core.reduceVoiceCaptureEvent
import com.aymanelbanhawy.aiassistant.core.beginVoiceCapture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventModel
import com.aymanelbanhawy.editor.core.collaboration.ActivityEventType
import com.aymanelbanhawy.editor.core.collaboration.ReviewFilterModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadModel
import com.aymanelbanhawy.editor.core.collaboration.ReviewThreadState
import com.aymanelbanhawy.editor.core.collaboration.ShareLinkModel
import com.aymanelbanhawy.editor.core.collaboration.SharePermission
import com.aymanelbanhawy.editor.core.collaboration.VersionSnapshotModel
import com.aymanelbanhawy.editor.core.collaboration.VoiceCommentAttachmentModel
import com.aymanelbanhawy.editor.core.command.AddAnnotationCommand
import com.aymanelbanhawy.editor.core.command.AddPageEditCommand
import com.aymanelbanhawy.editor.core.command.BatchRotatePagesCommand
import com.aymanelbanhawy.editor.core.command.DeleteAnnotationCommand
import com.aymanelbanhawy.editor.core.command.DeletePageEditCommand
import com.aymanelbanhawy.editor.core.command.DeletePagesCommand
import com.aymanelbanhawy.editor.core.command.DuplicateAnnotationCommand
import com.aymanelbanhawy.editor.core.command.DuplicatePagesCommand
import com.aymanelbanhawy.editor.core.command.ExtractPagesCommand
import com.aymanelbanhawy.editor.core.command.InsertBlankPageCommand
import com.aymanelbanhawy.editor.core.command.InsertImagePageCommand
import com.aymanelbanhawy.editor.core.command.MergePagesCommand
import com.aymanelbanhawy.editor.core.command.ReorderPagesCommand
import com.aymanelbanhawy.editor.core.command.ReplaceSecurityDocumentCommand
import com.aymanelbanhawy.editor.core.command.ReplaceFormDocumentCommand
import com.aymanelbanhawy.editor.core.command.ReplaceImageAssetCommand
import com.aymanelbanhawy.editor.core.command.RotatePageCommand
import com.aymanelbanhawy.editor.core.command.UpdateAnnotationCommand
import com.aymanelbanhawy.editor.core.command.UpdateFormFieldCommand
import com.aymanelbanhawy.editor.core.command.UpdatePageEditCommand
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountDraft
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorSaveRequest
import com.aymanelbanhawy.editor.core.connectors.ConnectorTransferJobModel
import com.aymanelbanhawy.editor.core.connectors.SaveDestinationMode
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementEngine
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryCategory
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.enterprise.newTelemetryEvent
import com.aymanelbanhawy.editor.core.forms.FormFieldModel
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.forms.FormProfileModel
import com.aymanelbanhawy.editor.core.forms.SavedSignatureModel
import com.aymanelbanhawy.editor.core.forms.SignatureCapture
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.forms.SignatureVerificationStatus
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.EditorSessionState
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel
import com.aymanelbanhawy.editor.core.model.duplicated
import com.aymanelbanhawy.editor.core.ocr.OcrJobSummary
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.ocr.OcrJobStatus
import com.aymanelbanhawy.editor.core.organize.SplitMode
import com.aymanelbanhawy.editor.core.organize.SplitRequest
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.search.IndexingPolicy
import com.aymanelbanhawy.editor.core.search.OutlineItem
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.aymanelbanhawy.editor.core.security.AppLockReason
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AppLockStateModel
import com.aymanelbanhawy.editor.core.security.AuditEventType
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.DocumentPermissionModel
import com.aymanelbanhawy.editor.core.security.MetadataScrubOptionsModel
import com.aymanelbanhawy.editor.core.security.RedactionMarkModel
import com.aymanelbanhawy.editor.core.security.RedactionStatus
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.TenantPolicyHooksModel
import com.aymanelbanhawy.editor.core.security.WatermarkModel
import com.aymanelbanhawy.editor.core.workflow.CompareMarkerModel
import com.aymanelbanhawy.editor.core.workflow.CompareReportModel
import com.aymanelbanhawy.editor.core.workflow.ExportImageFormat
import com.aymanelbanhawy.editor.core.workflow.PdfOptimizationPreset
import com.aymanelbanhawy.editor.core.workflow.FormTemplateModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowRecipientModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowRecipientRole
import com.aymanelbanhawy.editor.core.workflow.WorkflowRequestModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowResponseModel
import com.aymanelbanhawy.editor.core.workflow.WorkflowRequestStatus
import com.aymanelbanhawy.editor.core.workflow.WorkflowStateModel
import com.aymanelbanhawy.editor.core.session.EditorSession
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.editor.core.runtime.RuntimeLogLevel
import com.aymanelbanhawy.editor.core.runtime.RuntimeEventCategory
import com.aymanelbanhawy.editor.core.runtime.RuntimeDiagnosticsSnapshot
import com.aymanelbanhawy.enterprisepdf.app.AppContainer
import com.aymanelbanhawy.enterprisepdf.app.RecentDocumentSummary
import com.aymanelbanhawy.enterprisepdf.app.audio.resolveAudioFeatureCapabilities
import com.aymanelbanhawy.enterprisepdf.app.open.PendingPdfOpenRequest
import com.aymanelbanhawy.enterprisepdf.app.open.PdfOpenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class WorkspacePanel {
    Annotate,
    Forms,
    Sign,
    Search,
    Assistant,
    Review,
    Activity,
    Protect,
    Settings,
    Diagnostics,
}

data class ConnectorExportUiState(
    val destinationMode: SaveDestinationMode,
    val exportMode: AnnotationExportMode,
    val selectedAccountId: String = "",
    val remotePath: String = "",
    val displayName: String = "document.pdf",
)

data class RecentDocumentUiState(
    val sourceKey: String,
    val displayName: String,
    val sourceType: String,
    val workingCopyPath: String,
)

data class EditorUiState(
    val session: EditorSessionState = EditorSessionState(),
    val activeTool: AnnotationTool = AnnotationTool.Select,
    val activePanel: WorkspacePanel = WorkspacePanel.Annotate,
    val annotationSidebarVisible: Boolean = true,
    val organizeVisible: Boolean = false,
    val selectedPageIndexes: Set<Int> = emptySet(),
    val thumbnails: List<ThumbnailDescriptor> = emptyList(),
    val splitRangeExpression: String = "1-2",
    val formProfiles: List<FormProfileModel> = emptyList(),
    val savedSignatures: List<SavedSignatureModel> = emptyList(),
    val signatureCaptureVisible: Boolean = false,
    val signingFieldName: String? = null,
    val searchQuery: String = "",
    val searchResults: SearchResultSet = SearchResultSet(),
    val assistantState: AssistantUiState = AssistantUiState(),
    val recentSearches: List<String> = emptyList(),
    val outlineItems: List<OutlineItem> = emptyList(),
    val selectedTextSelection: TextSelectionPayload? = null,
    val isSearchIndexing: Boolean = false,
    val scanImportVisible: Boolean = false,
    val scanImportOptions: ScanImportOptions = ScanImportOptions(),
    val ocrJobs: List<OcrJobSummary> = emptyList(),
    val ocrSettings: OcrSettingsModel = OcrSettingsModel(),
    val shareLinks: List<ShareLinkModel> = emptyList(),
    val reviewThreads: List<ReviewThreadModel> = emptyList(),
    val versionSnapshots: List<VersionSnapshotModel> = emptyList(),
    val activityEvents: List<ActivityEventModel> = emptyList(),
    val reviewFilter: ReviewFilterModel = ReviewFilterModel(),
    val pendingSyncCount: Int = 0,
    val appLockSettings: AppLockSettingsModel = AppLockSettingsModel(),
    val appLockState: AppLockStateModel = AppLockStateModel(),
    val securityAuditEvents: List<AuditTrailEventModel> = emptyList(),
    val enterpriseState: EnterpriseAdminStateModel = EnterpriseAdminStateModel(),
    val entitlements: EntitlementStateModel = EntitlementStateModel(LicensePlan.Free, emptySet()),
    val telemetryEvents: List<TelemetryEventModel> = emptyList(),
    val diagnosticsBundleCount: Int = 0,
    val connectorAccounts: List<ConnectorAccountModel> = emptyList(),
    val connectorJobs: List<ConnectorTransferJobModel> = emptyList(),
    val connectorExportDialog: ConnectorExportUiState? = null,
    val runtimeDiagnostics: RuntimeDiagnosticsSnapshot = RuntimeDiagnosticsSnapshot(),
    val workflowState: WorkflowStateModel = WorkflowStateModel(),
    val pendingThreadVoiceComment: VoiceCommentAttachmentModel? = null,
    val pendingReplyVoiceComments: Map<String, VoiceCommentAttachmentModel> = emptyMap(),
    val activeVoiceCommentPlaybackId: String? = null,
    val recentDocuments: List<RecentDocumentUiState> = emptyList(),
    val lastDocumentOpenDiagnostic: String? = null,
) {
    val selectedAnnotation: AnnotationModel?
        get() = session.document?.pages?.flatMap { it.annotations }?.firstOrNull { it.id in session.selection.selectedAnnotationIds }

    val currentPageAnnotations: List<AnnotationModel>
        get() = session.document?.pages?.getOrNull(session.selection.selectedPageIndex)?.annotations.orEmpty()

    val selectedFormField: FormFieldModel?
        get() = session.document?.formDocument?.field(session.selection.selectedFormFieldName.orEmpty())

    val currentPageFormFields: List<FormFieldModel>
        get() = session.document?.formDocument?.fields?.filter { it.pageIndex == session.selection.selectedPageIndex }.orEmpty()

    val selectedEditObject: PageEditModel?
        get() = session.document?.pages?.flatMap { it.editObjects }?.firstOrNull { it.id == session.selection.selectedEditId }

    val currentPageEditObjects: List<PageEditModel>
        get() = session.document?.pages?.getOrNull(session.selection.selectedPageIndex)?.editObjects.orEmpty()

    val selectedSearchHit
        get() = searchResults.selectedHit
}

class EditorViewModel(
    private val session: EditorSession,
    private val repository: DocumentRepository,
    private val appContainer: AppContainer,
) : ViewModel() {
    private val activeTool = MutableStateFlow(AnnotationTool.Select)
    private val activePanel = MutableStateFlow(WorkspacePanel.Annotate)
    private val sidebarVisible = MutableStateFlow(true)
    private val organizeVisible = MutableStateFlow(false)
    private val selectedPageIndexes = MutableStateFlow(emptySet<Int>())
    private val thumbnails = MutableStateFlow(emptyList<ThumbnailDescriptor>())
    private val splitRangeExpression = MutableStateFlow("1-2")
    private val formProfiles = MutableStateFlow(emptyList<FormProfileModel>())
    private val savedSignatures = MutableStateFlow(emptyList<SavedSignatureModel>())
    private val signatureCaptureVisible = MutableStateFlow(false)
    private val signingFieldName = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val searchResults = MutableStateFlow(SearchResultSet())
    private val recentSearches = MutableStateFlow(emptyList<String>())
    private val outlineItems = MutableStateFlow(emptyList<OutlineItem>())
    private val selectedTextSelection = MutableStateFlow<TextSelectionPayload?>(null)
    private val isSearchIndexing = MutableStateFlow(false)
    private val scanImportVisible = MutableStateFlow(false)
    private val scanImportOptions = MutableStateFlow(ScanImportOptions())
    private val ocrJobs = MutableStateFlow(emptyList<OcrJobSummary>())
    private val ocrSettings = MutableStateFlow(OcrSettingsModel())
    private val shareLinks = MutableStateFlow(emptyList<ShareLinkModel>())
    private val reviewThreads = MutableStateFlow(emptyList<ReviewThreadModel>())
    private val versionSnapshots = MutableStateFlow(emptyList<VersionSnapshotModel>())
    private val activityEvents = MutableStateFlow(emptyList<ActivityEventModel>())
    private val reviewFilter = MutableStateFlow(ReviewFilterModel())
    private val pendingSyncCount = MutableStateFlow(0)
    private val appLockSettings = MutableStateFlow(AppLockSettingsModel())
    private val appLockState = MutableStateFlow(AppLockStateModel())
    private val securityAuditEvents = MutableStateFlow(emptyList<AuditTrailEventModel>())
    private val enterpriseState = MutableStateFlow(EnterpriseAdminStateModel())
    private val entitlements = MutableStateFlow(EntitlementStateModel(LicensePlan.Free, emptySet()))
    private val telemetryEvents = MutableStateFlow(emptyList<TelemetryEventModel>())
    private val diagnosticsBundleCount = MutableStateFlow(0)
    private val connectorAccounts = MutableStateFlow(emptyList<ConnectorAccountModel>())
    private val connectorJobs = MutableStateFlow(emptyList<ConnectorTransferJobModel>())
    private val connectorExportDialog = MutableStateFlow<ConnectorExportUiState?>(null)
    private val runtimeDiagnostics = MutableStateFlow(RuntimeDiagnosticsSnapshot())
    private val workflowState = MutableStateFlow(WorkflowStateModel())
    private val assistantState = MutableStateFlow(AssistantUiState())
    private val assistantAudioState = MutableStateFlow(AssistantAudioUiState())
    private val pendingThreadVoiceComment = MutableStateFlow<VoiceCommentAttachmentModel?>(null)
    private val pendingReplyVoiceComments = MutableStateFlow<Map<String, VoiceCommentAttachmentModel>>(emptyMap())
    private val activeVoiceCommentPlaybackId = MutableStateFlow<String?>(null)
    private val recentDocuments = MutableStateFlow(emptyList<RecentDocumentUiState>())
    private val lastDocumentOpenDiagnostic = MutableStateFlow<String?>(null)
    private val localEvents = MutableSharedFlow<EditorSessionEvent>(extraBufferCapacity = 16)
    private val indexingPolicy = IndexingPolicy()
    private var ocrObservationJob: Job? = null
    private var voiceCaptureJob: Job? = null
    private var readAloudJob: Job? = null
    private var activeReadAloudSession: ActiveReadAloudSession? = null
    private var lastSpokenAssistantResultEpochMillis: Long? = null
    private var suppressReadAloudStopUpdate = false
    private var documentInitialized = false

    val uiState: StateFlow<EditorUiState> = combine(
        session.state,
        activeTool,
        activePanel,
        sidebarVisible,
        organizeVisible,
        selectedPageIndexes,
        thumbnails,
        splitRangeExpression,
        formProfiles,
        savedSignatures,
        signatureCaptureVisible,
        signingFieldName,
        searchQuery,
        searchResults,
        assistantState,
        assistantAudioState,
        recentSearches,
        outlineItems,
        selectedTextSelection,
        isSearchIndexing,
        scanImportVisible,
        scanImportOptions,
        shareLinks,
        reviewThreads,
        versionSnapshots,
        activityEvents,
        reviewFilter,
        pendingSyncCount,
        appLockSettings,
        appLockState,
        securityAuditEvents,
        enterpriseState,
        entitlements,
        telemetryEvents,
        diagnosticsBundleCount,
        connectorAccounts,
        connectorJobs,
        connectorExportDialog,
        runtimeDiagnostics,
        workflowState,
        pendingThreadVoiceComment,
        pendingReplyVoiceComments,
        activeVoiceCommentPlaybackId,
        recentDocuments,
        lastDocumentOpenDiagnostic,
    ) { values ->
        EditorUiState(
            session = values[0] as EditorSessionState,
            activeTool = values[1] as AnnotationTool,
            activePanel = values[2] as WorkspacePanel,
            annotationSidebarVisible = values[3] as Boolean,
            organizeVisible = values[4] as Boolean,
            selectedPageIndexes = values[5] as Set<Int>,
            thumbnails = values[6] as List<ThumbnailDescriptor>,
            splitRangeExpression = values[7] as String,
            formProfiles = values[8] as List<FormProfileModel>,
            savedSignatures = values[9] as List<SavedSignatureModel>,
            signatureCaptureVisible = values[10] as Boolean,
            signingFieldName = values[11] as String?,
            searchQuery = values[12] as String,
            searchResults = values[13] as SearchResultSet,
            assistantState = (values[14] as AssistantUiState).copy(audio = values[15] as AssistantAudioUiState),
            recentSearches = values[16] as List<String>,
            outlineItems = values[17] as List<OutlineItem>,
            selectedTextSelection = values[18] as TextSelectionPayload?,
            isSearchIndexing = values[19] as Boolean,
            scanImportVisible = values[20] as Boolean,
            scanImportOptions = values[21] as ScanImportOptions,
            shareLinks = values[22] as List<ShareLinkModel>,
            reviewThreads = values[23] as List<ReviewThreadModel>,
            versionSnapshots = values[24] as List<VersionSnapshotModel>,
            activityEvents = values[25] as List<ActivityEventModel>,
            reviewFilter = values[26] as ReviewFilterModel,
            pendingSyncCount = values[27] as Int,
            appLockSettings = values[28] as AppLockSettingsModel,
            appLockState = values[29] as AppLockStateModel,
            securityAuditEvents = values[30] as List<AuditTrailEventModel>,
            enterpriseState = values[31] as EnterpriseAdminStateModel,
            entitlements = values[32] as EntitlementStateModel,
            telemetryEvents = values[33] as List<TelemetryEventModel>,
            diagnosticsBundleCount = values[34] as Int,
            connectorAccounts = values[35] as List<ConnectorAccountModel>,
            connectorJobs = values[36] as List<ConnectorTransferJobModel>,
            connectorExportDialog = values[37] as ConnectorExportUiState?,
            runtimeDiagnostics = values[38] as RuntimeDiagnosticsSnapshot,
            workflowState = values[39] as WorkflowStateModel,
            pendingThreadVoiceComment = values[40] as VoiceCommentAttachmentModel?,
            pendingReplyVoiceComments = values[41] as Map<String, VoiceCommentAttachmentModel>,
            activeVoiceCommentPlaybackId = values[42] as String?,
            recentDocuments = values[43] as List<RecentDocumentUiState>,
            lastDocumentOpenDiagnostic = values[44] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorUiState())

    val events: Flow<EditorSessionEvent> = merge(session.events, localEvents)

    init {
        observeDocumentOcrState()
        viewModelScope.launch {
            ocrSettings.value = appContainer.ocrJobPipeline.loadSettings()
            scanImportOptions.value = scanImportOptions.value.copy(ocrSettings = ocrSettings.value)
            runCatching { refreshEnterpriseData() }
        }
        viewModelScope.launch {
            appContainer.observeRecentDocuments().collectLatest { documents ->
                recentDocuments.value = documents.map { it.toUiState() }
            }
        }
    }

    fun onDocumentLoaded(pageCount: Int) = session.onDocumentLoaded(pageCount)
    fun onPageChanged(page: Int, pageCount: Int) = session.onPageChanged(page, pageCount)

    fun initializeDocument(initialOpenRequest: PendingPdfOpenRequest?) {
        if (documentInitialized) return
        documentInitialized = true
        viewModelScope.launch {
            val resolved = initialOpenRequest ?: PendingPdfOpenRequest(
                request = appContainer.seedDocumentRequest(),
                source = PdfOpenSource.SampleSeed,
                activeUri = "asset://sample.pdf",
                displayName = "sample.pdf",
            )
            openDocumentRequest(
                pendingRequest = resolved,
                userMessage = when (resolved.source) {
                    PdfOpenSource.SampleSeed -> null
                    PdfOpenSource.SafPicker -> "Opened ${resolved.displayName} from Files"
                    PdfOpenSource.ExternalViewIntent -> "Opened ${resolved.displayName} from external PDF view intent"
                    PdfOpenSource.ExternalSendIntent -> "Opened ${resolved.displayName} from external PDF share intent"
                    PdfOpenSource.RecentDocument -> "Opened recent PDF ${resolved.displayName}"
                    PdfOpenSource.Connector -> "Opened ${resolved.displayName} from connector"
                },
                fallbackToSampleOnFailure = resolved.source != PdfOpenSource.SampleSeed,
            )
        }
    }

    fun openIncomingDocument(pendingRequest: PendingPdfOpenRequest) {
        viewModelScope.launch {
            openDocumentRequest(
                pendingRequest = pendingRequest,
                userMessage = when (pendingRequest.source) {
                    PdfOpenSource.SafPicker -> "Opened ${pendingRequest.displayName} from Files"
                    PdfOpenSource.ExternalViewIntent -> "Opened ${pendingRequest.displayName} from external PDF view intent"
                    PdfOpenSource.ExternalSendIntent -> "Opened ${pendingRequest.displayName} from external PDF share intent"
                    PdfOpenSource.RecentDocument -> "Opened recent PDF ${pendingRequest.displayName}"
                    PdfOpenSource.Connector -> "Opened ${pendingRequest.displayName} from connector"
                    PdfOpenSource.SampleSeed -> null
                },
            )
        }
    }

    fun openPdfFromSafSelection(uriString: String, displayName: String) {
        openIncomingDocument(
            PendingPdfOpenRequest(
                request = OpenDocumentRequest.FromUri(uriString = uriString, displayName = displayName),
                source = PdfOpenSource.SafPicker,
                activeUri = uriString,
                displayName = displayName,
            ),
        )
    }

    fun showUserMessage(message: String) {
        viewModelScope.launch {
            localEvents.emit(EditorSessionEvent.UserMessage(message))
        }
    }

    fun openRecentDocument(document: RecentDocumentUiState) {
        val file = File(document.workingCopyPath)
        if (!file.exists()) {
            showUserMessage("Recent PDF ${document.displayName} is no longer available")
            return
        }
        openIncomingDocument(
            PendingPdfOpenRequest(
                request = OpenDocumentRequest.FromFile(
                    absolutePath = file.absolutePath,
                    displayNameOverride = document.displayName,
                ),
                source = PdfOpenSource.RecentDocument,
                activeUri = file.toURI().toString(),
                displayName = document.displayName,
            ),
        )
    }

    fun onActionSelected(action: EditorAction) {
        if (action != EditorAction.Organize) {
            organizeVisible.value = false
        }

        when (action) {
            EditorAction.Organize -> {
                organizeVisible.value = true
                activePanel.value = WorkspacePanel.Annotate
                sidebarVisible.value = false
                refreshThumbnailsAsync()
            }
            EditorAction.Forms -> {
                activePanel.value = WorkspacePanel.Forms
                sidebarVisible.value = true
                viewModelScope.launch { refreshFormSupportData() }
                session.onActionSelected(action)
            }
            EditorAction.Sign -> {
                activePanel.value = WorkspacePanel.Sign
                sidebarVisible.value = true
                viewModelScope.launch { refreshFormSupportData() }
                session.onActionSelected(action)
            }
            EditorAction.Annotate -> {
                activePanel.value = WorkspacePanel.Annotate
                sidebarVisible.value = true
                session.onActionSelected(action)
            }
            EditorAction.Search -> {
                activePanel.value = WorkspacePanel.Search
                sidebarVisible.value = true
                viewModelScope.launch { refreshSearchSupportData(forceSync = false) }
            }
            EditorAction.Assistant -> {
                activePanel.value = WorkspacePanel.Assistant
                sidebarVisible.value = true
                viewModelScope.launch {
                    refreshEnterpriseData()
                    refreshAssistantData()
                }
                session.onActionSelected(action)
            }
            EditorAction.Review -> {
                activePanel.value = WorkspacePanel.Review
                sidebarVisible.value = true
                viewModelScope.launch { refreshCollaborationData() }
            }
            EditorAction.Activity -> {
                activePanel.value = WorkspacePanel.Activity
                sidebarVisible.value = true
                viewModelScope.launch { refreshCollaborationData() }
            }
            EditorAction.Protect -> {
                activePanel.value = WorkspacePanel.Protect
                sidebarVisible.value = true
                viewModelScope.launch { refreshSecurityData() }
                session.onActionSelected(action)
            }
            EditorAction.Settings -> {
                activePanel.value = WorkspacePanel.Settings
                sidebarVisible.value = true
                viewModelScope.launch { refreshEnterpriseData(); refreshConnectorData() }
                session.onActionSelected(action)
            }
            EditorAction.Diagnostics -> {
                activePanel.value = WorkspacePanel.Diagnostics
                sidebarVisible.value = true
                viewModelScope.launch { refreshDiagnosticsData() }
            }
            EditorAction.Share -> {
                val document = session.state.value.document ?: return
                val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Share)
                if (decision.allowed) {
                    session.onActionSelected(action)
                } else {
                    localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Share blocked"))
                    viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Share blocked", mapOf("action" to RestrictedAction.Share.name)) }
                }
            }
            else -> session.onActionSelected(action)
        }
    }

    fun showEditor() {
        organizeVisible.value = false
        sidebarVisible.value = true
    }

    fun showOrganize() {
        organizeVisible.value = true
        activePanel.value = WorkspacePanel.Annotate
        sidebarVisible.value = false
        refreshThumbnailsAsync()
    }

    fun onToolSelected(tool: AnnotationTool) {
        organizeVisible.value = false
        activeTool.value = tool
        activePanel.value = WorkspacePanel.Annotate
        sidebarVisible.value = true
    }
    fun toggleAnnotationSidebar() { sidebarVisible.value = !sidebarVisible.value }
    fun updateSearchQuery(value: String) { searchQuery.value = value }
    fun updateAssistantPrompt(value: String) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updatePrompt(value)
            syncAssistantStateFromRepository()
        }
    }

    fun updateAssistantProviderDraft(draft: AiProviderDraft) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updateProviderDraft(draft)
            syncAssistantStateFromRepository()
        }
    }

    fun saveAssistantProvider() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.saveProviderDraft(entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
        }
    }

    fun refreshAssistantProviders() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.refreshProviderCatalog(entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
        }
    }

    fun testAssistantConnection() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.testProviderConnection(entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
        }
    }

    fun cancelAssistantRequest() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.cancelActiveRequest()
            stopReadAloud(clearSession = true)
            syncAssistantStateFromRepository()
        }
    }

    fun askPdf() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.askPdf(document, assistantState.value.prompt.ifBlank { "What should I know about this PDF?" }, selectedTextSelection.value, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun summarizeDocumentWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.summarizeDocument(document, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun summarizeCurrentPageWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.summarizePage(document, session.state.value.selection.selectedPageIndex, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun extractActionItemsWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.extractActionItems(document, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun explainSelectionWithAi() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.explainSelection(document, selectedTextSelection.value, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun runAiSemanticSearch() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.semanticSearch(document, assistantState.value.prompt.ifBlank { searchQuery.value }, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun askWorkspaceWithAi() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.askAcrossWorkspace(session.state.value.document, assistantState.value.prompt.ifBlank { "What are the key findings across these documents?" }, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun summarizeWorkspaceWithAi() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.summarizeWorkspace(session.state.value.document, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun compareWorkspaceWithAi() {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.compareAndSummarizeWorkspace(session.state.value.document, entitlements.value, enterpriseState.value)
            syncAssistantStateFromRepository()
            speakLatestAssistantResultIfEligible()
        }
    }

    fun pinCurrentDocumentToAiWorkspace() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.aiAssistantRepository.pinDocument(document)
            syncAssistantStateFromRepository()
        }
    }

    fun toggleAiWorkspaceDocument(documentKey: String, selected: Boolean) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.toggleWorkspaceDocument(documentKey, selected)
            syncAssistantStateFromRepository()
        }
    }

    fun unpinAiWorkspaceDocument(documentKey: String) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.unpinDocument(documentKey)
            syncAssistantStateFromRepository()
        }
    }

    fun saveAiWorkspaceDocumentSet(title: String) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.saveWorkspaceDocumentSet(title)
            syncAssistantStateFromRepository()
        }
    }

    fun updateAssistantPrivacyMode(mode: AssistantPrivacyMode) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updateSettings(assistantState.value.settings.copy(privacyMode = mode))
            syncAssistantStateFromRepository()
        }
    }

    fun setAssistantAudioEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appContainer.aiAssistantRepository.updateSettings(
                assistantState.value.settings.copy(
                    spokenResponsesEnabled = enabled,
                    readAloudEnabled = enabled,
                    voicePromptCaptureEnabled = enabled,
                ),
            )
            syncAssistantStateFromRepository()
            refreshAssistantAudioAvailability()
        }
    }

    fun startVoicePromptCapture() {
        val capabilities = currentAudioCapabilities()
        if (!capabilities.voicePromptCaptureAllowed) {
            assistantAudioState.value = assistantAudioState.value.copy(enabled = false, reason = capabilities.voicePromptReason())
            syncAssistantStateFromRepository()
            return
        }
        voiceCaptureJob?.cancel()
        voiceCaptureJob = viewModelScope.launch {
            pauseReadAloudForInterruption()
            assistantAudioState.value = assistantAudioState.value.beginVoiceCapture()
            syncAssistantStateFromRepository()
            appContainer.speechCaptureEngine.startCapture().collect { event ->
                assistantAudioState.value = assistantAudioState.value.reduceVoiceCaptureEvent(event)
                if (event is SpeechCaptureEvent.FinalResult) {
                    updateAssistantPrompt(event.transcript)
                    askPdf()
                }
                syncAssistantStateFromRepository()
            }
        }
    }

    fun stopVoicePromptCapture() {
        appContainer.speechCaptureEngine.stopCapture()
        assistantAudioState.value = assistantAudioState.value.voiceCaptureStopped()
        syncAssistantStateFromRepository()
    }

    fun cancelVoicePromptCapture() {
        appContainer.speechCaptureEngine.cancelCapture()
        assistantAudioState.value = assistantAudioState.value.voiceCaptureCancelled()
        syncAssistantStateFromRepository()
    }

    fun readCurrentPageAloud() {
        val document = session.state.value.document ?: return
        val pageIndex = session.state.value.selection.selectedPageIndex
        viewModelScope.launch {
            val indexedPage = runCatching { appContainer.documentSearchService.ensureIndex(document) }
                .getOrNull()
                ?.firstOrNull { it.pageIndex == pageIndex }
            val pageText = indexedPage
                ?.blocks
                ?.joinToString(separator = " ") { it.text }
                ?.takeIf { it.isNotBlank() }
                ?: searchResults.value.selectedHit?.matchText
                ?: return@launch
            startReadAloud("Page ${pageIndex + 1}", pageText)
        }
    }

    fun readSelectedTextAloud() {
        val selection = selectedTextSelection.value ?: return
        startReadAloud("Selection", selection.text)
    }

    fun pauseReadAloud() {
        val session = activeReadAloudSession ?: return
        val currentIndex = assistantAudioState.value.readAloud.progress.currentIndex.takeIf { it >= 0 } ?: session.startIndex
        activeReadAloudSession = session.copy(startIndex = currentIndex)
        suppressReadAloudStopUpdate = true
        appContainer.readAloudEngine.stop()
        assistantAudioState.value = assistantAudioState.value.readAloudPaused(
            title = session.title,
            index = currentIndex,
            totalSegments = session.segments.size,
            text = session.segments.getOrElse(currentIndex) { session.segments.lastOrNull().orEmpty() },
        )
        syncAssistantStateFromRepository()
    }

    fun resumeReadAloud() {
        val session = activeReadAloudSession ?: return
        if (assistantAudioState.value.readAloud.status != ReadAloudStatus.Paused) return
        startReadAloudSession(session)
    }

    fun stopReadAloud() {
        stopReadAloud(clearSession = true)
    }

    private fun stopReadAloud(clearSession: Boolean) {
        appContainer.readAloudEngine.stop()
        if (clearSession) {
            activeReadAloudSession = null
        }
        assistantAudioState.value = assistantAudioState.value.readAloudStopped()
        syncAssistantStateFromRepository()
    }

    fun startVoiceCommentForNewThread() {
        val capabilities = currentAudioCapabilities()
        if (!capabilities.voiceCommentsAllowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(capabilities.voiceCommentReason() ?: "Voice comments are disabled by policy"))
            return
        }
        val attachment = runCatching { appContainer.voiceCommentRuntime.startRecording() }.getOrElse {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(it.message ?: "Unable to start recording"))
            return
        }
        pendingThreadVoiceComment.value = VoiceCommentAttachmentModel(
            id = attachment.nameWithoutExtension,
            localFilePath = attachment.absolutePath,
            mimeType = "audio/mp4",
            durationMillis = 0,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
    }

    fun stopVoiceCommentForNewThread() {
        pendingThreadVoiceComment.value = appContainer.voiceCommentRuntime.stopRecording()
    }

    fun cancelVoiceCommentForNewThread() {
        appContainer.voiceCommentRuntime.cancelRecording()
        pendingThreadVoiceComment.value = null
    }

    fun startVoiceCommentReply(threadId: String) {
        val capabilities = currentAudioCapabilities()
        if (!capabilities.voiceCommentsAllowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(capabilities.voiceCommentReason() ?: "Voice comments are disabled by policy"))
            return
        }
        val file = runCatching { appContainer.voiceCommentRuntime.startRecording() }.getOrElse {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(it.message ?: "Unable to start recording"))
            return
        }
        pendingReplyVoiceComments.value = pendingReplyVoiceComments.value + (threadId to VoiceCommentAttachmentModel(
            id = file.nameWithoutExtension,
            localFilePath = file.absolutePath,
            mimeType = "audio/mp4",
            durationMillis = 0,
            createdAtEpochMillis = System.currentTimeMillis(),
        ))
    }

    fun stopVoiceCommentReply(threadId: String) {
        appContainer.voiceCommentRuntime.stopRecording()?.let { attachment ->
            pendingReplyVoiceComments.value = pendingReplyVoiceComments.value + (threadId to attachment)
        }
    }

    fun cancelVoiceCommentReply(threadId: String) {
        appContainer.voiceCommentRuntime.cancelRecording()
        pendingReplyVoiceComments.value = pendingReplyVoiceComments.value - threadId
    }

    fun cancelAllPendingVoiceComments() {
        appContainer.voiceCommentRuntime.cancelRecording()
        pendingThreadVoiceComment.value = null
        pendingReplyVoiceComments.value = emptyMap()
    }

    fun playVoiceComment(commentId: String) {
        val capabilities = currentAudioCapabilities()
        if (!capabilities.voiceCommentsAllowed || !capabilities.speechOutputPolicyEnabled) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(capabilities.voiceCommentReason() ?: capabilities.readAloudReason() ?: "Voice comment playback is unavailable"))
            return
        }
        val comment = reviewThreads.value.flatMap { it.comments }.firstOrNull { it.id == commentId } ?: return
        val attachment = comment.voiceAttachment ?: return
        runCatching { appContainer.voiceCommentRuntime.startPlayback(attachment) }
            .onSuccess { activeVoiceCommentPlaybackId.value = commentId }
            .onFailure { localEvents.tryEmit(EditorSessionEvent.UserMessage(it.message ?: "Unable to play voice comment")) }
    }

    fun stopVoiceCommentPlayback() {
        appContainer.voiceCommentRuntime.stopPlayback()
        activeVoiceCommentPlaybackId.value = null
    }
    fun openAssistantCitation(pageIndex: Int) {
        viewModelScope.launch {
            session.updateSelection(
                session.state.value.selection.copy(
                    selectedPageIndex = pageIndex,
                    selectedAnnotationIds = emptySet(),
                    selectedFormFieldName = null,
                    selectedEditId = null,
                ),
            )
        }
    }
    fun showScanImportDialog() { scanImportVisible.value = true }
    fun dismissScanImportDialog() { scanImportVisible.value = false }
    fun updateScanImportOptions(options: ScanImportOptions) {
        scanImportOptions.value = options
        ocrSettings.value = options.ocrSettings
    }
    fun updateOcrSettings(settings: OcrSettingsModel) {
        ocrSettings.value = settings
        scanImportOptions.value = scanImportOptions.value.copy(ocrSettings = settings)
    }
    fun saveOcrSettings() {
        viewModelScope.launch {
            appContainer.ocrJobPipeline.saveSettings(ocrSettings.value)
            scanImportOptions.value = scanImportOptions.value.copy(ocrSettings = ocrSettings.value)
            localEvents.emit(EditorSessionEvent.UserMessage("Saved OCR settings"))
        }
    }
    fun pauseOcr(pageIndex: Int? = null) {
        val documentKey = session.state.value.document?.documentRef?.sourceKey ?: return
        viewModelScope.launch {
            appContainer.ocrJobPipeline.pause(documentKey, pageIndex)
            localEvents.emit(EditorSessionEvent.UserMessage(if (pageIndex == null) "Paused OCR jobs" else "Paused OCR for page ${pageIndex + 1}"))
        }
    }
    fun resumeOcr(pageIndex: Int? = null) {
        val documentKey = session.state.value.document?.documentRef?.sourceKey ?: return
        viewModelScope.launch {
            appContainer.ocrJobPipeline.resume(documentKey, pageIndex)
            localEvents.emit(EditorSessionEvent.UserMessage(if (pageIndex == null) "Resumed OCR jobs" else "Resumed OCR for page ${pageIndex + 1}"))
        }
    }
    fun rerunOcr(pageIndex: Int? = null) {
        val documentKey = session.state.value.document?.documentRef?.sourceKey ?: return
        viewModelScope.launch {
            appContainer.ocrJobPipeline.rerun(documentKey, pageIndex)
            localEvents.emit(EditorSessionEvent.UserMessage(if (pageIndex == null) "Queued OCR re-run for all pages" else "Queued OCR re-run for page ${pageIndex + 1}"))
        }
    }
    fun openOcrPage(pageIndex: Int) { openOutlineItem(pageIndex) }
    fun updateReviewFilter(filter: ReviewFilterModel) { reviewFilter.value = filter; refreshCollaborationAsync() }

    fun createShareLink() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.collaborationRepository.createShareLink(document, document.documentRef.displayName, SharePermission.Comment, System.currentTimeMillis() + 604_800_000L)
            refreshCollaborationData()
            localEvents.emit(EditorSessionEvent.UserMessage("Created share link"))
        }
    }

    fun addReviewThread(title: String, message: String) {
        val document = session.state.value.document ?: return
        if (message.isBlank()) return
        viewModelScope.launch {
            appContainer.collaborationRepository.addReviewThread(
                document = document,
                title = title,
                message = message,
                pageIndex = session.state.value.selection.selectedPageIndex,
                anchorBounds = selectedTextSelection.value?.blocks?.firstOrNull()?.bounds,
            )
            refreshCollaborationData()
        }
    }

    fun addReviewReply(threadId: String, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            appContainer.collaborationRepository.addReviewReply(threadId, "Ayman", message)
            refreshCollaborationData()
        }
    }

    fun toggleThreadResolved(threadId: String, resolved: Boolean) {
        viewModelScope.launch {
            appContainer.collaborationRepository.setThreadResolved(threadId, resolved)
            refreshCollaborationData()
        }
    }

    fun createVersionSnapshot() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            appContainer.collaborationRepository.createVersionSnapshot(document, "Snapshot ${System.currentTimeMillis()}")
            refreshCollaborationData()
            localEvents.emit(EditorSessionEvent.UserMessage("Created local snapshot"))
        }
    }

    fun syncCollaboration() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val summary = appContainer.collaborationRepository.processSync(document.documentRef.sourceKey)
            refreshCollaborationData()
            refreshWorkflowData()
            localEvents.emit(EditorSessionEvent.UserMessage("Sync processed  operation(s)"))
        }
    }

    fun compareAgainstLatestSnapshot() {
        val document = session.state.value.document ?: return
        val snapshot = versionSnapshots.value.firstOrNull() ?: run {
            localEvents.tryEmit(EditorSessionEvent.UserMessage("Create a snapshot first"))
            return
        }
        viewModelScope.launch {
            runCatching { appContainer.workflowRepository.compareWithSnapshot(document, snapshot.snapshotPath, snapshot.label) }
                .onSuccess {
                    refreshWorkflowData()
                    localEvents.emit(EditorSessionEvent.UserMessage("Compared against "))
                }
                .onFailure { localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Compare failed")) }
        }
    }

    fun openCompareMarker(marker: CompareMarkerModel) {
        focusSearchHit(marker.pageIndex, marker.bounds, marker.summary, searchResults.value.selectedHitIndex)
        activePanel.value = WorkspacePanel.Review
        sidebarVisible.value = true
    }

    fun exportDocumentAsText() {
        val document = session.state.value.document ?: return
        if (!ensureExportAllowed(document)) return
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.exportDocumentAsText(
                    document = document,
                    destination = exportDirectory().resolve(document.documentRef.displayName.removeSuffix(".pdf") + ".txt"),
                )
            }.onSuccess { result ->
                localEvents.emit(EditorSessionEvent.UserMessage("Exported text to ${result.artifacts.first().displayName}"))
                recordActivity(ActivityEventType.Exported, "Exported text")
                recordSecurityAudit(AuditEventType.ProtectedExported, "Exported text copy", mapOf("path" to result.artifacts.first().path))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Text export failed"))
            }
        }
    }

    fun exportDocumentAsMarkdown() {
        val document = session.state.value.document ?: return
        if (!ensureExportAllowed(document)) return
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.exportDocumentAsMarkdown(
                    document = document,
                    destination = exportDirectory().resolve(document.documentRef.displayName.removeSuffix(".pdf") + ".md"),
                )
            }.onSuccess { result ->
                localEvents.emit(EditorSessionEvent.UserMessage("Exported markdown to ${result.artifacts.first().displayName}"))
                recordActivity(ActivityEventType.Exported, "Exported markdown")
                recordSecurityAudit(AuditEventType.ProtectedExported, "Exported markdown copy", mapOf("path" to result.artifacts.first().path))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Markdown export failed"))
            }
        }
    }

    fun exportDocumentAsWord() {
        val document = session.state.value.document ?: return
        if (!ensureExportAllowed(document)) return
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.exportDocumentAsWord(
                    document = document,
                    destination = exportDirectory().resolve(document.documentRef.displayName.removeSuffix(".pdf") + ".docx"),
                )
            }.onSuccess { result ->
                localEvents.emit(EditorSessionEvent.UserMessage("Exported Word document to ${result.artifacts.first().displayName}"))
                recordActivity(ActivityEventType.Exported, "Exported Word")
                recordSecurityAudit(AuditEventType.ProtectedExported, "Exported Word copy", mapOf("path" to result.artifacts.first().path))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Word export failed"))
            }
        }
    }

    fun importSourceAsPdf(uri: Uri) {
        viewModelScope.launch {
            val source = copyUriToCache(uri, "import-source", ".${uri.lastPathSegment?.substringAfterLast('.', "bin") ?: "bin"}")
                ?: run {
                    localEvents.emit(EditorSessionEvent.UserMessage("Unable to read selected source"))
                    return@launch
                }
            runCatching {
                appContainer.workflowRepository.importSourceAsPdf(source, source.nameWithoutExtension + ".pdf")
            }.onSuccess { result ->
                session.openDocument(result.request)
                refreshThumbnails()
                refreshFormSupportData()
                refreshSearchSupportData(forceSync = true)
                refreshCollaborationData()
                refreshWorkflowData()
                localEvents.emit(EditorSessionEvent.UserMessage("Imported ${source.name} as PDF"))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Import to PDF failed"))
            }
        }
    }

    fun exportDocumentAsImages() {
        val document = session.state.value.document ?: return
        if (!ensureExportAllowed(document)) return
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.exportDocumentAsImages(
                    document = document,
                    outputDirectory = exportDirectory().resolve(document.documentRef.displayName.removeSuffix(".pdf") + "-images"),
                    format = ExportImageFormat.Png,
                )
            }.onSuccess { result ->
                localEvents.emit(EditorSessionEvent.UserMessage("Exported ${result.artifacts.size} page image(s)"))
                recordActivity(ActivityEventType.Exported, "Exported images")
                recordSecurityAudit(AuditEventType.ProtectedExported, "Exported page images", mapOf("count" to result.artifacts.size.toString()))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Image export failed"))
            }
        }
    }

    fun optimizeDocument(preset: PdfOptimizationPreset) {
        val document = session.state.value.document ?: return
        if (!ensureExportAllowed(document)) return
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.optimizeDocument(
                    document = document,
                    destination = exportDirectory().resolve("${document.documentRef.displayName.removeSuffix(".pdf")}_${preset.name.lowercase()}.pdf"),
                    preset = preset,
                )
            }.onSuccess { result ->
                localEvents.emit(EditorSessionEvent.UserMessage("Optimized copy saved as ${result.destination.name}"))
                recordActivity(ActivityEventType.Exported, "Optimized ${preset.name.lowercase()} copy")
                recordSecurityAudit(
                    AuditEventType.ProtectedExported,
                    "Optimized export created",
                    mapOf(
                        "preset" to preset.name,
                        "path" to result.destination.absolutePath,
                        "originalSize" to result.originalSizeBytes.toString(),
                        "optimizedSize" to result.optimizedSizeBytes.toString(),
                    ),
                )
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Optimize failed"))
            }
        }
    }
    fun createCurrentFormTemplate(name: String) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            runCatching { appContainer.workflowRepository.createFormTemplate(document, name) }
                .onSuccess {
                    refreshWorkflowData()
                    localEvents.emit(EditorSessionEvent.UserMessage("Saved template "))
                }
                .onFailure { localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to save template")) }
        }
    }

    fun createSignatureRequest(recipientCsv: String) {
        val document = session.state.value.document ?: return
        val recipients = recipientCsv.split(',', ';').mapNotNull { token ->
            val email = token.trim()
            if (email.isBlank()) null else WorkflowRecipientModel(email = email, displayName = email.substringBefore('@'), role = WorkflowRecipientRole.Signer, order = 1)
        }
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.createSignatureRequest(
                    document,
                    "Signature Request ",
                    recipients.distinctBy { it.email }.mapIndexed { index, recipient -> recipient.copy(order = index + 1) },
                    3,
                    System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L,
                )
            }.onSuccess {
                refreshWorkflowData()
                localEvents.emit(EditorSessionEvent.UserMessage("Signature request sent"))
            }.onFailure { localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to create signature request")) }
        }
    }

    fun createFormRequest(templateId: String, recipientCsv: String) {
        val document = session.state.value.document ?: return
        val recipients = recipientCsv.split(',', ';').mapNotNull { token ->
            val email = token.trim()
            if (email.isBlank()) null else WorkflowRecipientModel(email = email, displayName = email.substringBefore('@'), role = WorkflowRecipientRole.Submitter, order = 1)
        }
        viewModelScope.launch {
            runCatching {
                appContainer.workflowRepository.createFormRequest(
                    document,
                    templateId,
                    "Form Request ",
                    recipients.distinctBy { it.email }.mapIndexed { index, recipient -> recipient.copy(order = index + 1) },
                    3,
                    System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L,
                )
            }.onSuccess {
                refreshWorkflowData()
                localEvents.emit(EditorSessionEvent.UserMessage("Form request sent"))
            }.onFailure { localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to create form request")) }
        }
    }

    fun markWorkflowRequestCompleted(requestId: String) {
        viewModelScope.launch {
            val request = workflowState.value.requests.firstOrNull { it.id == requestId } ?: return@launch
            val recipient = request.recipients.firstOrNull() ?: return@launch
            appContainer.workflowRepository.updateRequestResponse(
                requestId,
                WorkflowResponseModel(
                    recipientEmail = recipient.email,
                    status = WorkflowRequestStatus.Completed,
                    actedAtEpochMillis = System.currentTimeMillis(),
                    note = "Completed in app",
                    fieldValues = request.assignedFields.associate { it.fieldName to "completed" },
                ),
            )
            refreshWorkflowData()
        }
    }

    fun sendWorkflowReminder(requestId: String) {
        viewModelScope.launch {
            appContainer.workflowRepository.sendReminder(requestId)
            refreshWorkflowData()
            localEvents.emit(EditorSessionEvent.UserMessage("Reminder sent"))
        }
    }
    fun performSearch(queryOverride: String? = null) {
        val document = session.state.value.document ?: return
        val query = queryOverride ?: searchQuery.value
        searchQuery.value = query
        viewModelScope.launch {
            isSearchIndexing.value = true
            val results = appContainer.documentSearchService.search(document, query)
            searchResults.value = results
            recentSearches.value = appContainer.documentSearchService.recentSearches(document.documentRef.sourceKey)
            isSearchIndexing.value = false
            results.selectedHit?.let { focusSearchHit(it.pageIndex, it.bounds, it.matchText, results.selectedHitIndex) }
        }
    }

    fun selectSearchHit(index: Int) {
        val current = searchResults.value
        val hit = current.hits.getOrNull(index) ?: return
        searchResults.value = current.copy(selectedHitIndex = index)
        focusSearchHit(hit.pageIndex, hit.bounds, hit.matchText, index)
    }

    fun nextSearchHit() {
        val current = searchResults.value
        if (current.hits.isEmpty()) return
        val next = if (current.selectedHitIndex < 0) 0 else (current.selectedHitIndex + 1) % current.hits.size
        selectSearchHit(next)
    }

    fun previousSearchHit() {
        val current = searchResults.value
        if (current.hits.isEmpty()) return
        val previous = if (current.selectedHitIndex <= 0) current.hits.lastIndex else current.selectedHitIndex - 1
        selectSearchHit(previous)
    }

    fun openOutlineItem(pageIndex: Int) {
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun copySelectedText() {
        val selection = selectedTextSelection.value ?: return
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Copy)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Copy blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Copy blocked", mapOf("action" to RestrictedAction.Copy.name)) }
            return
        }
        val clipboard = appContainer.appContext.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("Selected text", selection.text))
        localEvents.tryEmit(EditorSessionEvent.UserMessage("Copied selected text"))
    }

    fun shareSelectedText() {
        val selection = selectedTextSelection.value ?: return
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Share)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Share blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Share blocked", mapOf("action" to RestrictedAction.Share.name)) }
            return
        }
        localEvents.tryEmit(EditorSessionEvent.ShareText("Selected text", selection.text))
    }

    fun importScanImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val cachedImages = uris.mapIndexedNotNull { index, uri -> copyUriToCache(uri, "scan-import-$index", ".png") }
            if (cachedImages.isEmpty()) return@launch
            val request = appContainer.scanImportService.importImages(cachedImages, scanImportOptions.value)
            scanImportVisible.value = false
            session.openDocument(request)
            refreshThumbnails()
            refreshFormSupportData()
            refreshSearchSupportData(forceSync = true)
            refreshCollaborationData()
            recordActivity(ActivityEventType.Opened, "Opened scanned import ${session.state.value.document?.documentRef?.displayName.orEmpty()}")
            localEvents.emit(EditorSessionEvent.UserMessage("Imported ${cachedImages.size} scan image(s)"))
        }
    }

    fun onAnnotationCreated(annotation: AnnotationModel) {
        session.execute(AddAnnotationCommand(annotation.pageIndex, annotation))
        activeTool.value = AnnotationTool.Select
    }

    fun onAnnotationUpdated(before: AnnotationModel, after: AnnotationModel) {
        session.execute(UpdateAnnotationCommand(before.pageIndex, before, after))
    }

    fun onAnnotationSelectionChanged(pageIndex: Int, annotationIds: Set<String>) {
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = annotationIds,
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun onPageEditSelectionChanged(pageIndex: Int, editId: String?) {
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = null,
                selectedEditId = editId,
            ),
        )
        if (editId != null) {
            activePanel.value = WorkspacePanel.Annotate
            activeTool.value = AnnotationTool.Select
        }
    }

    fun onPageEditUpdated(before: PageEditModel, after: PageEditModel) {
        session.execute(UpdatePageEditCommand(before, after))
    }

    fun addTextBox() {
        val pageIndex = session.state.value.selection.selectedPageIndex
        val edit = TextBoxEditModel(
            id = UUID.randomUUID().toString(),
            pageIndex = pageIndex,
            bounds = NormalizedRect(0.14f, 0.16f, 0.54f, 0.28f),
            text = "Edit text",
        )
        session.execute(AddPageEditCommand(pageIndex, edit))
        activePanel.value = WorkspacePanel.Annotate
    }

    fun addImageEdit(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "page-edit-image", ".png") ?: return@launch
            val pageIndex = session.state.value.selection.selectedPageIndex
            val edit = ImageEditModel(
                id = UUID.randomUUID().toString(),
                pageIndex = pageIndex,
                bounds = NormalizedRect(0.18f, 0.22f, 0.56f, 0.48f),
                imagePath = copied.absolutePath,
                label = copied.name,
            )
            session.execute(AddPageEditCommand(pageIndex, edit))
            activePanel.value = WorkspacePanel.Annotate
        }
    }

    fun replaceSelectedImage(uri: Uri) {
        val selected = uiState.value.selectedEditObject as? ImageEditModel ?: return
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "page-edit-image-replace", ".png") ?: return@launch
            session.execute(ReplaceImageAssetCommand(selected, selected.replaced(copied.absolutePath, copied.name)))
        }
    }

    fun selectPageEdit(editId: String) {
        val document = session.state.value.document ?: return
        val page = document.pages.firstOrNull { page -> page.editObjects.any { it.id == editId } } ?: return
        onPageEditSelectionChanged(page.index, editId)
    }

    fun deleteSelectedEdit() {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(DeletePageEditCommand(selected.pageIndex, selected))
    }

    fun duplicateSelectedEdit() {
        val selected = uiState.value.selectedEditObject ?: return
        val duplicated = selected.duplicated(UUID.randomUUID().toString())
        session.execute(AddPageEditCommand(selected.pageIndex, duplicated))
    }

    fun updateSelectedTextContent(text: String) {
        val selected = uiState.value.selectedEditObject as? TextBoxEditModel ?: return
        session.execute(UpdatePageEditCommand(selected, selected.withText(text)))
    }

    fun updateSelectedTextStyle(
        fontFamily: FontFamilyToken = (uiState.value.selectedEditObject as? TextBoxEditModel)?.fontFamily ?: FontFamilyToken.Sans,
        fontSizeSp: Float = (uiState.value.selectedEditObject as? TextBoxEditModel)?.fontSizeSp ?: 16f,
        textColorHex: String = (uiState.value.selectedEditObject as? TextBoxEditModel)?.textColorHex ?: "#202124",
        alignment: TextAlignment = (uiState.value.selectedEditObject as? TextBoxEditModel)?.alignment ?: TextAlignment.Start,
        opacity: Float = uiState.value.selectedEditObject?.opacity ?: 1f,
        lineSpacingMultiplier: Float = (uiState.value.selectedEditObject as? TextBoxEditModel)?.lineSpacingMultiplier ?: 1.2f,
    ) {
        val selected = uiState.value.selectedEditObject as? TextBoxEditModel ?: return
        val updated = selected.withTypography(fontFamily, fontSizeSp, textColorHex, alignment, lineSpacingMultiplier).withOpacity(opacity) as TextBoxEditModel
        session.execute(UpdatePageEditCommand(selected, updated))
    }

    fun updateSelectedEditRotation(rotationDegrees: Float) {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(UpdatePageEditCommand(selected, selected.rotatedTo(rotationDegrees)))
    }

    fun updateSelectedEditOpacity(opacity: Float) {
        val selected = uiState.value.selectedEditObject ?: return
        session.execute(UpdatePageEditCommand(selected, selected.withOpacity(opacity)))
    }

    fun onFormFieldTapped(fieldName: String) {
        selectFormField(fieldName)
    }

    fun selectAnnotation(annotationId: String) {
        val document = session.state.value.document ?: return
        val page = document.pages.firstOrNull { page -> page.annotations.any { it.id == annotationId } } ?: return
        activePanel.value = WorkspacePanel.Annotate
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = page.index,
                selectedAnnotationIds = setOf(annotationId),
                selectedFormFieldName = null,
                selectedEditId = null,
            ),
        )
    }

    fun recolorSelectedAnnotation(colorHex: String) {
        val selected = uiState.value.selectedAnnotation ?: return
        session.execute(UpdateAnnotationCommand(selected.pageIndex, selected, selected.recolored(colorHex, selected.fillColorHex)))
    }

    fun deleteSelectedAnnotation() {
        uiState.value.selectedAnnotation?.let { session.execute(DeleteAnnotationCommand(it.pageIndex, it)) }
    }

    fun duplicateSelectedAnnotation() {
        uiState.value.selectedAnnotation?.let { selected ->
            session.execute(DuplicateAnnotationCommand(selected.pageIndex, selected, selected.duplicated(UUID.randomUUID().toString())))
        }
    }

    fun selectFormField(fieldName: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        activePanel.value = if (field.type.name == "Signature") WorkspacePanel.Sign else WorkspacePanel.Forms
        selectedTextSelection.value = null
        session.updateSelection(
            session.state.value.selection.copy(
                selectedPageIndex = field.pageIndex,
                selectedAnnotationIds = emptySet(),
                selectedFormFieldName = field.name,
                selectedEditId = null,
            ),
        )
    }

    fun updateTextField(fieldName: String, value: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        val updated = field.copy(value = FormFieldValue.Text(value), signatureStatus = field.signatureStatus)
        session.execute(UpdateFormFieldCommand(field, updated))
    }

    fun toggleBooleanField(fieldName: String, checked: Boolean) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        session.execute(UpdateFormFieldCommand(field, field.copy(value = FormFieldValue.BooleanValue(checked))))
    }

    fun updateChoiceField(fieldName: String, choice: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        session.execute(UpdateFormFieldCommand(field, field.copy(value = FormFieldValue.Choice(choice))))
    }

    fun saveFormProfile(name: String) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val profile = appContainer.formSupportRepository.saveProfile(name, document.formDocument)
            refreshFormSupportData()
            localEvents.emit(EditorSessionEvent.UserMessage("Saved form profile ${profile.name}"))
        }
    }

    fun applyFormProfile(profileId: String) {
        val document = session.state.value.document ?: return
        val profile = formProfiles.value.firstOrNull { it.id == profileId } ?: return
        val updated = document.formDocument.copy(
            fields = document.formDocument.fields.map { field ->
                profile.values[field.name]?.let { value ->
                    field.copy(
                        value = value,
                        signatureStatus = if (value is FormFieldValue.SignatureValue) value.status else field.signatureStatus,
                    )
                } ?: field
            },
        )
        session.execute(ReplaceFormDocumentCommand(document.formDocument, updated))
        localEvents.tryEmit(EditorSessionEvent.UserMessage("Applied profile ${profile.name}"))
    }

    fun importFormProfile(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "form-profile", ".json") ?: return@launch
            val imported = appContainer.formSupportRepository.importProfile(copied)
            refreshFormSupportData()
            applyFormProfile(imported.id)
        }
    }

    fun exportCurrentFormData() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val exportName = document.documentRef.displayName.removeSuffix(".pdf") + "-form-profile.json"
            val destination = File(appContainer.appContext.cacheDir, "form-exports/$exportName")
            val profile = appContainer.formSupportRepository.saveProfile("Export ${document.documentRef.displayName}", document.formDocument)
            appContainer.formSupportRepository.exportProfile(profile, destination)
            refreshFormSupportData()
            localEvents.emit(EditorSessionEvent.UserMessage("Exported form data to ${destination.name}"))
        }
    }

    fun openSignatureCapture(fieldName: String? = uiState.value.selectedFormField?.name) {
        val targetField = fieldName ?: return
        signingFieldName.value = targetField
        signatureCaptureVisible.value = true
        activePanel.value = WorkspacePanel.Sign
    }

    fun dismissSignatureCapture() {
        signatureCaptureVisible.value = false
        signingFieldName.value = null
    }

    fun saveSignatureAndApply(name: String, kind: SignatureKind, capture: SignatureCapture) {
        val fieldName = signingFieldName.value ?: return
        viewModelScope.launch {
            val savedSignature = appContainer.formSupportRepository.saveSignature(name, kind, capture)
            refreshFormSupportData()
            applySavedSignature(fieldName, savedSignature.id)
            dismissSignatureCapture()
            localEvents.emit(EditorSessionEvent.UserMessage("Saved ${kind.name.lowercase()} for $name"))
        }
    }

    fun applySavedSignature(fieldName: String, signatureId: String) {
        val field = session.state.value.document?.formDocument?.field(fieldName) ?: return
        val savedSignature = savedSignatures.value.firstOrNull { it.id == signatureId } ?: return
        val signatureValue = FormFieldValue.SignatureValue(
            savedSignatureId = savedSignature.id,
            signerName = savedSignature.name,
            signedAtEpochMillis = System.currentTimeMillis(),
            status = SignatureVerificationStatus.Signed,
            imagePath = savedSignature.imagePath,
            kind = savedSignature.kind,
        )
        session.execute(
            UpdateFormFieldCommand(
                field,
                field.copy(
                    value = signatureValue,
                    signatureStatus = SignatureVerificationStatus.Signed,
                ),
            ),
        )
        viewModelScope.launch { recordActivity(ActivityEventType.Signed, "Signed field ${field.name}") }
        selectFormField(fieldName)
    }

    fun selectPage(index: Int) { selectedPageIndexes.value = selectedPageIndexes.value.toggle(index) }
    fun clearPageSelection() { selectedPageIndexes.value = emptySet() }
    fun movePage(fromIndex: Int, toIndex: Int) { session.execute(ReorderPagesCommand(fromIndex, toIndex)); refreshThumbnailsAsync() }
    fun moveSelectionBackward() {
        val anchor = effectivePageSelection().minOrNull() ?: return
        if (anchor > 0) {
            movePage(anchor, anchor - 1)
        }
    }
    fun moveSelectionForward() {
        val document = session.state.value.document ?: return
        val anchor = effectivePageSelection().maxOrNull() ?: return
        if (anchor < document.pageCount - 1) {
            movePage(anchor, anchor + 1)
        }
    }
    fun rotateSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(BatchRotatePagesCommand(it, 90)); refreshThumbnailsAsync() } }
    fun deleteSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(DeletePagesCommand(it)); selectedPageIndexes.value = emptySet(); refreshThumbnailsAsync() } }
    fun duplicateSelectedPages() { effectivePageSelection().toList().sorted().takeIf { it.isNotEmpty() }?.let { session.execute(DuplicatePagesCommand(it)); refreshThumbnailsAsync() } }
    fun extractSelectedPages() { effectivePageSelection().takeIf { it.isNotEmpty() }?.let { session.execute(ExtractPagesCommand(it)); selectedPageIndexes.value = emptySet(); refreshThumbnailsAsync() } }
    fun insertBlankPage() { session.execute(InsertBlankPageCommand(((selectedPageIndexes.value.maxOrNull()?.plus(1)) ?: (session.state.value.selection.selectedPageIndex + 1)))); refreshThumbnailsAsync() }

    fun insertImagePage(uri: Uri) {
        viewModelScope.launch {
            val copied = copyUriToCache(uri, "organize-image", ".png") ?: return@launch
            val bitmap = BitmapFactory.decodeFile(copied.absolutePath) ?: return@launch
            val target = (selectedPageIndexes.value.maxOrNull()?.plus(1)) ?: (session.state.value.selection.selectedPageIndex + 1)
            session.execute(InsertImagePageCommand(target, copied.absolutePath, bitmap.width.toFloat(), bitmap.height.toFloat()))
            refreshThumbnails()
        }
    }

    fun mergeDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val sourceFiles = uris.mapIndexedNotNull { index, uri ->
                val suffix = ".${uri.lastPathSegment?.substringAfterLast('.', "pdf") ?: "pdf"}"
                copyUriToCache(uri, "merge-source-${index + 1}", suffix)
            }
            if (sourceFiles.isEmpty()) {
                localEvents.emit(EditorSessionEvent.UserMessage("No mergeable sources were selected"))
                return@launch
            }
            runCatching {
                appContainer.workflowRepository.mergeSourcesAsPdf(sourceFiles, "merged_${System.currentTimeMillis()}.pdf")
            }.onSuccess { result ->
                session.openDocument(result.request)
                refreshThumbnails()
                refreshFormSupportData()
                refreshSearchSupportData(forceSync = true)
                refreshWorkflowData()
                localEvents.emit(EditorSessionEvent.UserMessage("Merged ${sourceFiles.size} source file(s)"))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Merge failed"))
            }
        }
    }

    fun updateSplitRangeExpression(value: String) { splitRangeExpression.value = value }
    fun splitByRange() { split(SplitRequest(SplitMode.PageRanges, rangeExpression = uiState.value.splitRangeExpression)) }
    fun splitOddPages() { split(SplitRequest(SplitMode.OddPages)) }
    fun splitEvenPages() { split(SplitRequest(SplitMode.EvenPages)) }
    fun splitSelectedPages() { split(SplitRequest(SplitMode.SelectedPages, selectedPageIndexes = effectivePageSelection())) }

    private fun split(request: SplitRequest) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val outputDir = File(appContainer.appContext.cacheDir, "splits/${document.sessionId}")
            val files = repository.split(document, request, outputDir)
            localEvents.tryEmit(EditorSessionEvent.UserMessage("Created ${files.size} split file(s) in ${outputDir.name}"))
        }
    }

    fun signInPersonal(displayName: String) {
        viewModelScope.launch {
            syncEnterpriseState(appContainer.enterpriseAdminRepository.signInPersonal(displayName))
            queueTelemetry("sign_in_personal", mapOf("user" to enterpriseState.value.authSession.displayName))
        }
    }

    fun signInEnterprise(email: String, tenant: TenantConfigurationModel) {
        viewModelScope.launch {
            syncEnterpriseState(appContainer.enterpriseAdminRepository.signInEnterprise(email, tenant))
            queueTelemetry("sign_in_enterprise", mapOf("tenant" to tenant.tenantName))
        }
    }

    fun refreshEnterpriseRemoteState() {
        viewModelScope.launch {
            syncEnterpriseState(appContainer.enterpriseAdminRepository.refreshRemoteState(force = true))
            localEvents.emit(EditorSessionEvent.UserMessage("Refreshed enterprise tenant and policy state"))
        }
    }

    fun flushEnterpriseTelemetry() {
        viewModelScope.launch {
            val uploaded = appContainer.enterpriseAdminRepository.flushTelemetry()
            telemetryEvents.value = appContainer.enterpriseAdminRepository.pendingTelemetry()
            localEvents.emit(EditorSessionEvent.UserMessage(if (uploaded > 0) "Uploaded $uploaded telemetry event(s)" else "No telemetry uploaded"))
        }
    }

    fun signOutEnterprise() {
        viewModelScope.launch {
            syncEnterpriseState(appContainer.enterpriseAdminRepository.signOut())
            queueTelemetry("sign_out")
        }
    }

    fun setEnterprisePlan(plan: LicensePlan) {
        viewModelScope.launch {
            val updated = enterpriseState.value.copy(plan = plan)
            appContainer.enterpriseAdminRepository.saveState(updated)
            syncEnterpriseState(updated)
            queueTelemetry("plan_changed", mapOf("plan" to plan.name))
        }
    }

    fun updateEnterprisePrivacy(settings: PrivacySettingsModel) {
        viewModelScope.launch {
            val updated = enterpriseState.value.copy(privacySettings = settings)
            appContainer.enterpriseAdminRepository.saveState(updated)
            syncEnterpriseState(updated)
        }
    }

    fun updateEnterprisePolicy(policy: AdminPolicyModel) {
        viewModelScope.launch {
            val updated = enterpriseState.value.copy(adminPolicy = policy)
            appContainer.enterpriseAdminRepository.saveState(updated)
            syncEnterpriseState(updated)
            queueTelemetry("policy_updated")
        }
    }

    fun generateDiagnosticsBundle() {
        viewModelScope.launch {
            val destination = File(appContainer.appContext.cacheDir, "diagnostics/diagnostics-${System.currentTimeMillis()}.json")
            appContainer.enterpriseAdminRepository.diagnosticsBundle(
                destination,
                mapOf(
                    "documentLoaded" to (session.state.value.document != null).toString(),
                    "pageCount" to (session.state.value.document?.pageCount ?: 0).toString(),
                    "activePanel" to activePanel.value.name,
                ),
            )
            diagnosticsBundleCount.value = diagnosticsBundleCount.value + 1
            queueTelemetry("diagnostics_bundle_generated")
            localEvents.emit(EditorSessionEvent.UserMessage("Generated diagnostics bundle ${destination.name}"))
        }
    }
    fun configureAppLock(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int) {
        viewModelScope.launch {
            appLockSettings.value = appContainer.securityRepository.updateAppLockSettings(enabled, pin, biometricsEnabled, timeoutSeconds)
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun unlockWithPin(pin: String) {
        viewModelScope.launch {
            val success = appContainer.securityRepository.unlockWithPin(pin)
            appLockState.value = appContainer.securityRepository.appLockState.value
            if (!success) {
                localEvents.emit(EditorSessionEvent.UserMessage("PIN did not match"))
            }
        }
    }

    fun unlockWithBiometric() {
        viewModelScope.launch {
            appContainer.securityRepository.unlockWithBiometric()
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun lockNow() {
        viewModelScope.launch {
            appContainer.securityRepository.lockApp(AppLockReason.Manual)
            appLockState.value = appContainer.securityRepository.appLockState.value
        }
    }

    fun updateDocumentPermissions(permissions: DocumentPermissionModel) {
        replaceSecurityDocument { copy(permissions = permissions) }
    }

    fun updateTenantPolicy(policy: TenantPolicyHooksModel) {
        replaceSecurityDocument { copy(tenantPolicy = policy) }
    }

    fun updatePasswordProtection(enabled: Boolean, userPassword: String, ownerPassword: String) {
        replaceSecurityDocument { copy(passwordProtection = passwordProtection.copy(enabled = enabled, userPassword = userPassword, ownerPassword = ownerPassword)) }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.PasswordProtectionUpdated, "Updated password protection") }
    }

    fun updateWatermark(enabled: Boolean, text: String) {
        replaceSecurityDocument { copy(watermark = watermark.copy(enabled = enabled, text = text)) }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.WatermarkUpdated, "Updated watermark") }
    }

    fun updateMetadataScrub(options: MetadataScrubOptionsModel) {
        replaceSecurityDocument { copy(metadataScrub = options) }
    }

    fun generateInspectionReport() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val report = appContainer.securityRepository.inspectDocument(document)
            replaceSecurityDocument { copy(inspectionReport = report) }
            refreshSecurityData()
        }
    }

    fun markSelectedTextForRedaction() {
        val document = session.state.value.document ?: return
        val selection = selectedTextSelection.value ?: return
        val marks = selection.blocks.mapIndexed { index, block ->
            RedactionMarkModel(
                id = UUID.randomUUID().toString(),
                pageIndex = block.pageIndex,
                bounds = block.bounds,
                label = "Selection ${index + 1}",
                createdAtEpochMillis = System.currentTimeMillis(),
            )
        }
        if (marks.isEmpty()) return
        replaceSecurityDocument {
            copy(
                redactionWorkflow = redactionWorkflow.copy(
                    marks = redactionWorkflow.marks + marks,
                    previewEnabled = true,
                ),
            )
        }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.RedactionMarked, "Marked ${marks.size} redaction region(s)") }
    }

    fun setRedactionPreview(enabled: Boolean) {
        replaceSecurityDocument { copy(redactionWorkflow = redactionWorkflow.copy(previewEnabled = enabled)) }
    }

    fun removeRedactionMark(markId: String) {
        replaceSecurityDocument { copy(redactionWorkflow = redactionWorkflow.copy(marks = redactionWorkflow.marks.filterNot { it.id == markId })) }
    }

    fun applyRedactions() {
        replaceSecurityDocument {
            copy(
                redactionWorkflow = redactionWorkflow.copy(
                    previewEnabled = false,
                    irreversibleConfirmed = true,
                    marks = redactionWorkflow.marks.map { it.copy(status = RedactionStatus.Applied, appliedAtEpochMillis = System.currentTimeMillis()) },
                ),
            )
        }
        viewModelScope.launch { recordSecurityAudit(AuditEventType.RedactionApplied, "Applied redactions") }
    }

    fun exportAuditTrail() {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val destination = File(appContainer.appContext.cacheDir, "audit/${document.documentRef.displayName.removeSuffix(".pdf")}-audit.json")
            appContainer.securityRepository.exportAuditTrail(document.documentRef.sourceKey, destination)
            refreshSecurityData()
            localEvents.emit(EditorSessionEvent.UserMessage("Exported audit trail to ${destination.name}"))
        }
    }
    fun rotateCurrentPage() { session.execute(RotatePageCommand(session.state.value.selection.selectedPageIndex, 90)); refreshThumbnailsAsync() }
    fun reorderFirstPageToEnd() { session.state.value.document?.takeIf { it.pages.size >= 2 }?.let { session.execute(ReorderPagesCommand(0, it.pages.lastIndex)); refreshThumbnailsAsync() } }
    fun undo() { session.undo(); refreshThumbnailsAsync() }
    fun redo() { session.redo(); refreshThumbnailsAsync() }
    private fun ensureExportAllowed(document: com.aymanelbanhawy.editor.core.model.DocumentModel): Boolean {
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (decision.allowed) return true
        localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
        viewModelScope.launch {
            recordSecurityAudit(
                AuditEventType.PolicyBlocked,
                decision.message ?: "Export blocked",
                mapOf("action" to RestrictedAction.Export.name),
            )
        }
        return false
    }

    private fun exportDirectory(): File = File(appContainer.appContext.cacheDir, "exports").apply { mkdirs() }
    fun saveEditable() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        session.manualSave(AnnotationExportMode.Editable)
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved editable PDF")
        }
    }

    fun saveFlattened() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        session.manualSave(AnnotationExportMode.Flatten)
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved flattened PDF")
        }
    }

    fun saveAsEditable() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        File(document.documentRef.workingCopyPath).parentFile?.resolve("editable_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Editable) }
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved editable copy")
        }
    }

    fun saveAsFlattened() {
        val document = session.state.value.document ?: return
        val decision = appContainer.securityRepository.evaluatePolicy(document.security, RestrictedAction.Export)
        if (!decision.allowed) {
            localEvents.tryEmit(EditorSessionEvent.UserMessage(decision.message ?: "Export blocked"))
            viewModelScope.launch { recordSecurityAudit(AuditEventType.PolicyBlocked, decision.message ?: "Export blocked", mapOf("action" to RestrictedAction.Export.name)) }
            return
        }
        File(document.documentRef.workingCopyPath).parentFile?.resolve("flattened_${document.documentRef.displayName}")?.let { session.saveAs(it, AnnotationExportMode.Flatten) }
        viewModelScope.launch {
            persistCurrentSecurity()
            recordActivity(ActivityEventType.Exported, "Saved flattened copy")
        }
    }

    fun saveToConnectorEditable() {
        showConnectorExportDialog(SaveDestinationMode.SaveCopy, AnnotationExportMode.Editable)
    }

    fun saveToConnectorFlattened() {
        showConnectorExportDialog(SaveDestinationMode.SaveCopy, AnnotationExportMode.Flatten)
    }

    fun shareToConnectorEditable() {
        showConnectorExportDialog(SaveDestinationMode.ShareCopy, AnnotationExportMode.Editable)
    }

    fun dismissConnectorExportDialog() {
        connectorExportDialog.value = null
    }

    fun updateConnectorExportAccount(accountId: String) {
        connectorExportDialog.value = connectorExportDialog.value?.copy(selectedAccountId = accountId)
    }

    fun updateConnectorExportRemotePath(remotePath: String) {
        connectorExportDialog.value = connectorExportDialog.value?.copy(remotePath = remotePath)
    }

    fun updateConnectorExportDisplayName(displayName: String) {
        connectorExportDialog.value = connectorExportDialog.value?.copy(displayName = displayName)
    }

    fun submitConnectorExport() {
        val document = session.state.value.document ?: return
        val exportState = connectorExportDialog.value ?: return
        viewModelScope.launch {
            try {
                appContainer.connectorRepository.queueExport(
                    document = document,
                    request = ConnectorSaveRequest(
                        connectorAccountId = exportState.selectedAccountId,
                        remotePath = exportState.remotePath,
                        displayName = exportState.displayName,
                        exportMode = exportState.exportMode,
                        destinationMode = exportState.destinationMode,
                        overwrite = true,
                    ),
                )
                val completed = appContainer.connectorRepository.syncPendingTransfers()
                refreshConnectorData()
                connectorExportDialog.value = null
                localEvents.emit(EditorSessionEvent.UserMessage(if (completed > 0) "Connector transfer completed" else "Transfer queued for background sync"))
                recordActivity(ActivityEventType.Exported, "Queued ${exportState.destinationMode.name.lowercase()} to connector")
                queueTelemetry("connector_export_queued", mapOf("mode" to exportState.destinationMode.name, "exportMode" to exportState.exportMode.name))
            } catch (error: Throwable) {
                localEvents.emit(EditorSessionEvent.UserMessage(error.message ?: "Connector export failed"))
            }
        }
    }

    fun saveConnectorAccount(draft: ConnectorAccountDraft) {
        viewModelScope.launch {
            runCatching {
                appContainer.connectorRepository.saveAccount(draft)
            }.onSuccess {
                refreshConnectorData()
                queueTelemetry("connector_account_saved", mapOf("type" to draft.connectorType.name))
                localEvents.emit(EditorSessionEvent.UserMessage("Saved connector account ${it.displayName}"))
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to save connector account"))
            }
        }
    }

    fun testConnectorConnection(accountId: String) {
        viewModelScope.launch {
            val result = runCatching { appContainer.connectorRepository.testConnection(accountId) }.getOrElse {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Connector test failed"))
                return@launch
            }
            localEvents.emit(EditorSessionEvent.UserMessage(result.message ?: "Connection test passed"))
        }
    }

    private suspend fun openDocumentRequest(
        pendingRequest: PendingPdfOpenRequest,
        userMessage: String? = null,
        fallbackToSampleOnFailure: Boolean = false,
    ) {
        runCatching {
            session.openDocument(pendingRequest.request)
            refreshDocumentDependentData()
            publishOpenDiagnostic(pendingRequest)
            userMessage?.let { localEvents.emit(EditorSessionEvent.UserMessage(it)) }
        }.onFailure { error ->
            if (fallbackToSampleOnFailure && session.state.value.document == null) {
                runCatching {
                    val sampleRequest = PendingPdfOpenRequest(
                        request = appContainer.seedDocumentRequest(),
                        source = PdfOpenSource.SampleSeed,
                        activeUri = "asset://sample.pdf",
                        displayName = "sample.pdf",
                    )
                    session.openDocument(sampleRequest.request)
                    refreshDocumentDependentData()
                    publishOpenDiagnostic(sampleRequest)
                }
            }
            localEvents.emit(EditorSessionEvent.UserMessage(error.message ?: "Unable to open PDF"))
        }
    }

    fun openDocumentFromConnector(accountId: String, remotePath: String, displayName: String) {
        viewModelScope.launch {
            runCatching {
                appContainer.connectorRepository.openDocument(accountId, remotePath, displayName)
            }.onSuccess { request ->
                openDocumentRequest(
                    pendingRequest = PendingPdfOpenRequest(
                        request = request,
                        source = PdfOpenSource.Connector,
                        activeUri = "connector://$accountId/$remotePath",
                        displayName = displayName,
                    ),
                    userMessage = "Opened $displayName from connector",
                )
            }.onFailure {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to open connector document"))
            }
        }
    }

    fun syncConnectorTransfers() {
        viewModelScope.launch {
            val completed = runCatching { appContainer.connectorRepository.syncPendingTransfers() }.getOrElse {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Connector sync failed"))
                return@launch
            }
            refreshConnectorData()
            localEvents.emit(EditorSessionEvent.UserMessage(if (completed > 0) "Synced $completed connector transfer(s)" else "No connector transfers completed"))
        }
    }

    fun cleanupConnectorCache() {
        viewModelScope.launch {
            val evicted = runCatching { appContainer.connectorRepository.cleanupCache() }.getOrElse {
                localEvents.emit(EditorSessionEvent.UserMessage(it.message ?: "Unable to clean connector cache"))
                return@launch
            }
            refreshConnectorData()
            localEvents.emit(EditorSessionEvent.UserMessage("Evicted $evicted connector cache file(s)"))
        }
    }

    private suspend fun refreshDocumentDependentData() {
        refreshThumbnails()
        refreshFormSupportData()
        refreshSearchSupportData(forceSync = true)
        refreshCollaborationData()
        refreshWorkflowData()
        refreshSecurityData()
        refreshEnterpriseData()
        refreshConnectorData()
        refreshDiagnosticsData()
        recordActivity(ActivityEventType.Opened, "Opened ${session.state.value.document?.documentRef?.displayName.orEmpty()}")
        recordSecurityAudit(AuditEventType.DocumentOpened, "Opened document")
        queueTelemetry("document_opened", mapOf("mode" to enterpriseState.value.authSession.mode.name))
    }

    private fun publishOpenDiagnostic(pendingRequest: PendingPdfOpenRequest) {
        lastDocumentOpenDiagnostic.value = buildString {
            append("activeDocument=")
            append(pendingRequest.displayName)
            append(" source=")
            append(pendingRequest.source.name)
            append(" uri=")
            append(pendingRequest.activeUri)
        }
    }

    private fun showConnectorExportDialog(destinationMode: SaveDestinationMode, exportMode: AnnotationExportMode) {
        val document = session.state.value.document ?: return
        viewModelScope.launch {
            val accounts = appContainer.connectorRepository.allowedDestinations(forShare = destinationMode == SaveDestinationMode.ShareCopy)
            if (accounts.isEmpty()) {
                localEvents.emit(EditorSessionEvent.UserMessage("No connector destinations are allowed by current policy"))
                return@launch
            }
            connectorAccounts.value = accounts
            connectorExportDialog.value = ConnectorExportUiState(
                destinationMode = destinationMode,
                exportMode = exportMode,
                selectedAccountId = accounts.first().id,
                remotePath = "/${document.documentRef.displayName}",
                displayName = document.documentRef.displayName,
            )
        }
    }
    fun refreshDiagnostics() { viewModelScope.launch { refreshDiagnosticsData() } }

    fun repairRuntimeState() {
        viewModelScope.launch {
            val repair = appContainer.runtimeDiagnosticsRepository.runStartupRepair()
            appContainer.runtimeDiagnosticsRepository.recordBreadcrumb(
                category = RuntimeEventCategory.Recovery,
                level = RuntimeLogLevel.Info,
                eventName = "manual_repair",
                message = "Manual runtime repair executed.",
                metadata = mapOf(
                    "repairedDrafts" to repair.repairedDraftCount.toString(),
                    "recoveredSaves" to repair.recoveredSaveCount.toString(),
                ),
            )
            refreshDiagnosticsData()
            localEvents.emit(EditorSessionEvent.UserMessage("Repair complete"))
        }
    }

    private fun observeDocumentOcrState() {
        ocrObservationJob?.cancel()
        ocrObservationJob = viewModelScope.launch {
            session.state
                .map { it.document?.documentRef?.sourceKey }
                .distinctUntilChanged()
                .collectLatest { documentKey ->
                    if (documentKey.isNullOrBlank()) {
                        ocrJobs.value = emptyList()
                        return@collectLatest
                    }
                    ocrSettings.value = appContainer.ocrJobPipeline.loadSettings()
                    scanImportOptions.value = scanImportOptions.value.copy(ocrSettings = ocrSettings.value)
                    var completedCount = -1
                    appContainer.ocrJobPipeline.observe(documentKey).collectLatest { jobs ->
                        val nextCompleted = jobs.count { it.status == OcrJobStatus.Completed }
                        val hadFreshCompletion = completedCount >= 0 && nextCompleted > completedCount
                        completedCount = nextCompleted
                        ocrJobs.value = jobs
                        if (hadFreshCompletion) {
                            session.state.value.document?.let { document ->
                                if (searchQuery.value.isNotBlank()) {
                                    searchResults.value = appContainer.documentSearchService.search(document, searchQuery.value)
                                }
                                if (activePanel.value == WorkspacePanel.Assistant) {
                                    refreshAssistantData()
                                }
                            }
                        }
                    }
                }
        }
    }

    private suspend fun refreshConnectorData() {
        connectorAccounts.value = appContainer.connectorRepository.accounts()
        connectorJobs.value = appContainer.connectorRepository.transferJobs()
    }

    private suspend fun refreshDiagnosticsData() {
        runtimeDiagnostics.value = appContainer.runtimeDiagnosticsRepository.captureSnapshot(session.state.value.document)
    }

    private suspend fun refreshFormSupportData() {
        formProfiles.value = appContainer.formSupportRepository.loadProfiles()
        savedSignatures.value = appContainer.formSupportRepository.loadSignatures()
    }

    private suspend fun refreshSearchSupportData(forceSync: Boolean) {
        val document = session.state.value.document ?: return
        isSearchIndexing.value = true
        recentSearches.value = appContainer.documentSearchService.recentSearches(document.documentRef.sourceKey)
        outlineItems.value = appContainer.documentSearchService.outline(document.documentRef)
        if (forceSync || !indexingPolicy.shouldIndexInBackground(document)) {
            appContainer.documentSearchService.ensureIndex(document)
        } else {
            appContainer.searchIndexScheduler.scheduleIfNeeded(document)
        }
        isSearchIndexing.value = false
    }

    private suspend fun refreshAssistantAudioAvailability() {
        val capabilities = currentAudioCapabilities()
        assistantAudioState.value = assistantAudioState.value.copy(
            enabled = capabilities.assistantAudioEnabled(),
            reason = capabilities.assistantAudioReason(),
        )
        syncAssistantStateFromRepository()
    }

    private fun syncAssistantStateFromRepository() {
        val repositoryState = appContainer.aiAssistantRepository.state.value
        val capabilities = currentAudioCapabilities(repositoryState.settings)
        assistantState.value = repositoryState.copy(
            availability = repositoryState.availability,
            audio = assistantAudioState.value.copy(
                enabled = capabilities.assistantAudioEnabled(),
                reason = capabilities.assistantAudioReason(),
            ),
        )
    }

    private fun startReadAloud(title: String, text: String) {
        val capabilities = currentAudioCapabilities()
        if (!capabilities.readAloudAllowed) {
            assistantAudioState.value = assistantAudioState.value.copy(enabled = false, reason = capabilities.readAloudReason())
            syncAssistantStateFromRepository()
            return
        }
        val segments = splitReadAloudSegments(text)
        activeReadAloudSession = ActiveReadAloudSession(title = title, segments = segments, startIndex = 0)
        startReadAloudSession(requireNotNull(activeReadAloudSession))
    }

    private fun startReadAloudSession(sessionState: ActiveReadAloudSession) {
        val remainingSegments = sessionState.segments.drop(sessionState.startIndex)
        if (remainingSegments.isEmpty()) {
            assistantAudioState.value = assistantAudioState.value.readAloudStopped()
            syncAssistantStateFromRepository()
            activeReadAloudSession = null
            return
        }
        readAloudJob?.cancel()
        readAloudJob = viewModelScope.launch {
            appContainer.speechCaptureEngine.cancelCapture()
            appContainer.readAloudEngine.speak(
                ReadAloudRequest(
                    title = sessionState.title,
                    text = remainingSegments.joinToString(separator = " "),
                ),
            ).collect { event ->
                if (event is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Stopped && suppressReadAloudStopUpdate) {
                    suppressReadAloudStopUpdate = false
                    return@collect
                }
                assistantAudioState.value = assistantAudioState.value.reduceReadAloudEvent(
                    remapReadAloudEvent(event, sessionState),
                )
                if (event is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.SegmentStarted) {
                    activeReadAloudSession = sessionState.copy(startIndex = sessionState.startIndex + event.index)
                }
                if (event is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Completed || event is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Stopped || event is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Failure) {
                    activeReadAloudSession = null
                }
                syncAssistantStateFromRepository()
            }
        }
    }

    private fun currentAudioCapabilities(settings: com.aymanelbanhawy.aiassistant.core.AssistantSettings = assistantState.value.settings) =
        resolveAudioFeatureCapabilities(
            entitlements = entitlements.value,
            policy = enterpriseState.value.adminPolicy,
            privacySettings = enterpriseState.value.privacySettings,
            assistantSettings = settings,
        )

    private suspend fun speakLatestAssistantResultIfEligible() {
        val latestResult = assistantState.value.latestResult ?: return
        val capabilities = currentAudioCapabilities()
        if (!capabilities.spokenAssistantResponsesAllowed) return
        if (lastSpokenAssistantResultEpochMillis == latestResult.generatedAtEpochMillis) return
        lastSpokenAssistantResultEpochMillis = latestResult.generatedAtEpochMillis
        startReadAloud(latestResult.headline, buildString {
            append(latestResult.headline)
            if (latestResult.body.isNotBlank()) {
                append(". ")
                append(latestResult.body)
            }
        })
    }

    private fun pauseReadAloudForInterruption() {
        if (assistantAudioState.value.readAloud.status == ReadAloudStatus.Preparing || assistantAudioState.value.readAloud.status == ReadAloudStatus.Speaking) {
            pauseReadAloud()
        }
    }

    private fun splitReadAloudSegments(text: String): List<String> {
        return text
            .split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text.ifBlank { "No text available." }) }
    }

    private fun remapReadAloudEvent(
        event: com.aymanelbanhawy.aiassistant.core.ReadAloudEvent,
        sessionState: ActiveReadAloudSession,
    ): com.aymanelbanhawy.aiassistant.core.ReadAloudEvent {
        return when (event) {
            is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Starting -> com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Starting(
                title = sessionState.title,
                totalSegments = sessionState.segments.size,
            )
            is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.SegmentStarted -> com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.SegmentStarted(
                index = sessionState.startIndex + event.index,
                totalSegments = sessionState.segments.size,
                text = event.text,
            )
            is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Paused -> com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Paused(
                title = sessionState.title,
                index = sessionState.startIndex + event.index,
                totalSegments = sessionState.segments.size,
                text = event.text,
            )
            is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Completed -> com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Completed(sessionState.title)
            is com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Failure -> event
            com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Stopped -> com.aymanelbanhawy.aiassistant.core.ReadAloudEvent.Stopped
        }
    }
    private suspend fun refreshAssistantData() {
        appContainer.aiAssistantRepository.refresh(
            document = session.state.value.document,
            selection = selectedTextSelection.value,
            entitlements = entitlements.value,
            enterpriseState = enterpriseState.value,
        )
        appContainer.aiAssistantRepository.refreshProviderCatalog(entitlements.value, enterpriseState.value)
        syncAssistantStateFromRepository()
    }
    private suspend fun refreshEnterpriseData() {
        val persistedState = appContainer.enterpriseAdminRepository.loadState()
        syncEnterpriseState(persistedState)
        if (persistedState.authSession.isEnterpriseRemote()) {
            syncEnterpriseState(appContainer.enterpriseAdminRepository.refreshRemoteState(force = false))
        }
    }

    private suspend fun syncEnterpriseState(state: EnterpriseAdminStateModel) {
        enterpriseState.value = state
        entitlements.value = resolvedEntitlements(state)
        telemetryEvents.value = appContainer.enterpriseAdminRepository.pendingTelemetry()
        refreshAssistantAvailabilitySnapshot()
        if (activePanel.value == WorkspacePanel.Assistant) {
            appContainer.aiAssistantRepository.refreshProviderCatalog(entitlements.value, enterpriseState.value)
        }
        refreshAssistantAudioAvailability()
    }

    private fun resolvedEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel {
        return EntitlementEngine.resolve(state.plan, state.adminPolicy, state.remoteFeatureOverrides)
    }

    private suspend fun refreshAssistantAvailabilitySnapshot() {
        appContainer.aiAssistantRepository.refresh(
            document = session.state.value.document,
            selection = selectedTextSelection.value,
            entitlements = entitlements.value,
            enterpriseState = enterpriseState.value,
        )
        syncAssistantStateFromRepository()
    }

    private suspend fun queueTelemetry(name: String, properties: Map<String, String> = emptyMap()) {
        appContainer.enterpriseAdminRepository.queueTelemetry(
            newTelemetryEvent(TelemetryCategory.Product, name, properties),
        )
        telemetryEvents.value = appContainer.enterpriseAdminRepository.pendingTelemetry()
    }
    private suspend fun refreshSecurityData() {
        val document = session.state.value.document ?: return
        appLockSettings.value = appContainer.securityRepository.loadAppLockSettings()
        appLockState.value = appContainer.securityRepository.appLockState.value
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(document.documentRef.sourceKey)
        if (document.security != appContainer.securityRepository.loadDocumentSecurity(document.documentRef.sourceKey)) {
            persistCurrentSecurity()
        }
    }

    private fun replaceSecurityDocument(transform: com.aymanelbanhawy.editor.core.security.SecurityDocumentModel.() -> com.aymanelbanhawy.editor.core.security.SecurityDocumentModel) {
        val current = session.state.value.document ?: return
        val before = current.security
        val after = before.transform()
        session.execute(ReplaceSecurityDocumentCommand(before, after))
        viewModelScope.launch { persistCurrentSecurity() }
    }

    private suspend fun persistCurrentSecurity() {
        val document = session.state.value.document ?: return
        appContainer.securityRepository.persistDocumentSecurity(document.documentRef.sourceKey, document.security)
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(document.documentRef.sourceKey)
    }

    private suspend fun recordSecurityAudit(type: AuditEventType, message: String, metadata: Map<String, String> = emptyMap()) {
        val documentKey = session.state.value.document?.documentRef?.sourceKey ?: "__app__"
        appContainer.securityRepository.recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = documentKey,
                type = type,
                actor = "Ayman",
                message = message,
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = metadata,
            ),
        )
        securityAuditEvents.value = appContainer.securityRepository.auditEvents(documentKey)
    }

    private suspend fun refreshCollaborationData() {
        val document = session.state.value.document ?: return
        shareLinks.value = appContainer.collaborationRepository.shareLinks(document.documentRef.sourceKey)
        reviewThreads.value = appContainer.collaborationRepository.reviewThreads(document.documentRef.sourceKey, reviewFilter.value)
        versionSnapshots.value = appContainer.collaborationRepository.versionSnapshots(document.documentRef.sourceKey)
        activityEvents.value = appContainer.collaborationRepository.activityEvents(document.documentRef.sourceKey)
        pendingSyncCount.value = appContainer.collaborationRepository.pendingSyncOperations(document.documentRef.sourceKey).size
    }

    private suspend fun refreshWorkflowData() {
        val document = session.state.value.document ?: return
        workflowState.value = WorkflowStateModel(
            compareReports = appContainer.workflowRepository.compareReports(document.documentRef.sourceKey),
            formTemplates = appContainer.workflowRepository.formTemplates(document.documentRef.sourceKey),
            requests = appContainer.workflowRepository.workflowRequests(document.documentRef.sourceKey),
        )
    }

    private fun refreshCollaborationAsync() { viewModelScope.launch { refreshCollaborationData(); refreshWorkflowData() } }
    private suspend fun recordActivity(type: ActivityEventType, summary: String, threadId: String? = null) {
        val document = session.state.value.document ?: return
        appContainer.collaborationRepository.recordActivity(
            ActivityEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = document.documentRef.sourceKey,
                type = type,
                actor = "Ayman",
                summary = summary,
                createdAtEpochMillis = System.currentTimeMillis(),
                threadId = threadId,
            ),
        )
        refreshCollaborationData()
    }

    private fun focusSearchHit(pageIndex: Int, bounds: NormalizedRect, text: String, selectedIndex: Int) {
        viewModelScope.launch {
            val document = session.state.value.document ?: return@launch
            session.updateSelection(
                session.state.value.selection.copy(
                    selectedPageIndex = pageIndex,
                    selectedAnnotationIds = emptySet(),
                    selectedFormFieldName = null,
                    selectedEditId = null,
                ),
            )
            selectedTextSelection.value = appContainer.documentSearchService.selectionForBounds(document, pageIndex, bounds)
                ?: TextSelectionPayload(pageIndex = pageIndex, text = text, blocks = emptyList())
            searchResults.value = searchResults.value.copy(selectedHitIndex = selectedIndex)
        }
    }

    private fun effectivePageSelection(): Set<Int> = selectedPageIndexes.value.ifEmpty { setOf(session.state.value.selection.selectedPageIndex) }
    private fun refreshThumbnailsAsync() { viewModelScope.launch { refreshThumbnails() } }

    private suspend fun refreshThumbnails() {
        session.state.value.document?.let { thumbnails.value = appContainer.pageThumbnailRepository.thumbnailsFor(it) }
    }

    private fun copyUriToCache(uri: Uri, prefix: String, suffix: String): File? {
        val output = File(appContainer.appContext.cacheDir, "$prefix-${UUID.randomUUID()}$suffix")
        appContainer.appContext.contentResolver.openInputStream(uri)?.use { input -> output.outputStream().use { input.copyTo(it) } } ?: return null
        return output
    }

    companion object {
        fun factory(appContainer: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EditorViewModel(appContainer.createSession(), appContainer.documentRepository, appContainer) as T
            }
        }
    }
}

private fun Set<Int>.toggle(index: Int): Set<Int> = if (index in this) this - index else this + index

private fun RecentDocumentSummary.toUiState(): RecentDocumentUiState = RecentDocumentUiState(
    sourceKey = sourceKey,
    displayName = displayName,
    sourceType = sourceType,
    workingCopyPath = workingCopyPath,
)

private data class ActiveReadAloudSession(
    val title: String,
    val segments: List<String>,
    val startIndex: Int,
)



















































