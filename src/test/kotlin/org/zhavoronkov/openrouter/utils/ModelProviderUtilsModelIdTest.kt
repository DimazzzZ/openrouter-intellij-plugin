package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/** Model ids of shapes the catalogue rarely sends: a Latest Models id rebuilt, an empty author. */
@DisplayName("ModelProviderUtils model id shapes")
class ModelProviderUtilsModelIdTest {

    @Test
    fun `a Latest Models id round-trips with its marker`() {
        assertEquals("~OpenAI/gpt-latest", ModelProviderUtils.parseModelId("~openai/gpt-latest").toFullId())
    }

    @Test
    fun `an id with nothing before its slash has an empty provider`() {
        val parsed = ModelProviderUtils.parseModelId("/gpt-4o")

        assertEquals("", parsed.provider)
        assertEquals("gpt-4o", parsed.baseName)
    }
}
