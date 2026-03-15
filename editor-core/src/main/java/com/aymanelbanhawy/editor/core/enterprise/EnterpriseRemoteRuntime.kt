package com.aymanelbanhawy.editor.core.enterprise

import android.content.Context
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface EnterpriseRemoteDataSource {
    suspend fun bootstrap(request: TenantBootstrapRequest): TenantBootstrapResponse
    suspend fun refreshSession(state: EnterpriseAdminStateModel, refreshToken: String?): SessionRefreshResponse
    suspend fun syncPolicy(state: EnterpriseAdminStateModel, request: PolicySyncRequest): PolicySyncResponse
    suspend fun revokeSession(state: EnterpriseAdminStateModel, accessToken: String?)
    suspend fun uploadTelemetry(state: EnterpriseAdminStateModel, request: TelemetryBatchUploadRequest, accessToken: String?): TelemetryBatchUploadResponse
}

class EnterpriseCredentialStore(
    context: Context,
    private val json: Json,
    private val cipher: SecureFileCipher = AndroidSecureFileCipher(context.applicationContext, "enterprise_pdf_enterprise_tokens"),
) {
    private val root = File(context.applicationContext.filesDir, "secure-enterprise").apply { mkdirs() }

    suspend fun store(alias: String, secret: String) = withContext(Dispatchers.IO) {
        cipher.encryptToFile(
            json.encodeToString(SecretPayload.serializer(), SecretPayload(secret)).toByteArray(StandardCharsets.UTF_8),
            fileFor(alias),
        )
    }

    suspend fun load(alias: String?): String? = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext null
        val file = fileFor(alias)
        if (!file.exists()) return@withContext null
        runCatching {
            json.decodeFromString(
                SecretPayload.serializer(),
                cipher.decryptFromFile(file).toString(StandardCharsets.UTF_8),
            ).value
        }.getOrNull()
    }

    suspend fun clear(alias: String?) = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext
        fileFor(alias).delete()
    }

    fun newAlias(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    private fun fileFor(alias: String): File = File(root, sha256(alias) + ".bin")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Serializable
    private data class SecretPayload(val value: String)
}

open class EnterpriseRemoteRegistry(
    private val context: Context,
    private val json: Json,
) {
    open fun select(tenant: TenantConfigurationModel): EnterpriseRemoteDataSource {
        return if (tenant.bootstrapMode == EnterpriseBootstrapMode.Remote && tenant.apiBaseUrl.isNotBlank()) {
            HttpEnterpriseRemoteDataSource(json, tenant.apiBaseUrl)
        } else {
            LocalEnterpriseEmulatorDataSource(File(context.filesDir, "enterprise-emulator"), json)
        }
    }
}

