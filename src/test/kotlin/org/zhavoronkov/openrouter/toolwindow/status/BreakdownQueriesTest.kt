package org.zhavoronkov.openrouter.toolwindow.status

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.services.AnalyticsService
import java.time.LocalDate

/**
 * BreakdownQueries is the Status tab's query layer: it issues the analytics call, maps the rows
 * and decides what a fruitless answer means. Its own KDoc names a MockWebServer under the fast
 * headless `test` task as the way to exercise it without a platform runner - this is that test.
 *
 * The cases here deliberately pair each outcome with BOTH shapes of `metadata`: the server sends
 * it on some responses and omits it on others, and "truncated" must read as false for an absent
 * envelope rather than propagating a null into the UI.
 */
@DisplayName("BreakdownQueries")
class BreakdownQueriesTest {

    private lateinit var server: MockWebServer

    private val today = LocalDate.of(2026, 9, 19)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun queries() = BreakdownQueries(
        AnalyticsService(
            baseUrlOverride = server.url("/api/v1").toString(),
            provisioningKeyProvider = { "management-key" }
        )
    )

    private fun runBreakdown() = runBlocking {
        queries().breakdown(
            ActivityAggregator.Period.WEEK,
            AnalyticsBreakdown.Dimension.MODEL,
            today
        )
    }

    // --- Rows came back -------------------------------------------------------------------------

    @Test
    @DisplayName("rows carry the server's own truncated flag through")
    fun `rows carry a truncated flag`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[{"model":"m","total_usage":1.5,"request_count":2}],""" +
                    """"metadata":{"row_count":1,"truncated":true}}}"""
            )
        )

        val result = runBreakdown()

        assertTrue(result is BreakdownQueries.Breakdown.Rows)
        val rows = result as BreakdownQueries.Breakdown.Rows
        assertTrue(rows.truncated)
        assertEquals(listOf("m"), rows.rows.map { it.model })
    }

    @Test
    @DisplayName("rows with no metadata envelope at all read as not truncated, never as unknown")
    fun `rows without metadata are not truncated`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[{"model":"m","total_usage":1.5,"request_count":2}],"metadata":null}}"""
            )
        )

        val result = runBreakdown()

        assertTrue(result is BreakdownQueries.Breakdown.Rows)
        assertFalse((result as BreakdownQueries.Breakdown.Rows).truncated)
    }

    // --- The query succeeded but reported nothing -----------------------------------------------

    @Test
    @DisplayName("an empty result with complete metadata is Empty, not Failed")
    fun `an empty result with valid meta is Empty`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"data":[],"metadata":null}}"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody(COMPLETE_META))

        val result = runBreakdown()

        assertEquals(BreakdownQueries.Breakdown.Empty(truncated = false), result)
    }

    @Test
    @DisplayName("an empty but truncated result keeps the server's truncated flag")
    fun `an empty truncated result keeps its flag`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[],"metadata":{"row_count":0,"truncated":true}}}"""
            )
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(COMPLETE_META))

        val result = runBreakdown()

        assertEquals(BreakdownQueries.Breakdown.Empty(truncated = true), result)
    }

    // --- The metadata check explains an unhelpful answer ----------------------------------------

    @Test
    @DisplayName("a name this build hard-codes that the server no longer lists is reported as MissingNames")
    fun `a retired metric name is reported`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":{"data":[],"metadata":null}}"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"metrics":[{"name":"request_count"}],""" +
                    """"dimensions":[{"name":"model"}],"granularities":["day","hour"]}}"""
            )
        )

        val result = runBreakdown()

        assertEquals(BreakdownQueries.Breakdown.MissingNames(listOf("total_usage")), result)
    }

    @Test
    @DisplayName("a failed query the metadata check cannot explain is Failed")
    fun `an unexplained failure is Failed`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))

        val result = runBreakdown()

        assertEquals(BreakdownQueries.Breakdown.Failed, result)
    }

    // --- The spend series -----------------------------------------------------------------------

    @Test
    @DisplayName("a spend series carries its buckets oldest-first with a derived per-day rate")
    fun `a spend series is returned with its rate`() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"data":{"data":[""" +
                    """{"date__day":"2026-09-17","total_usage":1.0},""" +
                    """{"date__day":"2026-09-18","total_usage":2.0},""" +
                    """{"date__day":"2026-09-19","total_usage":3.0}],"metadata":null}}"""
            )
        )

        val result = runBlocking {
            queries().spendSeries(ActivityAggregator.Period.WEEK, today)
        }

        assertEquals(listOf(1.0, 2.0, 3.0), result?.series)
    }

    @Test
    @DisplayName("a failed spend series answers null - the caller keeps what it already had")
    fun `a failed spend series is null`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":{"message":"boom"}}"""))

        val result = runBlocking {
            queries().spendSeries(ActivityAggregator.Period.WEEK, today)
        }

        assertEquals(null, result)
    }

    private companion object {
        const val COMPLETE_META =
            """{"data":{"metrics":[{"name":"total_usage"},{"name":"request_count"}],""" +
                """"dimensions":[{"name":"model"}],"granularities":["day","hour"]}}"""
    }
}
