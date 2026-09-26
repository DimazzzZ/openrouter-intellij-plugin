package org.zhavoronkov.openrouter.proxy.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Contract for the declarative routers table. Every property downstream
 * code needs — the model slug, the OpenRouter plugin id, the single
 * primary parameter (kind + allowed values + optional default) — is
 * expressed once, here. Injector, chat UI and settings all read from
 * this table; no router-specific branches elsewhere.
 */
@DisplayName("RouterCatalog Tests")
class RouterCatalogTest {

    @Nested
    @DisplayName("Membership")
    inner class Membership {
        @Test
        @DisplayName("lists auto, fusion, pareto-code, fusion-flash and free as routers")
        fun listsExpectedSlugs() {
            val slugs = RouterCatalog.all.map { it.modelSlug }.toSet()
            assertTrue(slugs.contains("openrouter/auto"))
            assertTrue(slugs.contains("openrouter/fusion"))
            assertTrue(slugs.contains("openrouter/fusion-flash"))
            assertTrue(slugs.contains("openrouter/pareto-code"))
            assertTrue(slugs.contains("openrouter/free"))
        }

        @Test
        @DisplayName("isRouter recognises catalog slugs and rejects unknown models")
        fun recognisesRouterSlugs() {
            assertTrue(RouterCatalog.isRouter("openrouter/auto"))
            assertTrue(RouterCatalog.isRouter("openrouter/pareto-code"))
            assertFalse(RouterCatalog.isRouter("anthropic/claude-sonnet-4.5"))
            assertFalse(RouterCatalog.isRouter(""))
            assertFalse(RouterCatalog.isRouter("openrouter/auto-beta")) // beta is not a v1 target
        }

        @Test
        @DisplayName("find returns definition by slug or null")
        fun findBySlug() {
            assertNotNull(RouterCatalog.find("openrouter/fusion"))
            assertNull(RouterCatalog.find("mistralai/mixtral"))
        }
    }

    @Nested
    @DisplayName("Parameter shape")
    inner class ParameterShape {
        @Test
        @DisplayName("auto has cost_tier as closed enum on auto-router plugin")
        fun autoCostTier() {
            val def = RouterCatalog.find("openrouter/auto")!!
            assertEquals("auto-router", def.pluginId)
            val p = def.param!!
            assertEquals("cost_tier", p.key)
            assertTrue(p is RouterParam.Enum)
            val enumParam = p as RouterParam.Enum
            assertEquals(listOf("low", "medium", "high", "xhigh", "max"), enumParam.values)
            assertFalse(enumParam.editable)
        }

        @Test
        @DisplayName("pareto has min_coding_score as float in [0.0, 1.0]")
        fun paretoScore() {
            val def = RouterCatalog.find("openrouter/pareto-code")!!
            assertEquals("pareto-router", def.pluginId)
            val p = def.param!! as RouterParam.FloatRange
            assertEquals("min_coding_score", p.key)
            assertEquals(0.0, p.min)
            assertEquals(1.0, p.max)
        }

        @Test
        @DisplayName("fusion has preset as editable enum with general-fast suggestion")
        fun fusionPreset() {
            val def = RouterCatalog.find("openrouter/fusion")!!
            assertEquals("fusion", def.pluginId)
            val p = def.param!! as RouterParam.Enum
            assertEquals("preset", p.key)
            assertTrue(p.editable)
            assertTrue(p.values.contains("general-fast"))
        }

        @Test
        @DisplayName("fusion-flash and free are parameterless routers")
        fun parameterlessRouters() {
            val flash = RouterCatalog.find("openrouter/fusion-flash")!!
            assertNull(flash.param)
            assertNull(flash.pluginId) // no plugin block needed

            val free = RouterCatalog.find("openrouter/free")!!
            assertNull(free.param)
            assertNull(free.pluginId)
        }
    }

    @Nested
    @DisplayName("Row invariant")
    inner class RowInvariant {
        @Test
        @DisplayName("every catalog row has pluginId and param both set or both null")
        fun catalogRowsUpholdInvariant() {
            RouterCatalog.all.forEach { def ->
                assertEquals(
                    def.pluginId == null,
                    def.param == null,
                    "'\${def.modelSlug}': pluginId and param must agree on nullability"
                )
            }
        }

        @Test
        @DisplayName("constructing a param without a pluginId is rejected")
        fun paramWithoutPluginRejected() {
            assertThrows(IllegalArgumentException::class.java) {
                RouterDefinition(
                    modelSlug = "openrouter/broken",
                    displayName = "Broken",
                    pluginId = null,
                    param = RouterParam.Enum(key = "x", values = listOf("a"))
                )
            }
        }

        @Test
        @DisplayName("constructing a pluginId without a param is rejected")
        fun pluginWithoutParamRejected() {
            assertThrows(IllegalArgumentException::class.java) {
                RouterDefinition(
                    modelSlug = "openrouter/broken",
                    displayName = "Broken",
                    pluginId = "broken",
                    param = null
                )
            }
        }
    }

    @Nested
    @DisplayName("slugs seed accessor")
    inner class SlugsAccessor {
        @Test
        @DisplayName("slugs mirrors catalog order and covers every router")
        fun slugsMatchCatalog() {
            assertEquals(RouterCatalog.all.map { it.modelSlug }, RouterCatalog.slugs)
        }

        @Test
        @DisplayName("slugs exposes fusion, fusion-flash and pareto-code so the UI can list them")
        fun slugsIncludeParamRouters() {
            val slugs = RouterCatalog.slugs
            assertTrue(slugs.contains("openrouter/fusion"))
            assertTrue(slugs.contains("openrouter/fusion-flash"))
            assertTrue(slugs.contains("openrouter/pareto-code"))
        }
    }
}