class HttpEnterpriseRemoteDataSource(
    private val json: Json,
    private val baseUrl: String,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 45_000,
    private val requestTimeoutMillis: Long = 60_000,
    private val retryCount: Int = 3,
) : EnterpriseRemoteDataSource {

    override suspend fun bootstrap(request: TenantBootstrapRequest): TenantBootstrapResponse {
        return requestJson(
            method = "POST",
            url = buildUrl("/v1/tenant/bootstrap"),
            requestBody = json.encodeToString(TenantBootstrapRequest.serializer(), request),
            responseSerializer = TenantBootstrapResponse.serializer(),
        )
    }

    override suspend fun refreshSession(state: EnterpriseAdminStateModel, refreshToken: String?): SessionRefreshResponse {
        val payload = mapOf(
            "tenantId" to state.tenantConfiguration.tenantId,
            "sessionId" to state.authSession.session.sessionId,
            "refreshToken" to (refreshToken ?: ""),
        )
        return requestJson(
            method = "POST",
            url = buildUrl("/v1/session/refresh"),
            requestBody = json.encodeToString(StringMapSerializer, payload),
            responseSerializer = SessionRefreshResponse.serializer(),
        )
    }

    override suspend fun syncPolicy(state: EnterpriseAdminStateModel, request: PolicySyncRequest): PolicySyncResponse {
        return requestJson(
            method = "GET",
            url = buildUrl("/v1/tenant/policy", mapOf("tenantId" to request.tenantId)),
            requestBody = null,
            responseSerializer = PolicySyncResponse.serializer(),
            headers = if (request.ifNoneMatch != null) mapOf("If-None-Match" to request.ifNoneMatch) else emptyMap(),
        )
    }

    override suspend fun revokeSession(state: EnterpriseAdminStateModel, accessToken: String?) {
        requestUnit(
            method = "POST",
            url = buildUrl("/v1/session/revoke"),
            requestBody = json.encodeToString(StringMapSerializer, mapOf("sessionId" to state.authSession.session.sessionId)),
            headers = if (accessToken != null) mapOf("Authorization" to "Bearer $accessToken") else emptyMap(),
        )
    }

    override suspend fun uploadTelemetry(state: EnterpriseAdminStateModel, request: TelemetryBatchUploadRequest, accessToken: String?): TelemetryBatchUploadResponse {
        return requestJson(
            method = "POST",
            url = buildUrl(state.tenantConfiguration.telemetryPath),
            requestBody = json.encodeToString(TelemetryBatchUploadRequest.serializer(), request),
            responseSerializer = TelemetryBatchUploadResponse.serializer(),
            headers = if (accessToken != null) mapOf("Authorization" to "Bearer $accessToken") else emptyMap(),
        )
    }

    private suspend fun requestUnit(method: String, url: String, requestBody: String?, headers: Map<String, String>) {
        execute(method, url, requestBody, headers)
    }

    private suspend fun <T> requestJson(
        method: String,
        url: String,
        requestBody: String?,
        responseSerializer: kotlinx.serialization.KSerializer<T>,
        headers: Map<String, String> = emptyMap(),
    ): T {
        val response = execute(method, url, requestBody, headers)
        if (response.code !in 200..299) throw EnterpriseRemoteException(response.code, response.body ?: "Unexpected HTTP ${response.code}", response.code >= 500 || response.code == 429)
        return json.decodeFromString(responseSerializer, response.body ?: throw EnterpriseRemoteException(response.code, "Empty response", false))
    }

    private suspend fun execute(method: String, url: String, body: String?, headers: Map<String, String>): HttpResponse {
        var lastError: Throwable? = null
        var attempt = 0
        while (attempt <= retryCount) {
            try {
                return withTimeout(requestTimeoutMillis) {
                    withContext(Dispatchers.IO) {
                        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = method
                            connectTimeout = this@HttpEnterpriseRemoteDataSource.connectTimeoutMillis
                            readTimeout = this@HttpEnterpriseRemoteDataSource.readTimeoutMillis
                            doInput = true
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Content-Type", "application/json")
                            headers.forEach { (key, value) -> setRequestProperty(key, value) }
                            if (body != null) {
                                doOutput = true
                                outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
                            }
                        }
                        val code = connection.responseCode
                        val responseBody = runCatching {
                            (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                        HttpResponse(code, responseBody)
                    }
                }
            } catch (throwable: Throwable) {
                lastError = throwable
                val retryable = throwable is java.io.IOException || throwable is SocketTimeoutException
                if (!retryable || attempt >= retryCount) break
            }
            attempt += 1
            withContext(Dispatchers.IO) { Thread.sleep((1000L shl attempt.coerceAtMost(3)).coerceAtMost(8_000L)) }
        }
        throw when (val error = lastError) {
            is EnterpriseRemoteException -> error
            is SocketTimeoutException -> EnterpriseRemoteException(408, "Enterprise request timed out.", true)
            else -> EnterpriseRemoteException(null, error?.message ?: "Unable to reach enterprise service.", true)
        }
    }

    private fun buildUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val querySuffix = if (query.isEmpty()) "" else query.entries.joinToString(prefix = "?", separator = "&") {
            "${URLEncoder.encode(it.key, StandardCharsets.UTF_8.name())}=${URLEncoder.encode(it.value, StandardCharsets.UTF_8.name())}"
        }
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/') + querySuffix
    }

    private data class HttpResponse(val code: Int, val body: String?)
}

