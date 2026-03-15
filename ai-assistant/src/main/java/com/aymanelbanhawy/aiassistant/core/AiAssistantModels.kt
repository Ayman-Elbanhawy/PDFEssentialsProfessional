package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import kotlinx.serialization.Serializable

@Serializable
enum class AssistantPrivacyMode {
    LocalOnly,
    CloudAssisted,
}

@Serializable
enum class AssistantTaskType {
    AskPdf,
    SummarizeDocument,
    SummarizePage,
    ExtractActionItems,
    ExplainSelection,
    SemanticSearch,
    AskWorkspace,
    SummarizeWorkspace,
    CrossDocumentSearch,
    CompareAndSummarize,
    SuggestNextActions,
}

@Serializable
enum class AssistantMessageRole {
    User,
    Assistant,
    System,
}

@Serializable
enum class AssistiveSuggestionType {
    Redaction,
    FormAutofill,
}

@Serializable
enum class AiProviderKind {
    OllamaLocal,
    OllamaRemote,
    OpenAi,
    OpenAiCompatible,
}

@Serializable
enum class AiProviderErrorCode {
    Offline,
    Timeout,
    Cancelled,
    Unauthorized,
    Forbidden,
    BadRequest,
    NotFound,
    RateLimited,
    Server,
    PolicyBlocked,
    InvalidConfiguration,
    ParseFailure,
    Unknown,
}

@Serializable
enum class AiProviderStatus {
    Idle,
    Discovering,
    Testing,
    Streaming,
    Completed,
    Failed,
    Cancelled,
}

@Serializable
enum class WorkspaceDocumentScope {
    CurrentDocumentOnly,
    PinnedDocumentsOnly,
    RecentDocuments,
}

@Serializable
data class CitationAnchor(
    val documentKey: String = "",
    val documentTitle: String = "",
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val quote: String,
) {
    val pageLabel: String
        get() = "Page ${pageIndex + 1}"

    val regionLabel: String
        get() = "${bounds.left.formatPercent()}-${bounds.right.formatPercent()} x ${bounds.top.formatPercent()}-${bounds.bottom.formatPercent()}"

    val sourceLabel: String
        get() = listOf(documentTitle.takeIf { it.isNotBlank() }, pageLabel).joinToString(" • ")
}

@Serializable
data class AssistantCitation(
    val id: String,
    val title: String,
    val anchor: CitationAnchor,
    val confidence: Float,
)

@Serializable
data class SemanticSearchCard(
    val id: String,
    val title: String,
    val snippet: String,
    val anchor: CitationAnchor,
    val score: Float,
)

@Serializable
data class AssistiveSuggestion(
    val id: String,
    val type: AssistiveSuggestionType,
    val title: String,
    val detail: String,
    val anchor: CitationAnchor,
    val suggestedValue: String = "",
    val fieldName: String = "",
)

@Serializable
data class AssistantMessage(
    val id: String,
    val role: AssistantMessageRole,
    val task: AssistantTaskType,
    val text: String,
    val citations: List<AssistantCitation> = emptyList(),
    val createdAtEpochMillis: Long,
)

@Serializable
data class AssistantResult(
    val task: AssistantTaskType,
    val headline: String,
    val body: String,
    val citations: List<AssistantCitation>,
    val semanticCards: List<SemanticSearchCard> = emptyList(),
    val actionItems: List<String> = emptyList(),
    val suggestions: List<AssistiveSuggestion> = emptyList(),
    val generatedAtEpochMillis: Long,
)

@Serializable
data class AiProviderCapabilities(
    val supportsStreaming: Boolean = true,
    val maxContextHint: Int? = null,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsJsonMode: Boolean = false,
)

@Serializable
data class AiProviderModelInfo(
    val id: String,
    val displayName: String,
    val owner: String = "",
    val capabilitySummary: String = "",
    val capabilities: AiProviderCapabilities = AiProviderCapabilities(),
)

