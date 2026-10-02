package org.zhavoronkov.openrouter.services.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.OpenRouterSettings
import org.zhavoronkov.openrouter.models.WebSearchEngine
import org.zhavoronkov.openrouter.models.WebSearchSettings

@DisplayName("WebSearchSettingsManager")
class WebSearchSettingsManagerTest {

    private lateinit var settings: OpenRouterSettings
    private var notifications = 0
    private lateinit var manager: WebSearchSettingsManager

    private val tuned = WebSearchSettings(
        engine = WebSearchEngine.EXA,
        maxResults = 8,
        includeDomains = listOf("docs.gradle.org", "*.jetbrains.com"),
        excludeDomains = listOf("pinterest.com"),
        mode = "deep"
    )

    @BeforeEach
    fun setUp() {
        settings = OpenRouterSettings()
        notifications = 0
        manager = WebSearchSettingsManager(settings) { notifications++ }
    }

    @Test
    @DisplayName("fresh settings leave every choice to OpenRouter")
    fun `fresh settings leave every choice to OpenRouter`() {
        assertEquals(WebSearchSettings(), manager.current())
        assertEquals(emptyMap<String, Any>(), manager.current().toolParameters())
    }

    @Test
    @DisplayName("a stored configuration reads back as it was stored, and notifies once")
    fun `a stored configuration reads back as it was stored`() {
        manager.replace(tuned)

        assertEquals(tuned, manager.current())
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("storing what is already stored does not notify")
    fun `storing what is already stored does not notify`() {
        manager.replace(tuned)
        manager.replace(tuned.copy())

        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("an engine this build does not know falls back to letting OpenRouter choose")
    fun `an unknown engine falls back to letting OpenRouter choose`() {
        settings.webSearchEngine = "some-future-engine"

        assertEquals(null, manager.current().engine)
    }

    @Test
    @DisplayName("a stored mode the stored engine does not take reads as its default")
    fun `a stored mode the engine does not take reads as its default`() {
        settings.webSearchEngine = "parallel"
        settings.webSearchMode = "deep"

        assertEquals(null, manager.current().mode)
    }

    /**
     * A clean value can equal what [WebSearchSettingsManager.current] made of a bad stored one, and
     * must still replace the stored fields rather than be taken for no change.
     */
    @Test
    @DisplayName("storing a clean value over a forgiven stored one rewrites it")
    fun `storing a clean value over a forgiven stored one rewrites it`() {
        settings.webSearchEngine = "some-future-engine"

        manager.replace(WebSearchSettings())

        assertEquals("", settings.webSearchEngine)
        assertEquals(1, notifications)
    }

    @Test
    @DisplayName("a stored result count outside the page's bounds is brought inside them")
    fun `a stored result count outside the bounds is brought inside them`() {
        settings.webSearchMaxResults = 0
        assertEquals(WebSearchSettings.MIN_MAX_RESULTS, manager.current().maxResults)

        settings.webSearchMaxResults = 1_000
        assertEquals(WebSearchSettings.MAX_MAX_RESULTS, manager.current().maxResults)
    }
}
