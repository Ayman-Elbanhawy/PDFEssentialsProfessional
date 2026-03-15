package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OpenAiCompatibleProviderAdapterTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun listsModelsAndStreamsResponse() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"data":[{"id":"gpt-4o-mini","owned_by":"openai"}]}
        """.trimIndent()))
        server.enqueue(
            MockResponse().setBody(
                "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n" +
                    "data: {\"choices\":[{\"delta\":{\"content\":\" world\"}}]}\n" +
                    "data: [DONE]\n",
            ),
        )
        val registry = ProviderRuntimeFactory(OkHttpClient(), json).createRegistry()
        val adapter = registry.adapter(AiProviderKind.OpenAiCompatible)
        val profile = AiProviderProfile(
            id = "openai-compatible",
            kind = AiProviderKind.OpenAiCompatible,
            displayName = "Compat",
            endpointUrl = server.url("/v1").toString().trimEnd('/'),
            modelId = "gpt-4o-mini",
            hasStoredCredential = true,
        )

        val catalog = adapter.listModels(profile, "secret")
        val events = adapter.streamCompletion(ProviderRequest(profile, "secret", "hello", 30, 0)).events.toList()

        assertThat(catalog.models).hasSize(1)
        assertThat(catalog.models.first().id).isEqualTo("gpt-4o-mini")
        assertThat(events.filterIsInstance<ProviderStreamEvent.Completed>().single().fullText).isEqualTo("Hello world")
    }
}
