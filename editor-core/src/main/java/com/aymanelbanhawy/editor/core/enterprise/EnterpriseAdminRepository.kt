package com.aymanelbanhawy.editor.core.enterprise

import android.content.Context
import com.aymanelbanhawy.editor.core.data.EnterpriseSettingsDao
import com.aymanelbanhawy.editor.core.data.EnterpriseSettingsEntity
import com.aymanelbanhawy.editor.core.data.TelemetryEventDao
import com.aymanelbanhawy.editor.core.data.TelemetryEventEntity
import java.io.File
import java.util.UUID
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

interface EnterpriseAdminRepository {
    suspend fun loadState(): EnterpriseAdminStateModel
    suspend fun saveState(state: EnterpriseAdminStateModel)
    suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel
    suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel
    suspend fun signOut(): EnterpriseAdminStateModel
    suspend fun refreshRemoteState(force: Boolean = false): EnterpriseAdminStateModel
    suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel
    suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel
    suspend fun queueTelemetry(event: TelemetryEventModel)
    suspend fun pendingTelemetry(): List<TelemetryEventModel>
    suspend fun flushTelemetry(): Int
    suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File
}

class DefaultEnterpriseAdminRepository(
    private val context: Context,
    private val settingsDao: EnterpriseSettingsDao,
    private val telemetryDao: TelemetryEventDao,
    private val credentialStore: EnterpriseCredentialStore,
    private val remoteRegistry: EnterpriseRemoteRegistry,
    private val json: Json,
) : EnterpriseAdminRepository {

    override suspend fun loadState(): EnterpriseAdminStateModel {
        return settingsDao.get()?.let { entity ->
            runCatching {
                json.decodeFromString(EnterpriseAdminStateModel.serializer(), entity.payloadJson)
            }.getOrDefault(EnterpriseAdminStateModel())
        } ?: EnterpriseAdminStateModel()
    }

    override suspend fun saveState(state: EnterpriseAdminStateModel) {
        settingsDao.upsert(
            EnterpriseSettingsEntity(
                singletonId = SINGLETON_ID,
                payloadJson = json.encodeToString(EnterpriseAdminStateModel.serializer(), state),
                policyVersion = state.policySync.policyVersion,
                policyEtag = state.policySync.policyEtag,
                lastSyncAtEpochMillis = state.policySync.lastPolicySyncAtEpochMillis,
                schemaVersion = CURRENT_SCHEMA_VERSION,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel {
        val previous = loadState()
        clearSessionSecrets(previous.authSession)
        val updated = previous.copy(
            authSession = AuthSessionModel(
                mode = AuthenticationMode.Personal,
                provider = AuthenticationProvider.Local,
                status = AuthSessionStatus.Active,
                isSignedIn = true,
                displayName = displayName.ifBlank { "Personal User" },
                lastRefreshedAtEpochMillis = System.currentTimeMillis(),
            ),
            tenantConfiguration = TenantConfigurationModel(),
            plan = LicensePlan.Free,
            adminPolicy = previous.adminPolicy.copy(
                allowExternalSharing = true,
                allowCollaborationSync = true,
                aiEnabled = false,
            ),
            policySync = previous.policySync.copy(
                policyVersion = "personal-local",
                policyEtag = null,
                lastBootstrapAtEpochMillis = System.currentTimeMillis(),
                lastPolicySyncAtEpochMillis = System.currentTimeMillis(),
                lastEntitlementRefreshAtEpochMillis = System.currentTimeMillis(),
                lastRemoteError = null,
            ),
            remoteFeatureOverrides = emptySet(),
        )
        saveState(updated)
        return updated
    }

    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel {
        val remote = remoteRegistry.select(resolveTenantDefaults(tenant))
        return try {
            val bootstrap = remote.bootstrap(
                TenantBootstrapRequest(
                    email = email,
                    tenantHint = tenant.domain.ifBlank { tenant.tenantId },
                    currentPolicyVersion = loadState().policySync.policyVersion,
                ),
            )
            val accessAlias = credentialStore.newAlias("enterprise-access")
            val refreshAlias = credentialStore.newAlias("enterprise-refresh")
            val collaborationAlias = credentialStore.newAlias("enterprise-collaboration")
            credentialStore.store(accessAlias, accessTokenFor(bootstrap.authSession, email))
            credentialStore.store(refreshAlias, refreshTokenFor(bootstrap.authSession, email))
            credentialStore.store(collaborationAlias, accessTokenFor(bootstrap.authSession, email))
            val now = System.currentTimeMillis()
            val updated = EnterpriseAdminStateModel(
                authSession = bootstrap.authSession.copy(
                    collaborationCredentialAlias = collaborationAlias,
                    session = bootstrap.authSession.session.copy(
                        accessTokenAlias = accessAlias,
                        refreshTokenAlias = refreshAlias,
                    ),
                    sessionExpiresAtEpochMillis = bootstrap.authSession.session.accessTokenExpiresAtEpochMillis,
                    lastRefreshedAtEpochMillis = now,
                    lastFailure = null,
                ),
                tenantConfiguration = bootstrap.tenantConfiguration,
                plan = bootstrap.plan,
                privacySettings = loadState().privacySettings,
                adminPolicy = bootstrap.adminPolicy,
                policySync = bootstrap.policySync.copy(
                    lastBootstrapAtEpochMillis = now,
                    lastPolicySyncAtEpochMillis = now,
                    lastEntitlementRefreshAtEpochMillis = now,
                    lastRemoteError = null,
                ),
                remoteFeatureOverrides = bootstrap.remoteFeatureOverrides,
            )
            saveState(updated)
            updated
        } catch (error: EnterpriseRemoteException) {
            val fallback = loadState().copy(
                policySync = loadState().policySync.copy(lastRemoteError = error.message),
            )
            saveState(fallback)
            throw error
        }
    }

    override suspend fun signOut(): EnterpriseAdminStateModel {
        val current = loadState()
        if (current.authSession.isEnterpriseRemote()) {
            runCatching {
                remoteRegistry.select(current.tenantConfiguration).revokeSession(current, credentialStore.load(current.authSession.session.accessTokenAlias))
            }
        }
        clearSessionSecrets(current.authSession)
        val updated = current.copy(
            authSession = AuthSessionModel(status = AuthSessionStatus.Revoked),
            remoteFeatureOverrides = emptySet(),
            policySync = current.policySync.copy(lastRemoteError = null),
        )
        saveState(updated)
        return updated
    }

    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel {
        var state = refreshSessionIfNeeded()
        if (!state.authSession.isEnterpriseRemote()) return state
        val remote = remoteRegistry.select(state.tenantConfiguration)
        return try {
            val policyResponse = remote.syncPolicy(
                state,
                PolicySyncRequest(state.tenantConfiguration.tenantId, if (force) null else state.policySync.policyEtag),
            )
            val updated = if (policyResponse.modified) {
                state.copy(
                    plan = policyResponse.plan,
                    adminPolicy = policyResponse.adminPolicy,
                    remoteFeatureOverrides = policyResponse.remoteFeatureOverrides,
                    policySync = state.policySync.copy(
                        policyVersion = policyResponse.policyVersion,
                        policyEtag = policyResponse.policyEtag,
                        lastPolicySyncAtEpochMillis = policyResponse.serverTimestampEpochMillis,
                        lastEntitlementRefreshAtEpochMillis = policyResponse.serverTimestampEpochMillis,
                        lastRemoteError = null,
                    ),
                )
            } else {
                state.copy(
                    policySync = state.policySync.copy(
                        lastPolicySyncAtEpochMillis = policyResponse.serverTimestampEpochMillis,
                        lastRemoteError = null,
                    ),
                )
            }
            saveState(updated)
            updated
        } catch (error: EnterpriseRemoteException) {
            state = state.copy(policySync = state.policySync.copy(lastRemoteError = error.message))
            saveState(state)
            state
        }
    }

    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel {
        val state = loadState()
        if (!state.authSession.isEnterpriseRemote()) return state
        val expiry = state.authSession.session.accessTokenExpiresAtEpochMillis ?: state.authSession.sessionExpiresAtEpochMillis ?: return state
        val now = System.currentTimeMillis()
        if (expiry - now > REFRESH_WINDOW_MILLIS && state.authSession.status != AuthSessionStatus.RefreshFailed) {
            return state.copy(
                authSession = state.authSession.copy(
                    status = if (expiry - now <= EXPIRING_SOON_WINDOW_MILLIS) AuthSessionStatus.ExpiringSoon else AuthSessionStatus.Active,
                ),
            ).also { saveState(it) }
        }
        return try {
            val remote = remoteRegistry.select(state.tenantConfiguration)
            val refresh = remote.refreshSession(state, credentialStore.load(state.authSession.session.refreshTokenAlias))
            val updated = state.copy(
                authSession = refresh.authSession.copy(
                    collaborationCredentialAlias = state.authSession.collaborationCredentialAlias,
                    session = refresh.authSession.session.copy(
                        accessTokenAlias = state.authSession.session.accessTokenAlias,
                        refreshTokenAlias = state.authSession.session.refreshTokenAlias,
                    ),
                    lastRefreshedAtEpochMillis = now,
                    lastFailure = null,
                ),
                plan = refresh.plan,
                remoteFeatureOverrides = refresh.remoteFeatureOverrides,
                policySync = state.policySync.copy(
                    policyVersion = refresh.policyVersion ?: state.policySync.policyVersion,
                    policyEtag = refresh.policyEtag ?: state.policySync.policyEtag,
                    lastEntitlementRefreshAtEpochMillis = now,
                    lastRemoteError = null,
                ),
            )
            state.authSession.session.accessTokenAlias?.let { alias -> credentialStore.store(alias, accessTokenFor(updated.authSession, updated.authSession.email)) }
            state.authSession.collaborationCredentialAlias?.let { alias -> credentialStore.store(alias, accessTokenFor(updated.authSession, updated.authSession.email)) }
            saveState(updated)
            updated
        } catch (error: EnterpriseRemoteException) {
            val updated = state.copy(
                authSession = state.authSession.copy(
                    status = if ((state.authSession.session.accessTokenExpiresAtEpochMillis ?: 0L) <= now) AuthSessionStatus.Expired else AuthSessionStatus.RefreshFailed,
                    lastFailure = error.message,
                ),
                policySync = state.policySync.copy(lastRemoteError = error.message),
            )
            saveState(updated)
            updated
        }
    }

    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel {
        return EntitlementEngine.resolve(state.plan, state.adminPolicy, state.remoteFeatureOverrides)
    }

    override suspend fun queueTelemetry(event: TelemetryEventModel) {
        val state = loadState()
        if (!state.privacySettings.telemetryEnabled) return
        val properties = if (state.privacySettings.includeDocumentNames) {
            event.properties
        } else {
            event.properties.filterKeys { !it.contains("document", ignoreCase = true) }
        }
        telemetryDao.upsert(
            TelemetryEventEntity(
                id = event.id,
                category = event.category.name,
                name = event.name,
                createdAtEpochMillis = event.createdAtEpochMillis,
                propertiesJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), properties),
                uploadState = event.uploadState.name,
                attemptCount = event.attemptCount,
                lastAttemptAtEpochMillis = event.lastAttemptAtEpochMillis,
                uploadedAtEpochMillis = event.uploadedAtEpochMillis,
                failureMessage = event.failureMessage,
            ),
        )
    }

    override suspend fun pendingTelemetry(): List<TelemetryEventModel> {
        return telemetryDao.all().map { entity ->
            TelemetryEventModel(
                id = entity.id,
                category = TelemetryCategory.valueOf(entity.category),
                name = entity.name,
                createdAtEpochMillis = entity.createdAtEpochMillis,
                properties = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), entity.propertiesJson),
                uploadState = TelemetryUploadState.valueOf(entity.uploadState),
                attemptCount = entity.attemptCount,
                lastAttemptAtEpochMillis = entity.lastAttemptAtEpochMillis,
                uploadedAtEpochMillis = entity.uploadedAtEpochMillis,
                failureMessage = entity.failureMessage,
            )
        }
    }

    override suspend fun flushTelemetry(): Int {
        val state = refreshSessionIfNeeded()
        if (!state.privacySettings.telemetryEnabled || !state.adminPolicy.telemetryUploadEnabled) return 0
        if (state.privacySettings.localOnlyMode) return 0
        if (!state.authSession.isEnterpriseRemote()) return 0
        val pending = telemetryDao.pending(TELEMETRY_BATCH_LIMIT)
        if (pending.isEmpty()) return 0
        val now = System.currentTimeMillis()
        pending.forEach { telemetryDao.markState(it.id, TelemetryUploadState.Uploading.name, it.attemptCount + 1, now, it.uploadedAtEpochMillis, null) }
        return try {
            val events = pending.map { entity ->
                TelemetryEventModel(
                    id = entity.id,
                    category = TelemetryCategory.valueOf(entity.category),
                    name = entity.name,
                    createdAtEpochMillis = entity.createdAtEpochMillis,
                    properties = json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), entity.propertiesJson),
                    uploadState = TelemetryUploadState.Uploading,
                    attemptCount = entity.attemptCount + 1,
                    lastAttemptAtEpochMillis = now,
                )
            }
            val response = remoteRegistry.select(state.tenantConfiguration).uploadTelemetry(
                state,
                TelemetryBatchUploadRequest(state.tenantConfiguration.tenantId, events),
                credentialStore.load(state.authSession.session.accessTokenAlias),
            )
            response.acceptedIds.forEach { id ->
                val source = pending.first { it.id == id }
                telemetryDao.markState(id, TelemetryUploadState.Uploaded.name, source.attemptCount + 1, now, response.serverTimestampEpochMillis, null)
            }
            response.rejectedIds.forEach { id ->
                val source = pending.first { it.id == id }
                telemetryDao.markState(id, TelemetryUploadState.Failed.name, source.attemptCount + 1, now, null, "Rejected by server")
            }
            val retentionCutoff = response.serverTimestampEpochMillis - state.adminPolicy.retentionDays * DAY_MILLIS
            telemetryDao.deleteUploadedBefore(retentionCutoff)
            saveState(state.copy(policySync = state.policySync.copy(lastSuccessfulTelemetryUploadAtEpochMillis = response.serverTimestampEpochMillis)))
            response.acceptedIds.size
        } catch (error: EnterpriseRemoteException) {
            pending.forEach { telemetryDao.markState(it.id, TelemetryUploadState.Failed.name, it.attemptCount + 1, now, null, error.message) }
            saveState(state.copy(policySync = state.policySync.copy(lastRemoteError = error.message)))
            0
        }
    }

    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File {
        destination.parentFile?.mkdirs()
        val state = loadState()
        val payload = mapOf(
            "generatedAt" to System.currentTimeMillis().toString(),
            "authMode" to state.authSession.mode.name,
            "authStatus" to state.authSession.status.name,
            "plan" to state.plan.name,
            "tenantId" to state.tenantConfiguration.tenantId,
            "tenantName" to state.tenantConfiguration.tenantName,
            "policyVersion" to state.policySync.policyVersion,
            "policyEtag" to (state.policySync.policyEtag ?: ""),
            "lastPolicySyncAt" to (state.policySync.lastPolicySyncAtEpochMillis?.toString() ?: ""),
            "lastTelemetryUploadAt" to (state.policySync.lastSuccessfulTelemetryUploadAtEpochMillis?.toString() ?: ""),
            "remoteError" to (state.policySync.lastRemoteError ?: ""),
            "telemetryEnabled" to state.privacySettings.telemetryEnabled.toString(),
            "localOnlyMode" to state.privacySettings.localOnlyMode.toString(),
            "queuedTelemetryCount" to telemetryDao.pending(500).size.toString(),
            "summary" to appSummary.entries.joinToString(";") { "${it.key}=${it.value}" },
        )
        destination.writeText(json.encodeToString(MapSerializer(String.serializer(), String.serializer()), payload))
        saveState(state.copy(policySync = state.policySync.copy(lastDiagnosticsBundleAtEpochMillis = System.currentTimeMillis())))
        return destination
    }

    private suspend fun clearSessionSecrets(session: AuthSessionModel) {
        credentialStore.clear(session.session.accessTokenAlias)
        credentialStore.clear(session.session.refreshTokenAlias)
        credentialStore.clear(session.collaborationCredentialAlias)
    }

    private fun resolveTenantDefaults(tenant: TenantConfigurationModel): TenantConfigurationModel {
        val domain = tenant.domain.ifBlank { tenant.tenantId.ifBlank { "enterprise.local" } }
        val baseUrl = tenant.apiBaseUrl.ifBlank {
            if (tenant.bootstrapMode == EnterpriseBootstrapMode.Remote) "https://$domain" else ""
        }
        val issuerBase = tenant.issuerBaseUrl.ifBlank {
            if (tenant.bootstrapMode == EnterpriseBootstrapMode.Remote) "https://$domain" else ""
        }
        return tenant.copy(
            domain = domain,
            apiBaseUrl = baseUrl,
            issuerBaseUrl = issuerBase,
            oidc = tenant.oidc.copy(
                issuerUrl = tenant.oidc.issuerUrl.ifBlank { if (issuerBase.isBlank()) "" else "$issuerBase/.well-known/openid-configuration" },
                clientId = tenant.oidc.clientId.ifBlank { "enterprise-pdf-mobile" },
                redirectUri = tenant.oidc.redirectUri.ifBlank { "enterprisepdf://auth/callback" },
            ),
        )
    }

    private fun accessTokenFor(session: AuthSessionModel, email: String): String {
        val expiry = session.session.accessTokenExpiresAtEpochMillis ?: (System.currentTimeMillis() + 45 * 60_000)
        return "ent_access_${session.subjectId.ifBlank { email }}_$expiry"
    }

    private fun refreshTokenFor(session: AuthSessionModel, email: String): String {
        val expiry = session.session.refreshTokenExpiresAtEpochMillis ?: (System.currentTimeMillis() + 7 * DAY_MILLIS)
        return "ent_refresh_${session.subjectId.ifBlank { email }}_$expiry"
    }

    companion object {
        private const val SINGLETON_ID = "enterprise-admin"
        private const val CURRENT_SCHEMA_VERSION = 2
        private const val REFRESH_WINDOW_MILLIS = 5 * 60_000L
        private const val EXPIRING_SOON_WINDOW_MILLIS = 15 * 60_000L
        private const val TELEMETRY_BATCH_LIMIT = 50
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

fun newTelemetryEvent(category: TelemetryCategory, name: String, properties: Map<String, String> = emptyMap()): TelemetryEventModel {
    return TelemetryEventModel(
        id = UUID.randomUUID().toString(),
        category = category,
        name = name,
        createdAtEpochMillis = System.currentTimeMillis(),
        properties = properties,
    )
}
