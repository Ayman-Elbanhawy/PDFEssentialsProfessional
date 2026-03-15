package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminRepository
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.aymanelbanhawy.editor.core.enterprise.TelemetryEventModel
import com.aymanelbanhawy.editor.core.security.AppLockReason
import com.aymanelbanhawy.editor.core.security.AppLockSettingsModel
import com.aymanelbanhawy.editor.core.security.AppLockStateModel
import com.aymanelbanhawy.editor.core.security.AuditTrailEventModel
import com.aymanelbanhawy.editor.core.security.InspectionReportModel
import com.aymanelbanhawy.editor.core.security.PolicyDecision
import com.aymanelbanhawy.editor.core.security.RestrictedAction
import com.aymanelbanhawy.editor.core.security.SecurityDocumentModel
import com.aymanelbanhawy.editor.core.security.SecurityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ProviderRuntimeErrorHandlingTest {
    @Test
    fun mapsOfflineAndTimeoutErrors() {
        val runtime = AiProviderRuntime(
            registry = ProviderRuntimeFactory(okhttp3.OkHttpClient(), kotlinx.serialization.json.Json { ignoreUnknownKeys = true }).createRegistry(),
            selectionEngine = ProviderSelectionEngine(),
            secureCredentialStore = object : SecureCredentialStore {
                override suspend fun loadSecret(profileId: String): String? = null
                override suspend fun saveSecret(profileId: String, secret: String) = Unit
                override suspend fun clearSecret(profileId: String) = Unit
            },
            discovery = object : LocalAiAppDiscovery {
                override suspend fun discoverInstalledApps(): List<LocalAiAppInfo> = emptyList()
            },
            enterpriseAdminRepository = object : EnterpriseAdminRepository {
                override suspend fun loadState() = EnterpriseAdminStateModel()
                override suspend fun saveState(state: EnterpriseAdminStateModel) = Unit
                override suspend fun signInPersonal(displayName: String) = EnterpriseAdminStateModel()
                override suspend fun signInEnterprise(email: String, tenant: com.aymanelbanhawy.editor.core.enterprise.TenantConfigurationModel) = EnterpriseAdminStateModel()
                override suspend fun signOut() = EnterpriseAdminStateModel()
                override suspend fun refreshRemoteState(force: Boolean) = EnterpriseAdminStateModel()
                override suspend fun refreshSessionIfNeeded() = EnterpriseAdminStateModel()
                override suspend fun resolveEntitlements(state: EnterpriseAdminStateModel) = EntitlementStateModel(LicensePlan.Free, emptySet())
                override suspend fun queueTelemetry(event: TelemetryEventModel) = Unit
                override suspend fun pendingTelemetry(): List<TelemetryEventModel> = emptyList()
                override suspend fun flushTelemetry(): Int = 0
                override suspend fun diagnosticsBundle(destination: java.io.File, appSummary: Map<String, String>) = destination
            },
            securityRepository = object : SecurityRepository {
                override val appLockState = MutableStateFlow(AppLockStateModel())
                override suspend fun loadAppLockSettings() = AppLockSettingsModel()
                override suspend fun updateAppLockSettings(enabled: Boolean, pin: String, biometricsEnabled: Boolean, timeoutSeconds: Int) = AppLockSettingsModel()
                override suspend fun lockApp(reason: AppLockReason) = Unit
                override suspend fun unlockWithPin(pin: String) = false
                override suspend fun unlockWithBiometric() = false
                override suspend fun loadDocumentSecurity(documentKey: String) = SecurityDocumentModel()
                override suspend fun persistDocumentSecurity(documentKey: String, security: SecurityDocumentModel) = Unit
                override suspend fun inspectDocument(document: com.aymanelbanhawy.editor.core.model.DocumentModel) = InspectionReportModel()
                override fun evaluatePolicy(security: SecurityDocumentModel, action: RestrictedAction) = PolicyDecision(true)
                override suspend fun recordAudit(event: AuditTrailEventModel) = Unit
                override suspend fun auditEvents(documentKey: String): List<AuditTrailEventModel> = emptyList()
                override suspend fun exportAuditTrail(documentKey: String, destination: java.io.File) = destination
            },
        )

        assertThat(runtime.mapThrowable(UnknownHostException()).code).isEqualTo(AiProviderErrorCode.Offline)
        assertThat(runtime.mapThrowable(SocketTimeoutException()).code).isEqualTo(AiProviderErrorCode.Timeout)
    }
}
