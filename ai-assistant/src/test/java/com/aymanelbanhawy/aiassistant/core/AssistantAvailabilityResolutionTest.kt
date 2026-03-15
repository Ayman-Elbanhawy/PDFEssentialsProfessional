package com.aymanelbanhawy.aiassistant.core

import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import com.aymanelbanhawy.editor.core.enterprise.EntitlementStateModel
import com.aymanelbanhawy.editor.core.enterprise.FeatureFlag
import com.aymanelbanhawy.editor.core.enterprise.LicensePlan
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AssistantAvailabilityResolutionTest {
    @Test
    fun premiumWithoutAiEntitlementRemainsUnavailableEvenWhenPolicyIsEnabled() {
        val availability = resolveAssistantAvailability(
            entitlements = EntitlementStateModel(LicensePlan.Premium, emptySet()),
            enterpriseState = EnterpriseAdminStateModel(
                plan = LicensePlan.Premium,
                adminPolicy = AdminPolicyModel(aiEnabled = true),
            ),
        )

        assertThat(availability.enabled).isFalse()
        assertThat(availability.reason).isEqualTo("AI is not included in the current plan.")
        assertThat(availability.missingFeatures).containsExactly(FeatureFlag.Ai)
    }

    @Test
    fun enterpriseWithAiEntitlementAndEnabledPolicyIsAvailable() {
        val availability = resolveAssistantAvailability(
            entitlements = EntitlementStateModel(LicensePlan.Enterprise, setOf(FeatureFlag.Ai)),
            enterpriseState = EnterpriseAdminStateModel(
                plan = LicensePlan.Enterprise,
                adminPolicy = AdminPolicyModel(aiEnabled = true),
            ),
        )

        assertThat(availability.enabled).isTrue()
        assertThat(availability.reason).isNull()
    }
}
