package org.zhavoronkov.openrouter.settings

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.utils.ModelProviderUtils

class ModelFilterCriteriaTest {

    @Test
    fun `default criteria should have no active filters`() {
        val criteria = ModelFilterCriteria.default()

        assertEquals("All Providers", criteria.provider)
        assertEquals(ModelProviderUtils.ContextRange.ANY, criteria.contextRange)
        assertFalse(criteria.requireVision)
        assertFalse(criteria.requireAudio)
        assertFalse(criteria.requireTools)
        assertFalse(criteria.requireImageGen)
        assertEquals("", criteria.searchText)
        assertFalse(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when provider is set`() {
        val criteria = ModelFilterCriteria(provider = "OpenAI")
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when context range is set`() {
        val criteria = ModelFilterCriteria(contextRange = ModelProviderUtils.ContextRange.LARGE)
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when vision is required`() {
        val criteria = ModelFilterCriteria(requireVision = true)
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when audio is required`() {
        val criteria = ModelFilterCriteria(requireAudio = true)
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when tools is required`() {
        val criteria = ModelFilterCriteria(requireTools = true)
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should return true when image gen is required`() {
        val criteria = ModelFilterCriteria(requireImageGen = true)
        assertTrue(criteria.hasActiveFilters())
    }

    @Test
    fun `hasActiveFilters should not count search text`() {
        val criteria = ModelFilterCriteria(searchText = "gpt")
        assertFalse(criteria.hasActiveFilters())
    }

    @Test
    fun `getActiveFiltersDescription should describe provider filter`() {
        val criteria = ModelFilterCriteria(provider = "OpenAI")
        val description = criteria.getActiveFiltersDescription()
        assertTrue(description.contains("Provider: OpenAI"))
    }

    @Test
    fun `getActiveFiltersDescription should describe context filter`() {
        val criteria = ModelFilterCriteria(contextRange = ModelProviderUtils.ContextRange.LARGE)
        val description = criteria.getActiveFiltersDescription()
        assertTrue(description.contains("Context: > 128K"))
    }

    @Test
    fun `getActiveFiltersDescription should describe capability filters`() {
        val criteria = ModelFilterCriteria(
            requireVision = true,
            requireTools = true
        )
        val description = criteria.getActiveFiltersDescription()
        assertTrue(description.contains("Capabilities: Vision, Tools"))
    }

    @Test
    fun `getActiveFiltersDescription should combine multiple filters`() {
        val criteria = ModelFilterCriteria(
            provider = "OpenAI",
            contextRange = ModelProviderUtils.ContextRange.MEDIUM,
            requireVision = true
        )
        val description = criteria.getActiveFiltersDescription()

        // Check that all filter types are present in the description
        assertTrue(description.contains("Provider: OpenAI"))
        assertTrue(description.contains("Context:"))
        assertTrue(description.contains("Capabilities: Vision"))

        // Check that filters are separated by pipe
        assertTrue(description.contains(" | "))
    }

    @Test
    fun `getActiveFiltersDescription should return No filters when none active`() {
        val criteria = ModelFilterCriteria.default()
        assertEquals("No filters", criteria.getActiveFiltersDescription())
    }

    @Test
    fun `getActiveFilterCount should count all active filters`() {
        val criteria = ModelFilterCriteria(
            provider = "OpenAI",
            contextRange = ModelProviderUtils.ContextRange.LARGE,
            requireVision = true,
            requireTools = true
        )
        assertEquals(4, criteria.getActiveFilterCount())
    }

    @Test
    fun `getActiveFilterCount should return 0 for default criteria`() {
        val criteria = ModelFilterCriteria.default()
        assertEquals(0, criteria.getActiveFilterCount())
    }

    @Test
    fun `forProvider should create criteria with provider set`() {
        val criteria = ModelFilterCriteria.forProvider("Anthropic")
        assertEquals("Anthropic", criteria.provider)
        assertEquals(ModelProviderUtils.ContextRange.ANY, criteria.contextRange)
        assertFalse(criteria.requireVision)
    }

    @Test
    fun `forMultimodal should create criteria with vision required`() {
        val criteria = ModelFilterCriteria.forMultimodal()
        assertTrue(criteria.requireVision)
        assertEquals("All Providers", criteria.provider)
    }

    @Test
    fun `forCoding should create criteria with tools required`() {
        val criteria = ModelFilterCriteria.forCoding()
        assertTrue(criteria.requireTools)
        assertEquals("All Providers", criteria.provider)
    }

    @Test
    fun `criteria should be immutable data class`() {
        val criteria1 = ModelFilterCriteria(provider = "OpenAI")
        val criteria2 = criteria1.copy(requireVision = true)

        assertEquals("OpenAI", criteria1.provider)
        assertFalse(criteria1.requireVision)

        assertEquals("OpenAI", criteria2.provider)
        assertTrue(criteria2.requireVision)
    }

    @Test
    fun `criteria equality should work correctly`() {
        val criteria1 = ModelFilterCriteria(provider = "OpenAI", requireVision = true)
        val criteria2 = ModelFilterCriteria(provider = "OpenAI", requireVision = true)
        val criteria3 = ModelFilterCriteria(provider = "Anthropic", requireVision = true)

        assertEquals(criteria1, criteria2)
        assertNotEquals(criteria1, criteria3)
    }
}
