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

@DisplayName("OpenRouter Service Quota Info Tests")
class OpenRouterServiceQuotaInfoTest {

    private lateinit var service: OpenRouterService
    private lateinit var mockSettingsService: OpenRouterSettingsService

    @BeforeEach
    fun setUp() {
        mockSettingsService = mock(OpenRouterSettingsService::class.java)
        service = OpenRouterService(settingsService = mockSettingsService)
    }

    @Test
    fun `should return error when provisioning key is blank`() = runBlocking {
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("")

        val result = service.getQuotaInfo()

        assertTrue(result is ApiResult.Error)
        val error = result as ApiResult.Error
        assertEquals("No provisioning key configured", error.message)
    }

    @Test
    fun `should summarize quota from enabled keys`() = runBlocking {
        `when`(mockSettingsService.getProvisioningKey()).thenReturn("pk-test")

        val keys = listOf(
            ApiKeyInfo(
                name = "Key 1",
                label = "sk-or-v1-1",
                limit = 100.0,
                usage = 25.0,
                disabled = false,
                createdAt = "2025-01-01",
                updatedAt = null,
                hash = "hash-1"
            ),
            ApiKeyInfo(
                name = "Key 2",
                label = "sk-or-v1-2",
                limit = 50.0,
                usage = 10.0,
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

        val result = serviceSpy.getQuotaInfo()

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(150.0, success.data.total)
        assertEquals(35.0, success.data.used)
        assertEquals(115.0, success.data.remaining)
    }
}
