package org.zhavoronkov.openrouter.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The favourites service as the platform builds it, reading the favourites from the plugin's own settings. */
class FavoriteModelsServicePlatformTest : BasePlatformTestCase() {

    fun testTheApplicationsServiceReadsTheSettingsFavorites() {
        val settings = OpenRouterSettingsService.getInstance().favoriteModelsManager
        val before = settings.getFavoriteModels()
        try {
            settings.setFavoriteModels(listOf("openai/gpt-4o"))

            assertEquals(listOf("openai/gpt-4o"), FavoriteModelsService.getInstance().getFavoriteModels().map { it.id })
        } finally {
            settings.setFavoriteModels(before)
        }
    }
}
