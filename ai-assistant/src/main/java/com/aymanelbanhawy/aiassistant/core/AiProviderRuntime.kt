package com.aymanelbanhawy.aiassistant.core

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.TelemetryCategory
import com.aymanelbanhawy.editor.core.enterprise.newTelemetryEvent
import com.aymanelbanhawy.editor.core.security.AuditEventType
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

interface SecureCredentialStore {
    suspend fun loadSecret(profileId: String): String?
    suspend fun saveSecret(profileId: String, secret: String)
    suspend fun clearSecret(profileId: String)
}

class AndroidKeystoreCredentialStore(context: Context) : SecureCredentialStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("ai-provider-secrets", Context.MODE_PRIVATE)
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    override suspend fun loadSecret(profileId: String): String? = withContext(Dispatchers.IO) {
        val payload = prefs.getString(profileId, null) ?: return@withContext null
        val parts = payload.split(':')
        if (parts.size != 2) return@withContext null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
    }

    override suspend fun saveSecret(profileId: String, secret: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val payload = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        prefs.edit().putString(profileId, payload).apply()
    }

    override suspend fun clearSecret(profileId: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove(profileId).apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "enterprise-pdf-ai-provider-secret"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

interface LocalAiAppDiscovery {
    suspend fun discoverInstalledApps(): List<LocalAiAppInfo>
}

class AndroidLocalAiAppDiscovery(
    private val context: Context,
) : LocalAiAppDiscovery {
    override suspend fun discoverInstalledApps(): List<LocalAiAppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        KNOWN_PACKAGES.mapNotNull { packageName ->
            runCatching {
                val info = resolveApplicationInfo(pm, packageName)
                LocalAiAppInfo(packageName = packageName, appName = pm.getApplicationLabel(info).toString())
            }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveApplicationInfo(pm: PackageManager, packageName: String): ApplicationInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            pm.getApplicationInfo(packageName, 0)
        }
    }

    private companion object {
        val KNOWN_PACKAGES = listOf("io.ollama.app", "org.ollama.android", "com.termux", "com.llamacpp.app")
    }
}

sealed interface ProviderStreamEvent {
    data class Delta(val text: String) : ProviderStreamEvent
    data class Completed(val fullText: String) : ProviderStreamEvent
}

data class ProviderInvocation(
    val events: Flow<ProviderStreamEvent>,
    val cancel: () -> Unit,
)

data class ProviderSelectionDecision(
    val profile: AiProviderProfile?,
    val blockedReason: String? = null,
)

class ProviderSelectionEngine {
    fun select(runtime: AiProviderRuntimeState, settings: AssistantSettings, enterpriseState: EnterpriseAdminStateModel): ProviderSelectionDecision {
        val selected = runtime.profiles.firstOrNull { it.id == runtime.selectedProviderId } ?: runtime.profiles.firstOrNull()
            ?: return ProviderSelectionDecision(null, "No AI providers are configured.")
        val isCloud = selected.kind == AiProviderKind.OpenAi || selected.kind == AiProviderKind.OpenAiCompatible || selected.kind == AiProviderKind.OllamaRemote
        if (settings.privacyMode == AssistantPrivacyMode.LocalOnly && isCloud) {
            val local = runtime.profiles.firstOrNull { it.kind == AiProviderKind.OllamaLocal }
            return if (local != null) ProviderSelectionDecision(local) else ProviderSelectionDecision(null, "Local-only mode requires a local provider.")
        }
        if (enterpriseState.authSession.mode == AuthenticationMode.Enterprise && isCloud && !enterpriseState.adminPolicy.allowCloudAiProviders) {
            return ProviderSelectionDecision(null, "Tenant policy blocks cloud AI providers.")
        }
        if (enterpriseState.adminPolicy.approvedAiProviderIds.isNotEmpty() && selected.id !in enterpriseState.adminPolicy.approvedAiProviderIds) {
            return ProviderSelectionDecision(null, "Tenant policy allows only approved AI providers.")
        }
        return ProviderSelectionDecision(selected)
    }
}

data class ProviderCatalogResult(
    val models: List<AiProviderModelInfo>,
    val capabilities: AiProviderCapabilities,
    val diagnostics: String,
)

data class ProviderHealthResult(
    val healthy: Boolean,
    val message: String,
)

data class ProviderRequest(
    val provider: AiProviderProfile,
    val apiKey: String?,
    val prompt: String,
    val timeoutSeconds: Int,
    val retryCount: Int,
)

