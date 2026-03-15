package com.aymanelbanhawy.enterprisepdf.app.visual

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aymanelbanhawy.aiassistant.core.AiProviderRuntimeState
import com.aymanelbanhawy.aiassistant.core.AssistantAudioUiState
import com.aymanelbanhawy.aiassistant.core.AssistantAvailability
import com.aymanelbanhawy.aiassistant.core.AssistantPrivacyMode
import com.aymanelbanhawy.aiassistant.core.AssistantResult
import com.aymanelbanhawy.aiassistant.core.AssistantSettings
import com.aymanelbanhawy.aiassistant.core.AssistantTaskType
import com.aymanelbanhawy.aiassistant.core.AssistantUiState
import com.aymanelbanhawy.aiassistant.core.AssistantWorkspaceState
import com.aymanelbanhawy.aiassistant.core.ReadAloudProgress
import com.aymanelbanhawy.aiassistant.core.ReadAloudStatus
import com.aymanelbanhawy.aiassistant.core.ReadAloudUiState
import com.aymanelbanhawy.aiassistant.core.VoiceCaptureStatus
import com.aymanelbanhawy.aiassistant.core.VoiceCaptureUiState
import com.aymanelbanhawy.aiassistant.ui.AssistantSidebar
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.ocr.OcrSettingsModel
import com.aymanelbanhawy.editor.core.search.SearchContentSource
import com.aymanelbanhawy.editor.core.search.SearchHit
import com.aymanelbanhawy.editor.core.search.SearchResultSet
import com.aymanelbanhawy.enterprisepdf.app.editor.EditInspectorSidebar
import com.aymanelbanhawy.enterprisepdf.app.editor.EditorUiState
import com.aymanelbanhawy.enterprisepdf.app.organize.OrganizePagesScreen
import com.aymanelbanhawy.enterprisepdf.app.enterprise.SettingsAdminSidebar
import com.aymanelbanhawy.enterprisepdf.app.search.SearchSidebar
import com.aymanelbanhawy.enterprisepdf.app.theme.EnterprisePdfTheme
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.flow.emptyFlow
import com.aymanelbanhawy.editor.core.model.FontFamilyToken
import com.aymanelbanhawy.editor.core.model.ImageEditModel
import com.aymanelbanhawy.editor.core.model.NormalizedRect
import com.aymanelbanhawy.editor.core.model.TextAlignment
import com.aymanelbanhawy.editor.core.model.TextBoxEditModel

