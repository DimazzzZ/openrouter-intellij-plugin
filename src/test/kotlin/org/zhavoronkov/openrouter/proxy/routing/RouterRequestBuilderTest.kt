package org.zhavoronkov.openrouter.proxy.routing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * RouterRequestBuilder is the pure seam between the chat UI (which owns a
 * model slug + the user's chosen param value as a raw String) and the typed
 * request. Given those two inputs it returns the plugins list to attach to
 * ChatCompletionRequest, or null when nothing should be attached.
 *
 * Keeping it pure keeps ChatPanel's Swing code thin and lets us test the
 * table-driven behaviour without the IntelliJ platform fixture. All router
 * knowledge comes from RouterCatalog; this builder has no per-router branch.
 */
@DisplayName("RouterRequestBuilder Tests")
class RouterRequestBuilderTest {

    @Nested
    @DisplayName("Non-routers")
    inner class NonRouters {
        @Test
        @DisplayName("returns null for an ordinary model regardless of value")
        fun ordinaryModel() {
            assertNull(RouterRequestBuilder.buildPlugins("anthropic/claude-sonnet-4.5", "high"))
            assertNull(RouterRequestBuilder.buildPlugins("anthropic/claude-sonnet-4.5", null))
        }
    }

    @Nested
    @DisplayName("Parameterless routers")
    inner class Parameterless {
        @Test
        @DisplayName("fusion-flash and free attach no plugin block")
        fun attachNoPluginBlock() {
            assertNull(RouterRequestBuilder.buildPlugins("openrouter/fusion-flash", null))
            assertNull(RouterRequestBuilder.buildPlugins("openrouter/free", "ignored"))
        }
    }

    @Nested
    @DisplayName("Enum-param routers")
    inner class EnumParam {
        @Test
        @DisplayName("auto emits auto-router plugin with cost_tier string")
        fun autoCostTier() {
            val plugins = RouterRequestBuilder.buildPlugins("openrouter/auto", "medium")!!
            assertEquals(1, plugins.size)
            assertEquals("auto-router", plugins[0].id)
            assertEquals("medium", plugins[0].params["cost_tier"])
        }

        @Test
        @DisplayName("auto with no chosen value attaches no param (OpenRouter default)")
        fun autoNoValue() {
            val plugins = RouterRequestBuilder.buildPlugins("openrouter/auto", null)
            assertNull(plugins)
        }

        @Test
        @DisplayName("auto rejects a value outside the closed enum")
        fun autoInvalidValue() {
            // 'ultra' is not in [low,medium,high,xhigh,max]; treat as no selection
            val plugins = RouterRequestBuilder.buildPlugins("openrouter/auto", "ultra")
            assertNull(plugins)
        }

        @Test
        @DisplayName("fusion (editable enum) accepts an arbitrary preset string")
        fun fusionEditable() {
            val plugins = RouterRequestBuilder.buildPlugins("openrouter/fusion", "my-custom-preset")!!
            assertEquals("fusion", plugins[0].id)
            assertEquals("my-custom-preset", plugins[0].params["preset"])
        }
    }

    @Nested
    @DisplayName("Float-range routers")
    inner class FloatParam {
        @Test
        @DisplayName("pareto coerces the value to a Double in range")
        fun paretoInRange() {
            val plugins = RouterRequestBuilder.buildPlugins("openrouter/pareto-code", "0.8")!!
            assertEquals("pareto-router", plugins[0].id)
            assertEquals(0.8, plugins[0].params["min_coding_score"])
        }

        @Test
        @DisplayName("pareto rejects out-of-range and non-numeric values as no selection")
        fun paretoInvalid() {
            assertNull(RouterRequestBuilder.buildPlugins("openrouter/pareto-code", "1.5"))
            assertNull(RouterRequestBuilder.buildPlugins("openrouter/pareto-code", "abc"))
            assertNull(RouterRequestBuilder.buildPlugins("openrouter/pareto-code", null))
        }
    }

    @Nested
    @DisplayName("Resolved-model echo label")
    inner class ResolvedModelEcho {
        @Test
        @DisplayName("router request echoes the underlying model OpenRouter picked")
        fun routerEcho() {
            val label = RouterRequestBuilder.resolvedModelLabel(
                requestedModel = "openrouter/auto",
                responseModel = "anthropic/claude-sonnet-4.5"
            )
            assertEquals("Routed to anthropic/claude-sonnet-4.5", label)
        }

        @Test
        @DisplayName("no label for an ordinary (non-router) model")
        fun nonRouterNoEcho() {
            assertNull(
                RouterRequestBuilder.resolvedModelLabel(
                    requestedModel = "anthropic/claude-sonnet-4.5",
                    responseModel = "anthropic/claude-sonnet-4.5"
                )
            )
        }

        @Test
        @DisplayName("no label when the router response omits the model")
        fun missingResponseModel() {
            assertNull(
                RouterRequestBuilder.resolvedModelLabel(
                    requestedModel = "openrouter/auto",
                    responseModel = null
                )
            )
        }

        @Test
        @DisplayName("no label when router response echoes the router slug unchanged")
        fun unchangedSlug() {
            assertNull(
                RouterRequestBuilder.resolvedModelLabel(
                    requestedModel = "openrouter/auto",
                    responseModel = "openrouter/auto"
                )
            )
        }
    }

    @Nested
    @DisplayName("paramControlUpdate (preserve value across stray refreshes)")
    inner class ParamControlUpdateCases {
        @Test
        @DisplayName("non-router hides the control and rebuilds nothing")
        fun nonRouterHides() {
            val u = RouterRequestBuilder.paramControlUpdate(shownParamKey = null, selectedModel = "openai/gpt-4o")
            assertFalse(u.visible)
            assertFalse(u.rebuild)
            assertNull(u.paramKey)
        }

        @Test
        @DisplayName("parameterless router hides the control")
        fun parameterlessHides() {
            val u = RouterRequestBuilder.paramControlUpdate(shownParamKey = null, selectedModel = "openrouter/free")
            assertFalse(u.visible)
            assertFalse(u.rebuild)
            assertNull(u.paramKey)
        }

        @Test
        @DisplayName("first time a router param is shown, rebuild")
        fun firstShowRebuilds() {
            val u = RouterRequestBuilder.paramControlUpdate(shownParamKey = null, selectedModel = "openrouter/auto")
            assertTrue(u.visible)
            assertTrue(u.rebuild)
            assertEquals("cost_tier", u.paramKey)
        }

        @Test
        @DisplayName("same param already shown does NOT rebuild (preserves the user's value)")
        fun sameParamPreserves() {
            val u = RouterRequestBuilder.paramControlUpdate(
                shownParamKey = "cost_tier",
                selectedModel = "openrouter/auto"
            )
            assertTrue(u.visible)
            assertFalse(u.rebuild)
            assertEquals("cost_tier", u.paramKey)
        }

        @Test
        @DisplayName("switching to a router with a different param rebuilds")
        fun differentParamRebuilds() {
            val u = RouterRequestBuilder.paramControlUpdate(
                shownParamKey = "cost_tier",
                selectedModel = "openrouter/fusion"
            )
            assertTrue(u.visible)
            assertTrue(u.rebuild)
            assertEquals("preset", u.paramKey)
        }
    }
}
