package com.aymanelbanhawy.enterprisepdf.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.aymanelbanhawy.editor.core.session.EditorSessionEvent
import com.aymanelbanhawy.editor.core.workflow.PdfOptimizationPreset
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorScreenRoute
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorScreenCallbacks
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorViewModel
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorWorkflowExportActions
import com.aymanelbanhawy.enterprisepdf.app.open.PdfOpenIntentResolver

@Composable
fun EditorWorkspaceHost(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onShareDocument: (EditorSessionEvent.ShareDocument) -> Unit,
    onShareText: (EditorSessionEvent.ShareText) -> Unit,
) {
    val context = LocalContext.current
    // Basic PDF open stays on the Android Storage Access Framework so Google Drive,
    // Downloads, Documents, and on-device providers all appear through one picker.
    val openPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        persistReadPermission(context, uri)
        val request = PdfOpenIntentResolver.resolveSafSelection(context, uri)
        if (request != null) {
            viewModel.openIncomingDocument(request)
        } else if (uri != null) {
            viewModel.showUserMessage("The selected file is not a readable PDF")
        }
    }
    val mergeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.mergeDocuments(uris)
    }
    val importSourceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importSourceAsPdf)
    }
    val editImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::addImageEdit)
    }
    val replaceEditImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::replaceSelectedImage)
    }
    val profileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let(viewModel::importFormProfile)
    }
    val scanImagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        viewModel.importScanImages(uris)
    }
    var pendingAudioAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingAudioAction?.invoke()
        } else {
            viewModel.cancelVoicePromptCapture()
            viewModel.cancelAllPendingVoiceComments()
        }
        pendingAudioAction = null
    }
    val workflowExportActions = rememberWorkflowExportActions(viewModel, importSourceLauncher, mergeLauncher)
    val callbacks = rememberEditorScreenCallbacks(
        viewModel = viewModel,
        onShareDocument = onShareDocument,
        onShareText = onShareText,
        workflowExportActions = workflowExportActions,
        onOpenPdf = { openPdfLauncher.launch(arrayOf("application/pdf")) },
        onOpenFromFiles = { openPdfLauncher.launch(arrayOf("application/pdf")) },
        onImportProfile = { profileLauncher.launch("application/json") },
        onAddImage = { editImageLauncher.launch("image/*") },
        onReplaceSelectedImage = { replaceEditImageLauncher.launch("image/*") },
        onPickScanImages = { scanImagesLauncher.launch("image/*") },
        onRequestVoicePromptCapture = {
            pendingAudioAction = viewModel::startVoicePromptCapture
            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        },
        onRequestThreadVoiceComment = {
            pendingAudioAction = viewModel::startVoiceCommentForNewThread
            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        },
        onRequestReplyVoiceComment = { threadId ->
            pendingAudioAction = { viewModel.startVoiceCommentReply(threadId) }
            microphonePermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        },
    )

    EditorScreenRoute(
        state = state,
        events = viewModel.events,
        callbacks = callbacks,
    )
}

@Composable
private fun rememberWorkflowExportActions(
    viewModel: EditorViewModel,
    importSourceLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, Uri?>,
    mergeLauncher: androidx.activity.compose.ManagedActivityResultLauncher<Array<String>, List<Uri>>,
): EditorWorkflowExportActions {
    return EditorWorkflowExportActions(
        onCompareAgainstLatestSnapshot = viewModel::compareAgainstLatestSnapshot,
        onOpenCompareMarker = viewModel::openCompareMarker,
        onExportText = viewModel::exportDocumentAsText,
        onExportMarkdown = viewModel::exportDocumentAsMarkdown,
        onExportWord = viewModel::exportDocumentAsWord,
        onExportImages = viewModel::exportDocumentAsImages,
        onImportSourceAsPdf = {
            importSourceLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "text/markdown",
                    "image/*",
                ),
            )
        },
        onMergeSources = {
            mergeLauncher.launch(
                arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "text/plain",
                    "text/markdown",
                    "image/*",
                ),
            )
        },
        onOptimizeHighQuality = { viewModel.optimizeDocument(PdfOptimizationPreset.HighQuality) },
        onOptimizeBalanced = { viewModel.optimizeDocument(PdfOptimizationPreset.Balanced) },
        onOptimizeSmallSize = { viewModel.optimizeDocument(PdfOptimizationPreset.SmallSize) },
        onOptimizeArchivalSafe = { viewModel.optimizeDocument(PdfOptimizationPreset.ArchivalSafe) },
    )
}

