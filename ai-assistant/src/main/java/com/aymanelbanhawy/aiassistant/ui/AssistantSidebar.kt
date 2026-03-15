package com.aymanelbanhawy.aiassistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Subject
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.aiassistant.core.AiProviderDraft
import com.aymanelbanhawy.aiassistant.core.AiProviderKind
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistiveSuggestionType
import com.aymanelbanhawy.aiassistant.core.SemanticSearchCard
import com.aymanelbanhawy.aiassistant.core.WorkspaceDocumentReference
import com.aymanelbanhawy.aiassistant.core.endpointHintUrl
import com.aymanelbanhawy.aiassistant.core.requiresCredential
import com.aymanelbanhawy.aiassistant.core.toDraft

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantSidebar(
    modifier: Modifier = Modifier,
    state: AssistantUiState,
    onPromptChanged: (String) -> Unit,
    onAskPdf: () -> Unit,
    onSummarizeDocument: () -> Unit,
    onSummarizePage: () -> Unit,
    onExtractActionItems: () -> Unit,
    onExplainSelection: () -> Unit,
    onSemanticSearch: () -> Unit,
    onAskWorkspace: () -> Unit,
    onSummarizeWorkspace: () -> Unit,
    onCompareWorkspace: () -> Unit,
    onPinCurrentDocument: () -> Unit,
    onToggleWorkspaceDocument: (String, Boolean) -> Unit,
    onUnpinDocument: (String) -> Unit,
    onSaveWorkspaceSet: (String) -> Unit,
    onPrivacyModeChanged: (AssistantPrivacyMode) -> Unit,
    onProviderDraftChanged: (AiProviderDraft) -> Unit,
    onSaveProvider: () -> Unit,
    onRefreshProviders: () -> Unit,
    onTestConnection: () -> Unit,
    onCancelRequest: () -> Unit,
    onOpenCitation: (Int) -> Unit,
    onStartVoicePromptCapture: () -> Unit,
    onStopVoicePromptCapture: () -> Unit,
    onCancelVoicePromptCapture: () -> Unit,
    onReadCurrentPageAloud: () -> Unit,
    onReadSelectionAloud: () -> Unit,
    onPauseReadAloud: () -> Unit,
    onResumeReadAloud: () -> Unit,
    onStopReadAloud: () -> Unit,
    onAssistantAudioEnabledChanged: (Boolean) -> Unit,
) {
    val providerState = state.providerRuntime
    val draft = providerState.draft
    var workspaceSetTitle by remember { mutableStateOf("") }
    Surface(modifier = modifier.semantics { paneTitle = "AI assistant panel" }.testTag("assistant-sidebar"), tonalElevation = 4.dp, shadowElevation = 8.dp, shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Assistant", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(state.availability.reason ?: providerState.diagnosticsMessage.ifBlank { "Grounded answers use page and region citations." }, style = MaterialTheme.typography.bodyMedium, color = if (state.availability.enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            }
            item { OutlinedTextField(value = state.prompt, onValueChange = onPromptChanged, modifier = Modifier.fillMaxWidth(), label = { Text("Ask about this PDF or document set") }, minLines = 3) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantPrivacyMode.entries.forEach { mode -> FilterChip(selected = state.settings.privacyMode == mode, onClick = { onPrivacyModeChanged(mode) }, label = { Text(mode.name) }) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantActionButton(icon = Icons.Outlined.PushPin, tooltip = "Pin Current Document", onClick = onPinCurrentDocument)
                    AssistantActionButton(icon = Icons.Outlined.Save, tooltip = "Save Document Set", enabled = state.workspace.selectedDocumentKeys.isNotEmpty(), onClick = { onSaveWorkspaceSet(workspaceSetTitle) })
                }
            }
            item { OutlinedTextField(value = workspaceSetTitle, onValueChange = { workspaceSetTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Document set name") }) }
            item { Text("Workspace Documents", style = MaterialTheme.typography.titleMedium) }
            item {
                WorkspaceDocumentSection(
                    title = "Pinned",
                    documents = state.workspace.pinnedDocuments,
                    selectedKeys = state.workspace.selectedDocumentKeys,
                    onToggle = onToggleWorkspaceDocument,
                    onRemove = onUnpinDocument,
                )
            }
            item {
                WorkspaceDocumentSection(
                    title = "Recent",
                    documents = state.workspace.availableRecentDocuments,
                    selectedKeys = state.workspace.selectedDocumentKeys,
                    onToggle = onToggleWorkspaceDocument,
                    onRemove = {},
                    removable = false,
                )
            }
            if (state.workspace.recentDocumentSets.isNotEmpty()) {
                item { Text("Recent Sets", style = MaterialTheme.typography.titleSmall) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.workspace.recentDocumentSets.take(6).forEach { set ->
                            FilterChip(selected = set.documentKeys.toSet() == state.workspace.selectedDocumentKeys, onClick = { set.documentKeys.forEach { key -> onToggleWorkspaceDocument(key, true) } }, label = { Text(set.title) })
                        }
                    }
                }
            }
            if (state.workspace.workspaceSummaries.isNotEmpty()) {
                item { Text("Workspace Summaries", style = MaterialTheme.typography.titleSmall) }
                items(state.workspace.workspaceSummaries.take(4), key = { it.id }) { summary ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(summary.title, style = MaterialTheme.typography.labelLarge)
                            Text(summary.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item { Text("Provider", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProviderKind.entries.forEach { kind ->
                        FilterChip(selected = draft.kind == kind, onClick = {
                            val profile = providerState.profiles.firstOrNull { it.kind == kind }
                            val nextDraft = profile?.toDraft() ?: draft.copy(profileId = defaultProfileId(kind), kind = kind, displayName = defaultProviderName(kind), endpointUrl = kind.endpointHintUrl(), modelId = "")
                            onProviderDraftChanged(nextDraft.copy(apiKeyInput = draft.apiKeyInput))
                        }, label = { Text(kind.name) })
                    }
                }
            }
            item { OutlinedTextField(value = draft.displayName, onValueChange = { onProviderDraftChanged(draft.copy(displayName = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Provider name") }) }
            item { OutlinedTextField(value = draft.endpointUrl, onValueChange = { onProviderDraftChanged(draft.copy(endpointUrl = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Endpoint URL") }, placeholder = { Text(draft.kind.endpointHintUrl()) }, supportingText = { Text(endpointHelp(draft.kind)) }, leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) }) }
            item { OutlinedTextField(value = draft.apiKeyInput, onValueChange = { onProviderDraftChanged(draft.copy(apiKeyInput = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("API key / token") }, supportingText = { Text(if (draft.kind.requiresCredential()) "Stored securely with Android Keystore-backed encryption." else "Optional for this provider.") }, visualTransformation = PasswordVisualTransformation()) }
            item { OutlinedTextField(value = draft.modelId, onValueChange = { onProviderDraftChanged(draft.copy(modelId = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, leadingIcon = { Icon(Icons.Outlined.ModelTraining, contentDescription = null) }) }
            item { OutlinedTextField(value = draft.requestTimeoutSeconds.toString(), onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { onProviderDraftChanged(draft.copy(requestTimeoutSeconds = it)) } }, modifier = Modifier.fillMaxWidth(), label = { Text("Timeout (s)") }) }
            item { OutlinedTextField(value = draft.retryCount.toString(), onValueChange = { value -> value.filter(Char::isDigit).toIntOrNull()?.let { onProviderDraftChanged(draft.copy(retryCount = it)) } }, modifier = Modifier.fillMaxWidth(), label = { Text("Retries") }) }
            if (providerState.availableModels.isNotEmpty()) {
                item { Text("Available models", style = MaterialTheme.typography.titleSmall) }
                item { FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { providerState.availableModels.take(8).forEach { model -> FilterChip(selected = draft.modelId == model.id, onClick = { onProviderDraftChanged(draft.copy(modelId = model.id)) }, label = { Text(model.displayName) }) } } }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { AssistantActionButton(icon = Icons.Outlined.CheckCircleOutline, tooltip = "Save Provider", onClick = onSaveProvider); AssistantActionButton(icon = Icons.Outlined.NetworkCheck, tooltip = "Test Connection", onClick = onTestConnection); AssistantActionButton(icon = Icons.Outlined.ModelTraining, tooltip = "Refresh Models", onClick = onRefreshProviders); if (state.isWorking) AssistantActionButton(icon = Icons.Outlined.StopCircle, tooltip = "Cancel Request", onClick = onCancelRequest) } }
            item { Text("Voice and Read Aloud", style = MaterialTheme.typography.titleMedium) }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.settings.spokenResponsesEnabled,
                        onClick = { onAssistantAudioEnabledChanged(!state.settings.spokenResponsesEnabled) },
                        label = { Text(if (state.settings.spokenResponsesEnabled) "Audio On" else "Audio Off") },
                    )
                    AssistantActionButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Capture Voice Prompt", enabled = state.audio.enabled && !state.isWorking, onClick = onStartVoicePromptCapture)
                    AssistantActionButton(icon = Icons.Outlined.StopCircle, tooltip = "Stop Voice Capture", enabled = state.audio.voiceCapture.status == com.aymanelbanhawy.aiassistant.core.VoiceCaptureStatus.Listening || state.audio.voiceCapture.status == com.aymanelbanhawy.aiassistant.core.VoiceCaptureStatus.Recognizing, onClick = onStopVoicePromptCapture)
                    AssistantActionButton(icon = Icons.Outlined.StopCircle, tooltip = "Cancel Voice Capture", enabled = state.audio.voiceCapture.status != com.aymanelbanhawy.aiassistant.core.VoiceCaptureStatus.Idle, onClick = onCancelVoicePromptCapture)
                    AssistantActionButton(icon = Icons.Outlined.Description, tooltip = "Read Current Page", enabled = state.audio.enabled, onClick = onReadCurrentPageAloud)
                    AssistantActionButton(icon = Icons.AutoMirrored.Outlined.Subject, tooltip = "Read Selection", enabled = state.audio.enabled, onClick = onReadSelectionAloud)
                    AssistantActionButton(icon = Icons.Outlined.PauseCircleOutline, tooltip = "Pause Read Aloud", enabled = state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Speaking || state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Preparing, onClick = onPauseReadAloud)
                    AssistantActionButton(icon = Icons.Outlined.PlayCircleOutline, tooltip = "Resume Read Aloud", enabled = state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Paused, onClick = onResumeReadAloud)
                    AssistantActionButton(icon = Icons.Outlined.StopCircle, tooltip = "Stop Read Aloud", enabled = state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Preparing || state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Speaking || state.audio.readAloud.status == com.aymanelbanhawy.aiassistant.core.ReadAloudStatus.Paused, onClick = onStopReadAloud)
                }
            }
            if (state.audio.reason != null || state.audio.voiceCapture.diagnosticsMessage.isNotBlank() || state.audio.readAloud.diagnosticsMessage.isNotBlank()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.audio.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                        if (state.audio.voiceCapture.diagnosticsMessage.isNotBlank()) {
                            Text(state.audio.voiceCapture.diagnosticsMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.audio.voiceCapture.partialTranscript.isNotBlank()) {
                            Text(state.audio.voiceCapture.partialTranscript, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.audio.readAloud.diagnosticsMessage.isNotBlank()) {
                            Text(state.audio.readAloud.diagnosticsMessage, style = MaterialTheme.typography.bodySmall)
                        }
                        if (state.audio.readAloud.progress.currentSegment.isNotBlank()) {
                            Text(state.audio.readAloud.progress.currentSegment, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            providerState.activeCapabilities?.let { capabilities -> item { Text("Capabilities: streaming=${capabilities.supportsStreaming}, json=${capabilities.supportsJsonMode}, maxContext=${capabilities.maxContextHint ?: "unknown"}", style = MaterialTheme.typography.bodySmall) } }
            providerState.lastError?.let { error -> item { Text(error.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
            if (providerState.discoveredLocalApps.isNotEmpty()) {
                item { Text("Detected local AI apps", style = MaterialTheme.typography.titleSmall) }
                items(providerState.discoveredLocalApps, key = { it.packageName }) { app ->
                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(app.appName, style = MaterialTheme.typography.labelLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantActionButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Ask PDF", enabled = state.availability.enabled && !state.isWorking, onClick = onAskPdf)
                    AssistantActionButton(icon = Icons.AutoMirrored.Outlined.Subject, tooltip = "Summarize Document", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizeDocument)
                    AssistantActionButton(icon = Icons.Outlined.Description, tooltip = "Summarize Page", enabled = state.availability.enabled && !state.isWorking, onClick = onSummarizePage)
                    AssistantActionButton(icon = Icons.Outlined.Checklist, tooltip = "Extract Action Items", enabled = state.availability.enabled && !state.isWorking, onClick = onExtractActionItems)
                    AssistantActionButton(icon = Icons.AutoMirrored.Outlined.HelpOutline, tooltip = "Explain Selection", enabled = state.availability.enabled && !state.isWorking, onClick = onExplainSelection)
                    AssistantActionButton(icon = Icons.Outlined.Search, tooltip = "Semantic Search", enabled = state.availability.enabled && !state.isWorking, onClick = onSemanticSearch)
                    AssistantActionButton(icon = Icons.Outlined.Bookmarks, tooltip = "Ask Workspace", enabled = state.availability.enabled && !state.isWorking && state.workspace.selectedDocumentKeys.isNotEmpty(), onClick = onAskWorkspace)
                    AssistantActionButton(icon = Icons.Outlined.Description, tooltip = "Summarize Workspace", enabled = state.availability.enabled && !state.isWorking && state.workspace.selectedDocumentKeys.isNotEmpty(), onClick = onSummarizeWorkspace)
                    AssistantActionButton(icon = Icons.Outlined.CompareArrows, tooltip = "Compare Workspace", enabled = state.availability.enabled && !state.isWorking && state.workspace.selectedDocumentKeys.size > 1, onClick = onCompareWorkspace)
                }
            }
            if (providerState.streamPreview.isNotBlank() && state.isWorking) item { Text(providerState.streamPreview, style = MaterialTheme.typography.bodyMedium) }
            state.latestResult?.let { result ->
                item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(result.headline, style = MaterialTheme.typography.titleMedium); Text(result.body, style = MaterialTheme.typography.bodyMedium) } }
                if (result.actionItems.isNotEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text("Action Items", style = MaterialTheme.typography.titleSmall); result.actionItems.forEach { action -> Text("- $action", style = MaterialTheme.typography.bodyMedium) } } }
                if (result.citations.isNotEmpty()) {
                    item { Text("Citations", style = MaterialTheme.typography.titleSmall) }
                    items(result.citations, key = { it.id }) { citation ->
                        Surface(onClick = { onOpenCitation(citation.anchor.pageIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(citation.title, style = MaterialTheme.typography.labelLarge)
                                Text(citation.anchor.sourceLabel, style = MaterialTheme.typography.labelMedium)
                                Text(citation.anchor.regionLabel, style = MaterialTheme.typography.labelSmall)
                                Text(citation.anchor.quote, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            if (state.semanticCards.isNotEmpty()) {
                item { Text("Semantic Results", style = MaterialTheme.typography.titleSmall) }
                items(state.semanticCards, key = { it.id }) { card -> SemanticCard(card = card, onOpen = { onOpenCitation(card.anchor.pageIndex) }) }
            }
            if (state.suggestions.isNotEmpty()) {
                item { Text("Assistive Suggestions", style = MaterialTheme.typography.titleSmall) }
                items(state.suggestions, key = { it.id }) { suggestion ->
                    Surface(onClick = { onOpenCitation(suggestion.anchor.pageIndex) }, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(suggestion.title, style = MaterialTheme.typography.labelLarge)
                            Text(if (suggestion.type == AssistiveSuggestionType.FormAutofill) "Form autofill" else "Redaction review", style = MaterialTheme.typography.labelMedium)
                            Text(suggestion.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceDocumentSection(
    title: String,
    documents: List<WorkspaceDocumentReference>,
    selectedKeys: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    removable: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (documents.isEmpty()) {
            Text("No documents", style = MaterialTheme.typography.bodySmall)
        } else {
            documents.forEach { document ->
                Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(document.displayName, style = MaterialTheme.typography.labelLarge)
                            Text(document.workingCopyPath, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = document.documentKey in selectedKeys, onClick = { onToggle(document.documentKey, document.documentKey !in selectedKeys) }, label = { Text(if (document.documentKey in selectedKeys) "Selected" else "Select") })
                            if (removable) {
                                IconButton(onClick = { onRemove(document.documentKey) }) { Icon(Icons.Outlined.PushPin, contentDescription = "Remove pinned document") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticCard(card: SemanticSearchCard, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(card.title, style = MaterialTheme.typography.labelLarge)
                Text(card.anchor.sourceLabel, style = MaterialTheme.typography.labelMedium)
                Text(card.snippet, style = MaterialTheme.typography.bodySmall)
            }
            Text(String.format("%.2f", card.score), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AssistantActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tooltip: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = tooltip)
        }
    }
}

private fun endpointHelp(kind: AiProviderKind): String = when (kind) {
    AiProviderKind.OllamaLocal -> "Point this to the local Ollama HTTP endpoint reachable from the device or emulator."
    AiProviderKind.OllamaRemote -> "Use your managed Ollama endpoint over HTTPS."
    AiProviderKind.OpenAi -> "OpenAI uses the official v1 API base URL."
    AiProviderKind.OpenAiCompatible -> "Use any OpenAI-compatible base URL that exposes /models and /chat/completions."
}

private fun defaultProfileId(kind: AiProviderKind): String = when (kind) {
    AiProviderKind.OllamaLocal -> "ollama-local"
    AiProviderKind.OllamaRemote -> "ollama-remote"
    AiProviderKind.OpenAi -> "openai"
    AiProviderKind.OpenAiCompatible -> "openai-compatible"
}

private fun defaultProviderName(kind: AiProviderKind): String = when (kind) {
    AiProviderKind.OllamaLocal -> "Local Ollama"
    AiProviderKind.OllamaRemote -> "Remote Ollama"
    AiProviderKind.OpenAi -> "OpenAI"
    AiProviderKind.OpenAiCompatible -> "OpenAI Compatible"
}