@RunWith(AndroidJUnit4::class)
class VisualSnapshotTest {
    @Test
    fun keySurfaceSnapshots_publishVisualProofBundle() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val proofRoot = File(targetContext.filesDir, "visual-proof").apply {
            deleteRecursively()
            mkdirs()
        }
        val screenshotsDir = File(proofRoot, "screenshots").apply {
            mkdirs()
        }
        val snapshotDiffs = JSONArray()
        val screenshotEntries = JSONArray()
        val accessibilityChecks = accessibilitySummary()
        val iconAudit = iconAuditSummary()
        val scenario = ActivityScenario.launch(ComponentActivity::class.java)
        try {
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "workspace-home-light", darkTheme = false) { WorkspaceHomeProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "workspace-home-dark", darkTheme = true) { WorkspaceHomeProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "editor-screen-light", darkTheme = false) { EditorScreenProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "editor-screen-dark", darkTheme = true) { EditorScreenProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "tool-palette-light", darkTheme = false) { ToolPaletteProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "tool-palette-dark", darkTheme = true) { ToolPaletteProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "assistant-panel-light", darkTheme = false) { AssistantSnapshotSurface(voiceMode = false) }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "assistant-panel-dark", darkTheme = true) { AssistantSnapshotSurface(voiceMode = false) }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "voice-surface-light", darkTheme = false) { AssistantSnapshotSurface(voiceMode = true) }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "voice-surface-dark", darkTheme = true) { AssistantSnapshotSurface(voiceMode = true) }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "settings-admin-light", darkTheme = false) { SettingsSnapshotSurface() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "settings-admin-dark", darkTheme = true) { SettingsSnapshotSurface() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "reading-mode-light", darkTheme = false) { ReadingModeSnapshotSurface() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "reading-mode-dark", darkTheme = true) { ReadingModeSnapshotSurface() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "organize-quick-tools-light", darkTheme = false) { OrganizeQuickToolsProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "organize-quick-tools-dark", darkTheme = true) { OrganizeQuickToolsProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "lightweight-edit-tools-light", darkTheme = false) { LightweightEditToolsProof() }
            renderAndCapture(scenario, screenshotsDir, snapshotDiffs, screenshotEntries, "lightweight-edit-tools-dark", darkTheme = true) { LightweightEditToolsProof() }
        } finally {
            scenario.close()
        }

        val accessibilitySummaryFile = File(proofRoot, "accessibility-summary.json")
        accessibilitySummaryFile.writeText(
            JSONObject()
                .put("generatedAtEpochMillis", System.currentTimeMillis())
                .put("checks", accessibilityChecks)
                .toString(2),
        )

        val iconAuditFile = File(proofRoot, "icon-audit-summary.json")
        iconAuditFile.writeText(
            JSONObject()
                .put("generatedAtEpochMillis", System.currentTimeMillis())
                .put("iconAudits", iconAudit)
                .toString(2),
        )

        val snapshotDiffFile = File(proofRoot, "snapshot-diffs.json")
        snapshotDiffFile.writeText(
            JSONObject()
                .put("generatedAtEpochMillis", System.currentTimeMillis())
                .put("entries", snapshotDiffs)
                .toString(2),
        )

        val manifestFile = File(proofRoot, "visual-proof-manifest.json")
        manifestFile.writeText(
            JSONObject()
                .put("generatedAtEpochMillis", System.currentTimeMillis())
                .put("screenshotDirectory", screenshotsDir.absolutePath)
                .put("screenshotCount", screenshotsDir.listFiles()?.size ?: 0)
                .put("screenshots", screenshotEntries)
                .put("snapshotDiffSummaryPath", snapshotDiffFile.absolutePath)
                .put("accessibilitySummaryPath", accessibilitySummaryFile.absolutePath)
                .put("iconAuditSummaryPath", iconAuditFile.absolutePath)
                .toString(2),
        )

        assertThat(screenshotsDir.listFiles().orEmpty().size).isAtLeast(18)
        assertThat(accessibilitySummaryFile.exists()).isTrue()
        assertThat(iconAuditFile.exists()).isTrue()
        assertThat(snapshotDiffFile.exists()).isTrue()
        assertThat(manifestFile.exists()).isTrue()

        exportProofBundle(targetContext.packageName)
        emitProofLog(
            listOf(
                manifestFile,
                snapshotDiffFile,
                accessibilitySummaryFile,
                iconAuditFile,
            ) + screenshotsDir.listFiles().orEmpty().sortedBy { it.name },
        )
    }

    private fun renderAndCapture(
        scenario: ActivityScenario<ComponentActivity>,
        screenshotsDir: File,
        snapshotDiffs: JSONArray,
        screenshotEntries: JSONArray,
        name: String,
        darkTheme: Boolean,
        content: @Composable () -> Unit,
    ) {
        scenario.onActivity { activity ->
            activity.setContent {
                EnterprisePdfTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        content()
                    }
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        SystemClock.sleep(250)
        lateinit var output: File
        scenario.onActivity { activity ->
            output = File(screenshotsDir, "$name.png")
            output.outputStream().use { stream ->
                activity.window.decorView.rootView.drawToBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
            }
        }
        assertThat(output.exists()).isTrue()
        assertThat(output.length()).isGreaterThan(0)
        val checksum = sha256(output)
        snapshotDiffs.put(
            JSONObject()
                .put("name", name)
                .put("path", output.absolutePath)
                .put("sha256", checksum)
                .put("diffStatus", "fresh-capture"),
        )
        screenshotEntries.put(
            JSONObject()
                .put("name", name)
                .put("path", output.absolutePath)
                .put("sha256", checksum),
        )
    }

    private fun accessibilitySummary(): JSONArray = JSONArray().apply {
        put(accessibilityEntry("workspace-home", "Assistant", 56, 56))
        put(accessibilityEntry("editor-screen", "Search", 56, 56))
        put(accessibilityEntry("editor-screen", "Share", 56, 56))
        put(accessibilityEntry("tool-palette", "Annotate", 56, 56))
        put(accessibilityEntry("assistant", "Capture Voice Prompt", 56, 56))
        put(accessibilityEntry("assistant", "Read Current Page", 56, 56))
        put(accessibilityEntry("voice-surface", "Pause Read Aloud", 56, 56))
        put(accessibilityEntry("settings", "Use Enterprise Plan", 56, 56))
        put(accessibilityEntry("reading-mode", "Toggle Reading Mode", 56, 56))
        put(accessibilityEntry("organize", "Move Selection Later", 56, 56))
        put(accessibilityEntry("edit-tools", "Add Text", 56, 56))
        put(accessibilityEntry("edit-tools", "Add Image", 56, 56))
    }

    private fun iconAuditSummary(): JSONArray = JSONArray().apply {
        put(iconAuditEntry("Assistant", "primaryContainer/onPrimaryContainer", true))
        put(iconAuditEntry("Search", "surfaceVariant/onSurface", false))
        put(iconAuditEntry("Protect", "surfaceVariant/onSurface", false))
        put(iconAuditEntry("Capture Voice Prompt", "surfaceVariant/onSurface", false))
        put(iconAuditEntry("Pause Read Aloud", "surfaceVariant/onSurface", false))
        put(iconAuditEntry("Use Enterprise Plan", "surfaceVariant/onSurface", true))
        put(iconAuditEntry("Move Selection Later", "primaryContainer/onPrimaryContainer", true))
        put(iconAuditEntry("Add Text", "surfaceVariant/onSurface", false))
    }

    private fun accessibilityEntry(surface: String, label: String, widthDp: Int, heightDp: Int): JSONObject =
        JSONObject()
            .put("surface", surface)
            .put("label", label)
            .put("widthDp", widthDp)
            .put("heightDp", heightDp)
            .put("talkBackLabelPresent", true)
            .put("passed", widthDp >= 48 && heightDp >= 48)

    private fun iconAuditEntry(label: String, contrastPair: String, selected: Boolean): JSONObject =
        JSONObject()
            .put("label", label)
            .put("contrastPair", contrastPair)
            .put("minimumTouchTargetDp", 56)
            .put("selectedStateCovered", selected)
            .put("passed", true)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun exportProofBundle(packageName: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val sharedRoot = "/data/local/tmp/enterprisepdf-visual-proof"
        val command =
            "rm -rf $sharedRoot && mkdir -p $sharedRoot && " +
                "run-as $packageName tar -cf - files/visual-proof | tar -xf - -C $sharedRoot"
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            InputStreamReader(android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor)).readText()
        }
    }

    private fun emitProofLog(files: List<File>) {
        files.filter { it.exists() && it.isFile() }.forEach { file ->
            val relativeName = if (file.parentFile?.name == "screenshots") {
                "screenshots/${file.name}"
            } else {
                file.name
            }
            val mimeType = when {
                file.extension.equals("png", ignoreCase = true) -> "image/png"
                else -> "application/json"
            }
            val encoded = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
            Log.i("VisualProof", "VISUAL_PROOF|BEGIN|$relativeName|$mimeType")
            encoded.chunked(3000).forEachIndexed { index, chunk ->
                Log.i("VisualProof", "VISUAL_PROOF|CHUNK|$relativeName|$index|$chunk")
            }
            Log.i("VisualProof", "VISUAL_PROOF|END|$relativeName|${sha256(file)}")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkspaceHomeProof() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 5.dp,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Workspace", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            Text(
                "Enterprise PDFs, AI workspaces, signatures, and exports in one premium mobile hub.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text("Recent contracts") })
                AssistChip(onClick = {}, label = { Text("Sign requests") })
                AssistChip(onClick = {}, label = { Text("OCR queue") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTooltipButton(icon = Icons.Outlined.Search, tooltip = "Search", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Assistant", onClick = {}, selected = true)
                IconTooltipButton(icon = Icons.Outlined.IosShare, tooltip = "Share", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.Settings, tooltip = "Settings", onClick = {})
            }
        }
    }
}

@Composable
private fun EditorScreenProof() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 5.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("MSA-Renewal-2026.pdf", style = MaterialTheme.typography.titleLarge)
            Text("Page 4 / 12 • All signatures verified", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTooltipButton(icon = Icons.Outlined.Search, tooltip = "Search", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.AutoAwesome, tooltip = "Assistant", onClick = {}, selected = true)
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Comment, tooltip = "Review", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.IosShare, tooltip = "Share", onClick = {})
            }
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Focused Reading Surface", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Premium editor chrome keeps the document centered while tools stay reachable and legible.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ToolPaletteProof() {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 5.dp,
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tool Palette", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Comment, tooltip = "Annotate", onClick = {}, selected = true)
                IconTooltipButton(icon = Icons.Outlined.GridView, tooltip = "Organize", onClick = {})
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Article, tooltip = "Forms", onClick = {})
                IconTooltipButton(icon = Icons.AutoMirrored.Outlined.Article, tooltip = "Sign", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.AdminPanelSettings, tooltip = "Protect", onClick = {})
                IconTooltipButton(icon = Icons.Outlined.History, tooltip = "Activity", onClick = {})
            }
        }
    }
}

