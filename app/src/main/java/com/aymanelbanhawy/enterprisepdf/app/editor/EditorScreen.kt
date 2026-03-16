package com.aymanelbanhawy.enterprisepdf.app.editor

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.ui.AssistantSidebar
import com.aymanelbanhawy.editor.core.collaboration.ReviewFilterModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountDraft
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.forms.FormFieldType
import com.aymanelbanhawy.editor.core.forms.SignatureKind
import com.aymanelbanhawy.editor.core.security.DocumentPermissionModel
import com.aymanelbanhawy.editor.core.security.MetadataScrubOptionsModel
import com.aymanelbanhawy.editor.core.security.TenantPolicyHooksModel
import com.aymanelbanhawy.editor.core.model.AnnotationModel
import com.aymanelbanhawy.editor.core.model.AnnotationTool
import com.aymanelbanhawy.editor.core.model.EditorAction
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.PageEditModel
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.organize.ThumbnailDescriptor
import com.aymanelbanhawy.editor.core.scan.ScanImportOptions
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.enterprisepdf.app.collaboration.ActivitySidebar
import com.aymanelbanhawy.enterprisepdf.app.connectors.ConnectorExportDialog
import com.aymanelbanhawy.enterprisepdf.app.collaboration.ReviewSidebar
import com.aymanelbanhawy.enterprisepdf.app.enterprise.SettingsAdminSidebar
import com.aymanelbanhawy.enterprisepdf.app.diagnostics.DiagnosticsSidebar
import com.aymanelbanhawy.enterprisepdf.app.forms.FormsSidebar
import com.aymanelbanhawy.enterprisepdf.app.forms.SignatureCaptureDialog
import com.aymanelbanhawy.enterprisepdf.app.scan.ScanImportDialog
import com.aymanelbanhawy.enterprisepdf.app.search.SearchSidebar
import com.aymanelbanhawy.enterprisepdf.app.security.AppLockDialog
import com.aymanelbanhawy.enterprisepdf.app.security.SecuritySidebar
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import com.github.barteksc.pdfviewer.bridge.PdfSessionViewport
import com.github.barteksc.pdfviewer.bridge.PdfViewportCallbacks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest


