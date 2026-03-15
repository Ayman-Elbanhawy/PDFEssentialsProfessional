package com.aymanelbanhawy.editor.core.enterprise

import kotlinx.serialization.Serializable

@Serializable
enum class AuthenticationMode {
    Personal,
    Enterprise,
}

@Serializable
enum class AuthenticationProvider {
    Local,
    Oidc,
}

@Serializable
enum class EnterpriseBootstrapMode {
    LocalDevelopment,
    Remote,
}

@Serializable
data class OidcProviderConfig(
    val issuerUrl: String = "",
    val clientId: String = "",
    val redirectUri: String = "",
    val scopes: List<String> = listOf("openid", "profile", "email", "offline_access"),
)

@Serializable
enum class CollaborationBackendMode {
    Disabled,
    LocalEmulator,
    RemoteHttp,
}

@Serializable
enum class CollaborationScope {
    TenantOnly,
    SharedWorkspace,
    ExternalGuests,
}

@Serializable
data class CollaborationServiceConfig(
    val backendMode: CollaborationBackendMode = CollaborationBackendMode.RemoteHttp,
    val baseUrl: String = "",
    val apiPath: String = "/v1/collaboration",
    val connectTimeoutMillis: Long = 15_000,
    val readTimeoutMillis: Long = 45_000,
    val requestTimeoutMillis: Long = 60_000,
    val pageSize: Int = 100,
    val retryCount: Int = 3,
    val requireEnterpriseAuth: Boolean = false,
    val allowMeteredNetwork: Boolean = true,
)

@Serializable
data class TenantConfigurationModel(
    val tenantId: String = "personal",
    val tenantName: String = "Personal Workspace",
    val domain: String = "",
    val issuerBaseUrl: String = "",
    val apiBaseUrl: String = "",
    val policyPath: String = "/v1/tenant/policy",
    val authPath: String = "/v1/tenant/bootstrap",
    val telemetryPath: String = "/v1/telemetry/batch",
    val oidc: OidcProviderConfig = OidcProviderConfig(),
    val collaboration: CollaborationServiceConfig = CollaborationServiceConfig(),
    val bootstrapMode: EnterpriseBootstrapMode = EnterpriseBootstrapMode.LocalDevelopment,
    val bootstrapCompletedAtEpochMillis: Long? = null,
    val supportsRemotePolicySync: Boolean = false,
)

@Serializable
enum class LicensePlan {
    Free,
    Premium,
    Enterprise,
}

@Serializable
enum class FeatureFlag {
    Annotate,
    Organize,
    Forms,
    Sign,
    Search,
    Collaboration,
    Security,
    Ai,
    CloudConnectors,
    AdminConsole,
}

@Serializable
enum class CloudConnector {
    LocalFiles,
    S3Compatible,
    GoogleDrive,
    OneDrive,
    SharePoint,
    Box,
    WebDav,
    DocumentProvider,
}


@Serializable
enum class AiDocumentScopePolicy {
    CurrentDocumentOnly,
    PinnedDocumentsOnly,
    RecentDocuments,
}
@Serializable
data class AdminPolicyModel(
    val retentionDays: Int = 30,
    val restrictExport: Boolean = false,
    val restrictPrint: Boolean = false,
    val restrictCopy: Boolean = false,
    val forcedWatermarkText: String = "",
    val allowedCloudConnectors: List<CloudConnector> = listOf(CloudConnector.LocalFiles),
    val allowedDestinationPatterns: List<String> = emptyList(),
    val approvedAiProviderIds: List<String> = emptyList(),
    val allowCloudMultiDocumentAi: Boolean = false,
    val maxAiWorkspaceDocuments: Int = 5,
    val aiHistoryRetentionDays: Int = 30,
    val aiDocumentScope: AiDocumentScopePolicy = AiDocumentScopePolicy.PinnedDocumentsOnly,
    val aiEnabled: Boolean = false,
    val audioFeaturesEnabled: Boolean = true,
    val voiceInputEnabled: Boolean = true,
    val speechOutputEnabled: Boolean = true,
    val voiceCommentsEnabled: Boolean = true,
    val voiceCommentRetentionDays: Int = 30,
    val allowCloudAiProviders: Boolean = false,
    val allowCollaborationSync: Boolean = true,
    val allowExternalSharing: Boolean = true,
    val collaborationScope: CollaborationScope = CollaborationScope.ExternalGuests,
    val telemetryUploadEnabled: Boolean = true,
)

@Serializable
data class PrivacySettingsModel(
    val telemetryEnabled: Boolean = true,
    val includeDocumentNames: Boolean = false,
    val includeDiagnostics: Boolean = true,
    val localOnlyMode: Boolean = false,
)

@Serializable
enum class AuthSessionStatus {
    SignedOut,
    Active,
    ExpiringSoon,
    Expired,
    RefreshFailed,
    Revoked,
}

