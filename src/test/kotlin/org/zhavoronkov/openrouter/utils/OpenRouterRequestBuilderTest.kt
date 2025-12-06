package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for OpenRouterRequestBuilder
 * These tests verify that our refactoring maintains correct header behavior
 */
@DisplayName("OpenRouter Request Builder Tests")
class OpenRouterRequestBuilderTest {

    @Nested
    @DisplayName("GET Request Tests")
    inner class GetRequestTests {

        @Test
        @DisplayName("Should build GET request with no authentication")
        fun shouldBuildGetRequestWithNoAuth() {
            val request = OpenRouterRequestBuilder.buildGetRequest(
                url = "https://openrouter.ai/api/v1/models",
                authType = OpenRouterRequestBuilder.AuthType.NONE
            )

            assertEquals("GET", request.method)
            assertEquals("https://openrouter.ai/api/v1/models", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertNull(request.header("Authorization"))
        }

        @Test
        @DisplayName("Should build GET request with API key authentication")
        fun shouldBuildGetRequestWithApiKey() {
            val request = OpenRouterRequestBuilder.buildGetRequest(
                url = "https://openrouter.ai/api/v1/credits",
                authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                authToken = "sk-or-v1-test-key"
            )

            assertEquals("GET", request.method)
            assertEquals("https://openrouter.ai/api/v1/credits", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertEquals("Bearer sk-or-v1-test-key", request.header("Authorization"))
        }

        @Test
        @DisplayName("Should build GET request with provisioning key authentication")
        fun shouldBuildGetRequestWithProvisioningKey() {
            val request = OpenRouterRequestBuilder.buildGetRequest(
                url = "https://openrouter.ai/api/v1/keys",
                authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                authToken = "sk-or-v1-prov-key"
            )

            assertEquals("GET", request.method)
            assertEquals("https://openrouter.ai/api/v1/keys", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertEquals("Bearer sk-or-v1-prov-key", request.header("Authorization"))
        }
    }

    @Nested
    @DisplayName("POST Request Tests")
    inner class PostRequestTests {

        @Test
        @DisplayName("Should build POST request with JSON body and API key")
        fun shouldBuildPostRequestWithApiKey() {
            val jsonBody = """{"model":"openai/gpt-4","messages":[{"role":"user","content":"Hello"}]}"""

            val request = OpenRouterRequestBuilder.buildPostRequest(
                url = "https://openrouter.ai/api/v1/chat/completions",
                jsonBody = jsonBody,
                authType = OpenRouterRequestBuilder.AuthType.API_KEY,
                authToken = "sk-or-v1-test-key"
            )

            assertEquals("POST", request.method)
            assertEquals("https://openrouter.ai/api/v1/chat/completions", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertEquals("Bearer sk-or-v1-test-key", request.header("Authorization"))
            assertNotNull(request.body)
        }

        @Test
        @DisplayName("Should build POST request with provisioning key")
        fun shouldBuildPostRequestWithProvisioningKey() {
            val jsonBody = """{"name":"Test API Key"}"""

            val request = OpenRouterRequestBuilder.buildPostRequest(
                url = "https://openrouter.ai/api/v1/keys",
                jsonBody = jsonBody,
                authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                authToken = "sk-or-v1-prov-key"
            )

            assertEquals("POST", request.method)
            assertEquals("https://openrouter.ai/api/v1/keys", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertEquals("Bearer sk-or-v1-prov-key", request.header("Authorization"))
            assertNotNull(request.body)
        }
    }

    @Nested
    @DisplayName("DELETE Request Tests")
    inner class DeleteRequestTests {

        @Test
        @DisplayName("Should build DELETE request with provisioning key")
        fun shouldBuildDeleteRequestWithProvisioningKey() {
            val request = OpenRouterRequestBuilder.buildDeleteRequest(
                url = "https://openrouter.ai/api/v1/keys/test-hash",
                authType = OpenRouterRequestBuilder.AuthType.PROVISIONING_KEY,
                authToken = "sk-or-v1-prov-key"
            )

            assertEquals("DELETE", request.method)
            assertEquals("https://openrouter.ai/api/v1/keys/test-hash", request.url.toString())
            assertEquals("application/json", request.header("Content-Type"))
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", request.header("HTTP-Referer"))
            assertEquals("OpenRouter IntelliJ Plugin", request.header("X-Title"))
            assertEquals("Bearer sk-or-v1-prov-key", request.header("Authorization"))
        }
    }

    @Nested
    @DisplayName("Standard Headers Tests")
    inner class StandardHeadersTests {

        @Test
        @DisplayName("Should return standard headers without auth token")
        fun shouldReturnStandardHeadersWithoutAuth() {
            val headers = OpenRouterRequestBuilder.getStandardHeaders()

            assertEquals("application/json", headers["Content-Type"])
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", headers["HTTP-Referer"])
            assertEquals("OpenRouter IntelliJ Plugin", headers["X-Title"])
            assertFalse(headers.containsKey("Authorization"))
        }

        @Test
        @DisplayName("Should return standard headers with auth token")
        fun shouldReturnStandardHeadersWithAuth() {
            val headers = OpenRouterRequestBuilder.getStandardHeaders("sk-or-v1-test-key")

            assertEquals("application/json", headers["Content-Type"])
            assertEquals("https://github.com/DimazzzZ/openrouter-intellij-plugin", headers["HTTP-Referer"])
            assertEquals("OpenRouter IntelliJ Plugin", headers["X-Title"])
            assertEquals("Bearer sk-or-v1-test-key", headers["Authorization"])
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    inner class ConfigurationTests {

        @Test
        @DisplayName("Should return correct configuration values")
        fun shouldReturnCorrectConfigValues() {
            assertEquals(
                "https://github.com/DimazzzZ/openrouter-intellij-plugin",
                OpenRouterRequestBuilder.Config.getHttpReferer()
            )
            assertEquals(
                "OpenRouter IntelliJ Plugin",
                OpenRouterRequestBuilder.Config.getXTitle()
            )
            assertEquals(
                "application/json",
                OpenRouterRequestBuilder.Config.getContentType()
            )
        }
    }
}
