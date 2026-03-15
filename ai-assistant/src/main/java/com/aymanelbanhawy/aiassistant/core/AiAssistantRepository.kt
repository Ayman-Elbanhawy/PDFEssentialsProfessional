package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import com.aymanelbanhawy.editor.core.data.RecentDocumentDao
import com.aymanelbanhawy.editor.core.data.RecentDocumentEntity
import com.aymanelbanhawy.editor.core.enterprise.AiDocumentScopePolicy
import com.aymanelbanhawy.editor.core.enterprise.AiFeatureAccessResolution
import com.aymanelbanhawy.editor.core.enterprise.AiFeatureAccessResolver
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.forms.FormFieldValue
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.search.DocumentSearchService
import com.aymanelbanhawy.editor.core.search.ExtractedTextBlock
import com.aymanelbanhawy.editor.core.search.IndexedPageContent
import com.aymanelbanhawy.editor.core.search.TextSelectionPayload
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

interface AiAssistantRepository {
    val state: StateFlow<AssistantUiState>
    suspend fun refresh(document: DocumentModel?, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun updatePrompt(prompt: String)
    suspend fun updateSettings(settings: AssistantSettings)
    suspend fun updateProviderDraft(draft: AiProviderDraft)
    suspend fun saveProviderDraft(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun refreshProviderCatalog(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun testProviderConnection(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun cancelActiveRequest()
    suspend fun pinDocument(document: DocumentModel)
    suspend fun unpinDocument(documentKey: String)
    suspend fun toggleWorkspaceDocument(documentKey: String, selected: Boolean)
    suspend fun saveWorkspaceDocumentSet(title: String)
    suspend fun askPdf(document: DocumentModel, question: String, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun summarizeDocument(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun summarizePage(document: DocumentModel, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun extractActionItems(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun explainSelection(document: DocumentModel, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun semanticSearch(document: DocumentModel, query: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun askAcrossWorkspace(currentDocument: DocumentModel?, question: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun summarizeWorkspace(currentDocument: DocumentModel?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
    suspend fun compareAndSummarizeWorkspace(currentDocument: DocumentModel?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel)
}

class DefaultAiAssistantRepository(
    private val documentSearchService: DocumentSearchService,
    private val documentRepository: DocumentRepository,
    private val recentDocumentDao: RecentDocumentDao,
    private val settingsStore: AiProviderSettingsStore,
    private val workspaceStore: AiWorkspaceStore,
    private val providerRuntime: AiProviderRuntime,
) : AiAssistantRepository {
    private val mutableState = MutableStateFlow(AssistantUiState())
    private var activeInvocation: ProviderInvocation? = null
    private var latestEntitlements: EntitlementStateModel = EntitlementStateModel(plan = com.aymanelbanhawy.editor.core.enterprise.LicensePlan.Free, features = emptySet())
    private var latestEnterpriseState: EnterpriseAdminStateModel = EnterpriseAdminStateModel()
    override val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    suspend fun initialize() {
        val persisted = settingsStore.load()
        val profiles = persisted.profiles.ifEmpty { defaultProviderProfiles() }
        val selected = profiles.firstOrNull { it.id == persisted.selectedProviderId }?.id ?: profiles.first().id
        val workspace = workspaceStore.load()
        mutableState.value = AssistantUiState(
            settings = persisted.settings,
            availability = AssistantAvailability(false, "AI is disabled"),
            providerRuntime = AiProviderRuntimeState(selectedProviderId = selected, profiles = profiles, draft = profiles.first { it.id == selected }.toDraft()),
            workspace = workspace,
            conversation = workspace.conversationHistory,
        )
    }

    override suspend fun refresh(document: DocumentModel?, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        latestEntitlements = entitlements
        latestEnterpriseState = enterpriseState
        val availability = resolveAvailability(entitlements, enterpriseState)
        val policy = workspacePolicyFor(enterpriseState)
        val recent = recentDocumentDao.observeRecent(12).first().map { it.toWorkspaceRef() }
        val pinned = workspaceStore.pinnedDocuments()
        val selected = mutableState.value.workspace.selectedDocumentKeys
            .filter { key -> (pinned + recent).any { it.documentKey == key } }
            .toSet()
            .ifEmpty { pinned.take(policy.maxDocumentCount).map { it.documentKey }.toSet() }
        workspaceStore.pruneHistory(policy.retentionDays)
        mutableState.update {
            it.copy(
                availability = availability,
                providerRuntime = it.providerRuntime.copy(discoveredLocalApps = providerRuntime.discoverLocalApps()),
                workspace = it.workspace.copy(
                    pinnedDocuments = pinned,
                    availableRecentDocuments = mergeWorkspaceRefs(document, recent),
                    selectedDocumentKeys = selected,
                    conversationHistory = workspaceStore.messages(),
                    workspaceSummaries = workspaceStore.summaries(),
                    recentDocumentSets = workspaceStore.recentSets(),
                    policy = policy,
                ),
                conversation = workspaceStore.messages(),
                suggestions = if (availability.enabled && document != null && it.settings.allowSuggestions) buildSuggestions(document, selection, enterpriseState) else emptyList(),
            )
        }
    }

    override suspend fun updatePrompt(prompt: String) {
        mutableState.update { it.copy(prompt = prompt) }
    }

    override suspend fun updateSettings(settings: AssistantSettings) {
        mutableState.update { it.copy(settings = settings) }
        persistState()
    }

    override suspend fun updateProviderDraft(draft: AiProviderDraft) {
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(draft = draft, selectedProviderId = draft.profileId, lastError = null)) }
    }
    override suspend fun saveProviderDraft(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val draft = mutableState.value.providerRuntime.draft
        val profiles = mutableState.value.providerRuntime.profiles.toMutableList()
        providerRuntime.saveCredential(draft.profileId, draft.apiKeyInput.ifBlank { null })
        val updated = draft.toProfile(draft.apiKeyInput.isNotBlank() || profiles.firstOrNull { it.id == draft.profileId }?.hasStoredCredential == true)
        val index = profiles.indexOfFirst { it.id == draft.profileId }
        if (index >= 0) profiles[index] = updated else profiles += updated
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(profiles = profiles, selectedProviderId = updated.id, draft = updated.toDraft(), diagnosticsMessage = "Saved provider settings for ${updated.displayName}.")) }
        persistState()
        refreshProviderCatalog(entitlements, enterpriseState)
        providerRuntime.auditProviderSwitch(updated)
        if (updated.modelId.isNotBlank()) providerRuntime.auditModelSwitch(updated)
    }

    override suspend fun refreshProviderCatalog(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        latestEntitlements = entitlements
        latestEnterpriseState = enterpriseState
        val availability = resolveAvailability(entitlements, enterpriseState)
        val current = mutableState.value.providerRuntime.currentProfile() ?: return
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Discovering, lastError = null)) }
        runCatching {
            val selection = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
            val selected = selection.profile ?: error(selection.blockedReason ?: "No provider available")
            val catalog = providerRuntime.loadCatalog(selected)
            mutableState.update {
                it.copy(availability = availability, providerRuntime = it.providerRuntime.copy(selectedProviderId = selected.id, draft = selected.toDraft(), availableModels = catalog.models, activeCapabilities = catalog.capabilities, status = AiProviderStatus.Completed, diagnosticsMessage = catalog.diagnostics, lastError = null))
            }
        }.getOrElse { throwable ->
            val error = providerRuntime.mapThrowable(throwable, current.id)
            mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
        }
    }

    override suspend fun testProviderConnection(entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        latestEntitlements = entitlements
        latestEnterpriseState = enterpriseState
        val availability = resolveAvailability(entitlements, enterpriseState)
        val current = mutableState.value.providerRuntime.currentProfile() ?: return
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Testing, lastError = null)) }
        runCatching {
            val selection = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
            val selected = selection.profile ?: error(selection.blockedReason ?: "No provider available")
            val health = providerRuntime.testConnection(selected, mutableState.value.settings, enterpriseState)
            val catalog = providerRuntime.loadCatalog(selected)
            mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(selectedProviderId = selected.id, draft = selected.toDraft(), availableModels = catalog.models, activeCapabilities = catalog.capabilities, status = AiProviderStatus.Completed, diagnosticsMessage = health.message, lastError = null)) }
        }.getOrElse { throwable ->
            val error = providerRuntime.mapThrowable(throwable, current.id)
            mutableState.update { it.copy(providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
        }
    }

    override suspend fun cancelActiveRequest() {
        activeInvocation?.cancel?.invoke()
        activeInvocation = null
        val profile = mutableState.value.providerRuntime.currentProfile()
        if (profile != null) providerRuntime.auditPromptCancelled(profile, mutableState.value.latestResult?.task ?: AssistantTaskType.AskPdf)
        mutableState.update { it.copy(isWorking = false, providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Cancelled)) }
    }

    override suspend fun pinDocument(document: DocumentModel) {
        workspaceStore.pin(document.toWorkspaceRef())
        refresh(document, null, latestEntitlements, latestEnterpriseState)
    }

    override suspend fun unpinDocument(documentKey: String) {
        workspaceStore.unpin(documentKey)
        mutableState.update { it.copy(workspace = it.workspace.copy(selectedDocumentKeys = it.workspace.selectedDocumentKeys - documentKey)) }
        refresh(null, null, latestEntitlements, latestEnterpriseState)
    }

    override suspend fun toggleWorkspaceDocument(documentKey: String, selected: Boolean) {
        mutableState.update { state ->
            val next = if (selected) state.workspace.selectedDocumentKeys + documentKey else state.workspace.selectedDocumentKeys - documentKey
            state.copy(workspace = state.workspace.copy(selectedDocumentKeys = next.take(state.workspace.policy.maxDocumentCount).toSet()))
        }
    }

    override suspend fun saveWorkspaceDocumentSet(title: String) {
        val selected = mutableState.value.workspace.selectedDocumentKeys.toList()
        if (selected.isEmpty()) return
        workspaceStore.saveRecentSet(RecentDocumentSetModel(UUID.randomUUID().toString(), title.ifBlank { "Workspace Set" }, selected, System.currentTimeMillis(), System.currentTimeMillis()))
        mutableState.update { it.copy(workspace = it.workspace.copy(recentDocumentSets = workspaceStore.recentSets())) }
    }

    override suspend fun askPdf(document: DocumentModel, question: String, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.AskPdf, question, selection, document.pages.indices.firstOrNull() ?: 0, entitlements, enterpriseState)
    override suspend fun summarizeDocument(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.SummarizeDocument, "Summarize the document", null, 0, entitlements, enterpriseState)
    override suspend fun summarizePage(document: DocumentModel, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.SummarizePage, "Summarize page ${currentPageIndex + 1}", null, currentPageIndex, entitlements, enterpriseState)
    override suspend fun extractActionItems(document: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.ExtractActionItems, "Extract action items from this PDF", null, 0, entitlements, enterpriseState)
    override suspend fun explainSelection(document: DocumentModel, selection: TextSelectionPayload?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.ExplainSelection, "Explain the selected text", selection, selection?.pageIndex ?: 0, entitlements, enterpriseState)
    override suspend fun semanticSearch(document: DocumentModel, query: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runSingleDocumentTask(document, AssistantTaskType.SemanticSearch, query, null, 0, entitlements, enterpriseState)
    override suspend fun askAcrossWorkspace(currentDocument: DocumentModel?, question: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runWorkspaceTask(currentDocument, AssistantTaskType.AskWorkspace, question, entitlements, enterpriseState)
    override suspend fun summarizeWorkspace(currentDocument: DocumentModel?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runWorkspaceTask(currentDocument, AssistantTaskType.SummarizeWorkspace, "Summarize the selected document set", entitlements, enterpriseState)
    override suspend fun compareAndSummarizeWorkspace(currentDocument: DocumentModel?, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) = runWorkspaceTask(currentDocument, AssistantTaskType.CompareAndSummarize, "Compare the selected documents and summarize the important differences", entitlements, enterpriseState)
    private suspend fun runSingleDocumentTask(document: DocumentModel, task: AssistantTaskType, prompt: String, selection: TextSelectionPayload?, currentPageIndex: Int, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        val indexed = documentSearchService.ensureIndex(document)
        val docContext = GroundedDocumentContext(
            document.documentRef.sourceKey,
            document.documentRef.displayName,
            indexed.take(8).map { page -> GroundedPageContext(page.pageIndex, page.blocks.take(6).map { block -> GroundedSnippet(block.text.trim(), block.bounds) }) },
        )
        val request = AssistantPromptRequest(task, prompt, document.documentRef.displayName, currentPageIndex, selection?.text.orEmpty(), docContext.pageContext, listOf(docContext), mutableState.value.settings.privacyMode)
        executeTask(task, prompt, request, document, entitlements, enterpriseState)
    }

    private suspend fun runWorkspaceTask(currentDocument: DocumentModel?, task: AssistantTaskType, prompt: String, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel) {
        latestEntitlements = entitlements
        latestEnterpriseState = enterpriseState
        val availability = resolveAvailability(entitlements, enterpriseState)
        if (!availability.enabled) {
            mutableState.update { it.copy(availability = availability) }
            return
        }
        val workspacePolicy = workspacePolicyFor(enterpriseState)
        val documents = loadWorkspaceDocuments(currentDocument, workspacePolicy)
        if (documents.isEmpty()) {
            mutableState.update { it.copy(availability = AssistantAvailability(false, "Select at least one document for the AI workspace.")) }
            return
        }
        if (mutableState.value.settings.privacyMode == AssistantPrivacyMode.CloudAssisted && !workspacePolicy.cloudAssistedMultiDocumentEnabled) {
            mutableState.update { it.copy(availability = AssistantAvailability(false, "Tenant policy allows only local multi-document AI.")) }
            return
        }
        val contexts = documents.map { document ->
            val indexed = documentSearchService.ensureIndex(document)
            GroundedDocumentContext(document.documentRef.sourceKey, document.documentRef.displayName, indexed.take(5).map { page -> GroundedPageContext(page.pageIndex, page.blocks.take(4).map { block -> GroundedSnippet(block.text.trim(), block.bounds) }) })
        }
        val request = AssistantPromptRequest(task, prompt, documents.first().documentRef.displayName, 0, "", contexts.firstOrNull()?.pageContext.orEmpty(), contexts, mutableState.value.settings.privacyMode)
        executeTask(task, prompt, request, documents.first(), entitlements, enterpriseState, documents)
    }

    private suspend fun executeTask(task: AssistantTaskType, prompt: String, request: AssistantPromptRequest, primaryDocument: DocumentModel, entitlements: EntitlementStateModel, enterpriseState: EnterpriseAdminStateModel, workspaceDocuments: List<DocumentModel> = listOf(primaryDocument)) {
        latestEntitlements = entitlements
        latestEnterpriseState = enterpriseState
        val availability = resolveAvailability(entitlements, enterpriseState)
        val selectionDecision = providerRuntime.selectProvider(mutableState.value.providerRuntime, mutableState.value.settings, enterpriseState)
        val selectedProvider = selectionDecision.profile
        if (!availability.enabled || selectedProvider == null) {
            val reason = availability.reason ?: selectionDecision.blockedReason ?: "No AI provider is available."
            mutableState.update { it.copy(availability = AssistantAvailability(false, reason), providerRuntime = it.providerRuntime.copy(lastError = AiProviderError(AiProviderErrorCode.PolicyBlocked, reason, false))) }
            return
        }
        mutableState.update { it.copy(prompt = prompt, isWorking = true, providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Streaming, streamPreview = "", lastError = null), availability = availability) }
        providerRuntime.auditPromptSubmitted(selectedProvider, task)
        val invocation = providerRuntime.stream(selectedProvider, buildProviderPrompt(request))
        activeInvocation = invocation
        val buffer = StringBuilder()
        runCatching {
            invocation.events.collect { event ->
                when (event) {
                    is ProviderStreamEvent.Delta -> {
                        buffer.append(event.text)
                        mutableState.update { state -> state.copy(providerRuntime = state.providerRuntime.copy(streamPreview = buffer.toString(), status = AiProviderStatus.Streaming)) }
                    }
                    is ProviderStreamEvent.Completed -> buffer.clear().append(event.fullText)
                }
            }
        }.onSuccess {
            val flattenedBlocks = workspaceDocuments.flatMap { document -> documentSearchService.ensureIndex(document).flatMap { page -> page.blocks.map { document to it } } }
            val semanticCards = if (task == AssistantTaskType.SemanticSearch || task == AssistantTaskType.CrossDocumentSearch || task == AssistantTaskType.AskWorkspace) rankSemanticCards(flattenedBlocks, prompt) else emptyList()
            val citations = buildCitations(request)
            val suggestions = if (mutableState.value.settings.allowSuggestions) buildSuggestions(primaryDocument, null, enterpriseState) else emptyList()
            val result = AssistantResult(task, headlineFor(task), buffer.toString().ifBlank { "The provider returned an empty response." }, citations, semanticCards, extractActionItems(buffer.toString()), suggestions, System.currentTimeMillis())
            val conversation = (workspaceStore.messages() + listOf(
                AssistantMessage(UUID.randomUUID().toString(), AssistantMessageRole.User, task, prompt, createdAtEpochMillis = System.currentTimeMillis()),
                AssistantMessage(UUID.randomUUID().toString(), AssistantMessageRole.Assistant, task, AssistantResultFormatter.format(result), citations = citations, createdAtEpochMillis = System.currentTimeMillis()),
            )).takeLast(40)
            workspaceStore.replaceMessages(conversation, workspacePolicyFor(enterpriseState).retentionDays)
            if (task == AssistantTaskType.SummarizeWorkspace || task == AssistantTaskType.CompareAndSummarize) {
                workspaceStore.saveSummary(WorkspaceSummaryModel(UUID.randomUUID().toString(), headlineFor(task), result.body.take(600), workspaceDocuments.map { it.documentRef.sourceKey }, System.currentTimeMillis()))
            }
            mutableState.update {
                it.copy(
                    isWorking = false,
                    latestResult = result,
                    conversation = conversation,
                    semanticCards = semanticCards,
                    suggestions = suggestions,
                    workspace = it.workspace.copy(conversationHistory = conversation, workspaceSummaries = workspaceStore.summaries(), recentDocumentSets = workspaceStore.recentSets()),
                    providerRuntime = it.providerRuntime.copy(status = AiProviderStatus.Completed, streamPreview = buffer.toString(), diagnosticsMessage = "Completed with ${selectedProvider.displayName}"),
                )
            }
            providerRuntime.auditPromptSuccess(selectedProvider, task)
            persistState()
        }.onFailure { throwable ->
            val error = providerRuntime.mapThrowable(throwable, selectedProvider.id)
            mutableState.update { it.copy(isWorking = false, providerRuntime = it.providerRuntime.copy(status = if (error.code == AiProviderErrorCode.Cancelled) AiProviderStatus.Cancelled else AiProviderStatus.Failed, lastError = error, diagnosticsMessage = error.message)) }
            providerRuntime.auditPromptFailure(selectedProvider, task, error)
        }
        activeInvocation = null
    }

    private suspend fun loadWorkspaceDocuments(currentDocument: DocumentModel?, policy: AssistantWorkspacePolicy): List<DocumentModel> {
        val candidates = when (policy.allowedDocumentScope) {
            WorkspaceDocumentScope.CurrentDocumentOnly -> listOfNotNull(currentDocument?.toWorkspaceRef())
            WorkspaceDocumentScope.PinnedDocumentsOnly -> mutableState.value.workspace.pinnedDocuments
            WorkspaceDocumentScope.RecentDocuments -> mutableState.value.workspace.availableRecentDocuments
        }
        val selectedKeys = mutableState.value.workspace.selectedDocumentKeys.ifEmpty { candidates.take(policy.maxDocumentCount).map { it.documentKey }.toSet() }
        return candidates.filter { it.documentKey in selectedKeys }.take(policy.maxDocumentCount).mapNotNull { ref -> openWorkspaceDocument(ref, currentDocument) }
    }

    private suspend fun openWorkspaceDocument(ref: WorkspaceDocumentReference, currentDocument: DocumentModel?): DocumentModel? {
        if (currentDocument?.documentRef?.sourceKey == ref.documentKey) return currentDocument
        val file = File(ref.workingCopyPath)
        if (!file.exists()) return null
        return runCatching { documentRepository.open(OpenDocumentRequest.FromFile(file.absolutePath, ref.displayName)) }.getOrNull()
    }
    private suspend fun buildSuggestions(document: DocumentModel, selection: TextSelectionPayload?, enterpriseState: EnterpriseAdminStateModel): List<AssistiveSuggestion> {
        val indexed = documentSearchService.ensureIndex(document)
        val sensitive = indexed.flatMap { page -> page.blocks.filter { block -> sensitiveTerms.any { term -> block.text.contains(term, ignoreCase = true) } }.map { block -> AssistiveSuggestion(UUID.randomUUID().toString(), AssistiveSuggestionType.Redaction, "Review for redaction", block.text.take(140), CitationAnchor(document.documentRef.sourceKey, document.documentRef.displayName, page.pageIndex, block.bounds, block.text.take(120))) } }.take(3)
        val autofill = document.formDocument.fields.mapNotNull { field ->
            val shouldSuggest = when (val value = field.value) {
                is FormFieldValue.Text -> value.text.isBlank()
                is FormFieldValue.Choice -> value.selected.isBlank()
                else -> false
            }
            if (!shouldSuggest) return@mapNotNull null
            val candidate = when {
                field.name.contains("email", true) -> enterpriseState.authSession.email
                field.name.contains("name", true) -> enterpriseState.authSession.displayName
                else -> selection?.text?.take(60).orEmpty()
            }
            candidate.takeIf { it.isNotBlank() }?.let { AssistiveSuggestion(UUID.randomUUID().toString(), AssistiveSuggestionType.FormAutofill, "Suggested value for ${field.label}", it, CitationAnchor(document.documentRef.sourceKey, document.documentRef.displayName, field.pageIndex, field.bounds, it), suggestedValue = it, fieldName = field.name) }
        }.take(3)
        return (sensitive + autofill).distinctBy { it.title + it.anchor.documentKey + it.anchor.pageIndex + it.anchor.regionLabel }
    }

    private fun rankSemanticCards(blocks: List<Pair<DocumentModel, ExtractedTextBlock>>, query: String): List<SemanticSearchCard> {
        val terms = query.lowercase().split(' ').filter { it.isNotBlank() }.toSet()
        if (terms.isEmpty()) return emptyList()
        return blocks.map { (document, block) -> Triple(document, block, terms.count { term -> block.text.lowercase().contains(term) }.toFloat() / terms.size.toFloat()) }
            .filter { it.third > 0f }
            .sortedByDescending { it.third }
            .take(8)
            .mapIndexed { index, (document, block, score) -> SemanticSearchCard("semantic-$index", "${document.documentRef.displayName} - Page ${block.pageIndex + 1}", block.text.take(180), CitationAnchor(document.documentRef.sourceKey, document.documentRef.displayName, block.pageIndex, block.bounds, block.text.take(120)), score) }
    }

    private fun buildCitations(request: AssistantPromptRequest): List<AssistantCitation> =
        request.documentContext.flatMap { doc -> doc.pageContext.flatMap { page -> page.snippets.map { snippet -> Triple(doc, page.pageIndex, snippet) } } }
            .take(6)
            .mapIndexed { index, (doc, pageIndex, snippet) -> AssistantCitation("citation-${index + 1}", "Evidence ${index + 1}", CitationAnchor(doc.documentKey, doc.documentTitle, pageIndex, snippet.bounds, snippet.text.take(180)), (0.82f - index * 0.07f).coerceAtLeast(0.4f)) }

    private fun buildProviderPrompt(request: AssistantPromptRequest): String {
        val contextLines = request.documentContext.flatMap { doc -> doc.pageContext.flatMap { page -> page.snippets.map { snippet -> "${doc.documentTitle} | Page ${page.pageIndex + 1} | ${CitationAnchor(doc.documentKey, doc.documentTitle, page.pageIndex, snippet.bounds, snippet.text).regionLabel} | ${snippet.text}" } } }
        return buildString {
            appendLine("You are assisting with enterprise PDFs. Answer only from grounded context below.")
            appendLine("Task: ${request.task.name}")
            appendLine("Prompt: ${request.prompt}")
            if (request.selectionText.isNotBlank()) appendLine("Selected text: ${request.selectionText}")
            appendLine("Grounded context:")
            contextLines.forEach { appendLine(it) }
        }
    }

    private fun extractActionItems(text: String): List<String> = text.lines().map { it.trim().removePrefix("- ").removePrefix("* ") }.filter { it.length > 8 }.take(6)
    

private fun resolveAvailability(
    entitlements: EntitlementStateModel,
    enterpriseState: EnterpriseAdminStateModel,
): AssistantAvailability {
    return resolveAssistantAvailability(entitlements, enterpriseState)
}


    private fun workspacePolicyFor(enterpriseState: EnterpriseAdminStateModel): AssistantWorkspacePolicy = AssistantWorkspacePolicy(
        localOnlyMultiDocumentMode = enterpriseState.privacySettings.localOnlyMode || !enterpriseState.adminPolicy.allowCloudAiProviders,
        cloudAssistedMultiDocumentEnabled = enterpriseState.adminPolicy.allowCloudMultiDocumentAi,
        approvedProviderIds = enterpriseState.adminPolicy.approvedAiProviderIds.toSet(),
        maxDocumentCount = enterpriseState.adminPolicy.maxAiWorkspaceDocuments,
        allowedDocumentScope = when (enterpriseState.adminPolicy.aiDocumentScope) {
            AiDocumentScopePolicy.CurrentDocumentOnly -> WorkspaceDocumentScope.CurrentDocumentOnly
            AiDocumentScopePolicy.PinnedDocumentsOnly -> WorkspaceDocumentScope.PinnedDocumentsOnly
            AiDocumentScopePolicy.RecentDocuments -> WorkspaceDocumentScope.RecentDocuments
        },
        retentionDays = enterpriseState.adminPolicy.aiHistoryRetentionDays,
    )

    private suspend fun persistState() {
        val state = mutableState.value
        settingsStore.save(AssistantPersistenceModel(state.settings, state.providerRuntime.selectedProviderId, state.providerRuntime.profiles))
    }

    private fun mergeWorkspaceRefs(document: DocumentModel?, recents: List<WorkspaceDocumentReference>): List<WorkspaceDocumentReference> {
        val current = document?.toWorkspaceRef()
        return listOfNotNull(current) + recents.filterNot { it.documentKey == current?.documentKey }
    }
    private fun headlineFor(task: AssistantTaskType): String = when (task) {
        AssistantTaskType.AskPdf -> "Ask PDF"
        AssistantTaskType.SummarizeDocument -> "Document Summary"
        AssistantTaskType.SummarizePage -> "Page Summary"
        AssistantTaskType.ExtractActionItems -> "Action Items"
        AssistantTaskType.ExplainSelection -> "Explain Selection"
        AssistantTaskType.SemanticSearch -> "Semantic Matches"
        AssistantTaskType.AskWorkspace -> "Ask Across Documents"
        AssistantTaskType.SummarizeWorkspace -> "Workspace Summary"
        AssistantTaskType.CrossDocumentSearch -> "Cross-Document Search"
        AssistantTaskType.CompareAndSummarize -> "Compare And Summarize"
        AssistantTaskType.SuggestNextActions -> "Suggested Next Actions"
    }
    private fun AiProviderRuntimeState.currentProfile(): AiProviderProfile? = profiles.firstOrNull { it.id == selectedProviderId } ?: profiles.firstOrNull()

    companion object {
        suspend fun create(context: Context, documentSearchService: DocumentSearchService, documentRepository: DocumentRepository, recentDocumentDao: RecentDocumentDao, enterpriseAdminRepository: EnterpriseAdminRepository, securityRepository: SecurityRepository): DefaultAiAssistantRepository {
            val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            val database = newAiAssistantDatabase(context)
            val store = RoomAiProviderSettingsStore(database.providerSettingsDao(), json)
            val workspaceStore = RoomAiWorkspaceStore(database.workspaceDocumentDao(), database.workspaceMessageDao(), database.workspaceSummaryDao(), database.recentDocumentSetDao(), json)
            val runtime = AiProviderRuntime(
                registry = ProviderRuntimeFactory(OkHttpClient.Builder().retryOnConnectionFailure(true).build(), json).createRegistry(),
                selectionEngine = ProviderSelectionEngine(),
                secureCredentialStore = AndroidKeystoreCredentialStore(context),
                discovery = AndroidLocalAiAppDiscovery(context),
                enterpriseAdminRepository = enterpriseAdminRepository,
                securityRepository = securityRepository,
            )
            return DefaultAiAssistantRepository(documentSearchService, documentRepository, recentDocumentDao, store, workspaceStore, runtime).also { it.initialize() }
        }
        private val sensitiveTerms = listOf("ssn", "social security", "passport", "confidential", "bank", "account")
    }
}

internal fun resolveAssistantAvailability(
    entitlements: EntitlementStateModel,
    enterpriseState: EnterpriseAdminStateModel,
): AssistantAvailability {
    val resolution = AiFeatureAccessResolver.resolve(entitlements, enterpriseState.adminPolicy)
    return resolution.toAssistantAvailability()
}

private fun AiFeatureAccessResolution.toAssistantAvailability(): AssistantAvailability {
    val reason = unavailableReason()
    return if (reason == null) {
        AssistantAvailability(true)
    } else {
        AssistantAvailability(
            enabled = false,
            reason = reason,
            missingFeatures = if (!entitled) setOf(FeatureFlag.Ai) else emptySet(),
        )
    }
}

interface AiWorkspaceStore {
    suspend fun load(): AssistantWorkspaceState
    suspend fun pinnedDocuments(): List<WorkspaceDocumentReference>
    suspend fun pin(document: WorkspaceDocumentReference)
    suspend fun unpin(documentKey: String)
    suspend fun messages(): List<AssistantMessage>
    suspend fun replaceMessages(messages: List<AssistantMessage>, retentionDays: Int)
    suspend fun saveSummary(summary: WorkspaceSummaryModel)
    suspend fun summaries(): List<WorkspaceSummaryModel>
    suspend fun saveRecentSet(set: RecentDocumentSetModel)
    suspend fun recentSets(): List<RecentDocumentSetModel>
    suspend fun pruneHistory(retentionDays: Int)
}

class RoomAiWorkspaceStore(private val documentDao: AiWorkspaceDocumentDao, private val messageDao: AiWorkspaceMessageDao, private val summaryDao: AiWorkspaceSummaryDao, private val recentSetDao: AiRecentDocumentSetDao, private val json: Json) : AiWorkspaceStore {
    override suspend fun load(): AssistantWorkspaceState = AssistantWorkspaceState(pinnedDocuments = pinnedDocuments(), conversationHistory = messages(), workspaceSummaries = summaries(), recentDocumentSets = recentSets())
    override suspend fun pinnedDocuments(): List<WorkspaceDocumentReference> = documentDao.all().map { WorkspaceDocumentReference(it.documentKey, it.displayName, it.sourceType, it.workingCopyPath, it.pinnedAtEpochMillis) }
    override suspend fun pin(document: WorkspaceDocumentReference) { documentDao.upsert(AiWorkspaceDocumentEntity(document.documentKey, document.displayName, document.sourceType, document.workingCopyPath, document.pinnedAtEpochMillis)) }
    override suspend fun unpin(documentKey: String) { documentDao.delete(documentKey) }
    override suspend fun messages(): List<AssistantMessage> = messageDao.all().map { AssistantMessage(it.id, AssistantMessageRole.valueOf(it.role), AssistantTaskType.valueOf(it.task), it.text, json.decodeFromString(ListSerializer(AssistantCitation.serializer()), it.citationsJson), it.createdAtEpochMillis) }
    override suspend fun replaceMessages(messages: List<AssistantMessage>, retentionDays: Int) { messageDao.clear(); messageDao.upsertAll(messages.map { AiWorkspaceMessageEntity(it.id, it.role.name, it.task.name, it.text, json.encodeToString(ListSerializer(AssistantCitation.serializer()), it.citations), it.createdAtEpochMillis) }); pruneHistory(retentionDays) }
    override suspend fun saveSummary(summary: WorkspaceSummaryModel) { summaryDao.upsert(AiWorkspaceSummaryEntity(summary.id, summary.title, summary.summary, json.encodeToString(ListSerializer(String.serializer()), summary.documentKeys), summary.createdAtEpochMillis)) }
    override suspend fun summaries(): List<WorkspaceSummaryModel> = summaryDao.all().map { WorkspaceSummaryModel(it.id, it.title, it.summary, json.decodeFromString(ListSerializer(String.serializer()), it.documentKeysJson), it.createdAtEpochMillis) }
    override suspend fun saveRecentSet(set: RecentDocumentSetModel) { recentSetDao.upsert(AiRecentDocumentSetEntity(set.id, set.title, json.encodeToString(ListSerializer(String.serializer()), set.documentKeys), set.createdAtEpochMillis, set.lastUsedAtEpochMillis)) }
    override suspend fun recentSets(): List<RecentDocumentSetModel> = recentSetDao.all().map { RecentDocumentSetModel(it.id, it.title, json.decodeFromString(ListSerializer(String.serializer()), it.documentKeysJson), it.createdAtEpochMillis, it.lastUsedAtEpochMillis) }
    override suspend fun pruneHistory(retentionDays: Int) { val threshold = System.currentTimeMillis() - retentionDays.coerceAtLeast(1) * 86_400_000L; messageDao.deleteOlderThan(threshold); summaryDao.deleteOlderThan(threshold) }
}

private fun RecentDocumentEntity.toWorkspaceRef(): WorkspaceDocumentReference = WorkspaceDocumentReference(sourceKey, displayName, sourceType, workingCopyPath, openedAtEpochMillis)
private fun DocumentModel.toWorkspaceRef(): WorkspaceDocumentReference = WorkspaceDocumentReference(documentRef.sourceKey, documentRef.displayName, documentRef.sourceType.name, documentRef.workingCopyPath, System.currentTimeMillis())

object AssistantResultFormatter {
    fun format(result: AssistantResult): String {
        val citations = if (result.citations.isEmpty()) "No citations" else result.citations.joinToString(separator = "\n") { citation -> "[${citation.anchor.sourceLabel}] ${citation.anchor.regionLabel}: ${citation.anchor.quote.take(90)}" }
        return buildString {
            appendLine(result.headline)
            appendLine(result.body)
            if (result.actionItems.isNotEmpty()) {
                appendLine("Action items:")
                result.actionItems.forEach { appendLine("- $it") }
            }
            appendLine("Citations:")
            append(citations)
        }.trim()
    }

    fun citationAnchor(documentKey: String, documentTitle: String, pageIndex: Int, bounds: com.aymanelbanhawy.editor.core.model.NormalizedRect, quote: String): CitationAnchor = CitationAnchor(documentKey, documentTitle, pageIndex, bounds, quote)
}


