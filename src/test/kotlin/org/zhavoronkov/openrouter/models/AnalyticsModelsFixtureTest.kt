package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Parses the analytics query response from a FIXTURE FILE rather than from a
 * string built here, so that replacing it with a payload captured from a live
 * account is dropping in one file. A mock written from the documentation proves
 * only that we read the documentation correctly — which is precisely the
 * failure this redesign exists to fix.
 *
 * To replace this fixture: capture the endpoint's response with a management key, strip anything
 * identifying, keep the structure, drop it in — no test changes required. The assertions check
 * server invariants that hold for any valid capture, not the literals this particular fixture carries.
 */
@DisplayName("Analytics models against the response fixture")
class AnalyticsModelsFixtureTest {

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) {
            "fixture /fixtures/$name is missing"
        }.bufferedReader().use { it.readText() }

    private fun parsed() = Gson().fromJson(
        fixture("analytics-query-response.json"),
        AnalyticsQueryResponse::class.java
    )

    @Test
    @DisplayName("the query response parses into rows and metadata")
    fun `the query response parses`() {
        val response = parsed()
        val rows = response.data.data

        assertTrue(rows.isNotEmpty(), "Row list must be non-empty")
        assertNotNull(response.data.metadata, "Metadata must be present")

        val metadata = response.data.metadata!!
        assertEquals(rows.size, metadata.rowCount, "rowCount must equal data.size")
        assertNotNull(metadata.truncated, "truncated must be present")

        rows.forEach { row ->
            assertTrue(row.isNotEmpty(), "Each row must have at least one entry")
        }
    }

    @Test
    @DisplayName("a row keeps its dimension and metric values under their server-side names")
    fun `a row keeps its server-side names`() {
        val rows = parsed().data.data

        // Assert that at least one row carries the dimension key from the query grouping.
        // This fixture is grouped by "model"; any valid capture of a model-grouped query holds this.
        assertTrue(
            rows.any { it.containsKey("model") },
            "At least one row must carry the dimension key 'model'"
        )
    }

    @Test
    @DisplayName("a metric value parses as a number, not as a string")
    fun `a metric value parses as a number`() {
        val rows = parsed().data.data

        val withUsage = rows.filter { it.containsKey("total_usage") }
        assertTrue(
            withUsage.isNotEmpty(),
            "no row carried the queried metric total_usage"
        )
        withUsage.forEach { row ->
            val value = row["total_usage"]
            assertTrue(
                value is Number,
                "total_usage should be a Number, was ${value?.javaClass?.simpleName}"
            )
        }

        val withCount = rows.filter { it.containsKey("request_count") }
        assertTrue(
            withCount.isNotEmpty(),
            "no row carried the queried metric request_count"
        )
        withCount.forEach { row ->
            val value = row["request_count"]
            assertTrue(
                value is Number,
                "request_count should be a Number, was ${value?.javaClass?.simpleName}"
            )
        }
    }

    @Test
    @DisplayName("a query request serialises with the API's snake_case field names")
    fun `a query request serialises with snake_case`() {
        val json = Gson().toJson(
            AnalyticsQueryRequest(
                metrics = listOf("total_usage"),
                dimensions = listOf("model"),
                granularity = "day",
                timeRange = AnalyticsTimeRange(
                    start = "2026-09-12T00:00:00Z",
                    end = "2026-09-19T00:00:00Z"
                ),
                limit = 1000,
                orderBy = AnalyticsOrderBy(field = "total_usage", direction = "desc")
            )
        )

        assertEquals(true, json.contains("\"time_range\""))
        assertEquals(true, json.contains("\"order_by\""))
        assertEquals(false, json.contains("\"timeRange\""))
    }
}
