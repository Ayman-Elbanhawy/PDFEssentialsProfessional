package com.aymanelbanhawy.editor.core.connectors

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import com.aymanelbanhawy.editor.core.data.ConnectorAccountDao
import com.aymanelbanhawy.editor.core.data.ConnectorAccountEntity
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobDao
import com.aymanelbanhawy.editor.core.data.ConnectorTransferJobEntity
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataDao
import com.aymanelbanhawy.editor.core.data.RemoteDocumentMetadataEntity
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.CloudConnector
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.PolicySyncMetadataModel
import com.aymanelbanhawy.editor.core.enterprise.PrivacySettingsModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.aymanelbanhawy.editor.core.model.AnnotationExportMode
import com.aymanelbanhawy.editor.core.model.DocumentModel
import com.aymanelbanhawy.editor.core.model.DocumentSourceType
import com.aymanelbanhawy.editor.core.model.PageModel
import com.aymanelbanhawy.editor.core.model.PdfDocumentRef
import com.aymanelbanhawy.editor.core.repository.DocumentRepository
import com.aymanelbanhawy.editor.core.repository.DraftRestoreResult
import com.aymanelbanhawy.editor.core.security.AppLockReason
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AppLockStateModel
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.DocumentPermissionModel
import com.aymanelbanhawy.editor.core.security.PolicyDecision
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import com.google.common.truth.Truth.assertThat
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test

class DefaultConnectorRepositoryTest {
    private lateinit var context: Context
    private lateinit var accountDao: TestConnectorAccountDao
    private lateinit var metadataDao: TestRemoteDocumentMetadataDao
    private lateinit var transferJobDao: TestConnectorTransferJobDao
    private lateinit var enterpriseRepository: TestEnterpriseAdminRepository
    private lateinit var securityRepository: TestSecurityRepository
    private lateinit var documentRepository: TestDocumentRepository
    private lateinit var repository: DefaultConnectorRepository
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before
    fun setUp() {
        val root = File("build/test-temp/${UUID.randomUUID()}").apply { mkdirs() }
        context = TestContext(root)
        accountDao = TestConnectorAccountDao()
        metadataDao = TestRemoteDocumentMetadataDao()
        transferJobDao = TestConnectorTransferJobDao()
        enterpriseRepository = TestEnterpriseAdminRepository()
        securityRepository = TestSecurityRepository()
        documentRepository = TestDocumentRepository(context.cacheDir)
        repository = DefaultConnectorRepository(context, accountDao, metadataDao, transferJobDao, documentRepository, enterpriseRepository, securityRepository, PassthroughCipher(), json)
    }

    @After
    fun tearDown() {
        File(context.cacheDir, "connector-cache").deleteRecursively()
        File(context.cacheDir, "connector-export").deleteRecursively()
        File(context.cacheDir, "test-documents").deleteRecursively()
        File(context.filesDir, "connector-secrets").deleteRecursively()
    }

    @Test
    fun saveAccount_blocks_disallowed_connector_by_policy() = runTest {
        enterpriseRepository.state = enterpriseRepository.state.copy(adminPolicy = enterpriseRepository.state.adminPolicy.copy(allowedCloudConnectors = listOf(CloudConnector.LocalFiles)))
        val error = runCatching { repository.saveAccount(ConnectorAccountDraft(CloudConnector.WebDav, "Corp WebDAV", "http://127.0.0.1:9999")) }.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(error?.message).contains("not allowed")
    }

    @Test
    fun saveAccount_blocks_nonapproved_destination_pattern() = runTest {
        enterpriseRepository.state = enterpriseRepository.state.copy(
            adminPolicy = enterpriseRepository.state.adminPolicy.copy(
                allowedCloudConnectors = listOf(CloudConnector.LocalFiles, CloudConnector.WebDav),
                allowedDestinationPatterns = listOf("approved.example.com"),
            ),
        )
        val error = runCatching { repository.saveAccount(ConnectorAccountDraft(CloudConnector.WebDav, "Restricted", "https://blocked.example.com/webdav")) }.exceptionOrNull()
        assertThat(error).isNotNull()
        assertThat(error?.message).contains("not approved")
    }

