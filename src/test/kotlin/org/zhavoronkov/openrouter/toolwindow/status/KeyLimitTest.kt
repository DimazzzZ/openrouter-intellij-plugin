package org.zhavoronkov.openrouter.toolwindow.status

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ApiKeyInfo
import org.zhavoronkov.openrouter.models.ApiKeysListResponse

@DisplayName("KeyLimit")
class KeyLimitTest {

    private fun key(
        limit: Double?,
        usage: Double,
        disabled: Boolean = false,
        name: String = "key"
    ) = ApiKeyInfo(
        name = name,
        label = name,
        limit = limit,
        usage = usage,
        disabled = disabled,
        createdAt = "2026-01-01T00:00:00Z",
        updatedAt = null,
        hash = "hash-$name"
    )

    @Test
    @DisplayName("used and limit are summed only over the capped keys, never mixed with an uncapped key's usage")
    fun `capped and uncapped keys sum only over the capped set`() {
        val keys = ApiKeysListResponse(
            data = listOf(
                key(name = "capped", limit = 10.0, usage = 3.20),
                key(name = "uncapped", limit = null, usage = 50.0)
            )
        )

        val reading = KeyLimit.from(keys)

        checkNotNull(reading) { "a capped key exists, so a reading must be produced" }
        // The old getQuotaInfo() bug: totalLimit summed only the capped key ($10), but
        // totalUsed summed BOTH keys ($3.20 + $50.00 = $53.20), rendering "used $53.20 of a
        // $10.00 cap". This assertion pins `used` to the capped key's own usage only.
        assertEquals(
            3.20,
            reading.used,
            1e-9,
            "used must come from the capped key alone (3.20), not include the uncapped key's " +
                "50.0 usage as the old getQuotaInfo() did"
        )
        assertEquals(10.0, reading.limit, 1e-9)
    }

    @Test
    @DisplayName("no keys at all means no cap to show")
    fun `no keys returns null`() {
        assertNull(KeyLimit.from(ApiKeysListResponse(data = emptyList())))
    }

    @Test
    @DisplayName("a null key list (cache never populated) means no cap to show")
    fun `null key list returns null`() {
        assertNull(KeyLimit.from(null))
    }

    @Test
    @DisplayName("a disabled key's limit does not count, even though it is set")
    fun `all keys disabled returns null`() {
        val keys = ApiKeysListResponse(data = listOf(key(limit = 10.0, usage = 1.0, disabled = true)))

        assertNull(KeyLimit.from(keys))
    }

    @Test
    @DisplayName("every key uncapped (limit == null) means no cap to show")
    fun `all keys uncapped returns null`() {
        val keys = ApiKeysListResponse(
            data = listOf(
                key(limit = null, usage = 1.0, name = "a"),
                key(limit = null, usage = 2.0, name = "b")
            )
        )

        assertNull(KeyLimit.from(keys))
    }

    @Test
    @DisplayName("a limit of exactly 0.0 is treated as uncapped, not a real zero-spend cap")
    fun `a zero limit returns null`() {
        val keys = ApiKeysListResponse(data = listOf(key(limit = 0.0, usage = 0.0)))

        assertNull(KeyLimit.from(keys))
    }

    @Test
    @DisplayName("positive control: a single enabled key with a real positive limit produces a reading")
    fun `a single capped key produces a reading`() {
        val keys = ApiKeysListResponse(data = listOf(key(limit = 25.0, usage = 4.5)))

        val reading = KeyLimit.from(keys)

        checkNotNull(reading)
        assertEquals(4.5, reading.used, 1e-9)
        assertEquals(25.0, reading.limit, 1e-9)
    }

    @Test
    @DisplayName("multiple capped keys sum both used and limit together")
    fun `multiple capped keys sum together`() {
        val keys = ApiKeysListResponse(
            data = listOf(
                key(limit = 10.0, usage = 2.0, name = "a"),
                key(limit = 5.0, usage = 1.0, name = "b")
            )
        )

        val reading = KeyLimit.from(keys)

        checkNotNull(reading)
        assertEquals(3.0, reading.used, 1e-9)
        assertEquals(15.0, reading.limit, 1e-9)
    }
}
