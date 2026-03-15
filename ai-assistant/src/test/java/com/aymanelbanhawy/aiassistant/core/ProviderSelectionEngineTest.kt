package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import com.aymanelbanhawy.editor.core.enterprise.AdminPolicyModel
import com.aymanelbanhawy.editor.core.enterprise.AuthSessionModel
import com.aymanelbanhawy.editor.core.enterprise.AuthenticationMode
import com.aymanelbanhawy.editor.core.enterprise.EnterpriseAdminStateModel
import org.junit.Test

class ProviderSelectionEngineTest {
    private val engine = ProviderSelectionEngine()

    @Test
    fun localOnlyMode_prefersLocalProvider() {
        val runtime = AiProviderRuntimeState(
            selectedProviderId = "openai",
            profiles = defaultProviderProfiles(),
        )

        val decision = engine.select(runtime, AssistantSettings(privacyMode = AssistantPrivacyMode.LocalOnly), EnterpriseAdminStateModel())

        assertThat(decision.profile?.kind).isEqualTo(AiProviderKind.OllamaLocal)
        assertThat(decision.blockedReason).isNull()
    }

    @Test
    fun enterprisePolicy_blocksCloudProvider() {
        val runtime = AiProviderRuntimeState(
            selectedProviderId = "openai",
            profiles = defaultProviderProfiles(),
        )
        val enterpriseState = EnterpriseAdminStateModel(
            authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise),
            adminPolicy = AdminPolicyModel(aiEnabled = true, allowCloudAiProviders = false),
        )

        val decision = engine.select(runtime, AssistantSettings(privacyMode = AssistantPrivacyMode.CloudAssisted), enterpriseState)

        assertThat(decision.profile).isNull()
        assertThat(decision.blockedReason).contains("blocks cloud AI providers")
    }

    @Test
    fun enterprisePolicy_blocksUnapprovedProvider() {
        val runtime = AiProviderRuntimeState(
            selectedProviderId = "openai",
            profiles = defaultProviderProfiles(),
        )
        val enterpriseState = EnterpriseAdminStateModel(
            authSession = AuthSessionModel(mode = AuthenticationMode.Enterprise),
            adminPolicy = AdminPolicyModel(
                aiEnabled = true,
                allowCloudAiProviders = true,
                approvedAiProviderIds = listOf("ollama-local"),
            ),
        )

        val decision = engine.select(runtime, AssistantSettings(privacyMode = AssistantPrivacyMode.CloudAssisted), enterpriseState)

        assertThat(decision.profile).isNull()
        assertThat(decision.blockedReason).contains("approved AI providers")
    }

}