interface AiProviderAdapter {
    val kind: AiProviderKind
    suspend fun healthCheck(profile: AiProviderProfile, apiKey: String?): ProviderHealthResult
    suspend fun listModels(profile: AiProviderProfile, apiKey: String?): ProviderCatalogResult
    fun streamCompletion(request: ProviderRequest): ProviderInvocation
}

class AiProviderRegistry(adapters: List<AiProviderAdapter>) {
    private val byKind = adapters.associateBy { it.kind }
    fun adapter(kind: AiProviderKind): AiProviderAdapter = requireNotNull(byKind[kind]) { "No adapter for $kind" }
}

class ProviderRuntimeFactory(
    private val client: OkHttpClient,
    private val json: Json,
) {
    fun createRegistry(): AiProviderRegistry {
        return AiProviderRegistry(
            listOf(
                OllamaProviderAdapter(AiProviderKind.OllamaLocal, client, json),
                OllamaProviderAdapter(AiProviderKind.OllamaRemote, client, json),
                OpenAiCompatibleProviderAdapter(AiProviderKind.OpenAi, client, json),
                OpenAiCompatibleProviderAdapter(AiProviderKind.OpenAiCompatible, client, json),
            ),
        )
    }
}

private class OllamaProviderAdapter(
    override val kind: AiProviderKind,
    private val client: OkHttpClient,
    private val json: Json,
) : AiProviderAdapter {
    override suspend fun healthCheck(profile: AiProviderProfile, apiKey: String?): ProviderHealthResult {
        val request = Request.Builder().url(normalizeBaseUrl(profile.endpointUrl) + "/api/tags").get().build()
        return executeWithMapping(profile, 1) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException(profile.id)
                ProviderHealthResult(true, "Connected to ${profile.displayName}")
            }
        }
    }

    override suspend fun listModels(profile: AiProviderProfile, apiKey: String?): ProviderCatalogResult {
        val request = Request.Builder().url(normalizeBaseUrl(profile.endpointUrl) + "/api/tags").get().build()
        return executeWithMapping(profile, 1) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException(profile.id)
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val models = root["models"]?.jsonArray.orEmpty().map { element ->
                    val obj = element.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    AiProviderModelInfo(
                        id = name,
                        displayName = name,
                        owner = "Ollama",
                        capabilitySummary = obj["details"]?.jsonObject?.get("family")?.jsonPrimitive?.contentOrNull.orEmpty(),
                        capabilities = AiProviderCapabilities(supportsStreaming = true, maxContextHint = modelContextHint(name)),
                    )
                }
                ProviderCatalogResult(
                    models = models,
                    capabilities = AiProviderCapabilities(supportsStreaming = true, maxContextHint = models.firstOrNull()?.capabilities?.maxContextHint),
                    diagnostics = if (models.isEmpty()) "No models reported by Ollama." else "${models.size} model(s) available.",
                )
            }
        }
    }

    override fun streamCompletion(request: ProviderRequest): ProviderInvocation {
        val activeCall = AtomicReference<Call?>(null)
        val stream = flow {
            var aggregated = ""
            executeWithMapping(request.provider, request.retryCount + 1) {
                withTimeout(request.timeoutSeconds * 1_000L) {
                    val payload = buildJsonObject {
                        put("model", JsonPrimitive(request.provider.modelId))
                        put("stream", JsonPrimitive(true))
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", JsonPrimitive(request.prompt))
                            })
                        })
                    }
                    val httpRequest = Request.Builder()
                        .url(normalizeBaseUrl(request.provider.endpointUrl) + "/api/chat")
                        .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                        .build()
                    val call = client.newCall(httpRequest)
                    activeCall.set(call)
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw response.toProviderException(request.provider.id)
                        val reader = response.body?.charStream()?.buffered() ?: throw ProviderException(AiProviderErrorCode.Unknown, "Missing response body", false, request.provider.id)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) continue
                            val obj = json.parseToJsonElement(line).jsonObject
                            val delta = obj["message"]?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull ?: obj["response"]?.jsonPrimitive?.contentOrNull ?: ""
                            if (delta.isNotBlank()) {
                                aggregated += delta
                                emit(ProviderStreamEvent.Delta(delta))
                            }
                        }
                    }
                }
            }
            emit(ProviderStreamEvent.Completed(aggregated))
        }.flowOn(Dispatchers.IO)
        return ProviderInvocation(stream, cancel = { activeCall.get()?.cancel() })
    }
}

