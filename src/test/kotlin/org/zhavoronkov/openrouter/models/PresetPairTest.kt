package org.zhavoronkov.openrouter.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

class PresetPairTest {

    @ParameterizedTest
    @CsvSource(
        "openai/gpt-5.2@preset/research,      openai/gpt-5.2,          research",
        "x-ai/grok-4-fast:free@preset/quick,  x-ai/grok-4-fast:free,   quick",
        "~anthropic/claude-latest@preset/deep, ~anthropic/claude-latest, deep",
        "~openai/gpt-latest:nitro@preset/a-b_1, ~openai/gpt-latest:nitro, a-b_1",
        "openrouter/auto@preset/cheap,        openrouter/auto,         cheap"
    )
    @DisplayName("a pair id splits into its model - variants and Latest Models included - and its preset")
    fun parses(id: String, model: String, preset: String) {
        assertEquals(PresetPair(model, preset), PresetPair.parse(id))
        assertEquals(id, PresetPair(model, preset).id)
    }

    @ParameterizedTest
    @CsvSource(
        "openai/gpt-5.2",
        "@preset/research",
        "openai/gpt-5.2@preset/",
        "openai/gpt-5.2@preset/two words",
        "'openai/gpt-5.2@preset/ research'",
        "a@preset/x@preset/y"
    )
    @DisplayName("anything else - a whole preset included - is an ordinary model id")
    fun notPairs(id: String) {
        assertNull(PresetPair.parse(id))
        assertFalse(PresetPair.isPair(id))
        assertEquals(id, PresetPair.modelOf(id))
    }

    @Test
    @DisplayName("a pair's model is what it sends")
    fun modelOf() {
        assertEquals("openai/gpt-4o:nitro", PresetPair.modelOf("openai/gpt-4o:nitro@preset/research"))
        assertTrue(PresetPair.isPair("openai/gpt-4o@preset/research"))
    }

    /** The model id's own parsing runs on the model, so a pair files under its real author and variant. */
    @Test
    @DisplayName("the model part parses as the model it is")
    fun modelPartParses() {
        val model = PresetPair.modelOf("~x-ai/grok-latest:free@preset/quick")

        assertEquals("x-ai", ModelProviderUtils.authorSlug(model))
        assertEquals(ModelProviderUtils.ModelVariant.FREE, ModelProviderUtils.parseModelId(model).variant)
        assertTrue(ModelProviderUtils.parseModelId(model).latest)
    }

    @ParameterizedTest
    @ValueSource(strings = [" @preset/research", "   @preset/research"])
    @DisplayName("a pair id whose model part is only space is no pair")
    fun blankModel(id: String) {
        assertNull(PresetPair.parse(id))
        assertEquals(id, PresetPair.modelOf(id))
    }
}