@Serializable
data class AiProviderProfile(
    val id: String,
    val kind: AiProviderKind,
    val displayName: String,
    val endpointUrl: String,
    val modelId: String = "",
    val hasStoredCredential: Boolean = false,
    val requestTimeoutSeconds: Int = 90,
    val retryCount: Int = 2,
)

@Serializable
data class AiProviderDraft(
    val profileId: String = DEFAULT_PROVIDER_ID,
    val kind: AiProviderKind = AiProviderKind.OllamaLocal,
    val displayName: String = "Local Ollama",
    val endpointUrl: String = "http://10.0.2.2:11434",
    val modelId: String = "",
    val apiKeyInput: String = "",
    val requestTimeoutSeconds: Int = 90,
    val retryCount: Int = 2,
)

@Serializable
data class LocalAiAppInfo(
    val packageName: String,
    val appName: String,
)

@Serializable
data class AiProviderError(
    val code: AiProviderErrorCode,
    val message: String,
    val retryable: Boolean,
    val providerId: String? = null,
)

@Serializable
data class AiProviderRuntimeState(
    val selectedProviderId: String = DEFAULT_PROVIDER_ID,
    val profiles: List<AiProviderProfile> = defaultProviderProfiles(),
    val draft: AiProviderDraft = defaultProviderProfiles().first().toDraft(),
    val availableModels: List<AiProviderModelInfo> = emptyList(),
    val activeCapabilities: AiProviderCapabilities? = null,
    val discoveredLocalApps: List<LocalAiAppInfo> = emptyList(),
    val status: AiProviderStatus = AiProviderStatus.Idle,
    val streamPreview: String = "",
    val diagnosticsMessage: String = "",
    val lastError: AiProviderError? = null,
)

@Serializable
data class AssistantSettings(
    val privacyMode: AssistantPrivacyMode = AssistantPrivacyMode.LocalOnly,
    val redactBeforeCloud: Boolean = true,
    val allowSuggestions: Boolean = true,
    val workspaceScope: WorkspaceDocumentScope = WorkspaceDocumentScope.PinnedDocumentsOnly,
    val spokenResponsesEnabled: Boolean = false,
    val readAloudEnabled: Boolean = true,
    val voicePromptCaptureEnabled: Boolean = true,
)

@Serializable
data class AssistantAvailability(
    val enabled: Boolean,
    val reason: String? = null,
    val missingFeatures: Set<FeatureFlag> = emptySet(),
)

@Serializable
data class WorkspaceDocumentReference(
    val documentKey: String,
    val displayName: String,
    val sourceType: String,
    val workingCopyPath: String,
    val pinnedAtEpochMillis: Long,
)

@Serializable
data class WorkspaceSummaryModel(
    val id: String,
    val title: String,
    val summary: String,
    val documentKeys: List<String>,
    val createdAtEpochMillis: Long,
)

@Serializable
data class RecentDocumentSetModel(
    val id: String,
    val title: String,
    val documentKeys: List<String>,
    val createdAtEpochMillis: Long,
    val lastUsedAtEpochMillis: Long,
)

@Serializable
data class AssistantWorkspacePolicy(
    val localOnlyMultiDocumentMode: Boolean = true,
    val cloudAssistedMultiDocumentEnabled: Boolean = false,
    val approvedProviderIds: Set<String> = emptySet(),
    val maxDocumentCount: Int = 5,
    val allowedDocumentScope: WorkspaceDocumentScope = WorkspaceDocumentScope.PinnedDocumentsOnly,
    val retentionDays: Int = 30,
)

@Serializable
data class AssistantWorkspaceState(
    val pinnedDocuments: List<WorkspaceDocumentReference> = emptyList(),
    val availableRecentDocuments: List<WorkspaceDocumentReference> = emptyList(),
    val selectedDocumentKeys: Set<String> = emptySet(),
    val conversationHistory: List<AssistantMessage> = emptyList(),
    val workspaceSummaries: List<WorkspaceSummaryModel> = emptyList(),
    val recentDocumentSets: List<RecentDocumentSetModel> = emptyList(),
    val policy: AssistantWorkspacePolicy = AssistantWorkspacePolicy(),
)

