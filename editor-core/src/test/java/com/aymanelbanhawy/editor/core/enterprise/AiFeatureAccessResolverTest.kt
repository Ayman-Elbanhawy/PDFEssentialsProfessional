package com.aymanelbanhawy.editor.core.enterprise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFeatureAccessResolverTest {

    @Test
    fun free_without_ai_entitlement_and_policy_enabled_is_unavailable() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Free,
            features = setOf(FeatureFlag.Annotate, FeatureFlag.Search),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        assertFalse(access.entitled)
        assertFalse(access.available)
        assertEquals("AI is not included in the current plan.", access.adminControlMessage())
    }

    @Test
    fun premium_without_ai_entitlement_and_policy_enabled_is_unavailable() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Premium,
            features = setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
            ),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        assertFalse(access.entitled)
        assertFalse(access.available)
        assertEquals("AI is not included in the current plan.", access.adminControlMessage())
    }

    @Test
    fun premium_with_ai_entitlement_and_policy_disabled_is_unavailable() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Premium,
            features = setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
                FeatureFlag.Ai,
            ),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = false),
        )

        assertTrue(access.entitled)
        assertFalse(access.available)
        assertEquals(
            "AI is included in this plan but currently disabled by admin policy.",
            access.adminControlMessage(),
        )
    }

    @Test
    fun premium_with_ai_entitlement_and_policy_enabled_is_available() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Premium,
            features = setOf(
                FeatureFlag.Annotate,
                FeatureFlag.Organize,
                FeatureFlag.Forms,
                FeatureFlag.Sign,
                FeatureFlag.Search,
                FeatureFlag.Security,
                FeatureFlag.Ai,
            ),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        assertTrue(access.entitled)
        assertTrue(access.available)
        assertEquals(
            "AI is included in this plan and can be controlled here.",
            access.adminControlMessage(),
        )
    }

    @Test
    fun enterprise_without_ai_entitlement_and_policy_enabled_is_unavailable() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Enterprise,
            features = FeatureFlag.entries.filterNot { it == FeatureFlag.Ai }.toSet(),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        assertFalse(access.entitled)
        assertFalse(access.available)
        assertEquals("AI is not included in the current plan.", access.adminControlMessage())
    }

    @Test
    fun enterprise_with_ai_entitlement_and_policy_enabled_is_available() {
        val entitlements = EntitlementStateModel(
            plan = LicensePlan.Enterprise,
            features = FeatureFlag.entries.toSet(),
        )

        val access = AiFeatureAccessResolver.resolve(
            entitlements = entitlements,
            adminPolicy = AdminPolicyModel(aiEnabled = true),
        )

        assertTrue(access.entitled)
        assertTrue(access.available)
        assertEquals(
            "AI is included in this plan and can be controlled here.",
            access.adminControlMessage(),
        )
    }

    @Test
    fun entitlement_engine_does_not_remove_ai_when_policy_is_disabled() {
        val entitlements = EntitlementEngine.resolve(
            plan = LicensePlan.Enterprise,
            policy = AdminPolicyModel(aiEnabled = false),
        )

        assertTrue(FeatureFlag.Ai in entitlements.features)
    }

    @Test
    fun premium_default_entitlements_do_not_include_ai() {
        val entitlements = EntitlementEngine.resolve(
            plan = LicensePlan.Premium,
            policy = AdminPolicyModel(aiEnabled = true),
        )

        assertFalse(FeatureFlag.Ai in entitlements.features)
    }
}