package com.aymanelbanhawy.editor.core.enterprise

data class AiFeatureAccessResolution(
    val entitled: Boolean,
    val policyEnabled: Boolean,
) {
    val available: Boolean
        get() = entitled && policyEnabled

    fun unavailableReason(): String? = when {
        available -> null
        !entitled -> ENTITLEMENT_REQUIRED_MESSAGE
        else -> POLICY_DISABLED_MESSAGE
    }

    fun adminControlMessage(): String = when {
        !entitled -> ENTITLEMENT_REQUIRED_MESSAGE
        !policyEnabled -> "AI is included in this plan but currently disabled by admin policy."
        else -> "AI is included in this plan and can be controlled here."
    }

    companion object {
        const val ENTITLEMENT_REQUIRED_MESSAGE = "AI is not included in the current plan."
        const val POLICY_DISABLED_MESSAGE = "Tenant policy has disabled AI assistance."
    }
}

object AiFeatureAccessResolver {
    fun resolve(
        entitlements: EntitlementStateModel,
        adminPolicy: AdminPolicyModel,
    ): AiFeatureAccessResolution {
        return AiFeatureAccessResolution(
            entitled = FeatureFlag.Ai in entitlements.features,
            policyEnabled = adminPolicy.aiEnabled,
        )
    }
}