class LocalEnterpriseEmulatorDataSource(
    private val rootDir: File,
    private val json: Json,
) : EnterpriseRemoteDataSource {
    private val stateFile = File(rootDir, "tenant-state.json")

    init {
        rootDir.mkdirs()
    }

    override suspend fun bootstrap(request: TenantBootstrapRequest): TenantBootstrapResponse = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val tenantId = request.tenantHint.ifBlank { request.email.substringAfter('@', "enterprise") }
        val current = readState()
        val tenantConfiguration = TenantConfigurationModel(
            tenantId = tenantId,
            tenantName = tenantId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " Workspace",
            domain = tenantId,
            issuerBaseUrl = "https://$tenantId",
            apiBaseUrl = "https://$tenantId/api",
            bootstrapMode = EnterpriseBootstrapMode.LocalDevelopment,
            supportsRemotePolicySync = true,
            bootstrapCompletedAtEpochMillis = now,
            oidc = OidcProviderConfig(
                issuerUrl = "https://$tenantId/.well-known/openid-configuration",
                clientId = "enterprise-pdf-mobile",
                redirectUri = "enterprisepdf://auth/callback",
            ),
            collaboration = current.tenantConfiguration.collaboration.copy(
                baseUrl = current.tenantConfiguration.collaboration.baseUrl.ifBlank { "https://$tenantId/api" },
                requireEnterpriseAuth = true,
                backendMode = CollaborationBackendMode.RemoteHttp,
            ),
        )
        val tokenSession = TokenBackedSessionModel(
            sessionId = UUID.randomUUID().toString(),
            tokenType = "Bearer",
            issuedAtEpochMillis = now,
            accessTokenExpiresAtEpochMillis = now + 45 * 60_000,
            refreshTokenExpiresAtEpochMillis = now + 7 * 24 * 60 * 60_000L,
        )
        val authSession = AuthSessionModel(
            mode = AuthenticationMode.Enterprise,
            provider = AuthenticationProvider.Oidc,
            status = AuthSessionStatus.Active,
            isSignedIn = true,
            displayName = request.email.substringBefore('@').ifBlank { tenantConfiguration.tenantName },
            email = request.email,
            subjectId = request.email,
            sessionExpiresAtEpochMillis = tokenSession.accessTokenExpiresAtEpochMillis,
            session = tokenSession,
            lastRefreshedAtEpochMillis = now,
        )
        val policy = current.policy.takeIf { it != AdminPolicyModel() } ?: AdminPolicyModel(
            retentionDays = 90,
            aiEnabled = true,
            allowCloudAiProviders = false,
            allowCollaborationSync = true,
            allowExternalSharing = false,
            collaborationScope = CollaborationScope.TenantOnly,
            forcedWatermarkText = "Confidential",
        )
        val response = TenantBootstrapResponse(
            tenantConfiguration = tenantConfiguration,
            authSession = authSession,
            plan = LicensePlan.Enterprise,
            adminPolicy = policy,
            remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
            policySync = PolicySyncMetadataModel(
                policyVersion = current.policyVersion,
                policyEtag = current.policyEtag,
                lastBootstrapAtEpochMillis = now,
                lastPolicySyncAtEpochMillis = now,
                lastEntitlementRefreshAtEpochMillis = now,
            ),
        )
        writeState(current.copy(tenantConfiguration = tenantConfiguration, policy = policy, lastServerSyncAt = now))
        response
    }

    override suspend fun refreshSession(state: EnterpriseAdminStateModel, refreshToken: String?): SessionRefreshResponse = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val refreshedSession = state.authSession.copy(
            status = AuthSessionStatus.Active,
            isSignedIn = true,
            sessionExpiresAtEpochMillis = now + 45 * 60_000,
            session = state.authSession.session.copy(
                issuedAtEpochMillis = now,
                accessTokenExpiresAtEpochMillis = now + 45 * 60_000,
            ),
            lastRefreshedAtEpochMillis = now,
            lastFailure = null,
        )
        val current = readState()
        SessionRefreshResponse(
            authSession = refreshedSession,
            plan = LicensePlan.Enterprise,
            remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
            policyVersion = current.policyVersion,
            policyEtag = current.policyEtag,
        )
    }

    override suspend fun syncPolicy(state: EnterpriseAdminStateModel, request: PolicySyncRequest): PolicySyncResponse = withContext(Dispatchers.IO) {
        val current = readState()
        val now = System.currentTimeMillis()
        val modified = request.ifNoneMatch != current.policyEtag
        PolicySyncResponse(
            modified = modified,
            adminPolicy = current.policy,
            plan = LicensePlan.Enterprise,
            policyVersion = current.policyVersion,
            policyEtag = current.policyEtag,
            remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
            serverTimestampEpochMillis = now,
        )
    }

    override suspend fun revokeSession(state: EnterpriseAdminStateModel, accessToken: String?) = Unit

    override suspend fun uploadTelemetry(state: EnterpriseAdminStateModel, request: TelemetryBatchUploadRequest, accessToken: String?): TelemetryBatchUploadResponse = withContext(Dispatchers.IO) {
        val file = File(rootDir, "telemetry-${state.tenantConfiguration.tenantId}.jsonl")
        file.parentFile?.mkdirs()
        request.events.forEach { event ->
            file.appendText(json.encodeToString(TelemetryEventModel.serializer(), event) + "\n")
        }
        TelemetryBatchUploadResponse(
            acceptedIds = request.events.map { it.id },
            serverTimestampEpochMillis = System.currentTimeMillis(),
        )
    }

    private fun readState(): EmulatorTenantState {
        if (!stateFile.exists()) {
            return EmulatorTenantState()
        }
        return runCatching {
            json.decodeFromString(EmulatorTenantState.serializer(), stateFile.readText())
        }.getOrDefault(EmulatorTenantState())
    }

    private fun writeState(state: EmulatorTenantState) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(json.encodeToString(EmulatorTenantState.serializer(), state))
    }

    @Serializable
    private data class EmulatorTenantState(
        val tenantConfiguration: TenantConfigurationModel = TenantConfigurationModel(),
        val policy: AdminPolicyModel = AdminPolicyModel(),
        val policyVersion: String = "dev-1",
        val policyEtag: String = "dev-etag-1",
        val lastServerSyncAt: Long? = null,
    )
}

class EnterpriseRemoteException(
    val httpStatus: Int?,
    override val message: String,
    val retryable: Boolean,
) : IllegalStateException(message)

private val StringMapSerializer = MapSerializer(String.serializer(), String.serializer())
