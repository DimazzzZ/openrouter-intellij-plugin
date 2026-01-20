package org.zhavoronkov.openrouter.regression

import com.google.gson.Gson
import com.google.gson.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatChoice
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatCompletionRequest
import org.zhavoronkov.openrouter.proxy.models.OpenAIChatMessage
import org.zhavoronkov.openrouter.proxy.models.OpenAIModel
import org.zhavoronkov.openrouter.proxy.models.OpenAIPermission
import org.zhavoronkov.openrouter.proxy.models.OpenAIUsage

/**
 * Regression tests for Issue #1: OpenAI Model Parameter Naming with @SerializedName
 *
 * These tests ensure that the @SerializedName annotations correctly map camelCase
 * properties to snake_case JSON fields, preventing detekt warnings while maintaining
 * API compatibility.
 */
@DisplayName("Regression: OpenAI Model Serialization with @SerializedName")
class SerializationRegressionTest {

    private val gson = Gson()

    @Test
    @DisplayName("OpenAIChatCompletionRequest should serialize camelCase to snake_case")
    fun testRequestSerialization() {
        // Given: Request with camelCase properties
        val request = OpenAIChatCompletionRequest(
            model = "gpt-4",
            messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("test"))),
            maxTokens = 100,
            topP = 0.9,
            frequencyPenalty = 0.5,
            presencePenalty = 0.3
        )

        // When: Serialize to JSON
        val json = gson.toJson(request)

        // Then: JSON should have snake_case field names
        assertTrue(json.contains("\"max_tokens\":100"), "Should serialize maxTokens to max_tokens")
        assertTrue(json.contains("\"top_p\":0.9"), "Should serialize topP to top_p")
        assertTrue(json.contains("\"frequency_penalty\":0.5"), "Should serialize frequencyPenalty to frequency_penalty")
        assertTrue(json.contains("\"presence_penalty\":0.3"), "Should serialize presencePenalty to presence_penalty")

        // Should NOT contain camelCase in JSON
        assertFalse(json.contains("maxTokens"), "JSON should not contain camelCase maxTokens")
        assertFalse(json.contains("topP"), "JSON should not contain camelCase topP")
    }

    @Test
    @DisplayName("OpenAIChatCompletionRequest should deserialize snake_case to camelCase")
    fun testRequestDeserialization() {
        // Given: JSON with snake_case field names
        val json = """
            {
                "model": "gpt-4",
                "messages": [{"role": "user", "content": "test"}],
                "max_tokens": 100,
                "top_p": 0.9,
                "frequency_penalty": 0.5,
                "presence_penalty": 0.3
            }
        """.trimIndent()

        // When: Deserialize from JSON
        val request = gson.fromJson(json, OpenAIChatCompletionRequest::class.java)

        // Then: Properties should be accessible via camelCase
        assertEquals(100, request.maxTokens, "Should deserialize max_tokens to maxTokens")
        assertEquals(0.9, request.topP, "Should deserialize top_p to topP")
        assertEquals(0.5, request.frequencyPenalty, "Should deserialize frequency_penalty to frequencyPenalty")
        assertEquals(0.3, request.presencePenalty, "Should deserialize presence_penalty to presencePenalty")
    }

    @Test
    @DisplayName("OpenAIChatChoice should serialize finishReason to finish_reason")
    fun testChoiceSerialization() {
        // Given: Choice with camelCase property
        val choice = OpenAIChatChoice(
            index = 0,
            message = OpenAIChatMessage(role = "assistant", content = JsonPrimitive("response")),
            finishReason = "stop"
        )

        // When: Serialize to JSON
        val json = gson.toJson(choice)

        // Then: JSON should have snake_case field name
        assertTrue(json.contains("\"finish_reason\":\"stop\""), "Should serialize finishReason to finish_reason")
        assertFalse(json.contains("finishReason"), "JSON should not contain camelCase finishReason")
    }

    @Test
    @DisplayName("OpenAIUsage should serialize all token fields to snake_case")
    fun testUsageSerialization() {
        // Given: Usage with camelCase properties
        val usage = OpenAIUsage(
            promptTokens = 10,
            completionTokens = 20,
            totalTokens = 30
        )

        // When: Serialize to JSON
        val json = gson.toJson(usage)

        // Then: JSON should have snake_case field names
        assertTrue(json.contains("\"prompt_tokens\":10"), "Should serialize promptTokens to prompt_tokens")
        assertTrue(json.contains("\"completion_tokens\":20"), "Should serialize completionTokens to completion_tokens")
        assertTrue(json.contains("\"total_tokens\":30"), "Should serialize totalTokens to total_tokens")
    }

    @Test
    @DisplayName("OpenAIModel should serialize ownedBy to owned_by")
    fun testModelSerialization() {
        // Given: Model with camelCase property
        val model = OpenAIModel(
            id = "gpt-4",
            created = 1234567890,
            ownedBy = "openai"
        )

        // When: Serialize to JSON
        val json = gson.toJson(model)

        // Then: JSON should have snake_case field name
        assertTrue(json.contains("\"owned_by\":\"openai\""), "Should serialize ownedBy to owned_by")
        assertFalse(json.contains("ownedBy"), "JSON should not contain camelCase ownedBy")
    }

    @Test
    @DisplayName("OpenAIPermission should serialize all boolean fields to snake_case")
    fun testPermissionSerialization() {
        // Given: Permission with camelCase properties
        val permission = OpenAIPermission(
            id = "perm-123",
            created = 1234567890,
            allowCreateEngine = false,
            allowSampling = true,
            allowLogprobs = true,
            allowSearchIndices = false,
            allowView = true,
            allowFineTuning = false,
            isBlocking = false
        )

        // When: Serialize to JSON
        val json = gson.toJson(permission)

        // Then: JSON should have snake_case field names
        assertTrue(json.contains("\"allow_create_engine\":false"))
        assertTrue(json.contains("\"allow_sampling\":true"))
        assertTrue(json.contains("\"allow_logprobs\":true"))
        assertTrue(json.contains("\"allow_search_indices\":false"))
        assertTrue(json.contains("\"allow_view\":true"))
        assertTrue(json.contains("\"allow_fine_tuning\":false"))
        assertTrue(json.contains("\"is_blocking\":false"))
    }

    @Test
    @DisplayName("Round-trip serialization should preserve all values")
    fun testRoundTripSerialization() {
        // Given: Original request
        val original = OpenAIChatCompletionRequest(
            model = "gpt-4",
            messages = listOf(OpenAIChatMessage(role = "user", content = JsonPrimitive("test"))),
            maxTokens = 150,
            topP = 0.95,
            frequencyPenalty = 0.2,
            presencePenalty = 0.1
        )

        // When: Serialize and deserialize
        val json = gson.toJson(original)
        val deserialized = gson.fromJson(json, OpenAIChatCompletionRequest::class.java)

        // Then: All values should be preserved
        assertEquals(original.model, deserialized.model)
        assertEquals(original.maxTokens, deserialized.maxTokens)
        assertEquals(original.topP, deserialized.topP)
        assertEquals(original.frequencyPenalty, deserialized.frequencyPenalty)
        assertEquals(original.presencePenalty, deserialized.presencePenalty)
    }
}
