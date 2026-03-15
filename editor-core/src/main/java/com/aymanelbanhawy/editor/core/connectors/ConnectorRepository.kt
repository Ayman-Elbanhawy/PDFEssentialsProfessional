package com.aymanelbanhawy.editor.core.connectors

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorAccountEntity
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobEntity
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataDao
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataEntity
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.OpenDocumentRequest
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.security.AuditEventType
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface ConnectorRepository {
    suspend fun descriptors(): List<ConnectorDescriptor>
    suspend fun accounts(): List<ConnectorAccountModel>
    suspend fun saveAccount(draft: ConnectorAccountDraft): ConnectorAccountModel
    suspend fun testConnection(accountId: String): ConnectorPolicyDecision
    suspend fun browse(accountId: String, path: String): List<ConnectorItemModel>
    suspend fun openDocument(accountId: String, remotePath: String, displayName: String): OpenDocumentRequest
    suspend fun queueExport(document: DocumentModel, request: ConnectorSaveRequest): ConnectorTransferJobModel
    suspend fun syncPendingTransfers(): Int
    suspend fun transferJobs(): List<ConnectorTransferJobModel>
    suspend fun allowedDestinations(forShare: Boolean): List<ConnectorAccountModel>
    suspend fun cleanupCache(): Int
}

@Serializable
data class ConnectorAccountDraft(
    val connectorType: CloudConnector,
    val displayName: String,
    val baseUrl: String,
    val credentialType: ConnectorCredentialType = ConnectorCredentialType.None,
    val username: String = "",
    val secret: String = "",
    val configuration: ConnectorConfigurationModel = ConnectorConfigurationModel(),
    val isEnterpriseManaged: Boolean = false,
)

private interface StorageConnector {
    val descriptor: ConnectorDescriptor
    suspend fun testConnection(account: ConnectorAccountModel, secret: String?): ConnectorPolicyDecision
    suspend fun list(account: ConnectorAccountModel, secret: String?, path: String): List<ConnectorItemModel>
    suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?): ConnectorFileMetadata
    suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?, conflictStrategy: ConnectorConflictStrategy): ConnectorFileMetadata
}

class ConnectorCredentialStore(context: Context, private val cipher: SecureFileCipher, private val json: Json) {
    private val root = File(context.filesDir, "connector-secrets").apply { mkdirs() }
    suspend fun store(alias: String, secret: String) = withContext(Dispatchers.IO) {
        val file = File(root, alias.sha256() + ".bin")
        cipher.encryptToFile(json.encodeToString(SecretPayload.serializer(), SecretPayload(secret)).toByteArray(StandardCharsets.UTF_8), file)
    }
    suspend fun load(alias: String?): String? = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext null
        val file = File(root, alias.sha256() + ".bin")
        if (!file.exists()) return@withContext null
        runCatching { json.decodeFromString(SecretPayload.serializer(), cipher.decryptFromFile(file).toString(StandardCharsets.UTF_8)).secret }.getOrNull()
    }
    @Serializable private data class SecretPayload(val secret: String)
}

class SecureConnectorCache(private val context: Context, private val cipher: SecureFileCipher) {
    private val encryptedDir = File(context.cacheDir, "connector-cache/encrypted").apply { mkdirs() }
    private val tempDir = File(context.cacheDir, "connector-cache/temp").apply { mkdirs() }
    suspend fun stash(file: File): File = withContext(Dispatchers.IO) {
        val encrypted = File(encryptedDir, UUID.randomUUID().toString() + ".bin")
        cipher.encryptToFile(file.readBytes(), encrypted)
        encrypted
    }
    suspend fun materialize(encryptedFile: File, displayName: String): File = withContext(Dispatchers.IO) {
        val target = File(tempDir, UUID.randomUUID().toString() + "_" + displayName)
        target.parentFile?.mkdirs(); target.writeBytes(cipher.decryptFromFile(encryptedFile)); target
    }
    suspend fun clearTemp(file: File?) = withContext(Dispatchers.IO) { file?.delete() }
    suspend fun evictOlderThan(thresholdEpochMillis: Long): Int = withContext(Dispatchers.IO) {
        (encryptedDir.listFiles().orEmpty().toList() + tempDir.listFiles().orEmpty().toList()).count { file -> file.lastModified() < thresholdEpochMillis && file.delete() }
    }
}