    @Test
    fun syncPendingTransfers_marks_conflict_when_local_target_changed() = runTest {
        val target = File(context.cacheDir, "test-documents/conflict.pdf").apply { parentFile?.mkdirs(); writeText("existing") }
        metadataDao.upsert(RemoteDocumentMetadataEntity("local-files::${target.absolutePath}", "local-files", target.absolutePath, target.name, "old", target.lastModified(), "stale-etag", "stale", target.length(), "application/pdf", "{}", null, System.currentTimeMillis()))
        repository.queueExport(sampleDocument(), ConnectorSaveRequest("local-files", target.absolutePath, target.name, AnnotationExportMode.Editable, SaveDestinationMode.SaveCopy))
        val completed = repository.syncPendingTransfers()
        val job = repository.transferJobs().first()
        assertThat(completed).isEqualTo(0)
        assertThat(job.status).isEqualTo(TransferStatus.Failed)
        assertThat(job.lastError).contains("changed since last sync")
    }

    @Test
    fun syncPendingTransfers_recovers_after_remote_becomes_available() = runTest {
        val probe = ServerSocket(0)
        val port = probe.localPort
        probe.close()
        enterpriseRepository.state = enterpriseRepository.state.copy(adminPolicy = enterpriseRepository.state.adminPolicy.copy(allowedCloudConnectors = listOf(CloudConnector.LocalFiles, CloudConnector.WebDav)))
        val account = repository.saveAccount(ConnectorAccountDraft(CloudConnector.WebDav, "Retry WebDAV", "http://127.0.0.1:$port"))
        repository.queueExport(sampleDocument(), ConnectorSaveRequest(account.id, "/retry/document.pdf", "document.pdf", AnnotationExportMode.Editable, SaveDestinationMode.SaveCopy))
        val firstAttempt = repository.syncPendingTransfers()
        assertThat(firstAttempt).isEqualTo(0)
        assertThat(repository.transferJobs().first().status).isEqualTo(TransferStatus.Failed)
        MiniWebDavServer(port).use { server ->
            server.start()
            val secondAttempt = repository.syncPendingTransfers()
            val job = repository.transferJobs().first()
            assertThat(secondAttempt).isEqualTo(1)
            assertThat(job.status).isEqualTo(TransferStatus.Completed)
            assertThat(server.payloads).containsKey("/retry/document.pdf")
        }
    }

    private fun sampleDocument(): DocumentModel {
        val file = File(context.cacheDir, "test-documents/source.pdf").apply { parentFile?.mkdirs(); writeText("sample") }
        return DocumentModel(
            sessionId = UUID.randomUUID().toString(),
            documentRef = PdfDocumentRef(file.toURI().toString(), file.name, null, DocumentSourceType.File, file.absolutePath, file.absolutePath),
            pages = listOf(PageModel(index = 0)),
            security = SecurityDocumentModel(permissions = DocumentPermissionModel()),
        )
    }
}

