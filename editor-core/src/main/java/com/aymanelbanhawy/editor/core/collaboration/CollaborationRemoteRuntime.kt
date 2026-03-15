package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.CollaborationBackendMode
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.security.AndroidSecureFileCipher
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import java.io.File
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class CollaborationCredentialStore(
    context: Context,
    private val json: Json,
    private val cipher: SecureFileCipher = AndroidSecureFileCipher(context.applicationContext, "enterprise_pdf_collaboration_tokens"),
) {
    private val root = File(context.applicationContext.filesDir, "secure-collaboration").apply { mkdirs() }

    suspend fun store(alias: String, token: String) = withContext(Dispatchers.IO) {
        cipher.encryptToFile(
            json.encodeToString(CredentialPayload.serializer(), CredentialPayload(token)).toByteArray(StandardCharsets.UTF_8),
            tokenFile(alias),
        )
    }

    suspend fun load(alias: String?): String? = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext null
        val file = tokenFile(alias)
        if (!file.exists()) return@withContext null
        runCatching {
            val payload = cipher.decryptFromFile(file).toString(StandardCharsets.UTF_8)
            json.decodeFromString(CredentialPayload.serializer(), payload).token
        }.getOrNull()
    }

    suspend fun clear(alias: String?) = withContext(Dispatchers.IO) {
        if (alias.isNullOrBlank()) return@withContext
        tokenFile(alias).delete()
    }

    private fun tokenFile(alias: String): File = File(root, sha256(alias) + ".bin")

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }

    @Serializable
    private data class CredentialPayload(val token: String)
}

open class CollaborationRemoteRegistry(
    private val context: Context,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val credentialStore: CollaborationCredentialStore,
    private val json: Json,
) {
    open suspend fun select(): CollaborationRemoteDataSource {
        val state = enterpriseAdminRepository.loadState()
        enforcePolicy(state)
        val collaboration = state.tenantConfiguration.collaboration
        val effectiveMode = resolveEffectiveMode(collaboration.backendMode, collaboration.baseUrl)
        return when (effectiveMode) {
            CollaborationBackendMode.Disabled -> throw disabledError()
            CollaborationBackendMode.RemoteHttp -> HttpCollaborationRemoteDataSource(
                json = json,
                baseUrl = collaboration.baseUrl,
                apiPath = collaboration.apiPath,
                accessTokenProvider = {
                    val authSession = state.authSession
                    if (collaboration.requireEnterpriseAuth && !authSession.isSignedIn) {
                        throw CollaborationRemoteException(
                            RemoteErrorMetadata(
                                code = RemoteErrorCode.Unauthorized,
                                message = "Enterprise sign-in is required for collaboration sync.",
                                retryable = false,
                            ),
                        )
                    }
                    credentialStore.load(authSession.collaborationCredentialAlias)
                },
                connectTimeoutMillis = collaboration.connectTimeoutMillis.toInt(),
                readTimeoutMillis = collaboration.readTimeoutMillis.toInt(),
                requestTimeoutMillis = collaboration.requestTimeoutMillis,
                retryCount = collaboration.retryCount,
            )
            CollaborationBackendMode.LocalEmulator -> throw CollaborationRemoteException(
                RemoteErrorMetadata(
                    code = RemoteErrorCode.InvalidRequest,
                    message = "Legacy local-emulator collaboration mode is no longer available in production. Configure a collaboration service endpoint.",
                    retryable = false,
                ),
            )
        }
    }

    private fun enforcePolicy(state: EnterpriseAdminStateModel) {
        if (!state.adminPolicy.allowCollaborationSync) {
            throw disabledError()
        }
        val effectiveMode = resolveEffectiveMode(
            state.tenantConfiguration.collaboration.backendMode,
            state.tenantConfiguration.collaboration.baseUrl,
        )
        if (effectiveMode == CollaborationBackendMode.RemoteHttp) {
            if (!state.adminPolicy.allowExternalSharing && state.adminPolicy.collaborationScope.name == "ExternalGuests") {
                throw CollaborationRemoteException(
                    RemoteErrorMetadata(
                        code = RemoteErrorCode.Forbidden,
                        message = "Tenant policy blocks external collaboration.",
                        retryable = false,
                    ),
                )
            }
            if (state.authSession.mode == AuthenticationMode.Personal && state.tenantConfiguration.collaboration.requireEnterpriseAuth) {
                throw CollaborationRemoteException(
                    RemoteErrorMetadata(
                        code = RemoteErrorCode.Forbidden,
                        message = "Enterprise collaboration requires enterprise mode.",
                        retryable = false,
                    ),
                )
            }
            if (state.tenantConfiguration.collaboration.baseUrl.isBlank()) {
                throw CollaborationRemoteException(
                    RemoteErrorMetadata(
                        code = RemoteErrorCode.InvalidRequest,
                        message = "A collaboration service endpoint must be configured before remote review can sync.",
                        retryable = false,
                    ),
                )
            }
        }
    }

    private fun resolveEffectiveMode(mode: CollaborationBackendMode, baseUrl: String): CollaborationBackendMode {
        return when {
            mode == CollaborationBackendMode.Disabled -> CollaborationBackendMode.Disabled
            mode == CollaborationBackendMode.LocalEmulator && baseUrl.isNotBlank() -> CollaborationBackendMode.RemoteHttp
            else -> mode
        }
    }

    private fun disabledError(): CollaborationRemoteException {
        return CollaborationRemoteException(
            RemoteErrorMetadata(
                code = RemoteErrorCode.Forbidden,
                message = "Collaboration sync is disabled by policy.",
                retryable = false,
            ),
        )
    }
}

