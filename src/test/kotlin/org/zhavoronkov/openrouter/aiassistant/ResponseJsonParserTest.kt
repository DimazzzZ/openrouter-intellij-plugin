package org.zhavoronkov.openrouter.aiassistant

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ResponseJsonParser Tests")
class ResponseJsonParserTest {

    @Nested
    @DisplayName("getAsJsonObjectOrNull")
    inner class GetAsJsonObjectOrNullTests {

        @Test
        fun `returns JsonObject when member exists and is object`() {
            val json = JsonObject().apply {
                add("nested", JsonObject().apply {
                    addProperty("key", "value")
                })
            }
            
            val result = ResponseJsonParser.getAsJsonObjectOrNull(json, "nested")
            
            assertNotNull(result)
            assertEquals("value", result?.get("key")?.asString)
        }

        @Test
        fun `returns null when member does not exist`() {
            val json = JsonObject()
            
            val result = ResponseJsonParser.getAsJsonObjectOrNull(json, "missing")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is not an object`() {
            val json = JsonObject().apply {
                addProperty("notObject", "string value")
            }
            
            val result = ResponseJsonParser.getAsJsonObjectOrNull(json, "notObject")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is array`() {
            val json = JsonObject().apply {
                add("array", JsonArray())
            }
            
            val result = ResponseJsonParser.getAsJsonObjectOrNull(json, "array")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is null`() {
            val json = JsonObject().apply {
                add("nullValue", null)
            }
            
            val result = ResponseJsonParser.getAsJsonObjectOrNull(json, "nullValue")
            
            assertNull(result)
        }
    }

    @Nested
    @DisplayName("getAsJsonArrayOrNull")
    inner class GetAsJsonArrayOrNullTests {

        @Test
        fun `returns JsonArray when member exists and is array`() {
            val json = JsonObject().apply {
                add("items", JsonArray().apply {
                    add("item1")
                    add("item2")
                })
            }
            
            val result = ResponseJsonParser.getAsJsonArrayOrNull(json, "items")
            
            assertNotNull(result)
            assertEquals(2, result?.size())
        }

        @Test
        fun `returns null when member does not exist`() {
            val json = JsonObject()
            
            val result = ResponseJsonParser.getAsJsonArrayOrNull(json, "missing")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is not an array`() {
            val json = JsonObject().apply {
                addProperty("notArray", "string value")
            }
            
            val result = ResponseJsonParser.getAsJsonArrayOrNull(json, "notArray")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is object`() {
            val json = JsonObject().apply {
                add("object", JsonObject())
            }
            
            val result = ResponseJsonParser.getAsJsonArrayOrNull(json, "object")
            
            assertNull(result)
        }

        @Test
        fun `returns empty array when member is empty array`() {
            val json = JsonObject().apply {
                add("emptyArray", JsonArray())
            }
            
            val result = ResponseJsonParser.getAsJsonArrayOrNull(json, "emptyArray")
            
            assertNotNull(result)
            assertEquals(0, result?.size())
        }
    }

    @Nested
    @DisplayName("getAsStringOrNull")
    inner class GetAsStringOrNullTests {

        @Test
        fun `returns string when member exists and is primitive string`() {
            val json = JsonObject().apply {
                addProperty("name", "test value")
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "name")
            
            assertEquals("test value", result)
        }

        @Test
        fun `returns null when member does not exist`() {
            val json = JsonObject()
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "missing")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is object`() {
            val json = JsonObject().apply {
                add("object", JsonObject())
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "object")
            
            assertNull(result)
        }

        @Test
        fun `returns null when member is array`() {
            val json = JsonObject().apply {
                add("array", JsonArray())
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "array")
            
            assertNull(result)
        }

        @Test
        fun `returns string representation for numeric primitives`() {
            val json = JsonObject().apply {
                addProperty("number", 42)
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "number")
            
            assertEquals("42", result)
        }

        @Test
        fun `returns string representation for boolean primitives`() {
            val json = JsonObject().apply {
                addProperty("flag", true)
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "flag")
            
            assertEquals("true", result)
        }

        @Test
        fun `returns empty string when value is empty`() {
            val json = JsonObject().apply {
                addProperty("empty", "")
            }
            
            val result = ResponseJsonParser.getAsStringOrNull(json, "empty")
            
            assertEquals("", result)
        }
    }

    @Nested
    @DisplayName("Complex JSON scenarios")
    inner class ComplexJsonTests {

        @Test
        fun `handles deeply nested structures`() {
            val json = JsonObject().apply {
                add("level1", JsonObject().apply {
                    add("level2", JsonObject().apply {
                        addProperty("value", "deep")
                    })
                })
            }
            
            val level1 = ResponseJsonParser.getAsJsonObjectOrNull(json, "level1")
            assertNotNull(level1)
            
            val level2 = ResponseJsonParser.getAsJsonObjectOrNull(level1!!, "level2")
            assertNotNull(level2)
            
            val value = ResponseJsonParser.getAsStringOrNull(level2!!, "value")
            assertEquals("deep", value)
        }

        @Test
        fun `handles mixed content types`() {
            val json = JsonObject().apply {
                addProperty("string", "text")
                addProperty("number", 123)
                add("object", JsonObject())
                add("array", JsonArray())
            }
            
            assertEquals("text", ResponseJsonParser.getAsStringOrNull(json, "string"))
            assertEquals("123", ResponseJsonParser.getAsStringOrNull(json, "number"))
            assertNotNull(ResponseJsonParser.getAsJsonObjectOrNull(json, "object"))
            assertNotNull(ResponseJsonParser.getAsJsonArrayOrNull(json, "array"))
        }
    }
}