private class MiniWebDavServer(private val port: Int) : AutoCloseable {
    private val running = AtomicBoolean(false)
    private lateinit var serverSocket: ServerSocket
    private var thread: Thread? = null
    val payloads: MutableMap<String, ByteArray> = ConcurrentHashMap()
    fun start() {
        serverSocket = ServerSocket(port)
        running.set(true)
        thread = Thread {
            while (running.get()) {
                runCatching { serverSocket.accept() }.onSuccess { socket -> handle(socket) }.onFailure { if (running.get()) throw it }
            }
        }.apply { isDaemon = true; start() }
    }
    private fun handle(socket: Socket) {
        socket.use { client ->
            val input = BufferedInputStream(client.getInputStream())
            val requestLine = input.readHttpLine()
            if (requestLine.isBlank()) return
            val parts = requestLine.split(" ")
            val method = parts[0]
            val path = parts[1]
            val headers = linkedMapOf<String, String>()
            while (true) {
                val line = input.readHttpLine()
                if (line.isBlank()) break
                val separator = line.indexOf(':')
                if (separator > 0) headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }
            val output = client.getOutputStream()
            when (method) {
                "PUT" -> {
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    val bytes = ByteArray(contentLength)
                    input.readFully(bytes)
                    payloads[path] = bytes
                    output.write("HTTP/1.1 201 Created\r\nETag: etag-1\r\nContent-Length: 0\r\n\r\n".toByteArray())
                }
                "HEAD" -> {
                    if (payloads.containsKey(path)) {
                        val body = payloads.getValue(path)
                        output.write("HTTP/1.1 200 OK\r\nETag: etag-1\r\nContent-Length: ${body.size}\r\n\r\n".toByteArray())
                    } else {
                        output.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".toByteArray())
                    }
                }
                else -> output.write("HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\n\r\n".toByteArray())
            }
            output.flush()
        }
    }
    override fun close() { running.set(false); runCatching { serverSocket.close() }; thread?.join(500) }
}