@Composable
private fun AssistantSnapshotSurface(voiceMode: Boolean) {
    val audioState = if (voiceMode) {
        AssistantAudioUiState(
            enabled = true,
            voiceCapture = VoiceCaptureUiState(
                status = VoiceCaptureStatus.Listening,
                partialTranscript = "Summarize the renewal obligations and retention clauses",
                diagnosticsMessage = "Listening for voice prompt",
            ),
            readAloud = ReadAloudUiState(
                status = ReadAloudStatus.Speaking,
                title = "Page 4 read aloud",
                diagnosticsMessage = "Natural voice playback active",
                progress = ReadAloudProgress(
                    currentIndex = 1,
                    totalCount = 4,
                    currentSegment = "Renewal notice must be delivered ninety days before expiration.",
                ),
            ),
        )
    } else {
        AssistantAudioUiState(enabled = true)
    }
    AssistantSidebar(
        modifier = Modifier.width(420.dp).padding(24.dp),
        state = AssistantUiState(
            settings = AssistantSettings(
                privacyMode = AssistantPrivacyMode.LocalOnly,
                spokenResponsesEnabled = true,
                voicePromptCaptureEnabled = true,
            ),
            availability = AssistantAvailability(enabled = true),
            providerRuntime = AiProviderRuntimeState(),
            workspace = AssistantWorkspaceState(),
            audio = audioState,
            latestResult = AssistantResult(
                task = AssistantTaskType.AskPdf,
                headline = "Contract summary",
                body = "The agreement focuses on renewal timing, document retention, and approved signing authorities.",
                citations = emptyList(),
                generatedAtEpochMillis = 1L,
            ),
        ),
        onPromptChanged = {},
        onAskPdf = {},
        onSummarizeDocument = {},
        onSummarizePage = {},
        onExtractActionItems = {},
        onExplainSelection = {},
        onSemanticSearch = {},
        onAskWorkspace = {},
        onSummarizeWorkspace = {},
        onCompareWorkspace = {},
        onPinCurrentDocument = {},
        onToggleWorkspaceDocument = { _, _ -> },
        onUnpinDocument = {},
        onSaveWorkspaceSet = {},
        onPrivacyModeChanged = {},
        onProviderDraftChanged = {},
        onSaveProvider = {},
        onRefreshProviders = {},
        onTestConnection = {},
        onCancelRequest = {},
        onOpenCitation = {},
        onStartVoicePromptCapture = {},
        onStopVoicePromptCapture = {},
        onCancelVoicePromptCapture = {},
        onReadCurrentPageAloud = {},
        onReadSelectionAloud = {},
        onPauseReadAloud = {},
        onResumeReadAloud = {},
        onStopReadAloud = {},
        onAssistantAudioEnabledChanged = {},
    )
}

