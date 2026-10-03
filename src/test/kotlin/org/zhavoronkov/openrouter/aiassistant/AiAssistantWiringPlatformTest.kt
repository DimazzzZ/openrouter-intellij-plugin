package org.zhavoronkov.openrouter.aiassistant

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** AI Assistant builds these through their no-argument constructors, which resolve the plugin's services. */
class AiAssistantWiringPlatformTest : BasePlatformTestCase() {

    fun testTheProvidersAIAssistantCreatesResolveTheirServices() {
        assertNotNull(OpenRouterChatContextProvider())
        assertNotNull(OpenRouterSmartChatEndpointProvider())
        assertNotNull(OpenRouterModelProvider())
    }
}