private fun rememberEditorScreenCallbacks(
    viewModel: EditorViewModel,
    onShareDocument: (EditorSessionEvent.ShareDocument) -> Unit,
    onShareText: (EditorSessionEvent.ShareText) -> Unit,
    workflowExportActions: EditorWorkflowExportActions,
    onOpenPdf: () -> Unit,
    onOpenFromFiles: () -> Unit,
    onImportProfile: () -> Unit,
    onAddImage: () -> Unit,
    onReplaceSelectedImage: () -> Unit,
    onPickScanImages: () -> Unit,
    onRequestVoicePromptCapture: () -> Unit,
    onRequestThreadVoiceComment: () -> Unit,
    onRequestReplyVoiceComment: (String) -> Unit,
): EditorScreenCallbacks = EditorScreenCallbacks(
    onActionSelected = viewModel::onActionSelected,
    onDocumentLoaded = viewModel::onDocumentLoaded,
    onPageChanged = viewModel::onPageChanged,
    onToolSelected = viewModel::onToolSelected,
    onAnnotationCreated = viewModel::onAnnotationCreated,
    onAnnotationUpdated = viewModel::onAnnotationUpdated,
    onAnnotationSelectionChanged = viewModel::onAnnotationSelectionChanged,
    onFormFieldTapped = viewModel::onFormFieldTapped,
    onPageEditSelectionChanged = viewModel::onPageEditSelectionChanged,
    onPageEditUpdated = viewModel::onPageEditUpdated,
    onSidebarToggle = viewModel::toggleAnnotationSidebar,
    onDeleteSelected = viewModel::deleteSelectedAnnotation,
    onDuplicateSelected = viewModel::duplicateSelectedAnnotation,
    onRecolorSelected = viewModel::recolorSelectedAnnotation,
    onSelectAnnotation = viewModel::selectAnnotation,
    onSelectFormField = viewModel::selectFormField,
    onTextFieldChanged = viewModel::updateTextField,
    onBooleanFieldChanged = viewModel::toggleBooleanField,
    onChoiceFieldChanged = viewModel::updateChoiceField,
    onSaveFormProfile = viewModel::saveFormProfile,
    onApplyFormProfile = viewModel::applyFormProfile,
    onExportFormData = viewModel::exportCurrentFormData,
    onOpenPdf = onOpenPdf,
    onOpenFromFiles = onOpenFromFiles,
    onOpenRecentDocument = viewModel::openRecentDocument,
    onImportProfile = onImportProfile,
    onOpenSignatureCapture = viewModel::openSignatureCapture,
    onApplySavedSignature = viewModel::applySavedSignature,
    onDismissSignatureCapture = viewModel::dismissSignatureCapture,
    onSaveSignatureCapture = viewModel::saveSignatureAndApply,
    onAddTextBox = viewModel::addTextBox,
    onAddImage = onAddImage,
    onSelectEditObject = viewModel::selectPageEdit,
    onDeleteSelectedEdit = viewModel::deleteSelectedEdit,
    onDuplicateSelectedEdit = viewModel::duplicateSelectedEdit,
    onReplaceSelectedImage = onReplaceSelectedImage,
    onSelectedEditTextChanged = viewModel::updateSelectedTextContent,
    onSelectedEditFontFamilyChanged = { viewModel.updateSelectedTextStyle(fontFamily = it) },
    onSelectedEditFontSizeChanged = { viewModel.updateSelectedTextStyle(fontSizeSp = it) },
    onSelectedEditColorChanged = { viewModel.updateSelectedTextStyle(textColorHex = it) },
    onSelectedEditAlignmentChanged = { viewModel.updateSelectedTextStyle(alignment = it) },
    onSelectedEditLineSpacingChanged = { viewModel.updateSelectedTextStyle(lineSpacingMultiplier = it) },
    onSelectedEditOpacityChanged = viewModel::updateSelectedEditOpacity,
    onSelectedEditRotationChanged = viewModel::updateSelectedEditRotation,
    onSearchQueryChanged = viewModel::updateSearchQuery,
    onSearch = viewModel::performSearch,
    onAssistantPromptChanged = viewModel::updateAssistantPrompt,
    onAskPdf = viewModel::askPdf,
    onSummarizeDocumentWithAi = viewModel::summarizeDocumentWithAi,
    onSummarizePageWithAi = viewModel::summarizeCurrentPageWithAi,
    onExtractActionItemsWithAi = viewModel::extractActionItemsWithAi,
    onExplainSelectionWithAi = viewModel::explainSelectionWithAi,
    onSemanticSearchWithAi = viewModel::runAiSemanticSearch,
    onAskWorkspaceWithAi = viewModel::askWorkspaceWithAi,
    onSummarizeWorkspaceWithAi = viewModel::summarizeWorkspaceWithAi,
    onCompareWorkspaceWithAi = viewModel::compareWorkspaceWithAi,
    onPinCurrentDocumentToAiWorkspace = viewModel::pinCurrentDocumentToAiWorkspace,
    onToggleAiWorkspaceDocument = viewModel::toggleAiWorkspaceDocument,
    onUnpinAiWorkspaceDocument = viewModel::unpinAiWorkspaceDocument,
    onSaveAiWorkspaceSet = viewModel::saveAiWorkspaceDocumentSet,
    onAssistantPrivacyModeChanged = viewModel::updateAssistantPrivacyMode,
    onAssistantProviderDraftChanged = viewModel::updateAssistantProviderDraft,
    onSaveAssistantProvider = viewModel::saveAssistantProvider,
    onRefreshAssistantProviders = viewModel::refreshAssistantProviders,
    onTestAssistantConnection = viewModel::testAssistantConnection,
    onCancelAssistantRequest = viewModel::cancelAssistantRequest,
    onOpenAssistantCitation = viewModel::openAssistantCitation,
    onStartVoicePromptCapture = onRequestVoicePromptCapture,
    onStopVoicePromptCapture = viewModel::stopVoicePromptCapture,
    onCancelVoicePromptCapture = viewModel::cancelVoicePromptCapture,
    onReadCurrentPageAloud = viewModel::readCurrentPageAloud,
    onReadSelectionAloud = viewModel::readSelectedTextAloud,
    onPauseReadAloud = viewModel::pauseReadAloud,
    onResumeReadAloud = viewModel::resumeReadAloud,
    onStopReadAloud = viewModel::stopReadAloud,
    onAssistantAudioEnabledChanged = viewModel::setAssistantAudioEnabled,
    onNextSearchHit = viewModel::nextSearchHit,
    onPreviousSearchHit = viewModel::previousSearchHit,
    onSelectSearchHit = viewModel::selectSearchHit,
    onUseRecentSearch = { query -> viewModel.updateSearchQuery(query); viewModel.performSearch(query) },
    onOpenOutlineItem = viewModel::openOutlineItem,
    onCopySelectedText = viewModel::copySelectedText,
    onShareSelectedText = viewModel::shareSelectedText,
    onOcrSettingsChanged = viewModel::updateOcrSettings,
    onSaveOcrSettings = viewModel::saveOcrSettings,
    onPauseOcr = viewModel::pauseOcr,
    onResumeOcr = viewModel::resumeOcr,
    onRerunOcr = viewModel::rerunOcr,
    onOpenOcrPage = viewModel::openOcrPage,
    onShowScanImport = viewModel::showScanImportDialog,
    onDismissScanImport = viewModel::dismissScanImportDialog,
    onScanImportOptionsChanged = viewModel::updateScanImportOptions,
    onPickScanImages = onPickScanImages,
    onCreateShareLink = viewModel::createShareLink,
    onCreateSnapshot = viewModel::createVersionSnapshot,
    onSyncNow = viewModel::syncCollaboration,
    workflowExportActions = workflowExportActions,
    onCreateFormTemplate = viewModel::createCurrentFormTemplate,
    onCreateSignatureRequest = viewModel::createSignatureRequest,
    onCreateFormRequest = viewModel::createFormRequest,
    onSendWorkflowReminder = viewModel::sendWorkflowReminder,
    onMarkWorkflowRequestCompleted = viewModel::markWorkflowRequestCompleted,
    onReviewFilterChanged = viewModel::updateReviewFilter,
    onAddReviewThread = viewModel::addReviewThread,
    onAddReviewReply = viewModel::addReviewReply,
    onToggleThreadResolved = viewModel::toggleThreadResolved,
    onStartThreadVoiceComment = onRequestThreadVoiceComment,
    onStopThreadVoiceComment = viewModel::stopVoiceCommentForNewThread,
    onCancelThreadVoiceComment = viewModel::cancelVoiceCommentForNewThread,
    onStartReplyVoiceComment = onRequestReplyVoiceComment,
    onStopReplyVoiceComment = viewModel::stopVoiceCommentReply,
    onCancelReplyVoiceComment = viewModel::cancelVoiceCommentReply,
    onPlayVoiceComment = viewModel::playVoiceComment,
    onStopVoiceCommentPlayback = viewModel::stopVoiceCommentPlayback,
    onConfigureAppLock = viewModel::configureAppLock,
    onUnlockWithPin = viewModel::unlockWithPin,
    onUnlockWithBiometric = viewModel::unlockWithBiometric,
    onLockNow = viewModel::lockNow,
    onUpdatePermissions = viewModel::updateDocumentPermissions,
    onUpdateTenantPolicy = viewModel::updateTenantPolicy,
    onUpdatePasswordProtection = viewModel::updatePasswordProtection,
    onUpdateWatermark = viewModel::updateWatermark,
    onUpdateMetadataScrub = viewModel::updateMetadataScrub,
    onInspectSecurity = viewModel::generateInspectionReport,
    onMarkRedaction = viewModel::markSelectedTextForRedaction,
    onPreviewRedactions = viewModel::setRedactionPreview,
    onApplyRedactions = viewModel::applyRedactions,
    onRemoveRedaction = viewModel::removeRedactionMark,
    onExportAuditTrail = viewModel::exportAuditTrail,
    onSignInPersonal = viewModel::signInPersonal,
    onSignInEnterprise = viewModel::signInEnterprise,
    onSignOutEnterprise = viewModel::signOutEnterprise,
    onSetEnterprisePlan = viewModel::setEnterprisePlan,
    onUpdateEnterprisePrivacy = viewModel::updateEnterprisePrivacy,
    onUpdateEnterprisePolicy = viewModel::updateEnterprisePolicy,
    onGenerateDiagnosticsBundle = viewModel::generateDiagnosticsBundle,
    onRefreshEnterpriseRemote = viewModel::refreshEnterpriseRemoteState,
    onFlushEnterpriseTelemetry = viewModel::flushEnterpriseTelemetry,
    onSaveConnectorAccount = viewModel::saveConnectorAccount,
    onTestConnectorConnection = viewModel::testConnectorConnection,
    onOpenConnectorDocument = viewModel::openDocumentFromConnector,
    onSyncConnectorTransfers = viewModel::syncConnectorTransfers,
    onCleanupConnectorCache = viewModel::cleanupConnectorCache,
    onRefreshDiagnostics = viewModel::refreshDiagnostics,
    onRepairRuntimeState = viewModel::repairRuntimeState,
    onCleanupRuntimeCaches = viewModel::cleanupConnectorCache,
    onSaveToConnectorEditable = viewModel::saveToConnectorEditable,
    onSaveToConnectorFlattened = viewModel::saveToConnectorFlattened,
    onShareToConnectorEditable = viewModel::shareToConnectorEditable,
    onDismissConnectorExportDialog = viewModel::dismissConnectorExportDialog,
    onConnectorExportAccountChanged = viewModel::updateConnectorExportAccount,
    onConnectorExportRemotePathChanged = viewModel::updateConnectorExportRemotePath,
    onConnectorExportDisplayNameChanged = viewModel::updateConnectorExportDisplayName,
    onSubmitConnectorExport = viewModel::submitConnectorExport,
    onRotatePage = viewModel::rotateCurrentPage,
    onReorderPages = viewModel::showOrganize,
    onUndo = viewModel::undo,
    onRedo = viewModel::redo,
    onSaveEditable = viewModel::saveEditable,
    onSaveFlattened = viewModel::saveFlattened,
    onSaveAsEditable = viewModel::saveAsEditable,
    onSaveAsFlattened = viewModel::saveAsFlattened,
    onShareRequested = onShareDocument,
    onShareTextRequested = onShareText,
)

private fun persistReadPermission(context: android.content.Context, uri: Uri?) {
    if (uri == null) return
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}