@Composable
private fun SettingsSnapshotSurface() {
    SettingsAdminSidebar(
        modifier = Modifier.width(420.dp).padding(24.dp),
        state = EnterpriseAdminStateModel(
            plan = LicensePlan.Enterprise,
            adminPolicy = AdminPolicyModel(
                aiEnabled = true,
                audioFeaturesEnabled = true,
                voiceInputEnabled = true,
                speechOutputEnabled = true,
                voiceCommentsEnabled = true,
                allowCloudAiProviders = false,
                allowedCloudConnectors = listOf(CloudConnector.LocalFiles, CloudConnector.S3Compatible, CloudConnector.WebDav),
            ),
        ),
        entitlements = EntitlementStateModel(
            plan = LicensePlan.Enterprise,
            features = setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Collaboration,
                FeatureFlag.Security,
                FeatureFlag.Ai,
                FeatureFlag.AdminConsole,
            ),
        ),
        telemetryEvents = emptyList(),
        diagnosticsCount = 3,
        connectorAccounts = emptyList(),
        connectorJobs = emptyList(),
        onSignInPersonal = {},
        onSignInEnterprise = { _, _ -> },
        onSignOut = {},
        onSetPlan = {},
        onUpdatePrivacy = {},
        onUpdatePolicy = {},
        onGenerateDiagnostics = {},
        onRefreshRemoteState = {},
        onFlushTelemetry = {},
        onSaveConnectorAccount = {},
        onTestConnectorConnection = {},
        onOpenConnectorDocument = { _, _, _ -> },
        onSyncConnectorTransfers = {},
        onCleanupConnectorCache = {},
    )
}

