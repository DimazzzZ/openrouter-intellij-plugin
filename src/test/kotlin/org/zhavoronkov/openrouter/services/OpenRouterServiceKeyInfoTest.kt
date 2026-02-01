package org.zhavoronkov.openrouter.services

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse
import org.zhavoronkov.openrouter.models.ApiResult

@DisplayName("OpenRouter Service Key Info Tests")
class OpenRouterServiceKeyInfoTest {

    private lateinit var service: OpenRouterService
    private lateinit var mockSettingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        service = OpenRouterService(settingsService = mockSettingsService)
    }

    @Test
    fun `getKeyInfo should summarize enabled keys`() = runBlocking {
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")

        val keys = listOf(
            ApiKeyInfo(
                name = "Key 1",
                label = "sk-or-v1-1",
                limit = 10.0,
                usage = 2.0,
                disabled = false,
                createdAt = "2025-01-01",
                updatedAt = null,
                hash = "hash-1"
            ),
            ApiKeyInfo(
                name = "Key 2",
                label = "sk-or-v1-2",
                limit = 30.0,
                usage = 5.0,
                disabled = false,
                createdAt = "2025-01-01",
                updatedAt = null,
                hash = "hash-2"
            ),
            ApiKeyInfo(
                name = "Disabled",
                label = "sk-or-v1-3",
                limit = 999.0,
                usage = 999.0,
                disabled = true,
                createdAt = "2025-01-01",
                updatedAt = null,
                hash = "hash-3"
            )
        )

        val response = ApiKeysListResponse(keys)
        val serviceSpy = org.mockito.Mockito.spy(service)
        org.mockito.Mockito.doReturn(ApiResult.Success(response, 200))
            .`when`(serviceSpy)
            .getApiKeysList("pk-test")

        val result = serviceSpy.getKeyInfo()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals("All Keys Summary", success.data.data.label)
        assertEquals(7.0, success.data.data.usage)
        assertEquals(40.0, success.data.data.limit)
    }
}