data class EditorWorkflowExportActions(
    val onCompareAgainstLatestSnapshot: () -> Unit,
    val onOpenCompareMarker: (com.aymanelbanhawy.editor.core.workflow.CompareMarkerModel) -> Unit,
    val onExportText: () -> Unit,
    val onExportMarkdown: () -> Unit,
    val onExportWord: () -> Unit,
    val onExportImages: () -> Unit,
    val onImportSourceAsPdf: () -> Unit,
    val onMergeSources: () -> Unit,
    val onOptimizeHighQuality: () -> Unit,
    val onOptimizeBalanced: () -> Unit,
    val onOptimizeSmallSize: () -> Unit,
    val onOptimizeArchivalSafe: () -> Unit,
)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    events: Flow<EditorSessionEvent>,
    onActionSelected: (EditorAction) -> Unit,
    onDocumentLoaded: (Int) -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onToolSelected: (AnnotationTool) -> Unit,
    onAnnotationCreated: (AnnotationModel) -> Unit,
    onAnnotationUpdated: (AnnotationModel, AnnotationModel) -> Unit,
    onAnnotationSelectionChanged: (Int, Set<String>) -> Unit,
    onFormFieldTapped: (String) -> Unit,
    onPageEditSelectionChanged: (Int, String?) -> Unit,
    onPageEditUpdated: (PageEditModel, PageEditModel) -> Unit,
    onSidebarToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onRecolorSelected: (String) -> Unit,
    onSelectAnnotation: (String) -> Unit,
    onSelectFormField: (String) -> Unit,
    onTextFieldChanged: (String, String) -> Unit,
    onBooleanFieldChanged: (String, Boolean) -> Unit,
    onChoiceFieldChanged: (String, String) -> Unit,
    onSaveFormProfile: (String) -> Unit,
    onApplyFormProfile: (String) -> Unit,
    onExportFormData: () -> Unit,
    onOpenPdf: () -> Unit,
    onOpenFromFiles: () -> Unit,
    onOpenRecentDocument: (RecentDocumentUiState) -> Unit,
    onImportProfile: () -> Unit,
    onOpenSignatureCapture: (String) -> Unit,
    onApplySavedSignature: (String, String) -> Unit,
    onDismissSignatureCapture: () -> Unit,
    onSaveSignatureCapture: (String, SignatureKind, com.aymanelbanhawy.editor.core.forms.SignatureCapture) -> Unit,
    onAddTextBox: () -> Unit,
    onAddImage: () -> Unit,
    onSelectEditObject: (String) -> Unit,
    onDeleteSelectedEdit: () -> Unit,
    onDuplicateSelectedEdit: () -> Unit,
    onReplaceSelectedImage: () -> Unit,
    onSelectedEditTextChanged: (String) -> Unit,
    onSelectedEditFontFamilyChanged: (FontFamilyToken) -> Unit,
    onSelectedEditFontSizeChanged: (Float) -> Unit,
    onSelectedEditColorChanged: (String) -> Unit,
    onSelectedEditAlignmentChanged: (TextAlignment) -> Unit,
    onSelectedEditLineSpacingChanged: (Float) -> Unit,
    onSelectedEditOpacityChanged: (Float) -> Unit,
    onSelectedEditRotationChanged: (Float) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onAssistantPromptChanged: (String) -> Unit,
    onAskPdf: () -> Unit,
    onSummarizeDocumentWithAi: () -> Unit,
    onSummarizePageWithAi: () -> Unit,
    onExtractActionItemsWithAi: () -> Unit,
    onExplainSelectionWithAi: () -> Unit,
    onSemanticSearchWithAi: () -> Unit,
    onAskWorkspaceWithAi: () -> Unit,
    onSummarizeWorkspaceWithAi: () -> Unit,
    onCompareWorkspaceWithAi: () -> Unit,
    onPinCurrentDocumentToAiWorkspace: () -> Unit,
    onToggleAiWorkspaceDocument: (String, Boolean) -> Unit,
    onUnpinAiWorkspaceDocument: (String) -> Unit,
    onSaveAiWorkspaceSet: (String) -> Unit,
    onAssistantPrivacyModeChanged: (AssistantPrivacyMode) -> Unit,
    onAssistantProviderDraftChanged: (AiProviderDraft) -> Unit,
    onSaveAssistantProvider: () -> Unit,
    onRefreshAssistantProviders: () -> Unit,
    onTestAssistantConnection: () -> Unit,
    onCancelAssistantRequest: () -> Unit,
    onOpenAssistantCitation: (Int) -> Unit,
    onStartVoicePromptCapture: () -> Unit,
    onStopVoicePromptCapture: () -> Unit,
    onCancelVoicePromptCapture: () -> Unit,
    onReadCurrentPageAloud: () -> Unit,
    onReadSelectionAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onResumeReadAloud: () -> Unit,
    onStopReadAloud: () -> Unit,
    onAssistantAudioEnabledChanged: (Boolean) -> Unit,
    onNextSearchHit: () -> Unit,
    onPreviousSearchHit: () -> Unit,
    onSelectSearchHit: (Int) -> Unit,
    onUseRecentSearch: (String) -> Unit,
    onOpenOutlineItem: (Int) -> Unit,
    onCopySelectedText: () -> Unit,
    onShareSelectedText: () -> Unit,
    onOcrSettingsChanged: (com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel) -> Unit,
    onSaveOcrSettings: () -> Unit,
    onPauseOcr: (Int?) -> Unit,
    onResumeOcr: (Int?) -> Unit,
    onRerunOcr: (Int?) -> Unit,
    onOpenOcrPage: (Int) -> Unit,
    onShowScanImport: () -> Unit,
    onDismissScanImport: () -> Unit,
    onScanImportOptionsChanged: (ScanImportOptions) -> Unit,
    onPickScanImages: () -> Unit,
    onCreateShareLink: () -> Unit,
    onCreateSnapshot: () -> Unit,
    onSyncNow: () -> Unit,
    workflowExportActions: EditorWorkflowExportActions,
    onCreateFormTemplate: (String) -> Unit,
    onCreateSignatureRequest: (String) -> Unit,
    onCreateFormRequest: (String, String) -> Unit,
    onSendWorkflowReminder: (String) -> Unit,
    onMarkWorkflowRequestCompleted: (String) -> Unit,
    onReviewFilterChanged: (ReviewFilterModel) -> Unit,
    onAddReviewThread: (String, String) -> Unit,
    onAddReviewReply: (String, String) -> Unit,
    onToggleThreadResolved: (String, Boolean) -> Unit,
    onStartThreadVoiceComment: () -> Unit,
    onStopThreadVoiceComment: () -> Unit,
    onCancelThreadVoiceComment: () -> Unit,
    onStartReplyVoiceComment: (String) -> Unit,
    onStopReplyVoiceComment: (String) -> Unit,
    onCancelReplyVoiceComment: (String) -> Unit,
    onPlayVoiceComment: (String) -> Unit,
    onStopVoiceCommentPlayback: () -> Unit,
    onConfigureAppLock: (Boolean, String, Boolean, Int) -> Unit,
    onUnlockWithPin: (String) -> Unit,
    onUnlockWithBiometric: () -> Unit,
    onLockNow: () -> Unit,
    onUpdatePermissions: (DocumentPermissionModel) -> Unit,
    onUpdateTenantPolicy: (TenantPolicyHooksModel) -> Unit,
    onUpdatePasswordProtection: (Boolean, String, String) -> Unit,
    onUpdateWatermark: (Boolean, String) -> Unit,
    onUpdateMetadataScrub: (MetadataScrubOptionsModel) -> Unit,
    onInspectSecurity: () -> Unit,
    onMarkRedaction: () -> Unit,
    onPreviewRedactions: (Boolean) -> Unit,
    onApplyRedactions: () -> Unit,
    onRemoveRedaction: (String) -> Unit,
    onExportAuditTrail: () -> Unit,
    onSignInPersonal: (String) -> Unit,
    onSignInEnterprise: (String, TenantConfigurationModel) -> Unit,
    onSignOutEnterprise: () -> Unit,
    onSetEnterprisePlan: (LicensePlan) -> Unit,
    onUpdateEnterprisePrivacy: (PrivacySettingsModel) -> Unit,
    onUpdateEnterprisePolicy: (AdminPolicyModel) -> Unit,
    onGenerateDiagnosticsBundle: () -> Unit,
    onRefreshEnterpriseRemote: () -> Unit,
    onFlushEnterpriseTelemetry: () -> Unit,
    onSaveConnectorAccount: (ConnectorAccountDraft) -> Unit,
    onTestConnectorConnection: (String) -> Unit,
    onOpenConnectorDocument: (String, String, String) -> Unit,
    onSyncConnectorTransfers: () -> Unit,
    onCleanupConnectorCache: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onRepairRuntimeState: () -> Unit,
    onCleanupRuntimeCaches: () -> Unit,
    onSaveToConnectorEditable: () -> Unit,
    onSaveToConnectorFlattened: () -> Unit,
    onShareToConnectorEditable: () -> Unit,
    onDismissConnectorExportDialog: () -> Unit,
    onConnectorExportAccountChanged: (String) -> Unit,
    onConnectorExportRemotePathChanged: (String) -> Unit,
    onConnectorExportDisplayNameChanged: (String) -> Unit,
    onSubmitConnectorExport: () -> Unit,
    onRotatePage: () -> Unit,
    onReorderPages: () -> Unit,
    onCloseOrganize: () -> Unit,
    onSelectOrganizerPage: (Int, Boolean) -> Unit,
    onMoveOrganizerPage: (Int, Int) -> Unit,
    onDeleteOrganizerPages: () -> Unit,
    onDuplicateOrganizerPages: () -> Unit,
    onSplitRangeChanged: (String) -> Unit,
    onApplySplitRange: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveEditable: () -> Unit,
    onSaveFlattened: () -> Unit,
    onSaveAsEditable: () -> Unit,
    onSaveAsFlattened: () -> Unit,
    onShareRequested: (EditorSessionEvent.ShareDocument) -> Unit,
    onShareTextRequested: (EditorSessionEvent.ShareText) -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var overflowExpanded by remember { mutableStateOf(false) }
    val session = state.session
    val document = session.document
    val hasDocument = document != null
    val hasSelectedAnnotation = state.selectedAnnotation != null
    val hasSignatureFields = state.currentPageFormFields.any { it.type == FormFieldType.Signature }
    val hasConnectorAccounts = state.connectorAccounts.isNotEmpty()

    LaunchedEffect(events) {
        events.collectLatest { event ->
            when (event) {
                is EditorSessionEvent.ShareDocument -> onShareRequested(event)
                is EditorSessionEvent.ShareText -> onShareTextRequested(event)
                is EditorSessionEvent.UserMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (state.appLockState.isLocked && state.appLockSettings.enabled) {
        AppLockDialog(
            settings = state.appLockSettings,
            state = state.appLockState,
            onUnlockWithPin = onUnlockWithPin,
            onUnlockWithBiometric = onUnlockWithBiometric,
        )
    }
    if (state.signatureCaptureVisible) {
        SignatureCaptureDialog(onDismiss = onDismissSignatureCapture, onSave = onSaveSignatureCapture)
    }
    if (state.scanImportVisible) {
        ScanImportDialog(
            options = state.scanImportOptions,
            onOptionsChanged = onScanImportOptionsChanged,
            onDismiss = onDismissScanImport,
            onPickImages = onPickScanImages,
        )
    }
    state.connectorExportDialog?.let { dialog ->
        ConnectorExportDialog(
            state = dialog,
            accounts = state.connectorAccounts,
            onDismiss = onDismissConnectorExportDialog,
            onAccountChanged = onConnectorExportAccountChanged,
            onRemotePathChanged = onConnectorExportRemotePathChanged,
            onDisplayNameChanged = onConnectorExportDisplayNameChanged,
            onSubmit = onSubmitConnectorExport,
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document?.documentRef?.displayName ?: "Enterprise PDF Editor")
                        Text(
                            when {
                                document == null -> "No document"
                                session.isLoading -> "Loading document"
                                else -> "Page ${session.selection.selectedPageIndex + 1} / ${document.pageCount}"
                            },
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Undo, tooltip = "Undo", enabled = session.undoRedoState.canUndo, onClick = onUndo)
                    IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Redo, tooltip = "Redo", enabled = session.undoRedoState.canRedo, onClick = onRedo)
                    IconTooltipButton(icon = Icons.Outlined.FileOpen, tooltip = "Open PDF", onClick = onOpenPdf)
                    IconTooltipButton(
                        icon = Icons.Outlined.Save,
                        tooltip = "Save Editable",
                        enabled = hasDocument,
                        onClick = onSaveEditable,
                    )
                    IconTooltipButton(
                        icon = Icons.Outlined.IosShare,
                        tooltip = "Share",
                        enabled = hasDocument,
                        onClick = { onActionSelected(EditorAction.Share) },
                    )
                    IconTooltipButton(icon = Icons.Outlined.MoreVert, tooltip = "More", onClick = { overflowExpanded = true })
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(text = { Text("Open PDF") }, onClick = { overflowExpanded = false; onOpenPdf() })
                        DropdownMenuItem(text = { Text("Open from Files") }, onClick = { overflowExpanded = false; onOpenFromFiles() })
                        Text(
                            text = "Recent PDFs",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        if (state.recentDocuments.isNotEmpty()) {
                            state.recentDocuments.take(5).forEach { recentDocument ->
                                DropdownMenuItem(
                                    text = { Text(recentDocument.displayName) },
                                    onClick = {
                                        overflowExpanded = false
                                        onOpenRecentDocument(recentDocument)
                                    },
                                )
                            }
                        } else {
                            DropdownMenuItem(text = { Text("No recent PDFs yet") }, enabled = false, onClick = {})
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Save Flattened") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; onSaveFlattened() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save Editable To Connector") },
                            enabled = hasDocument && hasConnectorAccounts,
                            onClick = { overflowExpanded = false; onSaveToConnectorEditable() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save Flattened To Connector") },
                            enabled = hasDocument && hasConnectorAccounts,
                            onClick = { overflowExpanded = false; onSaveToConnectorFlattened() },
                        )
                        DropdownMenuItem(
                            text = { Text("Share To Connector") },
                            enabled = hasDocument && hasConnectorAccounts,
                            onClick = { overflowExpanded = false; onShareToConnectorEditable() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save Editable Copy") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; onSaveAsEditable() },
                        )
                        DropdownMenuItem(
                            text = { Text("Save Flattened Copy") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; onSaveAsFlattened() },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("Import Form Profile (JSON)") }, onClick = { overflowExpanded = false; onImportProfile() })
                        DropdownMenuItem(
                            text = { Text("Scan Images To Searchable PDF…") },
                            onClick = { overflowExpanded = false; onShowScanImport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export Text") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onExportText() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export Markdown") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onExportMarkdown() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export Word") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onExportWord() },
                        )
                        DropdownMenuItem(
                            text = { Text("Export Page Images") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onExportImages() },
                        )
                        DropdownMenuItem(text = { Text("Import Document As PDF") }, onClick = { overflowExpanded = false; workflowExportActions.onImportSourceAsPdf() })
                        DropdownMenuItem(text = { Text("Merge Sources To PDF") }, onClick = { overflowExpanded = false; workflowExportActions.onMergeSources() })
                        DropdownMenuItem(
                            text = { Text("Optimize High Quality") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onOptimizeHighQuality() },
                        )
                        DropdownMenuItem(
                            text = { Text("Optimize Balanced") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onOptimizeBalanced() },
                        )
                        DropdownMenuItem(
                            text = { Text("Optimize Small Size") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onOptimizeSmallSize() },
                        )
                        DropdownMenuItem(
                            text = { Text("Optimize Archival Safe") },
                            enabled = hasDocument,
                            onClick = { overflowExpanded = false; workflowExportActions.onOptimizeArchivalSafe() },
                        )
                        DropdownMenuItem(text = { Text(if (state.annotationSidebarVisible) "Hide Sidebar" else "Show Sidebar") }, onClick = { overflowExpanded = false; onSidebarToggle() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EditorAction.entries.forEach { action ->
                        IconTooltipButton(
                            icon = action.icon(),
                            tooltip = action.tooltipLabel(),
                            selected = when (action) {
                                EditorAction.Organize -> state.organizeVisible
                                EditorAction.Annotate -> state.activePanel == WorkspacePanel.Annotate && !state.organizeVisible
                                EditorAction.Forms -> state.activePanel == WorkspacePanel.Forms
                                EditorAction.Sign -> state.activePanel == WorkspacePanel.Sign
                                EditorAction.Search -> state.activePanel == WorkspacePanel.Search
                                EditorAction.Assistant -> state.activePanel == WorkspacePanel.Assistant
                                EditorAction.Review -> state.activePanel == WorkspacePanel.Review
                                EditorAction.Activity -> state.activePanel == WorkspacePanel.Activity
                                EditorAction.Protect -> state.activePanel == WorkspacePanel.Protect
                                EditorAction.Settings -> state.activePanel == WorkspacePanel.Settings
                                EditorAction.Diagnostics -> state.activePanel == WorkspacePanel.Diagnostics
                                EditorAction.Share -> false
                            },
                            onClick = { onActionSelected(action) },
                        )
                    }
                }
                if (state.activePanel == WorkspacePanel.Annotate && !state.organizeVisible) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnnotationTool.entries.forEach { tool ->
                            IconTooltipButton(
                                icon = tool.icon(),
                                tooltip = tool.label(),
                                selected = state.activeTool == tool,
                                onClick = { onToolSelected(tool) },
                            )
                        }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("#F9AB00", "#0B57D0", "#B3261E", "#137333", "#5E35B1").forEach { colorHex ->
                            ColorChip(
                                colorHex = colorHex,
                                enabled = hasSelectedAnnotation,
                                onClick = { onRecolorSelected(colorHex) },
                            )
                        }
                        IconTooltipButton(
                            icon = Icons.AutoMirrored.Outlined.Article,
                            tooltip = "Add Text Box",
                            enabled = hasDocument,
                            onClick = onAddTextBox,
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.IosShare,
                            tooltip = "Add Image",
                            enabled = hasDocument,
                            onClick = onAddImage,
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.Draw,
                            tooltip = "Open Signature Tools",
                            enabled = hasDocument && hasSignatureFields,
                            onClick = { onActionSelected(EditorAction.Sign) },
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.ContentCopy,
                            tooltip = "Duplicate Selected Annotation",
                            enabled = hasSelectedAnnotation,
                            onClick = onDuplicateSelected,
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.Delete,
                            tooltip = "Delete Selected Annotation",
                            enabled = hasSelectedAnnotation,
                            onClick = onDeleteSelected,
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.Rotate90DegreesCw,
                            tooltip = "Rotate Current Page",
                            enabled = hasDocument,
                            onClick = onRotatePage,
                        )
                        IconTooltipButton(
                            icon = Icons.Outlined.GridView,
                            tooltip = "Open Organizer",
                            enabled = hasDocument,
                            onClick = onReorderPages,
                        )
                    }
                }
                if (state.organizeVisible) {
                    PageOrganizerPane(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        thumbnails = state.thumbnails,
                        selectedPageIndexes = state.selectedPageIndexes,
                        splitRangeExpression = state.splitRangeExpression,
                        onClose = onCloseOrganize,
                        onSelectPage = onSelectOrganizerPage,
                        onMovePage = onMoveOrganizerPage,
                        onDeleteSelected = onDeleteOrganizerPages,
                        onDuplicateSelected = onDuplicateOrganizerPages,
                        onSplitRangeChanged = onSplitRangeChanged,
                        onApplySplitRange = onApplySplitRange,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PdfSessionViewport(
                            modifier = Modifier.fillMaxSize(),
                            document = document,
                            selection = session.selection,
                            activeTool = state.activeTool,
                            currentPage = session.selection.selectedPageIndex,
                            searchHits = state.searchResults.hits,
                            selectedTextBlocks = state.selectedTextSelection?.blocks.orEmpty(),
                            callbacks = PdfViewportCallbacks(
                                onDocumentLoaded = onDocumentLoaded,
                                onPageChanged = onPageChanged,
                                onAnnotationCreated = onAnnotationCreated,
                                onAnnotationUpdated = onAnnotationUpdated,
                                onAnnotationSelectionChanged = onAnnotationSelectionChanged,
                                onFormFieldTapped = onFormFieldTapped,
                                onPageEditSelected = onPageEditSelectionChanged,
                                onPageEditUpdated = onPageEditUpdated,
                            ),
                        )
                    }
                }
            }
            when {
                !state.organizeVisible && state.activePanel == WorkspacePanel.Search && state.annotationSidebarVisible -> {
                    SearchSidebar(
                        modifier = Modifier.width(380.dp).fillMaxHeight().padding(12.dp),
                        query = state.searchQuery,
                        results = state.searchResults,
                        recentSearches = state.recentSearches,
                        outlineItems = state.outlineItems,
                        selectedText = state.selectedTextSelection?.text.orEmpty(),
                        isIndexing = state.isSearchIndexing,
                        ocrJobs = state.ocrJobs,
                        ocrSettings = state.ocrSettings,
                        onQueryChanged = onSearchQueryChanged,
                        onSearch = onSearch,
                        onSelectHit = onSelectSearchHit,
                        onPreviousHit = onPreviousSearchHit,
                        onNextHit = onNextSearchHit,
                        onUseRecentSearch = onUseRecentSearch,
                        onOpenOutlineItem = onOpenOutlineItem,
                        onCopySelectedText = onCopySelectedText,
                        onShareSelectedText = onShareSelectedText,
                        onOcrSettingsChanged = onOcrSettingsChanged,
                        onSaveOcrSettings = onSaveOcrSettings,
                        onPauseOcr = onPauseOcr,
                        onResumeOcr = onResumeOcr,
                        onRerunOcr = onRerunOcr,
                        onOpenOcrPage = onOpenOcrPage,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Assistant && state.annotationSidebarVisible -> {
                    AssistantSidebar(
                        modifier = Modifier.width(420.dp).fillMaxHeight().padding(12.dp),
                        state = state.assistantState,
                        onPromptChanged = onAssistantPromptChanged,
                        onAskPdf = onAskPdf,
                        onSummarizeDocument = onSummarizeDocumentWithAi,
                        onSummarizePage = onSummarizePageWithAi,
                        onExtractActionItems = onExtractActionItemsWithAi,
                        onExplainSelection = onExplainSelectionWithAi,
                        onSemanticSearch = onSemanticSearchWithAi,
                        onAskWorkspace = onAskWorkspaceWithAi,
                        onSummarizeWorkspace = onSummarizeWorkspaceWithAi,
                        onCompareWorkspace = onCompareWorkspaceWithAi,
                        onPinCurrentDocument = onPinCurrentDocumentToAiWorkspace,
                        onToggleWorkspaceDocument = onToggleAiWorkspaceDocument,
                        onUnpinDocument = onUnpinAiWorkspaceDocument,
                        onSaveWorkspaceSet = onSaveAiWorkspaceSet,
                        onPrivacyModeChanged = onAssistantPrivacyModeChanged,
                        onProviderDraftChanged = onAssistantProviderDraftChanged,
                        onSaveProvider = onSaveAssistantProvider,
                        onRefreshProviders = onRefreshAssistantProviders,
                        onTestConnection = onTestAssistantConnection,
                        onCancelRequest = onCancelAssistantRequest,
                        onOpenCitation = onOpenAssistantCitation,
                        onStartVoicePromptCapture = onStartVoicePromptCapture,
                        onStopVoicePromptCapture = onStopVoicePromptCapture,
                        onCancelVoicePromptCapture = onCancelVoicePromptCapture,
                        onReadCurrentPageAloud = onReadCurrentPageAloud,
                        onReadSelectionAloud = onReadSelectionAloud,
                        onPauseReadAloud = onPauseReadAloud,
                        onResumeReadAloud = onResumeReadAloud,
                        onStopReadAloud = onStopReadAloud,
                        onAssistantAudioEnabledChanged = onAssistantAudioEnabledChanged,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Review && state.annotationSidebarVisible -> {
                    ReviewSidebar(
                        modifier = Modifier.width(420.dp).fillMaxHeight().padding(12.dp),
                        shareLinks = state.shareLinks,
                        reviewThreads = state.reviewThreads,
                        snapshots = state.versionSnapshots,
                        compareReports = state.workflowState.compareReports,
                        formTemplates = state.workflowState.formTemplates,
                        workflowRequests = state.workflowState.requests,
                        filter = state.reviewFilter,
                        pendingSyncCount = state.pendingSyncCount,
                        pendingThreadVoiceComment = state.pendingThreadVoiceComment,
                        pendingReplyVoiceComments = state.pendingReplyVoiceComments,
                        activeVoiceCommentPlaybackId = state.activeVoiceCommentPlaybackId,
                        onCreateShareLink = onCreateShareLink,
                        onCreateSnapshot = onCreateSnapshot,
                        onSyncNow = onSyncNow,
                        onCompareAgainstLatestSnapshot = workflowExportActions.onCompareAgainstLatestSnapshot,
                        onCreateFormTemplate = onCreateFormTemplate,
                        onCreateSignatureRequest = onCreateSignatureRequest,
                        onCreateFormRequest = onCreateFormRequest,
                        onFilterChanged = onReviewFilterChanged,
                        onAddThread = onAddReviewThread,
                        onAddReply = onAddReviewReply,
                        onToggleResolved = onToggleThreadResolved,
                        onStartThreadVoiceComment = onStartThreadVoiceComment,
                        onStopThreadVoiceComment = onStopThreadVoiceComment,
                        onCancelThreadVoiceComment = onCancelThreadVoiceComment,
                        onStartReplyVoiceComment = onStartReplyVoiceComment,
                        onStopReplyVoiceComment = onStopReplyVoiceComment,
                        onCancelReplyVoiceComment = onCancelReplyVoiceComment,
                        onPlayVoiceComment = onPlayVoiceComment,
                        onStopVoiceCommentPlayback = onStopVoiceCommentPlayback,
                        onOpenCompareMarker = workflowExportActions.onOpenCompareMarker,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Activity && state.annotationSidebarVisible -> {
                    ActivitySidebar(
                        modifier = Modifier.width(380.dp).fillMaxHeight().padding(12.dp),
                        events = state.activityEvents,
                        requests = state.workflowState.requests,
                        pendingSyncCount = state.pendingSyncCount,
                        onSyncNow = onSyncNow,
                        onSendReminder = onSendWorkflowReminder,
                        onMarkRequestCompleted = onMarkWorkflowRequestCompleted,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Settings && state.annotationSidebarVisible -> {
                    SettingsAdminSidebar(
                        modifier = Modifier.width(420.dp).fillMaxHeight().padding(12.dp),
                        state = state.enterpriseState,
                        entitlements = state.entitlements,
                        telemetryEvents = state.telemetryEvents,
                        diagnosticsCount = state.diagnosticsBundleCount,
                        connectorAccounts = state.connectorAccounts,
                        connectorJobs = state.connectorJobs,
                        onSignInPersonal = onSignInPersonal,
                        onSignInEnterprise = onSignInEnterprise,
                        onSignOut = onSignOutEnterprise,
                        onSetPlan = onSetEnterprisePlan,
                        onUpdatePrivacy = onUpdateEnterprisePrivacy,
                        onUpdatePolicy = onUpdateEnterprisePolicy,
                        onGenerateDiagnostics = onGenerateDiagnosticsBundle,
                        onRefreshRemoteState = onRefreshEnterpriseRemote,
                        onFlushTelemetry = onFlushEnterpriseTelemetry,
                        onSaveConnectorAccount = onSaveConnectorAccount,
                        onTestConnectorConnection = onTestConnectorConnection,
                        onOpenConnectorDocument = onOpenConnectorDocument,
                        onSyncConnectorTransfers = onSyncConnectorTransfers,
                        onCleanupConnectorCache = onCleanupConnectorCache,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Diagnostics && state.annotationSidebarVisible -> {
                    DiagnosticsSidebar(
                        modifier = Modifier.width(420.dp).fillMaxHeight().padding(12.dp),
                        snapshot = state.runtimeDiagnostics,
                        onRefresh = onRefreshDiagnostics,
                        onRepair = onRepairRuntimeState,
                        onCleanupCaches = onCleanupRuntimeCaches,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Protect && state.annotationSidebarVisible -> {
                    SecuritySidebar(
                        modifier = Modifier.width(420.dp).fillMaxHeight().padding(12.dp),
                        security = state.session.document?.security ?: com.aymanelbanhawy.editor.core.security.SecurityDocumentModel(),
                        appLockSettings = state.appLockSettings,
                        auditEvents = state.securityAuditEvents,
                        onConfigureAppLock = onConfigureAppLock,
                        onLockNow = onLockNow,
                        onUpdatePermissions = onUpdatePermissions,
                        onUpdateTenantPolicy = onUpdateTenantPolicy,
                        onUpdatePasswordProtection = onUpdatePasswordProtection,
                        onUpdateWatermark = onUpdateWatermark,
                        onUpdateMetadataScrub = onUpdateMetadataScrub,
                        onInspect = onInspectSecurity,
                        onMarkRedaction = onMarkRedaction,
                        onPreviewRedactions = onPreviewRedactions,
                        onApplyRedactions = onApplyRedactions,
                        onRemoveRedaction = onRemoveRedaction,
                        onExportAudit = onExportAuditTrail,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Annotate && state.selectedEditObject != null && state.annotationSidebarVisible -> {
                    EditInspectorSidebar(
                        modifier = Modifier.width(360.dp).fillMaxHeight().padding(12.dp),
                        editObjects = state.currentPageEditObjects,
                        selectedEditObject = state.selectedEditObject,
                        onSelectEdit = onSelectEditObject,
                        onAddTextBox = onAddTextBox,
                        onAddImage = onAddImage,
                        onDeleteSelected = onDeleteSelectedEdit,
                        onDuplicateSelected = onDuplicateSelectedEdit,
                        onReplaceSelectedImage = onReplaceSelectedImage,
                        onTextChanged = onSelectedEditTextChanged,
                        onFontFamilyChanged = onSelectedEditFontFamilyChanged,
                        onFontSizeChanged = onSelectedEditFontSizeChanged,
                        onTextColorChanged = onSelectedEditColorChanged,
                        onTextAlignmentChanged = onSelectedEditAlignmentChanged,
                        onLineSpacingChanged = onSelectedEditLineSpacingChanged,
                        onOpacityChanged = onSelectedEditOpacityChanged,
                        onRotationChanged = onSelectedEditRotationChanged,
                    )
                }
                !state.organizeVisible && state.activePanel == WorkspacePanel.Annotate && state.annotationSidebarVisible -> {
                    AnnotationSidebar(
                        modifier = Modifier.width(320.dp).fillMaxHeight().padding(12.dp),
                        annotations = state.currentPageAnnotations,
                        selectedAnnotationId = state.selectedAnnotation?.id,
                        onSelectAnnotation = onSelectAnnotation,
                    )
                }
                !state.organizeVisible &&
                    state.annotationSidebarVisible &&
                    (state.activePanel == WorkspacePanel.Forms || state.activePanel == WorkspacePanel.Sign) -> {
                    FormsSidebar(
                        modifier = Modifier.width(360.dp).fillMaxHeight().padding(12.dp),
                        activeSignMode = state.activePanel == WorkspacePanel.Sign,
                        fields = state.currentPageFormFields,
                        selectedField = state.selectedFormField,
                        validationMessage = session.formValidationSummary.issueFor(state.selectedFormField?.name.orEmpty())?.message,
                        profiles = state.formProfiles,
                        signatures = state.savedSignatures,
                        onSelectField = onSelectFormField,
                        onTextChanged = onTextFieldChanged,
                        onBooleanChanged = onBooleanFieldChanged,
                        onChoiceChanged = onChoiceFieldChanged,
                        onSaveProfile = onSaveFormProfile,
                        onApplyProfile = onApplyFormProfile,
                        onExportFormData = onExportFormData,
                        onOpenSignatureCapture = onOpenSignatureCapture,
                        onApplySignature = onApplySavedSignature,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnnotationSidebar(
    modifier: Modifier,
    annotations: List<AnnotationModel>,
    selectedAnnotationId: String?,
    onSelectAnnotation: (String) -> Unit,
) {
    Surface(modifier = modifier, tonalElevation = 2.dp, shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null)
                Text("Annotations", style = MaterialTheme.typography.titleMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(annotations, key = { it.id }) { annotation ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        tonalElevation = if (annotation.id == selectedAnnotationId) 4.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { onSelectAnnotation(annotation.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(annotation.type.name, style = MaterialTheme.typography.labelLarge)
                            Text(annotation.commentThread.author, style = MaterialTheme.typography.bodyMedium)
                            Text(annotation.commentThread.status.name, style = MaterialTheme.typography.bodySmall)
                            if (annotation.text.isNotBlank()) {
                                Text(annotation.text, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (annotation.commentThread.replies.isNotEmpty()) {
                                Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp)) {
                                    annotation.commentThread.replies.take(2).forEach { reply ->
                                        Text("${reply.author}: ${reply.message}", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageOrganizerPane(
    modifier: Modifier = Modifier,
    thumbnails: List<ThumbnailDescriptor>,
    selectedPageIndexes: Set<Int>,
    splitRangeExpression: String,
    onClose: () -> Unit,
    onSelectPage: (Int, Boolean) -> Unit,
    onMovePage: (Int, Int) -> Unit,
    onDeleteSelected: () -> Unit,
    onDuplicateSelected: () -> Unit,
    onSplitRangeChanged: (String) -> Unit,
    onApplySplitRange: () -> Unit,
) {
    val hasSelection = selectedPageIndexes.isNotEmpty()

    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Page Organizer", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${thumbnails.size} pages",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(onClick = onClose) {
                    Text("Back to Editor")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconTooltipButton(
                    icon = Icons.Outlined.Delete,
                    tooltip = "Delete Selected Pages",
                    enabled = hasSelection,
                    onClick = onDeleteSelected,
                )
                IconTooltipButton(
                    icon = Icons.Outlined.ContentCopy,
                    tooltip = "Duplicate Selected Pages",
                    enabled = hasSelection,
                    onClick = onDuplicateSelected,
                )
            }

            androidx.compose.material3.OutlinedTextField(
                value = splitRangeExpression,
                onValueChange = onSplitRangeChanged,
                label = { Text("Split range") },
                placeholder = { Text("Examples: 1-3,5 or leave selection active") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            TextButton(
                onClick = onApplySplitRange,
                enabled = hasSelection || splitRangeExpression.isNotBlank(),
            ) {
                Text("Apply Split")
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                thumbnails.forEach { thumbnail ->
                    val selected = thumbnail.pageIndex in selectedPageIndexes
                    val preview = remember(thumbnail.imagePath) {
                        BitmapFactory.decodeFile(thumbnail.imagePath)?.asImageBitmap()
                    }

                    Surface(
                        modifier = Modifier.width(156.dp),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = if (selected) 4.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        onClick = { onSelectPage(thumbnail.pageIndex, false) },
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.75f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                            ) {
                                if (preview != null) {
                                    Image(
                                        bitmap = preview,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "Page ${thumbnail.pageIndex + 1}",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Page ${thumbnail.pageIndex + 1}",
                                style = MaterialTheme.typography.labelLarge,
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconTooltipButton(
                                    icon = if (selected) {
                                        Icons.Outlined.CheckBox
                                    } else {
                                        Icons.Outlined.CheckBoxOutlineBlank
                                    },
                                    tooltip = "Toggle multi-select",
                                    onClick = { onSelectPage(thumbnail.pageIndex, true) },
                                )

                                IconTooltipButton(
                                    icon = Icons.AutoMirrored.Outlined.ArrowBack,
                                    tooltip = "Move Left",
                                    enabled = thumbnail.pageIndex > 0,
                                    onClick = {
                                        onMovePage(thumbnail.pageIndex, thumbnail.pageIndex - 1)
                                    },
                                )

                                IconTooltipButton(
                                    icon = Icons.AutoMirrored.Outlined.ArrowForward,
                                    tooltip = "Move Right",
                                    enabled = thumbnail.pageIndex < thumbnails.lastIndex,
                                    onClick = {
                                        onMovePage(thumbnail.pageIndex, thumbnail.pageIndex + 1)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChip(
    colorHex: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = Color(colorHex.toColorInt())
    TextButton(onClick = onClick, enabled = enabled) {
        Box(modifier = Modifier.clip(CircleShape).background(color).width(28.dp).padding(vertical = 14.dp))
    }
}

private fun AnnotationTool.icon() = when (this) {
    AnnotationTool.Select -> Icons.Outlined.BorderColor
    AnnotationTool.Highlight -> Icons.Outlined.BorderColor
    AnnotationTool.Underline -> Icons.Outlined.BorderColor
    AnnotationTool.Strikeout -> Icons.Outlined.BorderColor
    AnnotationTool.FreehandInk -> Icons.Outlined.BorderColor
    AnnotationTool.Rectangle -> Icons.Outlined.GridView
    AnnotationTool.Ellipse -> Icons.Outlined.GridView
    AnnotationTool.Arrow -> Icons.Outlined.IosShare
    AnnotationTool.Line -> Icons.Outlined.IosShare
    AnnotationTool.StickyNote -> Icons.AutoMirrored.Outlined.Comment
    AnnotationTool.TextBox -> Icons.AutoMirrored.Outlined.Article
}

private fun AnnotationTool.label(): String = when (this) {
    AnnotationTool.Select -> "Select"
    AnnotationTool.Highlight -> "Highlight"
    AnnotationTool.Underline -> "Underline"
    AnnotationTool.Strikeout -> "Strikeout"
    AnnotationTool.FreehandInk -> "Ink"
    AnnotationTool.Rectangle -> "Rectangle"
    AnnotationTool.Ellipse -> "Ellipse"
    AnnotationTool.Arrow -> "Arrow"
    AnnotationTool.Line -> "Line"
    AnnotationTool.StickyNote -> "Note"
    AnnotationTool.TextBox -> "Text"
}











private fun EditorAction.icon() = when (this) {
    EditorAction.Annotate -> Icons.Outlined.BorderColor
    EditorAction.Organize -> Icons.Outlined.GridView
    EditorAction.Forms -> Icons.Outlined.FactCheck
    EditorAction.Sign -> Icons.AutoMirrored.Outlined.Article
    EditorAction.Search -> Icons.Outlined.Search
    EditorAction.Protect -> Icons.Outlined.AdminPanelSettings
    EditorAction.Share -> Icons.Outlined.IosShare
    EditorAction.Assistant -> Icons.Outlined.AutoAwesome
    EditorAction.Review -> Icons.AutoMirrored.Outlined.Comment
    EditorAction.Activity -> Icons.Outlined.History
    EditorAction.Settings -> Icons.Outlined.Settings
    EditorAction.Diagnostics -> Icons.Outlined.BugReport
}

private fun EditorAction.tooltipLabel(): String = when (this) {
    EditorAction.Annotate -> "Annotate"
    EditorAction.Organize -> "Organize"
    EditorAction.Forms -> "Forms"
    EditorAction.Sign -> "Sign"
    EditorAction.Search -> "Search"
    EditorAction.Protect -> "Protect"
    EditorAction.Share -> "Share"
    EditorAction.Assistant -> "Assistant"
    EditorAction.Review -> "Review"
    EditorAction.Activity -> "Activity"
    EditorAction.Settings -> "Settings"
    EditorAction.Diagnostics -> "Diagnostics"
}