class HttpCollaborationRemoteDataSource(
    private val json: Json,
    private val baseUrl: String,
    private val apiPath: String,
    private val accessTokenProvider: suspend () -> String?,
    private val connectTimeoutMillis: Int,
    private val readTimeoutMillis: Int,
    private val requestTimeoutMillis: Long,
    private val retryCount: Int,
) : CollaborationRemoteDataSource {

    override suspend fun healthCheck(): RemoteServiceHealth {
        val response = execute("GET", buildUrl("health"), null, null)
        return parseJsonResponse(response, RemoteServiceHealth.serializer())
    }

    override suspend fun pull(request: CollaborationPullRequest): CollaborationRemoteSnapshot {
        val query = linkedMapOf(
            "documentKey" to request.documentKey,
            "pageSize" to request.pageSize.toString(),
            "shareLinksPageToken" to request.shareLinksPageToken,
            "reviewThreadsPageToken" to request.reviewThreadsPageToken,
            "activityPageToken" to request.activityPageToken,
            "snapshotsPageToken" to request.snapshotsPageToken,
        ).mapNotNull { (key, value) -> value?.takeIf { it.isNotBlank() }?.let { key to it } }.toMap()
        val response = execute("GET", buildUrl("documents/${urlEncode(request.documentKey)}/artifacts", query), null, null)
        return parseJsonResponse(response, CollaborationRemoteSnapshot.serializer())
    }

    override suspend fun push(request: RemoteMutationRequest): RemoteMutationResult {
        val body = json.encodeToString(RemoteMutationRequest.serializer(), request)
        val response = execute(
            method = "POST",
            url = buildUrl("mutations"),
            body = body,
            extraHeaders = mapOf("Idempotency-Key" to request.idempotencyKey),
        )
        if (response.code == HttpURLConnection.HTTP_CONFLICT) {
            throw CollaborationRemoteException(parseError(response))
        }
        return parseJsonResponse(response, RemoteMutationResult.serializer())
    }

    private suspend fun execute(method: String, url: String, body: String?, extraHeaders: Map<String, String>?): HttpResponse {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt <= retryCount) {
            try {
                return withTimeout(requestTimeoutMillis) {
                    withContext(Dispatchers.IO) {
                        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                            requestMethod = method
                            connectTimeout = connectTimeoutMillis
                            readTimeout = readTimeoutMillis
                            doInput = true
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Content-Type", "application/json")
                            accessTokenProvider()?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Authorization", "Bearer $it") }
                            extraHeaders?.forEach { (key, value) -> setRequestProperty(key, value) }
                            if (body != null) {
                                doOutput = true
                                outputStream.use { output ->
                                    output.write(body.toByteArray(StandardCharsets.UTF_8))
                                }
                            }
                        }
                        val code = connection.responseCode
                        val responseBody = runCatching {
                            (if (code >= 400) connection.errorStream else connection.inputStream)?.bufferedReader()?.use { it.readText() }
                        }.getOrNull()
                        HttpResponse(code = code, body = responseBody)
                    }
                }.also { response ->
                    if (!response.isRetryable()) return response
                    lastError = CollaborationRemoteException(parseError(response))
                }
            } catch (throwable: Throwable) {
                lastError = throwable
                if (!throwable.isRetryableNetwork() || attempt >= retryCount) break
            }
            attempt += 1
            delay((1000L shl attempt.coerceAtMost(4)).coerceAtMost(8_000L))
        }
        throw when (val error = lastError) {
            is CollaborationRemoteException -> error
            is SocketTimeoutException -> CollaborationRemoteException(
                RemoteErrorMetadata(RemoteErrorCode.Timeout, "Collaboration request timed out.", retryable = true),
            )
            else -> CollaborationRemoteException(
                RemoteErrorMetadata(RemoteErrorCode.Offline, error?.message ?: "Unable to reach collaboration service.", retryable = true),
            )
        }
    }

    private fun buildUrl(path: String, query: Map<String, String> = emptyMap()): String {
        val trimmedBase = baseUrl.trimEnd('/')
        val trimmedPath = apiPath.trim('/').takeIf { it.isNotBlank() }?.let { "/$it" } ?: ""
        val suffix = "/" + path.trimStart('/')
        val queryString = if (query.isEmpty()) "" else query.entries.joinToString(prefix = "?", separator = "&") { (key, value) -> "${urlEncode(key)}=${urlEncode(value)}" }
        return "$trimmedBase$trimmedPath$suffix$queryString"
    }

    private fun parseError(response: HttpResponse): RemoteErrorMetadata {
        return runCatching {
            if (!response.body.isNullOrBlank()) json.decodeFromString(RemoteErrorMetadata.serializer(), response.body)
            else defaultError(response.code, null)
        }.getOrElse {
            defaultError(response.code, response.body)
        }
    }

    private fun <T> parseJsonResponse(response: HttpResponse, serializer: kotlinx.serialization.KSerializer<T>): T {
        if (response.code !in 200..299) {
            throw CollaborationRemoteException(parseError(response))
        }
        val body = response.body ?: throw CollaborationRemoteException(defaultError(response.code, "Empty response body"))
        return json.decodeFromString(serializer, body)
    }

    private fun defaultError(code: Int, body: String?): RemoteErrorMetadata {
        val mappedCode = when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> RemoteErrorCode.Unauthorized
            HttpURLConnection.HTTP_FORBIDDEN -> RemoteErrorCode.Forbidden
            HttpURLConnection.HTTP_CONFLICT -> RemoteErrorCode.Conflict
            408 -> RemoteErrorCode.Timeout
            429 -> RemoteErrorCode.RateLimited
            in 400..499 -> RemoteErrorCode.InvalidRequest
            in 500..599 -> RemoteErrorCode.ServerError
            else -> RemoteErrorCode.Unknown
        }
        return RemoteErrorMetadata(
            code = mappedCode,
            message = body?.takeIf { it.isNotBlank() } ?: "Collaboration service returned HTTP $code",
            retryable = code == 408 || code == 429 || code >= 500,
            httpStatus = code,
        )
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class HttpResponse(
        val code: Int,
        val body: String?,
    ) {
        fun isRetryable(): Boolean = code == 408 || code == 429 || code >= 500
    }
}

private fun Throwable.isRetryableNetwork(): Boolean {
    return this is SocketTimeoutException || this is java.io.IOException
}