class DefaultConnectorRepository(
    private val context: Context,
    private val accountDao: ConnectorAccountDao,
    private val remoteDocumentMetadataDao: RemoteDocumentMetadataDao,
    private val transferJobDao: ConnectorTransferJobDao,
    private val documentRepository: DocumentRepository,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val securityRepository: SecurityRepository,
    private val secureFileCipher: SecureFileCipher,
    private val json: Json,
) : ConnectorRepository {
    private val credentialStore = ConnectorCredentialStore(context, secureFileCipher, json)
    private val secureCache = SecureConnectorCache(context, secureFileCipher)
    private val connectors = listOf(LocalFilesConnector(), S3CompatibleConnector(), WebDavConnector(), DocumentProviderConnector(context)).associateBy { it.descriptor.connectorType }

    override suspend fun descriptors(): List<ConnectorDescriptor> = connectors.values.map { it.descriptor }.sortedBy { it.title }
    override suspend fun accounts(): List<ConnectorAccountModel> {
        val stored = accountDao.all().map { it.toModel(json) }
        return if (stored.none { it.connectorType == CloudConnector.LocalFiles }) listOf(defaultLocalAccount()) + stored else stored
    }
    override suspend fun saveAccount(draft: ConnectorAccountDraft): ConnectorAccountModel {
        val descriptor = requireNotNull(connectors[draft.connectorType]).descriptor
        val policy = enforceDestinationPolicy(draft.connectorType, draft.baseUrl, false)
        if (!policy.allowed) throw IllegalStateException(policy.message)
        val now = System.currentTimeMillis()
        val secretAlias = draft.secret.takeIf { it.isNotBlank() }?.let { "connector-${UUID.randomUUID()}" }
        if (secretAlias != null) credentialStore.store(secretAlias, draft.secret)
        val model = ConnectorAccountModel(
            id = UUID.randomUUID().toString(),
            connectorType = draft.connectorType,
            displayName = draft.displayName.ifBlank { descriptor.title },
            baseUrl = draft.baseUrl,
            credentialType = draft.credentialType,
            username = draft.username,
            secretAlias = secretAlias,
            capabilities = descriptor.capabilities,
            configuration = draft.configuration,
            supportsOpen = ConnectorCapability.Open in descriptor.capabilities,
            supportsSave = ConnectorCapability.Save in descriptor.capabilities,
            supportsShare = ConnectorCapability.Share in descriptor.capabilities,
            supportsImport = ConnectorCapability.Import in descriptor.capabilities,
            supportsMetadataSync = ConnectorCapability.MetadataSync in descriptor.capabilities,
            supportsResumableTransfer = ConnectorCapability.ResumableDownload in descriptor.capabilities || ConnectorCapability.ResumableUpload in descriptor.capabilities,
            isEnterpriseManaged = draft.isEnterpriseManaged,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        accountDao.upsert(model.toEntity(json))
        return model
    }
    override suspend fun testConnection(accountId: String): ConnectorPolicyDecision {
        val account = resolveAccount(accountId)
        val policy = enforceDestinationPolicy(account.connectorType, account.baseUrl, false)
        if (!policy.allowed) return policy
        return resolveConnector(account).testConnection(account, credentialStore.load(account.secretAlias))
    }
    override suspend fun browse(accountId: String, path: String): List<ConnectorItemModel> {
        val account = resolveAccount(accountId)
        val policy = enforceDestinationPolicy(account.connectorType, account.baseUrl, false)
        if (!policy.allowed) throw IllegalStateException(policy.message)
        return resolveConnector(account).list(account, credentialStore.load(account.secretAlias), path)
    }
    override suspend fun openDocument(accountId: String, remotePath: String, displayName: String): OpenDocumentRequest {
        val account = resolveAccount(accountId)
        val tempFile = File(context.cacheDir, "connector-open/${UUID.randomUUID()}_$displayName").apply { parentFile?.mkdirs() }
        val key = remoteKey(account.id, remotePath)
        val metadata = remoteDocumentMetadataDao.get(key)?.toModel(json)
        val refreshed = resolveConnector(account).fetch(account, credentialStore.load(account.secretAlias), remotePath, tempFile, metadata)
        remoteDocumentMetadataDao.upsert(refreshed.toEntity(key, json))
        securityRepository.recordAudit(audit(remotePath, AuditEventType.ConnectorOpened, "Opened from ${account.displayName}"))
        return OpenDocumentRequest.FromFile(tempFile.absolutePath, displayNameOverride = displayName)
    }
    override suspend fun queueExport(document: DocumentModel, request: ConnectorSaveRequest): ConnectorTransferJobModel {
        val account = resolveAccount(request.connectorAccountId)
        val docPolicy = enforceDocumentPolicy(document, request.destinationMode)
        if (!docPolicy.allowed) throw IllegalStateException(docPolicy.message)
        val destPolicy = enforceDestinationPolicy(account.connectorType, account.baseUrl, request.destinationMode == SaveDestinationMode.ShareCopy)
        if (!destPolicy.allowed) throw IllegalStateException(destPolicy.message)
        val tempExport = File(context.cacheDir, "connector-export/${UUID.randomUUID()}_${request.displayName}").apply { parentFile?.mkdirs() }
        val exported = documentRepository.saveAs(prepareDocumentForDestination(document), tempExport, request.exportMode)
        val encrypted = secureCache.stash(File(exported.documentRef.workingCopyPath))
        val previousMetadata = remoteDocumentMetadataDao.get(remoteKey(account.id, request.remotePath))?.toModel(json)
        val now = System.currentTimeMillis()
        val job = ConnectorTransferJobModel(
            id = UUID.randomUUID().toString(),
            connectorAccountId = account.id,
            documentKey = document.documentRef.sourceKey,
            remotePath = request.remotePath,
            localCachePath = encrypted.absolutePath,
            direction = TransferDirection.Upload,
            status = TransferStatus.Pending,
            bytesTransferred = 0,
            totalBytes = encrypted.length(),
            remoteEtag = previousMetadata?.etag,
            remoteVersionId = previousMetadata?.versionId,
            conflictStrategy = request.conflictStrategy,
            cacheExpiresAtEpochMillis = now + CACHE_TTL_MILLIS,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
        )
        transferJobDao.upsert(job.toEntity())
        return job
    }
    override suspend fun syncPendingTransfers(): Int {
        var completed = 0
        for (entity in transferJobDao.pending()) {
            val job = entity.toModel()
            val account = resolveAccountOrNull(job.connectorAccountId) ?: continue
            val encrypted = File(job.localCachePath)
            if (!encrypted.exists()) {
                transferJobDao.upsert(job.copy(status = TransferStatus.Failed, attemptCount = job.attemptCount + 1, lastError = "Encrypted cache missing.", updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
                continue
            }
            val temp = secureCache.materialize(encrypted, File(job.remotePath).name.ifBlank { "document.pdf" })
            val metadata = remoteDocumentMetadataDao.get(remoteKey(account.id, job.remotePath))?.toModel(json)
            try {
                transferJobDao.upsert(job.copy(status = TransferStatus.Running, tempMaterializedPath = temp.absolutePath, updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
                val uploaded = resolveConnector(account).push(account, credentialStore.load(account.secretAlias), temp, job.remotePath, metadata, job.conflictStrategy)
                transferJobDao.upsert(job.copy(status = TransferStatus.Completed, tempMaterializedPath = null, bytesTransferred = temp.length(), totalBytes = temp.length(), remoteEtag = uploaded.etag, remoteVersionId = uploaded.versionId, updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
                remoteDocumentMetadataDao.upsert(uploaded.toEntity(remoteKey(account.id, uploaded.remotePath), json))
                securityRepository.recordAudit(audit(job.documentKey, if (account.connectorType == CloudConnector.LocalFiles) AuditEventType.ConnectorSaved else AuditEventType.ConnectorShared, "Transferred to ${account.displayName}", mapOf("remotePath" to uploaded.remotePath, "connectorType" to account.connectorType.name)))
                completed += 1
            } catch (error: Throwable) {
                transferJobDao.upsert(job.copy(status = TransferStatus.Failed, tempMaterializedPath = null, attemptCount = job.attemptCount + 1, lastError = error.message, updatedAtEpochMillis = System.currentTimeMillis()).toEntity())
            } finally {
                secureCache.clearTemp(temp)
            }
        }
        return completed
    }
    override suspend fun transferJobs(): List<ConnectorTransferJobModel> = transferJobDao.all().map { it.toModel() }
    override suspend fun allowedDestinations(forShare: Boolean): List<ConnectorAccountModel> = accounts().filter { enforceDestinationPolicy(it.connectorType, it.baseUrl, forShare).allowed }
    override suspend fun cleanupCache(): Int { transferJobDao.deleteCompletedBefore(System.currentTimeMillis() - CACHE_TTL_MILLIS); return secureCache.evictOlderThan(System.currentTimeMillis() - CACHE_TTL_MILLIS) }

    private suspend fun resolveAccount(id: String): ConnectorAccountModel = requireNotNull(resolveAccountOrNull(id)) { "Unknown connector account $id" }
    private suspend fun resolveAccountOrNull(id: String): ConnectorAccountModel? = if (id == LOCAL_ACCOUNT_ID) defaultLocalAccount() else accountDao.get(id)?.toModel(json)
    private fun resolveConnector(account: ConnectorAccountModel): StorageConnector = requireNotNull(connectors[account.connectorType])
    private suspend fun enforceDestinationPolicy(connector: CloudConnector, baseUrl: String, forShare: Boolean): ConnectorPolicyDecision {
        val state = enterpriseAdminRepository.loadState()
        if (connector !in state.adminPolicy.allowedCloudConnectors && connector != CloudConnector.LocalFiles) return ConnectorPolicyDecision(false, "$connector is not allowed by tenant policy.")
        if (forShare && !state.adminPolicy.allowExternalSharing && connector !in setOf(CloudConnector.LocalFiles, CloudConnector.DocumentProvider)) return ConnectorPolicyDecision(false, "External sharing is blocked by tenant policy.")
        val patterns = state.adminPolicy.allowedDestinationPatterns
        if (patterns.isNotEmpty() && !patterns.any { patternMatches(it, extractHostOrPath(baseUrl)) }) return ConnectorPolicyDecision(false, "Destination ${extractHostOrPath(baseUrl)} is not approved by tenant policy.")
        return ConnectorPolicyDecision(true)
    }
    private fun enforceDocumentPolicy(document: DocumentModel, mode: SaveDestinationMode): ConnectorPolicyDecision {
        val action = if (mode == SaveDestinationMode.ShareCopy) RestrictedAction.Share else RestrictedAction.Export
        val decision = securityRepository.evaluatePolicy(document.security, action)
        return ConnectorPolicyDecision(decision.allowed, decision.message)
    }
    private suspend fun prepareDocumentForDestination(document: DocumentModel): DocumentModel {
        val state = enterpriseAdminRepository.loadState()
        val text = state.adminPolicy.forcedWatermarkText
        return document.copy(security = document.security.copy(
            tenantPolicy = document.security.tenantPolicy.copy(
                forcedWatermarkText = document.security.tenantPolicy.forcedWatermarkText.ifBlank { text },
                forceMetadataScrub = document.security.tenantPolicy.forceMetadataScrub || state.adminPolicy.restrictExport,
            ),
            watermark = if (text.isNotBlank()) document.security.watermark.copy(enabled = true, text = text) else document.security.watermark,
            metadataScrub = if (state.adminPolicy.restrictExport) document.security.metadataScrub.copy(enabled = true) else document.security.metadataScrub,
        ))
    }
    private fun remoteKey(accountId: String, path: String): String = "$accountId::$path"
    private fun audit(documentKey: String, type: AuditEventType, message: String, metadata: Map<String, String> = emptyMap()): AuditTrailEventModel = AuditTrailEventModel(UUID.randomUUID().toString(), documentKey, type, "local-user", message, System.currentTimeMillis(), metadata)
    private fun defaultLocalAccount(): ConnectorAccountModel {
        val now = System.currentTimeMillis(); val descriptor = requireNotNull(connectors[CloudConnector.LocalFiles]).descriptor
        return ConnectorAccountModel(LOCAL_ACCOUNT_ID, CloudConnector.LocalFiles, "Local Files", context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath, capabilities = descriptor.capabilities, configuration = ConnectorConfigurationModel(rootPath = context.filesDir.parentFile?.absolutePath ?: context.filesDir.absolutePath, enforceTls = false), supportsResumableTransfer = true, createdAtEpochMillis = now, updatedAtEpochMillis = now)
    }
    companion object { private const val LOCAL_ACCOUNT_ID = "local-files"; private const val CACHE_TTL_MILLIS = 86_400_000L }
}

private class LocalFilesConnector : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.LocalFiles, "Local Files", false, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.Browse, ConnectorCapability.MetadataSync, ConnectorCapability.Versioning, ConnectorCapability.ConflictDetection, ConnectorCapability.BackgroundTransfer, ConnectorCapability.ResumableDownload, ConnectorCapability.ResumableUpload), setOf(ConnectorCredentialType.None))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { val root = File(account.configuration.rootPath.ifBlank { account.baseUrl }.ifBlank { "." }); ConnectorPolicyDecision(root.exists() || root.mkdirs(), if (root.exists() || root.mkdirs()) null else "Cannot access local path.") }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String) = withContext(Dispatchers.IO) { File(if (path.isBlank()) account.configuration.rootPath.ifBlank { account.baseUrl } else path).listFiles().orEmpty().sortedBy { it.name.lowercase() }.map { ConnectorItemModel(it.absolutePath, it.name, it.isDirectory, if (it.isFile) it.toMetadata(account.id) else null) } }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val source = File(remotePath); copyFileChunked(source, destination, account.configuration.chunkSizeBytes); source.toMetadata(account.id) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?, conflictStrategy: ConnectorConflictStrategy) = withContext(Dispatchers.IO) { var target = File(remotePath); target.parentFile?.mkdirs(); val current = target.takeIf { it.exists() }?.toMetadata(account.id); if (current != null && previousMetadata?.etag != null && current.etag != previousMetadata.etag) when (conflictStrategy) { ConnectorConflictStrategy.Fail -> throw IllegalStateException("Local destination changed since last sync."); ConnectorConflictStrategy.KeepBoth -> target = conflictSibling(target); ConnectorConflictStrategy.OverwriteRemote -> Unit }; copyFileChunked(source, target, account.configuration.chunkSizeBytes); target.toMetadata(account.id) }
}
private class S3CompatibleConnector : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.S3Compatible, "S3-Compatible", true, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.Browse, ConnectorCapability.MetadataSync, ConnectorCapability.Versioning, ConnectorCapability.ConflictDetection, ConnectorCapability.BackgroundTransfer, ConnectorCapability.ResumableDownload), setOf(ConnectorCredentialType.AccessKeySecret, ConnectorCredentialType.Bearer))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { runCatching { val request = buildRequest(account, secret, "GET", "/${account.configuration.bucket}", mapOf("list-type" to "2", "max-keys" to "1")); request.responseCode in 200..299 }.fold({ ConnectorPolicyDecision(it, if (it) null else "Unable to reach S3-compatible endpoint.") }, { ConnectorPolicyDecision(false, it.message) }) }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String) = withContext(Dispatchers.IO) { val prefix = buildPrefix(account, path); val request = buildRequest(account, secret, "GET", "/${account.configuration.bucket}", mapOf("list-type" to "2", "prefix" to prefix, "delimiter" to "/")); buildListFromS3Xml(account.id, prefix, request.inputStream.bufferedReader().use { it.readText() }) }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val key = buildPrefix(account, remotePath); val request = buildRequest(account, secret, "GET", "/${account.configuration.bucket}/$key"); destination.parentFile?.mkdirs(); destination.outputStream().use { output -> request.inputStream.use { it.copyTo(output) } }; metadataFromHeaders(account.id, key, request, destination.length()) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?, conflictStrategy: ConnectorConflictStrategy) = withContext(Dispatchers.IO) {
        var key = buildPrefix(account, remotePath)
        val head = runCatching { head(account, secret, key) }.getOrNull()
        if (head?.etag != null && previousMetadata?.etag != null && head.etag != previousMetadata.etag) when (conflictStrategy) { ConnectorConflictStrategy.Fail -> throw IllegalStateException("S3 object changed since last sync."); ConnectorConflictStrategy.KeepBoth -> key = conflictKey(key); ConnectorConflictStrategy.OverwriteRemote -> Unit }
        val request = buildRequest(account, secret, "PUT", "/${account.configuration.bucket}/$key", contentSha256 = sha256(source.readBytes()), contentType = "application/pdf")
        request.doOutput = true
        request.setFixedLengthStreamingMode(source.length())
        source.inputStream().use { input -> request.outputStream.use { output -> input.copyTo(output) } }
        if (request.responseCode !in 200..299) throw IllegalStateException("S3 upload failed with HTTP ${request.responseCode}")
        head(account, secret, key) ?: metadataFromFileFallback(account.id, key, source)
    }
    private fun buildPrefix(account: ConnectorAccountModel, path: String): String = listOfNotNull(account.configuration.apiPathPrefix.trim('/').ifBlank { null }, path.trim('/').ifBlank { null }).joinToString("/")
    private fun head(account: ConnectorAccountModel, secret: String?, key: String): ConnectorFileMetadata? { val connection = buildRequest(account, secret, "HEAD", "/${account.configuration.bucket}/$key"); return if (connection.responseCode in 200..299) metadataFromHeaders(account.id, key, connection, connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }) else null }
    private fun buildRequest(account: ConnectorAccountModel, secret: String?, method: String, path: String, query: Map<String, String> = emptyMap(), contentSha256: String = sha256(ByteArray(0)), contentType: String? = null): HttpURLConnection {
        require(account.configuration.bucket.isNotBlank()) { "S3 bucket is required." }
        val endpoint = URI(account.baseUrl)
        val encodedQuery = query.entries.joinToString("&") { "${urlEncode(it.key)}=${urlEncode(it.value)}" }
        val connection = (URL(account.baseUrl.trimEnd('/') + path + if (encodedQuery.isNotBlank()) "?$encodedQuery" else "").openConnection() as HttpURLConnection)
        connection.requestMethod = method; connection.connectTimeout = 20_000; connection.readTimeout = 60_000
        val host = endpoint.host + endpoint.port.takeIf { it > 0 }?.let { ":$it" }.orEmpty()
        val amzDate = amzTimestamp(); val dateStamp = amzDate.substring(0, 8)
        connection.setRequestProperty("Host", host); connection.setRequestProperty("x-amz-content-sha256", contentSha256); connection.setRequestProperty("x-amz-date", amzDate)
        if (!contentType.isNullOrBlank()) connection.setRequestProperty("Content-Type", contentType)
        when (account.credentialType) {
            ConnectorCredentialType.Bearer -> if (!secret.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $secret")
            ConnectorCredentialType.AccessKeySecret -> {
                require(account.username.isNotBlank()) { "S3 access key is required." }; require(!secret.isNullOrBlank()) { "S3 secret key is required." }
                val canonicalHeaders = buildString { append("host:$host\n"); if (!contentType.isNullOrBlank()) append("content-type:$contentType\n"); append("x-amz-content-sha256:$contentSha256\n"); append("x-amz-date:$amzDate\n") }
                val signedHeaders = listOfNotNull("host", contentType?.let { "content-type" }, "x-amz-content-sha256", "x-amz-date").joinToString(";")
                val canonicalRequest = listOf(method, path, encodedQuery, canonicalHeaders, signedHeaders, contentSha256).joinToString("\n")
                val scope = "$dateStamp/${account.configuration.region.ifBlank { "us-east-1" }}/s3/aws4_request"
                val stringToSign = listOf("AWS4-HMAC-SHA256", amzDate, scope, sha256(canonicalRequest.toByteArray(StandardCharsets.UTF_8))).joinToString("\n")
                val signature = hmacSha256Hex(deriveSigningKey(secret, dateStamp, account.configuration.region.ifBlank { "us-east-1" }, "s3"), stringToSign)
                connection.setRequestProperty("Authorization", "AWS4-HMAC-SHA256 Credential=${account.username}/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
            }
            else -> Unit
        }
        return connection
    }
}

private class WebDavConnector : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.WebDav, "WebDAV", true, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.Browse, ConnectorCapability.MetadataSync, ConnectorCapability.ConflictDetection, ConnectorCapability.BackgroundTransfer, ConnectorCapability.ResumableDownload), setOf(ConnectorCredentialType.None, ConnectorCredentialType.Basic, ConnectorCredentialType.Bearer))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { runCatching { openConnection(account, secret, account.baseUrl, "OPTIONS").responseCode }.fold({ ConnectorPolicyDecision(it in 200..299 || it == 405, if (it in 200..299 || it == 405) null else "HTTP $it") }, { ConnectorPolicyDecision(false, it.message) }) }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String) = withContext(Dispatchers.IO) { val connection = openConnection(account, secret, buildUrl(account.baseUrl, path), "PROPFIND"); connection.setRequestProperty("Depth", "1"); connection.doOutput = true; connection.outputStream.use { it.write(ByteArray(0)) }; val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }; Regex("<d:href>(.*?)</d:href>").findAll(body).mapNotNull { val href = java.net.URLDecoder.decode(it.groupValues[1], StandardCharsets.UTF_8.name()); if (href == path || href == "/") null else ConnectorItemModel(href, href.substringAfterLast('/').ifBlank { href }, href.endsWith('/')) }.toList() }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val connection = openConnection(account, secret, buildUrl(account.baseUrl, remotePath), "GET"); val existingBytes = destination.takeIf { it.exists() }?.length() ?: 0L; if (existingBytes > 0) connection.setRequestProperty("Range", "bytes=$existingBytes-"); val append = connection.responseCode == HttpURLConnection.HTTP_PARTIAL; destination.parentFile?.mkdirs(); if (append) { RandomAccessFile(destination, "rw").use { raf -> raf.seek(existingBytes); connection.inputStream.use { input -> val buffer = ByteArray(account.configuration.chunkSizeBytes.coerceAtMost(1024L * 1024L).toInt()); while (true) { val read = input.read(buffer); if (read <= 0) break; raf.write(buffer, 0, read) } } } } else { destination.outputStream().use { output -> connection.inputStream.use { it.copyTo(output) } } }; metadataFromHeaders(account.id, remotePath, connection, destination.length()) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?, conflictStrategy: ConnectorConflictStrategy) = withContext(Dispatchers.IO) { var effectivePath = remotePath; val head = runCatching { headMetadata(account, secret, effectivePath) }.getOrNull(); if (head?.etag != null && previousMetadata?.etag != null && head.etag != previousMetadata.etag) when (conflictStrategy) { ConnectorConflictStrategy.Fail -> throw IllegalStateException("Remote destination changed since last sync."); ConnectorConflictStrategy.KeepBoth -> effectivePath = conflictPath(effectivePath); ConnectorConflictStrategy.OverwriteRemote -> Unit }; val connection = openConnection(account, secret, buildUrl(account.baseUrl, effectivePath), "PUT"); connection.doOutput = true; connection.setRequestProperty("Content-Type", "application/pdf"); source.inputStream().use { input -> connection.outputStream.use { output -> input.copyTo(output) } }; val code = connection.responseCode; if (code !in 200..299 && code !in listOf(201, 204)) throw IllegalStateException("WebDAV upload failed with HTTP $code"); headMetadata(account, secret, effectivePath) ?: metadataFromFileFallback(account.id, effectivePath, source) }
    private fun headMetadata(account: ConnectorAccountModel, secret: String?, remotePath: String): ConnectorFileMetadata? { val connection = openConnection(account, secret, buildUrl(account.baseUrl, remotePath), "HEAD"); return if (connection.responseCode in 200..299) metadataFromHeaders(account.id, remotePath, connection, connection.getHeaderFieldLong("Content-Length", -1).takeIf { it >= 0 }) else null }
    private fun openConnection(account: ConnectorAccountModel, secret: String?, url: String, method: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply { requestMethod = method; connectTimeout = 15_000; readTimeout = 45_000; setRequestProperty("Accept", "application/json, text/xml, application/xml, */*"); when (account.credentialType) { ConnectorCredentialType.Basic -> if (!secret.isNullOrBlank()) setRequestProperty("Authorization", "Basic ${Base64.encodeToString("${account.username}:$secret".toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)}"); ConnectorCredentialType.Bearer -> if (!secret.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $secret"); else -> Unit } }
    private fun buildUrl(baseUrl: String, remotePath: String): String = baseUrl.trimEnd('/') + "/" + remotePath.trimStart('/').replace(" ", "%20")
}

private class DocumentProviderConnector(private val context: Context) : StorageConnector {
    override val descriptor = ConnectorDescriptor(CloudConnector.DocumentProvider, "Enterprise Document Provider", true, setOf(ConnectorCapability.Open, ConnectorCapability.Import, ConnectorCapability.Save, ConnectorCapability.Share, ConnectorCapability.Browse, ConnectorCapability.MetadataSync, ConnectorCapability.ConflictDetection), setOf(ConnectorCredentialType.None))
    override suspend fun testConnection(account: ConnectorAccountModel, secret: String?) = withContext(Dispatchers.IO) { ConnectorPolicyDecision(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == account.baseUrl || it.uri.toString().startsWith(account.baseUrl) }, "Persisted provider permission not granted.") }
    override suspend fun list(account: ConnectorAccountModel, secret: String?, path: String): List<ConnectorItemModel> = withContext(Dispatchers.IO) { val treeUri = Uri.parse(if (path.isBlank()) account.baseUrl else path); runCatching { val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri)); context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor -> buildList { val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID); val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME); val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE); while (cursor.moveToNext()) { val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(idIndex)); add(ConnectorItemModel(childUri.toString(), cursor.getString(nameIndex), cursor.getString(mimeIndex) == DocumentsContract.Document.MIME_TYPE_DIR)) } } } ?: emptyList() }.getOrDefault(emptyList()) }
    override suspend fun fetch(account: ConnectorAccountModel, secret: String?, remotePath: String, destination: File, metadata: ConnectorFileMetadata?) = withContext(Dispatchers.IO) { val uri = Uri.parse(remotePath); destination.parentFile?.mkdirs(); context.contentResolver.openInputStream(uri)?.use { input -> destination.outputStream().use { input.copyTo(it) } } ?: throw IllegalStateException("Unable to open document provider stream."); metadata ?: metadataFromFileFallback(account.id, remotePath, destination) }
    override suspend fun push(account: ConnectorAccountModel, secret: String?, source: File, remotePath: String, previousMetadata: ConnectorFileMetadata?, conflictStrategy: ConnectorConflictStrategy) = withContext(Dispatchers.IO) { val uri = Uri.parse(remotePath); context.contentResolver.openOutputStream(uri, "wt")?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: throw IllegalStateException("Unable to open document provider output stream."); metadataFromFileFallback(account.id, remotePath, source) }
}
private fun copyFileChunked(source: File, target: File, chunkSizeBytes: Long) {
    target.parentFile?.mkdirs()
    source.inputStream().use { input -> target.outputStream().use { output -> val buffer = ByteArray(chunkSizeBytes.coerceAtMost(1024L * 1024L).toInt()); while (true) { val read = input.read(buffer); if (read <= 0) break; output.write(buffer, 0, read) } } }
}
private fun buildListFromS3Xml(accountId: String, prefix: String, xml: String): List<ConnectorItemModel> {
    val directories = Regex("<CommonPrefixes>.*?<Prefix>(.*?)</Prefix>.*?</CommonPrefixes>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).map { val raw = xmlDecode(it.groupValues[1]); ConnectorItemModel(raw, raw.removePrefix(prefix).trim('/').ifBlank { raw }, true) }
    val files = Regex("<Contents>.*?<Key>(.*?)</Key>.*?<Size>(.*?)</Size>.*?</Contents>", setOf(RegexOption.DOT_MATCHES_ALL)).findAll(xml).mapNotNull { val key = xmlDecode(it.groupValues[1]); if (key == prefix || key.endsWith('/')) null else ConnectorItemModel(key, key.substringAfterLast('/').ifBlank { key }, false, ConnectorFileMetadata(accountId, key, key.substringAfterLast('/').ifBlank { key }, sizeBytes = it.groupValues[2].toLongOrNull())) }
    return (directories + files).sortedBy { it.displayName.lowercase() }.toList()
}
private fun conflictSibling(file: File): File { val base = file.nameWithoutExtension; val ext = file.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty(); return File(file.parentFile ?: file.parentFile, "$base-copy-${System.currentTimeMillis()}$ext") }
private fun conflictKey(key: String): String { val slash = key.lastIndexOf('/'); val folder = if (slash >= 0) key.substring(0, slash + 1) else ""; val file = if (slash >= 0) key.substring(slash + 1) else key; val dot = file.lastIndexOf('.'); return if (dot >= 0) "$folder${file.substring(0, dot)}-copy-${System.currentTimeMillis()}${file.substring(dot)}" else "$folder$file-copy-${System.currentTimeMillis()}" }
private fun conflictPath(path: String): String = conflictKey(path)
private fun extractHostOrPath(baseUrl: String): String = runCatching { URI(baseUrl).host ?: baseUrl }.getOrDefault(baseUrl).ifBlank { baseUrl }
private fun patternMatches(pattern: String, value: String): Boolean = Regex("^" + pattern.replace(".", "\\.").replace("*", ".*") + "$", RegexOption.IGNORE_CASE).matches(value)
private fun amzTimestamp(): String = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
private fun deriveSigningKey(secret: String, date: String, region: String, service: String): ByteArray { val kDate = hmacSha256(("AWS4$secret").toByteArray(StandardCharsets.UTF_8), date); val kRegion = hmacSha256(kDate, region); val kService = hmacSha256(kRegion, service); return hmacSha256(kService, "aws4_request") }
private fun hmacSha256(key: ByteArray, value: String): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value.toByteArray(StandardCharsets.UTF_8)) }
private fun hmacSha256Hex(key: ByteArray, value: String): String = hmacSha256(key, value).joinToString("") { "%02x".format(it) }
private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
private fun xmlDecode(value: String): String = value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")