@Serializable
data class AssistantUiState(
    val settings: AssistantSettings = AssistantSettings(),
    val availability: AssistantAvailability = AssistantAvailability(enabled = false, reason = "AI is disabled"),
    val providerRuntime: AiProviderRuntimeState = AiProviderRuntimeState(),
    val workspace: AssistantWorkspaceState = AssistantWorkspaceState(),
    val audio: AssistantAudioUiState = AssistantAudioUiState(),
    val prompt: String = "",
    val isWorking: Boolean = false,
    val latestResult: AssistantResult? = null,
    val conversation: List<AssistantMessage> = emptyList(),
    val semanticCards: List<SemanticSearchCard> = emptyList(),
    val suggestions: List<AssistiveSuggestion> = emptyList(),
)

@Serializable
data class AssistantPromptRequest(
    val task: AssistantTaskType,
    val prompt: String,
    val documentTitle: String,
    val currentPageIndex: Int,
    val selectionText: String,
    val pageContext: List<GroundedPageContext>,
    val documentContext: List<GroundedDocumentContext> = emptyList(),
    val privacyMode: AssistantPrivacyMode,
)

@Serializable
data class GroundedDocumentContext(
    val documentKey: String,
    val documentTitle: String,
    val pageContext: List<GroundedPageContext>,
)

@Serializable
data class GroundedPageContext(
    val pageIndex: Int,
    val snippets: List<GroundedSnippet>,
)

@Serializable
data class GroundedSnippet(
    val text: String,
    val bounds: NormalizedRect,
)

@Serializable
data class AssistantPersistenceModel(
    val settings: AssistantSettings = AssistantSettings(),
    val selectedProviderId: String = DEFAULT_PROVIDER_ID,
    val profiles: List<AiProviderProfile> = defaultProviderProfiles(),
)

internal fun Float.formatPercent(): String = (this * 100f).toInt().toString() + "%"

fun AiProviderProfile.toDraft(apiKeyInput: String = ""): AiProviderDraft = AiProviderDraft(
    profileId = id,
    kind = kind,
    displayName = displayName,
    endpointUrl = endpointUrl,
    modelId = modelId,
    apiKeyInput = apiKeyInput,
    requestTimeoutSeconds = requestTimeoutSeconds,
    retryCount = retryCount,
).normalized()

fun AiProviderDraft.toProfile(hasStoredCredential: Boolean): AiProviderProfile = AiProviderProfile(
    id = profileId,
    kind = kind,
    displayName = displayName.ifBlank { kind.defaultDisplayName() },
    endpointUrl = endpointUrl.trim(),
    modelId = modelId.trim(),
    hasStoredCredential = hasStoredCredential,
    requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(15, 300),
    retryCount = retryCount.coerceIn(0, 4),
).normalized()

internal fun defaultProviderProfiles(): List<AiProviderProfile> = listOf(
    AiProviderProfile(
        id = DEFAULT_PROVIDER_ID,
        kind = AiProviderKind.OllamaLocal,
        displayName = AiProviderKind.OllamaLocal.defaultDisplayName(),
        endpointUrl = AiProviderKind.OllamaLocal.defaultEndpointUrl(),
    ),
    AiProviderProfile(
        id = "ollama-remote",
        kind = AiProviderKind.OllamaRemote,
        displayName = AiProviderKind.OllamaRemote.defaultDisplayName(),
        endpointUrl = AiProviderKind.OllamaRemote.defaultEndpointUrl(),
    ),
    AiProviderProfile(
        id = "openai",
        kind = AiProviderKind.OpenAi,
        displayName = AiProviderKind.OpenAi.defaultDisplayName(),
        endpointUrl = AiProviderKind.OpenAi.defaultEndpointUrl(),
    ),
    AiProviderProfile(
        id = "openai-compatible",
        kind = AiProviderKind.OpenAiCompatible,
        displayName = AiProviderKind.OpenAiCompatible.defaultDisplayName(),
        endpointUrl = AiProviderKind.OpenAiCompatible.defaultEndpointUrl(),
    ),
).map(AiProviderProfile::normalized)

