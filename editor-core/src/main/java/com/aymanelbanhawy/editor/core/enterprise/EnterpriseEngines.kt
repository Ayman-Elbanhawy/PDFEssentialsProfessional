package com.aymanelbanhawy.editor.core.enterprise

object EntitlementEngine {

    fun resolve(
        plan: LicensePlan,
        @Suppress("UNUSED_PARAMETER") policy: AdminPolicyModel,
        remoteFeatureOverrides: Set<FeatureFlag> = emptySet(),
    ): EntitlementStateModel {
        val baseFeatures = when (plan) {
            LicensePlan.Free -> setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Search,
            )

            LicensePlan.Premium -> setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
            )

            LicensePlan.Enterprise -> FeatureFlag.entries.toSet()
        }

        // Important rule:
        // Entitlements are the source of truth for inclusion.
        // Admin policy may restrict usage, but it must not remove feature inclusion.
        // Remote feature overrides may add server-granted entitlements, but they do not
        // participate in policy-based restriction.
        return EntitlementStateModel(
            plan = plan,
            features = baseFeatures + remoteFeatureOverrides,
        )
    }
}

object PolicyEngine {

    fun exportAllowed(state: EnterpriseAdminStateModel): Boolean =
        !state.adminPolicy.restrictExport

    fun allowedConnectors(state: EnterpriseAdminStateModel): List<CloudConnector> =
        state.adminPolicy.allowedCloudConnectors

    fun watermarkFor(state: EnterpriseAdminStateModel): String? =
        state.adminPolicy.forcedWatermarkText.takeIf { it.isNotBlank() }
}