private fun ConnectorAccountEntity.toModel(json: Json) = ConnectorAccountModel(id, CloudConnector.valueOf(connectorType), displayName, baseUrl, ConnectorCredentialType.valueOf(credentialType), username, secretAlias, json.decodeFromString(SetSerializer(ConnectorCapability.serializer()), capabilitiesJson), json.decodeFromString(ConnectorConfigurationModel.serializer(), configurationJson), supportsOpen, supportsSave, supportsShare, supportsImport, supportsMetadataSync, supportsResumableTransfer, isEnterpriseManaged, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorAccountModel.toEntity(json: Json) = ConnectorAccountEntity(id, connectorType.name, displayName, baseUrl, credentialType.name, username, secretAlias, json.encodeToString(SetSerializer(ConnectorCapability.serializer()), capabilities), json.encodeToString(ConnectorConfigurationModel.serializer(), configuration), supportsOpen, supportsSave, supportsShare, supportsImport, supportsMetadataSync, supportsResumableTransfer, isEnterpriseManaged, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorTransferJobEntity.toModel() = ConnectorTransferJobModel(id, connectorAccountId, documentKey, remotePath, localCachePath, tempMaterializedPath, TransferDirection.valueOf(direction), TransferStatus.valueOf(status), bytesTransferred, totalBytes, resumableToken, attemptCount, lastError, remoteEtag, remoteVersionId, ConnectorConflictStrategy.valueOf(conflictStrategy), cacheExpiresAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis)
private fun ConnectorTransferJobModel.toEntity() = ConnectorTransferJobEntity(id, connectorAccountId, documentKey, remotePath, localCachePath, tempMaterializedPath, direction.name, status.name, bytesTransferred, totalBytes, resumableToken, attemptCount, lastError, remoteEtag, remoteVersionId, conflictStrategy.name, cacheExpiresAtEpochMillis, createdAtEpochMillis, updatedAtEpochMillis)
private fun RemoteDocumentMetadataEntity.toModel(json: Json) = ConnectorFileMetadata(connectorAccountId, remotePath, displayName, versionId, modifiedAtEpochMillis, etag, checksumSha256, sizeBytes, mimeType, json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), providerMetadataJson), lastConflictAtEpochMillis)
private fun ConnectorFileMetadata.toEntity(documentKey: String, json: Json) = RemoteDocumentMetadataEntity(documentKey, connectorAccountId, remotePath, displayName, versionId, modifiedAtEpochMillis, etag, checksumSha256, sizeBytes, mimeType, json.encodeToString(MapSerializer(String.serializer(), String.serializer()), providerMetadata), lastConflictAtEpochMillis, System.currentTimeMillis())
private fun File.toMetadata(accountId: String) = ConnectorFileMetadata(accountId, absolutePath, name, lastModified().toString(), lastModified(), "${length()}-${lastModified()}", sha256(readBytes()), length())
private fun metadataFromHeaders(accountId: String, remotePath: String, connection: HttpURLConnection, size: Long?) = ConnectorFileMetadata(accountId, remotePath, remotePath.substringAfterLast('/').ifBlank { remotePath }, connection.getHeaderField("x-version-id") ?: connection.getHeaderField("x-amz-version-id"), connection.getHeaderFieldDate("Last-Modified", -1).takeIf { it >= 0 }, connection.getHeaderField("ETag")?.trim('"'), connection.getHeaderField("X-Checksum-Sha256"), size, connection.contentType ?: "application/pdf", buildMap { connection.getHeaderField("x-amz-version-id")?.let { put("x-amz-version-id", it) } })
private fun metadataFromFileFallback(accountId: String, remotePath: String, file: File) = ConnectorFileMetadata(accountId, remotePath, remotePath.substringAfterLast('/').ifBlank { file.name }, file.lastModified().toString(), file.lastModified(), "${file.length()}-${file.lastModified()}", sha256(file.readBytes()), file.length())
private fun String.sha256(): String = sha256(toByteArray(StandardCharsets.UTF_8))
private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }



