package com.aymanelbanhawy.enterprisepdf.app.enterprise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountDraft
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorTransferJobModel
import com.aymanelbanhawy.editor.core.enterprise.AiFeatureAccessResolver
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseBootstrapMode
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.enterprisepdf.app.connectors.ConnectorSettingsSection
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@Composable
fun SettingsAdminSidebar(
    modifier: Modifier,
    state: EnterpriseAdminStateModel,
    entitlements: EntitlementStateModel,
    telemetryEvents: List<TelemetryEventModel>,
    diagnosticsCount: Int,
    connectorAccounts: List<ConnectorAccountModel>,
    connectorJobs: List<ConnectorTransferJobModel>,
    onSignInPersonal: (String) -> Unit,
    onSignInEnterprise: (String, TenantConfigurationModel) -> Unit,
    onSignOut: () -> Unit,
    onSetPlan: (LicensePlan) -> Unit,
    onUpdatePrivacy: (PrivacySettingsModel) -> Unit,
    onUpdatePolicy: (AdminPolicyModel) -> Unit,
    onGenerateDiagnostics: () -> Unit,
    onRefreshRemoteState: () -> Unit,
    onFlushTelemetry: () -> Unit,
    onSaveConnectorAccount: (ConnectorAccountDraft) -> Unit,
    onTestConnectorConnection: (String) -> Unit,
    onOpenConnectorDocument: (String, String, String) -> Unit,
    onSyncConnectorTransfers: () -> Unit,
    onCleanupConnectorCache: () -> Unit,
) {
    var personalName by remember(state.authSession.displayName) { mutableStateOf(state.authSession.displayName) }
    var enterpriseEmail by remember(state.authSession.email) { mutableStateOf(state.authSession.email) }
    var tenantName by remember(state.tenantConfiguration.tenantName) { mutableStateOf(state.tenantConfiguration.tenantName) }
    var tenantDomain by remember(state.tenantConfiguration.domain) { mutableStateOf(state.tenantConfiguration.domain) }
    var tenantBaseUrl by remember(state.tenantConfiguration.apiBaseUrl) { mutableStateOf(state.tenantConfiguration.apiBaseUrl) }
    var issuerBaseUrl by remember(state.tenantConfiguration.issuerBaseUrl) { mutableStateOf(state.tenantConfiguration.issuerBaseUrl) }
    var remoteBootstrap by remember(state.tenantConfiguration.bootstrapMode) { mutableStateOf(state.tenantConfiguration.bootstrapMode == EnterpriseBootstrapMode.Remote) }
    val aiAccess = remember(entitlements, state.adminPolicy) {
    AiFeatureAccessResolver.resolve(entitlements, state.adminPolicy)
}

    Surface(
        modifier = modifier
            .semantics { paneTitle = "Settings and admin panel" }
            .testTag("settings-admin-sidebar"),
        tonalElevation = 5.dp,
        shadowElevation = 12.dp,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Authentication", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Mode: ${state.authSession.mode.name} | ${state.authSession.status.name}")
                        Text("Policy: ${state.policySync.policyVersion}  ETag: ${state.policySync.policyEtag ?: "-"}")
                        OutlinedTextField(value = personalName, onValueChange = { personalName = it }, label = { Text("Personal display name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = enterpriseEmail, onValueChange = { enterpriseEmail = it }, label = { Text("Enterprise email") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("Tenant name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tenantDomain, onValueChange = { tenantDomain = it }, label = { Text("Tenant domain") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = tenantBaseUrl, onValueChange = { tenantBaseUrl = it }, label = { Text("Tenant API base URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = issuerBaseUrl, onValueChange = { issuerBaseUrl = it }, label = { Text("Issuer base URL") }, modifier = Modifier.fillMaxWidth())
                        ToggleRow("Use remote enterprise bootstrap", remoteBootstrap) { remoteBootstrap = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Person, tooltip = "Use Personal Mode", onClick = { onSignInPersonal(personalName) })
                            IconTooltipButton(
                                icon = Icons.Outlined.Apartment,
                                tooltip = "Sign In To Enterprise",
                                onClick = {
                                    onSignInEnterprise(
                                        enterpriseEmail,
                                        TenantConfigurationModel(
                                            tenantId = tenantDomain.ifBlank { "enterprise" },
                                            tenantName = tenantName.ifBlank { "Enterprise Tenant" },
                                            domain = tenantDomain,
                                            apiBaseUrl = tenantBaseUrl,
                                            issuerBaseUrl = issuerBaseUrl,
                                            bootstrapMode = if (remoteBootstrap) EnterpriseBootstrapMode.Remote else EnterpriseBootstrapMode.LocalDevelopment,
                                        ),
                                    )
                                },
                            )
                            IconTooltipButton(icon = Icons.Outlined.Logout, tooltip = "Sign Out", onClick = onSignOut)
                            IconTooltipButton(icon = Icons.Outlined.CloudSync, tooltip = "Refresh Remote State", onClick = onRefreshRemoteState)
                        }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Tenant + License", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Tenant: ${state.tenantConfiguration.tenantName}")
                        Text("Tenant ID: ${state.tenantConfiguration.tenantId}")
                        Text("Bootstrap: ${state.tenantConfiguration.bootstrapMode.name}")
                        Text("Plan: ${state.plan.name}")
                        Text("Last sync: ${state.policySync.lastPolicySyncAtEpochMillis ?: 0}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.Badge, tooltip = "Use Free Plan", selected = state.plan == LicensePlan.Free, onClick = { onSetPlan(LicensePlan.Free) })
                            IconTooltipButton(icon = Icons.Outlined.WorkspacePremium, tooltip = "Use Premium Plan", selected = state.plan == LicensePlan.Premium, onClick = { onSetPlan(LicensePlan.Premium) })
                            IconTooltipButton(icon = Icons.Outlined.Apartment, tooltip = "Use Enterprise Plan", selected = state.plan == LicensePlan.Enterprise, onClick = { onSetPlan(LicensePlan.Enterprise) })
                        }
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Entitlements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        FeatureFlag.entries.forEach { flag ->
                            Text("${flag.name}: ${flag in entitlements.features}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item {
                ConnectorSettingsSection(
                    accounts = connectorAccounts,
                    jobs = connectorJobs,
                    onSaveAccount = onSaveConnectorAccount,
                    onTestConnection = onTestConnectorConnection,
                    onOpenRemoteDocument = onOpenConnectorDocument,
                    onSyncTransfers = onSyncConnectorTransfers,
                    onCleanupCache = onCleanupConnectorCache,
                )
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Admin Policy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        ToggleRow("Restrict export", state.adminPolicy.restrictExport) { onUpdatePolicy(state.adminPolicy.copy(restrictExport = it)) }
                        ToggleRow("Restrict print", state.adminPolicy.restrictPrint) { onUpdatePolicy(state.adminPolicy.copy(restrictPrint = it)) }
                        ToggleRow("Restrict copy", state.adminPolicy.restrictCopy) { onUpdatePolicy(state.adminPolicy.copy(restrictCopy = it)) }
                        ToggleRow(
                            label = "AI enabled",
                            checked = state.adminPolicy.aiEnabled,
                            enabled = aiAccess.entitled,
                            switchTestTag = "settings-admin-ai-enabled-switch",
                        ) { onUpdatePolicy(state.adminPolicy.copy(aiEnabled = it)) }
                        Text(
                            text = aiAccess.adminControlMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (aiAccess.entitled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("settings-admin-ai-enabled-message"),
                        )
                        ToggleRow("Audio features enabled", state.adminPolicy.audioFeaturesEnabled) { onUpdatePolicy(state.adminPolicy.copy(audioFeaturesEnabled = it)) }
                        ToggleRow("Voice input enabled", state.adminPolicy.voiceInputEnabled) { onUpdatePolicy(state.adminPolicy.copy(voiceInputEnabled = it)) }
                        ToggleRow("Speech output enabled", state.adminPolicy.speechOutputEnabled) { onUpdatePolicy(state.adminPolicy.copy(speechOutputEnabled = it)) }
                        ToggleRow("Voice comments enabled", state.adminPolicy.voiceCommentsEnabled) { onUpdatePolicy(state.adminPolicy.copy(voiceCommentsEnabled = it)) }
                        ToggleRow("Allow cloud AI", state.adminPolicy.allowCloudAiProviders) { onUpdatePolicy(state.adminPolicy.copy(allowCloudAiProviders = it)) }
                        ToggleRow("Allow external sharing", state.adminPolicy.allowExternalSharing) { onUpdatePolicy(state.adminPolicy.copy(allowExternalSharing = it)) }
                        OutlinedTextField(
                            value = state.adminPolicy.forcedWatermarkText,
                            onValueChange = { onUpdatePolicy(state.adminPolicy.copy(forcedWatermarkText = it)) },
                            label = { Text("Forced watermark") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Privacy + Telemetry", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        ToggleRow("Telemetry enabled", state.privacySettings.telemetryEnabled) { onUpdatePrivacy(state.privacySettings.copy(telemetryEnabled = it)) }
                        ToggleRow("Include document names", state.privacySettings.includeDocumentNames) { onUpdatePrivacy(state.privacySettings.copy(includeDocumentNames = it)) }
                        ToggleRow("Include diagnostics", state.privacySettings.includeDiagnostics) { onUpdatePrivacy(state.privacySettings.copy(includeDiagnostics = it)) }
                        ToggleRow("Local-only mode", state.privacySettings.localOnlyMode) { onUpdatePrivacy(state.privacySettings.copy(localOnlyMode = it)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTooltipButton(icon = Icons.Outlined.CloudDone, tooltip = "Generate Diagnostics Bundle", onClick = onGenerateDiagnostics)
                            IconTooltipButton(icon = Icons.Outlined.Policy, tooltip = "Upload Telemetry Batch", onClick = onFlushTelemetry)
                        }
                        Text("Bundles created: $diagnosticsCount")
                        Text("Queued telemetry: ${telemetryEvents.count { it.uploadState.name != "Uploaded" }}")
                    }
                }
            }
            items(telemetryEvents.take(20), key = { it.id }) { event ->
                Card(shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(event.name, style = MaterialTheme.typography.labelLarge)
                        Text("${event.category.name} | ${event.uploadState.name}", style = MaterialTheme.typography.bodySmall)
                        Text(event.failureMessage ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    switchTestTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = if (switchTestTag != null) Modifier.testTag(switchTestTag) else Modifier,
        )
    }
}

