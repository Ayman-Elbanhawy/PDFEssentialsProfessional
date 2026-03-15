package com.aymanelbanhawy.editor.core.enterprise

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aymanelbanhawy.editor.core.data.PdfWorkspaceDatabase
import com.aymanelbanhawy.editor.core.security.SecureFileCipher
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EnterpriseAdminRepositoryRuntimeTest {

    private lateinit var context: Context
    private lateinit var database: PdfWorkspaceDatabase
    private lateinit var repository: DefaultEnterpriseAdminRepository
    private lateinit var remoteDataSource: TestEnterpriseRemoteDataSource

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "_type"
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, PdfWorkspaceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remoteDataSource = TestEnterpriseRemoteDataSource()
        repository = DefaultEnterpriseAdminRepository(
            context = context,
            settingsDao = database.enterpriseSettingsDao(),
            telemetryDao = database.telemetryEventDao(),
            credentialStore = EnterpriseCredentialStore(
                context = context,
                json = json,
                cipher = TestSecureFileCipher(),
            ),
            remoteRegistry = object : EnterpriseRemoteRegistry(context, json) {
                override fun select(tenant: TenantConfigurationModel): EnterpriseRemoteDataSource = remoteDataSource
            },
            json = json,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshSessionIfNeeded_updatesExpiredTokenSession() = runBlocking {
        val signedIn = repository.signInEnterprise(
            email = "alex@acme.com",
            tenant = TenantConfigurationModel(domain = "acme.com", bootstrapMode = EnterpriseBootstrapMode.Remote, apiBaseUrl = "https://acme.com"),
        )
        val expired = signedIn.copy(
            authSession = signedIn.authSession.copy(
                sessionExpiresAtEpochMillis = System.currentTimeMillis() - 1_000,
                session = signedIn.authSession.session.copy(
                    accessTokenExpiresAtEpochMillis = System.currentTimeMillis() - 1_000,
                ),
            ),
        )
        repository.saveState(expired)

        val refreshed = repository.refreshSessionIfNeeded()

        assertThat(refreshed.authSession.status).isEqualTo(AuthSessionStatus.Active)
        assertThat(refreshed.authSession.lastRefreshedAtEpochMillis).isNotNull()
        assertThat(remoteDataSource.refreshCalls).isEqualTo(1)
    }

    @Test
    fun refreshRemoteState_appliesPolicyDiffAndEntitlements() = runBlocking {
        repository.signInEnterprise(
            email = "alex@acme.com",
            tenant = TenantConfigurationModel(domain = "acme.com", bootstrapMode = EnterpriseBootstrapMode.Remote, apiBaseUrl = "https://acme.com"),
        )
        remoteDataSource.nextPolicy = remoteDataSource.nextPolicy.copy(
            aiEnabled = false,
            allowCloudAiProviders = false,
            restrictExport = true,
        )
        remoteDataSource.nextPolicyVersion = "policy-v2"
        remoteDataSource.nextPolicyEtag = "etag-v2"

        val updated = repository.refreshRemoteState(force = true)
        val entitlements = repository.resolveEntitlements(updated)

        assertThat(updated.adminPolicy.restrictExport).isTrue()
        assertThat(updated.adminPolicy.aiEnabled).isFalse()
        assertThat(updated.policySync.policyVersion).isEqualTo("policy-v2")
        assertThat(entitlements.features).contains(FeatureFlag.Ai)
    }

    @Test
    fun refreshRemoteState_keepsCachedPolicyWhenOffline() = runBlocking {
        val signedIn = repository.signInEnterprise(
            email = "alex@acme.com",
            tenant = TenantConfigurationModel(domain = "acme.com", bootstrapMode = EnterpriseBootstrapMode.Remote, apiBaseUrl = "https://acme.com"),
        )
        remoteDataSource.failPolicySync = true

        val updated = repository.refreshRemoteState(force = true)

        assertThat(updated.adminPolicy).isEqualTo(signedIn.adminPolicy)
        assertThat(updated.policySync.lastRemoteError).contains("offline")
    }

    @Test
    fun flushTelemetry_uploadsAndMarksBatch() = runBlocking {
        repository.signInEnterprise(
            email = "alex@acme.com",
            tenant = TenantConfigurationModel(domain = "acme.com", bootstrapMode = EnterpriseBootstrapMode.Remote, apiBaseUrl = "https://acme.com"),
        )
        repository.queueTelemetry(newTelemetryEvent(TelemetryCategory.Admin, "policy_opened"))

        val uploaded = repository.flushTelemetry()
        val pending = repository.pendingTelemetry()

        assertThat(uploaded).isEqualTo(1)
        assertThat(pending.single().uploadState).isEqualTo(TelemetryUploadState.Uploaded)
    }

    @Test
    fun saveState_persistsCustomizedPlanAndAiPolicyAcrossRepositoryRestart_withoutGrantingEntitlement() = runBlocking {
        val customized = EnterpriseAdminStateModel(
            authSession = AuthSessionModel(
                mode = AuthenticationMode.Personal,
                provider = AuthenticationProvider.Local,
                status = AuthSessionStatus.Active,
                isSignedIn = true,
                displayName = "Ayman",
            ),
            plan = LicensePlan.Premium,
            adminPolicy = AdminPolicyModel(
                aiEnabled = true,
                allowCloudAiProviders = true,
                allowExternalSharing = true,
            ),
            privacySettings = PrivacySettingsModel(localOnlyMode = false),
        )
        repository.saveState(customized)

        val restartedRepository = DefaultEnterpriseAdminRepository(
            context = context,
            settingsDao = database.enterpriseSettingsDao(),
            telemetryDao = database.telemetryEventDao(),
            credentialStore = EnterpriseCredentialStore(
                context = context,
                json = json,
                cipher = TestSecureFileCipher(),
            ),
            remoteRegistry = object : EnterpriseRemoteRegistry(context, json) {
                override fun select(tenant: TenantConfigurationModel): EnterpriseRemoteDataSource = remoteDataSource
            },
            json = json,
        )

        val restored = restartedRepository.loadState()
        val entitlements = restartedRepository.resolveEntitlements(restored)

        assertThat(restored.plan).isEqualTo(LicensePlan.Premium)
        assertThat(restored.adminPolicy.aiEnabled).isTrue()
        assertThat(entitlements.features).doesNotContain(FeatureFlag.Ai)
    }
    private class TestEnterpriseRemoteDataSource : EnterpriseRemoteDataSource {
        var refreshCalls: Int = 0
        var failPolicySync: Boolean = false
        var nextPolicy: AdminPolicyModel = AdminPolicyModel(aiEnabled = true, allowCloudAiProviders = false, allowExternalSharing = false)
        var nextPolicyVersion: String = "policy-v1"
        var nextPolicyEtag: String = "etag-v1"

        override suspend fun bootstrap(request: TenantBootstrapRequest): TenantBootstrapResponse {
            val now = System.currentTimeMillis()
            return TenantBootstrapResponse(
                tenantConfiguration = TenantConfigurationModel(
                    tenantId = "acme",
                    tenantName = "Acme",
                    domain = request.tenantHint,
                    apiBaseUrl = "https://acme.com",
                    issuerBaseUrl = "https://acme.com",
                    bootstrapMode = EnterpriseBootstrapMode.Remote,
                    supportsRemotePolicySync = true,
                ),
                authSession = AuthSessionModel(
                    mode = AuthenticationMode.Enterprise,
                    provider = AuthenticationProvider.Oidc,
                    status = AuthSessionStatus.Active,
                    isSignedIn = true,
                    displayName = "Alex",
                    email = request.email,
                    subjectId = request.email,
                    sessionExpiresAtEpochMillis = now + 60_000,
                    session = TokenBackedSessionModel(
                        sessionId = "session-1",
                        issuedAtEpochMillis = now,
                        accessTokenExpiresAtEpochMillis = now + 60_000,
                        refreshTokenExpiresAtEpochMillis = now + 120_000,
                    ),
                ),
                plan = LicensePlan.Enterprise,
                adminPolicy = nextPolicy,
                remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
                policySync = PolicySyncMetadataModel(
                    policyVersion = nextPolicyVersion,
                    policyEtag = nextPolicyEtag,
                    lastBootstrapAtEpochMillis = now,
                    lastPolicySyncAtEpochMillis = now,
                    lastEntitlementRefreshAtEpochMillis = now,
                ),
            )
        }

        override suspend fun refreshSession(state: EnterpriseAdminStateModel, refreshToken: String?): SessionRefreshResponse {
            refreshCalls += 1
            val now = System.currentTimeMillis()
            return SessionRefreshResponse(
                authSession = state.authSession.copy(
                    status = AuthSessionStatus.Active,
                    sessionExpiresAtEpochMillis = now + 90_000,
                    session = state.authSession.session.copy(
                        accessTokenExpiresAtEpochMillis = now + 90_000,
                        refreshTokenExpiresAtEpochMillis = now + 180_000,
                    ),
                ),
                plan = LicensePlan.Enterprise,
                remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
                policyVersion = nextPolicyVersion,
                policyEtag = nextPolicyEtag,
            )
        }

        override suspend fun syncPolicy(state: EnterpriseAdminStateModel, request: PolicySyncRequest): PolicySyncResponse {
            if (failPolicySync) {
                throw EnterpriseRemoteException(null, "offline policy sync", true)
            }
            return PolicySyncResponse(
                modified = request.ifNoneMatch != nextPolicyEtag,
                adminPolicy = nextPolicy,
                plan = LicensePlan.Enterprise,
                policyVersion = nextPolicyVersion,
                policyEtag = nextPolicyEtag,
                remoteFeatureOverrides = setOf(FeatureFlag.AdminConsole, FeatureFlag.Collaboration, FeatureFlag.Ai),
                serverTimestampEpochMillis = System.currentTimeMillis(),
            )
        }

        override suspend fun revokeSession(state: EnterpriseAdminStateModel, accessToken: String?) = Unit

        override suspend fun uploadTelemetry(state: EnterpriseAdminStateModel, request: TelemetryBatchUploadRequest, accessToken: String?): TelemetryBatchUploadResponse {
            return TelemetryBatchUploadResponse(
                acceptedIds = request.events.map { it.id },
                serverTimestampEpochMillis = System.currentTimeMillis(),
            )
        }
    }

    private class TestSecureFileCipher : SecureFileCipher {
        override fun encryptToFile(plainBytes: ByteArray, destination: File) {
            destination.parentFile?.mkdirs()
            destination.writeBytes(plainBytes)
        }

        override fun decryptFromFile(source: File): ByteArray = source.readBytes()
    }
}
