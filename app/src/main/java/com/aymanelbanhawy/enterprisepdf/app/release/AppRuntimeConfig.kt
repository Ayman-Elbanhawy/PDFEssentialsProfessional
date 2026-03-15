package com.aymanelbanhawy.enterprisepdf.app.release

import android.content.Context
import android.content.RestrictionsManager
import com.aymanelbanhawy.enterprisepdf.app.BuildConfig

data class ManagedAppRestrictions(
    val tenantBaseUrl: String? = null,
    val tenantIssuerUrl: String? = null,
    val tenantPolicyBaseUrl: String? = null,
    val aiBaseUrl: String? = null,
    val collaborationBaseUrl: String? = null,
    val connectorBaseUrl: String? = null,
    val defaultAiProviderId: String? = null,
    val defaultAiModel: String? = null,
    val approvedAiProviders: List<String> = emptyList(),
    val allowedConnectors: List<String> = emptyList(),
    val allowedDestinationPatterns: List<String> = emptyList(),
    val disableAi: Boolean = false,
    val disableCloudAi: Boolean = false,
    val forceWatermark: String? = null,
    val forceMetadataScrub: Boolean = false,
    val disableExternalSharing: Boolean = false,
    val telemetryUploadEnabled: Boolean? = null,
    val telemetryRetentionDays: Int? = null,
    val secureLogging: Boolean = true,
)

data class AppRuntimeConfig(
    val environment: String,
    val tenantBaseUrl: String,
    val tenantIssuerUrl: String,
    val tenantPolicyBaseUrl: String,
    val aiBaseUrl: String,
    val collaborationBaseUrl: String,
    val connectorBaseUrl: String,
    val aiCloudEnabledByDefault: Boolean,
    val secureLoggingEnabled: Boolean,
    val defaultExportWatermark: String,
    val defaultAiProviderId: String,
    val defaultAiModel: String,
    val approvedAiProviders: List<String>,
    val allowedConnectors: List<String>,
    val allowedDestinationPatterns: List<String>,
    val telemetryUploadEnabled: Boolean,
    val telemetryRetentionDays: Int,
    val certificatePins: List<String>,
    val managedRestrictions: ManagedAppRestrictions,
) {
    val externalSharingAllowed: Boolean
        get() = !managedRestrictions.disableExternalSharing

    val effectiveWatermark: String
        get() = managedRestrictions.forceWatermark?.takeIf { it.isNotBlank() } ?: defaultExportWatermark

    val effectiveCloudAiEnabled: Boolean
        get() = !managedRestrictions.disableAi && aiCloudEnabledByDefault && !managedRestrictions.disableCloudAi

    val effectiveAiEnabled: Boolean
        get() = !managedRestrictions.disableAi

    val forceMetadataScrubOnExport: Boolean
        get() = managedRestrictions.forceMetadataScrub
}

object AppRuntimeConfigLoader {
    fun load(context: Context): AppRuntimeConfig {
        val managedRestrictions = loadManagedRestrictions(context)
        return AppRuntimeConfig(
            environment = BuildConfig.APP_ENVIRONMENT,
            tenantBaseUrl = managedRestrictions.tenantBaseUrl.normalizeUrl(BuildConfig.TENANT_BASE_URL),
            tenantIssuerUrl = managedRestrictions.tenantIssuerUrl.normalizeToken(""),
            tenantPolicyBaseUrl = managedRestrictions.tenantPolicyBaseUrl.normalizeToken(""),
            aiBaseUrl = managedRestrictions.aiBaseUrl.normalizeUrl(BuildConfig.AI_BASE_URL),
            collaborationBaseUrl = managedRestrictions.collaborationBaseUrl.normalizeUrl(BuildConfig.COLLABORATION_BASE_URL),
            connectorBaseUrl = managedRestrictions.connectorBaseUrl.normalizeToken(""),
            aiCloudEnabledByDefault = BuildConfig.AI_CLOUD_DEFAULT_ENABLED,
            secureLoggingEnabled = BuildConfig.SECURE_LOGGING_ENABLED && managedRestrictions.secureLogging,
            defaultExportWatermark = BuildConfig.DEFAULT_EXPORT_WATERMARK,
            defaultAiProviderId = managedRestrictions.defaultAiProviderId.normalizeToken(""),
            defaultAiModel = managedRestrictions.defaultAiModel.normalizeToken(""),
            approvedAiProviders = managedRestrictions.approvedAiProviders,
            allowedConnectors = managedRestrictions.allowedConnectors,
            allowedDestinationPatterns = managedRestrictions.allowedDestinationPatterns,
            telemetryUploadEnabled = managedRestrictions.telemetryUploadEnabled ?: (BuildConfig.APP_ENVIRONMENT != "dev"),
            telemetryRetentionDays = managedRestrictions.telemetryRetentionDays ?: 30,
            certificatePins = BuildConfig.CERTIFICATE_PIN_SET.parseCsv(),
            managedRestrictions = managedRestrictions,
        )
    }

    private fun loadManagedRestrictions(context: Context): ManagedAppRestrictions {
        if (!BuildConfig.ENABLE_MANAGED_CONFIG) return ManagedAppRestrictions()
        val restrictionsManager = context.getSystemService(Context.RESTRICTIONS_SERVICE) as? RestrictionsManager
            ?: return ManagedAppRestrictions()
        val restrictions = restrictionsManager.applicationRestrictions
        return ManagedAppRestrictions(
            tenantBaseUrl = restrictions.getString("managed_tenant_base_url"),
            tenantIssuerUrl = restrictions.getString("managed_tenant_issuer_url"),
            tenantPolicyBaseUrl = restrictions.getString("managed_tenant_policy_base_url"),
            aiBaseUrl = restrictions.getString("managed_ai_base_url"),
            collaborationBaseUrl = restrictions.getString("managed_collaboration_base_url"),
            connectorBaseUrl = restrictions.getString("managed_connector_base_url"),
            defaultAiProviderId = restrictions.getString("managed_default_ai_provider"),
            defaultAiModel = restrictions.getString("managed_default_ai_model"),
            approvedAiProviders = restrictions.getString("managed_approved_ai_providers").parseCsv(),
            allowedConnectors = restrictions.getString("managed_allowed_connectors").parseCsv(),
            allowedDestinationPatterns = restrictions.getString("managed_allowed_destinations").parseCsv(),
            disableAi = restrictions.getBoolean("managed_disable_ai", false),
            disableCloudAi = restrictions.getBoolean("managed_disable_cloud_ai", false),
            forceWatermark = restrictions.getString("managed_force_watermark"),
            forceMetadataScrub = restrictions.getBoolean("managed_force_metadata_scrub", false),
            disableExternalSharing = restrictions.getBoolean("managed_disable_external_sharing", false),
            telemetryUploadEnabled = restrictions.takeIf { restrictions.containsKey("managed_telemetry_upload_enabled") }?.getBoolean("managed_telemetry_upload_enabled", true),
            telemetryRetentionDays = restrictions.takeIf { restrictions.containsKey("managed_telemetry_retention_days") }?.getInt("managed_telemetry_retention_days", 30),
            secureLogging = restrictions.getBoolean("managed_secure_logging", true),
        )
    }

    private fun String?.normalizeUrl(fallback: String): String = this.trimToNull() ?: fallback
    private fun String?.normalizeToken(fallback: String): String = this.trimToNull() ?: fallback
    private fun String?.parseCsv(): List<String> = this.trimToNull()?.split(',')?.mapNotNull { it.trim().takeIf(String::isNotEmpty) }?.distinct().orEmpty()
    private fun String?.trimToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

