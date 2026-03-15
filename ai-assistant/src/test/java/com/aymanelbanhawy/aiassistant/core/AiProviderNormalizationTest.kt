package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiProviderNormalizationTest {
    @Test
    fun normalizePersistenceModel_replacesLegacyExampleEndpoints() {
        val model = AssistantPersistenceModel(
            selectedProviderId = "openai-compatible",
            profiles = listOf(
                AiProviderProfile(
                    id = "ollama-remote",
                    kind = AiProviderKind.OllamaRemote,
                    displayName = "",
                    endpointUrl = "https://ollama.example.com",
                    requestTimeoutSeconds = 5,
                    retryCount = 9,
                ),
                AiProviderProfile(
                    id = "openai",
                    kind = AiProviderKind.OpenAi,
                    displayName = "",
                    endpointUrl = "",
                    requestTimeoutSeconds = 999,
                    retryCount = -2,
                ),
                AiProviderProfile(
                    id = "openai-compatible",
                    kind = AiProviderKind.OpenAiCompatible,
                    displayName = "Compatible",
                    endpointUrl = "https://api.example.com/v1",
                ),
            ),
        )

        val normalized = normalizePersistenceModel(model)

        assertThat(normalized.selectedProviderId).isEqualTo("openai-compatible")
        assertThat(normalized.profiles.first { it.id == "ollama-remote" }.endpointUrl).isEmpty()
        assertThat(normalized.profiles.first { it.id == "openai" }.endpointUrl).isEqualTo("https://api.openai.com/v1")
        assertThat(normalized.profiles.first { it.id == "openai" }.requestTimeoutSeconds).isEqualTo(300)
        assertThat(normalized.profiles.first { it.id == "openai" }.retryCount).isEqualTo(0)
        assertThat(normalized.profiles.first { it.id == "openai-compatible" }.endpointUrl).isEmpty()
    }

    @Test
    fun draftNormalization_appliesProviderDefaults() {
        val normalized = AiProviderDraft(
            profileId = "openai-compatible",
            kind = AiProviderKind.OpenAiCompatible,
            displayName = "",
            endpointUrl = "https://api.example.com/v1",
            modelId = " gpt-4o-mini ",
            requestTimeoutSeconds = 2,
            retryCount = 7,
        ).normalized()

        assertThat(normalized.displayName).isEqualTo("OpenAI Compatible")
        assertThat(normalized.endpointUrl).isEmpty()
        assertThat(normalized.modelId).isEqualTo("gpt-4o-mini")
        assertThat(normalized.requestTimeoutSeconds).isEqualTo(15)
        assertThat(normalized.retryCount).isEqualTo(4)
    }
}