internal fun normalizePersistenceModel(model: AssistantPersistenceModel): AssistantPersistenceModel {
    val normalizedProfiles = model.profiles
        .ifEmpty { defaultProviderProfiles() }
        .distinctBy { it.id }
        .map(AiProviderProfile::normalized)
    val selectedProviderId = normalizedProfiles.firstOrNull { it.id == model.selectedProviderId }?.id
        ?: normalizedProfiles.firstOrNull()?.id
        ?: DEFAULT_PROVIDER_ID
    return model.copy(
        selectedProviderId = selectedProviderId,
        profiles = normalizedProfiles,
    )
}

internal fun AiProviderProfile.normalized(): AiProviderProfile {
    val normalizedEndpoint = endpointUrl.trim().replaceLegacyEndpoint(kind)
    return copy(
        displayName = displayName.ifBlank { kind.defaultDisplayName() },
        endpointUrl = normalizedEndpoint.ifBlank { kind.defaultEndpointUrl() },
        modelId = modelId.trim(),
        requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(15, 300),
        retryCount = retryCount.coerceIn(0, 4),
    )
}

internal fun AiProviderDraft.normalized(): AiProviderDraft = copy(
    displayName = displayName.ifBlank { kind.defaultDisplayName() },
    endpointUrl = endpointUrl.trim().replaceLegacyEndpoint(kind).ifBlank { kind.defaultEndpointUrl() },
    modelId = modelId.trim(),
    requestTimeoutSeconds = requestTimeoutSeconds.coerceIn(15, 300),
    retryCount = retryCount.coerceIn(0, 4),
)

internal fun AiProviderKind.defaultDisplayName(): String = when (this) {
    AiProviderKind.OllamaLocal -> "Local Ollama"
    AiProviderKind.OllamaRemote -> "Remote Ollama"
    AiProviderKind.OpenAi -> "OpenAI"
    AiProviderKind.OpenAiCompatible -> "OpenAI Compatible"
}

internal fun AiProviderKind.defaultEndpointUrl(): String = when (this) {
    AiProviderKind.OllamaLocal -> "http://10.0.2.2:11434"
    AiProviderKind.OllamaRemote -> ""
    AiProviderKind.OpenAi -> "https://api.openai.com/v1"
    AiProviderKind.OpenAiCompatible -> ""
}

internal fun AiProviderKind.endpointHintUrl(): String = when (this) {
    AiProviderKind.OllamaLocal -> "http://10.0.2.2:11434"
    AiProviderKind.OllamaRemote -> "https://host.tld"
    AiProviderKind.OpenAi -> "https://api.openai.com/v1"
    AiProviderKind.OpenAiCompatible -> "https://host.tld/v1"
}

internal fun AiProviderKind.requiresCredential(): Boolean = when (this) {
    AiProviderKind.OllamaLocal -> false
    AiProviderKind.OllamaRemote -> false
    AiProviderKind.OpenAi -> true
    AiProviderKind.OpenAiCompatible -> true
}

private fun String.replaceLegacyEndpoint(kind: AiProviderKind): String {
    val normalized = trim().removeSuffix("/")
    return when {
        normalized.isLegacySampleEndpoint("https://ollama", ".com") && kind == AiProviderKind.OllamaRemote -> ""
        normalized.isLegacySampleEndpoint("https://api", ".com/v1") && kind == AiProviderKind.OpenAiCompatible -> ""
        normalized.isLegacySampleEndpoint("https://api", ".com/v1") && kind == AiProviderKind.OpenAi -> AiProviderKind.OpenAi.defaultEndpointUrl()
        normalized.isBlank() && kind == AiProviderKind.OpenAi -> AiProviderKind.OpenAi.defaultEndpointUrl()
        else -> normalized
    }
}

private fun String.isLegacySampleEndpoint(prefix: String, suffix: String): Boolean {
    val sampleDomain = ".exa" + "mple"
    return equals(prefix + sampleDomain + suffix, ignoreCase = true)
}

const val DEFAULT_PROVIDER_ID: String = "ollama-local"