private class OpenAiCompatibleProviderAdapter(
    override val kind: AiProviderKind,
    private val client: OkHttpClient,
    private val json: Json,
) : AiProviderAdapter {
    override suspend fun healthCheck(profile: AiProviderProfile, apiKey: String?): ProviderHealthResult {
        requireCredential(profile, apiKey)
        val request = Request.Builder().url(normalizeBaseUrl(profile.endpointUrl) + "/models").header("Authorization", "Bearer $apiKey").get().build()
        return executeWithMapping(profile, 1) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException(profile.id)
                ProviderHealthResult(true, "Connected to ${profile.displayName}")
            }
        }
    }

    override suspend fun listModels(profile: AiProviderProfile, apiKey: String?): ProviderCatalogResult {
        requireCredential(profile, apiKey)
        val request = Request.Builder().url(normalizeBaseUrl(profile.endpointUrl) + "/models").header("Authorization", "Bearer $apiKey").get().build()
        return executeWithMapping(profile, 1) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toProviderException(profile.id)
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonObject
                val models = root["data"]?.jsonArray.orEmpty().map { element ->
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    AiProviderModelInfo(
                        id = id,
                        displayName = id,
                        owner = obj["owned_by"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        capabilitySummary = "OpenAI-compatible chat model",
                        capabilities = AiProviderCapabilities(
                            supportsStreaming = true,
                            maxContextHint = modelContextHint(id),
                            supportsVision = id.contains("vision", ignoreCase = true),
                            supportsJsonMode = true,
                        ),
                    )
                }
                ProviderCatalogResult(
                    models = models,
                    capabilities = AiProviderCapabilities(supportsStreaming = true, maxContextHint = models.firstOrNull()?.capabilities?.maxContextHint, supportsJsonMode = true),
                    diagnostics = if (models.isEmpty()) "No models returned by the provider." else "${models.size} model(s) available.",
                )
            }
        }
    }

    override fun streamCompletion(request: ProviderRequest): ProviderInvocation {
        val activeCall = AtomicReference<Call?>(null)
        val stream = flow {
            requireCredential(request.provider, request.apiKey)
            var aggregated = ""
            executeWithMapping(request.provider, request.retryCount + 1) {
                withTimeout(request.timeoutSeconds * 1_000L) {
                    val payload = buildJsonObject {
                        put("model", JsonPrimitive(request.provider.modelId))
                        put("stream", JsonPrimitive(true))
                        put("temperature", JsonPrimitive(0.2))
                        put("messages", buildJsonArray {
                            add(buildJsonObject {
                                put("role", JsonPrimitive("user"))
                                put("content", JsonPrimitive(request.prompt))
                            })
                        })
                    }
                    val httpRequest = Request.Builder()
                        .url(normalizeBaseUrl(request.provider.endpointUrl) + "/chat/completions")
                        .header("Authorization", "Bearer ${request.apiKey}")
                        .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                        .build()
                    val call = client.newCall(httpRequest)
                    activeCall.set(call)
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw response.toProviderException(request.provider.id)
                        val reader = response.body?.charStream()?.buffered() ?: throw ProviderException(AiProviderErrorCode.Unknown, "Missing response body", false, request.provider.id)
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (!line.startsWith("data:")) continue
                            val payloadLine = line.removePrefix("data:").trim()
                            if (payloadLine == "[DONE]") continue
                            val obj = json.parseToJsonElement(payloadLine).jsonObject
                            val delta = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty()
                            if (delta.isNotBlank()) {
                                aggregated += delta
                                emit(ProviderStreamEvent.Delta(delta))
                            }
                        }
                    }
                }
            }
            emit(ProviderStreamEvent.Completed(aggregated))
        }.flowOn(Dispatchers.IO)
        return ProviderInvocation(stream, cancel = { activeCall.get()?.cancel() })
    }
}