@Serializable
data class TokenBackedSessionModel(
    val sessionId: String = "",
    val accessTokenAlias: String? = null,
    val refreshTokenAlias: String? = null,
    val tokenType: String = "Bearer",
    val issuedAtEpochMillis: Long? = null,
    val accessTokenExpiresAtEpochMillis: Long? = null,
    val refreshTokenExpiresAtEpochMillis: Long? = null,
    val revokedAtEpochMillis: Long? = null,
)

@Serializable
data class AuthSessionModel(
    val mode: AuthenticationMode = AuthenticationMode.Personal,
    val provider: AuthenticationProvider = AuthenticationProvider.Local,
    val status: AuthSessionStatus = AuthSessionStatus.SignedOut,
    val isSignedIn: Boolean = false,
    val displayName: String = "Guest",
    val email: String = "",
    val subjectId: String = "",
    val collaborationCredentialAlias: String? = null,
    val sessionExpiresAtEpochMillis: Long? = null,
    val session: TokenBackedSessionModel = TokenBackedSessionModel(),
    val lastRefreshedAtEpochMillis: Long? = null,
    val lastFailure: String? = null,
) {
    fun isEnterpriseRemote(): Boolean = mode == AuthenticationMode.Enterprise && provider == AuthenticationProvider.Oidc
}

@Serializable
data class PolicySyncMetadataModel(
    val policyVersion: String = "local",
    val policyEtag: String? = null,
    val lastPolicySyncAtEpochMillis: Long? = null,
    val lastEntitlementRefreshAtEpochMillis: Long? = null,
    val lastBootstrapAtEpochMillis: Long? = null,
    val lastSuccessfulTelemetryUploadAtEpochMillis: Long? = null,
    val lastDiagnosticsBundleAtEpochMillis: Long? = null,
    val lastRemoteError: String? = null,
)

@Serializable
data class EnterpriseAdminStateModel(
    val authSession: AuthSessionModel = AuthSessionModel(),
    val tenantConfiguration: TenantConfigurationModel = TenantConfigurationModel(),
    val plan: LicensePlan = LicensePlan.Free,
    val privacySettings: PrivacySettingsModel = PrivacySettingsModel(),
    val adminPolicy: AdminPolicyModel = AdminPolicyModel(),
    val policySync: PolicySyncMetadataModel = PolicySyncMetadataModel(),
    val remoteFeatureOverrides: Set<FeatureFlag> = emptySet(),
)

@Serializable
data class EntitlementStateModel(
    val plan: LicensePlan,
    val features: Set<FeatureFlag>,
)

@Serializable
enum class TelemetryCategory {
    Product,
    Admin,
    Diagnostic,
}

@Serializable
enum class TelemetryUploadState {
    Pending,
    Uploading,
    Uploaded,
    Failed,
}

@Serializable
data class TelemetryEventModel(
    val id: String,
    val category: TelemetryCategory,
    val name: String,
    val createdAtEpochMillis: Long,
    val properties: Map<String, String> = emptyMap(),
    val uploadState: TelemetryUploadState = TelemetryUploadState.Pending,
    val attemptCount: Int = 0,
    val lastAttemptAtEpochMillis: Long? = null,
    val uploadedAtEpochMillis: Long? = null,
    val failureMessage: String? = null,
)

@Serializable
data class TenantBootstrapRequest(
    val email: String,
    val tenantHint: String,
    val requestedMode: AuthenticationMode = AuthenticationMode.Enterprise,
    val currentPolicyVersion: String? = null,
)

@Serializable
data class TenantBootstrapResponse(
    val tenantConfiguration: TenantConfigurationModel,
    val authSession: AuthSessionModel,
    val plan: LicensePlan,
    val adminPolicy: AdminPolicyModel,
    val remoteFeatureOverrides: Set<FeatureFlag> = emptySet(),
    val policySync: PolicySyncMetadataModel = PolicySyncMetadataModel(),
)

@Serializable
data class PolicySyncRequest(
    val tenantId: String,
    val ifNoneMatch: String?,
)

@Serializable
data class PolicySyncResponse(
    val modified: Boolean,
    val adminPolicy: AdminPolicyModel,
    val plan: LicensePlan,
    val policyVersion: String,
    val policyEtag: String?,
    val remoteFeatureOverrides: Set<FeatureFlag> = emptySet(),
    val serverTimestampEpochMillis: Long,
)

@Serializable
data class SessionRefreshResponse(
    val authSession: AuthSessionModel,
    val plan: LicensePlan,
    val remoteFeatureOverrides: Set<FeatureFlag> = emptySet(),
    val policyVersion: String? = null,
    val policyEtag: String? = null,
)

@Serializable
data class TelemetryBatchUploadRequest(
    val tenantId: String,
    val events: List<TelemetryEventModel>,
)

@Serializable
data class TelemetryBatchUploadResponse(
    val acceptedIds: List<String>,
    val rejectedIds: List<String> = emptyList(),
    val serverTimestampEpochMillis: Long,
)







