package com.aymanelbanhawy.enterprisepdf.app.connectors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountDraft
import com.aymanelbanhawy.editor.core.connectors.ConnectorAccountModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorConfigurationModel
import com.aymanelbanhawy.editor.core.connectors.ConnectorCredentialType
import com.aymanelbanhawy.editor.core.connectors.ConnectorTransferJobModel
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.enterprisepdf.app.ui.IconTooltipButton

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectorSettingsSection(
    modifier: Modifier = Modifier,
    accounts: List<ConnectorAccountModel>,
    jobs: List<ConnectorTransferJobModel>,
    onSaveAccount: (ConnectorAccountDraft) -> Unit,
    onTestConnection: (String) -> Unit,
    onOpenRemoteDocument: (String, String, String) -> Unit,
    onSyncTransfers: () -> Unit,
    onCleanupCache: () -> Unit,
) {
    var connectorType by remember { mutableStateOf(CloudConnector.LocalFiles) }
    var displayName by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var credentialType by remember { mutableStateOf(ConnectorCredentialType.None) }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var rootPath by remember { mutableStateOf("") }
    var bucket by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("us-east-1") }
    var apiPathPrefix by remember { mutableStateOf("") }
    var chunkSizeKb by remember { mutableStateOf("512") }
    var selectedAccountId by remember(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var remotePath by remember { mutableStateOf("") }
    var remoteDisplayName by remember { mutableStateOf("document.pdf") }

    Card(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Connectors", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(CloudConnector.LocalFiles, CloudConnector.S3Compatible, CloudConnector.WebDav, CloudConnector.DocumentProvider).forEach { type ->
                    FilterChip(selected = connectorType == type, onClick = { connectorType = type }, label = { Text(type.label()) })
                }
            }
            OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text("Account name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it }, label = { Text(baseUrlLabel(connectorType)) }, modifier = Modifier.fillMaxWidth())
            if (connectorType == CloudConnector.LocalFiles) {
                OutlinedTextField(value = rootPath, onValueChange = { rootPath = it }, label = { Text("Root path") }, modifier = Modifier.fillMaxWidth())
            }
            if (connectorType == CloudConnector.S3Compatible) {
                OutlinedTextField(value = bucket, onValueChange = { bucket = it }, label = { Text("Bucket") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = region, onValueChange = { region = it }, label = { Text("Region") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = apiPathPrefix, onValueChange = { apiPathPrefix = it }, label = { Text("Key prefix") }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedTextField(value = chunkSizeKb, onValueChange = { chunkSizeKb = it }, label = { Text("Chunk size (KB)") }, modifier = Modifier.fillMaxWidth())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableCredentials(connectorType).forEach { type ->
                    FilterChip(selected = credentialType == type, onClick = { credentialType = type }, label = { Text(type.name) })
                }
            }
            if (credentialType != ConnectorCredentialType.None) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(usernameLabel(credentialType)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = secret, onValueChange = { secret = it }, label = { Text(secretLabel(credentialType)) }, modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTooltipButton(icon = Icons.Outlined.Link, tooltip = "Save Connector Account", onClick = {
                    onSaveAccount(
                        ConnectorAccountDraft(
                            connectorType = connectorType,
                            displayName = displayName,
                            baseUrl = baseUrl,
                            credentialType = credentialType,
                            username = username,
                            secret = secret,
                            configuration = ConnectorConfigurationModel(
                                rootPath = rootPath,
                                bucket = bucket,
                                region = region,
                                apiPathPrefix = apiPathPrefix,
                                chunkSizeBytes = (chunkSizeKb.toLongOrNull() ?: 512L) * 1024L,
                            ),
                        ),
                    )
                    secret = ""
                })
                IconTooltipButton(icon = Icons.Outlined.CloudSync, tooltip = "Sync Transfers", onClick = onSyncTransfers)
                IconTooltipButton(icon = Icons.Outlined.CleaningServices, tooltip = "Evict Connector Cache", onClick = onCleanupCache)
            }

            if (accounts.isNotEmpty()) {
                Text("Available accounts", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    accounts.forEach { account ->
                        FilterChip(selected = selectedAccountId == account.id, onClick = { selectedAccountId = account.id }, label = { Text(account.displayName) })
                    }
                }
                accounts.firstOrNull { it.id == selectedAccountId }?.let { account ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(account.connectorType.label(), style = MaterialTheme.typography.labelLarge)
                            Text(account.baseUrl, style = MaterialTheme.typography.bodySmall)
                            if (account.configuration.bucket.isNotBlank()) Text("Bucket: ${account.configuration.bucket}", style = MaterialTheme.typography.bodySmall)
                            Text("Capabilities: ${account.capabilities.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedTextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text("Remote path / file URI") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = remoteDisplayName, onValueChange = { remoteDisplayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconTooltipButton(icon = Icons.Outlined.VpnKey, tooltip = "Test Connection", onClick = { onTestConnection(account.id) })
                        IconTooltipButton(icon = Icons.Outlined.CloudDownload, tooltip = "Open Remote Document", enabled = remotePath.isNotBlank(), onClick = { onOpenRemoteDocument(account.id, remotePath, remoteDisplayName.ifBlank { "document.pdf" }) })
                    }
                }
            }

            if (jobs.isNotEmpty()) {
                Text("Transfers", style = MaterialTheme.typography.titleSmall)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    jobs.take(8).forEach { job ->
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(job.remotePath, style = MaterialTheme.typography.bodyMedium)
                                    Text("${job.status.name} ${job.bytesTransferred}/${job.totalBytes}", style = MaterialTheme.typography.bodySmall)
                                    job.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                }
                                IconTooltipButton(icon = Icons.Outlined.CloudUpload, tooltip = "Sync Queue", onClick = onSyncTransfers)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun availableCredentials(connector: CloudConnector): List<ConnectorCredentialType> = when (connector) {
    CloudConnector.LocalFiles, CloudConnector.DocumentProvider -> listOf(ConnectorCredentialType.None)
    CloudConnector.S3Compatible -> listOf(ConnectorCredentialType.AccessKeySecret, ConnectorCredentialType.Bearer)
    else -> listOf(ConnectorCredentialType.None, ConnectorCredentialType.Basic, ConnectorCredentialType.Bearer)
}

private fun baseUrlLabel(connector: CloudConnector): String = when (connector) {
    CloudConnector.LocalFiles -> "Base path"
    CloudConnector.S3Compatible -> "Endpoint URL"
    CloudConnector.WebDav -> "WebDAV URL"
    CloudConnector.DocumentProvider -> "Tree URI"
    CloudConnector.GoogleDrive -> "Drive base URL"
    CloudConnector.OneDrive -> "OneDrive base URL"
    CloudConnector.SharePoint -> "SharePoint base URL"
    CloudConnector.Box -> "Box base URL"
}

private fun usernameLabel(type: ConnectorCredentialType): String = when (type) {
    ConnectorCredentialType.AccessKeySecret -> "Access key"
    ConnectorCredentialType.Basic -> "Username"
    ConnectorCredentialType.Bearer -> "Token label"
    ConnectorCredentialType.None -> ""
}

private fun secretLabel(type: ConnectorCredentialType): String = when (type) {
    ConnectorCredentialType.AccessKeySecret -> "Secret key"
    ConnectorCredentialType.Basic -> "Password"
    ConnectorCredentialType.Bearer -> "Bearer token"
    ConnectorCredentialType.None -> ""
}

private fun CloudConnector.label(): String = when (this) {
    CloudConnector.LocalFiles -> "Local Files"
    CloudConnector.S3Compatible -> "S3-Compatible"
    CloudConnector.GoogleDrive -> "Google Drive"
    CloudConnector.OneDrive -> "OneDrive"
    CloudConnector.SharePoint -> "SharePoint"
    CloudConnector.Box -> "Box"
    CloudConnector.WebDav -> "WebDAV"
    CloudConnector.DocumentProvider -> "Document Provider"
}
