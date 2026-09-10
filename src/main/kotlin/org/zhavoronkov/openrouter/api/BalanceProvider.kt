package org.zhavoronkov.openrouter.api

import org.jetbrains.annotations.ApiStatus

/**
 * Extension point interface for receiving OpenRouter balance data.
 *
 * This interface allows other plugins to receive real-time balance updates from the
 * OpenRouter plugin without needing direct access to OpenRouter's internal services.
 *
 * ## Security Note
 * This extension point shares your OpenRouter balance information (credits, usage)
 * with implementing plugins. Users can disable this feature in Settings → Tools → OpenRouter.
 *
 * ## Implementation Guide
 * To receive balance updates in your plugin:
 *
 * 1. Add a dependency on the OpenRouter plugin in your `plugin.xml`:
 *    ```xml
 *    <depends>org.zhavoronkov.openrouter</depends>
 *    ```
 *
 * 2. Implement this interface:
 *    ```kotlin
 *    class MyBalanceListener : BalanceProvider {
 *        override fun onBalanceUpdated(data: BalanceData) {
 *            // Handle balance update
 *        }
 *    }
 *    ```
 *
 * 3. Register your implementation in your `plugin.xml`:
 *    ```xml
 *    <extensions defaultExtensionNs="org.zhavoronkov.openrouter">
 *        <balanceProvider implementation="com.example.MyBalanceListener"/>
 *    </extensions>
 *    ```
 *
 * ## Threading
 * Callbacks are invoked on the EDT (Event Dispatch Thread). Implementations should
 * handle data quickly to avoid blocking the UI. For long-running operations, use
 * background threads or coroutines.
 *
 * @see BalanceData
 * @since 0.6.0
 */
@ApiStatus.AvailableSince("0.6.0")
interface BalanceProvider {

    /**
     * Called when balance data is updated.
     *
     * This method is invoked whenever the OpenRouter plugin successfully refreshes
     * balance information from the API. The data is immutable and validated.
     *
     * @param data The current balance snapshot containing credits and usage information
     */
    fun onBalanceUpdated(data: BalanceData)

    /**
     * Called when balance loading starts.
     *
     * This optional callback can be used to show loading indicators in your UI.
     * Default implementation does nothing.
     */
    fun onBalanceLoading() {
        // Default empty implementation
    }

    /**
     * Called when balance loading fails.
     *
     * This optional callback is invoked when the OpenRouter plugin fails to retrieve
     * balance data (e.g., network error, authentication failure).
     *
     * @param error A human-readable error message describing the failure
     */
    fun onBalanceError(error: String) {
        // Default empty implementation
    }
}
