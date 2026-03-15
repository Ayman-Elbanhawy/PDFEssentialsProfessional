package com.aymanelbanhawy.editor.core.collaboration

import android.content.Context
import android.content.ContextWrapper
import java.io.File
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationProvider
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.CollaborationBackendMode
import com.aymanelbanhawy.editor.core.enterprise.CollaborationServiceConfig
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

class CollaborationRemoteRegistryTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "_type" }
    private val context = object : ContextWrapper(null) {
        private val filesRoot = File(System.getProperty("java.io.tmpdir"), "collaboration-registry-test").apply { mkdirs() }
        override fun getFilesDir(): File = filesRoot
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun legacyLocalModeWithRealEndpointMigratesToHttpProvider() = runTest {
        val registry = CollaborationRemoteRegistry(
            context = context,
            enterpriseAdminRepository = StaticEnterpriseRepo(
                EnterpriseAdminStateModel(
                    authSession = AuthSessionModel(mode = AuthenticationMode.Personal, provider = AuthenticationProvider.Local, isSignedIn = true),
                    tenantConfiguration = TenantConfigurationModel(
                        collaboration = CollaborationServiceConfig(
                            backendMode = CollaborationBackendMode.LocalEmulator,
                            baseUrl = "https://reviews.example.com",
                        ),
                    ),
                    adminPolicy = AdminPolicyModel(allowCollaborationSync = true, allowExternalSharing = true),
                ),
            ),
            credentialStore = CollaborationCredentialStore(context, json),
            json = json,
        )

        val selected = registry.select()

        assertThat(selected).isInstanceOf(HttpCollaborationRemoteDataSource::class.java)
    }

    @Test
    fun legacyLocalModeWithoutEndpointFailsWithMigrationMessage() = runTest {
        val registry = CollaborationRemoteRegistry(
            context = context,
            enterpriseAdminRepository = StaticEnterpriseRepo(
                EnterpriseAdminStateModel(
                    authSession = AuthSessionModel(mode = AuthenticationMode.Personal, provider = AuthenticationProvider.Local, isSignedIn = true),
                    tenantConfiguration = TenantConfigurationModel(
                        collaboration = CollaborationServiceConfig(
                            backendMode = CollaborationBackendMode.LocalEmulator,
                            baseUrl = "",
                        ),
                    ),
                    adminPolicy = AdminPolicyModel(allowCollaborationSync = true, allowExternalSharing = true),
                ),
            ),
            credentialStore = CollaborationCredentialStore(context, json),
            json = json,
        )

        val error = runCatching { registry.select() }.exceptionOrNull()

        assertThat(error).isInstanceOf(CollaborationRemoteException::class.java)
        assertThat((error as CollaborationRemoteException).error.message).contains("Configure a collaboration service endpoint")
    }

    @Test
    fun remoteModeBlockedWhenPolicyDisablesCollaboration() = runTest {
        val registry = CollaborationRemoteRegistry(
            context = context,
            enterpriseAdminRepository = StaticEnterpriseRepo(
                EnterpriseAdminStateModel(
                    authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise, provider = AuthenticationProvider.Oidc, isSignedIn = true),
                    tenantConfiguration = TenantConfigurationModel(collaboration = CollaborationServiceConfig(backendMode = CollaborationBackendMode.RemoteHttp, baseUrl = "https://example.com", requireEnterpriseAuth = true)),
                    adminPolicy = AdminPolicyModel(allowCollaborationSync = false),
                ),
            ),
            credentialStore = CollaborationCredentialStore(context, json),
            json = json,
        )

        val error = runCatching { registry.select() }.exceptionOrNull()

        assertThat(error).isInstanceOf(CollaborationRemoteException::class.java)
        assertThat((error as CollaborationRemoteException).error.code).isEqualTo(RemoteErrorCode.Forbidden)
    }
}

private class StaticEnterpriseRepo(
    private val state: EnterpriseAdminStateModel,
) : EnterpriseAdminRepository {
    override suspend fun loadState(): EnterpriseAdminStateModel = state
    override suspend fun saveState(state: EnterpriseAdminStateModel) = Unit
    override suspend fun signInPersonal(displayName: String): EnterpriseAdminStateModel = state
    override suspend fun signInEnterprise(email: String, tenant: TenantConfigurationModel): EnterpriseAdminStateModel = state
    override suspend fun signOut(): EnterpriseAdminStateModel = state
    override suspend fun refreshRemoteState(force: Boolean): EnterpriseAdminStateModel = state
    override suspend fun refreshSessionIfNeeded(): EnterpriseAdminStateModel = state
    override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel): EntitlementStateModel = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Collaboration))
    override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
    override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
    override suspend fun flushTelemetry(): Int = 0
    override suspend fun diagnosticsBundle(destination: File, appSummary: Map<String, String>): File = destination
}
