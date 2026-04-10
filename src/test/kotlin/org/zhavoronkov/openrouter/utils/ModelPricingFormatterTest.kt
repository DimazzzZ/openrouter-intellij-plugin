package org.zhavoronkov.openrouter.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.zhavoronkov.openrouter.models.ModelPricing
import org.zhavoronkov.openrouter.models.OpenRouterModelInfo

@DisplayName("ModelPricingFormatter Tests")
class ModelPricingFormatterTest {

    @Nested
    @DisplayName("Input Price Formatting")
    inner class InputPriceTests {

        @Test
        fun `should format valid input price`() {
            val pricing = ModelPricing(prompt = "0.0000015", completion = "0.000006", image = null, request = null)
            assertEquals("$1.5000", ModelPricingFormatter.formatInputPrice(pricing))
        }

        @Test
        fun `should return dash for null pricing`() {
            assertEquals("—", ModelPricingFormatter.formatInputPrice(null))
        }

        @Test
        fun `should return dash for null prompt price`() {
            val pricing = ModelPricing(prompt = null, completion = "0.000006", image = null, request = null)
            assertEquals("—", ModelPricingFormatter.formatInputPrice(pricing))
        }

        @Test
        fun `should return dash for empty prompt price`() {
            val pricing = ModelPricing(prompt = "", completion = "0.000006", image = null, request = null)
            assertEquals("—", ModelPricingFormatter.formatInputPrice(pricing))
        }

        @Test
        fun `should return dash for invalid prompt price`() {
            val pricing = ModelPricing(prompt = "invalid", completion = "0.000006", image = null, request = null)
            assertEquals("—", ModelPricingFormatter.formatInputPrice(pricing))
        }
    }

    @Nested
    @DisplayName("Output Price Formatting")
    inner class OutputPriceTests {

        @Test
        fun `should format valid output price`() {
            val pricing = ModelPricing(prompt = "0.0000015", completion = "0.000006", image = null, request = null)
            assertEquals("$6.0000", ModelPricingFormatter.formatOutputPrice(pricing))
        }

        @Test
        fun `should return dash for null completion price`() {
            val pricing = ModelPricing(prompt = "0.0000015", completion = null, image = null, request = null)
            assertEquals("—", ModelPricingFormatter.formatOutputPrice(pricing))
        }
    }

    @Nested
    @DisplayName("Combined Price Formatting")
    inner class CombinedPriceTests {

        @Test
        fun `should format combined price`() {
            val model = createTestModel(
                promptPrice = "0.0000015",
                completionPrice = "0.000006"
            )
            assertEquals("$1.5000 / $6.0000 per 1M tok", ModelPricingFormatter.formatCombinedPrice(model))
        }

        @Test
        fun `should handle missing pricing in combined format`() {
            val model = createTestModel(promptPrice = null, completionPrice = null)
            assertEquals("— / — per 1M tok", ModelPricingFormatter.formatCombinedPrice(model))
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        fun `should handle zero price`() {
            val pricing = ModelPricing(prompt = "0", completion = "0", image = null, request = null)
            assertEquals("$0.0000", ModelPricingFormatter.formatInputPrice(pricing))
            assertEquals("$0.0000", ModelPricingFormatter.formatOutputPrice(pricing))
        }

        @Test
        fun `should handle very small prices`() {
            val pricing = ModelPricing(prompt = "0.0000001", completion = "0.0000002", image = null, request = null)
            assertEquals("$0.1000", ModelPricingFormatter.formatInputPrice(pricing))
            assertEquals("$0.2000", ModelPricingFormatter.formatOutputPrice(pricing))
        }

        @Test
        fun `should handle large prices`() {
            val pricing = ModelPricing(prompt = "0.001", completion = "0.002", image = null, request = null)
            assertEquals("$1000.0000", ModelPricingFormatter.formatInputPrice(pricing))
            assertEquals("$2000.0000", ModelPricingFormatter.formatOutputPrice(pricing))
        }
    }

    private fun createTestModel(
        promptPrice: String?,
        completionPrice: String?
    ): OpenRouterModelInfo {
        return OpenRouterModelInfo(
            id = "openai/gpt-4",
            name = "openai/gpt-4",
            created = System.currentTimeMillis() / 1000,
            pricing = ModelPricing(
                prompt = promptPrice,
                completion = completionPrice,
                image = null,
                request = null
            )
        )
    }
}
