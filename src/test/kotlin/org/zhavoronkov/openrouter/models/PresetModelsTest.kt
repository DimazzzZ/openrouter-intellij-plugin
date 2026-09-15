package org.zhavoronkov.openrouter.models

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Preset API Model Tests")
class PresetModelsTest {

    private val gson = Gson()

    // Canonical bodies captured live from GET /presets and GET /presets/{slug}.
    private val listJson = """
        {"data":[
          {"id":"712ad1cb-7e25-42b8-9316-ea26739bfc59","creator_user_id":null,
           "workspace_id":"5f697d8a-f9e5-5092-a766-72680d5602c5","name":"test2",
           "slug":"test2","description":null,"status":"active",
           "designated_version_id":"01cc3583-31ac-4bb8-8845-87837e560a47",
           "created_at":"2026-03-26T16:50:43.440Z","updated_at":"2026-03-26T16:50:43.560Z",
           "status_updated_at":"2026-03-26T16:50:43.440Z"}
        ]}
    """.trimIndent()

    private val oneJson = """
        {"data":{
          "id":"dda2e635-cd89-4bdf-8855-107a96a9b533",
          "creator_user_id":"user_2zBswGniDoVLAmPLJoJpuAw0b3h",
          "workspace_id":"5f697d8a-f9e5-5092-a766-72680d5602c5",
          "name":"probe","slug":"probe","description":null,"status":"active",
          "designated_version_id":"1a9cd82d-4877-41f9-8e63-57bb998e372c",
          "created_at":"2026-09-14T17:33:36.959Z","updated_at":"2026-09-14T17:33:37.343Z",
          "status_updated_at":"2026-09-14T17:33:36.959Z",
          "designated_version":{
            "id":"1a9cd82d-4877-41f9-8e63-57bb998e372c",
            "preset_id":"dda2e635-cd89-4bdf-8855-107a96a9b533",
            "creator_id":"user_2zBswGniDoVLAmPLJoJpuAw0b3h","version":1,
            "system_prompt":null,
            "config":{"model":"openrouter/auto","temperature":0.5,"tools":[]},
            "created_at":"2026-09-14T17:33:37.159993+00:00",
            "updated_at":"2026-09-14T17:33:37.159993+00:00"}
        }}
    """.trimIndent()

    @Nested
    @DisplayName("ListPresetsResponse")
    inner class ListTests {
        @Test
        @DisplayName("parses list items with nullable creator_user_id and no version")
        fun parsesList() {
            val resp = gson.fromJson(listJson, ListPresetsResponse::class.java)
            assertEquals(1, resp.data.size)
            val p = resp.data[0]
            assertEquals("test2", p.slug)
            assertEquals("test2", p.name)
            assertEquals("active", p.status)
            assertNull(p.creatorUserId, "creator_user_id is null on list rows")
            assertNull(p.designatedVersion, "list rows omit designated_version")
            assertEquals("01cc3583-31ac-4bb8-8845-87837e560a47", p.designatedVersionId)
        }
    }

    @Nested
    @DisplayName("GetPresetResponse")
    inner class OneTests {
        @Test
        @DisplayName("parses single preset with designated_version and untyped config")
        fun parsesOne() {
            val resp = gson.fromJson(oneJson, GetPresetResponse::class.java)
            val p = resp.data
            assertEquals("probe", p.slug)
            assertEquals("user_2zBswGniDoVLAmPLJoJpuAw0b3h", p.creatorUserId)
            val v = p.designatedVersion!!
            assertEquals(1, v.version)
            assertNull(v.systemPrompt)
            assertEquals("openrouter/auto", v.config?.get("model"))
            assertEquals(0.5, v.config?.get("temperature"))
        }

        @Test
        @DisplayName("preserves unknown config keys as untyped passthrough")
        fun preservesUnknownConfigKeys() {
            val configJson = """{"model":"openrouter/auto","temperature":0.5,""" +
                """"top_p":0.9,"provider":{"order":["anthropic"]},"tools":[]}"""
            val withExtra = oneJson.replace(
                """"config":{"model":"openrouter/auto","temperature":0.5,"tools":[]}""",
                "\"config\":$configJson"
            )
            val resp = gson.fromJson(withExtra, GetPresetResponse::class.java)
            val config = resp.data.designatedVersion!!.config!!
            assertTrue(config.containsKey("top_p"), "unknown scalar key preserved")
            assertTrue(config.containsKey("provider"), "unknown nested key preserved")
        }
    }
}
