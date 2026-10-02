package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * The response models read from OpenRouter's own spelling of each field. A misspelt
 * `@SerializedName` reads as a silently missing value, so each field is read back by name rather
 * than compared whole: a data class's equals would not notice a field Gson never filled.
 */
@DisplayName("OpenRouter response models on the wire")
class OpenRouterModelsWireTest {

    private val gson = Gson()

    private inline fun <reified T> parse(json: String): T = gson.fromJson(json, T::class.java)

    @Test
    @DisplayName("a generation record")
    fun generation() {
        val generation = parse<GenerationResponse>(
            """{"id":"gen-1","model":"openai/gpt-4o","created":1700000000,"total_cost":0.42,"usage":null}"""
        )

        assertEquals("gen-1", generation.id)
        assertEquals("openai/gpt-4o", generation.model)
        assertEquals(1_700_000_000L, generation.created)
        assertEquals(0.42, generation.totalCost)
    }

    @Test
    @DisplayName("a key's rate limit and free-model allowance")
    fun keyAllowances() {
        val key = parse<KeyData>(
            """{"rate_limit":{"requests":20,"interval":"10s","note":"n"},
                "free_model_daily_requests":{"limit":50,"used":3,"remaining":47}}"""
        )

        val rate = key.rateLimit!!
        assertEquals(20, rate.requests)
        assertEquals("10s", rate.interval)
        assertEquals("n", rate.note)
        val free = key.freeModelDailyRequests!!
        assertEquals(50, free.limit)
        assertEquals(3, free.used)
        assertEquals(47, free.remaining)
    }

    @Test
    @DisplayName("a preset as one read returns it")
    fun preset() {
        val preset = parse<GetPresetResponse>(
            """{"data":{"id":"p1","name":"Research","slug":"research","status":"active","description":"d",
                "creator_user_id":"u1","workspace_id":"w1","designated_version_id":"v1",
                "created_at":"2026-01-01","updated_at":"2026-01-02","status_updated_at":"2026-01-03"}}"""
        ).data

        assertEquals("p1", preset.id)
        assertEquals("Research", preset.name)
        assertEquals("research", preset.slug)
        assertEquals("d", preset.description)
        assertEquals("w1", preset.workspaceId)
        assertEquals("2026-01-01", preset.createdAt)
        assertEquals("2026-01-02", preset.updatedAt)
        assertEquals("2026-01-03", preset.statusUpdatedAt)
    }

    @Test
    @DisplayName("a key just created, with its value at the root, and a deleted one")
    fun keyCreatedAndDeleted() {
        val created = parse<CreateApiKeyResponse>(
            """{"key":"sk-or-v1-new","data":{"name":"IntelliJ","label":"sk-or-v1-ne...","limit":10.0,"usage":0.5,
                "disabled":false,"created_at":"2026-01-01","updated_at":null,"hash":"h1"}}"""
        )

        assertEquals("sk-or-v1-new", created.key)
        val info = created.data
        assertEquals("IntelliJ", info.name)
        assertEquals("sk-or-v1-ne...", info.label)
        assertEquals(10.0, info.limit)
        assertEquals(0.5, info.usage)
        assertFalse(info.disabled)
        assertEquals("2026-01-01", info.createdAt)
        assertNull(info.updatedAt)
        assertEquals("h1", info.hash)
        assertTrue(parse<DeleteApiKeyResponse>("""{"deleted":true}""").deleted)
    }

    @Test
    @DisplayName("a provider's links")
    fun provider() {
        val provider = parse<ProvidersResponse>(
            """{"data":[{"name":"Azure","slug":"azure","privacy_policy_url":"p","terms_of_service_url":"t",
                "status_page_url":"s"}]}"""
        ).data.single()

        assertEquals("Azure", provider.name)
        assertEquals("azure", provider.slug)
        assertEquals("p", provider.privacyPolicyUrl)
        assertEquals("t", provider.termsOfServiceUrl)
        assertEquals("s", provider.statusPageUrl)
    }

    @Test
    @DisplayName("a model's top provider")
    fun topProvider() {
        val top = parse<TopProvider>("""{"is_moderated":true,"context_length":128000,"max_completion_tokens":4096}""")

        assertEquals(true, top.isModerated)
        assertEquals(128_000, top.contextLength)
        assertEquals(4096, top.maxCompletionTokens)
    }

    @Test
    @DisplayName("the key an auth code is exchanged for")
    fun exchangedKey() {
        assertEquals("sk-or-v1-x", parse<ExchangeAuthCodeResponse>("""{"key":"sk-or-v1-x"}""").key)
    }

    /**
     * A value built in code is written in OpenRouter's spelling and reads back as itself: a test
     * double or fixture built from these classes says on the wire what the server says.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("builtResponses")
    @DisplayName("a response built in code is written in OpenRouter's spelling and reads back equal")
    fun roundTrip(label: String, value: Any, wireKeys: List<String>) {
        val json = gson.toJson(value)
        val keys = JsonParser.parseString(json).asJsonObject.keySet()

        assertTrue(keys.containsAll(wireKeys), "$label wrote $keys, expected $wireKeys among them")
        assertEquals(value, gson.fromJson(json, value.javaClass), label)
    }

    companion object {
        @JvmStatic
        fun builtResponses(): List<Arguments> = listOf(
            Arguments.of(
                "preset read",
                GetPresetResponse(Preset(id = "p1", name = "Research", slug = "research")),
                listOf("data")
            ),
            Arguments.of(
                "created key",
                CreateApiKeyResponse(
                    data = CreatedApiKeyInfo(
                        name = "n",
                        label = "l",
                        limit = 5.0,
                        usage = 0.5,
                        disabled = false,
                        createdAt = "2026-01-01",
                        updatedAt = null,
                        hash = "h"
                    ),
                    key = "sk-or-v1-x"
                ),
                listOf("data", "key")
            ),
            Arguments.of(
                "created key info",
                CreatedApiKeyInfo("n", "l", null, 0.0, true, "2026-01-01", "2026-01-02", "h"),
                listOf("created_at", "updated_at", "hash", "disabled")
            ),
            Arguments.of("deleted key", DeleteApiKeyResponse(deleted = true), listOf("deleted")),
            Arguments.of(
                "provider",
                ProviderInfo("Azure", "azure", "p", "t", "s"),
                listOf("privacy_policy_url", "terms_of_service_url", "status_page_url")
            ),
            Arguments.of("exchanged key", ExchangeAuthCodeResponse("sk-or-v1-x"), listOf("key")),
            Arguments.of(
                "analytics query",
                AnalyticsQueryResponse(
                    AnalyticsQueryPayload(
                        data = listOf(mapOf("model" to "m", "requests" to 2.0)),
                        metadata = AnalyticsMetadata(rowCount = 1, truncated = false)
                    )
                ),
                listOf("data")
            ),
            Arguments.of(
                "analytics metadata",
                AnalyticsMetadata(rowCount = 3, truncated = true),
                listOf("row_count", "truncated")
            ),
            Arguments.of(
                "analytics meta",
                AnalyticsMetaResponse(
                    AnalyticsMeta(
                        metrics = listOf(AnalyticsMetric("cost", "Cost", "currency")),
                        dimensions = listOf(AnalyticsDimension("model", "Model")),
                        granularities = listOf("day")
                    )
                ),
                listOf("data")
            )
        )
    }
}