private fun InputStream.readHttpLine(): String {
    val builder = StringBuilder()
    while (true) {
        val value = read()
        if (value == -1 || value == '\n'.code) break
        if (value != '\r'.code) builder.append(value.toChar())
    }
    return builder.toString()
}
private fun InputStream.readFully(target: ByteArray) { var offset = 0; while (offset < target.size) { val count = read(target, offset, target.size - offset); if (count == -1) break; offset += count } }
private class PassthroughCipher : SecureFileCipher { override fun encryptToFile(plainBytes: ByteArray, destination: File) { destination.parentFile?.mkdirs(); destination.writeBytes(plainBytes) }; override fun decryptFromFile(source: File): ByteArray = source.readBytes() }
private class TestDocumentRepository(private val root: File) : DocumentRepository {
    override suspend fun open(request: com.aymanelbanhawy.editor.core.model.OpenDocumentRequest): DocumentModel = error("Not needed")
    override suspend fun importPages(requests: List<com.aymanelbanhawy.editor.core.model.OpenDocumentRequest>): List<PageModel> = emptyList()
    override suspend fun persistDraft(payload: com.aymanelbanhawy.editor.core.model.DraftPayload, autosave: Boolean) = Unit
    override suspend fun restoreDraft(sourceKey: String): DraftRestoreResult? = null
    override suspend fun clearDraft(sourceKey: String) = Unit
    override suspend fun save(document: DocumentModel, exportMode: AnnotationExportMode): DocumentModel = document
    override suspend fun saveAs(document: DocumentModel, destination: File, exportMode: AnnotationExportMode): DocumentModel { destination.parentFile?.mkdirs(); destination.writeText("export-${document.documentRef.displayName}-${exportMode.name}"); return document.copy(documentRef = document.documentRef.copy(uriString = destination.toURI().toString(), sourceKey = destination.absolutePath, workingCopyPath = destination.absolutePath)) }
    override suspend fun split(document: DocumentModel, request: com.aymanelbanhawy.editor.core.organize.SplitRequest, outputDirectory: File): List<File> = emptyList()
    override fun createAutosaveTempFile(sessionId: String): File = File(root, "$sessionId.json")
}
private class TestEnterpriseAdminRepository : EnterpriseAdminRepository {
    var state: EnterpriseAdminStateModel = EnterpriseAdminStateModel(authSession = AuthSessionModel(isSignedIn = true), plan = LicensePlan.Enterprise, privacySettings = PrivacySettingsModel(), adminPolicy = AdminPolicyModel(allowExternalSharing = true, allowedCloudConnectors = listOf(CloudConnector.LocalFiles, CloudConnector.S3Compatible, CloudConnector.WebDav, CloudConnector.DocumentProvider)), policySync = PolicySyncMetadataModel(), tenantConfiguration = TenantConfigurationModel())
    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) { this.state = state }
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(state.plan, emptySet())
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File = destination
}
private class TestSecurityRepository : SecurityRepository {
    private val mutableLockState = MutableStateFlow(AppLockStateModel())
    override val appLockState: StateFlow<AppLockStateModel> = mutableLockState
    override suspend fun loadAppLockSettings(): AppLockSettingsModel = AppLockSettingsModel()
    override suspend fun updateAppLockSettings(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int): AppLockSettingsModel = AppLockSettingsModel(enabled = enabled)
    override suspend fun lockApp(reason: AppLockReason) = Unit
    override suspend fun unlockWithPin(pin: String): Boolean = true
    override suspend fun unlockWithBiometric(): Boolean = true
    override suspend fun loadDocumentSecurity(documentKey: String): SecurityDocumentModel = SecurityDocumentModel()
    override suspend fun persistDocumentSecurity(documentKey: String, security: SecurityDocumentModel) = Unit
    override suspend fun inspectDocument(document: DocumentModel) = error("Not needed")
    override fun evaluatePolicy(security: SecurityDocumentModel, action: RestrictedAction): PolicyDecision = PolicyDecision(true)
    override suspend fun recordAudit(event: AuditTrailEventModel) = Unit
    override suspend fun auditEvents(documentKey: String): List<AuditTrailEventModel> = emptyList()
    override suspend fun exportAuditTrail(documentKey: String, destination: File): File = destination
}
private class TestConnectorAccountDao : ConnectorAccountDao {
    private val entities = linkedMapOf<String, ConnectorAccountEntity>()
    override suspend fun upsert(entity: ConnectorAccountEntity) { entities[entity.id] = entity }
    override suspend fun all(): List<ConnectorAccountEntity> = entities.values.sortedBy { it.displayName }
    override suspend fun get(id: String): ConnectorAccountEntity? = entities[id]
    override suspend fun deleteById(id: String) { entities.remove(id) }
}
private class TestRemoteDocumentMetadataDao : RemoteDocumentMetadataDao {
    private val entities = linkedMapOf<String, RemoteDocumentMetadataEntity>()
    override suspend fun upsert(entity: RemoteDocumentMetadataEntity) { entities[entity.documentKey] = entity }
    override suspend fun get(documentKey: String): RemoteDocumentMetadataEntity? = entities[documentKey]
    override suspend fun deleteByDocumentKey(documentKey: String) { entities.remove(documentKey) }
}
private class TestConnectorTransferJobDao : ConnectorTransferJobDao {
    private val entities = linkedMapOf<String, ConnectorTransferJobEntity>()
    override suspend fun upsert(entity: ConnectorTransferJobEntity) { entities[entity.id] = entity }
    override suspend fun get(id: String): ConnectorTransferJobEntity? = entities[id]
    override suspend fun pending(): List<ConnectorTransferJobEntity> = entities.values.filter { it.status in listOf("Pending", "Failed", "Paused") }.sortedBy { it.createdAtEpochMillis }
    override suspend fun all(): List<ConnectorTransferJobEntity> = entities.values.sortedByDescending { it.updatedAtEpochMillis }
    override suspend fun deleteById(id: String) { entities.remove(id) }
    override suspend fun deleteCompletedBefore(thresholdEpochMillis: Long) { entities.entries.removeIf { it.value.status == "Completed" && it.value.updatedAtEpochMillis < thresholdEpochMillis } }
}
private class TestContext(root: File) : ContextWrapper(Application()) {
    private val cache = File(root, "cache").apply { mkdirs() }
    private val files = File(root, "files").apply { mkdirs() }
    override fun getCacheDir(): File = cache
    override fun getFilesDir(): File = files
}

