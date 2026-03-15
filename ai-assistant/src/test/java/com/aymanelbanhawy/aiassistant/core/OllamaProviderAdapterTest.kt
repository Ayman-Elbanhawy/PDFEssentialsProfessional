package com.aymanelbanhawy.aiassistant.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class OllamaProviderAdapterTest {
    private lateinit var server: MockWebServer

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
    fun parsesTagsCatalog() = runTest {
        server.enqueue(MockResponse().setBody("""
            {"models":[{"name":"llama3.2","details":{"family":"llama"}}]}
        """.trimIndent()))
        val registry = ProviderRuntimeFactory(OkHttpClient(), Json { ignoreUnknownKeys = true }).createRegistry()
        val adapter = registry.adapter(AiProviderKind.OllamaLocal)
        val profile = AiProviderProfile(
            id = DEFAULT_PROVIDER_ID,
            kind = AiProviderKind.OllamaLocal,
            displayName = "Local Ollama",
            endpointUrl = server.url("").toString().trimEnd('/'),
            modelId = "llama3.2",
        )

        val catalog = adapter.listModels(profile, null)

        assertThat(catalog.models.first().id).isEqualTo("llama3.2")
        assertThat(catalog.capabilities.supportsStreaming).isTrue()
    }
}
