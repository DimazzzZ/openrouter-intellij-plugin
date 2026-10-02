package org.zhavoronkov.openrouter.presets

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The application's copy of the presets, as the proxy and the chat ask it whether a pair can be sent. */
class PresetCopyServicePlatformTest : BasePlatformTestCase() {

    fun testPairsAreAskedOfTheApplicationsCopyAndCatalogue() {
        val pairs = PresetCopyService.pairs()

        val taken = pairs.snapshot()

        assertNull("not a pair, so nothing is wrong with it", taken.problem("openai/gpt-4o"))
        assertSame(PresetCopyService.getInstance(), PresetCopyService.getInstance())
    }
}