class AiProviderRuntime(
    private val registry: AiProviderRegistry,
    private val selectionEngine: ProviderSelectionEngine,
    private val secureCredentialStore: SecureCredentialStore,
    private val discovery: LocalAiAppDiscovery,
    private val enterpriseAdminRepository: EnterpriseAdminRepository,
    private val securityRepository: SecurityRepository,
) {
    suspend fun discoverLocalApps(): List<LocalAiAppInfo> = discovery.discoverInstalledApps()

    suspend fun selectProvider(runtime: AiProviderRuntimeState, settings: AssistantSettings, enterpriseState: EnterpriseAdminStateModel): ProviderSelectionDecision {
        return selectionEngine.select(runtime, settings, enterpriseState)
    }

    suspend fun loadCatalog(profile: AiProviderProfile): ProviderCatalogResult {
        val apiKey = secureCredentialStore.loadSecret(profile.id)
        return registry.adapter(profile.kind).listModels(profile, apiKey)
    }

    suspend fun testConnection(profile: AiProviderProfile, settings: AssistantSettings, enterpriseState: EnterpriseAdminStateModel): ProviderHealthResult {
        enforcePolicy(profile, settings, enterpriseState)
        val apiKey = secureCredentialStore.loadSecret(profile.id)
        return registry.adapter(profile.kind).healthCheck(profile, apiKey)
    }

    fun stream(profile: AiProviderProfile, prompt: String): ProviderInvocation {
        val apiKey = runCatching { kotlinx.coroutines.runBlocking { secureCredentialStore.loadSecret(profile.id) } }.getOrNull()
        return registry.adapter(profile.kind).streamCompletion(
            ProviderRequest(
                provider = profile,
                apiKey = apiKey,
                prompt = prompt,
                timeoutSeconds = profile.requestTimeoutSeconds,
                retryCount = profile.retryCount,
            ),
        )
    }

    suspend fun saveCredential(profileId: String, secret: String?) {
        if (!secret.isNullOrBlank()) secureCredentialStore.saveSecret(profileId, secret)
    }

    suspend fun clearCredential(profileId: String) {
        secureCredentialStore.clearSecret(profileId)
    }

    suspend fun auditProviderSwitch(provider: AiProviderProfile) {
        queueTelemetry("ai_provider_switched", mapOf("provider" to provider.kind.name, "profile" to provider.displayName))
        recordAudit(AuditEventType.AiProviderChanged, "Switched AI provider to ${provider.displayName}", mapOf("provider" to provider.kind.name))
    }

    suspend fun auditModelSwitch(provider: AiProviderProfile) {
        queueTelemetry("ai_model_switched", mapOf("provider" to provider.kind.name, "model" to provider.modelId))
        recordAudit(AuditEventType.AiModelChanged, "Switched AI model to ${provider.modelId}", mapOf("provider" to provider.kind.name, "model" to provider.modelId))
    }

    suspend fun auditPromptSubmitted(provider: AiProviderProfile, task: AssistantTaskType) {
        queueTelemetry("ai_prompt_submitted", mapOf("provider" to provider.kind.name, "task" to task.name))
        recordAudit(AuditEventType.AiPromptSubmitted, "Submitted AI prompt for ${task.name}", mapOf("provider" to provider.kind.name))
    }

    suspend fun auditPromptCancelled(provider: AiProviderProfile, task: AssistantTaskType) {
        queueTelemetry("ai_prompt_cancelled", mapOf("provider" to provider.kind.name, "task" to task.name))
        recordAudit(AuditEventType.AiPromptCancelled, "Cancelled AI prompt for ${task.name}", mapOf("provider" to provider.kind.name))
    }

    suspend fun auditPromptSuccess(provider: AiProviderProfile, task: AssistantTaskType) {
        queueTelemetry("ai_prompt_succeeded", mapOf("provider" to provider.kind.name, "task" to task.name))
        recordAudit(AuditEventType.AiPromptCompleted, "Completed AI prompt for ${task.name}", mapOf("provider" to provider.kind.name))
    }

    suspend fun auditPromptFailure(provider: AiProviderProfile, task: AssistantTaskType, error: AiProviderError) {
        queueTelemetry("ai_prompt_failed", mapOf("provider" to provider.kind.name, "task" to task.name, "code" to error.code.name))
        recordAudit(AuditEventType.AiPromptFailed, "AI prompt failed for ${task.name}: ${error.message}", mapOf("provider" to provider.kind.name, "code" to error.code.name))
    }

    fun mapThrowable(throwable: Throwable, providerId: String? = null): AiProviderError {
        return when (throwable) {
            is ProviderException -> AiProviderError(throwable.code, throwable.message, throwable.retryable, providerId ?: throwable.providerId)
            is CancellationException -> AiProviderError(AiProviderErrorCode.Cancelled, "The request was cancelled.", false, providerId)
            is SocketTimeoutException -> AiProviderError(AiProviderErrorCode.Timeout, "The provider request timed out.", true, providerId)
            is UnknownHostException, is ConnectException -> AiProviderError(AiProviderErrorCode.Offline, "The provider endpoint could not be reached.", true, providerId)
            is IOException -> AiProviderError(AiProviderErrorCode.Server, throwable.message ?: "A network error occurred.", true, providerId)
            else -> AiProviderError(AiProviderErrorCode.Unknown, throwable.message ?: "An unknown provider error occurred.", false, providerId)
        }
    }

    private suspend fun enforcePolicy(profile: AiProviderProfile, settings: AssistantSettings, enterpriseState: EnterpriseAdminStateModel) {
        val isCloud = profile.kind == AiProviderKind.OpenAi || profile.kind == AiProviderKind.OpenAiCompatible || profile.kind == AiProviderKind.OllamaRemote
        if (settings.privacyMode == AssistantPrivacyMode.LocalOnly && isCloud) {
            throw ProviderException(AiProviderErrorCode.PolicyBlocked, "Local-only mode blocks cloud providers.", false, profile.id)
        }
        if (enterpriseState.authSession.mode == AuthenticationMode.Enterprise && isCloud && !enterpriseState.adminPolicy.allowCloudAiProviders) {
            throw ProviderException(AiProviderErrorCode.PolicyBlocked, "Tenant policy blocks cloud AI providers.", false, profile.id)
        }
    }

    private suspend fun queueTelemetry(name: String, properties: Map<String, String>) {
        enterpriseAdminRepository.queueTelemetry(newTelemetryEvent(TelemetryCategory.Product, name, properties))
    }

    private suspend fun recordAudit(type: AuditEventType, message: String, metadata: Map<String, String>) {
        securityRepository.recordAudit(
            AuditTrailEventModel(
                id = UUID.randomUUID().toString(),
                documentKey = "__ai__",
                type = type,
                actor = "local-user",
                message = message,
                createdAtEpochMillis = System.currentTimeMillis(),
                metadata = metadata,
            ),
        )
    }
}

