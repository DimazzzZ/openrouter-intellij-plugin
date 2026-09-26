package org.zhavoronkov.openrouter.services

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.AnalyticsQueryRequest
import org.zhavoronkov.openrouter.models.AnalyticsTimeRange
import org.zhavoronkov.openrouter.models.ApiResult

@DisplayName("AnalyticsService")
class AnalyticsServiceTest {

    private lateinit var server: MockWebServer

    private fun request() = AnalyticsQueryRequest(
        metrics = listOf("total_usage"),
        dimensions = listOf("model"),
        granularity = "day",
        timeRange = AnalyticsTimeRange("2026-09-12T00:00:00Z", "2026-09-19T00:00:00Z")
    )

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    @DisplayName("a successful query returns its rows")
    fun `a successful query returns its rows`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[{"model":"m","total_usage":1.5}],"metadata":{"row_count":1,"truncated":false}}}"""
            )
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val result = service.query(request())

        assertTrue(result is ApiResult.Success)
        assertEquals(1, (result as ApiResult.Success).data.data.size)
    }

    @Test
    @DisplayName("the query is POSTed to /analytics/query with the management key")
    fun `the query is posted with the management key`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"data":[],"metadata":null}}"""))
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "secret" }
        )

        service.query(request())
        val recorded = server.takeRequest()

        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/analytics/query"), "got ${recorded.path}")
        assertEquals("Bearer secret", recorded.getHeader("Authorization"))
    }

    @Test
    @DisplayName("a 403 is reported as an error rather than an empty result")
    fun `a 403 is reported as an error`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":{"message":"Only management keys can perform this operation"}}""")
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val result = service.query(request())

        assertTrue(result is ApiResult.Error, "expected an error, got $result")
    }

    @Test
    @DisplayName("an empty period yields an empty row list, not an error")
    fun `an empty period yields an empty row list`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"data":[],"metadata":{"row_count":0,"truncated":false}}}""")
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val result = service.query(request())

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.data.isEmpty())
    }

    @Test
    @DisplayName("a truncated response is still a success and keeps the flag")
    fun `a truncated response keeps its flag`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"data":[{"model":"m"}],"metadata":{"row_count":1,"truncated":true}}}""")
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val result = service.query(request())

        assertTrue(result is ApiResult.Success)
        assertEquals(true, (result as ApiResult.Success).data.metadata?.truncated)
    }

    @Test
    @DisplayName("without a management key the service reports itself unavailable and makes no call")
    fun `without a key the service is unavailable`() = runBlocking {
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "" }
        )

        assertTrue(!service.isAvailable())
        assertTrue(service.query(request()) is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }

    @Test
    @DisplayName("meta parses the discovered metrics and granularities")
    fun `meta parses metrics and granularities`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"metrics":[{"name":"total_usage","display_label":"Spend","display_format":"currency"}],""" +
                    """"dimensions":[{"name":"model","display_label":"Model"}],"granularities":["hour","day"]}}"""
            )
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val result = service.meta()

        assertTrue(result is ApiResult.Success)
        val meta = (result as ApiResult.Success).data
        assertEquals("currency", meta.metrics.first().displayFormat)
        assertTrue(meta.granularities.contains("day"))
    }

    @Test
    @DisplayName("an equal repeated query is served from cache and hits the server once")
    fun `a repeated equal query hits the cache`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[{"model":"m","total_usage":1.5}],"metadata":{"row_count":1,"truncated":false}}}"""
            )
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        val first = service.query(request())
        val second = service.query(request())

        assertTrue(first is ApiResult.Success, "expected first to succeed, got $first")
        assertTrue(second is ApiResult.Success, "expected second to succeed, got $second")
        assertEquals((first as ApiResult.Success).data.data, (second as ApiResult.Success).data.data)
        assertEquals(1, server.requestCount)
    }

    @Test
    @DisplayName("invalidate() clears the cache so the next equal query hits the server again")
    fun `invalidate clears the cache`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"data":[{"model":"m"}],"metadata":{"row_count":1,"truncated":false}}}""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"data":{"data":[{"model":"m"}],"metadata":{"row_count":1,"truncated":false}}}""")
        )
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "k" }
        )

        service.query(request())
        service.invalidate()
        service.query(request())

        assertEquals(2, server.requestCount)
    }

    @Test
    @DisplayName("without a management key meta() reports itself unavailable and makes no call")
    fun `without a key meta is unavailable`() = runBlocking {
        val service = AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "" }
        )

        assertTrue(service.meta() is ApiResult.Error)
        assertEquals(0, server.requestCount)
    }
}
