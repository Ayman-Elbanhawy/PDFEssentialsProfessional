package com.aymanelbanhawy.editor.core.enterprise

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnterpriseEnginesTest {
    @Test
    fun premiumPlan_enablesCorePremiumFeatures() {
        val resolved = EntitlementEngine.resolve(
            plan = LicensePlan.Premium,
            policy = AdminPolicyModel(aiEnabled = false, allowedCloudConnectors = listOf(CloudConnector.LocalFiles)),
        )

        assertThat(resolved.features).contains(FeatureFlag.Sign)
        assertThat(resolved.features).contains(FeatureFlag.Security)
        assertThat(resolved.features).doesNotContain(FeatureFlag.Ai)
        assertThat(resolved.features).doesNotContain(FeatureFlag.AdminConsole)
    }

    @Test
    fun premiumPlan_withAiDisabledPolicy_doesNotGainAiEntitlement() {
        val resolved = EntitlementEngine.resolve(
            plan = LicensePlan.Premium,
            policy = AdminPolicyModel(aiEnabled = false, allowedCloudConnectors = listOf(CloudConnector.LocalFiles)),
        )

        assertThat(resolved.features).doesNotContain(FeatureFlag.Ai)
    }

    @Test
    fun enterprisePolicy_restrictsExportAndForcesWatermark() {
        val state = EnterpriseAdminStateModel(
            plan = LicensePlan.Enterprise,
            adminPolicy = AdminPolicyModel(
                restrictExport = true,
                forcedWatermarkText = "Enterprise Confidential",
                allowedCloudConnectors = listOf(CloudConnector.LocalFiles, CloudConnector.SharePoint),
                aiEnabled = true,
            ),
        )

        assertThat(PolicyEngine.exportAllowed(state)).isFalse()
        assertThat(PolicyEngine.watermarkFor(state)).isEqualTo("Enterprise Confidential")
        assertThat(PolicyEngine.allowedConnectors(state)).contains(CloudConnector.SharePoint)
    }
}