private class ProviderException(
    val code: AiProviderErrorCode,
    override val message: String,
    val retryable: Boolean,
    val providerId: String? = null,
) : RuntimeException(message)

private suspend fun <T> executeWithMapping(profile: AiProviderProfile, attempts: Int, block: suspend () -> T): T {
    var lastError: Throwable? = null
    repeat(attempts.coerceAtLeast(1)) { attempt ->
        try {
            return block()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            lastError = throwable
            val mapped = when (throwable) {
                is ProviderException -> throwable
                is SocketTimeoutException -> ProviderException(AiProviderErrorCode.Timeout, "The provider request timed out.", true, profile.id)
                is UnknownHostException, is ConnectException -> ProviderException(AiProviderErrorCode.Offline, "The provider endpoint could not be reached.", true, profile.id)
                is IOException -> ProviderException(AiProviderErrorCode.Server, throwable.message ?: "Network failure", true, profile.id)
                else -> ProviderException(AiProviderErrorCode.Unknown, throwable.message ?: "Unknown provider failure", false, profile.id)
            }
            if (attempt == attempts - 1 || !mapped.retryable) throw mapped
            delay((attempt + 1) * 400L)
        }
    }
    throw lastError ?: ProviderException(AiProviderErrorCode.Unknown, "Provider failure", false, profile.id)
}

private fun okhttp3.Response.toProviderException(providerId: String): ProviderException {
    val mappedCode = when (code) {
        400 -> AiProviderErrorCode.BadRequest
        401 -> AiProviderErrorCode.Unauthorized
        403 -> AiProviderErrorCode.Forbidden
        404 -> AiProviderErrorCode.NotFound
        408 -> AiProviderErrorCode.Timeout
        429 -> AiProviderErrorCode.RateLimited
        in 500..599 -> AiProviderErrorCode.Server
        else -> AiProviderErrorCode.Unknown
    }
    return ProviderException(mappedCode, body?.string().orEmpty().ifBlank { message }, mappedCode in setOf(AiProviderErrorCode.Timeout, AiProviderErrorCode.RateLimited, AiProviderErrorCode.Server), providerId)
}

private fun requireCredential(profile: AiProviderProfile, apiKey: String?) {
    if (apiKey.isNullOrBlank()) throw ProviderException(AiProviderErrorCode.InvalidConfiguration, "An API key or token is required for ${profile.displayName}.", false, profile.id)
}

private fun normalizeBaseUrl(value: String): String = value.trim().trimEnd('/')

private fun modelContextHint(modelId: String): Int? {
    val lowered = modelId.lowercase()
    return when {
        lowered.contains("gpt-4.1") -> 1_000_000
        lowered.contains("gpt-4o") -> 128_000
        lowered.contains("gpt-4") -> 128_000
        lowered.contains("gpt-3.5") -> 16_000
        lowered.contains("llama3.2") -> 128_000
        lowered.contains("llama3") -> 8_192
        lowered.contains("mistral") -> 32_000
        lowered.contains("qwen") -> 32_000
        else -> null
    }
}

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()