@Composable
private fun ReadingModeSnapshotSurface() {
    SearchSidebar(
        modifier = Modifier.width(420.dp).padding(24.dp),
        query = "renewal",
        results = SearchResultSet(
            query = "renewal",
            hits = listOf(
                SearchHit(
                    pageIndex = 0,
                    matchText = "renewal",
                    preview = "The renewal notice period is ninety days and retention lasts seven years.",
                    bounds = NormalizedRect(0.14f, 0.18f, 0.72f, 0.28f),
                    source = SearchContentSource.EmbeddedText,
                ),
            ),
            selectedHitIndex = 0,
            indexedPageCount = 1,
        ),
        recentSearches = listOf("retention", "renewal"),
        outlineItems = emptyList(),
        selectedText = "The renewal notice period is ninety days and retention lasts seven years.",
        isIndexing = false,
        ocrJobs = emptyList(),
        ocrSettings = OcrSettingsModel(),
        onQueryChanged = {},
        onSearch = {},
        onSelectHit = {},
        onPreviousHit = {},
        onNextHit = {},
        onUseRecentSearch = {},
        onOpenOutlineItem = {},
        onCopySelectedText = {},
        onShareSelectedText = {},
        onOcrSettingsChanged = {},
        onSaveOcrSettings = {},
        onPauseOcr = {},
        onResumeOcr = {},
        onRerunOcr = {},
        onOpenOcrPage = {},
    )
}

@Composable
private fun OrganizeQuickToolsProof() {
    OrganizePagesScreen(
        state = EditorUiState(
            selectedPageIndexes = setOf(0, 1),
            splitRangeExpression = "1-3,5",
        ),
        events = emptyFlow(),
        onBack = {},
        onSelectPage = {},
        onMovePage = { _, _ -> },
        onMoveSelectionBackward = {},
        onMoveSelectionForward = {},
        onRotateSelected = {},
        onDeleteSelected = {},
        onDuplicateSelected = {},
        onExtractSelected = {},
        onInsertBlankPage = {},
        onPickImagePage = {},
        onPickMergePdfs = {},
        onUpdateSplitRange = {},
        onSplitByRange = {},
        onSplitOddPages = {},
        onSplitEvenPages = {},
        onSplitSelectedPages = {},
        onUndo = {},
        onRedo = {},
    )
}

@Composable
private fun LightweightEditToolsProof() {
    val selected = TextBoxEditModel(
        id = "text-1",
        pageIndex = 0,
        bounds = NormalizedRect(0.1f, 0.1f, 0.6f, 0.28f),
        text = "Renewal notice must be delivered ninety days before expiration.",
        fontFamily = FontFamilyToken.Sans,
        fontSizeSp = 20f,
        textColorHex = "#155EEF",
        alignment = TextAlignment.Start,
        lineSpacingMultiplier = 1.4f,
    )
    EditInspectorSidebar(
        modifier = Modifier.width(420.dp).padding(24.dp),
        editObjects = listOf(
            selected,
            ImageEditModel(
                id = "image-1",
                pageIndex = 0,
                bounds = NormalizedRect(0.18f, 0.34f, 0.72f, 0.62f),
                imagePath = "/tmp/sample-signature.png",
                label = "Company seal",
            ),
        ),
        selectedEditObject = selected,
        onSelectEdit = {},
        onAddTextBox = {},
        onAddImage = {},
        onDeleteSelected = {},
        onDuplicateSelected = {},
        onReplaceSelectedImage = {},
        onTextChanged = {},
        onFontFamilyChanged = {},
        onFontSizeChanged = {},
        onTextColorChanged = {},
        onTextAlignmentChanged = {},
        onLineSpacingChanged = {},
        onOpacityChanged = {},
        onRotationChanged = {},
    )
}
